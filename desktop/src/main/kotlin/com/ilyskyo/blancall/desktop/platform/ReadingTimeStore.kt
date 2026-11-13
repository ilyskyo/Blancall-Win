package com.ilyskyo.blancall.desktop.platform

import java.io.File
import java.util.Properties

/**
 * 每篇累计阅读秒数（对应 Android `AppPrefs.getReadingSeconds`）。
 *
 * 存在 `settings/reading_time.properties`，不进备份：与 Android 的口径一致
 * （阅读时长是设备本地行为统计，不是学习数据）。
 */
object ReadingTimeStore {

    private val file: File get() = File(AppPaths.settingsDir, "reading_time.properties")
    private val lock = Any()
    private var loaded: Properties? = null

    private fun props(): Properties {
        loaded?.let { return it }
        val p = Properties()
        runCatching {
            if (file.isFile) file.inputStream().use { p.load(it) }
        }
        loaded = p
        return p
    }

    fun add(articleId: Long, seconds: Long) {
        if (articleId <= 0 || seconds <= 0) return
        synchronized(lock) {
            val p = props()
            val key = keyOf(articleId)
            val current = p.getProperty(key, "0").toLongOrNull() ?: 0L
            p.setProperty(key, (current + seconds).toString())
            persist(p)
        }
    }

    fun get(articleId: Long): Long = synchronized(lock) {
        props().getProperty(keyOf(articleId), "0").toLongOrNull() ?: 0L
    }

    private fun keyOf(articleId: Long) = "a$articleId"

    private fun persist(p: Properties) {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { p.store(it, "Blancall reading time") }
        }
    }
}
