package com.ilyskyo.blancall.data.repository

import com.ilyskyo.blancall.data.model.PracticeRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 桌面端新增的 `record_id_seq` 守卫：
 * Android 的记录 id 没有持久游标，删除最大 id 后重启会复用，
 * 导致 `ink/<recordId>.json` 与复习日志挂到新记录上。
 */
class RecordIdCursorTest {

    private fun tempFile(): File =
        File(Files.createTempDirectory("blancall-records").toFile(), "records.json")

    @Test
    fun `删除最大记录后重启不复用旧 id`() = runBlocking {
        val file = tempFile()
        val first = RecordRepository(file.absolutePath)
        first.awaitLoaded()
        val a = first.insert(PracticeRecord(articleId = 1L, mode = "SENTENCE", totalBlanks = 1, correctCount = 1))
        val b = first.insert(PracticeRecord(articleId = 2L, mode = "SENTENCE", totalBlanks = 1, correctCount = 1))
        first.deleteByArticleId(2L)

        // 模拟重启
        val second = RecordRepository(file.absolutePath)
        second.awaitLoaded()
        val c = second.insert(PracticeRecord(articleId = 3L, mode = "SENTENCE", totalBlanks = 1, correctCount = 1))
        assertTrue("新 id 必须大于已删除的最大 id：a=${a.id}, b=${b.id}, c=${c.id}", c.id > b.id)
    }

    @Test
    fun `游标文件缺失时按最大记录 id 兜底`() = runBlocking {
        val file = tempFile()
        val first = RecordRepository(file.absolutePath)
        first.awaitLoaded()
        first.insert(PracticeRecord(articleId = 1L, mode = "SENTENCE", totalBlanks = 1, correctCount = 1))
        File(file.parentFile, "record_id_seq").delete()

        val second = RecordRepository(file.absolutePath)
        second.awaitLoaded()
        val next = second.insert(PracticeRecord(articleId = 2L, mode = "SENTENCE", totalBlanks = 1, correctCount = 1))
        assertTrue("缺游标时至少不能小于 maxId+1", next.id >= 2L)
    }
}
