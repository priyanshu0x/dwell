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
) {
    init {
        require(minCols in 1..12) { "minCols out of range" }
        require(minRows in 1..6) { "minRows out of range" }
        require(maxCols in minCols..12)
        require(maxRows in minRows..6)
        require(defaultCols in minCols..maxCols)
        require(defaultRows in minRows..maxRows)
    }
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
