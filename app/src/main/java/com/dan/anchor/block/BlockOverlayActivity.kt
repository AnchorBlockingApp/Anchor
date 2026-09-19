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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        val target = intent.getStringExtra(EXTRA_TARGET).orEmpty()
        val gateLabel = intent.getStringExtra(EXTRA_GATE_LABEL).orEmpty()
        val gateDone = intent.getIntExtra(EXTRA_GATE_DONE, 0)
        val gateNeeded = intent.getIntExtra(EXTRA_GATE_NEEDED, 0)
        val leftToday = intent.getIntExtra(EXTRA_LEFT_TODAY, 0)
        val gapSeconds = intent.getIntExtra(EXTRA_GAP_SECONDS, 0)
        val justEnded = intent.getBooleanExtra(EXTRA_JUST_ENDED, false)

        setContent {
            AnchorTheme {
                val ctx = LocalContext.current
                val prefs = remember { Prefs.get(ctx) }
                val verse = remember { Verses.next(ctx) }
                if (reason == Decision.Reason.SESSION.name) {
                    SessionScreen(
                        verse = verse,
                        label = label,
                        minutesLeftToday = leftToday,
                        gapSeconds = gapSeconds,
                        justEnded = justEnded,
                        onStart = { mins ->
                            prefs.startSession(target, mins)
                            openTarget(target)
                        },
                        onOneMore = {
                            if (leftToday <= 0) {
                                // The day is spent — this is the one grace minute.
                                prefs.grantGrace(target)
                            } else {
                                prefs.extendSession(target, 1, maxMs = leftToday * 60_000L)
                            }
                            openTarget(target)
                        },
                        onDismiss = { goHome() }
                    )
                    return@AnchorTheme
                }

                BlockScreen(
                    verse = verse,
                    label = label,
                    reason = reason,
                    gateLabel = gateLabel,
                    gateDone = gateDone,
                    gateNeeded = gateNeeded,
                    pauseSeconds = prefs.pauseSeconds,
                    emergenciesLeft = prefs.emergenciesLeft(),
                    canExtend = target.isNotBlank() &&
                        reason != Decision.Reason.ADULT.name &&
                        reason != Decision.Reason.SELF_DEFENCE.name &&
                        reason != Decision.Reason.RULE.name,
                    onExtend = {
                        prefs.grantEmergency(target)
                        openTarget(target)
                    },
                    onDismiss = { goHome() },
                    onOpenPassage = { openPassage(verse.book, verse.chapter, verse.verseStart) }
                )
            }
        }
    }

    /** Opens the reader at this passage instead of just sending you away. */
    private fun openPassage(book: String, chapter: Int, verse: Int) {
        startActivity(
            Intent(this, com.dan.anchor.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_OPEN_BOOK, book)
                putExtra(EXTRA_OPEN_CHAPTER, chapter)
                putExtra(EXTRA_OPEN_VERSE, verse)
            }
        )
        finish()
    }

    /**
     * Puts you back where you were once a session is claimed. Without this you
     * land on the launcher and have to find the app again, which is a daft end
     * to a decision you just made deliberately.
     */
    private fun openTarget(target: String) {
        val intent = runCatching { packageManager.getLaunchIntentForPackage(target) }.getOrNull()
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            overridePendingTransition(0, 0)
        } else {
            // A website — there's no launch intent, so just get out of the way.
            finish()
            overridePendingTransition(0, 0)
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

    override fun onResume() {
        super.onResume()
        showing = true
    }

    override fun onPause() {
        super.onPause()
        showing = false
    }

    override fun onDestroy() {
        showing = false
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LABEL = "label"
        const val EXTRA_REASON = "reason"
        const val EXTRA_TARGET = "target"
        const val EXTRA_LEFT_TODAY = "left_today"
        const val EXTRA_GAP_SECONDS = "gap_seconds"
        const val EXTRA_JUST_ENDED = "just_ended"
        const val EXTRA_GATE_LABEL = "gate_label"
        const val EXTRA_GATE_DONE = "gate_done"
        const val EXTRA_GATE_NEEDED = "gate_needed"
        const val EXTRA_OPEN_BOOK = "open_book"
        const val EXTRA_OPEN_CHAPTER = "open_chapter"
        const val EXTRA_OPEN_VERSE = "open_verse"

        /**
         * The service polls once a second and can't read the address bar while
         * this screen covers it, so without this it re-blocks the cached URL,
         * relaunches, and hands out a different verse every few seconds.
         */
        @Volatile var showing: Boolean = false
            private set
    }
}

