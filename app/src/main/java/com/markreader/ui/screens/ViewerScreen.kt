package com.markreader.ui.screens

import android.app.Application
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markreader.R
import com.markreader.data.AppThemeModePreference
import com.markreader.data.ReaderThemePreference
import com.markreader.OPENABLE_MIME_TYPES
import com.markreader.ui.export.ExportManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    onOpenSettings: () -> Unit,
    onOpenEditor: (String, Boolean) -> Unit = { _, _ -> },
    uriString: String?,
    fileSaved: Boolean = false,
    onFileSavedConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: ViewerViewModel = viewModel(
        factory = ViewerViewModel.factory(context.applicationContext as Application, uriString)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollToOffset by viewModel.scrollToOffset.collectAsStateWithLifecycle()
    val savedScrollY by viewModel.scrollY.collectAsStateWithLifecycle()
    val prefs = uiState.userPreferences
    val isSystemDark = isSystemInDarkTheme()

    var isReadingSurfaceDark by rememberSaveable { mutableStateOf(false) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isTocVisible by rememberSaveable { mutableStateOf(false) }
    var isExportDialogVisible by rememberSaveable { mutableStateOf(false) }
    var isWordWrapEnabled by rememberSaveable { mutableStateOf(true) }
    var isCodeBlockWrapEnabled by rememberSaveable { mutableStateOf(true) }
    val exportManager = remember { ExportManager(context) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ex: SecurityException) {
                    // Ignore: provider may not allow persistable grants.
                } catch (ex: IllegalArgumentException) {
                    // Ignore: not a persistable URI.
                }
                viewModel.loadUri(uri.toString())
            }
        }
    )

    LaunchedEffect(uiState.needsPermission) {
        if (uiState.needsPermission) {
            viewModel.onPermissionRequestConsumed()
            launcher.launch(OPENABLE_MIME_TYPES)
        }
    }

    LaunchedEffect(uriString) {
        if (!uriString.isNullOrBlank()) {
            viewModel.loadUri(uriString)
        }
    }
    LaunchedEffect(isSystemDark) {
        viewModel.onSystemDarkThemeChanged(isSystemDark)
    }
    LaunchedEffect(uiState.isSourceCode) {
        isWordWrapEnabled = !uiState.isSourceCode
    }
    LaunchedEffect(fileSaved) {
        if (fileSaved) {
            uriString?.let { viewModel.loadUri(it, forceReload = true) }
            onFileSavedConsumed()
        }
    }

    val fileName = if (uiState.fileName.isNotBlank()) uiState.fileName else "Untitled"
    val viewModeLabel = when {
        uiState.isSourceCode -> "Source code"
        uiState.viewMode == ViewMode.Raw -> "Raw mode"
        else -> "Rendered mode"
    }

    val dynamicLightScheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context) else null
    }
    val dynamicDarkScheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else null
    }

    val (lightReaderColors, darkReaderColors) = resolveReaderColors(
        readerLightTheme = prefs.readerLightTheme,
        readerDarkTheme = prefs.readerDarkTheme,
        dynamicLightScheme = dynamicLightScheme,
        dynamicDarkScheme = dynamicDarkScheme
    )
    val isBaseDark = when (prefs.appThemeMode) {
        AppThemeModePreference.System -> isSystemDark
        AppThemeModePreference.Light -> false
        AppThemeModePreference.Dark -> true
    }
    val isSurfaceDark = if (isReadingSurfaceDark) !isBaseDark else isBaseDark
    val activeReaderTheme = if (isSurfaceDark) prefs.readerDarkTheme else prefs.readerLightTheme
    val activeReaderColors = if (isSurfaceDark) darkReaderColors else lightReaderColors
    val chromeColors = viewerChromeColors(
        activeReaderTheme = activeReaderTheme,
        isSurfaceDark = isSurfaceDark,
        dynamicLightScheme = dynamicLightScheme,
        dynamicDarkScheme = dynamicDarkScheme
    )

    val surfaceColor by animateColorAsState(
        targetValue = activeReaderColors.surface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "surfaceColor"
    )
    val contentColor by animateColorAsState(
        targetValue = activeReaderColors.content,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "contentColor"
    )
    val selectionHighlightColor = when {
        isSurfaceDark -> 0x99FFD54F.toInt()
        activeReaderTheme == ReaderThemePreference.Sepia -> 0x99A85A00.toInt()
        else -> 0x994285F4.toInt()
    }

    LaunchedEffect(isSurfaceDark) {
        viewModel.onReadingSurfaceModeChanged(isSurfaceDark)
    }

    val view = LocalView.current
    DisposableEffect(isSurfaceDark) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousLightStatusBars = controller.isAppearanceLightStatusBars

            controller.isAppearanceLightStatusBars = !isSurfaceDark

            onDispose {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
            }
        } else {
            onDispose { }
        }
    }

    BackHandler(enabled = uiState.isSearchActive) {
        viewModel.onSearchQueryChanged("")
        viewModel.onSearchToggled()
    }

    // Determine if this file is editable (loaded, not binary, not error)
    val canEdit = !uiState.isLoading && uiState.errorMessage == null &&
        !uiState.isEmptyFile && uriString != null
    val isMarkdownFile = !uiState.isSourceCode &&
        uiState.fileName.substringAfterLast('.', "").lowercase().let { it == "md" || it == "markdown" }

    Scaffold(
        floatingActionButton = {
            if (canEdit && !uiState.isSearchActive) {
                FloatingActionButton(
                    onClick = {
                        onOpenEditor(uriString!!, isMarkdownFile)
                    },
                    containerColor = chromeColors.tonalContainer,
                    contentColor = chromeColors.content
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit file")
                }
            }
        },
        topBar = {
            Column {
                if (uiState.isSearchActive) {
                    ViewerSearchBar(
                        query = uiState.searchQuery,
                        matchIndex = uiState.searchMatchIndex,
                        matchCount = uiState.searchMatchCount,
                        onQueryChange = viewModel::onSearchQueryChanged,
                        onNext = viewModel::onNextMatch,
                        onPrevious = viewModel::onPreviousMatch,
                        onClear = {
                            viewModel.onSearchQueryChanged("")
                        },
                        onBack = {
                            viewModel.onSearchQueryChanged("")
                            viewModel.onSearchToggled()
                        },
                        modeLabel = viewModeLabel,
                        surfaceColor = chromeColors.surface,
                        contentColor = chromeColors.content,
                        tonalContainerColor = chromeColors.tonalContainer,
                        showBackButton = true
                    )
                } else {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = chromeColors.surface,
                            titleContentColor = chromeColors.content,
                            navigationIconContentColor = chromeColors.content,
                            actionIconContentColor = chromeColors.content
                        ),
                        title = {
                            Column {
                                Text(
                                    text = fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = viewModeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = chromeColors.muted
                                )
                            }
                        },
                        navigationIcon = {
                            if (!uiState.isSourceCode) {
                                FilledTonalIconButton(
                                    onClick = { isTocVisible = true },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = chromeColors.tonalContainer,
                                        contentColor = chromeColors.content
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = "Table of contents"
                                    )
                                }
                            }
                        },
                        actions = {
                            FilledTonalIconButton(
                                onClick = viewModel::onSearchToggled,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = chromeColors.tonalContainer,
                                    contentColor = chromeColors.content
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search"
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalIconButton(
                                onClick = { isMenuExpanded = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = chromeColors.tonalContainer,
                                    contentColor = chromeColors.content
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More options"
                                )
                            }
                            DropdownMenu(
                                expanded = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false },
                                modifier = Modifier.background(chromeColors.surface)
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.WrapText,
                                            contentDescription = null,
                                            tint = chromeColors.content
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "Wrap long lines",
                                            color = chromeColors.content
                                        )
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.Switch(
                                            checked = isWordWrapEnabled,
                                            onCheckedChange = null,
                                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                                checkedThumbColor = chromeColors.content,
                                                checkedTrackColor = chromeColors.tonalContainer,
                                                uncheckedThumbColor = chromeColors.muted,
                                                uncheckedTrackColor = chromeColors.tonalContainer
                                            )
                                        )
                                    },
                                    onClick = {
                                        isWordWrapEnabled = !isWordWrapEnabled
                                    }
                                )
                                if (isWordWrapEnabled) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.Code,
                                                contentDescription = null,
                                                tint = chromeColors.content
                                            )
                                        },
                                        text = {
                                            Text(
                                                text = "Wrap code blocks",
                                                color = chromeColors.content
                                            )
                                        },
                                        trailingIcon = {
                                            androidx.compose.material3.Switch(
                                                checked = isCodeBlockWrapEnabled,
                                                onCheckedChange = null,
                                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                                    checkedThumbColor = chromeColors.content,
                                                    checkedTrackColor = chromeColors.tonalContainer,
                                                    uncheckedThumbColor = chromeColors.muted,
                                                    uncheckedTrackColor = chromeColors.tonalContainer
                                                )
                                            )
                                        },
                                        onClick = {
                                            isCodeBlockWrapEnabled = !isCodeBlockWrapEnabled
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isReadingSurfaceDark) {
                                                Icons.Filled.DarkMode
                                            } else {
                                                Icons.Filled.LightMode
                                            },
                                            contentDescription = null,
                                            tint = chromeColors.content
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = if (isReadingSurfaceDark) {
                                                "Reader surface: Dark"
                                            } else {
                                                "Reader surface: Light"
                                            },
                                            color = chromeColors.content
                                        )
                                    },
                                    trailingIcon = {
                                        Text(
                                            text = if (isReadingSurfaceDark) "Dark" else "Light",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = chromeColors.muted
                                        )
                                    },
                                    onClick = {
                                        isReadingSurfaceDark = !isReadingSurfaceDark
                                    }
                                )
                                HorizontalDivider(color = chromeColors.tonalContainer)
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = null,
                                            tint = chromeColors.content
                                        )
                                    },
                                    text = { Text(text = "Export", color = chromeColors.content) },
                                    onClick = {
                                        isMenuExpanded = false
                                        isExportDialogVisible = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = null,
                                            tint = chromeColors.content
                                        )
                                    },
                                    text = { Text(text = "Share Raw", color = chromeColors.content) },
                                    onClick = {
                                        isMenuExpanded = false
                                        if (uiState.rawText.isNotBlank()) {
                                            exportManager.shareRawMarkdown(uiState.rawText)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Settings,
                                            contentDescription = null,
                                            tint = chromeColors.content
                                        )
                                    },
                                    text = { Text(text = "Settings", color = chromeColors.content) },
                                    onClick = {
                                        isMenuExpanded = false
                                        onOpenSettings()
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues: PaddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = surfaceColor,
            contentColor = contentColor
        ) {
            when {
                uiState.isLoadingVisible -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.isEmptyFile -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "This file is empty.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Button(
                            onClick = { launcher.launch(OPENABLE_MIME_TYPES) }
                        ) {
                            Text(text = "Open Different File")
                        }
                    }
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Unable to open file.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Button(
                            onClick = {
                                launcher.launch(OPENABLE_MIME_TYPES)
                            }
                        ) {
                            Text(text = "Open Different File")
                        }
                    }
                }
                else -> {
                    val text = if (uiState.viewMode == ViewMode.Raw) {
                        uiState.rawHighlighted ?: uiState.rawText
                    } else {
                        uiState.rendered ?: uiState.rawText
                    }
                    ContentContainer(
                        warningMessage = uiState.warningMessage
                    ) {
                        RenderedTextView(
                            text = text,
                            textColor = contentColor.toArgb(),
                            padding = PaddingValues(0.dp),
                            savedScrollY = savedScrollY,
                            scrollToOffset = scrollToOffset,
                            onScrollChanged = viewModel::onScrollPositionChanged,
                            onScrollConsumed = viewModel::onScrollConsumed,
                            headings = uiState.headings,
                            onActiveHeadingChanged = viewModel::onActiveHeadingChanged,
                            isWordWrapEnabled = isWordWrapEnabled,
                            isCodeBlockWrapEnabled = isCodeBlockWrapEnabled,
                            selectionHighlightColor = selectionHighlightColor,
                            fontSizeSp = prefs.fontSizeSp,
                            lineHeight = prefs.lineHeight,
                            readingFont = prefs.readingFont,
                            codeFont = prefs.codeFont,
                            isSourceCode = uiState.isSourceCode,
                            textAlignment = prefs.textAlignment,
                            codeBlockBackgroundColor = if (isSurfaceDark) {
                                0x19FFFFFF.toInt()
                            } else {
                                0x19000000.toInt()
                            }
                        )
                    }
                }
            }
        }
    }

    if (isTocVisible) {
        ModalBottomSheet(
            onDismissRequest = { isTocVisible = false },
            containerColor = chromeColors.surface,
            contentColor = chromeColors.content
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Surface(
                    tonalElevation = 2.dp,
                    color = chromeColors.surface,
                    contentColor = chromeColors.content
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Table of Contents",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${uiState.headings.size} heading${if (uiState.headings.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = chromeColors.muted
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { isTocVisible = false },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = chromeColors.tonalContainer,
                                contentColor = chromeColors.content
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close"
                            )
                        }
                    }
                }
                HorizontalDivider()
                if (uiState.headings.isEmpty()) {
                    Text(
                        text = "No headings found.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistChip(
                            onClick = { },
                            enabled = false,
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = chromeColors.tonalContainer,
                                disabledLabelColor = chromeColors.content
                            ),
                            label = { Text("Jump to section") }
                        )
                        if (uiState.activeHeadingIndex >= 0) {
                            val activeText = uiState.headings
                                .getOrNull(uiState.activeHeadingIndex)
                                ?.text
                                ?.take(18)
                                ?.ifBlank { "Active section" }
                                ?: "Active section"
                            AssistChip(
                                onClick = { },
                                enabled = false,
                                colors = AssistChipDefaults.assistChipColors(
                                    disabledContainerColor = chromeColors.tonalContainer,
                                    disabledLabelColor = chromeColors.content
                                ),
                                label = { Text("Current: $activeText") }
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        itemsIndexed(uiState.headings) { index, heading ->
                            val indent = when (heading.level) {
                                1 -> 0.dp
                                2 -> 12.dp
                                else -> 24.dp
                            }
                            val isActive = index == uiState.activeHeadingIndex
                            val background =
                                if (isActive) chromeColors.tonalContainer else chromeColors.surface
                            val textColor =
                                chromeColors.content
                            Surface(
                                color = background,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.onHeadingSelected(heading.offset)
                                        isTocVisible = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = indent, top = 6.dp, bottom = 6.dp, end = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = chromeColors.tonalContainer,
                                        contentColor = chromeColors.content,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "H${heading.level}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = heading.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isExportDialogVisible) {
        AlertDialog(
            onDismissRequest = { isExportDialogVisible = false },
            title = { Text(text = "Export") },
            text = { Text(text = "Choose an export format.") },
            confirmButton = {
                Row {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            isExportDialogVisible = false
                            exportManager.exportPdf(uiState.rawText, activeReaderTheme, fileName)
                        }
                    ) {
                        Text(text = "PDF")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.TextButton(
                        onClick = {
                            isExportDialogVisible = false
                            exportManager.exportHtml(uiState.rawText, activeReaderTheme)
                        }
                    ) {
                        Text(text = "HTML")
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { isExportDialogVisible = false }
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}
