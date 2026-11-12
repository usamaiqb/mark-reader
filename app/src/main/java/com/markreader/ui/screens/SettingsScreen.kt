package com.markreader.ui.screens

import android.app.Application
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markreader.R
import com.markreader.data.AppThemeModePreference
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReaderThemePreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.TextAlignmentPreference
import com.markreader.data.UserPreferences
import com.markreader.ui.expressive.AppIdentityRow
import com.markreader.ui.expressive.CollapsingLargeTopBar
import com.markreader.ui.expressive.ControlSettingsRow
import com.markreader.ui.expressive.ExternalLinkRow
import com.markreader.ui.expressive.GroupOuterRadius
import com.markreader.ui.expressive.PickerSettingsRow
import com.markreader.ui.expressive.SectionHeader
import com.markreader.ui.expressive.SegmentPosition
import com.markreader.ui.expressive.SegmentedGroup
import com.markreader.ui.expressive.SliderSettingsRow
import com.markreader.ui.expressive.SwitchSettingsRow
import com.markreader.ui.expressive.rememberAppVersionLabel
import com.markreader.ui.expressive.rememberExpressiveScrollBehavior
import com.markreader.ui.theme.CodeFontFamily
import com.markreader.ui.theme.ReadingFontFamily
import com.markreader.ui.theme.SansReadingFontFamily
import java.util.Locale
import kotlin.math.abs

private const val GITHUB_REPO_URL = "https://github.com/usamaiqb/mark-reader"

// ── Reader preview ─────────────────────────────────────────────────────────────

