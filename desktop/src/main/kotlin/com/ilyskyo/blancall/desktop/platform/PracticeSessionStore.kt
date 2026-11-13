package com.ilyskyo.blancall.desktop.platform

import com.ilyskyo.blancall.util.AtomicFiles
import org.json.JSONObject
import java.io.File

/**
 * 断点续练：文件格式与 Android 的 `practice_state_<articleId>.json` 对齐。
 * Android 扫描器只认 `status == "IN_PROGRESS"`，这里保持大写。
 */
object PracticeSessionStore {

    data class Saved(
        val mode: String,
        val answers: Map<Int, String>,
        val dictationInput: String,
        val totalBlanks: Int,
    )

    private fun file(dataDir: File, articleId: Long) =
        File(dataDir, "practice_state_$articleId.json")

    fun load(dataDir: File, articleId: Long): Saved? {
        return runCatching {
            val f = file(dataDir, articleId)
            if (!f.isFile) return null
            val root = JSONObject(f.readText())
            if (root.optString("status") != "IN_PROGRESS") return null
            val total = root.optInt("totalBlanks", 0)
            val answered = root.optInt("answeredCount", 0)
            if (total - answered <= 0) return null
            val answersObj = root.optJSONObject("answers") ?: JSONObject()
            val answers = buildMap {
                answersObj.keys().forEach { key ->
                    val index = key.toIntOrNull() ?: return@forEach
                    put(index, answersObj.optString(key, ""))
                }
            }
            Saved(
                mode = root.optString("mode", ""),
                answers = answers,
                dictationInput = root.optString("dictationInput", ""),
                totalBlanks = total,
            )
        }.getOrNull()
    }

    fun save(
        dataDir: File,
        articleId: Long,
        mode: String,
        totalBlanks: Int,
        answers: Map<Int, String>,
        dictationInput: String = "",
        startedAt: Long,
    ) {
        runCatching {
            val answered = answers.count { it.value.isNotBlank() }
            if (answered <= 0 && dictationInput.isBlank()) return
            val root = JSONObject()
            root.put("articleId", articleId)
            root.put("mode", mode)
            root.put("status", "IN_PROGRESS")
            root.put("totalBlanks", totalBlanks)
            root.put("answeredCount", answered)
            root.put("dictationInput", dictationInput)
            root.put("lastPracticeTime", System.currentTimeMillis())
            root.put("startedAt", startedAt)
            val answersObj = JSONObject()
            answers.forEach { (index, value) -> answersObj.put(index.toString(), value) }
            root.put("answers", answersObj)
            AtomicFiles.writeTextAtomic(file(dataDir, articleId), root.toString())
        }
    }

    fun clear(dataDir: File, articleId: Long) {
        runCatching {
            val f = file(dataDir, articleId)
            if (f.exists()) AtomicFiles.deleteWithCompanions(f)
        }
    }
}
