package com.dan.anchor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Anchor runs dark full stop — there is no light variant. The palette is
// deliberately narrow: three greys, bone for type, brass for the one thing on
// screen that matters.
object Ink {
    val Void = Color(0xFF08090A)      // app background
    val Surface = Color(0xFF121417)   // cards, sheets
    val Raised = Color(0xFF191C21)    // pressed / selected
    val Hairline = Color(0xFF23272D)  // 1px dividers and borders
    val Bone = Color(0xFFE8E6E1)      // primary text
    val Slate = Color(0xFF8A8F98)     // secondary text
    val Dim = Color(0xFF5A6068)       // tertiary / disabled
    val Brass = Color(0xFFC9A227)     // the accent, used sparingly
    val BrassDim = Color(0xFF6B570F)  // accent at rest
    val Rust = Color(0xFFB4553C)      // destructive only
}

private val AnchorColors = darkColorScheme(
    primary = Ink.Brass,
    onPrimary = Ink.Void,
    secondary = Ink.Slate,
    background = Ink.Void,
    onBackground = Ink.Bone,
    surface = Ink.Surface,
    onSurface = Ink.Bone,
    surfaceVariant = Ink.Raised,
    onSurfaceVariant = Ink.Slate,
    outline = Ink.Hairline,
    error = Ink.Rust
)

/**
 * Scripture is set in serif, the app's own machinery in sans. That split is the
 * whole type system — you always know at a glance whether you're reading the
 * Bible or reading Anchor.
 */
val Scripture = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 20.sp,
    lineHeight = 34.sp,
    fontWeight = FontWeight.Normal,
    color = Ink.Bone
)

val ScriptureLarge = Scripture.copy(fontSize = 26.sp, lineHeight = 42.sp)

val Eyebrow = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 11.sp,
    letterSpacing = 2.4.sp,
    fontWeight = FontWeight.Medium,
    color = Ink.Brass
)

private val AnchorType = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 30.sp,
        fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp, color = Ink.Bone
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 20.sp,
        fontWeight = FontWeight.Medium, color = Ink.Bone
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 16.sp,
        fontWeight = FontWeight.Medium, color = Ink.Bone
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 15.sp,
        lineHeight = 22.sp, color = Ink.Bone
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 13.sp,
        lineHeight = 19.sp, color = Ink.Slate
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = 11.sp,
        letterSpacing = 1.6.sp, color = Ink.Dim
    )
)

@Composable
fun AnchorTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AnchorColors,
        typography = AnchorType,
        content = content
    )
}