/**
 * A verse in its own scrolling area, with the controls pinned beneath it.
 *
 * Long passages used to run underneath the buttons — Romans 6 would swallow the
 * countdown and the "read it" link. Giving the text a bounded, scrollable region
 * means the controls always have their own space no matter how long the verse.
 */
@Composable
private fun VerseBody(
    verse: BlockVerse,
    modifier: Modifier = Modifier,
    large: Boolean = true,
    onOpenPassage: (() -> Unit)? = null
) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Text(verse.text, style = if (large) ScriptureLarge else Scripture)
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .then(if (onOpenPassage != null) Modifier.clickable(onClick = onOpenPassage) else Modifier)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(24.dp).height(1.dp).background(Ink.BrassDim))
            Spacer(Modifier.width(12.dp))
            Text(verse.reference.uppercase(), style = Eyebrow, color = Ink.Brass)
            if (onOpenPassage != null) {
                Spacer(Modifier.width(10.dp))
                Text("READ IT", style = Eyebrow, color = Ink.Dim)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun BlockScreen(
    verse: BlockVerse,
    label: String,
    reason: String,
    gateLabel: String,
    gateDone: Int,
    gateNeeded: Int,
    pauseSeconds: Int,
    emergenciesLeft: Int,
    canExtend: Boolean,
    onExtend: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPassage: () -> Unit
) {
    var elapsed by remember { mutableIntStateOf(0) }
    val done = elapsed >= pauseSeconds
    var extendStep by remember { mutableIntStateOf(0) }
    var extendWait by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        while (elapsed < pauseSeconds) { delay(1_000); elapsed += 1 }
    }
    LaunchedEffect(extendStep) {
        if (extendStep == 1) {
            extendWait = 60
            while (extendWait > 0) { delay(1_000); extendWait -= 1 }
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (pauseSeconds == 0) 1f else elapsed / pauseSeconds.toFloat(),
        animationSpec = tween(900, easing = LinearEasing),
        label = "pause"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
            .padding(horizontal = 32.dp)
            .padding(top = 72.dp, bottom = 28.dp)
    ) {
        Text(headerFor(reason), style = Eyebrow)
        Spacer(Modifier.height(10.dp))
        Text(
            if (reason == Decision.Reason.GATED.name)
                "$label opens up after $gateNeeded minutes in $gateLabel. " +
                    "You've done $gateDone so far today."
            else subheadFor(reason, label),
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Dim
        )

        if (reason == Decision.Reason.GATED.name && gateNeeded > 0) {
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Ink.Hairline)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((gateDone.toFloat() / gateNeeded).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Ink.BrassDim)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        VerseBody(
            verse = verse,
            modifier = Modifier.weight(1f),
            onOpenPassage = onOpenPassage
        )

        Spacer(Modifier.height(20.dp))

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
                    .background(Brush.horizontalGradient(listOf(Ink.BrassDim, Ink.Brass)))
            )
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!done) {
                Text("${pauseSeconds - elapsed}", style = ScriptureLarge, color = Ink.Dim)
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Leave it", style = Eyebrow, color = Ink.Bone)
                }
                if (canExtend && emergenciesLeft > 0) {
                    when (extendStep) {
                        0 -> TextButton(onClick = { extendStep = 1 }) {
                            Text(
                                "I NEED A FEW MINUTES  ·  $emergenciesLeft LEFT THIS WEEK",
                                style = Eyebrow, color = Ink.Dim, textAlign = TextAlign.Center
                            )
                        }
                        1 -> {
                            Text(
                                "Two a week. Worth being sure this is one of them.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink.Dim,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            if (extendWait > 0) {
                                Text("$extendWait", style = Eyebrow, color = Ink.Dim)
                            } else {
                                TextButton(onClick = onExtend) {
                                    Text("USE ONE NOW", style = Eyebrow, color = Ink.Brass)
                                }
                            }
                            TextButton(onClick = { extendStep = 0 }) {
                                Text("NEVER MIND", style = Eyebrow, color = Ink.Slate)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shown when a rule asks how long you want before each stretch of use.
 *
 * The countdown is the point. Sixty seconds is long enough to break the reflex
 * that opened the app, and the verse gives you something to do with the wait
 * other than stare at a timer.
 */
@Composable
private fun SessionScreen(
    verse: BlockVerse,
    label: String,
    minutesLeftToday: Int,
    gapSeconds: Int,
    justEnded: Boolean,
    onStart: (Int) -> Unit,
    onOneMore: () -> Unit,
    onDismiss: () -> Unit
) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (elapsed < gapSeconds) { delay(1_000); elapsed += 1 }
    }
    val waitLeft = (gapSeconds - elapsed).coerceAtLeast(0)
    val ready = waitLeft <= 0
    // The escape hatch unlocks sooner — it exists for finishing a video, and
    // making someone wait a full minute for one more minute is absurd.
    val oneMoreReady = elapsed >= 10 || gapSeconds <= 10

    val options = remember(minutesLeftToday) {
        listOf(5, 10, 15, 30).filter { it < minutesLeftToday }
    }
    val dayDone = minutesLeftToday <= 0
    val plural = if (minutesLeftToday == 1) "" else "s"
    val howMuchLeft = "You've got $minutesLeftToday minute$plural left on $label today."

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
            .padding(horizontal = 32.dp)
            .padding(top = 72.dp, bottom = 28.dp)
    ) {
        Text(
            when {
                dayDone -> "TIME'S UP"
                justEnded -> "THAT STRETCH IS UP"
                else -> "BEFORE YOU START"
            },
            style = Eyebrow
        )
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                dayDone ->
                    "That's today's time on $label gone. If needed, you may have an additional " +
                        "minute to finish what you were in the middle of."
                justEnded ->
                    "$howMuchLeft Wait for the timer if you want another stretch. If needed, you " +
                        "may have an additional minute to finish what you were in the middle of."
                options.isEmpty() -> "$howMuchLeft That's all that's left, so it's all or nothing."
                else -> "$howMuchLeft How much time do you want to spend on it now?"
            },
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(28.dp))

        VerseBody(verse = verse, modifier = Modifier.weight(1f), large = false)

        Spacer(Modifier.height(20.dp))

        if (!dayDone) {
            if (ready) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    options.forEach { m ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ink.Raised)
                                .clickable { onStart(m) }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$m min", style = Eyebrow, color = Ink.Bone)
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ink.Brass)
                            .clickable { onStart(minutesLeftToday) }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (options.isEmpty()) "USE THE LAST $minutesLeftToday"
                            else "ALL $minutesLeftToday",
                            style = Eyebrow, color = Ink.Void, textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Ink.Hairline)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(
                                if (gapSeconds == 0) 1f
                                else (elapsed.toFloat() / gapSeconds).coerceIn(0f, 1f)
                            )
                            .fillMaxHeight()
                            .background(Ink.BrassDim)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "$waitLeft",
                    style = ScriptureLarge,
                    color = Ink.Dim,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (justEnded || dayDone) {
                if (oneMoreReady) {
                    TextButton(onClick = onOneMore) {
                        Text(
                            "I'M IN THE MIDDLE OF SOMETHING — 1 MORE MINUTE",
                            style = Eyebrow, color = Ink.Brass, textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        "One more minute available in ${10 - elapsed}",
                        style = MaterialTheme.typography.bodyMedium, color = Ink.Dim
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text("LEAVE IT", style = Eyebrow, color = Ink.Slate)
            }
        }
    }
}

private fun headerFor(reason: String): String = when (reason) {
    Decision.Reason.LIMIT_REACHED.name -> "TIME'S UP"
    Decision.Reason.GATED.name -> "NOT YET"
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

