package com.markreader.ui.screens

import android.app.Application
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatLineSpacing
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markreader.data.AppThemeModePreference
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReaderThemePreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.TextAlignmentPreference
import com.markreader.data.UserPreferences
import com.markreader.ui.theme.CodeFontFamily
import com.markreader.ui.theme.ReadingFontFamily
import java.util.Locale
import kotlin.math.abs

private const val GithubRepoUrl = "https://github.com/usamaiqb/mark-reader"

// ── Segment shape helpers ──────────────────────────────────────────────────────

private enum class SegmentPosition { Single, First, Middle, Last }

private val GroupOuterRadius = 24.dp
private val GroupInnerRadius = 4.dp

private fun segmentShape(position: SegmentPosition): Shape = when (position) {
    SegmentPosition.Single -> RoundedCornerShape(GroupOuterRadius)
    SegmentPosition.First -> RoundedCornerShape(
        topStart = GroupOuterRadius, topEnd = GroupOuterRadius,
        bottomStart = GroupInnerRadius, bottomEnd = GroupInnerRadius
    )
    SegmentPosition.Middle -> RoundedCornerShape(GroupInnerRadius)
    SegmentPosition.Last -> RoundedCornerShape(
        topStart = GroupInnerRadius, topEnd = GroupInnerRadius,
        bottomStart = GroupOuterRadius, bottomEnd = GroupOuterRadius
    )
}

// ── Reusable composables ───────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
    )
}

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
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
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
    optionLabel: (T) -> String,
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
    optionLabel: (T) -> String,
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
                                contentDescription = "Selected",
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

    val dynamicLightScheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context) else null
    }
    val dynamicDarkScheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else null
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
                    text = "Preview",
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
                        contentDescription = "Toggle preview between light and dark reader theme",
                        tint = mutedColor
                    )
                }
            }
            Text(
                text = "The Art of Reading",
                fontFamily = readingFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (fontSizeSp * 1.2f).sp,
                lineHeight = (fontSizeSp * 1.2f * lineHeight).sp,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Comfortable typography makes long documents a pleasure to read. Changes apply instantly.",
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
                    text = "fun read(file: Uri) = markdown.render(file)",
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
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            application = context.applicationContext as Application
        )
    )
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val versionLabel = remember(context) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "Version ${packageInfo.versionName ?: "1.0"} ($versionCode)"
        }.getOrDefault("Version unknown")
    }

    var fontSizeDraft by rememberSaveable(preferences.fontSizeSp) {
        mutableFloatStateOf(preferences.fontSizeSp)
    }
    var lineHeightDraft by rememberSaveable(preferences.lineHeight) {
        mutableFloatStateOf(preferences.lineHeight)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
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
                SectionHeader("Appearance")
                SettingsGroup {
                    SegmentedSettingsRow(
                        position = SegmentPosition.Single,
                        icon = Icons.Rounded.Palette,
                        title = "App theme",
                        subtitle = "Overall look of the app"
                    ) {
                        AppThemeModePreferenceControl(
                            selected = preferences.appThemeMode,
                            onSelect = viewModel::setAppThemeMode
                        )
                    }
                }
            }

            // ── Reader ────────────────────────────────────────────────
            Column {
                SectionHeader("Reader")

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
                        title = "Reader light theme",
                        subtitle = "Used when reading in light mode",
                        options = listOf(
                            ReaderThemePreference.Light,
                            ReaderThemePreference.Sepia
                        ),
                        optionLabel = { it.displayLabel() },
                        selectedLabel = preferences.readerLightTheme.displayLabel(),
                        onSelect = viewModel::setReaderLightTheme
                    )
                    PickerSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.DarkMode,
                        title = "Reader dark theme",
                        subtitle = "Used when reading in dark mode",
                        options = listOf(
                            ReaderThemePreference.Dark,
                            ReaderThemePreference.Amoled
                        ),
                        optionLabel = { it.displayLabel() },
                        selectedLabel = preferences.readerDarkTheme.displayLabel(),
                        onSelect = viewModel::setReaderDarkTheme
                    )
                    PickerSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.TextFields,
                        title = "Reading font",
                        subtitle = "Typeface for prose and headings",
                        options = listOf(
                            ReadingFontPreference.Merriweather,
                            ReadingFontPreference.SystemSerif
                        ),
                        optionLabel = { it.displayLabel() },
                        selectedLabel = preferences.readingFont.displayLabel(),
                        onSelect = viewModel::setReadingFont
                    )
                    PickerSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.Code,
                        title = "Code font",
                        subtitle = "Typeface for code blocks and source view",
                        options = listOf(
                            CodeFontPreference.JetBrainsMono,
                            CodeFontPreference.SystemMono
                        ),
                        optionLabel = { it.displayLabel() },
                        selectedLabel = preferences.codeFont.displayLabel(),
                        onSelect = viewModel::setCodeFont
                    )
                    SliderSettingsRow(
                        position = SegmentPosition.Middle,
                        icon = Icons.Rounded.FormatSize,
                        title = "Font size",
                        valueLabel = "${fontSizeDraft.toInt()}sp",
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
                        title = "Line height",
                        valueLabel = "${String.format(Locale.US, "%.1f", lineHeightDraft)}x",
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
                        title = "Text alignment",
                        subtitle = "How paragraphs are laid out"
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
                SectionHeader("About")
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
                                    text = "GitHub",
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
                                    text = "MarkReader",
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
                                    text = "A focused Markdown reader for local files.",
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
            Text("System")
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
            Text("Light")
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
            Text("Dark")
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
            Text("Left")
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
            Text("Justified")
        }
    }
}

// ── Display label helpers ──────────────────────────────────────────────────────

private fun ReaderThemePreference.displayLabel(): String = when (this) {
    ReaderThemePreference.Light -> "Light"
    ReaderThemePreference.Dark -> "Dark"
    ReaderThemePreference.Amoled -> "AMOLED"
    ReaderThemePreference.Sepia -> "Sepia"
}

private fun ReadingFontPreference.displayLabel(): String = when (this) {
    ReadingFontPreference.Merriweather -> "Merriweather"
    ReadingFontPreference.SystemSerif -> "System serif"
}

private fun CodeFontPreference.displayLabel(): String = when (this) {
    CodeFontPreference.JetBrainsMono -> "JetBrains Mono"
    CodeFontPreference.SystemMono -> "System monospace"
}

private fun ReadingFontPreference.fontFamily(): FontFamily = when (this) {
    ReadingFontPreference.Merriweather -> ReadingFontFamily
    ReadingFontPreference.SystemSerif -> FontFamily.Serif
}

private fun CodeFontPreference.fontFamily(): FontFamily = when (this) {
    CodeFontPreference.JetBrainsMono -> CodeFontFamily
    CodeFontPreference.SystemMono -> FontFamily.Monospace
}
