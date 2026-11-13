package com.ilyskyo.blancall.desktop.platform

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption

/**
 * 单实例：防止两个进程同时写 `data/`。
 * 第二个实例只提示并退出（激活已有窗口的 IPC 在后续迭代补）。
 */
object SingleInstance {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    fun acquire(): Boolean {
        return runCatching {
            val file = File(AppPaths.rootDir, ".instance.lock")
            file.parentFile?.mkdirs()
            val ch = FileChannel.open(
                file.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val l = ch.tryLock()
            if (l == null) {
                ch.close()
                false
            } else {
                channel = ch
                lock = l
                true
            }
        }.getOrDefault(true) // 锁不可用时宁可放行，也不能阻止用户启动
    }

    fun release() {
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        lock = null
        channel = null
    }
}
