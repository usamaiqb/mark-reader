package com.markreader.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.LineBackgroundSpan
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.markreader.R
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.TextAlignmentPreference

internal fun resolveTypeface(
    context: Context,
    isSourceCode: Boolean,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference
): Typeface = if (isSourceCode) {
    when (codeFont) {
        CodeFontPreference.JetBrainsMono -> try {
            ResourcesCompat.getFont(
                context, R.font.jetbrains_mono_regular
            ) ?: Typeface.MONOSPACE
        } catch (_: Exception) { Typeface.MONOSPACE }
        CodeFontPreference.SystemMono -> Typeface.MONOSPACE
    }
} else {
    when (readingFont) {
        ReadingFontPreference.Merriweather -> try {
            ResourcesCompat.getFont(
                context, R.font.merriweather_regular
            ) ?: Typeface.SERIF
        } catch (_: Exception) { Typeface.SERIF }
        ReadingFontPreference.SystemSerif -> Typeface.SERIF
        ReadingFontPreference.MerriweatherSans -> try {
            ResourcesCompat.getFont(
                context, R.font.merriweather_sans_regular
            ) ?: Typeface.SANS_SERIF
        } catch (_: Exception) { Typeface.SANS_SERIF }
    }
}

internal fun createStyledTextView(
    context: Context,
    paddingPx: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference,
    isSourceCode: Boolean,
    textAlignment: TextAlignmentPreference,
    textColor: Int,
    selectionHighlightColor: Int,
    horizontalScroll: Boolean
): TextView {
    return SearchHighlightTextView(context).apply {
        textSize = fontSizeSp
        setLineSpacing(0f, lineHeight)
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        setHorizontallyScrolling(horizontalScroll)
        isHorizontalScrollBarEnabled = horizontalScroll
        setTextIsSelectable(true)
        setTextColor(textColor)
        highlightColor = selectionHighlightColor
        typeface = resolveTypeface(context, isSourceCode, readingFont, codeFont)
        // Derive paragraph direction from the text, not from the app locale.
        // TEXT_ALIGNMENT_VIEW_START below resolves against the *view's* layout
        // direction, which follows the locale — so an Arabic or Hebrew document opened
        // in an English-locale app was laid out LTR and "start" resolved to left.
        // Set outside the branch below: direction is independent of justification, and
        // justified right-to-left text needs it just as much.
        textDirection = android.view.View.TEXT_DIRECTION_FIRST_STRONG
        if (textAlignment == TextAlignmentPreference.Justified &&
            Build.VERSION.SDK_INT >= 26
        ) {
            justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
        } else {
            if (Build.VERSION.SDK_INT >= 26) {
                justificationMode = LineBreaker.JUSTIFICATION_MODE_NONE
            }
            this.textAlignment = android.view.View.TEXT_ALIGNMENT_VIEW_START
        }
    }
}

internal fun applyStyleToTextView(
    tv: TextView,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference,
    isSourceCode: Boolean,
    textAlignment: TextAlignmentPreference,
    textColor: Int,
    selectionHighlightColor: Int
) {
    tv.textSize = fontSizeSp
    tv.setLineSpacing(0f, lineHeight)
    tv.setTextColor(textColor)
    tv.highlightColor = selectionHighlightColor
    tv.typeface = resolveTypeface(tv.context, isSourceCode, readingFont, codeFont)
    // See createStyledTextView: content-derived direction, set outside the
    // justification branch because it applies either way.
    tv.textDirection = TextView.TEXT_DIRECTION_FIRST_STRONG
    if (textAlignment == TextAlignmentPreference.Justified &&
        Build.VERSION.SDK_INT >= 26
    ) {
        tv.justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
    } else {
        if (Build.VERSION.SDK_INT >= 26) {
            tv.justificationMode = LineBreaker.JUSTIFICATION_MODE_NONE
        }
        tv.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
    }
}

/**
 * Drop the spans that paint a code block's own background and indent, so a code
 * segment can be tinted by its wrapper view instead of by the text itself.
 */
internal fun stripBackgroundSpans(text: Spanned): Spanned {
    val mutable = SpannableStringBuilder(text)
    val lineSpans = mutable.getSpans(0, mutable.length, LineBackgroundSpan::class.java)
    for (span in lineSpans) {
        mutable.removeSpan(span)
    }
    val marginSpans = mutable.getSpans(0, mutable.length, LeadingMarginSpan::class.java)
    for (span in marginSpans) {
        mutable.removeSpan(span)
    }
    val anySpans = mutable.getSpans(0, mutable.length, Any::class.java)
    for (span in anySpans) {
        val name = span.javaClass.name
        if (name.contains("CodeBlock", ignoreCase = true)) {
            mutable.removeSpan(span)
        }
    }
    return mutable
}
