package com.markreader.ui.screens

import android.app.Application
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markreader.R
import com.markreader.data.AppThemeModePreference
import com.markreader.data.ReaderThemePreference
import com.markreader.OPENABLE_MIME_TYPES
import com.markreader.tryTakePersistablePermission
import com.markreader.ui.components.GroupInnerRadius
import com.markreader.ui.components.GroupOuterRadius
import com.markreader.ui.components.SegmentPosition
import com.markreader.ui.components.segmentPositionFor
import com.markreader.ui.components.segmentShape
import com.markreader.ui.export.ExportManager
import kotlin.math.roundToInt

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
    val scrollProgress by viewModel.scrollProgress.collectAsStateWithLifecycle()
    val prefs = uiState.userPreferences
    val isSystemDark = isSystemInDarkTheme()

    var isReadingSurfaceDark by rememberSaveable { mutableStateOf(false) }
    var isChromeVisible by remember { mutableStateOf(true) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isTocVisible by rememberSaveable { mutableStateOf(false) }
    var isExportSheetVisible by rememberSaveable { mutableStateOf(false) }
    var isWordWrapEnabled by rememberSaveable { mutableStateOf(true) }
    var isCodeBlockWrapEnabled by rememberSaveable { mutableStateOf(true) }
    val exportManager = remember { ExportManager(context) }
    val haptics = LocalHapticFeedback.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                context.contentResolver.tryTakePersistablePermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
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
    // Immersive reading: hide the chrome on downward scrolls, bring it back on
    // upward scrolls or at the top. Large deltas are programmatic jumps (TOC,
    // search match) where the user just used the chrome — keep it visible.
    LaunchedEffect(Unit) {
        var lastY = 0
        snapshotFlow { savedScrollY }.collect { y ->
            val delta = y - lastY
            when {
                y <= 0 -> isChromeVisible = true
                delta < -8 -> isChromeVisible = true
                delta in 9..1200 -> isChromeVisible = false
            }
            lastY = y
        }
    }
    LaunchedEffect(uiState.isSearchActive) {
        if (uiState.isSearchActive) {
            isChromeVisible = true
        }
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

    val dynamicLightScheme = remember(context, prefs.useDynamicColors) {
        if (prefs.useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicLightColorScheme(context)
        } else {
            null
        }
    }
    val dynamicDarkScheme = remember(context, prefs.useDynamicColors) {
        if (prefs.useDynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            null
        }
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
            AnimatedVisibility(
                visible = canEdit && !uiState.isSearchActive && isChromeVisible,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onOpenEditor(uriString!!, isMarkdownFile)
                    },
                    containerColor = chromeColors.tonalContainer,
                    contentColor = chromeColors.content
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit file")
                }
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .background(chromeColors.surface)
                    .statusBarsPadding()
            ) {
                AnimatedVisibility(
                    visible = isChromeVisible || uiState.isSearchActive,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    AnimatedContent(
                        targetState = uiState.isSearchActive,
                        transitionSpec = {
                            (fadeIn(tween(220, delayMillis = 60)) +
                                slideInVertically(tween(220)) { -it / 6 }) togetherWith
                                (fadeOut(tween(120)) +
                                    slideOutVertically(tween(220)) { -it / 6 })
                        },
                        label = "chromeBarSwap"
                    ) { searchActive ->
                        if (searchActive) {
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
                                surfaceColor = chromeColors.surface,
                                contentColor = chromeColors.content,
                                tonalContainerColor = chromeColors.tonalContainer,
                                showBackButton = true
                            )
                        } else {
                            TopAppBar(
                                windowInsets = WindowInsets(0.dp),
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = chromeColors.surface,
                                    titleContentColor = chromeColors.content,
                                    navigationIconContentColor = chromeColors.content,
                                    actionIconContentColor = chromeColors.content
                                ),
                                title = {
                                    val canToggleViewMode =
                                        !uiState.isSourceCode && uiState.rendered != null
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = fileName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Surface(
                                                onClick = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                    viewModel.toggleViewMode()
                                                },
                                                enabled = canToggleViewMode,
                                                shape = RoundedCornerShape(50),
                                                color = chromeColors.tonalContainer.copy(alpha = 0.6f),
                                                contentColor = chromeColors.muted
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(
                                                        horizontal = 8.dp,
                                                        vertical = 2.dp
                                                    )
                                                ) {
                                                    if (canToggleViewMode) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.SwapHoriz,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = viewModeLabel,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                            scrollProgress?.let { progress ->
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = chromeColors.tonalContainer.copy(alpha = 0.6f),
                                                    contentColor = chromeColors.muted
                                                ) {
                                                    Text(
                                                        text = "${(progress * 100).roundToInt()}%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(
                                                            horizontal = 8.dp,
                                                            vertical = 2.dp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                navigationIcon = {
                                    if (!uiState.isSourceCode) {
                                        FilledTonalIconButton(
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                isTocVisible = true
                                            },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = chromeColors.tonalContainer,
                                                contentColor = chromeColors.content
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                                contentDescription = "Table of contents"
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    FilledTonalIconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                            isReadingSurfaceDark = !isReadingSurfaceDark
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = chromeColors.tonalContainer,
                                            contentColor = chromeColors.content
                                        )
                                    ) {
                                        Crossfade(
                                            targetState = isReadingSurfaceDark,
                                            label = "surfaceFlipIcon"
                                        ) { isDark ->
                                            Icon(
                                                imageVector = if (isDark) {
                                                    Icons.Rounded.DarkMode
                                                } else {
                                                    Icons.Rounded.LightMode
                                                },
                                                contentDescription = "Toggle reading surface"
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalIconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                            viewModel.onSearchToggled()
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = chromeColors.tonalContainer,
                                            contentColor = chromeColors.content
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = "Search"
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalIconButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                            isMenuExpanded = true
                                        },
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = chromeColors.tonalContainer,
                                            contentColor = chromeColors.content
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
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
                                                    imageVector = Icons.AutoMirrored.Rounded.WrapText,
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
                                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                isWordWrapEnabled = !isWordWrapEnabled
                                            }
                                        )
                                        if (isWordWrapEnabled) {
                                            DropdownMenuItem(
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Code,
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
                                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                    isCodeBlockWrapEnabled = !isCodeBlockWrapEnabled
                                                }
                                            )
                                        }
                                        HorizontalDivider(color = chromeColors.tonalContainer)
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Download,
                                                    contentDescription = null,
                                                    tint = chromeColors.content
                                                )
                                            },
                                            text = {
                                                Text(
                                                    text = "Export & share",
                                                    color = chromeColors.content
                                                )
                                            },
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                isMenuExpanded = false
                                                isExportSheetVisible = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Settings,
                                                    contentDescription = null,
                                                    tint = chromeColors.content
                                                )
                                            },
                                            text = { Text(text = "Settings", color = chromeColors.content) },
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
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
                val animatedReadProgress by animateFloatAsState(
                    targetValue = scrollProgress ?: 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "readingProgress"
                )
                if (scrollProgress != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(chromeColors.tonalContainer.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedReadProgress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(chromeColors.content.copy(alpha = 0.7f))
                        )
                    }
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
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = "Table of contents",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${uiState.headings.size} heading${if (uiState.headings.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = chromeColors.muted
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            isTocVisible = false
                        },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = chromeColors.tonalContainer,
                            contentColor = chromeColors.content
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                if (uiState.headings.isEmpty()) {
                    Text(
                        text = "No headings found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = chromeColors.muted,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                    )
                } else {
                    val tocListState = rememberLazyListState()
                    LaunchedEffect(Unit) {
                        if (uiState.activeHeadingIndex > 0) {
                            tocListState.scrollToItem(uiState.activeHeadingIndex - 1)
                        }
                    }
                    LazyColumn(
                        state = tocListState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(uiState.headings) { index, heading ->
                            TocHeadingRow(
                                heading = heading,
                                position = segmentPositionFor(index, uiState.headings.size),
                                isActive = index == uiState.activeHeadingIndex,
                                colors = chromeColors,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    viewModel.onHeadingSelected(heading.offset)
                                    isTocVisible = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (isExportSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isExportSheetVisible = false },
            containerColor = chromeColors.surface,
            contentColor = chromeColors.content
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Export & share",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = chromeColors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, bottom = 10.dp)
                )
                ExportSheetItem(
                    icon = Icons.Rounded.PictureAsPdf,
                    title = "Export as PDF",
                    subtitle = "Print-ready document",
                    position = SegmentPosition.First,
                    colors = chromeColors,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        isExportSheetVisible = false
                        exportManager.exportPdf(uiState.rawText, activeReaderTheme, fileName)
                    }
                )
                ExportSheetItem(
                    icon = Icons.Rounded.Language,
                    title = "Export as HTML",
                    subtitle = "Styled web page",
                    position = SegmentPosition.Middle,
                    colors = chromeColors,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        isExportSheetVisible = false
                        exportManager.exportHtml(uiState.rawText, activeReaderTheme)
                    }
                )
                ExportSheetItem(
                    icon = Icons.Rounded.Share,
                    title = "Share raw text",
                    subtitle = "Send the markdown source",
                    position = SegmentPosition.Last,
                    colors = chromeColors,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        isExportSheetVisible = false
                        if (uiState.rawText.isNotBlank()) {
                            exportManager.shareRawMarkdown(uiState.rawText)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TocHeadingRow(
    heading: HeadingItem,
    position: SegmentPosition,
    isActive: Boolean,
    colors: ViewerColors,
    onClick: () -> Unit
) {
    val innerRadius by animateDpAsState(
        targetValue = if (isActive) 20.dp else GroupInnerRadius,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tocCornerMorph"
    )
    val topRadius = when (position) {
        SegmentPosition.Single, SegmentPosition.First -> GroupOuterRadius
        else -> innerRadius
    }
    val bottomRadius = when (position) {
        SegmentPosition.Single, SegmentPosition.Last -> GroupOuterRadius
        else -> innerRadius
    }
    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            colors.tonalContainer
        } else {
            colors.tonalContainer.copy(alpha = 0.4f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tocContainerColor"
    )
    val indent = when (heading.level) {
        1 -> 0.dp
        2 -> 12.dp
        else -> 24.dp
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = topRadius,
            topEnd = topRadius,
            bottomStart = bottomRadius,
            bottomEnd = bottomRadius
        ),
        color = containerColor,
        contentColor = colors.content
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp + indent,
                top = 12.dp,
                bottom = 12.dp,
                end = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = colors.surface,
                contentColor = colors.muted,
                shape = CircleShape
            ) {
                Text(
                    text = "H${heading.level}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Text(
                text = heading.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isActive) {
                Surface(
                    color = colors.surface,
                    contentColor = colors.content,
                    shape = CircleShape
                ) {
                    Text(
                        text = "Reading",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportSheetItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: SegmentPosition,
    colors: ViewerColors,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = segmentShape(position),
        color = colors.tonalContainer.copy(alpha = 0.55f),
        contentColor = colors.content
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
        }
    }
}
