package com.ilyskyo.blancall.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Properties

/**
 * 桌面偏好：只存 UI 级设置，不碰 Android 的 data/ 文件。
 * 使用 Properties 而不是 JSON，避免把桌面设置混进学习数据格式。
 */
class DesktopPrefs(private val file: File) {

    var themeMode by mutableStateOf("system") // system / light / dark
        private set
    var readerFontPx by mutableStateOf(18f)
        private set
    var readerLineHeight by mutableStateOf(1.8f)
        private set
    var lastArticleId by mutableStateOf(-1L)
        private set
    /** ask / tray / exit */
    var closeAction by mutableStateOf("ask")
        private set
    var hintsEnabled by mutableStateOf(true)
        private set
    /** 复习模板：sprint / standard / deep（对应 FSRS 目标留存率） */
    var reviewTemplateId by mutableStateOf("standard")
        private set
    /** 段落首行缩进（与 Android「段落首行缩进」同义） */
    var paragraphIndent by mutableStateOf(true)
        private set
    /** 阅读背景：default / sepia / gray（C-05） */
    var backgroundStyle by mutableStateOf("sepia")
        private set
    /** 界面字号缩放 0.85–1.30（C-04） */
    var uiScale by mutableStateOf(1f)
        private set
    /** 最近打开的文章（最多 8 个，最近的在前） */
    var recentArticleIds by mutableStateOf<List<Long>>(emptyList())
        private set
    /** 界面语言标签（zh-CN / zh-TW / en / ja / ko / ru） */
    var language by mutableStateOf("zh-CN")
        private set
    /** 是否已看过首启欢迎页 */
    var welcomeShown by mutableStateOf(false)
        private set
    /** 句子卡回忆面是否挖空遮挡（关 = 整句直出） */
    var sentenceClozeEnabled by mutableStateOf(true)
        private set
    /** 首次见到的句子是否也遮挡（关 = 新句先给全文） */
    var sentenceClozeNewEnabled by mutableStateOf(false)
        private set
    /** 首页卡片顺序（HomeSection.key） */
    var homeSectionOrder by mutableStateOf(DEFAULT_HOME_ORDER)
        private set
    /** 首页隐藏的卡片 key */
    var homeSectionHidden by mutableStateOf<Set<String>>(emptySet())
        private set
    /** 强调色序号（与 Android AccentPresets 同一组 6 套） */
    var accentIndex by mutableStateOf(0)
        private set

    init {
        runCatching {
            if (file.isFile) {
                val p = Properties()
                file.inputStream().use { p.load(it) }
                themeMode = p.getProperty("themeMode", "system")
                readerFontPx = p.getProperty("readerFontPx", "18").toFloatOrNull() ?: 18f
                readerLineHeight = p.getProperty("readerLineHeight", "1.8").toFloatOrNull() ?: 1.8f
                lastArticleId = p.getProperty("lastArticleId", "-1").toLongOrNull() ?: -1L
                closeAction = p.getProperty("closeAction", "ask")
                hintsEnabled = p.getProperty("hintsEnabled", "true").toBoolean()
                reviewTemplateId = p.getProperty("reviewTemplateId", "standard")
                paragraphIndent = p.getProperty("paragraphIndent", "true").toBoolean()
                backgroundStyle = p.getProperty("backgroundStyle", "sepia")
                uiScale = p.getProperty("uiScale", "1").toFloatOrNull()?.coerceIn(0.85f, 1.30f) ?: 1f
                recentArticleIds = p.getProperty("recentArticleIds", "")
                    .split(',')
                    .mapNotNull { it.trim().toLongOrNull() }
                    .take(8)
                language = p.getProperty("language", "zh-CN")
                welcomeShown = p.getProperty("welcomeShown", "false").toBoolean()
                sentenceClozeEnabled = p.getProperty("sentenceClozeEnabled", "true").toBoolean()
                sentenceClozeNewEnabled = p.getProperty("sentenceClozeNewEnabled", "false").toBoolean()
                homeSectionOrder = normalizeHomeOrder(
                    p.getProperty("homeSectionOrder", "").split(',').map { it.trim() },
                )
                homeSectionHidden = p.getProperty("homeSectionHidden", "")
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                accentIndex = p.getProperty("accentIndex", "0").toIntOrNull()?.coerceIn(0, 5) ?: 0
            }
        }
    }

