package com.markreader.ui.screens

import android.app.Application
import android.graphics.Typeface
import android.os.Build
import android.content.Context
import android.text.Layout
import android.text.Spanned
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.markreader.ui.markdown.CodeBlockMarkerSpan
import com.markreader.ui.zoom.ZoomableContentLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.TextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markreader.R
import com.markreader.data.AppThemeModePreference
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.ReaderThemePreference
import com.markreader.data.TextAlignmentPreference
import com.markreader.ui.export.ExportManager
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
            launcher.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript", "application/x-yaml", "application/octet-stream"))
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

    val lightReaderColors = when (prefs.readerLightTheme) {
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
    val darkReaderColors = when (prefs.readerDarkTheme) {
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
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "surfaceColor"
    )
    val contentColor by animateColorAsState(
        targetValue = activeReaderColors.content,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
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
                            onClick = { launcher.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript", "application/x-yaml", "application/octet-stream")) }
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
                                launcher.launch(arrayOf("text/*", "application/json", "application/xml", "application/javascript", "application/x-yaml", "application/octet-stream"))
                            }
                        ) {
                            Text(text = "Open Different File")
                        }
                    }
                }
                uiState.viewMode == ViewMode.Raw -> {
                    val text = uiState.rawHighlighted ?: uiState.rawText
                    ContentContainer(
                        warningMessage = uiState.warningMessage
                    ) {
                        RenderedTextView(
                            text = text,
                            isDarkSurface = isSurfaceDark,
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
                            textAlignment = prefs.textAlignment
                        )
                    }
                }
                else -> {
                    val text = uiState.rendered ?: uiState.rawText
                    ContentContainer(
                        warningMessage = uiState.warningMessage
                    ) {
                        RenderedTextView(
                            text = text,
                            isDarkSurface = isSurfaceDark,
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
                            textAlignment = prefs.textAlignment
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

@Composable
private fun ContentContainer(
    warningMessage: String?,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!warningMessage.isNullOrBlank()) {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = warningMessage,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerSearchBar(
    query: String,
    matchIndex: Int,
    matchCount: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modeLabel: String,
    surfaceColor: Color,
    contentColor: Color,
    tonalContainerColor: Color,
    showBackButton: Boolean,
    modifier: Modifier = Modifier
) {
    val hasMatches = matchCount > 0
    // Single Surface fills edge-to-edge exactly like TopAppBar, with the same
    // chromeColors.surface — no tonal elevation overlay, no floating gaps.
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = surfaceColor,
        contentColor = contentColor
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Search in document",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = contentColor
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.72f)
                        )
                    }
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = contentColor
                            )
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = tonalContainerColor,
                    unfocusedContainerColor = tonalContainerColor,
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor,
                    focusedPlaceholderColor = contentColor.copy(alpha = 0.55f),
                    unfocusedPlaceholderColor = contentColor.copy(alpha = 0.55f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = contentColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )

            // Controls row — flat, no elevation, same surface as the row above.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AssistChip(
                    onClick = { },
                    enabled = false,
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = tonalContainerColor,
                        disabledLabelColor = contentColor
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = tonalContainerColor.copy(alpha = 0.8f)
                    ),
                    label = {
                        Text(
                            text = "$modeLabel • ${if (hasMatches) "${matchIndex + 1} of $matchCount" else "No matches"}"
                        )
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val disabledContent = contentColor.copy(alpha = 0.45f)
                    val disabledContainer = tonalContainerColor.copy(alpha = 0.6f)
                    FilledTonalIconButton(
                        onClick = onPrevious,
                        enabled = hasMatches,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = tonalContainerColor,
                            contentColor = contentColor,
                            disabledContainerColor = disabledContainer,
                            disabledContentColor = disabledContent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Previous match"
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onNext,
                        enabled = hasMatches,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = tonalContainerColor,
                            contentColor = contentColor,
                            disabledContainerColor = disabledContainer,
                            disabledContentColor = disabledContent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Next match"
                        )
                    }
                }
            }

            HorizontalDivider(color = tonalContainerColor.copy(alpha = 0.5f))
        }
    }
}
private data class ViewerColors(
    val surface: Color,
    val content: Color,
    val muted: Color,
    val tonalContainer: Color
)

