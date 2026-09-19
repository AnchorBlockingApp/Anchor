package com.dan.anchor.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dan.anchor.R
import com.dan.anchor.block.BlockRules
import com.dan.anchor.block.BlockerService
import com.dan.anchor.data.Prefs
import com.dan.anchor.data.Rule
import com.dan.anchor.data.Schedule
import kotlinx.coroutines.delay

@Composable
fun BlocksScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val rules by prefs.rules.collectAsState()
    val strict by prefs.strictMode.collectAsState()
    val gate = rememberPinGate()

    var serviceOn by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(strict) {
        while (true) {
            serviceOn = isServiceEnabled(ctx)
            now = System.currentTimeMillis()
            // A countdown needs every second. A "3 of 10 min used" line does not.
            delay(if (strict) 1_000L else 5_000L)
        }
    }

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var showDisclosure by remember { mutableStateOf(false) }

    // Deliberately not rememberSaveable: leaving the tab re-hides the private
    // list. Handing someone your phone shouldn't leave it open behind you.
    var revealed by remember { mutableStateOf(false) }

    val editable = prefs.isEditable()
    val open = rules.filterNot { it.hidden }
    val hidden = rules.filter { it.hidden }

    /**
     * Adding a block, or tightening one, is free. Only loosening costs the PIN —
     * you should never have to authenticate to be more careful.
     */
    fun save(rule: Rule, replacing: Rule?) {
        val previous = replacing
            ?: rules.firstOrNull { it.target == rule.target && it.isApp == rule.isApp }
        val moreTime = previous != null && !previous.isHardBlock && !rule.isHardBlock &&
            rule.limitMinutes > previous.limitMinutes
        val unblocking = previous != null && previous.isHardBlock && !rule.isHardBlock
        // Narrowing the hours it applies means blocked for less of the week.
        val shorterHours = previous != null && rule.coverageMinutes() < previous.coverageMinutes()
        // Dropping the gate, or asking less of it, makes the rule easier to get past.
        val weakerGate = previous != null && previous.hasGate &&
            (!rule.hasGate || rule.gateMinutes < previous.gateMinutes)
        val droppedAsk = previous != null && previous.askEachTime && !rule.askEachTime

        if (moreTime || unblocking || shorterHours || weakerGate || droppedAsk) {
            val what = if (previous.hidden) "a private block" else previous.label
            val why = when {
                droppedAsk && !moreTime && !unblocking -> "Handing back the whole allowance on $what at once."
                weakerGate && !moreTime && !unblocking -> "Making $what easier to unlock."
                shorterHours && !moreTime && !unblocking -> "Blocking $what for fewer hours than before."
                else -> "Giving $what more time than it had."
            }
            gate.loosen(why) {
                if (replacing != null && replacing.target != rule.target) prefs.removeRule(replacing)
                prefs.upsertRule(rule)
            }
        } else {
            if (replacing != null && replacing.target != rule.target) prefs.removeRule(replacing)
            prefs.upsertRule(rule)
        }
    }

    fun remove(rule: Rule) {
        val what = if (rule.hidden) "a private block" else rule.label
        gate.loosen("Removing the block on $what.") { prefs.removeRule(rule) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 24.dp)
    ) {
        item {
            // The screen's own name is the title, matching Settings and Bible.
            // The mark and the app name carry the branding instead.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_anchor_mark),
                    contentDescription = null,
                    modifier = Modifier.size(width = 32.dp, height = 41.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("ANCHOR", style = Eyebrow)
                    Spacer(Modifier.height(4.dp))
                    Text("Blocks", style = MaterialTheme.typography.displaySmall)
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        if (!serviceOn) {
            item {
                SetupCard(
                    title = "Turn on blocking",
                    body = "Anchor needs the accessibility permission to see which app or site is in " +
                        "front. Find Anchor under Settings, Accessibility, Installed apps and switch " +
                        "it on. Leave the shortcut toggle off — it's a one-tap kill switch.",
                    cta = "Open accessibility settings"
                ) { showDisclosure = true }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (strict) {
            item {
                StrictBanner(prefs = prefs, now = now)
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR RULES", style = Eyebrow, color = Ink.Dim)
                Spacer(Modifier.weight(1f))
                Text("${open.size}", style = MaterialTheme.typography.labelSmall, color = Ink.Dim)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (open.isEmpty() && hidden.isEmpty()) {
            item {
                Text(
                    "Nothing is blocked yet. Add an app or a website below and Anchor will show " +
                        "you a verse instead of letting it open.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(20.dp))
            }
        }

        items(open, key = { "${it.isApp}:${it.target}" }) { rule ->
            RuleRow(
                rule = rule,
                usedMinutes = if (rule.isHardBlock) 0 else prefs.usedMinutes(rule.target),
                canEdit = editable,
                onEdit = { sheet = if (rule.isApp) Sheet.App(rule) else Sheet.Site(rule) },
                onRemove = { remove(rule) }
            )
        }

        // ---- private section ----
        if (hidden.isNotEmpty()) {
            item {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Lock, contentDescription = null,
                        tint = Ink.Dim, modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("PRIVATE", style = Eyebrow, color = Ink.Dim)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (revealed) "HIDE" else "SHOW",
                        style = Eyebrow,
                        color = Ink.Brass,
                        modifier = Modifier.clickable { revealed = !revealed }
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (!revealed) {
                    Text(
                        "${hidden.size} block${if (hidden.size == 1) "" else "s"} kept out of the " +
                            "list. Still enforced. Hides itself again when you leave this screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.Dim
                    )
                }
            }

            if (revealed) {
                items(hidden, key = { "h:${it.isApp}:${it.target}" }) { rule ->
                    RuleRow(
                        rule = rule,
                        usedMinutes = if (rule.isHardBlock) 0 else prefs.usedMinutes(rule.target),
                        canEdit = editable,
                        onEdit = { sheet = if (rule.isApp) Sheet.App(rule) else Sheet.Site(rule) },
                        onRemove = { remove(rule) }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AddButton("App", Modifier.weight(1f)) { sheet = Sheet.App(null) }
                AddButton("Website", Modifier.weight(1f)) { sheet = Sheet.Site(null) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    !editable ->
                        "You can still add to the block list. To remove anything, or give it more " +
                            "time, you'll need to wait for the cooldown to finish."
                    prefs.hasPin ->
                        "Tap a rule to change it. You don't need your PIN to add apps or websites " +
                            "to the block list, but you do need it to remove them or give them " +
                            "more time."
                    else ->
                        "Tap a rule to change it. Once you set a PIN in Settings, you'll need it " +
                            "to remove anything from the block list."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.Dim
            )
        }
    }

    if (showDisclosure) {
        AccessibilityDisclosure(
            onAccept = {
                showDisclosure = false
                prefs.allowSettingsBriefly()
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            onDismiss = { showDisclosure = false }
        )
    }

    when (val sh = sheet) {
        is Sheet.App -> AppSheet(
            existing = sh.rule,
            onDismiss = { sheet = null }
        ) { pkg, label, mins, priv, sc, ga, gl, gm, ask ->
            val keep = mins > 0
            save(
                Rule(
                    pkg, label, isApp = true, limitMinutes = mins, hidden = priv, schedule = sc,
                    askEachTime = ask && keep,
                    gateApp = if (keep) ga else null,
                    gateLabel = if (keep) gl else "",
                    gateMinutes = if (keep) gm else 0
                ),
                sh.rule
            )
            sheet = null
        }
        is Sheet.Site -> SiteSheet(
            existing = sh.rule,
            onDismiss = { sheet = null }
        ) { host, mins, priv, sc, ga, gl, gm, ask ->
            val keep = mins > 0
            save(
                Rule(
                    host, host, isApp = false, limitMinutes = mins, hidden = priv, schedule = sc,
                    askEachTime = ask && keep,
                    gateApp = if (keep) ga else null,
                    gateLabel = if (keep) gl else "",
                    gateMinutes = if (keep) gm else 0
                ),
                sh.rule
            )
            sheet = null
        }
        null -> Unit
    }
}

private sealed class Sheet {
    data class App(val rule: Rule?) : Sheet()
    data class Site(val rule: Rule?) : Sheet()
}

// ---------- pieces ----------

@Composable
private fun StrictBanner(prefs: Prefs, now: Long) {
    val at = prefs.unlockAt()
    val remaining = if (at > 0) ((at - now) / 1000).coerceAtLeast(0) else -1L

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Text(
            when {
                prefs.daysLockActive() -> "STRICT MODE — LOCKED IN"
                else -> "STRICT MODE ARMED"
            },
            style = Eyebrow
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                prefs.daysLockActive() ->
                    "Locked for a set time. Nothing loosens until it's over, PIN or not."
                at == 0L -> "Rules can't be loosened. Start the ${prefs.cooldownMinutes} minute " +
                    "wait in Settings — no PIN needed for that."
                remaining > 0 -> "Unlocking in ${fmt(remaining)}. You can cancel and stay locked."
                else -> "The wait is nearly up. Strict mode switches itself off when it does."
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun fmt(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun SetupCard(title: String, body: String, cta: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface)
            .border(1.dp, Ink.BrassDim, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text(cta.uppercase(), style = Eyebrow, modifier = Modifier.clickable(onClick = onClick))
    }
}

@Composable
private fun RuleRow(
    rule: Rule,
    usedMinutes: Int,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (rule.isHardBlock) Ink.Brass else Ink.Slate)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rule.label, style = MaterialTheme.typography.bodyLarge)
                if (rule.hidden) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Outlined.Lock, contentDescription = "Private",
                        tint = Ink.Dim, modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(if (rule.isApp) "App" else "Site")
                    append(" · ")
                    if (rule.isHardBlock) append("always blocked")
                    else append("$usedMinutes of ${rule.limitMinutes} min used today")
                    if (rule.askEachTime) append(" · asks each time")
                    if (rule.hasGate) append(" · after ${rule.gateMinutes}m ${rule.gateLabel}")
                    rule.schedule?.let { sc ->
                        append(" · ")
                        append(
                            if (sc.isAllDay) dayLabel(sc.days)
                            else "${Schedule.format(sc.fromMinutes)}-${Schedule.format(sc.toMinutes)} ${dayLabel(sc.days)}"
                        )
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.Dim
            )
        }
        if (canEdit) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove ${rule.label}",
                tint = Ink.Dim,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onRemove)
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))
}

@Composable
private fun AddButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Ink.Hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, tint = Ink.Brass, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label.uppercase(), style = Eyebrow, color = Ink.Bone)
    }
}

// ---------- sheets ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSheet(
    existing: Rule?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Boolean, Schedule?, String?, String, Int, Boolean) -> Unit
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember {
        mutableStateOf(existing?.let { it.target to it.label })
    }
    var minutes by remember { mutableStateOf(existing?.limitMinutes?.toString() ?: "0") }
    var priv by remember { mutableStateOf(existing?.hidden ?: false) }
    var sched by remember { mutableStateOf(existing?.schedule) }
    var gateApp by remember { mutableStateOf(existing?.gateApp) }
    var gateLabel by remember { mutableStateOf(existing?.gateLabel ?: "") }
    var gateMins by remember { mutableIntStateOf(existing?.gateMinutes ?: 0) }
    var askEach by remember { mutableStateOf(existing?.askEachTime ?: false) }

    LaunchedEffect(Unit) { apps = launchableApps(ctx) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink.Surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                when {
                    existing != null -> "EDIT THIS BLOCK"
                    selected == null -> "PICK AN APP"
                    else -> "SET A LIMIT"
                },
                style = Eyebrow
            )
            Spacer(Modifier.height(16.dp))

            if (selected == null) {
                Field(query, { query = it }, "Search apps")
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    val filtered = apps.filter { it.second.contains(query, true) }
                    items(filtered, key = { it.first }) { (pkg, label) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = pkg to label }
                                .padding(vertical = 14.dp)
                        )
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))
                    }
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(selected!!.second, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                LimitPicker(minutes) { minutes = it }
                Spacer(Modifier.height(24.dp))
                ScheduleEditor(sched) { sched = it }
                Spacer(Modifier.height(24.dp))
                GateEditor(
                    apps, gateApp, gateLabel, gateMins,
                    enabled = (minutes.toIntOrNull() ?: 0) > 0
                ) { a, l, m -> gateApp = a; gateLabel = l; gateMins = m }
                Spacer(Modifier.height(24.dp))
                AskEachTimeToggle(askEach, (minutes.toIntOrNull() ?: 0) > 0) { askEach = it }
                Spacer(Modifier.height(24.dp))
                PrivateToggle(priv) { priv = it }
                Spacer(Modifier.height(24.dp))
                PrimaryAction(if (existing != null) "Save changes" else "Add block") {
                    onSave(
                        selected!!.first, selected!!.second, minutes.toIntOrNull() ?: 0,
                        priv, sched, gateApp, gateLabel, gateMins, askEach
                    )
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiteSheet(
    existing: Rule?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Boolean, Schedule?, String?, String, Int, Boolean) -> Unit
) {
    var host by remember { mutableStateOf(existing?.target ?: "") }
    var minutes by remember { mutableStateOf(existing?.limitMinutes?.toString() ?: "0") }
    var priv by remember { mutableStateOf(existing?.hidden ?: false) }
    var sched by remember { mutableStateOf(existing?.schedule) }
    var gateApp by remember { mutableStateOf(existing?.gateApp) }
    var gateLabel by remember { mutableStateOf(existing?.gateLabel ?: "") }
    var gateMins by remember { mutableIntStateOf(existing?.gateMinutes ?: 0) }
    var askEach by remember { mutableStateOf(existing?.askEachTime ?: false) }
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { apps = launchableApps(ctx) }
    val clean = BlockRules.host(host)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink.Surface) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(if (existing != null) "EDIT THIS BLOCK" else "BLOCK A WEBSITE", style = Eyebrow)
            Spacer(Modifier.height(8.dp))
            Text(
                "Type the site, not the whole address. Blocking reddit.com also covers " +
                    "old.reddit.com and every page on it — and your browser itself keeps working.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))
            Field(host, { host = it }, "reddit.com")
            if (clean.isNotEmpty() && clean != host.lowercase()) {
                Spacer(Modifier.height(8.dp))
                Text("Will block: $clean", style = MaterialTheme.typography.bodyMedium, color = Ink.Brass)
            }
            Spacer(Modifier.height(20.dp))
            LimitPicker(minutes) { minutes = it }
            Spacer(Modifier.height(24.dp))
            ScheduleEditor(sched) { sched = it }
            Spacer(Modifier.height(24.dp))
            GateEditor(
                apps, gateApp, gateLabel, gateMins,
                enabled = (minutes.toIntOrNull() ?: 0) > 0
            ) { a, l, m -> gateApp = a; gateLabel = l; gateMins = m }
            Spacer(Modifier.height(24.dp))
            AskEachTimeToggle(askEach, (minutes.toIntOrNull() ?: 0) > 0) { askEach = it }
            Spacer(Modifier.height(24.dp))
            PrivateToggle(priv) { priv = it }
            Spacer(Modifier.height(24.dp))
            PrimaryAction(
                if (existing != null) "Save changes" else "Add block",
                enabled = clean.contains('.')
            ) {
                onSave(clean, minutes.toIntOrNull() ?: 0, priv, sched, gateApp, gateLabel, gateMins, askEach)
            }
        }
    }
}

