package com.markreader.ui.markdown

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.style.ReplacementSpan
import io.noties.markwon.core.MarkwonTheme
import kotlin.math.roundToInt

class InlineCodeSpan(
    private val theme: MarkwonTheme,
    private val paddingHorizontalPx: Int,
    private val paddingVerticalPx: Int,
    private val cornerRadiusPx: Float
) : ReplacementSpan() {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val workPaint = TextPaint(paint)
        theme.applyCodeTextStyle(workPaint)
        val width = workPaint.measureText(text, start, end)
        if (fm != null) {
            val metrics = workPaint.fontMetricsInt
            fm.ascent = metrics.ascent - paddingVerticalPx
            fm.descent = metrics.descent + paddingVerticalPx
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        return (width + paddingHorizontalPx * 2).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val workPaint = TextPaint(paint)
        theme.applyCodeTextStyle(workPaint)

        val textWidth = workPaint.measureText(text, start, end)
        val fm = workPaint.fontMetrics
        val rectTop = y + fm.ascent - paddingVerticalPx
        val rectBottom = y + fm.descent + paddingVerticalPx
        val rectLeft = x
        val rectRight = x + textWidth + paddingHorizontalPx * 2

        backgroundPaint.style = Paint.Style.FILL
        backgroundPaint.color = theme.getCodeBackgroundColor(workPaint)
        canvas.drawRoundRect(
            RectF(rectLeft, rectTop, rectRight, rectBottom),
            cornerRadiusPx,
            cornerRadiusPx,
            backgroundPaint
        )

        canvas.drawText(text, start, end, x + paddingHorizontalPx, y.toFloat(), workPaint)
    }
}