@Composable
private fun ReaderPreviewCard(
    preferences: UserPreferences,
    fontSizeSp: Float,
    lineHeight: Float
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val haptics = LocalHapticFeedback.current

    val dynamicLightScheme = remember(context, preferences.useDynamicColors) {
        if (preferences.useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            null
        }
    }
    val dynamicDarkScheme = remember(context, preferences.useDynamicColors) {
        if (preferences.useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            null
        }
    }
    val (lightReaderColors, darkReaderColors) = resolveReaderColors(
        readerLightTheme = preferences.readerLightTheme,
        readerDarkTheme = preferences.readerDarkTheme,
        dynamicLightScheme = dynamicLightScheme,
        dynamicDarkScheme = dynamicDarkScheme
    )
    val isBaseDark = when (preferences.appThemeMode) {
        AppThemeModePreference.System -> isSystemDark
        AppThemeModePreference.Light -> false
        AppThemeModePreference.Dark -> true
    }
    var previewDark by rememberSaveable(isBaseDark) { mutableStateOf(isBaseDark) }
    val colors = if (previewDark) darkReaderColors else lightReaderColors

    val surfaceColor by animateColorAsState(colors.surface, label = "previewSurface")
    val contentColor by animateColorAsState(colors.content, label = "previewContent")
    val mutedColor by animateColorAsState(colors.muted, label = "previewMuted")
    val tonalColor by animateColorAsState(colors.tonalContainer, label = "previewTonal")

    val readingFamily = preferences.readingFont.fontFamily()
    val codeFamily = preferences.codeFont.fontFamily()
    val textAlign = when (preferences.textAlignment) {
        TextAlignmentPreference.Left -> TextAlign.Start
        TextAlignmentPreference.Justified -> TextAlign.Justify
    }

    Surface(
        shape = RoundedCornerShape(GroupOuterRadius),
        color = surfaceColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_preview),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = mutedColor,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(
                            if (previewDark) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                        )
                        previewDark = !previewDark
                    }
                ) {
                    Icon(
                        imageVector = if (previewDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        contentDescription = stringResource(R.string.settings_preview_toggle),
                        tint = mutedColor
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_preview_heading),
                fontFamily = readingFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (fontSizeSp * 1.2f).sp,
                lineHeight = (fontSizeSp * 1.2f * lineHeight).sp,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_preview_body),
                fontFamily = readingFamily,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * lineHeight).sp,
                textAlign = textAlign,
                color = contentColor,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = tonalColor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.settings_preview_code),
                    fontFamily = codeFamily,
                    fontSize = (fontSizeSp * 0.85f).sp,
                    lineHeight = (fontSizeSp * 0.85f * lineHeight).sp,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ── Main screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            application = LocalContext.current.applicationContext as Application
        )
    )
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val versionLabel = rememberAppVersionLabel(
        prefix = stringResource(R.string.settings_version_prefix),
        unknown = stringResource(R.string.settings_version_unknown)
    )

    var fontSizeDraft by rememberSaveable(preferences.fontSizeSp) {
        mutableFloatStateOf(preferences.fontSizeSp)
    }
    var lineHeightDraft by rememberSaveable(preferences.lineHeight) {
        mutableFloatStateOf(preferences.lineHeight)
    }

    val scrollBehavior = rememberExpressiveScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CollapsingLargeTopBar(
                title = stringResource(R.string.settings_title),
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                navigateBackDescription = stringResource(R.string.action_back)
            )
        }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Appearance ─────────────────────────────────────────────
            Column {
                SectionHeader(stringResource(R.string.settings_section_appearance))
                val supportsDynamicColors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                SegmentedGroup {
                    ControlSettingsRow(
                        position = if (supportsDynamicColors) SegmentPosition.First else SegmentPosition.Single,
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.settings_app_theme),
                        subtitle = stringResource(R.string.settings_app_theme_subtitle)
                    ) {
                        AppThemeModePreferenceControl(
                            selected = preferences.appThemeMode,
                            onSelect = viewModel::setAppThemeMode
                        )
                    }
                    if (supportsDynamicColors) {
                        SwitchSettingsRow(
                            position = SegmentPosition.Last,
                            icon = Icons.Rounded.Wallpaper,
                            title = stringResource(R.string.settings_dynamic_colors),
                            subtitle = stringResource(R.string.settings_dynamic_colors_subtitle),
                            checked = preferences.useDynamicColors,
                            onCheckedChange = viewModel::setUseDynamicColors
                        )
                    }
                }
            }

            // ── Reader ────────────────────────────────────────────────
            Column {
                SectionHeader(stringResource(R.string.settings_section_reader))

                ReaderPreviewCard(
                    preferences = preferences,
                    fontSizeSp = fontSizeDraft,
                    lineHeight = lineHeightDraft
                )
                Spacer(modifier = Modifier.height(8.dp))

                // The picker rows take a plain label lambda, so every option's string is
                // resolved here rather than inside the row.
                val lightThemeOptions = listOf(
                    ReaderThemePreference.Light,
                    ReaderThemePreference.Sepia
                )
                val darkThemeOptions = listOf(
                    ReaderThemePreference.Dark,
                    ReaderThemePreference.Amoled
                )
                val readingFontOptions = listOf(
                    ReadingFontPreference.Merriweather,
                    ReadingFontPreference.SystemSerif,
                    ReadingFontPreference.MerriweatherSans
                )
                val codeFontOptions = listOf(
                    CodeFontPreference.JetBrainsMono,
                    CodeFontPreference.SystemMono
                )
                val lightThemeLabels = optionLabels(lightThemeOptions) { it.displayLabel() }
                val darkThemeLabels = optionLabels(darkThemeOptions) { it.displayLabel() }
                val readingFontLabels = optionLabels(readingFontOptions) { it.displayLabel() }
                val codeFontLabels = optionLabels(codeFontOptions) { it.displayLabel() }
                val selectedDescription = stringResource(R.string.settings_selected)

                SegmentedGroup {
                    PickerSettingsRow(
                        position = SegmentPosition.First,
                        icon = Icons.Rounded.LightMode,
                        title = stringResource(R.string.settings_reader_light_theme),
                        subtitle = stringResource(R.string.settings_reader_light_theme_subtitle),
                        options = lightThemeOptions,
                        optionLabel = lightThemeLabels,
                        selected = preferences.readerLightTheme,
                        selectedDescription = selectedDescription,
                        onSelect = viewModel::setReaderLightTheme
                    )
                    PickerSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.settings_reader_dark_theme),
                        subtitle = stringResource(R.string.settings_reader_dark_theme_subtitle),
                        options = darkThemeOptions,
                        optionLabel = darkThemeLabels,
                        selected = preferences.readerDarkTheme,
                        selectedDescription = selectedDescription,
                        onSelect = viewModel::setReaderDarkTheme
                    )
                    PickerSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.TextFields,
                        title = stringResource(R.string.settings_reading_font),
                        subtitle = stringResource(R.string.settings_reading_font_subtitle),
                        options = readingFontOptions,
                        optionLabel = readingFontLabels,
                        selected = preferences.readingFont,
                        selectedDescription = selectedDescription,
                        onSelect = viewModel::setReadingFont
                    )
                    PickerSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.settings_code_font),
                        subtitle = stringResource(R.string.settings_code_font_subtitle),
                        options = codeFontOptions,
                        optionLabel = codeFontLabels,
                        selected = preferences.codeFont,
                        selectedDescription = selectedDescription,
                        onSelect = viewModel::setCodeFont
                    )
                    SliderSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.FormatSize,
                        title = stringResource(R.string.settings_font_size),
                        valueLabel = stringResource(
                            R.string.settings_font_size_value,
                            fontSizeDraft.toInt()
                        ),
                        value = fontSizeDraft,
                        onValueChange = { fontSizeDraft = it },
                        onValueChangeFinished = {
                            if (abs(fontSizeDraft - preferences.fontSizeSp) > 0.01f) {
                                viewModel.setFontSize(fontSizeDraft)
                            }
                        },
                        valueRange = 12f..24f,
                        steps = 11
                    )
                    SliderSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.FormatLineSpacing,
                        title = stringResource(R.string.settings_line_height),
                        valueLabel = stringResource(
                            R.string.settings_line_height_value,
                            String.format(Locale.US, "%.1f", lineHeightDraft)
                        ),
                        value = lineHeightDraft,
                        onValueChange = { lineHeightDraft = it },
                        onValueChangeFinished = {
                            if (abs(lineHeightDraft - preferences.lineHeight) > 0.01f) {
                                viewModel.setLineHeight(lineHeightDraft)
                            }
                        },
                        valueRange = 1.2f..2.0f,
                        steps = 7
                    )
                    ControlSettingsRow(
                        position = SegmentPosition.Last,
                        icon = Icons.AutoMirrored.Rounded.FormatAlignLeft,
                        title = stringResource(R.string.settings_text_alignment),
                        subtitle = stringResource(R.string.settings_text_alignment_subtitle)
                    ) {
                        AlignmentPreference(
                            selected = preferences.textAlignment,
                            onSelect = viewModel::setTextAlignment
                        )
                    }
                }
            }

            // ── About ──────────────────────────────────────────────────
            Column {
                SectionHeader(stringResource(R.string.settings_section_about))
                SegmentedGroup {
                    ExternalLinkRow(
                        position = SegmentPosition.First,
                        label = stringResource(R.string.settings_github),
                        url = GITHUB_REPO_URL,
                        icon = Icons.Rounded.Code
                    )
                    AppIdentityRow(
                        position = SegmentPosition.Last,
                        appName = stringResource(R.string.app_name),
                        versionLabel = versionLabel,
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        tagline = stringResource(R.string.settings_app_description)
                    )
                }
            }
        }
    }
}

