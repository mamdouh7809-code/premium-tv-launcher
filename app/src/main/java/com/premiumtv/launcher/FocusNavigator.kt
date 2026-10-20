package com.premiumtv.launcher

/** Pure layout model. Headers consume a whole row and cannot receive focus. */
class FocusNavigator(focusable: List<Boolean>, private val columns: Int) {
    enum class Direction { LEFT, RIGHT, UP, DOWN }
    data class Cell(val index: Int, val row: Int, val column: Int)
    private val cells: List<Cell>

    init {
        require(columns > 0)
        var row = 0
        var column = 0
        cells = buildList {
            focusable.forEachIndexed { index, enabled ->
                if (!enabled) {
                    if (column > 0) { row++; column = 0 }
                    row++
                } else {
                    add(Cell(index, row, column))
                    column++
                    if (column == columns) { row++; column = 0 }
                }
            }
        }
    }

    /** -1 means the toolbar above the grid. Horizontal boundaries never wrap. */
    fun next(from: Int, direction: Direction, rtl: Boolean): Int {
        val current = cells.firstOrNull { it.index == from } ?: return cells.firstOrNull()?.index ?: -1
        if (direction == Direction.LEFT || direction == Direction.RIGHT) {
            val delta = if ((direction == Direction.RIGHT) != rtl) 1 else -1
            return cells.firstOrNull { it.row == current.row && it.column == current.column + delta }?.index ?: from
        }
        val rows = cells.map { it.row }.distinct()
        val targetRow = if (direction == Direction.UP) rows.lastOrNull { it < current.row }
                        else rows.firstOrNull { it > current.row }
        if (targetRow == null) return if (direction == Direction.UP) -1 else from
        return cells.filter { it.row == targetRow }.minByOrNull { kotlin.math.abs(it.column - current.column) }?.index ?: from
    }
}
