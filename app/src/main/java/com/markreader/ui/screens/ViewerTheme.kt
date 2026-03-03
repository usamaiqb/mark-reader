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

data class ViewerColors(
    val surface: Color,
    val content: Color,
    val muted: Color,
    val tonalContainer: Color
)

fun viewerChromeColors(
    activeReaderTheme: ReaderThemePreference,
    isSurfaceDark: Boolean,
    dynamicLightScheme: ColorScheme?,
    dynamicDarkScheme: ColorScheme?
): ViewerColors {
    return when (activeReaderTheme) {
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
        else -> if (isSurfaceDark) {
            val scheme = dynamicDarkScheme
            ViewerColors(
                surface = scheme?.surface ?: DarkSurface,
                content = scheme?.onSurface ?: DarkOnSurface,
                muted = scheme?.onSurfaceVariant ?: DarkOnSurfaceVariant,
                tonalContainer = scheme?.secondaryContainer ?: DarkSecondaryContainer
            )
        } else {
            val scheme = dynamicLightScheme
            ViewerColors(
                surface = scheme?.surface ?: LightSurface,
                content = scheme?.onSurface ?: LightOnSurface,
                muted = scheme?.onSurfaceVariant ?: LightOnSurfaceVariant,
                tonalContainer = scheme?.secondaryContainer ?: LightSecondaryContainer
            )
        }
    }
}

fun resolveReaderColors(
    readerLightTheme: ReaderThemePreference,
    readerDarkTheme: ReaderThemePreference,
    dynamicLightScheme: ColorScheme?,
    dynamicDarkScheme: ColorScheme?
): Pair<ViewerColors, ViewerColors> {
    val lightReaderColors = when (readerLightTheme) {
        ReaderThemePreference.Sepia -> ViewerColors(
            surface = SepiaSurface,
            content = SepiaOnSurface,
            muted = SepiaOnSurfaceVariant,
            tonalContainer = SepiaSecondaryContainer
        )
        ReaderThemePreference.Light -> ViewerColors(
            surface = dynamicLightScheme?.surface ?: LightSurface,
            content = dynamicLightScheme?.onSurface ?: LightOnSurface,
            muted = dynamicLightScheme?.onSurfaceVariant ?: LightOnSurfaceVariant,
            tonalContainer = dynamicLightScheme?.secondaryContainer
                ?: LightSecondaryContainer
        )
        ReaderThemePreference.Dark -> ViewerColors(
            surface = dynamicDarkScheme?.surface ?: DarkSurface,
            content = dynamicDarkScheme?.onSurface ?: DarkOnSurface,
            muted = dynamicDarkScheme?.onSurfaceVariant ?: DarkOnSurfaceVariant,
            tonalContainer = dynamicDarkScheme?.secondaryContainer
                ?: DarkSecondaryContainer
        )
        ReaderThemePreference.Amoled -> ViewerColors(
            surface = AmoledSurface,
            content = AmoledOnSurface,
            muted = AmoledOnSurface.copy(alpha = 0.72f),
            tonalContainer = AmoledOnSurface.copy(alpha = 0.12f)
        )
    }
    val darkReaderColors = when (readerDarkTheme) {
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
            tonalContainer = dynamicDarkScheme?.secondaryContainer
                ?: DarkSecondaryContainer
        )
        ReaderThemePreference.Light -> ViewerColors(
            surface = dynamicLightScheme?.surface ?: LightSurface,
            content = dynamicLightScheme?.onSurface ?: LightOnSurface,
            muted = dynamicLightScheme?.onSurfaceVariant ?: LightOnSurfaceVariant,
            tonalContainer = dynamicLightScheme?.secondaryContainer
                ?: LightSecondaryContainer
        )
        ReaderThemePreference.Sepia -> ViewerColors(
            surface = SepiaSurface,
            content = SepiaOnSurface,
            muted = SepiaOnSurfaceVariant,
            tonalContainer = SepiaSecondaryContainer
        )
    }
    return lightReaderColors to darkReaderColors
}
