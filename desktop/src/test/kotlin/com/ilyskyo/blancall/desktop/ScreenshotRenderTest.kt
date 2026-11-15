package com.ilyskyo.blancall.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.ilyskyo.blancall.data.model.Article
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.desktop.ui.BlancallApp
import com.ilyskyo.blancall.desktop.ui.ReadingModeScreen
import com.ilyskyo.blancall.desktop.ui.SettingsScreen
import com.ilyskyo.blancall.desktop.ui.theme.BlancallTheme
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat

/**
 * 离屏渲染截图（不进发布包）：验证新版 UI 的真实布局。
 * 生成到 `_shots/render_<name>.png`，不依赖鼠标/窗口焦点。
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotRenderTest {

    @Test
    fun renderScreens() {
        val root = File(System.getProperty("java.io.tmpdir"), "blancall-shot-${System.nanoTime()}")
        val data = File(root, "data").apply { mkdirs() }
        val settings = File(root, "settings").apply { mkdirs() }
        val state = AppState(data, settings)
        state.dismissWelcome()
        runBlocking {
            state.articles.awaitLoaded()
            state.articles.insertAll(
                listOf(
                    Article(
                        title = "岳阳楼记",
                        author = "范仲淹",
                        content = "庆历四年春，滕子京谪守巴陵郡。越明年，政通人和，百废具兴，乃重修岳阳楼，增其旧制，刻唐贤今人诗赋于其上，属予作文以记之。\n\n予观夫巴陵胜状，在洞庭一湖。衔远山，吞长江，浩浩汤汤，横无际涯，朝晖夕阴，气象万千，此则岳阳楼之大观也。\n\n嗟夫！予尝求古仁人之心，或异二者之为，何哉？不以物喜，不以己悲，居庙堂之高则忧其民，处江湖之远则忧其君。",
                    ),
                    Article(
                        title = "劝学",
                        author = "荀子",
                        content = "君子曰：学不可以已。青，取之于蓝，而青于蓝；冰，水为之，而寒于水。木直中绳，輮以为轮，其曲中规。\n\n故木受绳则直，金就砺则利，君子博学而日参省乎己，则知明而行无过矣。",
                    ),
                    Article(
                        title = "静夜思",
                        author = "李白",
                        content = "床前明月光，疑是地上霜。\n举头望明月，低头思故乡。",
                    ),
                ),
            )
            withTimeout(5_000) { state.articles.allArticles.first { it.size >= 3 } }
            state.records.awaitLoaded()
        }
        val out = File("_shots").apply { mkdirs() }

        fun shotContent(
            name: String,
            width: Int = 1200,
            height: Int = 800,
            density: Float = 1.0f,
            content: @androidx.compose.runtime.Composable () -> Unit,
        ) {
            val scene = ImageComposeScene(
                width = width,
                height = height,
                density = Density(density),
            ) {
                content()
            }
            try {
                scene.render(0L)
                scene.render(500_000_000L)
                val image = scene.render(1_000_000_000L)
                image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let {
                    File(out, "render_$name.png").writeBytes(it)
                }
            } finally {
                scene.close()
            }
        }

        fun shot(name: String, width: Int = 1200, height: Int = 800, density: Float = 1.0f) {
            shotContent(name, width, height, density) { BlancallApp(state) }
        }

        // 平板横屏基准（与 pad 实机 1219×813dp 同档）与三档缩小窗口
        shot("home")
        // 150% 缩放：1200 物理像素 = 800dp，应保持 rail 布局且不裁切
        shot("home_dpi150", density = 1.5f)
        shot("home_medium", width = 800, height = 800)
        shot("home_narrow", width = 520, height = 800)
        shot("home_min", width = 420, height = 640)
        state.navigate(Screen.Articles)
        shot("articles")
        shot("articles_narrow", width = 520, height = 800)
        state.navigate(Screen.Reader(1))
        shot("detail")
        shot("detail_narrow", width = 520, height = 800)
        state.navigate(Screen.ReadingMode(1))
        shot("reading_mode")
        shot("reading_mode_narrow", width = 520, height = 800)
        // Aa 面板直接以打开态渲染，不依赖鼠标点击；宽窗走右侧 Inspector，窄窗走底部面板。
        shotContent("reading_settings", width = 1200, height = 800) {
            BlancallTheme(
                themeMode = state.prefs.themeMode,
                systemDark = false,
                backgroundStyle = state.prefs.backgroundStyle,
                accentIndex = state.prefs.accentIndex,
                uiFont = state.prefs.uiFont,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReadingModeScreen(state, 1, initialSettingsOpen = true)
                }
            }
        }
        shotContent("reading_settings_narrow", width = 520, height = 800) {
            BlancallTheme(
                themeMode = state.prefs.themeMode,
                systemDark = false,
                backgroundStyle = state.prefs.backgroundStyle,
                accentIndex = state.prefs.accentIndex,
                uiFont = state.prefs.uiFont,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReadingModeScreen(state, 1, initialSettingsOpen = true)
                }
            }
        }
        state.navigate(Screen.Practice(1))
        shot("practice")
        shot("practice_narrow", width = 520, height = 800)
        state.navigate(Screen.Settings)
        shot("settings")
        shot("settings_narrow", width = 520, height = 800)
        // 各分类的 master-detail 渲染：分类切换由本地状态驱动，测试直接指定初始分类。
        fun settingsShot(name: String, category: String, width: Int = 1200, height: Int = 800) {
            shotContent(name, width, height) {
                BlancallTheme(
                    themeMode = state.prefs.themeMode,
                    systemDark = false,
                    backgroundStyle = state.prefs.backgroundStyle,
                    accentIndex = state.prefs.accentIndex,
                    uiFont = state.prefs.uiFont,
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        SettingsScreen(state, category)
                    }
                }
            }
        }
        settingsShot("settings_study", "STUDY")
        settingsShot("settings_reading", "READING")
        settingsShot("settings_data", "DATA")
        settingsShot("settings_about", "ABOUT")
        settingsShot("settings_study_narrow", "STUDY", width = 520, height = 800)

        // 英文渲染：验证顶栏/设置行/分段控件都真的走了本地化 Text，而不是只有 Strings.t() 单测通过。
        Strings.use(Strings.EN)
        shot("settings_en")
        settingsShot("settings_study_en", "STUDY")
        Strings.use(Strings.ZH_CN)
        state.navigate(Screen.Statistics())
        shot("stats")
        shot("stats_narrow", width = 520, height = 800)
        state.navigate(Screen.Tags)
        shot("tags")
        state.navigate(Screen.SentenceCards())
        shot("sentence_cards")
        state.navigate(Screen.Import)
        shot("import")
        state.navigate(Screen.ContentPacks)
        shot("content_packs")
        state.navigate(Screen.StudyFlow)
        shot("study_flow")
        state.navigate(Screen.CrossPractice(listOf(1L, 2L)))
        shot("cross_practice")
        state.navigate(Screen.TodayPlan)
        shot("today_plan")
        state.navigate(Screen.DueList)
        shot("due_list")
        state.navigate(Screen.PracticeHistory())
        shot("practice_history")
        state.navigate(Screen.Mistakes())
        shot("mistakes")
        state.navigate(Screen.DataInterchange)
        shot("data_interchange")
        state.navigate(Screen.CustomClozeList(1))
        shot("custom_cloze_list")
        state.navigate(Screen.CustomClozeEdit(1))
        shot("custom_cloze_edit")
        state.navigate(Screen.MaskConfigList(1))
        shot("mask_list")
        state.navigate(Screen.MaskConfigEdit(1))
        shot("mask_edit")
        state.navigate(Screen.Guide)
        shot("guide")
        state.navigate(Screen.Help)
        shot("help")
        state.navigate(Screen.Privacy)
        shot("privacy")
        // 双栏：宽窗下文章列表与详情并排
        state.goRoot(Screen.Articles)
        shot("articles_two_pane", width = 1500, height = 900)
        state.prefs.updateThemeMode("dark")
        state.goRoot(Screen.Home)
        shot("home_dark")
        state.goRoot(Screen.Articles)
        shot("articles_dark")
        state.navigate(Screen.Reader(1))
        shot("detail_dark")
        state.navigate(Screen.ReadingMode(1))
        shot("reading_mode_dark")
        state.navigate(Screen.Settings)
        shot("settings_dark")
        state.prefs.updateThemeMode("system")
        state.reportStorageError("保存文章", IllegalStateException("模拟：数据目录不可写"))
        state.goRoot(Screen.Home)
        shot("storage_error")
        state.dismissStorageError()
    }
}
