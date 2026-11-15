package com.ilyskyo.blancall.platform

/**
 * 桌面端日志门面。
 *
 * Android 源码使用 `android.util.Log`；迁移时统一替换为本对象，
 * 这样 `shared` 不依赖任何 Android 类，且测试环境不会出现 "not mocked"。
 */
object Log {
    @Volatile
    var enabled: Boolean = true

    /**
     * 桌面端日志出口（可选）。
     *
     * `shared` 不直接依赖 Logback；桌面启动时把滚动文件日志装到
     * [sink] 上，测试与未注入时仍走 stderr。
     */
    @Volatile
    var sink: ((level: String, tag: String, msg: String, error: Throwable?) -> Unit)? = null

    fun v(tag: String, msg: String): Int = printlnSafe("V", tag, msg)
    fun d(tag: String, msg: String): Int = printlnSafe("D", tag, msg)
    fun i(tag: String, msg: String): Int = printlnSafe("I", tag, msg)
    fun w(tag: String, msg: String): Int = printlnSafe("W", tag, msg)
    fun w(tag: String, msg: String, tr: Throwable?): Int = printlnSafe("W", tag, msg, tr)
    fun e(tag: String, msg: String): Int = printlnSafe("E", tag, msg)
    fun e(tag: String, msg: String, tr: Throwable?): Int = printlnSafe("E", tag, msg, tr)

    private fun printlnSafe(level: String, tag: String, msg: String, tr: Throwable? = null): Int {
        if (!enabled) return 0
        val sink = sink
        if (sink != null) {
            runCatching { sink(level, tag, msg, tr) }
            return 0
        }
        System.err.println("$level/$tag: $msg")
        tr?.printStackTrace()
        return 0
    }
}
