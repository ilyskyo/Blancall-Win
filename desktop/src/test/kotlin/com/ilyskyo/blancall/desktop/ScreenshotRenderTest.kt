package com.ilyskyo.blancall.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Screen
import com.ilyskyo.blancall.desktop.ui.BlancallApp
import java.io.File
import kotlin.test.Test
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
        val out = File("_shots").apply { mkdirs() }

        fun shot(name: String) {
            val scene = ImageComposeScene(
                width = 1440,
                height = 1000,
                density = Density(1.0f),
            ) {
                BlancallApp(state)
            }
            try {
                val image = scene.render(0L)
                image.encodeToData(EncodedImageFormat.PNG)?.bytes?.let {
                    File(out, "render_$name.png").writeBytes(it)
                }
            } finally {
                scene.close()
            }
        }

        shot("home")
        state.navigate(Screen.Articles)
        shot("articles")
        state.navigate(Screen.Settings)
        shot("settings")
    }
}
