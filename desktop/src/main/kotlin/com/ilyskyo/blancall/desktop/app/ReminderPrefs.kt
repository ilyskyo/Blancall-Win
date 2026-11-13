package com.ilyskyo.blancall.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ilyskyo.blancall.notification.ReminderFrequency
import com.ilyskyo.blancall.notification.ReminderSchedule
import com.ilyskyo.blancall.util.AtomicFiles
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Properties

/**
 * 学习提醒的桌面偏好（对应 Android 的 SharedPreferences `reminder_prefs`）。
 *
 * 桌面端用 `settings/reminder.properties` 保存，不写进 `data/`：
 * Android 的提醒偏好同样不在「一键备份」范围内，唯一例外是考试倒排目标
 * （`exam_goal.json`，在备份的 prefs 类目里）—— 这里也照同一口径镜像一份。
 */
class ReminderPrefs(
    private val file: File,
    private val dataDir: File,
) {

    var enabled by mutableStateOf(false)
        private set
    var hour by mutableStateOf(20)
        private set
    var minute by mutableStateOf(0)
        private set
    var goalMinutes by mutableStateOf(10)
        private set
    var frequency by mutableStateOf(ReminderFrequency.DAILY)
        private set
    var reminderDays by mutableStateOf(ReminderSchedule.ALL_DAYS)
        private set
    var studyStreakDays by mutableStateOf(0)
        private set
    var longestStreakDays by mutableStateOf(0)
        private set
    var dailyPracticeGoal by mutableStateOf(3)
        private set
    var showDailyGoalOnCard by mutableStateOf(true)
        private set
    var quietHoursEnabled by mutableStateOf(true)
        private set
    var quietStartMinutes by mutableStateOf(22 * 60)
        private set
    var quietEndMinutes by mutableStateOf(8 * 60)
        private set
    /** 考试日期（epochDay；0 = 未设置）。权威文件是 `data/exam_goal.json`。 */
    var examEpochDay by mutableStateOf(0L)
        private set
    var lastStudyDate by mutableStateOf("")
        private set

    private val streakLock = Any()

    init {
        runCatching {
            if (file.isFile) {
                val p = Properties()
                file.inputStream().use { p.load(it) }
                enabled = p.getProperty("enabled", "false").toBoolean()
                hour = p.getProperty("hour", "20").toIntOrNull()?.coerceIn(0, 23) ?: 20
                minute = p.getProperty("minute", "0").toIntOrNull()?.coerceIn(0, 59) ?: 0
                goalMinutes = p.getProperty("goalMinutes", "10").toIntOrNull()?.coerceIn(1, 1440) ?: 10
                frequency = ReminderFrequency.fromNameOrDaily(p.getProperty("frequency"))
                val days = ReminderSchedule.parseDays(p.getProperty("days"))
                reminderDays = days.ifEmpty { ReminderSchedule.daysFor(frequency) }
                studyStreakDays = p.getProperty("studyStreakDays", "0").toIntOrNull() ?: 0
                longestStreakDays = p.getProperty("longestStreakDays", "0").toIntOrNull() ?: 0
                dailyPracticeGoal = p.getProperty("dailyPracticeGoal", "3").toIntOrNull() ?: 3
                showDailyGoalOnCard = p.getProperty("showDailyGoalOnCard", "true").toBoolean()
                quietHoursEnabled = p.getProperty("quietHoursEnabled", "true").toBoolean()
                quietStartMinutes = p.getProperty("quietStartMinutes", "1320")
                    .toIntOrNull()?.coerceIn(0, 1439) ?: 1320
                quietEndMinutes = p.getProperty("quietEndMinutes", "480")
                    .toIntOrNull()?.coerceIn(0, 1439) ?: 480
                lastStudyDate = p.getProperty("lastStudyDate", "")
            }
            // 考试目标以 data/exam_goal.json 为准（与 Android 同一份文件、同一备份类目）
            examEpochDay = readExamGoalFromFile() ?: 0L
        }
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        save()
    }

    fun updateTime(h: Int, m: Int) {
        hour = h.coerceIn(0, 23)
        minute = m.coerceIn(0, 59)
        save()
    }

    fun updateGoalMinutes(value: Int) {
        goalMinutes = value.coerceIn(1, 1440)
        save()
    }

    fun updateFrequency(value: ReminderFrequency) {
        frequency = value
        if (value != ReminderFrequency.CUSTOM) {
            reminderDays = ReminderSchedule.daysFor(value)
        }
        save()
    }

    fun updateReminderDays(days: Set<DayOfWeek>) {
        reminderDays = days.ifEmpty { ReminderSchedule.daysFor(frequency) }
        frequency = ReminderFrequency.CUSTOM
        save()
    }

    fun updateDailyPracticeGoal(value: Int) {
        dailyPracticeGoal = value.coerceAtLeast(1)
        save()
    }

    fun updateShowDailyGoalOnCard(value: Boolean) {
        showDailyGoalOnCard = value
        save()
    }

    fun updateQuietHoursEnabled(value: Boolean) {
        quietHoursEnabled = value
        save()
    }

    fun updateQuietStart(minutes: Int) {
        quietStartMinutes = minutes.coerceIn(0, 1439)
        save()
    }

    fun updateQuietEnd(minutes: Int) {
        quietEndMinutes = minutes.coerceIn(0, 1439)
        save()
    }

    fun updateExamEpochDay(value: Long) {
        examEpochDay = value.coerceAtLeast(0L)
        writeExamGoalToFile(examEpochDay)
        save()
    }

    /** 同一天只记一次；昨天练过则连续天数 +1，否则从 1 重新开始。 */
    fun recordStudyToday(today: LocalDate = LocalDate.now()) {
        synchronized(streakLock) {
            val key = today.toString()
            if (lastStudyDate == key) return
            studyStreakDays = if (lastStudyDate == today.minusDays(1).toString()) studyStreakDays + 1 else 1
            if (studyStreakDays > longestStreakDays) longestStreakDays = studyStreakDays
            lastStudyDate = key
            save()
        }
    }

    fun hasStudiedToday(today: LocalDate = LocalDate.now()): Boolean = lastStudyDate == today.toString()

    /** 备份导入后重读考试目标（备份里可能带来新的 exam_goal.json）。 */
    fun reloadExamGoal() {
        examEpochDay = readExamGoalFromFile() ?: 0L
    }

    private fun examGoalFile(): File = File(dataDir, "exam_goal.json")

    private fun readExamGoalFromFile(): Long? = runCatching {
        val f = examGoalFile()
        if (!f.isFile) null
        else JSONObject(f.readText()).optLong("epochDay", 0L).coerceAtLeast(0L)
    }.getOrNull()

    private fun writeExamGoalToFile(epochDay: Long) {
        runCatching {
            AtomicFiles.writeTextAtomic(
                examGoalFile(),
                JSONObject().put("version", 1).put("epochDay", epochDay).toString(),
            )
        }
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val p = Properties()
            p.setProperty("enabled", enabled.toString())
            p.setProperty("hour", hour.toString())
            p.setProperty("minute", minute.toString())
            p.setProperty("goalMinutes", goalMinutes.toString())
            p.setProperty("frequency", frequency.name)
            p.setProperty("days", ReminderSchedule.encodeDays(reminderDays))
            p.setProperty("studyStreakDays", studyStreakDays.toString())
            p.setProperty("longestStreakDays", longestStreakDays.toString())
            p.setProperty("dailyPracticeGoal", dailyPracticeGoal.toString())
            p.setProperty("showDailyGoalOnCard", showDailyGoalOnCard.toString())
            p.setProperty("quietHoursEnabled", quietHoursEnabled.toString())
            p.setProperty("quietStartMinutes", quietStartMinutes.toString())
            p.setProperty("quietEndMinutes", quietEndMinutes.toString())
            p.setProperty("lastStudyDate", lastStudyDate)
            file.outputStream().use { p.store(it, "Blancall Windows reminder prefs") }
        }
    }
}
