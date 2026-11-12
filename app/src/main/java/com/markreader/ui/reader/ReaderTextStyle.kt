package com.markreader.ui.reader

import android.content.Context
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.markreader.R
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import com.markreader.data.TextAlignmentPreference

internal fun resolveTypeface(
    context: Context,
    isMonospaced: Boolean,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference
): Typeface = if (isMonospaced) {
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

internal fun applyStyleToTextView(
    tv: TextView,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    codeFont: CodeFontPreference,
    isMonospaced: Boolean,
    textAlignment: TextAlignmentPreference,
    textColor: Int,
    selectionHighlightColor: Int
) {
    tv.textSize = fontSizeSp
    tv.setLineSpacing(0f, lineHeight)
    tv.setTextColor(textColor)
    tv.highlightColor = selectionHighlightColor
    tv.typeface = resolveTypeface(tv.context, isMonospaced, readingFont, codeFont)
    // See createStyledTextView: content-derived direction, set outside the
    // justification branch because it applies either way.
    tv.textDirection = TextView.TEXT_DIRECTION_FIRST_STRONG
    if (textAlignment == TextAlignmentPreference.Justified &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    ) {
        tv.justificationMode = LineBreaker.JUSTIFICATION_MODE_INTER_WORD
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tv.justificationMode = LineBreaker.JUSTIFICATION_MODE_NONE
        }
        tv.textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
    }
}
