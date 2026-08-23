package com.markreader.ui.reader

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import com.markreader.data.CodeFontPreference
import com.markreader.data.ReadingFontPreference
import io.noties.markwon.ext.tables.TableRowSpan

/**
 * Build a real [TableLayout] out of Markwon's table row spans, so the table can
 * be scrolled horizontally on its own instead of forcing the whole document to
 * scroll. Returns null when the segment carries no table rows, in which case the
 * caller falls back to plain text.
 */
internal fun buildTableLayout(
    text: Spanned,
    context: Context,
    textColor: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    selectionHighlightColor: Int,
    density: Float
): TableLayout? {
    val tableRowSpans = text.getSpans(0, text.length, TableRowSpan::class.java)
    if (tableRowSpans.isEmpty()) return null

    tableRowSpans.sortBy { text.getSpanStart(it) }

    val cellsField = try {
        TableRowSpan::class.java.getDeclaredField("cells").apply { isAccessible = true }
    } catch (_: Exception) { return null }

    val cellPaddingPx = (8 * density).toInt()
    val borderWidthPx = tableBorderWidthPx(density)
    val typeface = resolveTypeface(context, false, readingFont, CodeFontPreference.JetBrainsMono)

    val tableLayout = TableLayout(context)

    for ((rowIndex, rowSpan) in tableRowSpans.withIndex()) {
        @Suppress("UNCHECKED_CAST")
        val cells = try {
            cellsField.get(rowSpan) as? List<TableRowSpan.Cell> ?: continue
        } catch (_: Exception) { continue }

        val isHeader = rowIndex == 0
        val isOdd = !isHeader && rowIndex % 2 == 1

        val tableRow = TableRow(context)

        for ((colIndex, cell) in cells.withIndex()) {
            val cellBg = tableCellBackground(textColor, isOdd, borderWidthPx)
            val cellTv = SearchHighlightTextView(context).apply {
                this.text = cell.text()
                textSize = fontSizeSp
                setLineSpacing(0f, lineHeight)
                setTextColor(textColor)
                setPadding(cellPaddingPx, cellPaddingPx, cellPaddingPx, cellPaddingPx)
                this.typeface = typeface
                if (isHeader) setTypeface(this.typeface, Typeface.BOLD)
                highlightColor = selectionHighlightColor
                background = cellBg
                gravity = when (cell.alignment()) {
                    TableRowSpan.ALIGN_CENTER -> Gravity.CENTER_HORIZONTAL
                    TableRowSpan.ALIGN_RIGHT -> Gravity.END
                    else -> Gravity.START
                }
            }
            val params = TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.MATCH_PARENT
            )
            // Overlap adjacent borders so they don't double up
            if (colIndex > 0) params.leftMargin = -borderWidthPx
            tableRow.addView(cellTv, params)
        }

        val rowParams = TableLayout.LayoutParams(
            TableLayout.LayoutParams.WRAP_CONTENT,
            TableLayout.LayoutParams.WRAP_CONTENT
        )
        if (rowIndex > 0) rowParams.topMargin = -borderWidthPx
        tableLayout.addView(tableRow, rowParams)
    }

    return tableLayout
}

private fun tableBorderWidthPx(density: Float): Int = maxOf(1, density.toInt())

// Markwon defaults: border = textColor at 75/255 alpha, odd row bg = textColor at 22/255 alpha
private fun tableCellBackground(
    textColor: Int,
    isOdd: Boolean,
    borderWidthPx: Int
): GradientDrawable = GradientDrawable().apply {
    setStroke(borderWidthPx, (textColor and 0x00FFFFFF) or (75 shl 24))
    setColor(if (isOdd) (textColor and 0x00FFFFFF) or (22 shl 24) else Color.TRANSPARENT)
}

/**
 * Reapply colors and text styling to an existing table without rebuilding it.
 * Cell backgrounds bake in [textColor], so a theme change has to touch them —
 * but tearing the table down to do it would also tear down the view tree the
 * user may be mid-gesture on.
 */
internal fun restyleTableLayout(
    table: TableLayout,
    textColor: Int,
    fontSizeSp: Float,
    lineHeight: Float,
    readingFont: ReadingFontPreference,
    selectionHighlightColor: Int,
    density: Float
) {
    val borderWidthPx = tableBorderWidthPx(density)
    val typeface = resolveTypeface(
        table.context, false, readingFont, CodeFontPreference.JetBrainsMono
    )
    for (rowIndex in 0 until table.childCount) {
        val row = table.getChildAt(rowIndex) as? TableRow ?: continue
        val isHeader = rowIndex == 0
        val isOdd = !isHeader && rowIndex % 2 == 1
        for (colIndex in 0 until row.childCount) {
            val cell = row.getChildAt(colIndex) as? TextView ?: continue
            cell.textSize = fontSizeSp
            cell.setLineSpacing(0f, lineHeight)
            cell.setTextColor(textColor)
            cell.highlightColor = selectionHighlightColor
            cell.typeface = if (isHeader) {
                Typeface.create(typeface, Typeface.BOLD)
            } else {
                typeface
            }
            cell.background = tableCellBackground(textColor, isOdd, borderWidthPx)
        }
    }
}
