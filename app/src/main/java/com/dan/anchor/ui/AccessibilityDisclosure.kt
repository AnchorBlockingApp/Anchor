package com.dan.anchor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Shown before Anchor sends anyone to the accessibility settings.
 *
 * Google Play requires a prominent in-app disclosure ahead of requesting
 * accessibility access: what is accessed, why, and an explicit yes. It also has
 * to be visible in the demo video submitted with the permission declaration.
 *
 * It's the right thing to show regardless. Accessibility access sounds alarming
 * because it is powerful, and someone about to grant it deserves a plain account
 * of what the app does with it before Android's own warning appears.
 */
@Composable
fun AccessibilityDisclosure(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Ink.Surface)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("BEFORE YOU CONTINUE", style = Eyebrow)
            Spacer(Modifier.height(14.dp))

            Text(
                "Anchor needs Android's accessibility permission to do the one thing it does.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))

            Text("WHAT IT READS", style = Eyebrow, color = Ink.Dim)
            Spacer(Modifier.height(8.dp))
            Text(
                "Which app is currently open, and the web address in your browser's address bar. " +
                    "That's what it checks against your block list.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            Text("WHAT IT DOESN'T DO", style = Eyebrow, color = Ink.Dim)
            Spacer(Modifier.height(8.dp))
            Text(
                "It doesn't read your messages, passwords, or anything you type. It doesn't keep " +
                    "a history of the sites you visit. Nothing is uploaded, there's no account, " +
                    "and there is no server for it to be sent to — everything stays on this phone.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            Text("WHAT IT'S STORED FOR", style = Eyebrow, color = Ink.Dim)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your block list and settings are saved on the device. If you set a daily limit, " +
                    "Anchor keeps a running total of minutes used for that item, and clears it " +
                    "each night. That's all.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))

            Text(
                "Android will show its own warning next, saying Anchor can view and control your " +
                    "screen. That wording is the same for every app that uses this permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.Dim
            )
            Spacer(Modifier.height(24.dp))

            PrimaryAction("I understand, continue", onClick = onAccept)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Not now", style = Eyebrow, color = Ink.Slate)
            }
        }
    }
}