    fun updateThemeMode(value: String) {
        themeMode = value
        save()
    }

    fun updateReaderFontPx(value: Float) {
        readerFontPx = value.coerceIn(14f, 36f)
        save()
    }

    fun updateReaderLineHeight(value: Float) {
        readerLineHeight = value.coerceIn(1.2f, 2.6f)
        save()
    }

    fun updateLastArticleId(value: Long) {
        lastArticleId = value
        save()
    }

    fun updateCloseAction(value: String) {
        closeAction = value
        save()
    }

    fun updateHintsEnabled(value: Boolean) {
        hintsEnabled = value
        save()
    }

    fun updateReviewTemplate(value: String) {
        reviewTemplateId = value
        save()
    }

    fun updateParagraphIndent(value: Boolean) {
        paragraphIndent = value
        save()
    }

    fun updateBackgroundStyle(value: String) {
        backgroundStyle = value
        save()
    }

    fun updateUiScale(value: Float) {
        uiScale = value.coerceIn(0.85f, 1.30f)
        save()
    }

    fun updateAccentIndex(value: Int) {
        accentIndex = value.coerceIn(0, 5)
        save()
    }

    fun pushRecentArticle(articleId: Long) {
        if (articleId <= 0) return
        recentArticleIds = (listOf(articleId) + recentArticleIds.filter { it != articleId }).take(8)
        save()
    }

    fun updateLanguage(tag: String) {
        language = tag
        save()
    }

    fun updateWelcomeShown(value: Boolean) {
        welcomeShown = value
        save()
    }

    fun updateSentenceClozeEnabled(value: Boolean) {
        sentenceClozeEnabled = value
        save()
    }

    fun updateSentenceClozeNewEnabled(value: Boolean) {
        sentenceClozeNewEnabled = value
        save()
    }

    fun updateHomeLayout(order: List<String>, hidden: Set<String>) {
        homeSectionOrder = normalizeHomeOrder(order)
        homeSectionHidden = hidden
        save()
    }

    fun resetHomeLayout() {
        homeSectionOrder = DEFAULT_HOME_ORDER
        homeSectionHidden = emptySet()
        save()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val p = Properties()
            p.setProperty("themeMode", themeMode)
            p.setProperty("readerFontPx", readerFontPx.toString())
            p.setProperty("readerLineHeight", readerLineHeight.toString())
            p.setProperty("lastArticleId", lastArticleId.toString())
            p.setProperty("closeAction", closeAction)
            p.setProperty("hintsEnabled", hintsEnabled.toString())
            p.setProperty("reviewTemplateId", reviewTemplateId)
            p.setProperty("paragraphIndent", paragraphIndent.toString())
            p.setProperty("backgroundStyle", backgroundStyle)
            p.setProperty("uiScale", uiScale.toString())
            p.setProperty("recentArticleIds", recentArticleIds.joinToString(","))
            p.setProperty("language", language)
            p.setProperty("welcomeShown", welcomeShown.toString())
            p.setProperty("sentenceClozeEnabled", sentenceClozeEnabled.toString())
            p.setProperty("sentenceClozeNewEnabled", sentenceClozeNewEnabled.toString())
            p.setProperty("homeSectionOrder", homeSectionOrder.joinToString(","))
            p.setProperty("homeSectionHidden", homeSectionHidden.joinToString(","))
            file.outputStream().use { p.store(it, "Blancall Windows UI prefs") }
        }
    }

    private fun normalizeHomeOrder(order: List<String>): List<String> {
        val known = order.filter { it in DEFAULT_HOME_ORDER }.distinct()
        return known + DEFAULT_HOME_ORDER.filter { it !in known }
    }

    companion object {
        /** 首页卡片默认顺序；与 HomeScreen 的 HomeSection.key 一一对应。 */
        val DEFAULT_HOME_ORDER = listOf("plan", "daily", "due", "recent", "articles")
    }
}
