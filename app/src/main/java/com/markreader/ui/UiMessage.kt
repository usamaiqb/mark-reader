package com.markreader.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A user-facing message chosen by a ViewModel and resolved by the UI.
 *
 * ViewModels outlive configuration changes, so a message resolved there would keep
 * the locale it was built with. Holding the resource id and its arguments instead
 * lets the string be looked up at composition time, in the current locale.
 */
data class UiMessage(
    @param:StringRes val resId: Int,
    val args: List<Any> = emptyList()
)

// The spread copies an array of at most a couple of format arguments, which is what
// the vararg overload of stringResource takes.
@Suppress("SpreadOperator")
@Composable
fun UiMessage.resolve(): String = stringResource(resId, *args.toTypedArray())
