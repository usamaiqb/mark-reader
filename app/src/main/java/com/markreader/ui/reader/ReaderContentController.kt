package com.markreader.ui.reader

import android.view.ViewTreeObserver
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.TextAlignmentPreference

/** Everything that decides whether the rendered views have to be rebuilt. */
internal data class ContentKey(
    val textHash: Int,
    val fontSizeSp: Float,
    val lineHeight: Float,
    val readingFont: ReadingFontPreference,
    val codeFont: CodeFontPreference,
    val isMonospaced: Boolean,
    val textAlignment: TextAlignmentPreference
)

/**
 * What the last update pass over [RenderedTextView]'s view tree left behind:
 * the text and styling it applied, and the scroll restore it claimed.
 *
 * Deliberately plain fields rather than Compose snapshot state. The update pass
 * both reads and writes this within a single invocation, which snapshot state
 * cannot support:
 *
 *  - Writing snapshot state from inside an `AndroidView` update lambda
 *    invalidates the composition, so every write scheduled another pass. A
 *    scroll restore posted by one pass could then have a second, conflicting
 *    restore queued behind it before the first runnable ran, and the
 *    `lastTextHash = 0` written on a restructure pass triggered a full
 *    `removeAllViews()` teardown on the pass that followed — on alternating
 *    frames of the text-color animation, cancelling any gesture in flight.
 *  - A `rememberUpdatedState` wrapper cannot observe a write made earlier in
 *    the same pass, so state written by one half of a pass read back stale in
 *    the other.
 *
 * Held in a `remember`, so it lives as long as the view tree it describes.
 * Read and written only from the update pass and the scroll listener, both on
 * the main thread.
 */
internal class ReaderContentController(
    textColor: Int,
    selectionHighlightColor: Int
) {
    /** The restore already claimed, so the same one is never applied twice. */
    var lastRestoredKey: ContentKey? = null

    /** Content identity of the text currently applied to the views. */
    var lastTextHash: Int = 0
    var lastTextRef: CharSequence? = null

    /** Styling currently applied to the views. */
    var lastStyleKey: ContentKey = UNSTYLED
    var lastTextColor: Int = textColor
    var lastSelectionHighlightColor: Int = selectionHighlightColor

    /**
     * The global-layout listener reporting scroll extent, held so it can be
     * detached when the view tree is released rather than outliving it.
     */
    var extentListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    /**
     * Last extent reported upstream. The listener fires on every window-wide
     * layout, so most calls carry a value that has not changed; comparing here
     * keeps those from reaching the ViewModel.
     */
    var lastReportedExtent: Int = -1

    private companion object {
        /** A key no real content can match, so the first pass always styles. */
        val UNSTYLED = ContentKey(
            textHash = 0,
            fontSizeSp = -1f,
            lineHeight = -1f,
            readingFont = ReadingFontPreference.Merriweather,
            codeFont = CodeFontPreference.JetBrainsMono,
            isMonospaced = false,
            textAlignment = TextAlignmentPreference.Left
        )
    }
}
