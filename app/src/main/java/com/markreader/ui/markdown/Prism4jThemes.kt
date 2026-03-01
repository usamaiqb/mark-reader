package com.markreader.ui.markdown

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import io.noties.markwon.syntax.Prism4jTheme
import io.noties.prism4j.Prism4j

/**
 * One Dark Pro – a rich, colorful dark theme for syntax highlighting.
 * Background is overridden by ThemedPrism4jTheme to match the app's code block background.
 */
class Prism4jThemeOneDark : Prism4jTheme {
    override fun background(): Int = 0xFF282C34.toInt()
    override fun textColor(): Int = 0xFFABB2BF.toInt()

    override fun apply(
        language: String,
        syntax: Prism4j.Syntax,
        output: SpannableStringBuilder,
        start: Int,
        end: Int
    ) {
        colorFor(syntax.type())?.let { color ->
            output.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isItalic(syntax.type())) {
            output.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun colorFor(type: String): Int? = when (type) {
        "keyword", "important", "selector"           -> 0xFFC678DD.toInt() // purple
        "string", "attr-value", "char", "regex"      -> 0xFF98C379.toInt() // green
        "number", "boolean", "unit"                  -> 0xFFD19A66.toInt() // orange
        "comment", "prolog", "doctype", "cdata"      -> 0xFF7F848E.toInt() // muted gray
        "function", "function-name"                  -> 0xFF61AFEF.toInt() // blue
        "class-name", "builtin", "type", "interface" -> 0xFFE5C07B.toInt() // yellow
        "operator", "entity"                         -> 0xFF56B6C2.toInt() // cyan
        "variable", "constant", "symbol"             -> 0xFFE06C75.toInt() // red-pink
        "attr-name"                                  -> 0xFFD19A66.toInt() // orange
        "tag"                                        -> 0xFFE06C75.toInt() // red-pink
        "punctuation"                                -> 0xFFABB2BF.toInt() // default text
        "namespace"                                  -> 0xFFE5C07B.toInt() // yellow
        "annotation", "decorator"                    -> 0xFF61AFEF.toInt() // blue
        "property", "parameter"                      -> 0xFFABB2BF.toInt() // default text
        else                                         -> null
    }

    private fun isItalic(type: String) =
        type == "comment" || type == "prolog" || type == "doctype" || type == "cdata"
}

/**
 * Atom One Light – a clean, colorful light theme for syntax highlighting.
 * Background is overridden by ThemedPrism4jTheme to match the app's code block background.
 */
class Prism4jThemeOneLight : Prism4jTheme {
    override fun background(): Int = 0xFFFAFAFA.toInt()
    override fun textColor(): Int = 0xFF383A42.toInt()

    override fun apply(
        language: String,
        syntax: Prism4j.Syntax,
        output: SpannableStringBuilder,
        start: Int,
        end: Int
    ) {
        colorFor(syntax.type())?.let { color ->
            output.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (isItalic(syntax.type())) {
            output.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun colorFor(type: String): Int? = when (type) {
        "keyword", "important", "selector"           -> 0xFFA626A4.toInt() // purple
        "string", "attr-value", "char", "regex"      -> 0xFF50A14F.toInt() // green
        "number", "boolean", "unit"                  -> 0xFF986801.toInt() // amber-brown
        "comment", "prolog", "doctype", "cdata"      -> 0xFFA0A1A7.toInt() // muted gray
        "function", "function-name"                  -> 0xFF4078F2.toInt() // blue
        "class-name", "builtin", "type", "interface" -> 0xFFC18401.toInt() // gold
        "operator", "entity"                         -> 0xFF0184BC.toInt() // teal
        "variable", "constant", "symbol"             -> 0xFFE45649.toInt() // red
        "attr-name"                                  -> 0xFF986801.toInt() // amber-brown
        "tag"                                        -> 0xFFE45649.toInt() // red
        "punctuation"                                -> 0xFF383A42.toInt() // default text
        "namespace"                                  -> 0xFFC18401.toInt() // gold
        "annotation", "decorator"                    -> 0xFF4078F2.toInt() // blue
        "property", "parameter"                      -> 0xFF383A42.toInt() // default text
        else                                         -> null
    }

    private fun isItalic(type: String) =
        type == "comment" || type == "prolog" || type == "doctype" || type == "cdata"
}
