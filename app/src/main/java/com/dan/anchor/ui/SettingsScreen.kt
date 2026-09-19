package com.dan.anchor.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dan.anchor.block.AdminReceiver
import com.dan.anchor.block.BlockerService
import com.dan.anchor.block.Watch
import com.dan.anchor.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val strict by prefs.strictMode.collectAsState()
    val gate = rememberPinGate()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    var adult by remember { mutableStateOf(prefs.adultFilterOn) }
    var pause by remember { mutableIntStateOf(prefs.pauseSeconds) }
    var cooldown by remember { mutableIntStateOf(prefs.cooldownMinutes) }
    var importCount by remember { mutableIntStateOf(prefs.importedDomains.size) }
    var importing by remember { mutableStateOf(false) }
    var importNote by remember { mutableStateOf<String?>(null) }

    var adminOn by remember { mutableStateOf(false) }
    var adminNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(now) { adminOn = AdminReceiver.isActive(ctx) }

    // Launching for a result means a silent failure stops being silent — we find
    // out whether the approval screen was cancelled or never really opened.
    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        adminOn = AdminReceiver.isActive(ctx)
        adminNote = if (adminOn) null else {
            if (!AdminReceiver.receiverVisible(ctx)) {
                "Android can't see Anchor's admin component. Reinstall the app, or use the manual " +
                    "route below."
            } else {
                "The approval screen closed without switching it on. Either it was cancelled, or " +
                    "your phone blocks this shortcut — try the manual route below."
            }
        }
    }

    var emgQuota by remember { mutableIntStateOf(prefs.emergencyQuota) }
    var emgMinutes by remember { mutableIntStateOf(prefs.emergencyMinutes) }
    var warnings by remember { mutableStateOf(prefs.warningsOn) }
    var resetDialog by remember { mutableStateOf(false) }
    var lockDays by remember { mutableStateOf("") }
    var confirmDaysLock by remember { mutableStateOf(false) }

    var pinDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 40.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(28.dp))

        // Only worth saying while there's still a decision to make.
        if (!prefs.hasPin) {
            Callout(
                title = "BEFORE YOU SET YOUR PIN",
                body = "A PIN you know will be of no use — have someone you trust set the PIN."
            )
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))
            Spacer(Modifier.height(28.dp))
        }

        // ---- strict mode ----
        Section("STRICT MODE")
        Text(
            "Without strict mode, your PIN is enough to remove a block straight away.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "With it on, you start a timer instead, and have to wait for it to run out before any " +
                "change is allowed. Starting the timer needs no PIN — the wait is the point. The " +
                "PIN is still needed for the change itself. Adding a block is unaffected either way.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Highly recommended if you know your own PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Brass
        )
        Spacer(Modifier.height(16.dp))

        if (!strict) {
            if (!prefs.hasPin) {
                Text(
                    "Set a PIN first — it stops a casual tap from undoing the wait.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink.Dim
                )
                Spacer(Modifier.height(12.dp))
                PrimaryAction("Set a PIN") { pinDialog = true }
            } else {
                Text("COOLDOWN LENGTH", style = Eyebrow, color = Ink.Dim)
                Spacer(Modifier.height(10.dp))
                var daysMode by remember { mutableStateOf(false) }

                Text("HOW LONG BEFORE A CHANGE IS ALLOWED", style = Eyebrow, color = Ink.Dim)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15 to "15 min", 60 to "1 hour", 240 to "4 hours", 1440 to "24 hours")
                        .forEach { (v, lab) ->
                            val on = !daysMode && cooldown == v
                            Text(
                                lab,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (on) Ink.Void else Ink.Bone,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (on) Ink.Brass else Ink.Raised)
                                    .clickable {
                                        daysMode = false; lockDays = ""
                                        cooldown = v; prefs.cooldownMinutes = v
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    Text(
                        "X days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (daysMode) Ink.Void else Ink.Bone,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (daysMode) Ink.Brass else Ink.Raised)
                            .clickable { daysMode = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                if (daysMode) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Strict mode stays on for this many days, and nothing loosens it in the " +
                            "meantime — not a cooldown, not your PIN.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Field(
                        value = lockDays,
                        onChange = { raw -> lockDays = raw.filter { it.isDigit() }.take(3) },
                        hint = "e.g. 7",
                        numeric = true
                    )
                }

                Spacer(Modifier.height(16.dp))
                val days = if (daysMode) lockDays.toIntOrNull() ?: 0 else 0
                PrimaryAction(
                    if (days > 0) "Arm for $days day${if (days == 1) "" else "s"}" else "Arm strict mode",
                    enabled = !daysMode || days > 0
                ) {
                    if (days > 0) confirmDaysLock = true else prefs.enableStrict(cooldown)
                }
            }
        } else {
            val at = prefs.unlockAt()
            val remaining = if (at > 0) ((at - now) / 1000).coerceAtLeast(0) else -1L
            val lockLeft = prefs.daysLockRemaining()
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.Surface)
                    .border(1.dp, Ink.Hairline, RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                when {
                    lockLeft > 0L -> {
                        Text("LOCKED FOR A SET TIME", style = Eyebrow, color = Ink.Brass)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Nothing can be loosened until ${endsOn(prefs.strictUntil())}.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${daysHours(lockLeft)} to go. You chose this — see it through.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.Dim
                        )
                    }
                    at == 0L -> {
                        Text("Armed. Rules are frozen.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(14.dp))
                        // No PIN here — the wait is the price, and this starts it.
                        PrimaryAction("Start the ${humanMinutes(cooldown)} wait") {
                            prefs.requestUnlock()
                        }
                    }
                    else -> {
                        Text("Unlocking in", style = MaterialTheme.typography.bodyMedium, color = Ink.Dim)
                        Spacer(Modifier.height(6.dp))
                        Text(clock(remaining), style = ScriptureLarge, color = Ink.Brass)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Strict mode switches itself off when this reaches zero.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink.Dim
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "CANCEL AND STAY LOCKED",
                            style = Eyebrow, color = Ink.Slate,
                            modifier = Modifier.clickable {
                                gate.requirePin("Cancelling the wait and staying locked.") {
                                    prefs.cancelUnlockRequest()
                                }
                            }
                        )
                    }
                }
            }
        }

        Divider24()

        // ---- adult filter ----
        Section("ADULT SITE FILTER")
        ToggleRow(
            label = "Filter adult sites everywhere",
            body = "Catches known adult domains in any browser Anchor can read, plus obvious " +
                "search terms. Works alongside your own list.",
            checked = adult
        ) { on ->
            if (on) {
                adult = true; prefs.adultFilterOn = true
            } else {
                gate.loosen("Switching the adult site filter off.") {
                    adult = false; prefs.adultFilterOn = false
                }
            }
        }

        if (adult) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (importCount > 0) "$importCount extra domains imported."
                else "The built-in list is short. Import a maintained public blocklist for much " +
                    "wider coverage — about 4 MB, one-off download.",
                style = MaterialTheme.typography.bodyMedium, color = Ink.Dim
            )
            Spacer(Modifier.height(12.dp))
            PrimaryAction(
                if (importing) "Importing…" else if (importCount > 0) "Refresh blocklist" else "Import blocklist",
                enabled = !importing
            ) {
                importing = true; importNote = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { importBlocklist() }
                    result.onSuccess {
                        prefs.importedDomains = it
                        importCount = it.size
                        importNote = "Imported ${it.size} domains."
                    }.onFailure {
                        importNote = "Couldn't download the list. Check your connection and try again."
                    }
                    importing = false
                }
            }
            importNote?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink.Brass)
            }
        }

        Divider24()

        // ---- uninstall protection ----
        Section("UNINSTALL PROTECTION")
        Text(
            "When uninstall protection is on, long-pressing the app icon or hitting Uninstall in " +
                "Settings won't work. To uninstall Anchor, first turn uninstall protection off " +
                "here by entering your PIN.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "It works by registering Anchor as a device administrator, so that's the wording " +
                "Android will use when it asks you to approve it.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Dim
        )
        Spacer(Modifier.height(16.dp))

        when {
            adminOn -> {
                Text("Protection is on.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(14.dp))
                PrimaryAction("Turn uninstall protection off") {
                    gate.loosen("Allowing Anchor to be uninstalled again.") {
                        AdminReceiver.disable(ctx)
                        adminOn = false
                        adminNote = null
                    }
                }
            }
            !prefs.hasPin -> {
                Text(
                    "Set a PIN first. Without one, turning this protection off costs nothing, so " +
                        "it wouldn't stop anybody.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink.Dim
                )
                Spacer(Modifier.height(12.dp))
                PrimaryAction("Set a PIN") { pinDialog = true }
            }
            else -> {
                PrimaryAction("Turn on uninstall protection") {
                    adminNote = null
                    prefs.allowSettingsBriefly()
                    runCatching { adminLauncher.launch(AdminReceiver.enableIntent(ctx)) }
                        .onFailure {
                            adminNote = "This phone wouldn't open the approval screen " +
                                "(${it::class.java.simpleName}). Use the manual route below."
                        }
                }
                adminNote?.let { note ->
                    Spacer(Modifier.height(14.dp))
                    Text(note, style = MaterialTheme.typography.bodyMedium, color = Ink.Rust)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "OPEN SECURITY SETTINGS",
                        style = Eyebrow,
                        color = Ink.Brass,
                        modifier = Modifier.clickable {
                            prefs.allowSettingsBriefly()
                            runCatching {
                                ctx.startActivity(
                                    AdminReceiver.settingsListIntent()
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Look for Device admin apps — on Samsung it's under Other security " +
                            "settings — then switch Anchor on there.",
                        style = MaterialTheme.typography.bodyMedium, color = Ink.Dim
                    )
                    Spacer(Modifier.height(14.dp))
                    Detail("Component visible", if (AdminReceiver.receiverVisible(ctx)) "yes" else "no")
                    Detail("Currently active", if (adminOn) "yes" else "no")
                }
            }
        }

        Divider24()

        // ---- emergency extensions ----
        Section("EMERGENCY EXTENSIONS")
        Text(
            "Sometimes an allowance runs out mid-conversation and whoever holds your PIN isn't " +
                "around. Rather than leave you stuck, Anchor gives you a small number of " +
                "extensions each week that don't need the PIN — just a short wait.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "The limit is the point. A couple a week gets spent on things that matter; it " +
                "doesn't work as a daily habit.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Dim
        )
        Spacer(Modifier.height(16.dp))
        Text("HOW MANY A WEEK", style = Eyebrow, color = Ink.Dim)
        Spacer(Modifier.height(10.dp))
        Chips(
            options = listOf(0 to "None", 1 to "1", 2 to "2", 3 to "3"),
            selected = emgQuota
        ) { picked ->
            if (picked <= emgQuota) {
                emgQuota = picked; prefs.emergencyQuota = picked
            } else {
                gate.loosen("Allowing more emergency extensions each week.") {
                    emgQuota = picked; prefs.emergencyQuota = picked
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("HOW LONG EACH", style = Eyebrow, color = Ink.Dim)
        Spacer(Modifier.height(10.dp))
        Chips(
            options = listOf(5 to "5 min", 10 to "10 min", 15 to "15 min", 30 to "30 min"),
            selected = emgMinutes
        ) { picked ->
            if (picked <= emgMinutes) {
                emgMinutes = picked; prefs.emergencyMinutes = picked
            } else {
                gate.loosen("Making each emergency extension longer.") {
                    emgMinutes = picked; prefs.emergencyMinutes = picked
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${prefs.emergenciesLeft()} of $emgQuota left this week.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Brass
        )

        Divider24()

        // ---- warnings ----
        Section("TIME WARNINGS")
        ToggleRow(
            label = "Warn me before time runs out",
            body = "A brief note at the bottom of the screen about a minute before an allowance " +
                "ends, and five minutes before on limits of ten minutes or more.",
            checked = warnings
        ) { warnings = it; prefs.warningsOn = it }

        Divider24()

        // ---- pause ----
        Section("THE PAUSE")
        Text(
            "How long the block screen holds you before the way out appears. Long enough to " +
                "actually read the verse is the point.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(14.dp))
        Chips(
            options = listOf(0 to "None", 5 to "5 sec", 8 to "8 sec", 20 to "20 sec", 60 to "1 min"),
            selected = pause
        ) { picked ->
            if (picked >= pause) {
                pause = picked; prefs.pauseSeconds = picked
            } else {
                gate.loosen("Making the pause shorter than it is now.") {
                    pause = picked; prefs.pauseSeconds = picked
                }
            }
        }

        Divider24()

        // ---- pin ----
        Section("PIN")
        Text(
            if (prefs.hasPin) "A PIN is set." else "No PIN set yet.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            (if (prefs.hasPin) "CHANGE PIN" else "SET PIN"),
            style = Eyebrow,
            modifier = Modifier.clickable { pinDialog = true }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Four digits minimum, no maximum — longer is better.",
            style = MaterialTheme.typography.bodyMedium, color = Ink.Dim
        )

        if (prefs.hasPin) {
            Spacer(Modifier.height(20.dp))
            val resetAt = prefs.pinResetAt()
            val remaining = if (resetAt > 0) ((resetAt - now) / 1000).coerceAtLeast(0) else -1L
            when {
                resetAt == 0L -> Text(
                    "FORGOTTEN THE PIN?",
                    style = Eyebrow,
                    color = Ink.Slate,
                    modifier = Modifier.clickable { resetDialog = true }
                )
                remaining > 0 -> Column {
                    Text("PIN RESET REQUESTED", style = Eyebrow, color = Ink.Rust)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your PIN clears in ${days(remaining)}. Everything stays locked until then.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "CANCEL THE RESET",
                        style = Eyebrow,
                        color = Ink.Brass,
                        modifier = Modifier.clickable { prefs.cancelPinReset() }
                    )
                }
                else -> Column {
                    Text("THE WAIT IS OVER", style = Eyebrow, color = Ink.Brass)
                    Spacer(Modifier.height(10.dp))
                    PrimaryAction("Clear the PIN and set a new one") {
                        prefs.clearPin()
                        pinDialog = true
                    }
                }
            }
        }

        Divider24()

        Section("WHAT ANCHOR IS SEEING")
        Text(
            "Diagnostics, for when something isn't being blocked and you want to know why. " +
                "Clears itself after a minute or two.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(14.dp))
        LiveCard(now = now)

        Divider24()

        Section("ABOUT")
        Text(
            "Anchor keeps everything on this phone. No account, no sync, nothing uploaded. " +
                "Scripture is the World English Bible, which is public domain.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "${prefs.blockCount} blocks so far.",
            style = MaterialTheme.typography.bodyMedium, color = Ink.Dim
        )
    }

    if (pinDialog) {
        SetPinDialog(
            requireOld = prefs.hasPin,
            check = { prefs.checkPin(it) },
            onSet = { prefs.setPin(it); pinDialog = false },
            onDismiss = { pinDialog = false }
        )
    }

    if (confirmDaysLock) {
        val days = lockDays.toIntOrNull() ?: 0
        val until = System.currentTimeMillis() + days * 24L * 60 * 60 * 1000
        AlertDialog(
            onDismissRequest = { confirmDaysLock = false },
            containerColor = Ink.Surface,
            title = { Text("Are you sure?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        "You will not be able to loosen any of your settings for $days " +
                            "day${if (days == 1) "" else "s"}, even if you have your PIN.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(12.dp))
                    // The date is the real check on a typo — 7 and 70 look alike
                    // in a text field, but next Tuesday and next December don't.
                    Text(
                        "That's until ${endsOn(until)}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink.Brass
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Adding new blocks still works, and your emergency extensions are " +
                            "untouched. It's only loosening that's off the table.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "If this is what you want — well done. God bless, and good luck.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.Dim
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.enableStrictForDays(days, cooldown)
                    lockDays = ""
                    confirmDaysLock = false
                }) { Text("Yes, arm", color = Ink.Brass) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDaysLock = false }) {
                    Text("Cancel", color = Ink.Slate)
                }
            }
        )
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            containerColor = Ink.Surface,
            title = { Text("Reset the PIN?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "This takes seven days, and it can't be shortened. Nothing unlocks in the " +
                        "meantime — the wait is what stops this being a way around your own " +
                        "blocks. You can cancel it at any point.\n\nIf whoever holds your PIN is " +
                        "reachable, asking them is faster.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { prefs.startPinReset(); resetDialog = false }) {
                    Text("Start the seven days", color = Ink.Rust)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetDialog = false }) {
                    Text("Cancel", color = Ink.Brass)
                }
            }
        )
    }

    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            containerColor = Ink.Surface,
            title = { Text("Reset the PIN?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "This takes seven days, and it can't be shortened. Nothing unlocks in the " +
                        "meantime — the wait is what stops this being a way around your own " +
                        "blocks. You can cancel it at any point.\n\nIf whoever holds your PIN is " +
                        "reachable, asking them is faster.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { prefs.startPinReset(); resetDialog = false }) {
                    Text("Start the seven days", color = Ink.Rust)
                }
            },
            dismissButton = {
                TextButton(onClick = { resetDialog = false }) {
                    Text("Cancel", color = Ink.Brass)
                }
            }
        )
    }
}

// ---------- pieces ----------

/**
 * Reading a browser's address bar depends on internals that differ between
 * browser versions and phone makers. When a site doesn't get blocked, this says
 * whether the service is running, whether events are arriving, and whether the
 * address was readable — which is the difference between a bug and an
 * unsupported browser.
 */
@Composable
private fun LiveCard(now: Long) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var serviceOn by remember { mutableStateOf(false) }
    LaunchedEffect(now) { serviceOn = isServiceEnabled(ctx) }
    val quiet = Watch.lastEventAt == 0L || now - Watch.lastEventAt > 10_000L

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(14.dp))
            .clickable { open = !open }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            !serviceOn -> Ink.Rust
                            quiet -> Ink.Slate
                            else -> Ink.Brass
                        }
                    )
            )
            Spacer(Modifier.width(12.dp))
            Text(
                when {
                    !serviceOn -> "NOT RUNNING"
                    quiet -> "RUNNING, NOTHING SEEN YET"
                    else -> "WATCHING"
                },
                style = Eyebrow,
                color = if (serviceOn) Ink.Bone else Ink.Rust
            )
            Spacer(Modifier.weight(1f))
            Text(if (open) "HIDE" else "DETAILS", style = Eyebrow, color = Ink.Dim)
        }

        if (open) {
            Spacer(Modifier.height(16.dp))
            Detail("Events seen", if (Watch.eventCount == 0L) "none" else "${Watch.eventCount}")
            Detail("App in front", Watch.packageForDisplay().ifBlank { "-" })
            Detail(
                "Address read",
                if (Watch.urlForDisplay().isBlank()) "-"
                else "${Watch.urlForDisplay()}  (${Watch.sourceForDisplay()})"
            )
            Detail("Last decision", Watch.decisionForDisplay().ifBlank { "-" })
            Spacer(Modifier.height(14.dp))
            Text(
                "Open a browser, go to a blocked site, then come back here. If the app in front " +
                    "never shows your browser, the service isn't getting events. If it shows the " +
                    "browser but no address, Anchor can't read that browser's address bar — block " +
                    "the browser itself instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.Dim
            )
        }
    }
}

