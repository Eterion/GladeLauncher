package com.eterion.glade

import kotlin.math.roundToInt

data class Placement(val component: String, val cell: Int)

/** Pure layout rules; coordinates are grid cells, independent of screen pixels. */
object HomeLayout {
    const val COLUMNS = 5
    const val ROWS = 7
    const val SIZE = COLUMNS * ROWS

    fun normalize(items: List<Placement>): List<Placement> {
        val components = mutableSetOf<String>()
        val cells = mutableSetOf<Int>()
        return items.filter { item ->
            item.component.isNotBlank() && item.cell in 0 until SIZE &&
                item.component !in components && item.cell !in cells &&
                components.add(item.component) && cells.add(item.cell)
        }
    }

    fun add(items: List<Placement>, component: String): List<Placement> {
        val valid = normalize(items)
        if (valid.any { it.component == component }) return valid
        // Bottom row is a convenient default, not a reserved dock.
        val order = (SIZE - COLUMNS until SIZE) + (0 until SIZE - COLUMNS)
        val cell = order.firstOrNull { candidate -> valid.none { it.cell == candidate } } ?: return valid
        return normalize(valid + Placement(component, cell))
    }

    fun move(items: List<Placement>, component: String, cell: Int): List<Placement> {
        val valid = normalize(items)
        if (cell !in 0 until SIZE || valid.none { it.component == component }) return valid
        if (valid.any { it.cell == cell && it.component != component }) return valid
        return valid.map { if (it.component == component) it.copy(cell = cell) else it }
    }

    fun removePackage(items: List<Placement>, packageName: String): List<Placement> =
        items.filterNot { it.component.substringBefore('/') == packageName }

    fun remapRows(items: List<Placement>, fromRows: Int): List<Placement> {
        if (fromRows == ROWS || fromRows < 2) return normalize(items)
        return normalize(items.filter { it.cell in 0 until fromRows * COLUMNS }.map {
            val oldRow = it.cell / COLUMNS
            val newRow = (oldRow.toFloat() * (ROWS - 1) / (fromRows - 1)).roundToInt()
            it.copy(cell = newRow * COLUMNS + it.cell % COLUMNS)
        })
    }
}
