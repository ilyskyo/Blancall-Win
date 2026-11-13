package com.ilyskyo.blancall.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Windows 专属备份安全校验：
 * `.` 段、保留设备名、大小写折叠冲突、重复条目都必须整包拒绝且不落盘。
 */
class BackupWindowsSafetyTest {

    private fun tempDir(name: String): File =
        Files.createTempDirectory("blancall-win-safety-$name").toFile()

    private fun zipWith(entries: List<Pair<String, String>>, manifestFiles: List<String>): File {
        val zip = File(tempDir("zip"), "backup.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            val filesJson = manifestFiles.joinToString(",") { "\"$it\"" }
            z.write("""{"formatVersion":1,"files":[$filesJson]}""".toByteArray())
            z.closeEntry()
            entries.forEach { (name, content) ->
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray())
                z.closeEntry()
            }
        }
        return zip
    }

    private inline fun <reified T : BackupManager.BackupError> expectReject(block: () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("期望 ${T::class.simpleName}，实际 ${thrown?.let { it::class.simpleName }}", thrown is T)
    }

    @Test
    fun `拒绝点段与保留名`() {
        val dst = tempDir("dst")
        File(dst, "articles.json").writeText("original")

        expectReject<BackupManager.BackupError.UnsafeEntry> {
            BackupManager.restoreZip(
                zipWith(listOf("files/." to "x"), listOf("files/.")),
                dst,
            )
        }
        expectReject<BackupManager.BackupError.UnsafeEntry> {
            BackupManager.restoreZip(
                zipWith(listOf("files/CON" to "x"), listOf("files/CON")),
                dst,
            )
        }
        expectReject<BackupManager.BackupError.UnsafeEntry> {
            BackupManager.restoreZip(
                zipWith(listOf("files/COM1.json" to "x"), listOf("files/COM1.json")),
                dst,
            )
        }
        assertFalse("不得写到 data 目录之外", File(dst.parentFile, "${dst.name}.import").exists())
        assertTrue(File(dst, "articles.json").readText() == "original")
    }

    @Test
    fun `拒绝大小写冲突与重复条目`() {
        val dst = tempDir("dst2")
        File(dst, "articles.json").writeText("original")

        expectReject<BackupManager.BackupError.UnsafeEntry> {
            BackupManager.restoreZip(
                zipWith(
                    listOf("files/A.json" to "a", "files/a.json" to "b"),
                    listOf("files/A.json", "files/a.json"),
                ),
                dst,
            )
        }
        expectReject<BackupManager.BackupError.UnsafeEntry> {
            BackupManager.restoreZip(
                zipWith(
                    listOf("files/articles.json" to "a"),
                    listOf("files/articles.json", "files/articles.json"),
                ),
                dst,
            )
        }
        assertTrue(File(dst, "articles.json").readText() == "original")
    }
}
