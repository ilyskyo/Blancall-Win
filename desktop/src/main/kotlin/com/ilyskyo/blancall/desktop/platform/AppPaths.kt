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
    val cacheDir: File get() = File(root, "cache")
    val settingsDir: File get() = File(root, "settings")

    fun ensureDirectories() {
        listOf(dataDir, backupsDir, logsDir, modelsDir, cacheDir, settingsDir).forEach {
            if (!it.exists()) it.mkdirs()
        }
    }
}
