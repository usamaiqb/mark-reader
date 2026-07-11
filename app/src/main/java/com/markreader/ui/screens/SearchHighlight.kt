package com.markreader.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.widget.TextView

/**
 * Marker span for search matches. Deliberately paints nothing itself — the
 * highlight boxes are drawn by [SearchHighlightTextView], bounded to the
 * actual text height instead of the full line height, so they don't bleed
 * into the extra space added by the line-spacing multiplier (worst on
 * headings, where a BackgroundColorSpan box extends a full block below the
 * word).
 */
class SearchHighlightSpan(
    val backgroundColor: Int,
    val isActive: Boolean
) : CharacterStyle() {
    override fun updateDrawState(tp: TextPaint) = Unit
}

@SuppressLint("AppCompatCustomView")
class SearchHighlightTextView(context: Context) : TextView(context) {

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightRect = RectF()

    override fun onDraw(canvas: Canvas) {
        drawSearchHighlights(canvas)
        super.onDraw(canvas)
    }

    private fun drawSearchHighlights(canvas: Canvas) {
        val spanned = text as? Spanned ?: return
        val textLayout = layout ?: return
        val spans = spanned.getSpans(0, spanned.length, SearchHighlightSpan::class.java)
        if (spans.isEmpty()) return

        val density = resources.displayMetrics.density
        val cornerRadius = 4 * density
        val horizontalInset = 2 * density
        canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        // Two passes so the active match is never covered by a passive box.
        for (activePass in booleanArrayOf(false, true)) {
            for (span in spans) {
                if (span.isActive != activePass) continue
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)
                if (start < 0 || end <= start || end > spanned.length) continue
                highlightPaint.color = span.backgroundColor
                drawRangeBackground(canvas, textLayout, start, end, cornerRadius, horizontalInset)
            }
        }
        canvas.restore()
    }

    private fun drawRangeBackground(
        canvas: Canvas,
        textLayout: Layout,
        start: Int,
        end: Int,
        cornerRadius: Float,
        horizontalInset: Float
    ) {
        val startLine = textLayout.getLineForOffset(start)
        val endLine = textLayout.getLineForOffset(end)
        val lastLine = textLayout.lineCount - 1
        for (line in startLine..endLine) {
            val left = if (line == startLine) {
                textLayout.getPrimaryHorizontal(start)
            } else {
                textLayout.getLineLeft(line)
            }
            val right = if (line == endLine) {
                textLayout.getPrimaryHorizontal(end)
            } else {
                textLayout.getLineRight(line)
            }
            if (right == left) continue
            val top = textLayout.getLineTop(line).toFloat()
            val fullHeight = textLayout.getLineBottom(line) - top
            // The line-spacing multiplier pads below the glyphs; shave it off
            // so the box hugs the text. The layout's last line carries no
            // extra spacing.
            val textHeight = if (line == lastLine || lineSpacingMultiplier <= 0f) {
                fullHeight
            } else {
                fullHeight / lineSpacingMultiplier
            }
            highlightRect.set(
                minOf(left, right) - horizontalInset,
                top,
                maxOf(left, right) + horizontalInset,
                top + textHeight
            )
            canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, highlightPaint)
        }
    }
}
