package com.markreader.ui.screens

import android.app.Application
import android.os.Build
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markreader.data.AppThemeModePreference
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.ReaderThemePreference
import com.markreader.data.TextAlignmentPreference
import java.util.Locale
import kotlin.math.abs

// ── Segment shape helpers ──────────────────────────────────────────────────────

private enum class SegmentPosition { Single, First, Middle, Last }

private val SegmentRadius = 16.dp

private fun segmentShape(position: SegmentPosition): Shape = when (position) {
    SegmentPosition.Single -> RoundedCornerShape(SegmentRadius)
    SegmentPosition.First -> RoundedCornerShape(
        topStart = SegmentRadius, topEnd = SegmentRadius,
        bottomStart = 0.dp, bottomEnd = 0.dp
    )
    SegmentPosition.Middle -> RoundedCornerShape(0.dp)
    SegmentPosition.Last -> RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = SegmentRadius, bottomEnd = SegmentRadius
    )
}

// ── Reusable composables ───────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SegmentedItem(
    position: SegmentPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = segmentShape(position)
    Column(modifier = modifier) {
        if (onClick != null) {
            Surface(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    content()
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    content()
                }
            }
        }
        if (position == SegmentPosition.First || position == SegmentPosition.Middle) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                thickness = 1.dp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> InlineDropdownRow(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    showSheet: Boolean,
    onShowSheet: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = selectedLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { onShowSheet(false) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            options.forEach { option ->
                val selected = optionLabel(option) == selectedLabel
                ListItem(
                    headlineContent = {
                        Text(
                            text = optionLabel(option),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    trailingContent = {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.clickable {
                        onSelect(option)
                        onShowSheet(false)
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                )
            )
        }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── Appearance ─────────────────────────────────────────────
            Column {
                SectionHeader("Appearance")

                // App theme — standalone segment
                SegmentedItem(position = SegmentPosition.Single) {
                    Column {
                        Text(
                            text = "App theme",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        AppThemeModePreferenceControl(
                            selected = preferences.appThemeMode,
                            onSelect = viewModel::setAppThemeMode
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Dropdown group — connected segments
                var showReaderLight by remember { mutableStateOf(false) }
                var showReaderDark by remember { mutableStateOf(false) }
                var showReadingFont by remember { mutableStateOf(false) }
                var showCodeFont by remember { mutableStateOf(false) }

                SegmentedItem(
                    position = SegmentPosition.First,
                    onClick = { showReaderLight = true }
                ) {
                    InlineDropdownRow(
                        label = "Reader light theme",
                        selectedLabel = preferences.readerLightTheme.displayLabel(),
                        options = listOf(
                            ReaderThemePreference.Light,
                            ReaderThemePreference.Sepia
                        ),
                        optionLabel = { it.displayLabel() },
                        onSelect = viewModel::setReaderLightTheme,
                        showSheet = showReaderLight,
                        onShowSheet = { showReaderLight = it }
                    )
                }
                SegmentedItem(
                    position = SegmentPosition.Middle,
                    onClick = { showReaderDark = true }
                ) {
                    InlineDropdownRow(
                        label = "Reader dark theme",
                        selectedLabel = preferences.readerDarkTheme.displayLabel(),
                        options = listOf(
                            ReaderThemePreference.Dark,
                            ReaderThemePreference.Amoled
                        ),
                        optionLabel = { it.displayLabel() },
                        onSelect = viewModel::setReaderDarkTheme,
                        showSheet = showReaderDark,
                        onShowSheet = { showReaderDark = it }
                    )
                }
                SegmentedItem(
                    position = SegmentPosition.Middle,
                    onClick = { showReadingFont = true }
                ) {
                    InlineDropdownRow(
                        label = "Reading font",
                        selectedLabel = preferences.readingFont.displayLabel(),
                        options = listOf(
                            ReadingFontPreference.Merriweather,
                            ReadingFontPreference.SystemSerif
                        ),
                        optionLabel = { it.displayLabel() },
                        onSelect = viewModel::setReadingFont,
                        showSheet = showReadingFont,
                        onShowSheet = { showReadingFont = it }
                    )
                }
                SegmentedItem(
                    position = SegmentPosition.Last,
                    onClick = { showCodeFont = true }
                ) {
                    InlineDropdownRow(
                        label = "Code font",
                        selectedLabel = preferences.codeFont.displayLabel(),
                        options = listOf(
                            CodeFontPreference.JetBrainsMono,
                            CodeFontPreference.SystemMono
                        ),
                        optionLabel = { it.displayLabel() },
                        onSelect = viewModel::setCodeFont,
                        showSheet = showCodeFont,
                        onShowSheet = { showCodeFont = it }
                    )
                }
            }

            // ── Typography ─────────────────────────────────────────────
            Column {
                SectionHeader("Typography")
                SegmentedItem(position = SegmentPosition.First) {
                    SliderPreference(
                        label = "Font size",
                        valueLabel = "${fontSizeDraft.toInt()}sp",
                        value = fontSizeDraft,
                        onValueChange = { fontSizeDraft = it },
                        onValueChangeFinished = {
                            if (abs(fontSizeDraft - preferences.fontSizeSp) > 0.01f) {
                                viewModel.setFontSize(fontSizeDraft)
                            }
                        },
                        valueRange = 12f..24f,
                        steps = 12
                    )
                }
                SegmentedItem(position = SegmentPosition.Last) {
                    SliderPreference(
                        label = "Line height",
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
                }
            }

            // ── Layout ─────────────────────────────────────────────────
            Column {
                SectionHeader("Layout")
                SegmentedItem(position = SegmentPosition.Single) {
                    AlignmentPreference(
                        selected = preferences.textAlignment,
                        onSelect = viewModel::setTextAlignment
                    )
                }
            }

            // ── About ──────────────────────────────────────────────────
            Column {
                SectionHeader("About")
                SegmentedItem(position = SegmentPosition.Single) {
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

// ── Preference controls ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppThemeModePreferenceControl(
    selected: AppThemeModePreference,
    onSelect: (AppThemeModePreference) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            onClick = { onSelect(AppThemeModePreference.System) },
            selected = selected == AppThemeModePreference.System,
            icon = { Icon(Icons.Filled.BrightnessAuto, contentDescription = null) }
        ) {
            Text("System")
        }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            onClick = { onSelect(AppThemeModePreference.Light) },
            selected = selected == AppThemeModePreference.Light,
            icon = { Icon(Icons.Filled.LightMode, contentDescription = null) }
        ) {
            Text("Light")
        }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            onClick = { onSelect(AppThemeModePreference.Dark) },
            selected = selected == AppThemeModePreference.Dark,
            icon = { Icon(Icons.Filled.DarkMode, contentDescription = null) }
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
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            onClick = { onSelect(TextAlignmentPreference.Left) },
            selected = selected == TextAlignmentPreference.Left,
            icon = {
                Icon(
                    imageVector = Icons.Filled.FormatAlignLeft,
                    contentDescription = null
                )
            }
        ) {
            Text("Left")
        }
        SegmentedButton(
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            onClick = { onSelect(TextAlignmentPreference.Justified) },
            selected = selected == TextAlignmentPreference.Justified,
            icon = {
                Icon(
                    imageVector = Icons.Filled.FormatAlignJustify,
                    contentDescription = null
                )
            }
        ) {
            Text("Justified")
        }
    }
}

@Composable
private fun SliderPreference(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
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
