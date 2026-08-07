package com.dan.anchor.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dan.anchor.block.BlockRules
import com.dan.anchor.block.BlockerService
import com.dan.anchor.block.Watch
import com.dan.anchor.data.Prefs
import com.dan.anchor.data.Rule
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
    LaunchedEffect(Unit) {
        while (true) {
            serviceOn = isServiceEnabled(ctx)
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    var sheet by remember { mutableStateOf<Sheet?>(null) }

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

        if (moreTime || unblocking) {
            val what = if (previous.hidden) "a private block" else previous.label
            gate.loosen("Giving $what more time than it had.") {
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
            Text("BLOCKS", style = Eyebrow)
            Spacer(Modifier.height(8.dp))
            Text("Anchor", style = MaterialTheme.typography.displaySmall)
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
                ) {
                    prefs.allowSettingsBriefly()
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
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
            LiveCard(now = now, serviceOn = serviceOn)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AddButton("App", Modifier.weight(1f)) { sheet = Sheet.App(null) }
                AddButton("Website", Modifier.weight(1f)) { sheet = Sheet.Site(null) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    !editable ->
                        "Adding a block still works. Removing one, or giving it more time, needs " +
                            "the cooldown to finish first."
                    prefs.hasPin ->
                        "Tap a rule to change it. Adding a block is free; removing one, or giving " +
                            "it more time, asks for your PIN."
                    else ->
                        "Tap a rule to change it. Set a PIN in Settings and removing a block will " +
                            "start asking for it."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.Dim
            )
        }
    }

    when (val sh = sheet) {
        is Sheet.App -> AppSheet(
            existing = sh.rule,
            onDismiss = { sheet = null }
        ) { pkg, label, mins, priv ->
            save(Rule(pkg, label, isApp = true, limitMinutes = mins, hidden = priv), sh.rule)
            sheet = null
        }
        is Sheet.Site -> SiteSheet(
            existing = sh.rule,
            onDismiss = { sheet = null }
        ) { host, mins, priv ->
            save(Rule(host, host, isApp = false, limitMinutes = mins, hidden = priv), sh.rule)
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
        Text("STRICT MODE ARMED", style = Eyebrow)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                at == 0L -> "Rules can't be loosened. Ask for a change in Settings to start the " +
                    "${prefs.cooldownMinutes} minute wait."
                remaining > 0 -> "Unlocking in ${fmt(remaining)}. You can cancel and stay locked."
                else -> "Unlocked. Make your change — arming strict mode again re-locks everything."
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

/**
 * Shows what the service is seeing right now. Reading a browser's address bar
 * depends on internals that differ between browser versions and phone makers,
 * so when a site doesn't get blocked this says whether the service is running,
 * whether events are arriving, and whether the address was readable.
 */
@Composable
private fun LiveCard(now: Long, serviceOn: Boolean) {
    var open by remember { mutableStateOf(false) }
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
            Detail("App in front", Watch.lastPackage.ifBlank { "-" })
            Detail(
                "Address read",
                if (Watch.lastUrl.isBlank()) "-" else "${Watch.lastUrl}  (${Watch.lastUrlSource})"
            )
            Detail("Last decision", Watch.lastDecision.ifBlank { "-" })
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
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium, color = Ink.Dim,
            modifier = Modifier.width(110.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Ink.Bone)
    }
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
                if (rule.isHardBlock) {
                    if (rule.isApp) "App · always blocked" else "Site · always blocked"
                } else {
                    "${if (rule.isApp) "App" else "Site"} · $usedMinutes of ${rule.limitMinutes} min used today"
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
    onSave: (String, String, Int, Boolean) -> Unit
) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember {
        mutableStateOf(existing?.let { it.target to it.label })
    }
    var minutes by remember { mutableStateOf(existing?.limitMinutes?.toString() ?: "0") }
    var priv by remember { mutableStateOf(existing?.hidden ?: false) }

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
                Text(selected!!.second, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                LimitPicker(minutes) { minutes = it }
                Spacer(Modifier.height(20.dp))
                PrivateToggle(priv) { priv = it }
                Spacer(Modifier.height(24.dp))
                PrimaryAction(if (existing != null) "Save changes" else "Add block") {
                    onSave(selected!!.first, selected!!.second, minutes.toIntOrNull() ?: 0, priv)
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
    onSave: (String, Int, Boolean) -> Unit
) {
    var host by remember { mutableStateOf(existing?.target ?: "") }
    var minutes by remember { mutableStateOf(existing?.limitMinutes?.toString() ?: "0") }
    var priv by remember { mutableStateOf(existing?.hidden ?: false) }
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
            Spacer(Modifier.height(20.dp))
            PrivateToggle(priv) { priv = it }
            Spacer(Modifier.height(24.dp))
            PrimaryAction(
                if (existing != null) "Save changes" else "Add block",
                enabled = clean.contains('.')
            ) {
                onSave(clean, minutes.toIntOrNull() ?: 0, priv)
            }
        }
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
