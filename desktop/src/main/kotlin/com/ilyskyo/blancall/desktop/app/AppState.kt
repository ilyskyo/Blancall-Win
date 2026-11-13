package com.ilyskyo.blancall.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ilyskyo.blancall.algorithm.FsrsEngine
import com.ilyskyo.blancall.algorithm.ReviewTemplate
import com.ilyskyo.blancall.algorithm.SentenceSelector
import com.ilyskyo.blancall.algorithm.StudyCoach
import com.ilyskyo.blancall.data.model.AnsweredBlank
import com.ilyskyo.blancall.data.model.MistakeDetail
import com.ilyskyo.blancall.data.model.PracticeRecord
import com.ilyskyo.blancall.data.repository.ArticleRepository
import com.ilyskyo.blancall.data.repository.AchievementTimelineStore
import com.ilyskyo.blancall.data.repository.CustomClozeStore
import com.ilyskyo.blancall.data.repository.DailySentenceCoordinator
import com.ilyskyo.blancall.data.repository.FsrsStateStore
import com.ilyskyo.blancall.data.repository.HomeLayoutStore
import com.ilyskyo.blancall.data.repository.InProgressPracticeStore
import com.ilyskyo.blancall.data.repository.MaskConfigStore
import com.ilyskyo.blancall.data.repository.ReaderPrefsStore
import com.ilyskyo.blancall.data.repository.ReadingMarkStore
import com.ilyskyo.blancall.data.repository.RecordRepository
import com.ilyskyo.blancall.data.repository.SentenceCardStore
import com.ilyskyo.blancall.data.repository.TagStore
import com.ilyskyo.blancall.data.contentpack.ContentPackStore
import com.ilyskyo.blancall.desktop.platform.AppPaths
import com.ilyskyo.blancall.desktop.platform.ReminderScheduler
import com.ilyskyo.blancall.desktop.handwriting.HandwritingRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.io.File

/**
 * 桌面应用的状态与仓储装配。
 *
 * 所有学习数据仍由 shared 的 Store 读写；UI 不直接碰 JSON。
 */