@Composable
private fun ScheduleEditor(
    schedule: Schedule?,
    onChange: (Schedule?) -> Unit
) {
    val ctx = LocalContext.current
    val on = schedule != null
    val sc = schedule ?: Schedule(emptySet(), 22 * 60, 7 * 60)

    fun pickTime(current: Int, apply: (Int) -> Unit) {
        android.app.TimePickerDialog(
            ctx,
            { _, h, m -> apply(h * 60 + m) },
            current / 60, current % 60, true
        ).show()
    }

    Column {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("Only at certain times", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Off means blocked around the clock.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = on,
                onCheckedChange = { onChange(if (it) sc else null) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink.Void,
                    checkedTrackColor = Ink.Brass,
                    uncheckedThumbColor = Ink.Slate,
                    uncheckedTrackColor = Ink.Raised,
                    uncheckedBorderColor = Ink.Hairline
                )
            )
        }

        if (on) {
            Spacer(Modifier.height(18.dp))
            Text("DAYS", style = Eyebrow, color = Ink.Dim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to "M", 2 to "T", 3 to "W", 4 to "T", 5 to "F", 6 to "S", 7 to "S")
                    .forEach { (day, letter) ->
                        // Empty set means every day, so show them all lit.
                        val picked = sc.days.isEmpty() || sc.days.contains(day)
                        Text(
                            letter,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (picked) Ink.Void else Ink.Bone,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (picked) Ink.Brass else Ink.Raised)
                                .clickable {
                                    val base = if (sc.days.isEmpty()) Schedule.ALL_DAYS else sc.days
                                    val next = if (base.contains(day)) base - day else base + day
                                    // Never let them clear every day — that would
                                    // silently mean "never blocked".
                                    onChange(sc.copy(days = if (next.isEmpty()) Schedule.ALL_DAYS else next))
                                }
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
            }

            Spacer(Modifier.height(18.dp))
            Text("BETWEEN", style = Eyebrow, color = Ink.Dim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeChip(Schedule.format(sc.fromMinutes), Modifier.weight(1f)) {
                    pickTime(sc.fromMinutes) { onChange(sc.copy(fromMinutes = it)) }
                }
                Text(
                    "to",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                TimeChip(Schedule.format(sc.toMinutes), Modifier.weight(1f)) {
                    pickTime(sc.toMinutes) { onChange(sc.copy(toMinutes = it)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    sc.isAllDay -> "Same start and end — blocked all day on those days."
                    sc.wrapsMidnight ->
                        "Runs overnight: from ${Schedule.format(sc.fromMinutes)} through to " +
                            "${Schedule.format(sc.toMinutes)} the next morning."
                    else -> "Blocked between those hours on the days you picked."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.Dim
            )
        }
    }
}

@Composable
private fun TimeChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Raised)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink.Brass)
    }
}

