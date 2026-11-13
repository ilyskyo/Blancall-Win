package com.ilyskyo.blancall.desktop.platform

import java.awt.Window
import java.io.File
import java.util.Properties

/** 窗口位置/大小记忆（像素，避免 dp/dpi 换算误差）。 */
object WindowGeometry {
    private val file: File get() = File(AppPaths.settingsDir, "window.properties")

    data class Geometry(val x: Int, val y: Int, val width: Int, val height: Int)

    fun load(): Geometry? = runCatching {
        if (!file.isFile) return null
        val p = Properties()
        file.inputStream().use { p.load(it) }
        Geometry(
            x = p.getProperty("x")?.toIntOrNull() ?: return null,
            y = p.getProperty("y")?.toIntOrNull() ?: return null,
            width = p.getProperty("width")?.toIntOrNull() ?: return null,
            height = p.getProperty("height")?.toIntOrNull() ?: return null,
        )
    }.getOrNull()

    fun save(window: Window) {
        runCatching {
            file.parentFile?.mkdirs()
            val p = Properties()
            p.setProperty("x", window.x.toString())
            p.setProperty("y", window.y.toString())
            p.setProperty("width", window.width.toString())
            p.setProperty("height", window.height.toString())
            file.outputStream().use { p.store(it, "Blancall window geometry") }
        }
    }
}
