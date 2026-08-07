package com.dan.anchor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dan.anchor.ui.*

private enum class Tab(val label: String, val icon: ImageVector) {
    BLOCKS("Blocks", Icons.Outlined.Shield),
    READ("Read", Icons.Outlined.Book),
    SETTINGS("Settings", Icons.Outlined.Tune)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnchorTheme {
                var tab by rememberSaveable { mutableStateOf(Tab.BLOCKS) }

                Scaffold(
                    containerColor = Ink.Void,
                    bottomBar = { AnchorBar(tab) { tab = it } }
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            Tab.BLOCKS -> BlocksScreen()
                            Tab.READ -> ReadScreen()
                            Tab.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        }
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