@Composable
internal fun Callout(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.Surface)
            .border(1.dp, Ink.BrassDim, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Text(title, style = Eyebrow)
        Spacer(Modifier.height(10.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = Eyebrow)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun Divider24() {
    Spacer(Modifier.height(28.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium, color = Ink.Dim,
            modifier = Modifier.width(140.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink.Bone)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    body: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
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
private fun Chips(
    options: List<Pair<Int, String>>,
    selected: Int,
    enabled: Boolean = true,
    onPick: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !enabled -> Ink.Dim
                    on -> Ink.Void
                    else -> Ink.Bone
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on && enabled) Ink.Brass else Ink.Raised)
                    .clickable(enabled = enabled) { onPick(v) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

// ---------- helpers ----------

private fun clock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "Tuesday, 22 September" — a date a typo can't hide behind. */
private fun endsOn(millis: Long): String =
    java.text.SimpleDateFormat("EEEE, d MMMM yyyy 'at' h:mma", java.util.Locale.getDefault())
        .format(java.util.Date(millis))

private fun daysHours(millis: Long): String {
    val totalHours = millis / 3_600_000L
    val d = totalHours / 24
    val h = totalHours % 24
    return when {
        d > 0 -> "$d day${if (d == 1L) "" else "s"}, $h hour${if (h == 1L) "" else "s"}"
        h > 0 -> "$h hour${if (h == 1L) "" else "s"}"
        else -> "under an hour"
    }
}

private fun days(seconds: Long): String {
    val d = seconds / 86_400
    val h = (seconds % 86_400) / 3600
    return when {
        d > 0 -> "$d day${if (d == 1L) "" else "s"}, $h hour${if (h == 1L) "" else "s"}"
        h > 0 -> "$h hour${if (h == 1L) "" else "s"}"
        else -> "${(seconds / 60).coerceAtLeast(1)} minutes"
    }
}

private fun humanMinutes(m: Int) = when {
    m >= 1440 -> "${m / 1440} day"
    m >= 60 -> "${m / 60} hour"
    else -> "$m minute"
}

/**
 * Pulls the porn-only host list from the StevenBlack hosts project — a
 * long-running, community-maintained blocklist. Only the hostnames are kept.
 */
private fun importBlocklist(): Result<Set<String>> = runCatching {
    val url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"
    val out = HashSet<String>(120_000)
    URL(url).openStream().bufferedReader().useLines { lines ->
        for (line in lines) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith('#')) continue
            val parts = t.split(Regex("\\s+"))
            if (parts.size < 2) continue
            val host = parts[1].lowercase().removePrefix("www.")
            if (host == "localhost" || host == "0.0.0.0" || !host.contains('.')) continue
            out += host
        }
    }
    out
}
