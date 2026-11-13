package com.ilyskyo.blancall.desktop.platform

import java.awt.GraphicsEnvironment
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 导出诊断包：只包含**环境信息 + 日志 + 偏好摘要 + 数据文件清单（名称/大小）**，
 * 不含文章正文、练习记录内容或任何用户文本。
 *
 * 与 Android 的差别：Android 没有这个入口；桌面版按发布要求提供，但保持最小披露。
 */
object DiagnosticPackage {

    fun export(target: File, dataDir: File, appVersion: String): Int {
        var entries = 0
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
                entries++
            }

            put("environment.txt", environmentText(dataDir, appVersion).toByteArray(Charsets.UTF_8))

            val logs = AppPaths.logsDir
                .listFiles { f -> f.isFile && f.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(10)
                .orEmpty()
            logs.forEach { file ->
                runCatching { put("logs/${file.name}", file.readBytes()) }
            }

            // 偏好：只带桌面设置，不含学习数据正文
            listOf("ui_prefs.properties", "reminder.properties", "window.properties", "window.json")
                .forEach { name ->
                    val f = File(AppPaths.settingsDir, name)
                    if (f.isFile) runCatching { put("settings/$name", f.readBytes()) }
                }

            val census = StringBuilder()
            census.append("version=1\n")
            dataDir.listFiles()?.sortedBy { it.name }?.forEach { f ->
                val kind = if (f.isDirectory) "dir" else "file"
                val size = if (f.isDirectory) {
                    f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                } else f.length()
                census.append("${f.name}\t$kind\t$size\n")
            }
            put("data-census.txt", census.toString().toByteArray(Charsets.UTF_8))
        }
        return entries
    }

    private fun environmentText(dataDir: File, appVersion: String): String = buildString {
        appendLine("Blancall Windows $appVersion")
        appendLine("time=${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
        appendLine("os=${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("java=${System.getProperty("java.version")} ${System.getProperty("java.vendor")}")
        appendLine("locale=${java.util.Locale.getDefault()}")
        appendLine("timezone=${java.util.TimeZone.getDefault().id}")
        appendLine("dataDir=${dataDir.absolutePath}")
        appendLine("userHome=${System.getProperty("user.home")}")
        runCatching {
            val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
            appendLine("screen=${env.maximumWindowBounds.width}x${env.maximumWindowBounds.height}")
            env.screenDevices.forEach { d ->
                appendLine(
                    "display=$d ${d.displayMode.width}x${d.displayMode.height}" +
                        " scale=${d.defaultConfiguration.defaultTransform.scaleX}"
                )
            }
        }
        appendLine("traySupported=${java.awt.SystemTray.isSupported()}")
        appendLine("fileEncoding=${System.getProperty("file.encoding")}")
    }
}
