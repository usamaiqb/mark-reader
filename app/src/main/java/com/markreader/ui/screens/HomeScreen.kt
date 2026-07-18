package com.markreader.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.markreader.OPENABLE_MIME_TYPES
import com.markreader.R
import com.markreader.data.RecentFile
import com.markreader.ui.components.SectionHeader
import com.markreader.ui.components.SegmentPosition
import com.markreader.ui.components.segmentPositionFor
import com.markreader.ui.components.segmentShape
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenViewer: (String) -> Unit,
    onOpenEditor: (String, Boolean) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current

    val openFileLauncher = rememberLauncherForActivityResult(
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
            }
            viewModel.onFilePicked(uri)
        }
    )

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (ex: SecurityException) { } catch (ex: IllegalArgumentException) { }
            }
            viewModel.onNewFileCreated(uri)
        }
    )

    LaunchedEffect(Unit) {
        viewModel.launchPickerSignal.collectLatest {
            openFileLauncher.launch(OPENABLE_MIME_TYPES)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchCreateSignal.collectLatest {
            createFileLauncher.launch("untitled.md")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToViewer.collectLatest { uriString ->
            onOpenViewer(uriString)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToEditor.collectLatest { (uriString, isMarkdown) ->
            onOpenEditor(uriString, isMarkdown)
        }
    }

    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            LargeTopAppBar(
                title = { Text(text = "MarkReader") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings"
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
        val recents = recentFiles
        when {
            recents == null -> {
                // Waiting for the first DataStore emission; avoid flashing the empty state.
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues))
            }
            recents.isEmpty() -> {
                EmptyHomeContent(
                    paddingValues = paddingValues,
                    onOpenFile = viewModel::onOpenFileRequested,
                    onNewFile = viewModel::onNewFileRequested
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
                ) {
                    item(key = "quick_actions") {
                        QuickActionsRow(
                            onOpenFile = viewModel::onOpenFileRequested,
                            onNewFile = viewModel::onNewFileRequested
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    item(key = "recent_header") {
                        SectionHeader("Recent")
                    }
                    itemsIndexed(
                        items = recents,
                        key = { _, file -> file.uri }
                    ) { index, file ->
                        RecentFileRow(
                            file = file,
                            position = segmentPositionFor(index, recents.size),
                            onClick = { viewModel.onRecentFileClicked(file.uri) },
                            onRemove = { viewModel.onRemoveRecentFile(file.uri) },
                            modifier = Modifier
                                .animateItem(
                                    fadeInSpec = tween(durationMillis = 180),
                                    fadeOutSpec = tween(durationMillis = 120),
                                    placementSpec = tween(durationMillis = 200)
                                )
                                .padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Quick actions ──────────────────────────────────────────────────────────────

@Composable
private fun QuickActionsRow(
    onOpenFile: () -> Unit,
    onNewFile: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onOpenFile()
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(
                topStart = 28.dp, topEnd = 12.dp,
                bottomStart = 28.dp, bottomEnd = 12.dp
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = "Open file")
        }
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onNewFile()
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(
                topStart = 12.dp, topEnd = 28.dp,
                bottomStart = 12.dp, bottomEnd = 28.dp
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = "New file")
        }
    }
}

// ── Recent file row ────────────────────────────────────────────────────────────

@Composable
private fun RecentFileRow(
    file: RecentFile,
    position: SegmentPosition,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val relativeTime = remember(file.lastOpenedMillis) {
        DateUtils.getRelativeTimeSpanString(
            file.lastOpenedMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    Surface(
        onClick = onClick,
        shape = segmentShape(position),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = fileTypeIcon(file.displayName),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${fileTypeLabel(file.displayName)} · $relativeTime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onRemove()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove from recents",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyHomeContent(
    paddingValues: PaddingValues,
    onOpenFile: () -> Unit,
    onNewFile: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Image(
            painter = painterResource(id = R.drawable.ic_logo),
            contentDescription = "MarkReader app icon",
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(128.dp)
        )
        Text(
            text = "Open a file",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = "Choose a Markdown or source code file to start reading. Recent files will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onOpenFile()
            },
            shape = MaterialTheme.shapes.extraLarge,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = "Open File",
                style = MaterialTheme.typography.titleMedium
            )
        }
        OutlinedButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onNewFile()
            },
            shape = MaterialTheme.shapes.extraLarge,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = "New File",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.weight(1.3f))
    }
}

// ── File type helpers ──────────────────────────────────────────────────────────

private fun fileExtension(displayName: String): String =
    displayName.substringAfterLast('.', "").lowercase()

private fun fileTypeLabel(displayName: String): String = when (val ext = fileExtension(displayName)) {
    "md", "markdown" -> "Markdown"
    "txt", "" -> "Text"
    else -> ext.uppercase()
}

private fun fileTypeIcon(displayName: String): ImageVector = when (fileExtension(displayName)) {
    "md", "markdown" -> Icons.AutoMirrored.Rounded.MenuBook
    "txt", "" -> Icons.Rounded.Description
    else -> Icons.Rounded.Code
}
