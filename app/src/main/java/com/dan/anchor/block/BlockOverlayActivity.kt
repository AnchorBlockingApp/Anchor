package com.dan.anchor.block

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dan.anchor.data.BlockVerse
import com.dan.anchor.data.Prefs
import com.dan.anchor.data.Verses
import com.dan.anchor.ui.*
import kotlinx.coroutines.delay

/**
 * What you actually see when a block trips. One verse, set large, on nothing.
 *
 * The deliberate part: there is no way out for the first few seconds. The brass
 * hairline filling across the bottom is the wait, and "Go back" doesn't appear
 * until it finishes. Long enough to read the verse; long enough for the pull to
 * pass.
 */
class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The back gesture lands on the launcher, never back inside the blocked app.
        onBackPressedDispatcher.addCallback(this) { goHome() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        }

        val label = intent.getStringExtra(EXTRA_LABEL) ?: "that"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: Decision.Reason.RULE.name

        setContent {
            AnchorTheme {
                val ctx = LocalContext.current
                val prefs = remember { Prefs.get(ctx) }
                val verse = remember { Verses.next(ctx) }
                BlockScreen(
                    verse = verse,
                    label = label,
                    reason = reason,
                    pauseSeconds = prefs.pauseSeconds,
                    onDismiss = { goHome() }
                )
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finish()
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_REASON = "reason"
    }
}

@Composable
private fun BlockScreen(
    verse: BlockVerse,
    label: String,
    reason: String,
    pauseSeconds: Int,
    onDismiss: () -> Unit
) {
    var elapsed by remember { mutableIntStateOf(0) }
    val done = elapsed >= pauseSeconds

    LaunchedEffect(Unit) {
        while (elapsed < pauseSeconds) {
            delay(1_000)
            elapsed += 1
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (pauseSeconds == 0) 1f else elapsed / pauseSeconds.toFloat(),
        animationSpec = tween(900, easing = LinearEasing),
        label = "pause"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
            .padding(horizontal = 32.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 96.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(headerFor(reason), style = Eyebrow)
            Spacer(Modifier.height(10.dp))
            Text(
                subheadFor(reason, label),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.Dim
            )

            Spacer(Modifier.height(48.dp))

            Text(verse.text, style = ScriptureLarge)

            Spacer(Modifier.height(28.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(24.dp)
                        .height(1.dp)
                        .background(Ink.BrassDim)
                )
                Spacer(Modifier.width(12.dp))
                Text(verse.reference.uppercase(), style = Eyebrow, color = Ink.Brass)
            }
        }

        // The wait, made visible.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Ink.Hairline)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(listOf(Ink.BrassDim, Ink.Brass))
                        )
                )
            }

            Spacer(Modifier.height(24.dp))

            if (done) {
                TextButton(onClick = onDismiss) {
                    Text("Go back", style = Eyebrow, color = Ink.Bone)
                }
            } else {
                Text(
                    "${pauseSeconds - elapsed}",
                    style = ScriptureLarge,
                    color = Ink.Dim,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun headerFor(reason: String): String = when (reason) {
    Decision.Reason.LIMIT_REACHED.name -> "TIME'S UP"
    Decision.Reason.ADULT.name -> "FILTERED"
    Decision.Reason.SELF_DEFENCE.name -> "STILL LOCKED"
    else -> "BLOCKED"
}

private fun subheadFor(reason: String, label: String): String = when (reason) {
    Decision.Reason.LIMIT_REACHED.name -> "You've used today's time on $label."
    Decision.Reason.ADULT.name -> "The filter caught that one."
    Decision.Reason.SELF_DEFENCE.name -> "Anchor's settings stay shut until the cooldown runs out."
    else -> "You asked to stay off $label."
}

