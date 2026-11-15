package com.ilyskyo.blancall.desktop.platform

import com.ilyskyo.blancall.desktop.app.Screen

/**
 * 命令行深链：`Blancall.exe --route=practice --article=12`。
 *
 * 第二实例会把同一组参数转发给已运行进程。未知路由返回 null，
 * 由调用方保持当前页面并提示，不猜用户意图。
 */
object DeepLink {

    fun parse(args: Array<String>): Screen? {
        val options = args.mapNotNull { arg ->
            val trimmed = arg.removePrefix("--")
            val idx = trimmed.indexOf('=')
            if (idx <= 0) null else trimmed.substring(0, idx) to trimmed.substring(idx + 1)
        }.toMap()
        val article = options["article"]?.toLongOrNull()?.takeIf { it > 0 }
        val config = options["config"]?.toLongOrNull()?.takeIf { it > 0 }
        val route = options["route"]?.lowercase() ?: return article?.let { Screen.Reader(it) }
        return when (route) {
            "home" -> Screen.Home
            "articles", "list" -> Screen.Articles
            "import" -> Screen.Import
            "search" -> Screen.Search
            "settings" -> Screen.Settings
            "tags", "tag-manager" -> Screen.Tags
            "library", "content-packs" -> Screen.ContentPacks
            "today", "today-plan" -> Screen.TodayPlan
            "due", "due-list" -> Screen.DueList
            "mistakes" -> Screen.Mistakes(article)
            "history", "practice-history" -> Screen.PracticeHistory(article)
            "data-interchange" -> Screen.DataInterchange
            "sentence-cards", "sentences" -> Screen.SentenceCards(articleId = article)
            "guide" -> Screen.Guide
            "help" -> Screen.Help
            "privacy" -> Screen.Privacy
            "reader", "read" -> article?.let { Screen.Reader(it) }
            "reading", "reading-mode" -> article?.let { Screen.ReadingMode(it) }
            "practice" -> article?.let { Screen.Practice(it) }
            "custom-cloze" -> article?.let {
                if (config != null) Screen.CustomClozeEdit(it, config) else Screen.CustomClozeList(it)
            }
            "mask", "mask-config" -> article?.let {
                if (config != null) Screen.MaskConfigEdit(it, config) else Screen.MaskConfigList(it)
            }
            else -> null
        }
    }
}
