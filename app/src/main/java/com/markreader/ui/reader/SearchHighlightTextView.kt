package com.markreader.ui.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.widget.TextView

/** Corner rounding on a match's box, in dp — a pill without looking like a button. */
private const val HIGHLIGHT_CORNER_RADIUS_DP = 4

/** How far a match's box is pulled in on each side, in dp, so adjacent matches stay distinct. */
private const val HIGHLIGHT_HORIZONTAL_INSET_DP = 2

/**
 * A [TextView] that draws search matches itself, from [searchHighlights], instead of reading
 * them out of spans on its text.
 *
 * The boxes are bounded to the actual text height instead of the full line height, so they don't
 * bleed into the extra space added by the line-spacing multiplier, which is what a
 * `BackgroundColorSpan` does. And because they are not spans, a change of match or of palette is
 * a redraw: the text is not set again and nothing is laid out.
 */
@SuppressLint("AppCompatCustomView")
class SearchHighlightTextView(context: Context) : TextView(context) {

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightRect = RectF()
    private val clipBounds = Rect()
    private val activePath = Path()

    /** The matches to draw over the current text, or null when no search is running. */
    var searchHighlights: SearchHighlights? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val highlights = searchHighlights
        val textLayout = layout
        if (highlights == null || textLayout == null || !canvas.getClipBounds(clipBounds)) {
            super.onDraw(canvas)
            return
        }
        drawPassiveMatches(canvas, textLayout, highlights)
        super.onDraw(canvas)
        drawActiveMatch(canvas, textLayout, highlights)
    }

    /**
     * Boxes behind every match the canvas can show, except the active one.
     *
     * Only matches on lines inside the clip are visited, found by binary search, so the cost
     * follows what is being drawn rather than how many matches the document has.
     */
    private fun drawPassiveMatches(canvas: Canvas, textLayout: Layout, highlights: SearchHighlights) {
        val matches = highlights.matches
        val length = textLayout.text.length
        val firstLine = textLayout.getLineForVertical(clipBounds.top - totalPaddingTop)
        val lastLine = textLayout.getLineForVertical(clipBounds.bottom - totalPaddingTop)
        val visibleEnd = textLayout.getLineEnd(lastLine)

        highlightPaint.color = highlights.colors.passiveBackground
        canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        var index = matches.firstIntersecting(textLayout.getLineStart(firstLine))
        while (index < matches.size && matches.start(index) < visibleEnd) {
            // A match past the end belongs to text that is no longer here; the next search
            // result replaces it, and until then it is not drawn.
            if (index != highlights.activeIndex && matches.end(index) <= length) {
                forEachLineBox(textLayout, matches.start(index), matches.end(index)) { box ->
                    canvas.drawRoundRect(box, cornerRadius, cornerRadius, highlightPaint)
                }
            }
            index++
        }
        canvas.restore()
    }

    /**
     * The active match: a stronger box drawn over the text, and the text drawn again inside it
     * in a colour that reads against that box.
     *
     * Drawn after the text rather than behind it because the box is opaque enough to need its
     * own text colour, and a colour span would mean setting the text again on every step. The
     * clip keeps the second draw to the lines the match is on.
     */
    private fun drawActiveMatch(canvas: Canvas, textLayout: Layout, highlights: SearchHighlights) {
        val matches = highlights.matches
        val index = highlights.activeIndex
        if (index !in 0 until matches.size || matches.end(index) > textLayout.text.length) return

        activePath.rewind()
        forEachLineBox(textLayout, matches.start(index), matches.end(index)) { box ->
            activePath.addRoundRect(box, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        if (activePath.isEmpty) return

        canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        canvas.clipPath(activePath)
        canvas.drawColor(highlights.colors.activeBackground)
        val textPaint = textLayout.paint
        val textColor = textPaint.color
        textPaint.color = highlights.colors.activeText
        textLayout.draw(canvas)
        textPaint.color = textColor
        canvas.restore()
    }

    private val cornerRadius: Float
        get() = HIGHLIGHT_CORNER_RADIUS_DP * resources.displayMetrics.density

    /** Calls [action] with the box behind each line of the range, in the layout's coordinates. */
    private inline fun forEachLineBox(
        textLayout: Layout,
        start: Int,
        end: Int,
        action: (RectF) -> Unit
    ) {
        val horizontalInset = HIGHLIGHT_HORIZONTAL_INSET_DP * resources.displayMetrics.density
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
            action(highlightRect)
        }
    }
}
