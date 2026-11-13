package com.ilyskyo.blancall.desktop.platform

import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 导出「学习成绩」分享图（PNG，1080×1350）。
 *
 * 纯本地绘制：不依赖网络/模板，字体优先 Microsoft YaHei UI（Windows 自带）。
 * 图中只包含调用方传入的聚合数字，不含文章正文。
 */
object ShareImageExporter {

    fun export(target: File, title: String, subtitle: String, lines: List<Pair<String, String>>) {
        val width = 1080
        val height = 1350
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            g.paint = GradientPaint(
                0f, 0f, Color(0xFFF7F1E6.toInt()),
                width.toFloat(), height.toFloat(), Color(0xFFEADCC6.toInt()),
            )
            g.fillRect(0, 0, width, height)

            g.color = Color(0xFF7A5C3E.toInt())
            g.font = font(Font.BOLD, 34)
            g.drawString("Blancall", 80, 110)

            g.color = Color(0xFF2C1E11.toInt())
            g.font = font(Font.BOLD, 64)
            g.drawString(title, 80, 220)

            g.color = Color(0xFF6B6259.toInt())
            g.font = font(Font.PLAIN, 30)
            g.drawString(subtitle, 80, 275)

            var y = 400
            lines.forEach { (label, value) ->
                g.color = Color(0xFF51473C.toInt())
                g.font = font(Font.PLAIN, 34)
                g.drawString(label, 80, y)

                g.color = Color(0xFF2C1E11.toInt())
                g.font = font(Font.BOLD, 56)
                val fm = g.fontMetrics
                g.drawString(value, width - 80 - fm.stringWidth(value), y)
                y += 120
            }

            g.color = Color(0xFF7A5C3E.toInt())
            g.font = font(Font.PLAIN, 24)
            g.drawString("填上空白，想起知识。 · 纯本地学习", 80, height - 90)
        } finally {
            g.dispose()
        }
        ImageIO.write(img, "png", target)
    }

    private fun font(style: Int, size: Int): Font =
        runCatching { Font("Microsoft YaHei UI", style, size) }
            .getOrDefault(Font("Dialog", style, size))
}
