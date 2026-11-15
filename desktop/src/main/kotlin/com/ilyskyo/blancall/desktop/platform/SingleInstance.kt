package com.ilyskyo.blancall.desktop.platform

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import javax.swing.SwingUtilities

/**
 * 单实例 + 第二实例激活。
 *
 * 第一道防线是 `%LOCALAPPDATA%\Blancall\.instance.lock` 文件锁：
 * 两个进程同时写 `data/` 会互相覆盖，锁失败即判定已有实例。
 * 第二道是本机回环端口：第二实例把命令行参数（如 `--route=practice --article=12`）
 * 发给已有进程后退出，由已有进程显示窗口并导航。
 *
 * 端口只绑 loopback，不对外监听；文件锁不可用（权限/网络盘）时宁可放行启动，
 * 也不能让应用打不开。
 */
object SingleInstance {

    private const val PORT = 47823
    private const val MAGIC = "BLANCALL_ACTIVATE"

    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null
    private var serverThread: Thread? = null

    @Volatile
    private var activationHandler: ((Array<String>) -> Unit)? = null

    private var pendingArgs: Array<String>? = null

    /** 第二实例转发是否成功；失败时调用方应提示用户从任务栏/托盘打开。 */
    @Volatile
    var lastForwardSucceeded: Boolean = false
        private set

    /**
     * @param args 本进程命令行参数；仅在已有实例时通过回环转发
     * @return true = 当前进程是主实例；false = 已有实例（参数已转发）
     */
    fun acquire(args: Array<String> = emptyArray()): Boolean {
        val locked = runCatching { tryLock() }.getOrElse {
            // 锁机制本身不可用：放行启动，不让权限问题把应用挡在门外
            return true
        }
        if (!locked) {
            lastForwardSucceeded = sendActivation(args)
            return false
        }
        startServer()
        return true
    }

    private fun tryLock(): Boolean {
        val file = File(AppPaths.rootDir, ".instance.lock")
        file.parentFile?.mkdirs()
        val ch = FileChannel.open(
            file.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
        val l = ch.tryLock()
        return if (l == null) {
            ch.close()
            false
        } else {
            channel = ch
            lock = l
            true
        }
    }

    fun setActivationHandler(handler: (Array<String>) -> Unit) {
        activationHandler = handler
        pendingArgs?.let {
            pendingArgs = null
            SwingUtilities.invokeLater { handler(it) }
        }
    }

    private fun sendActivation(args: Array<String>): Boolean = runCatching {
        Socket(InetAddress.getLoopbackAddress(), PORT).use { socket ->
            socket.soTimeout = 900
            val line = MAGIC + "\t" + args.joinToString("\u0001")
            socket.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        }
        true
    }.getOrDefault(false)

    private fun startServer() {
        if (server != null) return
        serverThread = Thread({
            runCatching {
                val socket = ServerSocket(PORT, 8, InetAddress.getLoopbackAddress())
                server = socket
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: continue
                    runCatching {
                        client.use {
                            val line = it.getInputStream().bufferedReader(Charsets.UTF_8).readLine()
                                ?: return@use
                            if (!line.startsWith(MAGIC)) return@use
                            val payload = line.removePrefix(MAGIC).removePrefix("\t")
                            val args = if (payload.isEmpty()) emptyArray()
                            else payload.split('\u0001').toTypedArray()
                            val handler = activationHandler
                            if (handler != null) {
                                SwingUtilities.invokeLater { handler(args) }
                            } else {
                                pendingArgs = args
                            }
                        }
                    }
                }
            }
        }, "blancall-activation").apply {
            isDaemon = true
            start()
        }
    }

    fun release() {
        runCatching { server?.close() }
        server = null
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }
}