/**
 * "Unlocks after": the thing you actually want to do has to happen first.
 *
 * Note this never grants extra minutes — the daily limit is unchanged. Doing
 * twenty minutes in the gate app instead of ten buys nothing, which is what
 * stops it becoming a currency you can farm.
 */
@Composable
private fun GateEditor(
    apps: List<Pair<String, String>>,
    gateApp: String?,
    gateLabel: String,
    gateMinutes: Int,
    /** A gate needs an allowance to open — with no daily limit there's nothing to unlock. */
    enabled: Boolean,
    onChange: (String?, String, Int) -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val on = gateApp != null && enabled

    Column {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Unlocks after another app",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) Ink.Bone else Ink.Dim
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (enabled)
                        "Blocked until you've put time into something else first. Doesn't change " +
                            "the daily limit — it just decides when it starts."
                    else
                        "Set a daily limit above and you can make this unlock only after time in " +
                            "another app. Works for websites too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) Ink.Slate else Ink.Dim
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = on,
                enabled = enabled,
                onCheckedChange = { picking = it; if (!it) onChange(null, "", 0) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink.Void,
                    checkedTrackColor = Ink.Brass,
                    uncheckedThumbColor = Ink.Slate,
                    uncheckedTrackColor = Ink.Raised,
                    uncheckedBorderColor = Ink.Hairline
                )
            )
        }

        if ((on || picking) && enabled) {
            Spacer(Modifier.height(16.dp))
            if (gateApp == null || picking) {
                Text("WHICH APP FIRST", style = Eyebrow, color = Ink.Dim)
                Spacer(Modifier.height(10.dp))
                Field(query, { query = it }, "Search apps")
                Spacer(Modifier.height(8.dp))
                val matches = apps.filter { it.second.contains(query, true) }
                Column {
                    matches.take(6).forEach { (pkg, label) ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChange(pkg, label, if (gateMinutes > 0) gateMinutes else 10)
                                    picking = false
                                    query = ""
                                }
                                .padding(vertical = 12.dp)
                        )
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))
                    }
                    if (matches.size > 6) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${matches.size - 6} more — type to narrow it down.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.Dim
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(gateLabel, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "CHANGE",
                        style = Eyebrow,
                        color = Ink.Brass,
                        modifier = Modifier.clickable { picking = true }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("MINUTES REQUIRED", style = Eyebrow, color = Ink.Dim)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 10, 15, 20, 30).forEach { m ->
                        val sel = gateMinutes == m
                        Text(
                            "$m min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (sel) Ink.Void else Ink.Bone,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) Ink.Brass else Ink.Raised)
                                .clickable { onChange(gateApp, gateLabel, m) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("OR TYPE ANY NUMBER OF MINUTES", style = Eyebrow, color = Ink.Dim)
                Spacer(Modifier.height(8.dp))
                Field(
                    value = if (gateMinutes == 0) "" else gateMinutes.toString(),
                    onChange = { raw ->
                        val n = raw.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0
                        onChange(gateApp, gateLabel, n)
                    },
                    hint = "e.g. 12",
                    numeric = true
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (gateMinutes > 0)
                        "Blocked until you've spent $gateMinutes minutes in $gateLabel today."
                    else "Set how many minutes in $gateLabel are needed first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.Dim
                )
            }
        }
    }
}

