package com.ilyskyo.blancall.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 多语言渲染链路守卫。
 *
 * 只测 `Strings.t()` 是不够的：UI 曾经在共享组件里直接 import Material3 的 Text，
 * 导致顶栏、导航、设置行全部绕过翻译，用户在设置里切了语言却看不出变化。
 * 这里直接扫源码，确保「本地化 Text」是唯一入口。
 */
class I18nUiGuardTest {

    @Test
    fun `UI 源码不得直接使用 Material3 Text`() {
        val roots = listOf(
            File("src/main/kotlin"),
            File("desktop/src/main/kotlin"),
        ).filter { it.isDirectory }
        assertTrue(roots.isNotEmpty(), "找不到 desktop 源码目录")

        val offenders = roots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.name != "LocalizedText.kt" }
                .filter { file ->
                    file.readLines().any { line ->
                        line.trim() == "import androidx.compose.material3.Text" ||
                            line.trim().startsWith("import androidx.compose.material3.Text as ")
                    }
                }
                .toList()
        }
        assertTrue(
            offenders.isEmpty(),
            "以下文件绕过了本地化 Text，切换语言时不会翻译：\n" +
                offenders.joinToString("\n") { it.path },
        )
    }
}
