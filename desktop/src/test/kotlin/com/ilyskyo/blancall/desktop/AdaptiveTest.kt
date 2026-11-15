package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.desktop.ui.WidthClass
import com.ilyskyo.blancall.desktop.ui.gridColumnsFor
import com.ilyskyo.blancall.desktop.ui.homeGridColumns
import com.ilyskyo.blancall.desktop.ui.twoPaneFits
import com.ilyskyo.blancall.desktop.ui.widthClassFor
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 断点与网格列数的纯函数：窗口缩放时这些值决定壳层和首页密度。 */
class AdaptiveTest {

    @Test
    fun widthClassMatchesAndroidBreakpoints() {
        assertEquals(WidthClass.Compact, widthClassFor(320.dp))
        assertEquals(WidthClass.Compact, widthClassFor(599.dp))
        assertEquals(WidthClass.Medium, widthClassFor(600.dp))
        assertEquals(WidthClass.Medium, widthClassFor(839.dp))
        assertEquals(WidthClass.Expanded, widthClassFor(840.dp))
        assertEquals(WidthClass.Expanded, widthClassFor(1200.dp))
    }

    @Test
    fun homeGridUsesEvenColumnsBetweenTwoAndSix() {
        assertEquals(2, homeGridColumns(0f))
        assertEquals(2, homeGridColumns(500f))
        // 900dp 起折算到 4 列：round(900/294/2)*2
        assertEquals(4, homeGridColumns(900f))
        // 1100dp 与 Android 平板同一算法：round(1100/294/2)*2 = 4
        assertEquals(4, homeGridColumns(1100f))
        assertEquals(6, homeGridColumns(1500f))
        assertEquals(6, homeGridColumns(3000f))
    }

    @Test
    fun gridColumnsGrowWithWidth() {
        assertEquals(1, gridColumnsFor(0f))
        assertEquals(2, gridColumnsFor(500f))
        assertEquals(6, gridColumnsFor(3000f))
    }

    @Test
    fun twoPaneNeedsBothWidthAndHeight() {
        assertTrue(twoPaneFits(720f, 480f))
        assertFalse(twoPaneFits(719f, 800f))
        // 手机横屏常见：宽度够、高度不够，仍应单列
        assertFalse(twoPaneFits(900f, 400f))
    }
}
