package com.ilyskyo.blancall.desktop.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * 原生文件对话框。桌面优先用 AWT（Windows 上是系统对话框），
 * 不用 SAF/Uri 概念。
 */
object FileDialogs {

    fun open(title: String, extensions: List<String>): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isMultipleMode = false
        if (extensions.isNotEmpty()) {
            dialog.setFilenameFilter { _, name ->
                extensions.any { name.lowercase().endsWith(it) }
            }
        }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(dir, name)
    }

    fun save(title: String, defaultName: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = defaultName
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(dir, name)
    }
}