// ── Preference controls ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppThemeModePreferenceControl(
    selected: AppThemeModePreference,
    onSelect: (AppThemeModePreference) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSelect(AppThemeModePreference.System)
            },
            selected = selected == AppThemeModePreference.System,
            icon = { Icon(Icons.Rounded.BrightnessAuto, contentDescription = null) }
        ) {
            Text(stringResource(R.string.theme_mode_system))
        }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSelect(AppThemeModePreference.Light)
            },
            selected = selected == AppThemeModePreference.Light,
            icon = { Icon(Icons.Rounded.LightMode, contentDescription = null) }
        ) {
            Text(stringResource(R.string.theme_mode_light))
        }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSelect(AppThemeModePreference.Dark)
            },
            selected = selected == AppThemeModePreference.Dark,
            icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) }
        ) {
            Text(stringResource(R.string.theme_mode_dark))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlignmentPreference(
    selected: TextAlignmentPreference,
    onSelect: (TextAlignmentPreference) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSelect(TextAlignmentPreference.Left)
            },
            selected = selected == TextAlignmentPreference.Left,
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.FormatAlignLeft,
                    contentDescription = null
                )
            }
        ) {
            Text(stringResource(R.string.alignment_left))
        }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSelect(TextAlignmentPreference.Justified)
            },
            selected = selected == TextAlignmentPreference.Justified,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.FormatAlignJustify,
                    contentDescription = null
                )
            }
        ) {
            Text(stringResource(R.string.alignment_justified))
        }
    }
}

// ── Display label helpers ──────────────────────────────────────────────────────

/**
 * Resolves every option's label once, up front.
 *
 * [PickerSettingsRow] takes a plain `(T) -> String` rather than a composable one — it calls the
 * lambda again from inside the option sheet, where a composable label would recompose per row —
 * so [stringResource] cannot be called from the lambda itself and has to happen out here.
 */
@Composable
private fun <T> optionLabels(options: List<T>, label: (T) -> Int): (T) -> String {
    val resolved = options.map { stringResource(label(it)) }
    val byOption = options.zip(resolved).toMap()
    return { byOption.getValue(it) }
}

@StringRes
private fun ReaderThemePreference.displayLabel(): Int = when (this) {
    ReaderThemePreference.Light -> R.string.reader_theme_light
    ReaderThemePreference.Dark -> R.string.reader_theme_dark
    ReaderThemePreference.Amoled -> R.string.reader_theme_amoled
    ReaderThemePreference.Sepia -> R.string.reader_theme_sepia
}

@StringRes
private fun ReadingFontPreference.displayLabel(): Int = when (this) {
    ReadingFontPreference.Merriweather -> R.string.reading_font_merriweather
    ReadingFontPreference.SystemSerif -> R.string.reading_font_system_serif
    ReadingFontPreference.MerriweatherSans -> R.string.reading_font_merriweather_sans
}

@StringRes
private fun CodeFontPreference.displayLabel(): Int = when (this) {
    CodeFontPreference.JetBrainsMono -> R.string.code_font_jetbrains_mono
    CodeFontPreference.SystemMono -> R.string.code_font_system_mono
}

private fun ReadingFontPreference.fontFamily(): FontFamily = when (this) {
    ReadingFontPreference.Merriweather -> ReadingFontFamily
    ReadingFontPreference.SystemSerif -> FontFamily.Serif
    ReadingFontPreference.MerriweatherSans -> SansReadingFontFamily
}

private fun CodeFontPreference.fontFamily(): FontFamily = when (this) {
    CodeFontPreference.JetBrainsMono -> CodeFontFamily
    CodeFontPreference.SystemMono -> FontFamily.Monospace
}
