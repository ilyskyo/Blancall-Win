package com.ilyskyo.blancall.desktop.ui

import com.ilyskyo.blancall.data.repository.HomeLayoutStore

/**
 * 首页卡片网格落位（与 Android HomeCardCanvas.layoutSlots 同一算法）。
 *
 * 第一轮：钉住且带 lock 的卡先占锁定槽位；锁定的列会收敛到合法范围，
 * 槽位被占时沿同列向下顺延。第二轮：其余卡按顺序自上而下、自左向右首次适配。
 *
 * 列数必须与绘制时一致，否则拖拽/固定后的视觉位置会整体错位。
 *
 * @return cardId → intArrayOf(row, col)
 */
fun layoutHomeSlots(
    cards: List<HomeLayoutStore.Card>,
    columns: Int = HomeLayoutStore.COLUMNS,
): Map<String, IntArray> {
    val cols = columns.coerceAtLeast(1)
    val out = LinkedHashMap<String, IntArray>(cards.size)
    val grid = mutableListOf<BooleanArray>()

    fun ensureRows(n: Int) {
        while (grid.size < n) grid.add(BooleanArray(cols))
    }

    fun fits(row: Int, col: Int, colSpan: Int, rowSpan: Int): Boolean {
        if (col < 0 || col + colSpan > cols) return false
        for (r in row until row + rowSpan) {
            for (c in col until col + colSpan) {
                if (grid[r][c]) return false
            }
        }
        return true
    }

    fun spanOf(card: HomeLayoutStore.Card): Pair<Int, Int> {
        val colSpan = card.colSpan.coerceIn(
            HomeLayoutStore.MIN_COL_SPAN, HomeLayoutStore.MAX_COL_SPAN
        )
        val rowSpan = card.rowSpan.coerceIn(
            HomeLayoutStore.MIN_ROW_SPAN, HomeLayoutStore.MAX_ROW_SPAN
        )
        return colSpan to rowSpan
    }

    // lock 来自 JSON，可能被手工改成极大值；不设上限会越界
    val maxRow = cards.size * HomeLayoutStore.MAX_ROW_SPAN

    for (card in cards) {
        if (!card.pinned || card.lockRow < 0 || card.lockCol < 0) continue
        val (colSpan, rowSpan) = spanOf(card)
        val lockCol = card.lockCol.coerceIn(0, (cols - colSpan).coerceAtLeast(0))
        var row = card.lockRow.coerceIn(0, maxRow)
        while (true) {
            ensureRows(row + rowSpan)
            if (fits(row, lockCol, colSpan, rowSpan)) break
            row++
        }
        for (r in row until row + rowSpan) {
            for (c in lockCol until lockCol + colSpan) grid[r][c] = true
        }
        out[card.id] = intArrayOf(row, lockCol)
    }

    for (card in cards) {
        if (out.containsKey(card.id)) continue
        val (colSpan, rowSpan) = spanOf(card)
        var placedRow = 0
        var placedCol = 0
        var row = 0
        scan@ while (true) {
            ensureRows(row + rowSpan)
            for (col in 0..cols - colSpan) {
                if (fits(row, col, colSpan, rowSpan)) {
                    placedRow = row
                    placedCol = col
                    break@scan
                }
            }
            row++
        }
        for (r in placedRow until placedRow + rowSpan) {
            for (c in placedCol until placedCol + colSpan) grid[r][c] = true
        }
        out[card.id] = intArrayOf(placedRow, placedCol)
    }
    return out
}

/** 画布总行数（含跨行卡占用的最后一行）。 */
fun homeSlotRows(cards: List<HomeLayoutStore.Card>, slots: Map<String, IntArray>): Int =
    cards.maxOfOrNull { card ->
        val slot = slots[card.id] ?: return@maxOfOrNull 0
        val rowSpan = card.rowSpan.coerceIn(
            HomeLayoutStore.MIN_ROW_SPAN, HomeLayoutStore.MAX_ROW_SPAN
        )
        slot[0] + rowSpan
    } ?: 0
