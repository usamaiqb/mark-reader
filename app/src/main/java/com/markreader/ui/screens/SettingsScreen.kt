package com.markreader.ui.screens

import android.app.Application
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.markreader.ui.components.GroupOuterRadius
import com.markreader.ui.components.SectionHeader
import com.markreader.ui.components.SegmentPosition
import com.markreader.ui.components.segmentShape
import com.markreader.ui.theme.CodeFontFamily
import com.markreader.ui.theme.ReadingFontFamily
import com.markreader.ui.theme.SansReadingFontFamily
import java.util.Locale
import kotlin.math.abs

private const val GithubRepoUrl = "https://github.com/usamaiqb/mark-reader"

// ── Reusable composables ───────────────────────────────────────────────────────

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        content()
    }
}

@Composable
private fun SettingsSurface(
    position: SegmentPosition,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = segmentShape(position)
    // One Surface with one content slot: branching used to call content() from two call
    // sites, which throws away any state it holds if a row gains or loses its onClick.
    // The click lives on the inner Box so its ripple stays inside the Surface's shape.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun RowLeadingIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .padding(end = 16.dp)
            .size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PickerSettingsRow(
    position: SegmentPosition,
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    selectedLabel: String,
    onSelect: (T) -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }

    SettingsSurface(position = position, onClick = { showSheet = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowLeadingIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = CircleShape
                ) {
                    AnimatedContent(
                        targetState = selectedLabel,
                        transitionSpec = {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                        },
                        label = "valueBadge"
                    ) { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        OptionSheet(
            title = title,
            options = options,
            optionLabel = optionLabel,
            selectedLabel = selectedLabel,
            onSelect = onSelect,
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSheet(
    title: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    selectedLabel: String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val selected = optionLabel(option) == selectedLabel
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSelect(option)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else null,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.settings_selected),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SwitchSettingsRow(
    position: SegmentPosition,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val toggle: (Boolean) -> Unit = { newValue ->
        haptics.performHapticFeedback(
            if (newValue) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
        )
        onCheckedChange(newValue)
    }

    SettingsSurface(position = position, onClick = { toggle(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowLeadingIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = toggle,
                thumbContent = {
                    AnimatedContent(
                        targetState = checked,
                        transitionSpec = {
                            fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                        },
                        label = "switchThumbIcon"
                    ) { isChecked ->
                        Icon(
                            imageVector = if (isChecked) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun SliderSettingsRow(
    position: SegmentPosition,
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    val haptics = LocalHapticFeedback.current

    SettingsSurface(position = position) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RowLeadingIcon(icon)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = { newValue ->
                    if (newValue != value) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    onValueChange(newValue)
                },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    thumbColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun SegmentedSettingsRow(
    position: SegmentPosition,
    icon: ImageVector,
    title: String,
    subtitle: String,
    control: @Composable () -> Unit
) {
    SettingsSurface(position = position) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowLeadingIcon(icon)
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            control()
        }
    }
}

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
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val versionFormat = stringResource(R.string.settings_version)
    val versionUnknown = stringResource(R.string.settings_version_unknown)
    val versionLabel = remember(context, versionFormat, versionUnknown) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            String.format(versionFormat, packageInfo.versionName ?: "1.0", versionCode)
        }.getOrDefault(versionUnknown)
    }

    var fontSizeDraft by rememberSaveable(preferences.fontSizeSp) {
        mutableFloatStateOf(preferences.fontSizeSp)
    }
    var lineHeightDraft by rememberSaveable(preferences.lineHeight) {
        mutableFloatStateOf(preferences.lineHeight)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                scrollBehavior = scrollBehavior
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
                SettingsGroup {
                    SegmentedSettingsRow(
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

                SettingsGroup {
                    PickerSettingsRow(
                        position = SegmentPosition.First,
                        icon = Icons.Rounded.LightMode,
                        title = stringResource(R.string.settings_reader_light_theme),
                        subtitle = stringResource(R.string.settings_reader_light_theme_subtitle),
                        options = listOf(
                            ReaderThemePreference.Light,
                            ReaderThemePreference.Sepia
                        ),
                        optionLabel = { stringResource(it.displayLabel()) },
                        selectedLabel = stringResource(preferences.readerLightTheme.displayLabel()),
                        onSelect = viewModel::setReaderLightTheme
                    )
                    PickerSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.settings_reader_dark_theme),
                        subtitle = stringResource(R.string.settings_reader_dark_theme_subtitle),
                        options = listOf(
                            ReaderThemePreference.Dark,
                            ReaderThemePreference.Amoled
                        ),
                        optionLabel = { stringResource(it.displayLabel()) },
                        selectedLabel = stringResource(preferences.readerDarkTheme.displayLabel()),
                        onSelect = viewModel::setReaderDarkTheme
                    )
                    PickerSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.TextFields,
                        title = stringResource(R.string.settings_reading_font),
                        subtitle = stringResource(R.string.settings_reading_font_subtitle),
                        options = listOf(
                            ReadingFontPreference.Merriweather,
                            ReadingFontPreference.SystemSerif,
                            ReadingFontPreference.MerriweatherSans
                        ),
                        optionLabel = { stringResource(it.displayLabel()) },
                        selectedLabel = stringResource(preferences.readingFont.displayLabel()),
                        onSelect = viewModel::setReadingFont
                    )
                    PickerSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.settings_code_font),
                        subtitle = stringResource(R.string.settings_code_font_subtitle),
                        options = listOf(
                            CodeFontPreference.JetBrainsMono,
                            CodeFontPreference.SystemMono
                        ),
                        optionLabel = { stringResource(it.displayLabel()) },
                        selectedLabel = stringResource(preferences.codeFont.displayLabel()),
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
                    SegmentedSettingsRow(
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
                SettingsGroup {
                    val uriHandler = LocalUriHandler.current
                    SettingsSurface(
                        position = SegmentPosition.First,
                        onClick = { uriHandler.openUri(GithubRepoUrl) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_github),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = GithubRepoUrl.removePrefix("https://"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SettingsSurface(position = SegmentPosition.Last) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = versionLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.settings_app_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
