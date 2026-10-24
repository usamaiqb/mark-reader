package com.markreader.format.markdown

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.markreader.R
import com.markreader.format.DocumentFormat
import com.markreader.format.FormatCapabilities
import com.markreader.format.ReaderContent
import com.markreader.format.RenderTheme
import kotlinx.coroutines.CancellationException

/** Markwon's own default: the text colour at ~10% alpha, so inline and block code agree. */
private const val CODE_BACKGROUND_ALPHA = 25

private fun codeBackground(isDark: Boolean): Int =
    ColorUtils.setAlphaComponent(if (isDark) Color.WHITE else Color.BLACK, CODE_BACKGROUND_ALPHA)

/**
 * Markdown, rendered by Markwon.
 *
 * Takes a renderer factory rather than building one, for two reasons: the renderer is expensive
 * enough to be worth caching per surface theme, and a factory is the only part of this class
 * that needs a [Context] — so everything else about the format can be asserted in a JVM test.
 */
class MarkdownFormat(private val newRenderer: (isDark: Boolean) -> MarkwonRenderer) : DocumentFormat {

    constructor(context: Context) : this({ isDark ->
        MarkwonRenderer(context, isDark, codeBackground(isDark))
    })

    /** One renderer per surface theme. Building Markwon costs milliseconds; toggling is instant. */
    private val renderers = mutableMapOf<Boolean, MarkwonRenderer>()

    override val capabilities = FormatCapabilities(
        hasHeadings = true,
        hasRawView = true,
        isMonospaced = false,
        hasEditorPreview = true,
        hasFormattingToolbar = true
    )

    override val nameRes = R.string.viewer_subtitle_markdown

    override val editorPlaceholderRes = R.string.editor_placeholder_markdown

    override val creationMimeType = "text/markdown"

    override val renderFailureWarningRes = R.string.viewer_warning_markdown

    /**
     * The source, unchanged, for the shared renderer to draw.
     *
     * Nothing is parsed here and nothing can fail — which is why [renderFailureWarningRes] is
     * now only reachable through [loadEditorPreview]. Markdown's renderer moved into the page.
     */
    override suspend fun load(text: String, theme: RenderTheme): ReaderContent =
        ReaderContent.Markdown(text)

    /** Markwon, until the editor preview moves onto the same renderer as the viewer. */
    override suspend fun loadEditorPreview(text: String, theme: RenderTheme): ReaderContent? = try {
        ReaderContent.Spans(renderer(theme.isDark).render(text))
    } catch (ex: CancellationException) {
        throw ex
    } catch (_: Exception) {
        // Markwon and commonmark between them can fail in a great many ways on a malformed
        // document, and they all have the same remedy: show the source instead.
        null
    }

    override fun invalidateRenderers() = renderers.clear()

    private fun renderer(isDark: Boolean): MarkwonRenderer =
        renderers.getOrPut(isDark) { newRenderer(isDark) }
}
