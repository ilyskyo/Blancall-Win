package com.ilyskyo.blancall.desktop.platform

import java.awt.Taskbar

/**
 * 任务栏进度（N-07 的桌面近似）。
 *
 * Windows + 当前 JDK 不一定实现 Taskbar 进度；所有调用都吞掉
 * UnsupportedOperationException / SecurityException，不能因为任务栏不支持就让练习页崩溃。
 */
object TaskbarProgress {

    fun set(fraction: Float) = runCatching {
        val taskbar = Taskbar.getTaskbar()
        taskbar.setWindowProgressValue(null, (fraction.coerceIn(0f, 1f) * 100).toInt())
        taskbar.setWindowProgressState(null, Taskbar.State.NORMAL)
    }

    fun indeterminate() = runCatching {
        val taskbar = Taskbar.getTaskbar()
        taskbar.setWindowProgressState(null, Taskbar.State.INDETERMINATE)
    }

    fun clear() = runCatching {
        Taskbar.getTaskbar().setWindowProgressState(null, Taskbar.State.OFF)
    }
}
