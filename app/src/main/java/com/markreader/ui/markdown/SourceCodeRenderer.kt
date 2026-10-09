package com.markreader.ui.markdown

import android.text.SpannableStringBuilder
import android.text.Spanned
import io.noties.markwon.syntax.Prism4jTheme
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Highlights source code using Prism4j directly, without Markwon's code-block container.
 * The result is a plain [Spanned] with only color/style spans — no background, padding,
 * or block markers — so callers control all layout and font decisions.
 */
class SourceCodeRenderer(isDark: Boolean) {
    private val prism4j = Prism4j(PrismGrammarLocatorImpl())
    private val theme: Prism4jTheme = if (isDark) Prism4jThemeOneDark() else Prism4jThemeOneLight()

    suspend fun highlight(code: String, language: String): Spanned = withContext(Dispatchers.Default) {
        val grammar = prism4j.grammar(prismGrammarFor(language))
            ?: return@withContext SpannableStringBuilder(code)
        val out = SpannableStringBuilder()
        walkNodes(prism4j.tokenize(code, grammar), out)
        out
    }

    private fun walkNodes(nodes: List<Prism4j.Node>, out: SpannableStringBuilder) {
        for (node in nodes) {
            when (node) {
                is Prism4j.Text -> out.append(node.literal())
                is Prism4j.Syntax -> {
                    val start = out.length
                    walkNodes(node.children(), out)
                    applyTheme(node, out, start, out.length)
                }
                else -> {}
            }
        }
    }

    private fun applyTheme(syntax: Prism4j.Syntax, out: SpannableStringBuilder, start: Int, end: Int) {
        theme.apply("", syntax, out, start, end)
    }
}

/**
 * Language name to Prism4j grammar id.
 *
 * Several are approximations: Prism4j bundles no Rust, Ruby, TOML or TypeScript grammar, so
 * those borrow one of similar lexical shape. The approximation lives here rather than in
 * `DocumentType` deliberately — it is a highlighting compromise, not a claim about what the
 * file is, and a `.rs` file should not have to call itself C to get coloured. Anything not
 * listed is passed through unchanged, and an id Prism4j does not know degrades to plain text.
 */
private val PRISM_GRAMMARS = mapOf(
    "rust" to "c",
    "ruby" to "python",
    "toml" to "yaml",
    "typescript" to "javascript",
    "html" to "markup",
    "xml" to "markup"
)

internal fun prismGrammarFor(language: String): String = PRISM_GRAMMARS[language] ?: language
