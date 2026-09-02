package com.markreader.ui.screens

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.markreader.data.ReaderThemePreference
import com.markreader.ui.theme.AmoledOnSurface
import com.markreader.ui.theme.AmoledSurface
import com.markreader.ui.theme.DarkOnSurface
import com.markreader.ui.theme.DarkOnSurfaceVariant
import com.markreader.ui.theme.DarkSecondaryContainer
import com.markreader.ui.theme.DarkSurface
import com.markreader.ui.theme.LightOnSurface
import com.markreader.ui.theme.LightOnSurfaceVariant
import com.markreader.ui.theme.LightSecondaryContainer
import com.markreader.ui.theme.LightSurface
import com.markreader.ui.theme.SepiaOnSurface
import com.markreader.ui.theme.SepiaOnSurfaceVariant
import com.markreader.ui.theme.SepiaSecondaryContainer
import com.markreader.ui.theme.SepiaSurface

/** The four colours the reader's chrome takes from whichever palette is reading. */
data class ViewerColors(
    val surface: Color,
    val content: Color,
    val muted: Color,
    val tonalContainer: Color
)

/**
 * One palette's chrome colours — the single place the mapping is written.
 *
 * Sepia and Amoled are fixed palettes and answer for themselves. Light and Dark defer to the
 * dynamic scheme when the device offers one, which is why both are passed in rather than one:
 * a caller asking for Dark colours may be doing so while the light scheme is the active one.
 */
fun readerColorsFor(
    theme: ReaderThemePreference,
    dynamicLightScheme: ColorScheme?,
    dynamicDarkScheme: ColorScheme?
): ViewerColors = when (theme) {
    ReaderThemePreference.Sepia -> ViewerColors(
        surface = SepiaSurface,
        content = SepiaOnSurface,
        muted = SepiaOnSurfaceVariant,
        tonalContainer = SepiaSecondaryContainer
    )
    ReaderThemePreference.Amoled -> ViewerColors(
        surface = AmoledSurface,
        content = AmoledOnSurface,
        muted = AmoledOnSurface.copy(alpha = 0.72f),
        tonalContainer = AmoledOnSurface.copy(alpha = 0.12f)
    )
    ReaderThemePreference.Dark -> ViewerColors(
        surface = dynamicDarkScheme?.surface ?: DarkSurface,
        content = dynamicDarkScheme?.onSurface ?: DarkOnSurface,
        muted = dynamicDarkScheme?.onSurfaceVariant ?: DarkOnSurfaceVariant,
        tonalContainer = dynamicDarkScheme?.secondaryContainer ?: DarkSecondaryContainer
    )
    ReaderThemePreference.Light -> ViewerColors(
        surface = dynamicLightScheme?.surface ?: LightSurface,
        content = dynamicLightScheme?.onSurface ?: LightOnSurface,
        muted = dynamicLightScheme?.onSurfaceVariant ?: LightOnSurfaceVariant,
        tonalContainer = dynamicLightScheme?.secondaryContainer ?: LightSecondaryContainer
    )
}

/** The colours for the palette actually on screen. */
fun viewerChromeColors(
    activeReaderTheme: ReaderThemePreference,
    isSurfaceDark: Boolean,
    dynamicLightScheme: ColorScheme?,
    dynamicDarkScheme: ColorScheme?
): ViewerColors = readerColorsFor(
    // Light and Dark are not choices of their own here: they follow the reading surface, which
    // the app theme has already decided. Sepia and Amoled override it.
    theme = when (activeReaderTheme) {
        ReaderThemePreference.Sepia,
        ReaderThemePreference.Amoled -> activeReaderTheme
        ReaderThemePreference.Light,
        ReaderThemePreference.Dark ->
            if (isSurfaceDark) ReaderThemePreference.Dark else ReaderThemePreference.Light
    },
    dynamicLightScheme = dynamicLightScheme,
    dynamicDarkScheme = dynamicDarkScheme
)

/**
 * Both of a document's palettes at once: the one for light mode and the one for dark.
 *
 * The reader keeps both so that a theme flip is a swap rather than a re-resolve, and so the
 * settings screen can preview each of them beside its own switch.
 */
fun resolveReaderColors(
    readerLightTheme: ReaderThemePreference,
    readerDarkTheme: ReaderThemePreference,
    dynamicLightScheme: ColorScheme?,
    dynamicDarkScheme: ColorScheme?
): Pair<ViewerColors, ViewerColors> =
    readerColorsFor(readerLightTheme, dynamicLightScheme, dynamicDarkScheme) to
        readerColorsFor(readerDarkTheme, dynamicLightScheme, dynamicDarkScheme)
