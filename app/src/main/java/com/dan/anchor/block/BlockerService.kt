package com.dan.anchor.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dan.anchor.data.Prefs

/**
 * Everything Anchor does hangs off this service. Android tells it which window
 * is in front; it works out whether that's something you've asked to stay away
 * from, and if so throws the block screen over the top.
 *
 * It reads only the address bar of browsers and the package name of the app in
 * front. Nothing leaves the phone.
 */
class BlockerService : AccessibilityService() {

    private lateinit var prefs: Prefs

    /**
     * An accessibility service shares the app's main looper, so polling there
     * meant a blocking call into the system process on the same thread that
     * draws the UI — once a second, which is exactly what you feel as lag.
     * Everything heavy runs here instead.
     */
    private val worker = HandlerThread("anchor-watch").apply { start() }
    private val handler = Handler(worker.looper)

    /** Toasts have to be raised on the main thread. */
    private val uiHandler = Handler(android.os.Looper.getMainLooper())
    private val power by lazy { getSystemService(POWER_SERVICE) as android.os.PowerManager }

    /**
     * The view id that actually worked for a given browser. Without this the
     * service re-tries every id in the list once a second, which is ten calls
     * across to the system process for no reason.
     */
    private val knownBarId = HashMap<String, String>()

    private var currentPkg: String = ""
    private var currentTrackedTarget: String? = null
    private var trackingSince: Long = 0L

    /**
     * Last URL successfully read for the browser in front. Chrome hides the
     * omnibox when you scroll down, which makes the node disappear from the
     * tree — without this, scrolling would quietly switch blocking off.
     * Cleared whenever the foreground app changes.
     */
    private var cachedUrl: String = ""
    private var cachedAt: Long = 0L

