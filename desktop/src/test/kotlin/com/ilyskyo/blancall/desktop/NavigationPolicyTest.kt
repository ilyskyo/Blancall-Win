package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.ui.RootTab
import com.ilyskyo.blancall.desktop.ui.rootTabOf
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationPolicyTest {

    @Test
    fun `非沉浸子页保留根 tab 归属`() {
        assertEquals(RootTab.HOME, rootTabOf(Screen.Home))
        assertEquals(RootTab.HOME, rootTabOf(Screen.Settings))
        assertEquals(RootTab.HOME, rootTabOf(Screen.SentenceCards()))
        assertEquals(RootTab.ARTICLES, rootTabOf(Screen.Articles))
        assertEquals(RootTab.ARTICLES, rootTabOf(Screen.Search))
        assertEquals(RootTab.ARTICLES, rootTabOf(Screen.Import))
        assertEquals(RootTab.ARTICLES, rootTabOf(Screen.Tags))
        assertEquals(RootTab.ARTICLES, rootTabOf(Screen.TagHome(7)))
        // 文章详情页不再是沉浸页：它保留侧栏，用户可以随时回首页/切 tab。
        assertEquals(RootTab.ARTICLES, rootTabOf(Screen.Reader(1)))
        assertEquals(RootTab.STATS, rootTabOf(Screen.Statistics()))
        assertEquals(RootTab.STATS, rootTabOf(Screen.Statistics(articleId = 7)))
        assertEquals(RootTab.STATS, rootTabOf(Screen.StudyFlow))
        assertEquals(RootTab.LIBRARY, rootTabOf(Screen.ContentPacks))
    }

    @Test
    fun `阅读与练习保持沉浸不显示导航壳`() {
        assertNull(rootTabOf(Screen.ReadingMode(1)))
        assertNull(rootTabOf(Screen.Practice(1)))
        assertNull(rootTabOf(Screen.CrossPractice(listOf(1, 2))))
        assertNull(rootTabOf(Screen.Drill(1)))
    }

    @Test
    fun `根 tab 平级切换清空子页栈`() {
        val dir = Files.createTempDirectory("blancall-nav").toFile()
        val state = AppState(dir, dir.resolve("settings"))
        state.navigate(Screen.Articles)
        state.navigate(Screen.Settings)
        assertTrue(state.canGoBack())
        state.goRoot(Screen.Home)
        assertEquals(Screen.Home, state.screen)
        assertFalse(state.canGoBack())
    }

    @Test
    fun `Esc 先给最上层面板消费`() {
        val dir = Files.createTempDirectory("blancall-esc").toFile()
        val state = AppState(dir, dir.resolve("settings"))
        state.navigate(Screen.Articles)
        var closed = 0
        val remove = state.pushEscapeHandler {
            closed++
            true
        }
        assertTrue(state.handleEscape())
        assertEquals(1, closed)
        assertEquals(Screen.Articles, state.screen, "面板消费 Esc 时不能回退导航")
        remove()
        assertTrue(state.handleEscape())
        assertEquals(Screen.Home, state.screen)
        assertFalse(state.handleEscape())
    }
}
