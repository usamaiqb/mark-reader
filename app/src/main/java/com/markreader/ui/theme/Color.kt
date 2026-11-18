package com.markreader.ui.theme

import androidx.compose.ui.graphics.Color

// The app's own scheme, used when dynamic colour is off: Material's tonal spot variant, 2021
// spec, from seed #306A42. Tonal spot and the 2021 spec are what Android itself uses for dynamic
// colour, so switching dynamic colour off changes the shade, not the character.
val LightPrimary = Color(0xFF306A42)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFB3F1BF)
val LightOnPrimaryContainer = Color(0xFF16512C)
val LightSecondary = Color(0xFF506352)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD2E8D3)
val LightOnSecondaryContainer = Color(0xFF384B3C)
val LightTertiary = Color(0xFF3A656E)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFBDEAF5)
val LightOnTertiaryContainer = Color(0xFF204D56)
val LightBackground = Color(0xFFF6FBF3)
val LightOnBackground = Color(0xFF181D18)
val LightSurface = Color(0xFFF6FBF3)
val LightOnSurface = Color(0xFF181D18)
val LightSurfaceVariant = Color(0xFFDDE5DA)
val LightOnSurfaceVariant = Color(0xFF414941)
val LightOutline = Color(0xFF717971)
// Neutral-variant tone 80 and 30. The same two values already appear above as
// DarkOnSurfaceVariant and LightOnSurfaceVariant: M3 draws outlineVariant from the same ramp.
val LightOutlineVariant = Color(0xFFC1C9BF)

val DarkPrimary = Color(0xFF98D5A4)
val DarkOnPrimary = Color(0xFF00391A)
val DarkPrimaryContainer = Color(0xFF16512C)
val DarkOnPrimaryContainer = Color(0xFFB3F1BF)
val DarkSecondary = Color(0xFFB7CCB8)
val DarkOnSecondary = Color(0xFF223526)
val DarkSecondaryContainer = Color(0xFF384B3C)
val DarkOnSecondaryContainer = Color(0xFFD2E8D3)
val DarkTertiary = Color(0xFFA2CED9)
val DarkOnTertiary = Color(0xFF01363F)
val DarkTertiaryContainer = Color(0xFF204D56)
val DarkOnTertiaryContainer = Color(0xFFBDEAF5)
val DarkBackground = Color(0xFF101510)
val DarkOnBackground = Color(0xFFDFE4DC)
val DarkSurface = Color(0xFF101510)
val DarkOnSurface = Color(0xFFDFE4DC)
val DarkSurfaceVariant = Color(0xFF414941)
val DarkOnSurfaceVariant = Color(0xFFC1C9BF)
val DarkOutline = Color(0xFF8B938A)
val DarkOutlineVariant = Color(0xFF414941)

// Light surface container tones
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF0F5ED)
val LightSurfaceContainer = Color(0xFFEBEFE7)
val LightSurfaceContainerHigh = Color(0xFFE5EAE2)
val LightSurfaceContainerHighest = Color(0xFFDFE4DC)

// Dark surface container tones
val DarkSurfaceContainerLowest = Color(0xFF0B0F0B)
val DarkSurfaceContainerLow = Color(0xFF181D18)
val DarkSurfaceContainer = Color(0xFF1C211C)
val DarkSurfaceContainerHigh = Color(0xFF262B26)
val DarkSurfaceContainerHighest = Color(0xFF313631)

// The reading surface's own palettes, read by ViewerColors and nothing else. Only the roles a
// reading surface actually needs — a fixed palette is not a Material scheme and never was one.
val AmoledSurface = Color(0xFF000000)
val AmoledOnSurface = Color(0xFFE6E6EB)

val SepiaSecondaryContainer = Color(0xFFF7DED0)
val SepiaSurface = Color(0xFFF6EDE3)
val SepiaOnSurface = Color(0xFF201A17)
val SepiaOnSurfaceVariant = Color(0xFF51443B)
