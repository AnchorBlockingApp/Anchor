package com.dan.anchor.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    var pinDialog by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }

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
            "With it on, you enter your PIN to start a timer, then have to wait for that timer to " +
                "run out before the change is allowed. Adding a block is unaffected either way.",
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
                Chips(
                    options = listOf(15 to "15 min", 60 to "1 hour", 240 to "4 hours", 1440 to "24 hours"),
                    selected = cooldown
                ) { cooldown = it; prefs.cooldownMinutes = it }
                Spacer(Modifier.height(16.dp))
                PrimaryAction("Arm strict mode") { prefs.enableStrict(cooldown) }
            }
        } else {
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
                when {
                    at == 0L -> {
                        Text("Armed. Rules are frozen.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(14.dp))
                        PrimaryAction("Start the ${humanMinutes(cooldown)} wait") {
                            gate.loosen("Starting the wait that unlocks your rules.") {
                                prefs.requestUnlock()
                            }
                        }
                    }
                    remaining > 0 -> {
                        Text("Unlocking in", style = MaterialTheme.typography.bodyMedium, color = Ink.Dim)
                        Spacer(Modifier.height(6.dp))
                        Text(clock(remaining), style = ScriptureLarge, color = Ink.Brass)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "CANCEL AND STAY LOCKED",
                            style = Eyebrow, color = Ink.Slate,
                            modifier = Modifier.clickable { prefs.cancelUnlockRequest() }
                        )
                    }
                    else -> {
                        Text("Unlocked. Rules can be edited.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(14.dp))
                        PrimaryAction("Turn strict mode off") {
                            gate.loosen("Switching strict mode off entirely.") { confirmDisable = true }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "RE-ARM NOW",
                            style = Eyebrow, color = Ink.Brass,
                            modifier = Modifier.clickable { prefs.enableStrict(cooldown) }
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

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            containerColor = Ink.Surface,
            title = { Text("Turn strict mode off?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "Your rules stay, but nothing stops you changing them on the spot any more.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { prefs.disableStrict(); confirmDisable = false }) {
                    Text("Turn it off", color = Ink.Rust)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) {
                    Text("Keep it on", color = Ink.Brass)
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
