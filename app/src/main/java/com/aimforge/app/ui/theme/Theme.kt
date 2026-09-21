package com.aimforge.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Design tokens. True black (AMOLED) base, steel-blue accent, orange reserved for "problem" signals. */
object AF {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF07080A)
    val Accent = Color(0xFF7FB2FF)
    val Signal = Color(0xFFFF7A45)
    val Good = Color(0xFF6EE7B7)
    val TextPrimary = Color(0xFFF1F3F6)
    val TextSecondary = Color(0xFF8A93A0)
    val Track = Color(0xFF1A1E24)
    val Border = Color(0x1FFFFFFF)
    val GlassTop = Color(0x1AFFFFFF)
    val GlassBottom = Color(0x08FFFFFF)
}

private val scheme = darkColorScheme(
    primary = AF.Accent,
    onPrimary = Color.Black,
    background = AF.Bg,
    onBackground = AF.TextPrimary,
    surface = AF.Surface,
    onSurface = AF.TextPrimary,
    surfaceVariant = AF.Track,
    onSurfaceVariant = AF.TextSecondary,
    outline = AF.Border,
    error = AF.Signal
)

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 34.sp, letterSpacing = 6.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp)
)

@Composable
fun AimForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
