package com.markreader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ContentContainer(
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

@Composable
fun ViewerSearchBar(
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
    val haptics = LocalHapticFeedback.current
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
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = contentColor
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.72f)
                        )
                    }
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onClear()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
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
                    border = BorderStroke(
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
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onPrevious()
                        },
                        enabled = hasMatches,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = tonalContainerColor,
                            contentColor = contentColor,
                            disabledContainerColor = disabledContainer,
                            disabledContentColor = disabledContent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Previous match"
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onNext()
                        },
                        enabled = hasMatches,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = tonalContainerColor,
                            contentColor = contentColor,
                            disabledContainerColor = disabledContainer,
                            disabledContentColor = disabledContent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Next match"
                        )
                    }
                }
            }

            HorizontalDivider(color = tonalContainerColor.copy(alpha = 0.5f))
        }
    }
}