class AppState(
    val dataDir: File,
    settingsDir: File = AppPaths.settingsDir,
) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

    private fun dataFile(name: String) = File(dataDir, name)

    val articles = ArticleRepository.getInstance(dataFile("articles.json").absolutePath)
    val records = RecordRepository.getInstance(dataFile("records.json").absolutePath)
    val fsrs = FsrsStateStore.getInstance(dataFile("fsrs_state.json").absolutePath)
    val tags = TagStore.getInstance(dataDir)
    val customCloze = CustomClozeStore.getInstance(dataDir)
    val maskConfig = MaskConfigStore.getInstance(dataDir)
    val readerPrefs = ReaderPrefsStore.getInstance(dataDir)
    val sentenceCards = SentenceCardStore.getInstance(dataDir)
    val homeLayout = HomeLayoutStore.getInstance(dataDir)
    val achievements = AchievementTimelineStore.getInstance(dataDir)
    val contentPacks = ContentPackStore.getInstance(dataDir)
    val readingMarks = ReadingMarkStore.getInstance(dataDir)

    val prefs = DesktopPrefs(File(settingsDir, "ui_prefs.properties"))
    val reminderPrefs = ReminderPrefs(File(settingsDir, "reminder.properties"), dataDir)
    val reminders = ReminderScheduler(this)
    val handwriting = HandwritingRecognizer()

    /** 通知出口：主窗口创建后注入托盘气泡；未注入时退回应用内提示。 */
    @Volatile
    var notifier: ((String, String, Long?) -> Unit)? = null

    /** 应用启动时把用户选择的复习模板应用到 FSRS（默认 standard = 90%）。 */
    fun applyReviewTemplate() {
        FsrsEngine.configure(
            FsrsEngine.DEFAULT_PARAMS,
            FsrsEngine.retentionForTemplate(prefs.reviewTemplateId),
        )
    }

    fun updateReviewTemplate(templateId: String) {
        prefs.updateReviewTemplate(templateId)
        applyReviewTemplate()
    }

    fun pushNotification(title: String, body: String, articleId: Long? = null) {
        val sink = notifier
        if (sink != null) sink(title, body, articleId) else showToast("$title · $body")
    }

    /**
     * 今日计划快照：与提醒、首页共用同一份取数口径（[StudyCoach]）。
     *
     * @param now 由调用方传入，便于测试与「同一 tick 内多处显示一致」
     */
    fun studySnapshot(now: Long = System.currentTimeMillis()): StudyCoach.Snapshot =
        StudyCoach.snapshot(
            StudyCoach.Source(
                articles = articles.snapshot,
                records = records.records.value,
                states = fsrs.allStates(),
                pauses = fsrs.pausedArticles(),
                enrolled = fsrs.enrolledArticles(),
                sentenceStates = fsrs.allSentenceStates(),
                template = ReviewTemplate.fromId(prefs.reviewTemplateId),
                resumables = runCatching { InProgressPracticeStore.scan(dataDir) }
                    .getOrDefault(emptyList()),
                todaySentenceKey = runCatching {
                    sentenceCards.today()
                        ?.takeIf { it.date == DailySentenceCoordinator.dateKey(now) }?.key
                }.getOrNull(),
            ),
            now = now,
            goalMinutes = reminderPrefs.goalMinutes,
            dailyPracticeGoal = reminderPrefs.dailyPracticeGoal,
            examEpochDay = reminderPrefs.examEpochDay.takeIf { it > 0 },
        )

    /**
     * 今日一句（幂等抽取，会写 `sentence_card.json`）。
     * 与 Android 同口径：到期句优先，无到期时轮转抽新句；暂停文章不参与。
     */
    fun ensureTodaySentence(now: Long = System.currentTimeMillis()): SentenceCardStore.Snapshot? =
        runCatching {
            DailySentenceCoordinator.ensureToday(
                store = sentenceCards,
                articles = articles.snapshot,
                sentenceStates = fsrs.allSentenceStates(),
                now = now,
                excludedArticleIds = fsrs.pausedArticleIds(),
            )
        }.getOrNull()

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    /** 最近一次导航是否为返回，供页面转场判断方向。 */
    var isBackNavigation by mutableStateOf(false)
        private set
    var toast by mutableStateOf<String?>(null)
        private set
    /** 首启欢迎页（设置里可重播） */
    var showWelcome by mutableStateOf(!prefs.welcomeShown)
        private set

    fun dismissWelcome() {
        prefs.updateWelcomeShown(true)
        showWelcome = false
    }

    fun replayWelcome() {
        showWelcome = true
    }

    private val backStack = ArrayDeque<Screen>()

    fun navigate(target: Screen) {
        if (target == screen) return
        isBackNavigation = false
        backStack.addLast(screen)
        screen = target
    }

    /** 根 tab 平级切换：清空子页栈（与 Android popUpTo(home) 的口径一致）。 */
    fun goRoot(target: Screen) {
        if (target == screen) return
        isBackNavigation = false
        backStack.clear()
        screen = target
    }

    fun back() {
        isBackNavigation = true
        screen = backStack.removeLastOrNull() ?: Screen.Home
    }

    fun canGoBack(): Boolean = backStack.isNotEmpty()

    fun showToast(message: String) {
        toast = message
        scope.launch {
            delay(2600)
            if (toast == message) toast = null
        }
    }

    fun clearToast() {
        toast = null
    }

    suspend fun reloadAll() {
        articles.reload()
        records.reload()
        fsrs.reload()
        reminderPrefs.reloadExamGoal()
        runCatching { tags.snapshot() }
    }

    /**
     * 保存一次练习：先写记录，再按相似度推进 FSRS。
     * 与 Android 的口径一致：评级由 [FsrsEngine.gradeFromSimilarity] 决定。
     */
    suspend fun savePractice(
        articleId: Long,
        mode: String,
        totalBlanks: Int,
        correctCount: Int,
        similarity: Float,
        mistakes: List<MistakeDetail>,
        answeredBlanks: List<AnsweredBlank>,
        weakHints: Int = 0,
        strongHints: Int = 0,
    ): FsrsEngine.Rating {
        val rating = FsrsEngine.gradeFromSimilarity(similarity)
        records.insert(
            PracticeRecord(
                articleId = articleId,
                mode = mode,
                totalBlanks = totalBlanks,
                correctCount = correctCount,
                mistakes = mistakes,
                similarity = similarity,
                rating = rating.value,
                answeredBlanks = answeredBlanks,
                weakHints = weakHints,
                strongHints = strongHints,
            )
        )
        fsrs.update(articleId) { current -> FsrsEngine.review(current, rating) }
        // 句子级状态：与 Android 的 s:<articleId>:<hash16> 同键同语义。
        // 同一场练习里同一句可能命中多个空，按平均相似度折算一次评级。
        val bySentence = answeredBlanks
            .filter { it.sentenceIndex >= 0 && it.sentenceText.isNotBlank() && it.similarity >= 0f }
            .groupBy { it.sentenceIndex }
        if (bySentence.isNotEmpty()) {
            val transforms = bySentence.mapNotNull { (_, blanks) ->
                val text = blanks.first().sentenceText
                if (text.isBlank()) return@mapNotNull null
                val key = SentenceSelector.sentenceKey(articleId, text)
                val sentenceRating = FsrsEngine.gradeFromSimilarity(
                    blanks.map { it.similarity }.average().toFloat()
                )
                key to { state: FsrsEngine.CardState -> FsrsEngine.review(state, sentenceRating) }
            }.toMap()
            if (transforms.isNotEmpty()) fsrs.updateSentences(transforms)
        }
        // 连续天数 / 今日已学：与 Android ReminderPrefs.recordStudyToday 同口径
        reminderPrefs.recordStudyToday()
        return rating
    }

    /**
     * 保存一次跨文混合练习：记录 articleId = -1，逐空带来源文章；
     * FSRS 按来源文章分别推进（与 Android `PracticeRecord.sourceArticleIds` 口径一致）。
     */
    suspend fun saveCrossPractice(
        sourceArticleIds: List<Long>,
        totalBlanks: Int,
        correctCount: Int,
        similarity: Float,
        mistakes: List<MistakeDetail>,
        answeredBlanks: List<AnsweredBlank>,
    ): FsrsEngine.Rating {
        val rating = FsrsEngine.gradeFromSimilarity(similarity)
        records.insert(
            PracticeRecord(
                articleId = -1L,
                sourceArticleIds = sourceArticleIds,
                mode = "SENTENCE",
                totalBlanks = totalBlanks,
                correctCount = correctCount,
                mistakes = mistakes,
                similarity = similarity,
                rating = rating.value,
                answeredBlanks = answeredBlanks,
            )
        )
        answeredBlanks
            .filter { it.articleId > 0 && it.similarity >= 0f }
            .groupBy { it.articleId }
            .forEach { (articleId, blanks) ->
                val articleRating = FsrsEngine.gradeFromSimilarity(
                    blanks.map { it.similarity }.average().toFloat()
                )
                fsrs.update(articleId) { current -> FsrsEngine.review(current, articleRating) }
                val bySentence = blanks
                    .filter { it.sentenceText.isNotBlank() }
                    .groupBy { it.sentenceIndex }
                if (bySentence.isNotEmpty()) {
                    val transforms = bySentence.mapNotNull { (_, group) ->
                        val text = group.first().sentenceText
                        if (text.isBlank()) return@mapNotNull null
                        val key = SentenceSelector.sentenceKey(articleId, text)
                        val sentenceRating = FsrsEngine.gradeFromSimilarity(
                            group.map { it.similarity }.average().toFloat()
                        )
                        key to { state: FsrsEngine.CardState -> FsrsEngine.review(state, sentenceRating) }
                    }.toMap()
                    if (transforms.isNotEmpty()) fsrs.updateSentences(transforms)
                }
            }
        reminderPrefs.recordStudyToday()
        return rating
    }
}
