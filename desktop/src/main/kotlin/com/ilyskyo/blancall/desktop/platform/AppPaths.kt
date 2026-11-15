package com.ilyskyo.blancall.desktop.platform

import java.io.File

/**
 * Windows 数据目录：
 * `%LOCALAPPDATA%\Blancall\`，data/ 与 Android 的 filesDir 同构。
 */
object AppPaths {
    private val root: File = run {
        val local = System.getenv("LOCALAPPDATA")
        if (!local.isNullOrBlank()) File(local, "Blancall")
        else File(System.getProperty("user.home"), "Blancall")
    }

    val rootDir: File get() = root
    val dataDir: File get() = File(root, "data")
    val backupsDir: File get() = File(root, "backups")
    val logsDir: File get() = File(root, "logs")
    val modelsDir: File get() = File(root, "models")
    val fontsDir: File get() = File(root, "fonts")
    val cacheDir: File get() = File(root, "cache")
    val settingsDir: File get() = File(root, "settings")

    /** @return 全部目录是否可用；失败时由调用方显示常驻错误，不能静默继续。 */
    fun ensureDirectories(): Boolean {
        var ok = true
        listOf(dataDir, backupsDir, logsDir, modelsDir, fontsDir, cacheDir, settingsDir).forEach {
            if (!it.isDirectory && !it.mkdirs() && !it.isDirectory) ok = false
        }
        return ok
    }
}
