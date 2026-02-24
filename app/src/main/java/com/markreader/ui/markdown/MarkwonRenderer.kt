package com.markreader.ui.markdown

import android.content.Context
import android.text.Spanned
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.node.Code
import kotlin.math.roundToInt

class MarkwonRenderer(
    context: Context,
    isDarkTheme: Boolean
) {
    private object EmptyGrammarLocator : GrammarLocator {
        override fun grammar(prism4j: Prism4j, language: String): Prism4j.Grammar? = null
        override fun languages(): MutableSet<String> = mutableSetOf()
    }

    private val inlineCodePaddingH = (4f * context.resources.displayMetrics.density).roundToInt()
    private val inlineCodePaddingV = (2f * context.resources.displayMetrics.density).roundToInt()
    private val inlineCodeCorner = 4f * context.resources.displayMetrics.density

    private val markwon: Markwon = Markwon.builder(context)
        .usePlugin(ImagesPlugin.create())
        .usePlugin(
            SyntaxHighlightPlugin.create(
                Prism4j(EmptyGrammarLocator),
                if (isDarkTheme) {
                    Prism4jThemeDarkula.create()
                } else {
                    Prism4jThemeDefault.create()
                }
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
        .build()

    suspend fun render(markdown: String): Spanned = withContext(Dispatchers.Default) {
        val node = markwon.parse(markdown)
        markwon.render(node)
    }
}
