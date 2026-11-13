package com.ilyskyo.blancall.desktop.platform

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.awt.Window
/**
 * 托盘：关闭到托盘后仍可接收提醒/快速回到应用。
 * 托盘不可用时静默降级（不崩溃）。
 */
class TrayController(
    private val window: Window,
    private val onExit: () -> Unit,
) {
    private var trayIcon: TrayIcon? = null

    init {
        runCatching {
            if (SystemTray.isSupported()) {
                val tray = SystemTray.getSystemTray()
                val menu = PopupMenu()
                val open = MenuItem("打开 Blancall")
                open.addActionListener { showWindow() }
                val exit = MenuItem("退出")
                exit.addActionListener { onExit() }
                menu.add(open)
                menu.add(exit)
                val icon = TrayIcon(createIcon(), "Blancall", menu)
                icon.addActionListener { showWindow() }
                tray.add(icon)
                trayIcon = icon
            }
        }
    }

    fun showWindow() {
        window.isVisible = true
        window.toFront()
        window.requestFocus()
    }

    fun hideWindow() {
        window.isVisible = false
    }

    /**
     * 托盘气泡通知。托盘不可用（远程桌面/精简系统）时返回 false，
     * 由调用方退回应用内提示，绝不因为通知失败影响主流程。
     */
    fun displayMessage(title: String, body: String): Boolean {
        val icon = trayIcon ?: return false
        return runCatching {
            icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
            true
        }.getOrDefault(false)
    }

    /** 托盘菜单里显示「今日复习」摘要（提醒可见性的兜底）。 */
    fun updateTooltip(text: String) {
        runCatching { trayIcon?.toolTip = text }
    }

    fun dispose() {
        trayIcon?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        trayIcon = null
    }

    private fun createIcon(): BufferedImage {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g: Graphics2D = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x7A5C3E)
        g.fillRoundRect(0, 0, 16, 16, 5, 5)
        g.color = Color.WHITE
        g.font = Font("Microsoft YaHei UI", Font.BOLD, 11)
        g.drawString("B", 4, 12)
        g.dispose()
        return img
    }
}
