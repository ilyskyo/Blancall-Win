package com.ilyskyo.blancall.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.ui.theme.BlancallTheme

private data class RailItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val screen: Screen)

private val railItems = listOf(
    RailItem("首页", Icons.Default.Home, Screen.Home),
    RailItem("文章", Icons.Default.MenuBook, Screen.Articles),
    RailItem("数据", Icons.Default.BarChart, Screen.Statistics()),
    RailItem("设置", Icons.Default.Settings, Screen.Settings),
)

@Composable
fun BlancallApp(state: AppState) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * state.prefs.uiScale),
    ) {
        BlancallTheme(state.prefs.themeMode, isSystemInDarkTheme(), state.prefs.backgroundStyle) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(state.toast) {
                state.toast?.let { snackbar.showSnackbar(it) }
            }
            Box(Modifier.fillMaxSize()) {
                when (val screen = state.screen) {
                    Screen.Home -> AppFrame(state, Screen.Home) { HomeScreen(state) }
                    Screen.Articles -> AppFrame(state, Screen.Articles) { ArticlesScreen(state) }
                    Screen.Import -> AppFrame(state, Screen.Articles) { ImportScreen(state) }
                    is Screen.Reader -> ReaderScreen(state, screen.articleId)
                    is Screen.Practice -> PracticeScreen(state, screen.articleId)
                    is Screen.CrossPractice -> CrossPracticeScreen(state, screen.articleIds)
                    is Screen.Drill -> DrillScreen(state, screen.articleId)
                    is Screen.Statistics -> AppFrame(
                        state,
                        Screen.Statistics(screen.articleId),
                    ) { StatisticsScreen(state, screen.articleId) }
                    Screen.StudyFlow -> AppFrame(state, Screen.Home) { StudyFlowScreen(state) }
                    Screen.Settings -> AppFrame(state, Screen.Settings) { SettingsScreen(state) }
                    Screen.Tags -> AppFrame(state, Screen.Settings) { TagsScreen(state) }
                    is Screen.TagHome -> AppFrame(state, Screen.Articles) {
                        TagHomeScreen(state, screen.tagId)
                    }
                    is Screen.SentenceCards -> AppFrame(state, Screen.Home) {
                        SentenceCardsScreen(state, screen.tagId, screen.articleId)
                    }
                    Screen.ContentPacks -> AppFrame(state, Screen.Settings) { ContentPacksScreen(state) }
                    Screen.Search -> AppFrame(state, Screen.Articles) { ArticlesScreen(state, focusSearch = true) }
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier.padding(24.dp),
                ) { data -> Snackbar(data) }
            }
            if (state.showWelcome) {
                AlertDialog(
                    onDismissRequest = { state.dismissWelcome() },
                    title = { Text("欢迎使用 Blancall") },
                    text = {
                        androidx.compose.foundation.layout.Column(
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        ) {
                            Text("把文章挖空，用 FSRS-6 间隔重复记住它。")
                            Text("所有数据保存在本机 %LOCALAPPDATA%\\Blancall，无账号、无网络请求、无遥测。")
                            Text("关闭窗口可选择最小化到托盘，学习提醒会继续工作。")
                            Text("手写模型如随包分发，基于 CASIA-HWDB，仅限学术研究，禁止商业用途。")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { state.dismissWelcome() }) { Text("开始使用") }
                    },
                )
            }
        }
        }
    }
}

@Composable
private fun AppFrame(
    state: AppState,
    selected: Screen,
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            railItems.forEach { item ->
                NavigationRailItem(
                    selected = selected::class == item.screen::class,
                    onClick = {
                        when (item.screen) {
                            Screen.Home -> state.navigate(Screen.Home)
                            Screen.Articles -> state.navigate(Screen.Articles)
                            is Screen.Statistics -> state.navigate(Screen.Statistics())
                            Screen.Settings -> state.navigate(Screen.Settings)
                            else -> Unit
                        }
                    },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                )
            }
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}
