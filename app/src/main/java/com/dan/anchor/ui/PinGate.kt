package com.dan.anchor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dan.anchor.data.Prefs

/**
 * Anything that makes Anchor *less* restrictive goes through here.
 *
 * The rule is deliberately one-sided: tightening a block costs you nothing,
 * loosening one costs you the PIN. Adding reddit.com to the list is free.
 * Taking it off, stretching its daily limit, or switching strict mode off
 * means typing the PIN — and if strict mode is armed, waiting out the
 * cooldown first as well.
 */
class PinGate internal constructor(
    private val prefs: Prefs,
    private val show: (Pending) -> Unit
) {
    internal data class Pending(val reason: String, val action: () -> Unit)

    /**
     * @param reason shown in the dialog so you know what you're unlocking.
     */
    /**
     * For the one action that must work *while* strict mode is armed: starting
     * the cooldown itself.
     *
     * Routing this through [loosen] created a deadlock — the button that begins
     * the wait was refused because the wait hadn't begun. It still costs the
     * PIN; it just doesn't ask the clock for permission to start the clock.
     */
    fun requirePin(reason: String, action: () -> Unit) {
        if (!prefs.hasPin) {
            action()
            return
        }
        show(Pending(reason, action))
    }

    fun loosen(reason: String, action: () -> Unit) {
        // Strict mode's cooldown comes first — no PIN gets you past a running clock.
        if (prefs.strictMode.value && !prefs.isEditable()) {
            show(Pending(LOCKED, action = {}))
            return
        }
        if (!prefs.hasPin) {
            action()
            return
        }
        show(Pending(reason, action))
    }

    companion object {
        internal const val LOCKED = "__locked__"
    }
}

/**
 * Call once per screen. Returns a gate you can wrap actions in, and renders
 * the prompt when one fires.
 */
@Composable
fun rememberPinGate(): PinGate {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    var pending by remember { mutableStateOf<PinGate.Pending?>(null) }
    val gate = remember { PinGate(prefs) { pending = it } }

    pending?.let { p ->
        if (p.reason == PinGate.LOCKED) {
            AlertDialog(
                onDismissRequest = { pending = null },
                containerColor = Ink.Surface,
                title = { Text("Still locked", style = MaterialTheme.typography.titleMedium) },
                text = {
                    Text(
                        when {
                            prefs.daysLockActive() ->
                                "Strict mode is locked for a set number of days. Nothing loosens " +
                                    "until it's over — that's what you chose it for."
                            prefs.unlockAt() > 0 ->
                                "The cooldown is still running. Nothing opens until it finishes — " +
                                    "not even with the PIN."
                            else ->
                                "Strict mode is armed. Start the cooldown in Settings — it needs no " +
                                    "PIN — then come back when it's done."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = { pending = null }) { Text("Fair enough", color = Ink.Brass) }
                }
            )
        } else {
            EnterPinDialog(
                reason = p.reason,
                check = { prefs.checkPin(it) },
                onPass = { pending = null; p.action() },
                onDismiss = { pending = null }
            )
        }
    }

    return gate
}

@Composable
private fun EnterPinDialog(
    reason: String,
    check: (String) -> Boolean,
    onPass: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        title = { Text("Enter your PIN", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(reason, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Field(pin, { pin = it; error = null }, "PIN", numeric = true)
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink.Rust)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= 4,
                onClick = {
                    if (check(pin)) onPass() else {
                        attempts += 1
                        pin = ""
                        error = if (attempts >= 3) "Still wrong. Worth stopping here."
                        else "That's not the PIN."
                    }
                }
            ) { Text("Unlock", color = Ink.Brass) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ink.Slate) }
        }
    )
}

/** Set or change the PIN. Changing it always requires the current one. */
@Composable
internal fun SetPinDialog(
    requireOld: Boolean,
    check: (String) -> Boolean,
    onSet: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        title = {
            Text(
                if (requireOld) "Change PIN" else "Set a PIN",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    "A PIN you know will be of no use — have someone you trust set the PIN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.Brass
                )
                Spacer(Modifier.height(16.dp))
                if (requireOld) {
                    Field(old, { old = it; error = null }, "Current PIN", numeric = true)
                    Spacer(Modifier.height(10.dp))
                }
                Field(new, { new = it; error = null }, "New PIN — 4 digits or more", numeric = true)
                Spacer(Modifier.height(10.dp))
                Field(again, { again = it; error = null }, "Confirm", numeric = true)
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink.Rust)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    requireOld && !check(old) -> "That's not the current PIN."
                    new.length < 4 -> "Use at least four digits. Longer is better."
                    new != again -> "The two new PINs don't match."
                    else -> null
                }
                if (error == null) onSet(new)
            }) { Text("Save", color = Ink.Brass) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Ink.Slate) }
        }
    )
}
