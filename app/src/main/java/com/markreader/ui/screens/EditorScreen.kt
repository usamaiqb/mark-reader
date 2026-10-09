package com.markreader.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markreader.R
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.ui.resolve
import com.markreader.ui.reader.RenderedTextView
import com.markreader.ui.theme.CodeFontFamily
import com.markreader.ui.theme.ReadingFontFamily
import com.markreader.ui.theme.SansReadingFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    uriString: String?,
    onNavigateBack: () -> Unit,
    onFileSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel(
        factory = EditorViewModel.factory(
            LocalContext.current.applicationContext as Application,
            uriString
        )
    )
) {
    // Keyed on the save result rather than the callback, so read the caller's current one.
    val currentOnFileSaved by rememberUpdatedState(onFileSaved)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSystemDark = isSystemInDarkTheme()

    LaunchedEffect(isSystemDark) { viewModel.onSystemDarkThemeChanged(isSystemDark) }

    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Resolved here rather than in the effect below, which is not a composable scope.
    val savedMessage = stringResource(R.string.editor_saved)
    val cannotSaveDirectlyMessage = stringResource(R.string.editor_cannot_save_directly)
    val saveAsLabel = stringResource(R.string.editor_save_as)

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            if (uiState.isMarkdown) "text/markdown" else "text/plain"
        ),
        onResult = { uri -> if (uri != null) viewModel.onSaveAs(uri) }
    )

    LaunchedEffect(uiState.saveResult) {
        when (uiState.saveResult) {
            is SaveResult.Success -> {
                currentOnFileSaved()
                snackbarHostState.showSnackbar(savedMessage)
                viewModel.onSaveResultConsumed()
            }
            is SaveResult.NoPermission -> {
                val result = snackbarHostState.showSnackbar(
                    message = cannotSaveDirectlyMessage,
                    actionLabel = saveAsLabel,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    saveAsLauncher.launch(uiState.fileName)
                }
                viewModel.onSaveResultConsumed()
            }
            null -> {}
        }
    }

    BackHandler(enabled = uiState.isModified) { showDiscardDialog = true }

    val keyboardVisible = WindowInsets.isImeVisible

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isModified) showDiscardDialog = true else onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !keyboardVisible && uiState.isModified && !uiState.isLoading,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.editor_save)) },
                    icon = { Icon(Icons.Filled.Save, contentDescription = null) },
                    onClick = viewModel::save
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(uiState.errorMessage?.resolve() ?: stringResource(R.string.error_open_file))
                    }
                }
                else -> {
                    // Tabs for markdown
                    if (uiState.isMarkdown) {
                        PrimaryTabRow(selectedTabIndex = if (uiState.activeTab == EditorTab.Edit) 0 else 1) {
                            Tab(
                                selected = uiState.activeTab == EditorTab.Edit,
                                onClick = { viewModel.onTabChanged(EditorTab.Edit) }
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_tab_edit),
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Tab(
                                selected = uiState.activeTab == EditorTab.Preview,
                                onClick = { viewModel.onTabChanged(EditorTab.Preview) }
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_tab_preview),
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    // Editor or Preview content
                    val prefs = uiState.userPreferences
                    val editorFontFamily = if (uiState.isMarkdown) {
                        when (prefs.readingFont) {
                            ReadingFontPreference.Merriweather -> ReadingFontFamily
                            ReadingFontPreference.SystemSerif -> FontFamily.Serif
                            ReadingFontPreference.MerriweatherSans -> SansReadingFontFamily
                        }
                    } else {
                        when (prefs.codeFont) {
                            CodeFontPreference.JetBrainsMono -> CodeFontFamily
                            CodeFontPreference.SystemMono -> FontFamily.Monospace
                        }
                    }

                    val showEdit = !uiState.isMarkdown || uiState.activeTab == EditorTab.Edit

                    if (showEdit) {
                        val scrollState = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            val onSurface = MaterialTheme.colorScheme.onSurface
                            val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                            val primary = MaterialTheme.colorScheme.primary
                            BasicTextField(
                                value = uiState.textFieldValue,
                                onValueChange = viewModel::onTextChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                textStyle = TextStyle(
                                    fontFamily = editorFontFamily,
                                    fontSize = prefs.fontSizeSp.sp,
                                    lineHeight = (prefs.fontSizeSp * prefs.lineHeight).sp,
                                    color = onSurface
                                ),
                                cursorBrush = SolidColor(primary),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (uiState.textFieldValue.text.isEmpty()) {
                                            Text(
                                                text = stringResource(
                                                    if (uiState.isMarkdown) {
                                                        R.string.editor_placeholder_markdown
                                                    } else {
                                                        R.string.editor_placeholder_text
                                                    }
                                                ),
                                                style = TextStyle(
                                                    fontFamily = editorFontFamily,
                                                    fontSize = prefs.fontSizeSp.sp,
                                                    color = onSurfaceVariant
                                                )
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    } else {
                        // Preview tab
                        val previewText = uiState.previewText
                        if (previewText == null) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_rendering_preview),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            val previewTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
                            val previewIsDark = isSystemInDarkTheme()
                            // The preview never restores a scroll position.
                            val previewScrollY = remember { mutableIntStateOf(0) }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                RenderedTextView(
                                    text = previewText,
                                    textColor = previewTextColor,
                                    padding = PaddingValues(0.dp),
                                    savedScrollY = previewScrollY,
                                    scrollToOffset = null,
                                    onScrollChanged = { _, _ -> },
                                    onScrollExtentChanged = {},
                                    onScrollConsumed = {},
                                    headings = emptyList(),
                                    onActiveHeadingChanged = {},
                                    isCodeBlockWrapEnabled = true,
                                    selectionHighlightColor = if (previewIsDark) 0x99FFD54F.toInt() else 0x994285F4.toInt(),
                                    fontSizeSp = prefs.fontSizeSp,
                                    lineHeight = prefs.lineHeight,
                                    readingFont = prefs.readingFont,
                                    codeFont = prefs.codeFont,
                                    isSourceCode = false,
                                    textAlignment = prefs.textAlignment,
                                    codeBlockBackgroundColor = if (previewIsDark) 0x19FFFFFF.toInt() else 0x19000000.toInt()
                                )
                            }
                        }
                    }

                    // Formatting toolbar — markdown edit mode, visible when keyboard is up
                    if (uiState.isMarkdown && uiState.activeTab == EditorTab.Edit) {
                        AnimatedVisibility(visible = keyboardVisible) {
                            FormattingToolbar(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onNavigateBack()
                }) { Text(stringResource(R.string.editor_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun FormattingToolbar(viewModel: EditorViewModel) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                FormatIconButton(Icons.Filled.FormatBold, R.string.format_bold) { viewModel.formatBold() }
                FormatIconButton(Icons.Filled.FormatItalic, R.string.format_italic) { viewModel.formatItalic() }
                FormatIconButton(
                    Icons.Filled.FormatStrikethrough,
                    R.string.format_strikethrough
                ) { viewModel.formatStrikethrough() }
                FormatDivider()
                FormatTextButton("H1") { viewModel.formatHeading(1) }
                FormatTextButton("H2") { viewModel.formatHeading(2) }
                FormatTextButton("H3") { viewModel.formatHeading(3) }
                FormatDivider()
                FormatIconButton(
                    Icons.AutoMirrored.Filled.FormatListBulleted,
                    R.string.format_bullet_list
                ) { viewModel.formatBullet() }
                FormatIconButton(
                    Icons.Filled.FormatListNumbered,
                    R.string.format_numbered_list
                ) { viewModel.formatNumbered() }
                FormatIconButton(
                    Icons.Filled.HorizontalRule,
                    R.string.format_horizontal_rule
                ) { viewModel.formatHorizontalRule() }
                FormatDivider()
                FormatIconButton(Icons.Filled.Code, R.string.format_inline_code) { viewModel.formatInlineCode() }
                FormatTextButton("```") { viewModel.formatCodeBlock() }
                FormatIconButton(Icons.Filled.FormatQuote, R.string.format_blockquote) { viewModel.formatBlockquote() }
                FormatIconButton(Icons.Filled.Link, R.string.format_link) { viewModel.formatLink() }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun FormatIconButton(
    icon: ImageVector,
    @StringRes contentDescription: Int,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescription),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FormatTextButton(label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FormatDivider(modifier: Modifier = Modifier) {
    // Horizontal padding rather than two Spacers, so this emits a single element.
    VerticalDivider(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .height(24.dp)
    )
}
