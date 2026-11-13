package com.ilyskyo.blancall.notification

/**
 * 提醒频率预设（桌面/共享层版本）。
 *
 * Android 端的枚举带 `labelRes`，依赖资源系统；共享层只保留调度语义，
 * 展示文案由桌面 UI 的 i18n 资源映射。
 */
enum class ReminderFrequency {
    DAILY,
    WEEKLY_FIVE,
    WEEKLY_THREE,
    CUSTOM;

    companion object {
        fun fromNameOrDaily(name: String?): ReminderFrequency =
            runCatching { valueOf(name ?: "") }.getOrDefault(DAILY)
    }
}
