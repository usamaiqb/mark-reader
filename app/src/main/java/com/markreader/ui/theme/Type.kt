package com.markreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.markreader.R

val ReadingFontFamily = FontFamily(
    Font(R.font.merriweather_regular, weight = FontWeight.Normal),
    Font(R.font.merriweather_bold, weight = FontWeight.Bold)
)

val CodeFontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, weight = FontWeight.Normal)
)

val Typography = Typography(
    // Display — hero / landing text (system sans-serif)
    displayLarge = TextStyle(
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
        fontWeight = FontWeight.Normal
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal
    ),
    // Headline — section headings (system sans-serif)
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal
    ),
    // Title — toolbar, card titles (system sans-serif)
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Medium
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        fontWeight = FontWeight.Medium
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        fontWeight = FontWeight.Medium
    ),
    // Body — reading content (Merriweather)
    bodyLarge = TextStyle(
        fontFamily = ReadingFontFamily,
        fontSize = 18.sp,
        lineHeight = 28.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ReadingFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ReadingFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // Label — buttons, chips, navigation (system sans-serif)
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Medium
    )
)
