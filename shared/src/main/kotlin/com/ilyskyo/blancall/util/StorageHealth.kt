// Copyright (c) 2026 ilyskyo
// SPDX-License-Identifier: MIT

package com.ilyskyo.blancall.util

import java.io.File

/**
 * 全局落盘失败出口。
 *
 * 各 Store 的写路径历史上都 `catch (_: Exception)` 后继续跑：界面显示成功、
 * 重启后数据消失，用户只能看到一句「保存失败」或什么都看不到。
 * 这里不改变任何 Store 的控制流，只在 [AtomicFiles] 抛出前广播一次，
 * 由桌面壳接上常驻横幅；共享层本身不依赖 UI。
 *
 * 回调必须轻量且不得抛异常；重复失败由监听方自行去重。
 */
object StorageHealth {

    @Volatile
    var listener: ((file: File, error: Throwable) -> Unit)? = null

    fun report(file: File, error: Throwable) {
        runCatching { listener?.invoke(file, error) }
    }
}
