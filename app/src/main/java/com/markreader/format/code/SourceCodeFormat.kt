package com.markreader.format.code

import com.markreader.R
import com.markreader.format.DocumentFormat
import com.markreader.format.FormatCapabilities
import com.markreader.format.ReaderContent

/**
 * Source code, drawn as a single fenced block by the shared renderer.
 *
 * One instance per language, because the language is a property of the document and not of the
 * call — a format that took it as a `load()` argument would be asking every caller to remember
 * something the registry already knows.
 *
 * There is no raw view: the highlighted form is the source, coloured. That is also why the
 * viewer names the format in its chrome instead of claiming to show a "rendered" document.
 *
 * **A fence is how a code file reaches a Markdown renderer**, and it is not a trick played for
 * convenience: highlight.js runs on fenced blocks, so wrapping the source in one is what buys
 * real grammars for the languages Prism4j only had approximations of. `rust` was highlighted as
 * `c`, `ruby` as `python` and `toml` as `yaml`; all three are now themselves, and the map that
 * held those substitutions is gone with the Prism renderer that needed it.
 *
 * ### This fencing is a stopgap
 *
 * It is in the wrong repo. The renderer is shared with the desktop app, and when that grows
 * source-code support it will need exactly this transformation — so it would be written a second
 * time, in TypeScript, which is the duplication the shared renderer exists to remove. The fix is
 * for the renderer to take a document *kind*: `showDocument({ path, text, kind, language })`,
 * with the fencing and the presentation both on that side. That work is planned.
 *
 * Two things follow when it lands, and neither is a tidy-up:
 *
 *  - **[ReaderContent.Markdown] stops being the right answer for a code file.** It becomes
 *    something like `Source(text, language)`, and this class stops producing Markdown at all.
 *  - **The double padding goes.** A fenced block sits inside the document's own body padding
 *    *and* its own — 16px plus 16px on a phone, against 16px for the `TextView` this replaced —
 *    inside a bordered, rounded card. For a document that is entirely code that is twice the
 *    inset on the content least able to spare it. It is deliberately **not** patched in Android
 *    CSS: any rule written against the fenced shape is thrown away when the kind arrives.
 */
class SourceCodeFormat(private val language: String) : DocumentFormat {

    override val capabilities = FormatCapabilities(
        hasHeadings = false,
        hasRawView = false,
        isMonospaced = true,
        hasEditorPreview = false,
        hasFormattingToolbar = false
    )

    override val nameRes = R.string.viewer_subtitle_source_code

    override val editorPlaceholderRes = R.string.editor_placeholder_text

    /**
     * `text/plain`, not a per-language MIME type. The picker uses this to name a *new* file, and
     * offering `text/x-kotlin` there would have the system suggest an extension for a language
     * the user has not chosen yet.
     */
    override val creationMimeType = "text/plain"

    /**
     * Null: fencing text cannot fail.
     *
     * The Prism4j path this replaced could — a grammar it mishandled, a pathological file — and
     * fell back to the plain source with a warning. Nothing here parses anything, so there is no
     * failure left to describe. A renderer that breaks on a device breaks for Markdown too, and
     * that is a host problem rather than a format's.
     */
    override val renderFailureWarningRes: Int? = null

    override suspend fun load(text: String): ReaderContent =
        ReaderContent.Markdown(fenced(text, language))
}

/**
 * Wraps [code] in a fenced block tagged [language].
 *
 * The fence has to be longer than the longest run of backticks in the document, or a file that
 * contains one closes the block early and the rest of it renders as prose. CommonMark's rule is
 * that a fence closes on a run of *at least* its own length, so one longer than the longest run
 * present cannot be closed by anything inside.
 */
internal fun fenced(code: String, language: String): String {
    val longestRun = BACKTICK_RUN.findAll(code).maxOfOrNull { it.value.length } ?: 0
    val fence = "`".repeat(maxOf(MIN_FENCE, longestRun + 1))
    return "$fence$language\n$code\n$fence"
}

private val BACKTICK_RUN = Regex("`+")

private const val MIN_FENCE = 3