    @Volatile private var lastContentCheck: Long = 0L
    private var lastLeavePage: Long = 0L
    private var lastUsageWrite: Long = 0L
    private var lastBlockedKey: String = ""
    private var lastBlockAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs.get(this)
        running = true
        handler.post(ticker)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        flushUsage(force = true)
        worker.quitSafely()
        super.onDestroy()
    }

    override fun onInterrupt() { /* nothing to clean up */ }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!::prefs.isInitialized) prefs = Prefs.get(this)

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        Watch.note(pkg)

        if (pkg != currentPkg) {
            flushUsage(force = true)
            currentPkg = pkg
            forgetUrl()
            Watch.clearUrl()
        }

        if (BlockOverlayActivity.showing) return

        // Events make blocking feel instant; the one-second poll is what makes
        // it reliable. Neither is trusted on its own. Both hand the actual work
        // to the worker thread — this callback is on the UI thread.
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                handler.post { handleForeground(pkg) }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // A busy page fires these dozens of times a second. Reading the
                // address bar that often is pure waste; it can't change faster
                // than you can navigate.
                val now = System.currentTimeMillis()
                if (BlockRules.isBrowser(pkg) && now - lastContentCheck > 500L) {
                    lastContentCheck = now
                    handler.post { checkBrowser(pkg) }
                }
            }
        }
    }

    private fun handleForeground(pkg: String) {
        if (guardSettings(pkg)) return

        when (val d = BlockRules.evaluateApp(this, pkg)) {
            is Decision.Block -> {
                Watch.decision("blocked app $pkg")
                stopTracking()
                fire(d, key = "app:$pkg")
                return
            }
            Decision.Allow -> Unit
        }

        if (BlockRules.isBrowser(pkg)) checkBrowser(pkg) else startTrackingIfLimited(pkg)
    }

    // ---------- browser URL reading ----------

    private fun checkBrowser(pkg: String) {
        val url = readUrl(pkg)
        if (url.isNullOrBlank()) {
            Watch.decision("browser in front, no URL readable")
            return
        }

        when (val d = BlockRules.evaluateUrl(this, url)) {
            is Decision.Block -> {
                Watch.decision("blocked ${BlockRules.host(url)}")
                stopTracking()
                if (d.reason == Decision.Reason.SESSION) {
                    // A picker, not a refusal — leave the page where it is.
                    fire(d, key = "url:${BlockRules.host(url)}")
                } else {
                    // Off the page first, then the verse. Reopening the browser
                    // otherwise lands straight back on the blocked page and
                    // blocks again, making the browser unusable for anything.
                    leaveBlockedPage()
                    handler.postDelayed({ fire(d, key = "url:${BlockRules.host(url)}") }, 350L)
                }
            }
            Decision.Allow -> {
                Watch.decision("allowed ${BlockRules.host(url)}")
                startTrackingIfLimited(BlockRules.host(url))
            }
        }
    }

    /**
     * Reads the address bar, and only the address bar.
     *
     * An earlier version scanned the whole view tree for anything shaped like a
     * domain. That was a bad idea: Chrome's new-tab page lists your shortcut
     * tiles by name, so "Reddit" sitting in a tile was read as the current
     * address and blocked the browser on open. Nothing but a node that is
     * genuinely the omnibox counts now.
     */
    private fun readUrl(pkg: String): String? {
        val root = rootInActiveWindow ?: return cachedIfFresh()

        val bar = findAddressBar(root, pkg)
            // Omnibox isn't in the tree — normally it's scrolled out of sight,
            // so fall back to the address we last read for a short while.
            ?: return cachedIfFresh()

        // A focused omnibox means you're typing. What's in it is a half-written
        // query, not the page you're on, and must never be evaluated.
        if (bar.isFocused) {
            forgetUrl()
            Watch.url("(typing)", "omnibox focused")
            return null
        }

        val text = bar.text?.toString()?.trim()
        if (text.isNullOrBlank() || !looksLikeUrl(text)) {
            forgetUrl()
            return null
        }

        cache(text)
        Watch.url(text, "omnibox")
        return text
    }

    /** Known address-bar view ids only — never a guess based on content. */
    private fun findAddressBar(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        // Whatever worked last time, first. This is the difference between one
        // lookup a second and eleven.
        knownBarId[pkg]?.let { id -> byId(root, id)?.let { return it } }

        BlockRules.BROWSERS[pkg]?.let { id ->
            byId(root, id)?.let { knownBarId[pkg] = id; return it }
        }
        for (id in BlockRules.URL_BAR_IDS) {
            val scoped = if (id.contains(':')) id else "$pkg:id/$id"
            byId(root, scoped)?.let { knownBarId[pkg] = scoped; return it }
        }
        return null
    }

    private fun byId(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? = try {
        root.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
    } catch (_: Exception) {
        null
    }

    private fun cache(url: String) {
        cachedUrl = url
        cachedAt = System.currentTimeMillis()
    }

    private fun forgetUrl() {
        cachedUrl = ""
        cachedAt = 0L
    }

    /**
     * The cache only covers the omnibox being temporarily off screen. It has to
     * expire, or one blocked page would poison every later page in the session.
     */
    private fun cachedIfFresh(): String? =
        cachedUrl.takeIf { it.isNotBlank() && System.currentTimeMillis() - cachedAt < 8_000L }

    /** Rejects hint text, page headings and anything without a real hostname. */
    private fun looksLikeUrl(text: String): Boolean {
        val t = text.trim()
        if (t.length !in 4..2048) return false
        if (t.contains(' ') && !t.startsWith("http")) return false
        if (!t.contains('.') && !t.startsWith("http")) return false
        val hostPart = BlockRules.host(t)
        return hostPart.contains('.') && hostPart.length >= 4
    }

    /**
     * Navigates the browser off the blocked page.
     *
     * An earlier version tried opening a blank tab with a data: URL. Chrome has
     * blocked data: navigation from external intents for years, so it silently
     * did nothing. A back action doesn't depend on the browser honouring any
     * URL scheme, which is why it's used instead.
     *
     * Timing matters: this has to happen while the browser is still in front and
     * before the block screen exists, or the back lands on the verse and
     * dismisses it instead.
     */
    private fun leaveBlockedPage() {
        val now = System.currentTimeMillis()
        if (now - lastLeavePage < 5_000L) return
        lastLeavePage = now
        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
    }

    // ---------- strict-mode self defence ----------

    private fun guardSettings(pkg: String): Boolean {
        if (!prefs.strictMode.value || prefs.isEditable()) return false
        // Anchor itself just sent you here — don't bounce you out of a screen we opened.
        if (prefs.settingsAllowed()) return false
        if (pkg != "com.android.settings" && !pkg.endsWith(".settings")) return false

        val root = rootInActiveWindow ?: return false
        val hit = try {
            listOf(
                "Anchor",
                "Installed apps", "Downloaded apps", "Downloaded services",
                "Device admin", "Device administrators", "Device admin apps",
                "Uninstall", "Force stop", "Clear storage", "Clear data"
            ).any { root.findAccessibilityNodeInfosByText(it)?.isNotEmpty() == true }
        } catch (_: Exception) {
            false
        }
        if (!hit) return false

        performGlobalAction(GLOBAL_ACTION_BACK)
        fire(Decision.Block("Anchor's own settings", Decision.Reason.SELF_DEFENCE), key = "self:settings")
        Watch.decision("bounced out of settings")
        return true
    }

    // ---------- daily limit tracking ----------

    private fun startTrackingIfLimited(target: String) {
        val rules = prefs.rules.value
        // Either it has an allowance of its own, or it's the gate app for
        // something else — both need their minutes counted.
        val tracked = rules.any { it.target == target && !it.isHardBlock } ||
            rules.any { it.hasGate && it.gateApp == target }

        if (!tracked) { stopTracking(); return }

        if (currentTrackedTarget != target) {
            flushUsage(force = true)
            currentTrackedTarget = target
            trackingSince = System.currentTimeMillis()
        }
    }

    private fun stopTracking() {
        flushUsage(force = true)
        currentTrackedTarget = null
    }

    /**
     * Time already banked to storage, plus whatever this session has run up but
     * not written yet. Lets the allowance check stay accurate to the second
     * without a disk write every second.
     */
    private fun usedMillisIncludingSession(target: String): Long {
        val banked = prefs.usedMillis(target)
        if (currentTrackedTarget != target) return banked
        val live = System.currentTimeMillis() - trackingSince
        return banked + live.coerceIn(0, 3_600_000)
    }

    private fun flushUsage(force: Boolean = false) {
        val t = currentTrackedTarget ?: return
        val now = System.currentTimeMillis()
        // Writing once a second was costing more than the timer is worth.
        if (!force && now - lastUsageWrite < 15_000L) return
        val elapsed = now - trackingSince
        if (elapsed in 1_000..3_600_000) prefs.addUsageMillis(t, elapsed)
        trackingSince = now
        lastUsageWrite = now
    }

    /**
     * Windows that sit on top of whatever you're actually using. Treating these
     * as "the foreground app" is what stopped daily timers: pull down the shade
     * or tap a text field and the timer would quietly stop counting.
     */
    private fun isOverlayPackage(pkg: String): Boolean =
        pkg == "com.android.systemui" ||
            pkg.contains("inputmethod") ||
            pkg.contains("keyboard") ||
            pkg == packageName

    /** What's genuinely in front right now, ignoring the shade and the keyboard. */
    private fun foregroundPackage(): String? {
        // An event in the last second already told us what's in front. Asking
        // the system again is a call into another process for no new answer.
        if (System.currentTimeMillis() - Watch.lastEventAt < 1_200L &&
            currentPkg.isNotBlank() && !isOverlayPackage(currentPkg)
        ) {
            return currentPkg
        }
        val live = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (live != null && !isOverlayPackage(live)) return live
        return currentPkg.takeIf { it.isNotBlank() && !isOverlayPackage(it) }
    }

    /**
     * How soon to look again.
     *
     * Most of the time you're in something with no rule attached, and checking
     * that four times as often changes nothing. Events still fire instantly, so
     * backing off here costs no responsiveness where it matters.
     */
    private fun nextTickDelay(fg: String?): Long {
        if (fg == null) return SLOW_TICK_MS
        if (BlockRules.isBrowser(fg)) return FAST_TICK_MS
        if (currentTrackedTarget != null) return FAST_TICK_MS
        val watched = prefs.rules.value.any { it.target == fg || it.gateApp == fg }
        return if (watched) FAST_TICK_MS else SLOW_TICK_MS
    }

    /**
     * Runs once a second and is the authority on what's happening, rather than
     * waiting for Android to send an event. Apps don't reliably emit a
     * window-state change every time they come back to the front, and a daily
     * timer that only starts on an event is a timer that silently reads zero.
     */
    private val ticker = object : Runnable {
        override fun run() {
            var delay = SLOW_TICK_MS
            val awake = runCatching { power.isInteractive }.getOrDefault(true)
            if (awake && ::prefs.isInitialized && !BlockOverlayActivity.showing) {
                val fg = foregroundPackage()
                delay = nextTickDelay(fg)

                if (fg != null) {
                    if (fg != currentPkg) {
                        flushUsage(force = true)
                        currentPkg = fg
                        forgetUrl()
                    }

                    if (BlockRules.isBrowser(fg)) {
                        checkBrowser(fg)
                    } else {
                        when (val d = BlockRules.evaluateApp(this@BlockerService, fg)) {
                            is Decision.Block -> {
                                stopTracking()
                                fire(d, key = "app:$fg")
                            }
                            Decision.Allow -> startTrackingIfLimited(fg)
                        }
                    }
                }

                checkSessionExpiry()
                checkWarnings()

                // Has an allowance run out mid-session?
                val t = currentTrackedTarget
                if (t != null) {
                    val rule = prefs.rules.value.firstOrNull { it.target == t }
                    // An extension has to be honoured here too. Without this the
                    // poll re-blocks a second after one is granted, and the app
                    // is unusable despite having just spent one.
                    val extended = prefs.emergencyActive(t)
                    if (rule != null && !rule.isHardBlock && !extended &&
                        usedMillisIncludingSession(t) >= rule.limitMinutes * 60_000L
                    ) {
                        stopTracking()
                        fire(
                            // The target must travel with the block, or the screen
                            // can't offer an extension and there's no way forward.
                            Decision.Block(
                                rule.publicLabel,
                                Decision.Reason.LIMIT_REACHED,
                                target = t
                            ),
                            key = "limit:$t"
                        )
                    } else {
                        flushUsage()
                    }
                }
            }
            if (running) handler.postDelayed(this, delay)
        }
    }

    /**
     * Ends a session the moment its minutes are up, rather than waiting for the
     * next time the app is opened.
     */
    private fun checkSessionExpiry() {
        val t = currentTrackedTarget ?: return
        val rule = prefs.rules.value.firstOrNull { it.target == t } ?: return
        if (!rule.askEachTime || rule.isHardBlock) return
        if (prefs.emergencyActive(t)) return

        // Uses the live figure, including time not yet written to storage, so a
        // stretch ends on the minute rather than up to fifteen seconds late.
        val live = usedMillisIncludingSession(t)
        if (prefs.graceActive(t, live)) return
        if (prefs.sessionSpent(t, live)) {
            prefs.endSession(t)
            stopTracking()
            flushUsage(force = true)
            // Re-evaluate rather than computing the numbers here — rounding
            // minutes locally once reported "1 minute left" with seconds to go.
            when (val d = BlockRules.evaluateTarget(this, t)) {
                is Decision.Block -> fire(d, key = "session:$t")
                Decision.Allow -> fire(
                    Decision.Block(
                        label = rule.publicLabel,
                        reason = Decision.Reason.SESSION,
                        target = t,
                        minutesLeftToday = 1,
                        gapSeconds = (Prefs.SESSION_GAP_MS / 1000L).toInt(),
                        justEnded = true
                    ),
                    key = "session:$t"
                )
            }
        }
    }

    /**
     * A quiet heads-up before an allowance runs out.
     *
     * The five-minute mark is skipped on short limits — on a ten-minute
     * allowance it would land at halfway, which is a progress report rather
     * than a warning.
     */
    private fun checkWarnings() {
        if (!prefs.warningsOn) return
        val t = currentTrackedTarget ?: return
        val rule = prefs.rules.value.firstOrNull { it.target == t } ?: return
        if (rule.isHardBlock) return
        if (prefs.emergencyActive(t)) return

        val leftMs = rule.limitMinutes * 60_000L - usedMillisIncludingSession(t)
        val leftMin = (leftMs / 60_000L).toInt()

        val threshold = when {
            leftMs in 1..60_000L && !prefs.warningShown(t, 1) -> 1
            leftMin in 4..5 && rule.limitMinutes >= 10 && !prefs.warningShown(t, 5) -> 5
            else -> return
        }

        prefs.markWarningShown(t, threshold)
        val name = rule.publicLabel
        val text = if (threshold == 1) "About a minute left on $name."
        else "About five minutes left on $name."

        handler.post {
            uiHandler.post {
                runCatching { android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }

    // ---------- showing the block screen ----------

    private fun fire(decision: Decision.Block, key: String) {
        if (BlockOverlayActivity.showing) return
        val now = System.currentTimeMillis()
        // Just long enough to stop an event and the poll launching two screens
        // at once. Anything longer becomes a window you can scroll in — an
        // earlier 8-second version left the blocked app usable that whole time.
        if (key == lastBlockedKey && now - lastBlockAt < 600L) return
        lastBlockedKey = key
        lastBlockAt = now

        prefs.blockCount += 1

        // Do NOT send the user home here. Android treats a home action as the
        // user leaving, which fires onUserLeaveHint on the block screen and
        // finishes it before it can be read — the verse never appears. The app
        // gets backgrounded when the block screen is dismissed instead.
        val i = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(BlockOverlayActivity.EXTRA_LABEL, decision.label)
            putExtra(BlockOverlayActivity.EXTRA_REASON, decision.reason.name)
            putExtra(BlockOverlayActivity.EXTRA_TARGET, decision.target)
            putExtra(BlockOverlayActivity.EXTRA_GATE_LABEL, decision.gateLabel)
            putExtra(BlockOverlayActivity.EXTRA_GATE_DONE, decision.gateDone)
            putExtra(BlockOverlayActivity.EXTRA_GATE_NEEDED, decision.gateNeeded)
            putExtra(BlockOverlayActivity.EXTRA_LEFT_TODAY, decision.minutesLeftToday)
            putExtra(BlockOverlayActivity.EXTRA_GAP_SECONDS, decision.gapSeconds)
            putExtra(BlockOverlayActivity.EXTRA_JUST_ENDED, decision.justEnded)
        }
        startActivity(i)
    }

    companion object {
        @Volatile var running: Boolean = false
            private set

        private const val FAST_TICK_MS = 1_000L
        private const val SLOW_TICK_MS = 3_000L

    }
}
