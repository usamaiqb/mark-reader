package com.markreader.ui.screens

import android.app.Application
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WrapText
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
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
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
import com.markreader.data.ReadingFontPreference
import com.markreader.data.ReaderThemePreference
import com.markreader.data.TextAlignmentPreference
import com.markreader.ui.export.ExportManager
import com.markreader.ui.theme.AmoledOnSurface
import com.markreader.ui.theme.AmoledSurface
import com.markreader.ui.theme.DarkOnSurface
import com.markreader.ui.theme.DarkSurface
import com.markreader.ui.theme.LightOnSurface
import com.markreader.ui.theme.LightSurface
import com.markreader.ui.theme.SepiaOnSurface
import com.markreader.ui.theme.SepiaSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    onOpenSettings: () -> Unit,
    uriString: String?
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
            launcher.launch(arrayOf("text/markdown", "text/plain"))
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

    val fileName = if (uiState.fileName.isNotBlank()) uiState.fileName else "Untitled"
    val viewModeLabel = if (uiState.viewMode == ViewMode.Raw) "Raw mode" else "Rendered mode"

    val dynamicLightScheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context) else null
    }
    val dynamicDarkScheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else null
    }

    val lightReaderColors = when (prefs.readerLightTheme) {
        ReaderThemePreference.Sepia -> ReaderSurfaceColors(
            surface = SepiaSurface,
            content = SepiaOnSurface,
            muted = SepiaOnSurface.copy(alpha = 0.72f),
            tonalContainer = SepiaOnSurface.copy(alpha = 0.10f)
        )
        ReaderThemePreference.Light -> ReaderSurfaceColors(
            surface = dynamicLightScheme?.surface ?: LightSurface,
            content = dynamicLightScheme?.onSurface ?: LightOnSurface,
            muted = dynamicLightScheme?.onSurfaceVariant ?: (dynamicLightScheme?.onSurface
                ?: LightOnSurface).copy(alpha = 0.72f),
            tonalContainer = dynamicLightScheme?.secondaryContainer
                ?: (dynamicLightScheme?.onSurface ?: LightOnSurface).copy(alpha = 0.10f)
        )
        ReaderThemePreference.Dark -> ReaderSurfaceColors(
            surface = dynamicDarkScheme?.surface ?: DarkSurface,
            content = dynamicDarkScheme?.onSurface ?: DarkOnSurface,
            muted = dynamicDarkScheme?.onSurfaceVariant ?: (dynamicDarkScheme?.onSurface
                ?: DarkOnSurface).copy(alpha = 0.72f),
            tonalContainer = dynamicDarkScheme?.secondaryContainer
                ?: (dynamicDarkScheme?.onSurface ?: DarkOnSurface).copy(alpha = 0.16f)
        )
        ReaderThemePreference.Amoled -> ReaderSurfaceColors(
            surface = AmoledSurface,
            content = AmoledOnSurface,
            muted = AmoledOnSurface.copy(alpha = 0.72f),
            tonalContainer = AmoledOnSurface.copy(alpha = 0.16f)
        )
    }
    val darkReaderColors = when (prefs.readerDarkTheme) {
        ReaderThemePreference.Amoled -> ReaderSurfaceColors(
            surface = AmoledSurface,
            content = AmoledOnSurface,
            muted = AmoledOnSurface.copy(alpha = 0.72f),
            tonalContainer = AmoledOnSurface.copy(alpha = 0.16f)
        )
        ReaderThemePreference.Dark -> ReaderSurfaceColors(
            surface = dynamicDarkScheme?.surface ?: DarkSurface,
            content = dynamicDarkScheme?.onSurface ?: DarkOnSurface,
            muted = dynamicDarkScheme?.onSurfaceVariant ?: (dynamicDarkScheme?.onSurface
                ?: DarkOnSurface).copy(alpha = 0.72f),
            tonalContainer = dynamicDarkScheme?.secondaryContainer
                ?: (dynamicDarkScheme?.onSurface ?: DarkOnSurface).copy(alpha = 0.16f)
        )
        ReaderThemePreference.Light -> ReaderSurfaceColors(
            surface = dynamicLightScheme?.surface ?: LightSurface,
            content = dynamicLightScheme?.onSurface ?: LightOnSurface,
            muted = dynamicLightScheme?.onSurfaceVariant ?: (dynamicLightScheme?.onSurface
                ?: LightOnSurface).copy(alpha = 0.72f),
            tonalContainer = dynamicLightScheme?.secondaryContainer
                ?: (dynamicLightScheme?.onSurface ?: LightOnSurface).copy(alpha = 0.10f)
        )
        ReaderThemePreference.Sepia -> ReaderSurfaceColors(
            surface = SepiaSurface,
            content = SepiaOnSurface,
            muted = SepiaOnSurface.copy(alpha = 0.72f),
            tonalContainer = SepiaOnSurface.copy(alpha = 0.10f)
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
    DisposableEffect(isSurfaceDark, chromeColors.surface) {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            val previousStatusBarColor = window.statusBarColor
            val previousLightStatusBars = WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars

            window.statusBarColor = chromeColors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isSurfaceDark

            onDispose {
                window.statusBarColor = previousStatusBarColor
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    previousLightStatusBars
            }
        } else {
            onDispose { }
        }
    }

    BackHandler(enabled = uiState.isSearchActive) {
        viewModel.onSearchQueryChanged("")
        viewModel.onSearchToggled()
    }

    Scaffold(
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
                            FilledTonalIconButton(
                                onClick = { isTocVisible = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = chromeColors.tonalContainer,
                                    contentColor = chromeColors.content
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FormatListBulleted,
                                    contentDescription = "Table of contents"
                                )
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
                                            imageVector = Icons.Filled.WrapText,
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
                            onClick = { launcher.launch(arrayOf("text/markdown", "text/plain")) }
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
                                launcher.launch(arrayOf("text/markdown", "text/plain"))
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
                            padding = PaddingValues(0.dp),
                            savedScrollY = savedScrollY,
                            scrollToOffset = scrollToOffset,
                            onScrollChanged = viewModel::onScrollPositionChanged,
                            onScrollConsumed = viewModel::onScrollConsumed,
                            headings = uiState.headings,
                            onActiveHeadingChanged = viewModel::onActiveHeadingChanged,
                            isWordWrapEnabled = isWordWrapEnabled,
                            selectionHighlightColor = selectionHighlightColor,
                            fontSizeSp = prefs.fontSizeSp,
                            lineHeight = prefs.lineHeight,
                            readingFont = prefs.readingFont,
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
                            padding = PaddingValues(0.dp),
                            savedScrollY = savedScrollY,
                            scrollToOffset = scrollToOffset,
                            onScrollChanged = viewModel::onScrollPositionChanged,
                            onScrollConsumed = viewModel::onScrollConsumed,
                            headings = uiState.headings,
                            onActiveHeadingChanged = viewModel::onActiveHeadingChanged,
                            isWordWrapEnabled = isWordWrapEnabled,
                            selectionHighlightColor = selectionHighlightColor,
                            fontSizeSp = prefs.fontSizeSp,
                            lineHeight = prefs.lineHeight,
                            readingFont = prefs.readingFont,
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
            DockedSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onNext() },
                active = false,
                onActiveChange = { },
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
                                imageVector = Icons.Filled.ArrowBack,
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
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = contentColor
                            )
                        }
                    }
                },
                colors = SearchBarDefaults.colors(
                    containerColor = surfaceColor,
                    dividerColor = tonalContainerColor,
                    inputFieldColors = TextFieldDefaults.colors(
                        focusedContainerColor = tonalContainerColor,
                        unfocusedContainerColor = tonalContainerColor,
                        focusedTextColor = contentColor,
                        unfocusedTextColor = contentColor,
                        focusedPlaceholderColor = contentColor.copy(alpha = 0.55f),
                        unfocusedPlaceholderColor = contentColor.copy(alpha = 0.55f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = contentColor
                    )
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .border(
                        width = 1.dp,
                        color = tonalContainerColor.copy(alpha = 0.55f),
                        shape = SearchBarDefaults.dockedShape
                    )
            ) {}

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
private data class ReaderSurfaceColors(
    val surface: Color,
    val content: Color,
    val muted: Color,
    val tonalContainer: Color
)

private data class ViewerChromeColors(
    val surface: Color,
    val content: Color,
    val muted: Color,
    val tonalContainer: Color
)

private fun viewerChromeColors(
    isSurfaceDark: Boolean,
    dynamicLightScheme: androidx.compose.material3.ColorScheme?,
    dynamicDarkScheme: androidx.compose.material3.ColorScheme?
): ViewerChromeColors {
    return if (isSurfaceDark) {
        val scheme = dynamicDarkScheme
        ViewerChromeColors(
            surface = scheme?.surface ?: DarkSurface,
            content = scheme?.onSurface ?: DarkOnSurface,
            muted = scheme?.onSurfaceVariant ?: (scheme?.onSurface ?: DarkOnSurface).copy(alpha = 0.72f),
            tonalContainer = scheme?.secondaryContainer
                ?: (scheme?.onSurface ?: DarkOnSurface).copy(alpha = 0.16f)
        )
    } else {
        val scheme = dynamicLightScheme
        ViewerChromeColors(
            surface = scheme?.surface ?: LightSurface,
            content = scheme?.onSurface ?: LightOnSurface,
            muted = scheme?.onSurfaceVariant ?: (scheme?.onSurface ?: LightOnSurface).copy(alpha = 0.72f),
            tonalContainer = scheme?.secondaryContainer
                ?: (scheme?.onSurface ?: LightOnSurface).copy(alpha = 0.10f)
        )
    }
}

private data class ContentKey(
    val textHash: Int,
    val fontSizeSp: Float,
    val lineHeight: Float,
    val readingFont: ReadingFontPreference,
    val textAlignment: TextAlignmentPreference
)

@Composable
private fun RenderedTextView(
    text: Any,
    isDarkSurface: Boolean,
    padding: PaddingValues,
    savedScrollY: Int,
    scrollToOffset: Int?,
    onScrollChanged: (Int) -> Unit,
    onScrollConsumed: () -> Unit,
    headings: List<HeadingItem>,
    onActiveHeadingChanged: (Int) -> Unit,
    isWordWrapEnabled: Boolean,
    selectionHighlightColor: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    textAlignment: TextAlignmentPreference
) {
    val contentKey = remember(text, fontSizeSp, lineHeight, readingFont, textAlignment) {
        ContentKey(
            textHash = text.hashCode(),
            fontSizeSp = fontSizeSp,
            lineHeight = lineHeight,
            readingFont = readingFont,
            textAlignment = textAlignment
        )
    }
    var lastRestoredKey by remember { mutableStateOf<ContentKey?>(null) }
    var lastWrapEnabled by remember { mutableStateOf(isWordWrapEnabled) }
    var pendingAnchorOffset by remember { mutableStateOf<Int?>(null) }
    var lastTextHash by remember { mutableStateOf(0) }
    var lastStyleKey by remember {
        mutableStateOf(
            ContentKey(
                textHash = 0,
                fontSizeSp = -1f,
                lineHeight = -1f,
                readingFont = ReadingFontPreference.Merriweather,
                textAlignment = TextAlignmentPreference.Left
            )
        )
    }
    var lastIsDarkSurface by remember { mutableStateOf(isDarkSurface) }
    var lastWrapEnabledApplied by remember { mutableStateOf(isWordWrapEnabled) }
    var lastSelectionHighlightColor by remember { mutableStateOf(selectionHighlightColor) }
    val currentHeadings by rememberUpdatedState(headings)
    val onActiveHeadingChangedState by rememberUpdatedState(onActiveHeadingChanged)

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        factory = { context ->
            val textView = TextView(context).apply {
                textSize = fontSizeSp
                setLineSpacing(0f, lineHeight)
                setPadding(20, 20, 20, 20)
                setHorizontallyScrolling(!isWordWrapEnabled)
                isHorizontalScrollBarEnabled = !isWordWrapEnabled
                setTextIsSelectable(true)
                setTextColor(
                    if (isDarkSurface) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                )
                highlightColor = selectionHighlightColor
            }
            val scrollView = ScrollView(context).apply {
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
                setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    onScrollChanged(scrollY)
                    val layout = textView.layout ?: return@setOnScrollChangeListener
                    val list = currentHeadings
                    if (list.isEmpty()) return@setOnScrollChangeListener
                    if (layout.text.isEmpty()) return@setOnScrollChangeListener
                    val y = scrollY + textView.paddingTop
                    onActiveHeadingChangedState(findActiveHeadingIndex(layout, list, y))
                }
            }
            scrollView
        },
        update = { scrollView ->
            val child = scrollView.getChildAt(0)
            val textView = if (child is HorizontalScrollView) {
                child.getChildAt(0) as TextView
            } else {
                child as TextView
            }

            if (lastWrapEnabled != isWordWrapEnabled) {
                val layout = textView.layout
                if (layout != null) {
                    val line = layout.getLineForVertical(scrollView.scrollY)
                    pendingAnchorOffset = layout.getLineStart(line)
                }
                (textView.parent as? ViewGroup)?.removeView(textView)
                scrollView.removeAllViews()
                if (isWordWrapEnabled) {
                    scrollView.addView(
                        textView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                } else {
                    val horizontalScrollView = HorizontalScrollView(scrollView.context).apply {
                        isHorizontalScrollBarEnabled = true
                        addView(
                            textView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    }
                    scrollView.addView(
                        horizontalScrollView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                lastWrapEnabled = isWordWrapEnabled
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
                textView.textSize = fontSizeSp
                textView.setLineSpacing(0f, lineHeight)
                textView.typeface = when (readingFont) {
                    ReadingFontPreference.Merriweather -> Typeface.SERIF
                    ReadingFontPreference.SystemSerif -> Typeface.SERIF
                }
                if (textAlignment == TextAlignmentPreference.Justified && Build.VERSION.SDK_INT >= 26) {
                    textView.justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
                } else {
                    if (Build.VERSION.SDK_INT >= 26) {
                        textView.justificationMode = Layout.JUSTIFICATION_MODE_NONE
                    }
                    textView.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
                }
                lastStyleKey = contentKey
            }

            if (lastIsDarkSurface != isDarkSurface) {
                textView.setTextColor(
                    if (isDarkSurface) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                )
                lastIsDarkSurface = isDarkSurface
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

            if (pendingAnchorOffset != null) {
                val targetOffset = pendingAnchorOffset
                pendingAnchorOffset = null
                scrollView.post {
                    val layout = textView.layout ?: return@post
                    val line = layout.getLineForOffset(targetOffset ?: return@post)
                    val y = layout.getLineTop(line)
                    lastRestoredKey = contentKey
                    scrollView.scrollTo(0, y)
                }
            } else if (scrollToOffset == null && savedScrollY > 0 && lastRestoredKey != contentKey) {
                lastRestoredKey = contentKey
                scrollView.post { scrollView.scrollTo(0, savedScrollY) }
            }

            if (scrollToOffset != null) {
                scrollView.post {
                    val layout = textView.layout ?: return@post
                    val line = layout.getLineForOffset(scrollToOffset)
                    val y = layout.getLineTop(line)
                    lastRestoredKey = contentKey
                    scrollView.scrollTo(0, y)
                    onScrollConsumed()
                }
            }

            val layout = textView.layout
            if (layout != null && currentHeadings.isNotEmpty() && layout.text.isNotEmpty()) {
                val y = scrollView.scrollY + textView.paddingTop
                onActiveHeadingChangedState(findActiveHeadingIndex(layout, currentHeadings, y))
            }
        }
    )
}

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