@Composable
private fun AskEachTimeToggle(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                "Ask how long each time",
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Ink.Bone else Ink.Dim
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (enabled)
                    "Instead of handing over the whole daily allowance at once, Anchor asks how " +
                        "much of it you want now. There's a minute's wait between stretches."
                else
                    "Set a daily limit above and Anchor can portion it out rather than giving " +
                        "you the lot in one go.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Ink.Slate else Ink.Dim
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked && enabled,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.Void,
                checkedTrackColor = Ink.Brass,
                uncheckedThumbColor = Ink.Slate,
                uncheckedTrackColor = Ink.Raised,
                uncheckedBorderColor = Ink.Hairline,
                disabledUncheckedThumbColor = Ink.Dim,
                disabledUncheckedTrackColor = Ink.Surface,
                disabledUncheckedBorderColor = Ink.Hairline
            )
        )
    }
}

@Composable
private fun PrivateToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text("Keep this private", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Kept out of the main list and never named on the block screen. Still enforced " +
                    "exactly the same.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.Void,
                checkedTrackColor = Ink.Brass,
                uncheckedThumbColor = Ink.Slate,
                uncheckedTrackColor = Ink.Raised,
                uncheckedBorderColor = Ink.Hairline
            )
        )
    }
}

@Composable
private fun LimitPicker(value: String, onChange: (String) -> Unit) {
    val quick = listOf("0", "5", "10", "15", "30", "60")

    Column {
        Text("DAILY LIMIT", style = Eyebrow, color = Ink.Dim)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quick.forEach { v ->
                val on = value == v
                Text(
                    if (v == "0") "None" else "$v min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (on) Ink.Void else Ink.Bone,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) Ink.Brass else Ink.Raised)
                        .clickable { onChange(v) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("OR TYPE ANY NUMBER OF MINUTES", style = Eyebrow, color = Ink.Dim)
        Spacer(Modifier.height(8.dp))
        Field(
            value = if (value == "0") "" else value,
            onChange = { raw -> onChange(raw.filter { it.isDigit() }.take(4).ifEmpty { "0" }) },
            hint = "e.g. 10",
            numeric = true
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (value == "0" || value.isEmpty()) "Blocked every time you open it."
            else "$value minutes a day, then blocked until tomorrow.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Dim
        )
    }
}

@Composable
internal fun Field(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    numeric: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, color = Ink.Dim) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Text
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink.Brass,
            unfocusedBorderColor = Ink.Hairline,
            focusedTextColor = Ink.Bone,
            unfocusedTextColor = Ink.Bone,
            cursorColor = Ink.Brass
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun PrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Ink.Brass else Ink.Raised)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label.uppercase(), style = Eyebrow, color = if (enabled) Ink.Void else Ink.Dim)
    }
}

private fun dayLabel(days: Set<Int>): String = when {
    days.isEmpty() || days.size == 7 -> "daily"
    days == setOf(1, 2, 3, 4, 5) -> "weekdays"
    days == setOf(6, 7) -> "weekends"
    else -> days.sorted().joinToString("") { "MTWTFSS"[it - 1].toString() }
}

// ---------- helpers ----------

private fun launchableApps(ctx: Context): List<Pair<String, String>> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == ctx.packageName) return@mapNotNull null
            pkg to ri.loadLabel(pm).toString()
        }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}

internal fun isServiceEnabled(ctx: Context): Boolean {
    if (BlockerService.running) return true
    val expected = ComponentName(ctx, BlockerService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
