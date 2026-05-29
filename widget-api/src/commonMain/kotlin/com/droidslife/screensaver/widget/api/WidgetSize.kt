package com.droidslife.screensaver.widget.api

import kotlinx.serialization.Serializable

/** Author-declared size constraints, used by Console grid layout. */
@Serializable
data class WidgetSize(
    val minCols: Int = 2,
    val minRows: Int = 1,
    val defaultCols: Int = 4,
    val defaultRows: Int = 2,
    val maxCols: Int = 12,
    val maxRows: Int = 6,
    /**
     * Per-row override for [minCols]. The host uses `max(minCols, get(rows))`
     * when validating a resize. Lets a widget say "at 3 rows you need at least
     * 6 cols, but at 4+ rows 4 cols is fine."
     */
    val minColsAtRowCount: Map<Int, Int> = emptyMap(),
) {
    init {
        require(minCols in 1..12) { "minCols out of range" }
        require(minRows in 1..6) { "minRows out of range" }
        require(maxCols in minCols..12)
        require(maxRows in minRows..6)
        require(defaultCols in minCols..maxCols)
        require(defaultRows in minRows..maxRows)
        minColsAtRowCount.forEach { (rows, cols) ->
            require(rows in minRows..maxRows) { "minColsAtRowCount row $rows out of range" }
            require(cols in minCols..maxCols) { "minColsAtRowCount cols $cols out of range" }
        }
    }

    fun effectiveMinCols(rows: Int): Int = maxOf(minCols, minColsAtRowCount[rows] ?: minCols)
}

/** A placed widget rect on the 12×6 Console grid. */
@Serializable
data class GridRect(
    val col: Int,
    val row: Int,
    val cols: Int,
    val rows: Int,
) {
    init {
        require(col in 0..11) { "col out of range" }
        require(row in 0..5) { "row out of range" }
        require(cols in 1..12) { "cols out of range" }
        require(rows in 1..6) { "rows out of range" }
        require(col + cols <= 12) { "rect overflows grid horizontally" }
        require(row + rows <= 6) { "rect overflows grid vertically" }
    }
}
