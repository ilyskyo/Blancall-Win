package com.ilyskyo.blancall.desktop.platform

import com.ilyskyo.blancall.algorithm.DayBucket
import com.ilyskyo.blancall.algorithm.FsrsEngine
import com.ilyskyo.blancall.desktop.app.AppState
import com.ilyskyo.blancall.desktop.app.Strings
import com.ilyskyo.blancall.notification.ReminderPlan
import com.ilyskyo.blancall.notification.ReminderPlanItem
import com.ilyskyo.blancall.notification.ReminderReason
import com.ilyskyo.blancall.notification.ReminderSchedule
import com.ilyskyo.blancall.notification.ReminderStateStore
import com.ilyskyo.blancall.notification.ReminderForgettingRisk
import com.ilyskyo.blancall.notification.ReminderLastPractice
import com.ilyskyo.blancall.notification.ReminderUpcomingTask
import com.ilyskyo.blancall.platform.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 桌面提醒调度器（对应 Android 的 ReminderWorker）。
 *
 * 判定规则完全复用 `shared/notification` 的 [ReminderPlan]、[ReminderSchedule]、
 * [ReminderStateStore]；本类只负责「取数 + 按点触发 + 交给托盘气泡」。
 *
 * 与 Android 的差异：
 * - 应用未运行就不会提醒（Android 靠 WorkManager 唤醒）；关闭到托盘可保持提醒。
 * - 没有系统通知渠道，用托盘气泡 + 应用内提示。
 * - 每 30 秒对齐一次时钟，睡眠唤醒后仍能在目标时刻附近触发。
 */
