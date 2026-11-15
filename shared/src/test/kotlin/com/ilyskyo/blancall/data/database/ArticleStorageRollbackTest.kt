package com.ilyskyo.blancall.data.database

import com.ilyskyo.blancall.data.model.Article
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * 文章写入失败时不能留下「内存里有、磁盘上没有」的假成功。
 *
 * 用户实测过：保存报错，但文章列表里已经出现新文章，重启后消失。
 * 这里用「父路径是普通文件」稳定制造写盘失败，断言内存回滚。
 */
class ArticleStorageRollbackTest {

    @Test
    fun `插入失败回滚内存列表`() = runBlocking {
        val root = Files.createTempDirectory("blancall-article-rollback").toFile()
        val blocked = File(root, "data").apply { writeText("not a directory") }
        val storage = ArticleStorage(File(blocked, "articles.json").absolutePath)
        storage.awaitLoaded()

        val error = runCatching {
            storage.insert(Article(title = "未落盘", content = "这段正文只应存在于失败的写盘尝试里。"))
        }.exceptionOrNull()

        assertNotNull(error, "写盘失败必须抛给调用方")
        assertTrue(storage.snapshot.isEmpty(), "写盘失败后内存必须回滚，实际 ${storage.snapshot.map { it.title }}")
    }

    @Test
    fun `删除失败回滚内存列表`() = runBlocking {
        val root = Files.createTempDirectory("blancall-article-delete-rollback").toFile()
        val data = File(root, "data").apply { mkdirs() }
        val storage = ArticleStorage(File(data, "articles.json").absolutePath)
        storage.awaitLoaded()
        val id = storage.insert(Article(title = "保留", content = "删除失败时不能从列表消失。"))

        // 插成功后把数据目录换成普通文件：下一次写盘必然失败，
        // 但内存里仍应保留这篇文章（回滚删除）。
        assertTrue(data.deleteRecursively())
        data.writeText("not a directory")

        val error = runCatching { storage.deleteAll(setOf(id)) }.exceptionOrNull()
        assertNotNull(error, "写盘失败必须抛给调用方")
        assertTrue(
            storage.snapshot.any { it.id == id },
            "删除落盘失败后必须回滚，实际 ${storage.snapshot.map { it.title }}",
        )
    }
}
