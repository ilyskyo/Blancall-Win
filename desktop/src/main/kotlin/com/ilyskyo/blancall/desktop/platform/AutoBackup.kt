package com.ilyskyo.blancall.desktop.platform

import com.ilyskyo.blancall.data.backup.BackupManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 每日自动备份：保留最近 7 份，失败只记日志，不阻塞启动。 */
object AutoBackup {

    fun runIfDue(dataDir: File, backupsDir: File) {
        runCatching {
            backupsDir.mkdirs()
            val existing = backupsDir.listFiles { f -> f.isFile && f.name.startsWith("auto_") }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()
            val newest = existing.firstOrNull()
            if (newest != null &&
                System.currentTimeMillis() - newest.lastModified() < TimeUnit.HOURS.toMillis(24)
            ) {
                return
            }
            val stamp = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
            val target = File(backupsDir, "auto_$stamp.zip")
            if (target.isFile) return
            runCatching {
                target.outputStream().use { out ->
                    BackupManager.exportZip(dataDir, out, BackupManager.Group.defaultSelection())
                }
            }.onFailure {
                target.delete()
                return
            }
            existing.drop(6).forEach { runCatching { it.delete() } }
        }
    }
}