class ReminderScheduler(private val state: AppState) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** 下一次提醒时间（设置页展示用；未开启时为 null）。 */
    @Volatile
    var nextFireAt: LocalDateTime? = null
        private set

    /** 已经排定、尚未触发的时刻；用于睡眠唤醒后的补发判定。 */
    @Volatile
    private var pendingFor: LocalDateTime? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { loop() }
    }

    fun restart() {
        pendingFor = null
        job?.cancel()
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        pendingFor = null
        nextFireAt = null
    }

    /** 设置页「发送测试提醒」：不写去重表，也不改下一次排期。 */
    fun sendTest() {
        state.pushNotification(
            title = "Blancall 测试提醒",
            body = "提醒已就绪。到设定时间后会在这里弹出今日复习提示。",
        )
    }

    private suspend fun loop() {
        while (currentCoroutineContext().isActive) {
            val prefs = state.reminderPrefs
            if (!prefs.enabled) {
                pendingFor = null
                nextFireAt = null
                delay(POLL_MS)
                continue
            }
            val zone = ZoneId.systemDefault()
            val now = LocalDateTime.now(zone)
            val pending = pendingFor
            if (pending != null && !now.isBefore(pending)) {
                pendingFor = null
                // 超过补发窗口（睡眠一整天/改过系统时间）就直接跳过，不在错误的时间打扰
                if (Duration.between(pending, now) <= CATCH_UP_WINDOW) {
                    runCatching { fire() }.onFailure { Log.e(TAG, "发送提醒失败", it) }
                }
                continue
            }
            val next = pending
                ?: ReminderSchedule.nextAt(now, prefs.hour, prefs.minute, prefs.reminderDays)
                    .also { pendingFor = it }
            nextFireAt = next
            val waitMs = Duration.between(now, next).toMillis()
            delay(waitMs.coerceIn(500L, POLL_MS))
        }
    }

    private fun fire() {
        val prefs = state.reminderPrefs
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val local = Instant.ofEpochMilli(now).atZone(zone)
        if (!ReminderSchedule.isReminderDay(local.toLocalDate(), prefs.reminderDays)) return
        if (prefs.quietHoursEnabled && ReminderPlan.isQuietMinute(
                minuteOfDay = local.hour * 60 + local.minute,
                startMinute = prefs.quietStartMinutes,
                endMinute = prefs.quietEndMinutes,
            )
        ) {
            return
        }

        val plans = buildPlans(now)
        val store = ReminderStateStore.getInstance(state.dataDir)
        var sent = 0
        for (item in plans.take(MAX_PER_RUN)) {
            if (!store.shouldSend(item.reason, now)) continue
            val notification = render(item)
            state.pushNotification(notification.title, notification.body, item.articleId)
            store.markSent(item.reason, now)
            sent++
        }
        if (sent > 0) Log.d(TAG, "已发送 $sent 条提醒")
    }

    /** 采集判定输入并交给纯函数拍板；与 Android ReminderWorker.buildPlans 同口径。 */
    private fun buildPlans(now: Long): List<ReminderPlanItem> {
        val prefs = state.reminderPrefs
        val states = state.fsrs.allStates()
        val pausedIds = state.fsrs.pausedArticleIds()

        val upcomingTasks = states
            .filterKeys { it !in pausedIds }
            .filterValues { s ->
                s.reviewCount > 0 && s.due > now && s.due <= now + DUE_SOON_WINDOW_MS
            }
            .entries
            .sortedBy { it.value.due }
            .take(MAX_PREDICTED_TASKS)
            .map { (id, s) -> ReminderUpcomingTask(id, articleTitle(id), s.due) }

        val dangerRetention = (FsrsEngine.targetRetention() + FORGETTING_MARGIN)
            .coerceAtMost(MAX_FORGETTING_RETENTION)
        val forgettingRisks = states
            .filterKeys { it !in pausedIds }
            .filterValues { s ->
                s.reviewCount > 0 && !FsrsEngine.isDue(s, now) &&
                    FsrsEngine.retentionRate(s, now) < dangerRetention
            }
            .entries
            .sortedBy { FsrsEngine.retentionRate(it.value, now) }
            .take(MAX_PREDICTED_TASKS)
            .map { (id, s) ->
                ReminderForgettingRisk(
                    articleId = id,
                    title = articleTitle(id),
                    retentionPercent = (FsrsEngine.retentionRate(s, now) * 100)
                        .roundToInt().coerceIn(0, 100),
                )
            }

        val sentenceStates = state.fsrs.allSentenceStates()
        val today = DayBucket.localDate(now)
        val sentenceCardPracticedToday = sentenceStates.values.any { s ->
            s.reviewCount > 0 && DayBucket.localDate(s.lastReview) == today
        }

        // 与首页同一份取数口径（到期文章、未完成练习、到期句、次数目标）
        val snapshot = state.studySnapshot(now)
        val records = state.records.records.value
        val dueReview = snapshot.input.dueArticles.filter { it.hasPractice }
        val dueNew = snapshot.input.dueArticles.filter { !it.hasPractice }
        val firstDue = dueReview.minByOrNull { it.due }
        val firstNew = dueNew.minByOrNull { it.due }
        val incomplete = snapshot.resumables.firstOrNull()
        val todayCount = records.count { DayBucket.localDate(it.timestamp) == today }
        val weakCount = countWeakContent(records, pausedIds)
        val lastPractice = records
            .filter { it.totalBlanks > 0 }
            .maxByOrNull { it.timestamp }
            ?.let { record ->
                ReminderLastPractice(
                    articleId = record.articleId,
                    title = articleTitle(record.articleId),
                    accuracyPercent = (record.correctCount.toFloat() / record.totalBlanks * 100)
                        .roundToInt().coerceIn(0, 100),
                    practicedAt = record.timestamp,
                )
            }

        return ReminderPlan.decide(
            now = now,
            hasStudiedToday = prefs.hasStudiedToday(),
            todayCount = todayCount,
            dailyGoal = prefs.dailyPracticeGoal,
            dueArticleCount = dueReview.size,
            dueNewArticleCount = dueNew.size,
            dueSentenceCount = snapshot.sentenceTotalDue,
            firstDueArticleId = firstDue?.articleId ?: -1L,
            firstNewArticleId = firstNew?.articleId ?: -1L,
            sentenceCardPracticedToday = sentenceCardPracticedToday,
            hasIncompletePractice = incomplete != null,
            incompleteArticleId = incomplete?.articleId ?: -1L,
            incompleteRemaining = incomplete?.remaining ?: 0,
            weakCount = weakCount,
            streakDays = prefs.studyStreakDays,
            dueArticleTitle = firstDue?.title.orEmpty(),
            firstNewArticleTitle = firstNew?.title.orEmpty(),
            incompleteArticleTitle = incomplete?.let { articleTitle(it.articleId) }.orEmpty(),
            upcomingTasks = upcomingTasks,
            forgettingRisks = forgettingRisks,
            lastPractice = lastPractice,
        )
    }

    private fun articleTitle(articleId: Long): String {
        if (articleId <= 0L) return ""
        return state.articles.snapshot.firstOrNull { it.id == articleId }?.title.orEmpty()
    }

    private fun countWeakContent(
        records: List<com.ilyskyo.blancall.data.model.PracticeRecord>,
        pausedArticleIds: Set<Long>,
    ): Int {
        if (records.isEmpty()) return 0
        val keys = mutableSetOf<String>()
        for (record in records) {
            if (record.articleId in pausedArticleIds) continue
            for (m in record.mistakes) keys.add("${record.articleId}_${m.blankIndex}")
        }
        return keys.size
    }

    private data class Notification(val title: String, val body: String)

    private fun render(item: ReminderPlanItem): Notification {
        val title = item.articleTitle.ifBlank { Strings.t("未命名文章") }
        return when (item.reason) {
            ReminderReason.IncompletePractice -> Notification(
                Strings.t("继续上次练习"),
                Strings.f("《{0}》还有 {1} 个空没填完，接着练完吧。", title, item.count),
            )
            ReminderReason.DueReview -> Notification(
                Strings.t("该复习了"),
                if (item.articleTitle.isNotBlank()) {
                    Strings.f("今天有 {0} 项到期，先从《{1}》开始。", item.count, title)
                } else {
                    Strings.f("今天有 {0} 项到期内容等待复习。", item.count)
                },
            )
            ReminderReason.StartArticle -> Notification(
                Strings.t("开始第一遍练习"),
                Strings.f("计划中的《{0}》还没开始，今天练一篇吧。", title),
            )
            ReminderReason.PracticeAccuracy -> Notification(
                Strings.t("再复习一下"),
                Strings.f("《{0}》最近一次正确率 {1}%，趁热回看错题。", title, item.accuracyPercent),
            )
            ReminderReason.ForgettingSoon -> Notification(
                Strings.t("快忘了"),
                Strings.f("《{0}》当前留存率约 {1}%，再拖就要重新记了。", title, item.retentionPercent),
            )
            ReminderReason.TaskDueSoon -> Notification(
                Strings.t("即将到期"),
                Strings.f("《{0}》将在 {1} 到期，可以提前复习。", title, formatDue(item.dueAt)),
            )
            ReminderReason.DailyGoalMissed -> Notification(
                Strings.t("今日目标"),
                Strings.f("还差 {0} 次练习达成今天的目标。", item.count),
            )
            ReminderReason.NoStudyToday -> Notification(
                Strings.t("今天还没练习"),
                if (item.count > 0) {
                    Strings.f("已经连续 {0} 天，今天也别断。", item.count)
                } else {
                    Strings.t("花十分钟背一段，胜过收藏十篇。")
                },
            )
            ReminderReason.WeakContent -> Notification(
                Strings.t("巩固薄弱点"),
                Strings.f("有 {0} 处错题值得再过一遍。", item.count),
            )
            ReminderReason.SentenceCardDue -> Notification(
                Strings.t("今日一句"),
                Strings.t("今天的句子卡还没看，打开首页就能复习。"),
            )
            ReminderReason.StreakContinue -> Notification(
                Strings.f("连续学习 {0} 天", item.count),
                Strings.t("保持节奏，来练一篇巩固记忆。"),
            )
        }
    }

    private fun formatDue(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
            .format(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(Locale.forLanguageTag(Strings.locale)),
            )

    companion object {
        private const val TAG = "ReminderScheduler"
        private const val MAX_PER_RUN = 2
        private const val MAX_PREDICTED_TASKS = 20
        private const val FORGETTING_MARGIN = 0.03
        private const val MAX_FORGETTING_RETENTION = 0.98
        private const val DUE_SOON_WINDOW_MS = 72L * 60 * 60 * 1000
        private const val POLL_MS = 30_000L
        private val CATCH_UP_WINDOW: Duration = Duration.ofHours(4)
    }
}
