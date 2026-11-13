package com.ilyskyo.blancall.desktop.app

sealed interface Screen {
    data object Home : Screen
    data object Articles : Screen
    data object Import : Screen
    data class Reader(val articleId: Long) : Screen
    data class Practice(val articleId: Long) : Screen
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
}
