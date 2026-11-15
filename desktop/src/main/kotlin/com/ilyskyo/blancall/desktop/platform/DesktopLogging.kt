package com.ilyskyo.blancall.desktop.platform

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.core.util.FileSize
import com.ilyskyo.blancall.platform.Log
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 桌面滚动日志：`logs/blancall.log`，10 MB × 5（ARCHITECTURE §9）。
 *
 * `shared` 的 [Log] 通过 [Log.sink] 把日志转到这里；日志目录不可写时
 * 整体降级为 stderr，不影响启动，也绝不因为日志失败阻断业务流程。
 */
object DesktopLogging {

    private const val MAX_FILE_SIZE = "10MB"
    private const val MAX_INDEX = 5

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        runCatching {
            val dir = AppPaths.logsDir
            if (!dir.isDirectory && !dir.mkdirs()) return@runCatching
            val context = LoggerFactory.getILoggerFactory() as LoggerContext

            val encoder = PatternLayoutEncoder().apply {
                this.context = context
                pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %logger{36} - %msg%n"
                start()
            }
            val appender = RollingFileAppender<ILoggingEvent>().apply {
                this.context = context
                name = "BLANCALL_FILE"
                file = File(dir, "blancall.log").absolutePath
                isAppend = true
                this.encoder = encoder
            }
            val rolling = FixedWindowRollingPolicy().apply {
                this.context = context
                setParent(appender)
                minIndex = 1
                maxIndex = MAX_INDEX
                fileNamePattern = File(dir, "blancall.%i.log").absolutePath
                start()
            }
            val trigger = SizeBasedTriggeringPolicy<ILoggingEvent>().apply {
                this.context = context
                maxFileSize = FileSize.valueOf(MAX_FILE_SIZE)
                start()
            }
            appender.rollingPolicy = rolling
            appender.triggeringPolicy = trigger
            appender.start()

            val root = context.getLogger(Logger.ROOT_LOGGER_NAME)
            root.level = Level.INFO
            // logback 默认会在首次使用时装一个控制台 appender；桌面版只保留文件日志。
            root.detachAndStopAllAppenders()
            root.addAppender(appender)

            Log.sink = { level, tag, msg, error ->
                val logger = context.getLogger(tag)
                when (level) {
                    "E" -> logger.error(msg, error)
                    "W" -> logger.warn(msg, error)
                    "I" -> logger.info(msg, error)
                    "D" -> logger.debug(msg, error)
                    else -> logger.trace(msg, error)
                }
            }
        }
    }

    /** 未捕获异常同时写入滚动日志（现有 crash-<ts>.log 由调用方保留）。 */
    fun logCrash(thread: Thread, error: Throwable) {
        runCatching {
            LoggerFactory.getLogger("BlancallCrash").error("uncaught in ${thread.name}", error)
        }
    }
}