private fun viewerChromeColors(
    activeReaderTheme: ReaderThemePreference,
    isSurfaceDark: Boolean,
    dynamicLightScheme: androidx.compose.material3.ColorScheme?,
    dynamicDarkScheme: androidx.compose.material3.ColorScheme?
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

private data class ContentKey(
    val textHash: Int,
    val fontSizeSp: Float,
    val lineHeight: Float,
    val readingFont: ReadingFontPreference,
    val codeFont: CodeFontPreference,
    val isSourceCode: Boolean,
    val textAlignment: TextAlignmentPreference
)

@Composable
private fun RenderedTextView(
    text: Any,
    isDarkSurface: Boolean,
    textColor: Int,
    padding: PaddingValues,
    savedScrollY: Int,
    scrollToOffset: Int?,
    onScrollChanged: (Int) -> Unit,
    onScrollConsumed: () -> Unit,
    headings: List<HeadingItem>,
    onActiveHeadingChanged: (Int) -> Unit,
    isWordWrapEnabled: Boolean,
    isCodeBlockWrapEnabled: Boolean,
    selectionHighlightColor: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference = CodeFontPreference.JetBrainsMono,
    isSourceCode: Boolean = false,
    textAlignment: TextAlignmentPreference
) {
    val contentKey = remember(text, fontSizeSp, lineHeight, readingFont, codeFont, isSourceCode, textAlignment) {
        ContentKey(
            textHash = text.hashCode(),
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            readingFont = readingFont,
            codeFont = codeFont,
            isSourceCode = isSourceCode,
            textAlignment = textAlignment
        )
    }
    var lastRestoredKey by remember { mutableStateOf<ContentKey?>(null) }
    var lastWrapEnabled by remember { mutableStateOf(isWordWrapEnabled) }
    var lastCodeBlockWrapEnabled by remember { mutableStateOf(isCodeBlockWrapEnabled) }
    var pendingAnchorOffset by remember { mutableStateOf<Int?>(null) }
    var lastTextHash by remember { mutableStateOf(0) }
    var lastStyleKey by remember {
        mutableStateOf(
            ContentKey(
                textHash = 0,
                fontSizeSp = -1f,
                lineHeight = -1f,
                readingFont = ReadingFontPreference.Merriweather,
                codeFont = CodeFontPreference.JetBrainsMono,
                isSourceCode = false,
                textAlignment = TextAlignmentPreference.Left
            )
        )
    }
    var lastTextColor by remember { mutableStateOf(textColor) }
    var lastWrapEnabledApplied by remember { mutableStateOf(isWordWrapEnabled) }
    var lastSelectionHighlightColor by remember { mutableStateOf(selectionHighlightColor) }
    var splitBoundaries by remember { mutableStateOf<List<Triple<Int, Int, Boolean>>>(emptyList()) }
    val currentHeadings by rememberUpdatedState(headings)
    val currentSplitBoundaries by rememberUpdatedState(splitBoundaries)
    val onActiveHeadingChangedState by rememberUpdatedState(onActiveHeadingChanged)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.TopCenter
    ) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 600.dp),
        factory = { context ->
            val density = context.resources.displayMetrics.density
            val paddingPx = (16 * density).toInt()
            val isSplitMode = isWordWrapEnabled && !isCodeBlockWrapEnabled && text is Spanned

            val scrollView = ScrollView(context).apply {
                if (isSplitMode) {
                    val container = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                    }
                    addView(
                        container,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                } else {
                    val textView = TextView(context).apply {
                        textSize = fontSizeSp
                        setLineSpacing(0f, lineHeight)
                        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                        setHorizontallyScrolling(!isWordWrapEnabled)
                        isHorizontalScrollBarEnabled = !isWordWrapEnabled
                        setTextIsSelectable(true)
                        setTextColor(textColor)
                        highlightColor = selectionHighlightColor
                    }
                    if (isWordWrapEnabled) {
                        addView(
                            textView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    } else {
                        val horizontalScrollView = HorizontalScrollView(context).apply {
                            isHorizontalScrollBarEnabled = true
                            addView(
                                textView,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            )
                        }
                        addView(
                            horizontalScrollView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    }
                }
                setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    onScrollChanged(scrollY)
                    val list = currentHeadings
                    if (list.isEmpty()) return@setOnScrollChangeListener
                    val firstChild = getChildAt(0) ?: return@setOnScrollChangeListener
                    if (firstChild is LinearLayout) {
                        onActiveHeadingChangedState(
                            findActiveHeadingInSplit(
                                firstChild, currentSplitBoundaries, list, scrollY
                            )
                        )
                    } else {
                        val tv = if (firstChild is HorizontalScrollView) {
                            firstChild.getChildAt(0) as? TextView
                        } else {
                            firstChild as? TextView
                        } ?: return@setOnScrollChangeListener
                        val layout = tv.layout ?: return@setOnScrollChangeListener
                        if (layout.text.isEmpty()) return@setOnScrollChangeListener
                        val y = scrollY + tv.paddingTop
                        onActiveHeadingChangedState(findActiveHeadingIndex(layout, list, y))
                    }
                }
            }
            val zoomLayout = ZoomableContentLayout(context).apply {
                addView(
                    scrollView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            zoomLayout
        },
        update = { zoomLayout ->
            val scrollView = zoomLayout.getChildAt(0) as ScrollView
            val child = scrollView.getChildAt(0) ?: return@AndroidView
            val isSplitMode = isWordWrapEnabled && !isCodeBlockWrapEnabled && text is Spanned
            val modeChanged = lastWrapEnabled != isWordWrapEnabled ||
                lastCodeBlockWrapEnabled != isCodeBlockWrapEnabled
            val density = scrollView.context.resources.displayMetrics.density
            val paddingPx = (16 * density).toInt()

            if (modeChanged) {
                // Save anchor from current view structure
                pendingAnchorOffset = getAnchorFromView(
                    scrollView, child, currentSplitBoundaries
                )
                scrollView.removeAllViews()

                if (isSplitMode) {
                    val container = LinearLayout(scrollView.context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                    }
                    scrollView.addView(
                        container,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                } else if (isWordWrapEnabled) {
                    val tv = createStyledTextView(
                        scrollView.context, paddingPx, fontSizeSp, lineHeight,
                        readingFont, codeFont, isSourceCode, textAlignment, textColor, selectionHighlightColor,
                        horizontalScroll = false
                    )
                    scrollView.addView(
                        tv,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                } else {
                    val tv = createStyledTextView(
                        scrollView.context, paddingPx, fontSizeSp, lineHeight,
                        readingFont, codeFont, isSourceCode, textAlignment, textColor, selectionHighlightColor,
                        horizontalScroll = true
                    )
                    val hsv = HorizontalScrollView(scrollView.context).apply {
                        isHorizontalScrollBarEnabled = true
                        addView(
                            tv,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    }
                    scrollView.addView(
                        hsv,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }

                lastWrapEnabled = isWordWrapEnabled
                lastCodeBlockWrapEnabled = isCodeBlockWrapEnabled
                // Force text and style to be re-applied below
                lastTextHash = 0
                lastStyleKey = ContentKey(0, -1f, -1f,
                    ReadingFontPreference.Merriweather, CodeFontPreference.JetBrainsMono,
                    false, TextAlignmentPreference.Left)
                lastTextColor = textColor.inv()
                lastWrapEnabledApplied = isWordWrapEnabled
                lastSelectionHighlightColor = selectionHighlightColor.inv()
            }

            // Text / style updates
            val currentChild = scrollView.getChildAt(0) ?: return@AndroidView

            if (currentChild is LinearLayout) {
                // Split mode
                val container = currentChild
                val textHash = text.hashCode()
                if (textHash != lastTextHash) {
                    val spanned = text as Spanned
                    val segments = splitByCodeBlocks(spanned)
                    splitBoundaries = segments
                    container.removeAllViews()
                    for ((start, end, isCode) in segments) {
                        val segText = spanned.subSequence(start, end) as Spanned
                        val tv = createStyledTextView(
                            container.context, 0, fontSizeSp, lineHeight,
                            readingFont, codeFont, isSourceCode, textAlignment, textColor, selectionHighlightColor,
                            horizontalScroll = isCode
                        )
                        tv.text = segText
                        if (isCode) {
                            val hsv = HorizontalScrollView(container.context).apply {
                                isHorizontalScrollBarEnabled = true
                                addView(
                                    tv,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    )
                                )
                            }
                            container.addView(
                                hsv,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            )
                        } else {
                            container.addView(
                                tv,
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            )
                        }
                    }
                    lastTextHash = textHash
                    lastStyleKey = contentKey
                    lastTextColor = textColor
                    lastSelectionHighlightColor = selectionHighlightColor
                } else if (lastStyleKey != contentKey || lastTextColor != textColor ||
                    lastSelectionHighlightColor != selectionHighlightColor
                ) {
                    for (i in 0 until container.childCount) {
                        val seg = container.getChildAt(i)
                        val tv = if (seg is HorizontalScrollView) {
                            seg.getChildAt(0) as? TextView
                        } else {
                            seg as? TextView
                        }
                        tv?.let {
                            applyStyleToTextView(
                                it, fontSizeSp, lineHeight, readingFont, codeFont, isSourceCode,
                                textAlignment, textColor, selectionHighlightColor
                            )
                        }
                    }
                    lastStyleKey = contentKey
                    lastTextColor = textColor
                    lastSelectionHighlightColor = selectionHighlightColor
                }
            } else {
                // Single-TV mode
                val textView = when (currentChild) {
                    is HorizontalScrollView -> currentChild.getChildAt(0) as TextView
                    else -> currentChild as TextView
                }

                val textHash = text.hashCode()
                if (textHash != lastTextHash) {
                    when (text) {
                        is Spanned -> textView.text = text
                        else -> textView.text = text.toString()
                    }
                    lastTextHash = textHash
                }
                if (lastStyleKey != contentKey) {
                    applyStyleToTextView(
                        textView, fontSizeSp, lineHeight, readingFont, codeFont, isSourceCode,
                        textAlignment, textColor, selectionHighlightColor
                    )
                    lastStyleKey = contentKey
                }
                if (lastTextColor != textColor) {
                    textView.setTextColor(textColor)
                    lastTextColor = textColor
                }
                if (lastWrapEnabledApplied != isWordWrapEnabled) {
                    textView.setHorizontallyScrolling(!isWordWrapEnabled)
                    textView.isHorizontalScrollBarEnabled = !isWordWrapEnabled
                    lastWrapEnabledApplied = isWordWrapEnabled
                }
                if (lastSelectionHighlightColor != selectionHighlightColor) {
                    textView.highlightColor = selectionHighlightColor
                    lastSelectionHighlightColor = selectionHighlightColor
                }
            }

            // Scroll handling
            if (pendingAnchorOffset != null) {
                val targetOffset = pendingAnchorOffset
                pendingAnchorOffset = null
                scrollView.post {
                    val y = resolveScrollY(
                        scrollView, currentSplitBoundaries, targetOffset ?: return@post
                    )
                    lastRestoredKey = contentKey
                    scrollView.smoothScrollTo(0, y)
                }
            } else if (scrollToOffset == null && savedScrollY > 0 &&
                lastRestoredKey != contentKey
            ) {
                lastRestoredKey = contentKey
                scrollView.post { scrollView.scrollTo(0, savedScrollY) }
            }

            if (scrollToOffset != null) {
                scrollView.post {
                    val y = resolveScrollY(
                        scrollView, currentSplitBoundaries, scrollToOffset
                    )
                    lastRestoredKey = contentKey
                    scrollView.smoothScrollTo(0, y)
                    if (zoomLayout.currentScale > 1f) zoomLayout.resetPan()
                    onScrollConsumed()
                }
            }

            // Heading tracking
            val curChild = scrollView.getChildAt(0)
            if (currentHeadings.isNotEmpty()) {
                if (curChild is LinearLayout) {
                    onActiveHeadingChangedState(
                        findActiveHeadingInSplit(
                            curChild, currentSplitBoundaries,
                            currentHeadings, scrollView.scrollY
                        )
                    )
                } else {
                    val tv = when (curChild) {
                        is HorizontalScrollView -> curChild.getChildAt(0) as? TextView
                        is TextView -> curChild
                        else -> null
                    }
                    val layout = tv?.layout
                    if (layout != null && layout.text.isNotEmpty()) {
                        val y = scrollView.scrollY + (tv?.paddingTop ?: 0)
                        onActiveHeadingChangedState(
                            findActiveHeadingIndex(layout, currentHeadings, y)
                        )
                    }
                }
            }
        }
    )
    }
}

// ---------- Helper functions ----------

private fun findActiveHeadingIndex(
    layout: Layout,
    headings: List<HeadingItem>,
    y: Int
): Int {
    var low = 0
    var high = headings.lastIndex
    var result = -1
    val textLength = layout.text.length
    while (low <= high) {
        val mid = (low + high) ushr 1
        val offset = headings[mid].offset.coerceIn(0, textLength - 1)
        val line = layout.getLineForOffset(offset)
        val top = layout.getLineTop(line)
        if (top <= y) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private fun findActiveHeadingInSplit(
    container: LinearLayout,
    boundaries: List<Triple<Int, Int, Boolean>>,
    headings: List<HeadingItem>,
    scrollY: Int
): Int {
    if (headings.isEmpty() || boundaries.isEmpty()) return -1
    var result = -1
    val adjustedY = scrollY + container.paddingTop
    for (i in 0 until container.childCount.coerceAtMost(boundaries.size)) {
        val child = container.getChildAt(i)
        val (segStart, segEnd, _) = boundaries[i]
        val tv = if (child is HorizontalScrollView) {
            child.getChildAt(0) as? TextView
        } else {
            child as? TextView
        } ?: continue
        val layout = tv.layout ?: continue
        for (h in headings.indices) {
            val offset = headings[h].offset
            if (offset < segStart || offset >= segEnd) continue
            val localOffset = (offset - segStart).coerceIn(0, layout.text.length - 1)
            val line = layout.getLineForOffset(localOffset)
            val top = child.top + layout.getLineTop(line)
            if (top <= adjustedY) {
                result = h
            }
        }
    }
    return result
}

private fun getAnchorFromView(
    scrollView: ScrollView,
    child: android.view.View,
    boundaries: List<Triple<Int, Int, Boolean>>
): Int? {
    val sy = scrollView.scrollY
    when (child) {
        is LinearLayout -> {
            for (i in 0 until child.childCount.coerceAtMost(boundaries.size)) {
                val seg = child.getChildAt(i)
                if (seg.top + seg.height > sy) {
                    val (segStart, _, _) = boundaries[i]
                    val tv = if (seg is HorizontalScrollView) {
                        seg.getChildAt(0) as? TextView
                    } else {
                        seg as? TextView
                    }
                    val layout = tv?.layout
                    return if (layout != null) {
                        val localY = (sy - seg.top).coerceAtLeast(0)
                        val line = layout.getLineForVertical(localY)
                        segStart + layout.getLineStart(line)
                    } else {
                        segStart
                    }
                }
            }
            return boundaries.lastOrNull()?.first
        }
        is HorizontalScrollView -> {
            val tv = child.getChildAt(0) as? TextView
            val layout = tv?.layout ?: return null
            val line = layout.getLineForVertical(sy)
            return layout.getLineStart(line)
        }
        is TextView -> {
            val layout = child.layout ?: return null
            val line = layout.getLineForVertical(sy)
            return layout.getLineStart(line)
        }
        else -> return null
    }
}

private fun resolveScrollY(
    scrollView: ScrollView,
    boundaries: List<Triple<Int, Int, Boolean>>,
    offset: Int
): Int {
    val child = scrollView.getChildAt(0) ?: return 0
    if (child is LinearLayout && boundaries.isNotEmpty()) {
        return scrollToOffsetInSplit(child, boundaries, offset)
    }
    val tv = when (child) {
        is HorizontalScrollView -> child.getChildAt(0) as? TextView
        is TextView -> child
        else -> null
    } ?: return 0
    val layout = tv.layout ?: return 0
    val line = layout.getLineForOffset(offset)
    return layout.getLineTop(line)
}

private fun scrollToOffsetInSplit(
    container: LinearLayout,
    boundaries: List<Triple<Int, Int, Boolean>>,
    offset: Int
): Int {
    for (i in boundaries.indices) {
        val (segStart, segEnd, _) = boundaries[i]
        if (offset < segStart || offset >= segEnd) continue
        if (i >= container.childCount) break
        val child = container.getChildAt(i)
        val tv = if (child is HorizontalScrollView) {
            child.getChildAt(0) as? TextView
        } else {
            child as? TextView
        } ?: continue
        val layout = tv.layout ?: continue
        val localOffset = (offset - segStart).coerceIn(
            0, layout.text.length.coerceAtLeast(1) - 1
        )
        val line = layout.getLineForOffset(localOffset)
        return child.top + layout.getLineTop(line) + container.paddingTop
    }
    return 0
}

private fun splitByCodeBlocks(text: Spanned): List<Triple<Int, Int, Boolean>> {
    val markers = text.getSpans(0, text.length, CodeBlockMarkerSpan::class.java)
    if (markers.isEmpty()) return listOf(Triple(0, text.length, false))
    val codeRanges = markers.map {
        text.getSpanStart(it) to text.getSpanEnd(it)
    }.sortedBy { it.first }
    val segments = mutableListOf<Triple<Int, Int, Boolean>>()
    var pos = 0
    for ((start, end) in codeRanges) {
        if (start > pos) {
            segments.add(Triple(pos, start, false))
        }
        segments.add(Triple(start, end, true))
        pos = end
    }
    if (pos < text.length) {
        segments.add(Triple(pos, text.length, false))
    }
    return segments
}

private fun resolveTypeface(
    context: Context,
    isSourceCode: Boolean,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference
): Typeface = if (isSourceCode) {
    when (codeFont) {
        CodeFontPreference.JetBrainsMono -> try {
            androidx.core.content.res.ResourcesCompat.getFont(
                context, R.font.jetbrains_mono_regular
            ) ?: Typeface.MONOSPACE
        } catch (_: Exception) { Typeface.MONOSPACE }
        CodeFontPreference.SystemMono -> Typeface.MONOSPACE
    }
} else {
    when (readingFont) {
        ReadingFontPreference.Merriweather -> try {
            androidx.core.content.res.ResourcesCompat.getFont(
                context, R.font.merriweather_regular
            ) ?: Typeface.SERIF
        } catch (_: Exception) { Typeface.SERIF }
        ReadingFontPreference.SystemSerif -> Typeface.SERIF
    }
}

private fun createStyledTextView(
    context: Context,
    paddingPx: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference,
    isSourceCode: Boolean,
    textAlignment: TextAlignmentPreference,
    textColor: Int,
    selectionHighlightColor: Int,
    horizontalScroll: Boolean
): TextView {
    return TextView(context).apply {
        textSize = fontSizeSp
        setLineSpacing(0f, lineHeight)
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        setHorizontallyScrolling(horizontalScroll)
        isHorizontalScrollBarEnabled = horizontalScroll
        setTextIsSelectable(true)
        setTextColor(textColor)
        highlightColor = selectionHighlightColor
        typeface = resolveTypeface(context, isSourceCode, readingFont, codeFont)
        if (textAlignment == TextAlignmentPreference.Justified &&
            Build.VERSION.SDK_INT >= 26
        ) {
            justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
        } else {
            if (Build.VERSION.SDK_INT >= 26) {
                justificationMode = Layout.JUSTIFICATION_MODE_NONE
            }
            this.textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
        }
    }
}

private fun applyStyleToTextView(
    tv: TextView,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference,
    isSourceCode: Boolean,
    textAlignment: TextAlignmentPreference,
    textColor: Int,
    selectionHighlightColor: Int
) {
    tv.textSize = fontSizeSp
    tv.setLineSpacing(0f, lineHeight)
    tv.setTextColor(textColor)
    tv.highlightColor = selectionHighlightColor
    tv.typeface = resolveTypeface(tv.context, isSourceCode, readingFont, codeFont)
    if (textAlignment == TextAlignmentPreference.Justified &&
        Build.VERSION.SDK_INT >= 26
    ) {
        tv.justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
    } else {
        if (Build.VERSION.SDK_INT >= 26) {
            tv.justificationMode = Layout.JUSTIFICATION_MODE_NONE
        }
        tv.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
    }
}
