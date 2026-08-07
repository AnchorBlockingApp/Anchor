package com.dan.anchor.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
    private val handler = Handler(Looper.getMainLooper())

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
        flushUsage()
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
            flushUsage()
            currentPkg = pkg
            forgetUrl()
            Watch.clearUrl()
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleForeground(pkg)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (BlockRules.isBrowser(pkg)) checkBrowser(pkg)
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
                fire(d, key = "url:${BlockRules.host(url)}")
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
        BlockRules.BROWSERS[pkg]?.let { id -> byId(root, id)?.let { return it } }
        for (id in BlockRules.URL_BAR_IDS) {
            val scoped = if (id.contains(':')) id else "$pkg:id/$id"
            byId(root, scoped)?.let { return it }
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
        val limited = prefs.rules.value.firstOrNull {
            it.target == target && !it.isHardBlock
        } ?: run { stopTracking(); return }

        if (currentTrackedTarget != limited.target) {
            flushUsage()
            currentTrackedTarget = limited.target
            trackingSince = System.currentTimeMillis()
        }
    }

    private fun stopTracking() {
        flushUsage()
        currentTrackedTarget = null
    }

    private fun flushUsage() {
        val t = currentTrackedTarget ?: return
        val now = System.currentTimeMillis()
        val elapsed = now - trackingSince
        if (elapsed in 1_000..3_600_000) prefs.addUsageMillis(t, elapsed)
        trackingSince = now
    }

    /**
     * Runs once a second. Two jobs: notice when a daily allowance runs out
     * mid-session, and re-read the address bar while a browser is in front.
     * The poll matters because navigation inside a page often produces no
     * event the service can act on.
     */
    private val ticker = object : Runnable {
        override fun run() {
            if (::prefs.isInitialized) {
                val t = currentTrackedTarget
                if (t != null) {
                    flushUsage()
                    val rule = prefs.rules.value.firstOrNull { it.target == t }
                    if (rule != null && !rule.isHardBlock &&
                        prefs.usedMillis(t) >= rule.limitMinutes * 60_000L
                    ) {
                        stopTracking()
                        fire(Decision.Block(rule.label, Decision.Reason.LIMIT_REACHED), key = "limit:$t")
                    }
                }
                if (BlockRules.isBrowser(currentPkg)) checkBrowser(currentPkg)
            }
            if (running) handler.postDelayed(this, 1_000L)
        }
    }

    // ---------- showing the block screen ----------

    private fun fire(decision: Decision.Block, key: String) {
        val now = System.currentTimeMillis()
        if (key == lastBlockedKey && now - lastBlockAt < 2_500L) return
        lastBlockedKey = key
        lastBlockAt = now

        prefs.blockCount += 1

        val i = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(BlockOverlayActivity.EXTRA_LABEL, decision.label)
            putExtra(BlockOverlayActivity.EXTRA_REASON, decision.reason.name)
        }
        startActivity(i)
    }

    companion object {
        @Volatile var running: Boolean = false
            private set
    }
}
