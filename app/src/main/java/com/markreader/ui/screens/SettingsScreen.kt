package com.markreader.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markreader.data.AppThemeModePreference
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.ReaderThemePreference
import com.markreader.data.TextAlignmentPreference
import android.os.Build
import java.util.Locale
import kotlin.math.abs

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
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SectionCard(
                title = "Appearance",
                subtitle = "App mode and reader surface defaults",
                icon = Icons.Filled.Palette
            ) {
                Text(
                    text = "App theme",
                    style = MaterialTheme.typography.bodyMedium
                )
                AppThemeModePreferenceControl(
                    selected = preferences.appThemeMode,
                    onSelect = viewModel::setAppThemeMode
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceDropdown(
                    label = "Reader light theme",
                    selectedLabel = preferences.readerLightTheme.displayLabel(),
                    options = listOf(
                        ReaderThemePreference.Light,
                        ReaderThemePreference.Sepia
                    ),
                    optionLabel = { it.displayLabel() },
                    onSelect = viewModel::setReaderLightTheme
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceDropdown(
                    label = "Reader dark theme",
                    selectedLabel = preferences.readerDarkTheme.displayLabel(),
                    options = listOf(
                        ReaderThemePreference.Dark,
                        ReaderThemePreference.Amoled
                    ),
                    optionLabel = { it.displayLabel() },
                    onSelect = viewModel::setReaderDarkTheme
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceDropdown(
                    label = "Reading font",
                    selectedLabel = preferences.readingFont.displayLabel(),
                    options = listOf(
                        ReadingFontPreference.Merriweather,
                        ReadingFontPreference.SystemSerif
                    ),
                    optionLabel = { it.displayLabel() },
                    onSelect = viewModel::setReadingFont
                )
                Spacer(modifier = Modifier.height(12.dp))
                PreferenceDropdown(
                    label = "Code font",
                    selectedLabel = preferences.codeFont.displayLabel(),
                    options = listOf(
                        CodeFontPreference.JetBrainsMono,
                        CodeFontPreference.SystemMono
                    ),
                    optionLabel = { it.displayLabel() },
                    onSelect = viewModel::setCodeFont
                )
            }

            SectionCard(
                title = "Typography",
                subtitle = "Scale and density for long-form reading",
                icon = Icons.Filled.TextFields
            ) {
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
                Spacer(modifier = Modifier.height(8.dp))
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

            SectionCard(
                title = "Layout",
                subtitle = "Choose how paragraphs align on screen",
                icon = Icons.Filled.Tune
            ) {
                AlignmentPreference(
                    selected = preferences.textAlignment,
                    onSelect = viewModel::setTextAlignment
                )
            }

            SectionCard(
                title = "About",
                subtitle = "App details and version",
                icon = Icons.Filled.Info
            ) {
                Text(
                    text = "MarkReader",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = versionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "A focused Markdown reader for local files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .offset(y = 2.dp)
            )
            content()
        }
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PreferenceDropdown(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                value = selectedLabel,
                onValueChange = { },
                readOnly = true,
                label = { Text(label) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.exposedDropdownSize()
            ) {
                options.forEach { option ->
                    val selected = optionLabel(option) == selectedLabel
                    DropdownMenuItem(
                        text = {
                            Text(optionLabel(option))
                        },
                        trailingIcon = {
                            if (selected) {
                                Text(
                                    "Selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

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
