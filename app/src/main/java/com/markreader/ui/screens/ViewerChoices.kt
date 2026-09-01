package com.markreader.ui.screens

import androidx.annotation.StringRes
import com.markreader.R
import com.markreader.format.ReaderContent

/**
 * The four decisions the viewer makes about *what* to show, kept out of the composable so they
 * can be tested.
 *
 * They are here rather than inline because one of their inputs — a WebView that cannot run the
 * shared renderer — is unreachable without a device old enough to have one, so nobody will
 * exercise these branches by hand again. Left in `ViewerScreen` they would be verified once and
 * then rot quietly; as functions they are checked on every build.
 */

/**
 * What the reader draws.
 *
 * @param rendererUnavailable the WebView never started the renderer, so a `Markdown` document has
 *   nothing to draw it *with*. Raw mode needs no WebView, which is why the source is the fallback
 *   rather than a second native renderer kept alive for the purpose. It shares raw mode's branch
 *   rather than having one of its own: the source is on screen either way, so a search over the
 *   source has to be able to highlight it either way.
 */
fun readerContentFor(
    isRawMode: Boolean,
    rawText: String,
    rawHighlighted: ReaderContent?,
    rendered: ReaderContent?,
    rendererUnavailable: Boolean,
): ReaderContent = when {
    isRawMode || rendererUnavailable -> rawHighlighted ?: ReaderContent.Spans(rawText)
    else -> rendered ?: ReaderContent.Spans(rawText)
}

/**
 * Whether what is on screen is the page's own rendering.
 *
 * The one question search and the table of contents both ask, for the same reason: both index a
 * rendering, and only one of the two reader surfaces keeps its rendering out here. Where the page
 * is drawing, a query goes to the page's find and an outline is available; where a `TextView` is,
 * the query is a span pass over text this side holds and there is no outline to offer.
 *
 * True exactly when [readerContentFor] answers [ReaderContent.Markdown], which the tests assert
 * rather than assume — the two are read for different reasons and must not drift.
 */
fun isDrawnByPage(
    isRawMode: Boolean,
    rendered: ReaderContent?,
    rendererUnavailable: Boolean,
): Boolean = !isRawMode && !rendererUnavailable && rendered is ReaderContent.Markdown

/**
 * What the chrome calls the current view.
 *
 * Naming a view mode is only truthful where there is more than one; where there is not, the chip
 * names the format instead. A renderer that never started is the same problem wearing a different
 * hat — the source is on screen, so calling it "Rendered mode" would be a lie.
 */
@StringRes
fun viewModeLabelFor(
    hasRawView: Boolean,
    isRawMode: Boolean,
    @StringRes formatNameRes: Int,
    rendererUnavailable: Boolean,
): Int = when {
    !hasRawView -> formatNameRes
    isRawMode || rendererUnavailable -> R.string.viewer_subtitle_raw_mode
    else -> R.string.viewer_subtitle_rendered_mode
}

/** Whether the raw/rendered toggle does anything. Both modes draw the source with no renderer. */
fun canToggleViewMode(
    hasRawView: Boolean,
    hasRendered: Boolean,
    rendererUnavailable: Boolean,
): Boolean = hasRawView && hasRendered && !rendererUnavailable
