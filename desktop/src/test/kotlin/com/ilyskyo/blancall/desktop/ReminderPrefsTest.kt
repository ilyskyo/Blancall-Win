package com.ilyskyo.blancall.desktop

import com.ilyskyo.blancall.desktop.app.ReminderPrefs
import com.ilyskyo.blancall.notification.ReminderFrequency
import com.ilyskyo.blancall.notification.ReminderSchedule
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalDate

class ReminderPrefsTest {

    private fun newPrefs(): Pair<ReminderPrefs, File> {
        val dir = Files.createTempDirectory("blancall-reminder-prefs").toFile()
        val settings = File(dir, "settings")
        return ReminderPrefs(File(settings, "reminder.properties"), dir) to dir
    }

    @Test
    fun `默认值与 Android 一致`() {
        val (prefs, _) = newPrefs()
        assertFalse(prefs.enabled)
        assertEquals(20, prefs.hour)
        assertEquals(0, prefs.minute)
        assertEquals(ReminderFrequency.DAILY, prefs.frequency)
        assertEquals(ReminderSchedule.ALL_DAYS, prefs.reminderDays)
        assertEquals(3, prefs.dailyPracticeGoal)
        assertTrue(prefs.quietHoursEnabled)
        assertEquals(22 * 60, prefs.quietStartMinutes)
        assertEquals(8 * 60, prefs.quietEndMinutes)
        assertEquals(0L, prefs.examEpochDay)
    }

    @Test
    fun `偏好落盘后可重新读回`() {
        val (prefs, dir) = newPrefs()
        prefs.updateEnabled(true)
        prefs.updateTime(7, 30)
        prefs.updateFrequency(ReminderFrequency.WEEKLY_THREE)
        prefs.updateQuietHoursEnabled(false)
        prefs.updateDailyPracticeGoal(5)

        val reloaded = ReminderPrefs(File(File(dir, "settings"), "reminder.properties"), dir)
        assertTrue(reloaded.enabled)
        assertEquals(7, reloaded.hour)
        assertEquals(30, reloaded.minute)
        assertEquals(ReminderFrequency.WEEKLY_THREE, reloaded.frequency)
        assertEquals(ReminderSchedule.THREE_DAYS, reloaded.reminderDays)
        assertFalse(reloaded.quietHoursEnabled)
        assertEquals(5, reloaded.dailyPracticeGoal)
    }

    @Test
    fun `自定义提醒日会切到 CUSTOM 并保留选择`() {
        val (prefs, dir) = newPrefs()
        prefs.updateReminderDays(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        assertEquals(ReminderFrequency.CUSTOM, prefs.frequency)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), prefs.reminderDays)

        val reloaded = ReminderPrefs(File(File(dir, "settings"), "reminder.properties"), dir)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), reloaded.reminderDays)
    }

    @Test
    fun `考试目标镜像到 exam_goal_json`() {
        val (prefs, dir) = newPrefs()
        prefs.updateExamEpochDay(20_000L)
        val file = File(dir, "exam_goal.json")
        assertTrue(file.isFile, "必须写出备份类目里的 exam_goal.json")
        assertEquals(20_000L, JSONObject(file.readText()).optLong("epochDay"))

        val reloaded = ReminderPrefs(File(File(dir, "settings"), "reminder.properties"), dir)
        assertEquals(20_000L, reloaded.examEpochDay)
    }

    @Test
    fun `连续学习天数按本地日历天推进`() {
        val (prefs, _) = newPrefs()
        val day1 = LocalDate.of(2026, 11, 1)
        prefs.recordStudyToday(day1)
        assertEquals(1, prefs.studyStreakDays)

        // 同一天重复记录不累加
        prefs.recordStudyToday(day1)
        assertEquals(1, prefs.studyStreakDays)

        prefs.recordStudyToday(day1.plusDays(1))
        assertEquals(2, prefs.studyStreakDays)
        assertEquals(2, prefs.longestStreakDays)

        // 断一天后重新从 1 开始，最长记录保留
        prefs.recordStudyToday(day1.plusDays(3))
        assertEquals(1, prefs.studyStreakDays)
        assertEquals(2, prefs.longestStreakDays)
        assertTrue(prefs.hasStudiedToday(day1.plusDays(3)))
        assertFalse(prefs.hasStudiedToday(day1.plusDays(4)))
    }
}
