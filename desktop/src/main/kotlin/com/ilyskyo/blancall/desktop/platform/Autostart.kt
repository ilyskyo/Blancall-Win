package com.ilyskyo.blancall.desktop.platform

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 开机自启：只写 HKCU 的 Run 项，不需要管理员权限。
 *
 * 值指向当前可执行文件（安装版为 Blancall.exe；开发运行为 java -jar），
 * 用户换目录后旧值会失效，设置页再次开关即可重写。
 */
object Autostart {

    private const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val NAME = "Blancall"

    fun isEnabled(): Boolean = runCatching {
        val process = ProcessBuilder("reg", "query", KEY, "/v", NAME)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(2, TimeUnit.SECONDS)
        process.exitValue() == 0 && output.contains(NAME, ignoreCase = true)
    }.getOrDefault(false)

    fun setEnabled(enabled: Boolean): Boolean = runCatching {
        val command = if (enabled) {
            listOf("reg", "add", KEY, "/v", NAME, "/t", "REG_SZ", "/d", launchCommand(), "/f")
        } else {
            listOf("reg", "delete", KEY, "/v", NAME, "/f")
        }
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        process.waitFor(3, TimeUnit.SECONDS)
        // 删除一个不存在的值会返回非 0；关闭时把「本来就没有」也视为成功
        enabled == (process.exitValue() == 0) || (!enabled && !isEnabled())
    }.getOrDefault(false)

    private fun launchCommand(): String {
        val packaged = System.getProperty("jpackage.app-path")
        if (!packaged.isNullOrBlank() && File(packaged).isFile) return "\"$packaged\""
        val java = ProcessHandle.current().info().command().orElse("javaw.exe")
        val jar = runCatching {
            val location = File(
                Autostart::class.java.protectionDomain.codeSource.location.toURI(),
            )
            location.takeIf { it.isFile && it.extension.equals("jar", ignoreCase = true) }
        }.getOrNull()
        return if (jar != null) "\"$java\" -jar \"${jar.absolutePath}\"" else "\"$java\""
    }
}
