package com.ilyskyo.blancall.desktop.platform

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.Window

/**
 * Windows 11 标题栏云母（DWM_SYSTEMBACKDROP_TYPE）。
 *
 * ARCHITECTURE ADR-016 明确不做实时背景采样；这里只让系统标题栏使用
 * 原生 Mica，客户端区域仍是可读的静态玻璃，避免半透明正文影响阅读。
 * 非 Windows 11 或调用失败时静默回退，绝不因材质开关影响启动。
 */
object WindowsBackdrop {

    private const val DWMWA_SYSTEMBACKDROP_TYPE = 38
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMSBT_MICA = 2

    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int
    }

    @Volatile
    private var api: DwmApi? = null

    fun apply(window: Window, enabled: Boolean, darkTitleBar: Boolean = false) {
        if (!enabled) return
        runCatching {
            val hwnd = Native.getWindowPointer(window) ?: return
            val dwm = api ?: runCatching { Native.load("dwmapi", DwmApi::class.java) }
                .getOrNull()
                ?.also { api = it }
                ?: return
            Memory(4).use { memory ->
                memory.setInt(0, if (darkTitleBar) 1 else 0)
                dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, memory, 4)
                memory.setInt(0, DWMSBT_MICA)
                dwm.DwmSetWindowAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, memory, 4)
            }
        }
    }
}
