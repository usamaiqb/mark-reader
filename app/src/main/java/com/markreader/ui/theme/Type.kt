package com.markreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.markreader.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val ReadingFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Merriweather"),
        fontProvider = provider,
        weight = FontWeight.Normal
    ),
    Font(
        googleFont = GoogleFont("Merriweather"),
        fontProvider = provider,
        weight = FontWeight.Bold
    )
)

val CodeFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("JetBrains Mono"),
        fontProvider = provider,
        weight = FontWeight.Normal
    )
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = ReadingFontFamily,
        fontSize = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ReadingFontFamily,
        fontSize = 16.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ReadingFontFamily,
        fontSize = 14.sp
    )
)
