package com.markreader.format

import androidx.annotation.StringRes
import com.markreader.file.DocumentType

/**
 * One document format: how to turn its text into something drawable, and what the chrome
 * around it may offer.
 *
 * **Formats do not recognise files.** [com.markreader.file.DocumentTypeResolver] is the single
 * detection authority and [FormatRegistry] is a [DocumentType] to [DocumentFormat] lookup over
 * it. The reason is that `DocumentResourceHandler` in the WebView host has to ask "is this file
 * text", and moving detection into formats would drag Markwon and Prism4j into that layer to
 * answer it.
 *
 * There is also no `type` property. Nothing asks a format what it reads — the registry's `when`
 * is that mapping, and having the answer in two places is how the two drift apart.
 */
interface DocumentFormat {
    /** What the viewer and editor may offer for documents of this format. */
    val capabilities: FormatCapabilities

    /**
     * The format's own name, for chrome that has to say what a document is rather than which
     * view of it is showing. Only read where [FormatCapabilities.hasRawView] is false.
     */
    @get:StringRes
    val nameRes: Int

    /** What the editor shows in an empty document of this format. */
    @get:StringRes
    val editorPlaceholderRes: Int

    /** The MIME type to ask the system picker for when saving a new document of this format. */
    val creationMimeType: String

    /**
     * What to tell the user when [load] returns null.
     *
     * Null for a format that cannot fail, which is one decision rather than two: a format whose
     * rendering *is* its source has nothing to fall back to and so no failure to describe.
     */
    @get:StringRes
    val renderFailureWarningRes: Int?

    /**
     * Render [text] for the given surface, or null if this format's renderer could not — in
     * which case the caller shows the source and says so with [renderFailureWarningRes].
     */
    suspend fun load(text: String, theme: RenderTheme): ReaderContent?

    /**
     * As [load], for the editor's preview pane.
     *
     * **A temporary second door, and closing it is planned.** The viewer's rendered path is a WebView
     * now, but the editor preview is still a Markwon `TextView` — so a format whose [load]
     * answers [ReaderContent.Markdown] has to answer something drawable here instead. Formats
     * that render the same way on both surfaces inherit this default and say nothing.
     *
     * Only called where [FormatCapabilities.hasEditorPreview] is true.
     */
    suspend fun loadEditorPreview(text: String, theme: RenderTheme): ReaderContent? =
        load(text, theme)

    /**
     * Drop any cached renderer. The reader palette changed, so a renderer built for the old one
     * would paint the previous theme's colours.
     */
    fun invalidateRenderers() {}
}
