package com.ilyskyo.blancall.desktop.app

sealed interface Screen {
    data object Home : Screen
    data object Articles : Screen
    data object Import : Screen
    data class Reader(val articleId: Long) : Screen
    data class ReadingMode(val articleId: Long) : Screen
    data class Practice(val articleId: Long) : Screen
    data class PracticeCustom(val articleId: Long, val configId: Long) : Screen
    data class CrossPractice(val articleIds: List<Long>) : Screen
    data class Drill(val articleId: Long) : Screen
    data class Statistics(val articleId: Long? = null) : Screen
    data object StudyFlow : Screen
    data object Settings : Screen
    data object Tags : Screen
    data class TagHome(val tagId: Long) : Screen
    data class SentenceCards(val tagId: Long? = null, val articleId: Long? = null) : Screen
    data object ContentPacks : Screen
    data object Search : Screen
    data object TodayPlan : Screen
    data object DueList : Screen
    data class Mistakes(val articleId: Long? = null) : Screen
    data class PracticeHistory(val articleId: Long? = null) : Screen
    data object DataInterchange : Screen
    data class CustomClozeList(val articleId: Long) : Screen
    data class CustomClozeEdit(val articleId: Long, val configId: Long = 0L) : Screen
    data class MaskConfigList(val articleId: Long) : Screen
    data class MaskConfigEdit(val articleId: Long, val configId: Long = 0L) : Screen
    data object Guide : Screen
    data object Help : Screen
    data object Privacy : Screen
}
