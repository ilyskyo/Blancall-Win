package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.data.repository.HomeLayoutStore
import com.ilyskyo.blancall.desktop.ui.homeSlotRows
import com.ilyskyo.blancall.desktop.ui.layoutHomeSlots
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** 首页网格落位：钉住优先、首次适配、跨行卡绕行。 */
class HomeGridTest {

    private fun card(
        id: String,
        colSpan: Int = 2,
        rowSpan: Int = 1,
        pinned: Boolean = false,
        lockRow: Int = -1,
        lockCol: Int = -1,
    ) = HomeLayoutStore.Card(
        id = id,
        type = HomeLayoutStore.CardType.RECENT,
        colSpan = colSpan,
        rowSpan = rowSpan,
        pinned = pinned,
        lockRow = lockRow,
        lockCol = lockCol,
    )

    @Test
    fun defaultCardsFillTwoColumnsOnePerRow() {
        val cards = listOf(card("a"), card("b"), card("c"))
        val slots = layoutHomeSlots(cards, columns = 2)
        assertContentEquals(intArrayOf(0, 0), slots["a"])
        assertContentEquals(intArrayOf(1, 0), slots["b"])
        assertContentEquals(intArrayOf(2, 0), slots["c"])
        assertEquals(3, homeSlotRows(cards, slots))
    }

    @Test
    fun wideCanvasPacksTwoHalfCardsPerRow() {
        val cards = listOf(card("a"), card("b"), card("c"), card("d"), card("e"))
        val slots = layoutHomeSlots(cards, columns = 4)
        assertContentEquals(intArrayOf(0, 0), slots["a"])
        assertContentEquals(intArrayOf(0, 2), slots["b"])
        assertContentEquals(intArrayOf(1, 0), slots["c"])
        assertContentEquals(intArrayOf(1, 2), slots["d"])
        assertContentEquals(intArrayOf(2, 0), slots["e"])
        assertEquals(3, homeSlotRows(cards, slots))
    }

    @Test
    fun singleColumnCardsPackFourPerRow() {
        val cards = listOf(
            card("a", colSpan = 1),
            card("b", colSpan = 1),
            card("c", colSpan = 1),
            card("d", colSpan = 1),
            card("e", colSpan = 1),
        )
        val slots = layoutHomeSlots(cards, columns = 4)
        assertContentEquals(intArrayOf(0, 0), slots["a"])
        assertContentEquals(intArrayOf(0, 1), slots["b"])
        assertContentEquals(intArrayOf(0, 2), slots["c"])
        assertContentEquals(intArrayOf(0, 3), slots["d"])
        assertContentEquals(intArrayOf(1, 0), slots["e"])
    }

    @Test
    fun pinnedCardKeepsLockedSlotAndOthersFlowAround() {
        val cards = listOf(
            card("a"),
            card("pinned", pinned = true, lockRow = 1, lockCol = 2),
            card("b"),
        )
        val slots = layoutHomeSlots(cards, columns = 4)
        assertContentEquals(intArrayOf(0, 0), slots["a"])
        assertContentEquals(intArrayOf(1, 2), slots["pinned"])
        // b 不能占 pinned 的位置：row0 col2 空着，首次适配先填它
        assertContentEquals(intArrayOf(0, 2), slots["b"])
    }

    @Test
    fun outOfRangeLockIsClamped() {
        val cards = listOf(card("unlocked", pinned = true, lockRow = -5, lockCol = 99))
        // lockRow < 0 视为未锁定，回到流式布局的首个空位
        val slots = layoutHomeSlots(cards, columns = 4)
        assertContentEquals(intArrayOf(0, 0), slots["unlocked"])

        val pinned = listOf(card("pinned2", pinned = true, lockRow = 3, lockCol = 99))
        val slots2 = layoutHomeSlots(pinned, columns = 2)
        assertContentEquals(intArrayOf(3, 0), slots2["pinned2"])
    }

    @Test
    fun tallCardLeavesHoleForLaterShortCard() {
        val cards = listOf(
            card("tall", colSpan = 1, rowSpan = 2),
            card("shortA", colSpan = 1),
            card("shortB", colSpan = 1),
            card("shortC", colSpan = 1),
        )
        val slots = layoutHomeSlots(cards, columns = 2)
        assertContentEquals(intArrayOf(0, 0), slots["tall"])
        assertContentEquals(intArrayOf(0, 1), slots["shortA"])
        // row0 已满，row1 col0 被 tall 占，shortB 落 row1 col1
        assertContentEquals(intArrayOf(1, 1), slots["shortB"])
        assertContentEquals(intArrayOf(2, 0), slots["shortC"])
    }
}
