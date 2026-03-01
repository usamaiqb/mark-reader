package com.markreader.ui.markdown

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.annotation.ColorInt
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.syntax.Prism4jTheme
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import kotlin.math.roundToInt

/** Empty marker span applied to fenced / indented code blocks so the UI can split them out. */
class CodeBlockMarkerSpan

/**
 * Wraps a [Prism4jTheme] and overrides its background color so that
 * fenced code blocks share the same background as inline code spans.
 */
private class ThemedPrism4jTheme(
    private val delegate: Prism4jTheme,
    @ColorInt private val bg: Int
) : Prism4jTheme {
    override fun background(): Int = bg
    override fun textColor(): Int = delegate.textColor()
    override fun apply(
        language: String,
        syntax: Prism4j.Syntax,
        output: SpannableStringBuilder,
        start: Int,
        end: Int
    ) = delegate.apply(language, syntax, output, start, end)
}

class MarkwonRenderer(
    context: Context,
    isDarkTheme: Boolean,
    @ColorInt codeBlockBackground: Int
) {
    private val inlineCodePaddingH = (4f * context.resources.displayMetrics.density).roundToInt()
    private val inlineCodePaddingV = (2f * context.resources.displayMetrics.density).roundToInt()
    private val inlineCodeCorner = 4f * context.resources.displayMetrics.density

    private val markwon: Markwon = Markwon.builder(context)
        .usePlugin(ImagesPlugin.create())
        .usePlugin(
            SyntaxHighlightPlugin.create(
                Prism4j(PrismGrammarLocatorImpl()),
                ThemedPrism4jTheme(
                    delegate = if (isDarkTheme) Prism4jThemeOneDark()
                               else Prism4jThemeOneLight(),
                    bg = codeBlockBackground
                )
            )
        )
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                builder.setFactory(Code::class.java) { configuration, _ ->
                    InlineCodeSpan(
                        configuration.theme(),
                        inlineCodePaddingH,
                        inlineCodePaddingV,
                        inlineCodeCorner
                    )
                }
            }
        })
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                val fencedFactory = builder.getFactory(FencedCodeBlock::class.java)
                if (fencedFactory != null) {
                    builder.setFactory(FencedCodeBlock::class.java) { configuration, props ->
                        val original = fencedFactory.getSpans(configuration, props)
                        val marker = CodeBlockMarkerSpan()
                        if (original is Array<*>) arrayOf(*original, marker)
                        else arrayOf(original, marker)
                    }
                }
                val indentedFactory = builder.getFactory(IndentedCodeBlock::class.java)
                if (indentedFactory != null) {
                    builder.setFactory(IndentedCodeBlock::class.java) { configuration, props ->
                        val original = indentedFactory.getSpans(configuration, props)
                        val marker = CodeBlockMarkerSpan()
                        if (original is Array<*>) arrayOf(*original, marker)
                        else arrayOf(original, marker)
                    }
                }
            }
        })
        .build()

    suspend fun render(markdown: String): Spanned = withContext(Dispatchers.Default) {
        val node = markwon.parse(markdown)
        markwon.render(node)
    }
}
