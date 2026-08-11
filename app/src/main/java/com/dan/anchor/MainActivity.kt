package com.dan.anchor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dan.anchor.block.BlockOverlayActivity
import com.dan.anchor.ui.*
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector) {
    BLOCKS("Blocks", Icons.Outlined.Shield),
    BIBLE("Bible", Icons.Outlined.Book),
    SETTINGS("Settings", Icons.Outlined.Tune)
}

class MainActivity : ComponentActivity() {

    /** Set when the block screen sends you here to read the verse in context. */
    private val passageRequest = mutableStateOf<PassageTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        passageRequest.value = targetFrom(intent)

        setContent {
            AnchorTheme {
                val pager = rememberPagerState(pageCount = { Tab.entries.size })
                val scope = rememberCoroutineScope()
                val current = Tab.entries[pager.currentPage]
                val target by passageRequest

                // Arriving with a passage should land you on the Bible, not on
                // whichever tab you happened to leave open.
                LaunchedEffect(target) {
                    if (target != null) pager.scrollToPage(Tab.BIBLE.ordinal)
                }

                Scaffold(
                    containerColor = Ink.Void,
                    bottomBar = {
                        AnchorBar(current) { tab ->
                            scope.launch { pager.animateScrollToPage(tab.ordinal) }
                        }
                    }
                ) { pad ->
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.padding(pad),
                        // Each tab keeps its own scroll position and state as you
                        // swipe back and forth.
                        beyondViewportPageCount = 1
                    ) { page ->
                        when (Tab.entries[page]) {
                            Tab.BLOCKS -> BlocksScreen()
                            Tab.BIBLE -> ReadScreen(
                                target = target,
                                onTargetHandled = { passageRequest.value = null }
                            )
                            Tab.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        passageRequest.value = targetFrom(intent)
    }

    private fun targetFrom(intent: Intent?): PassageTarget? {
        val book = intent?.getStringExtra(BlockOverlayActivity.EXTRA_OPEN_BOOK) ?: return null
        val chapter = intent.getIntExtra(BlockOverlayActivity.EXTRA_OPEN_CHAPTER, 0)
        if (chapter <= 0) return null
        return PassageTarget(
            book = book,
            chapter = chapter,
            verse = intent.getIntExtra(BlockOverlayActivity.EXTRA_OPEN_VERSE, 1)
        )
    }
}

@Composable
private fun AnchorBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ink.Void)
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            Tab.entries.forEach { t ->
                val on = t == current
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(t) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        t.icon,
                        contentDescription = t.label,
                        tint = if (on) Ink.Brass else Ink.Dim,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t.label.uppercase(),
                        style = Eyebrow,
                        color = if (on) Ink.Bone else Ink.Dim
                    )
                }
            }
        }
    }
}
