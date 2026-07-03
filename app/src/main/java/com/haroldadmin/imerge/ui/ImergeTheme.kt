package com.haroldadmin.imerge.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF242421)
val Canvas = Color(0xFFF4F2ED)
val Paper = Color(0xFFFFFEFA)
val Accent = Color(0xFFE97B47)
val Muted = Color(0xFF73716B)
val Hairline = Color(0xFFE2DFD7)
val PhotoWall = Color(0xFF33322E)

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    secondary = Accent,
    onSecondary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    outline = Hairline,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F0EA),
    onPrimary = Color(0xFF20201D),
    secondary = Color(0xFFFF9563),
    background = Color(0xFF171715),
    onBackground = Color(0xFFF2F0EA),
    surface = Color(0xFF242421),
    onSurface = Color(0xFFF2F0EA),
    outline = Color(0xFF44423D),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun IMergeTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
