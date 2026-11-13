package com.ilyskyo.blancall.desktop.handwriting

import com.ilyskyo.blancall.data.handwriting.HandwritingCharset
import com.ilyskyo.blancall.handwriting.HandwritingPreprocess
import com.ilyskyo.blancall.platform.Log
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import java.io.File

/**
 * NCNN C API（ncnn.dll）的 JNA 绑定。只声明本项目用到的函数，避免把整份头文件搬进 JVM。
 */
private interface NcnnApi : Library {
    fun ncnn_net_create(): Pointer
    fun ncnn_net_destroy(net: Pointer)
    fun ncnn_net_get_option(net: Pointer): Pointer
    fun ncnn_net_set_option(net: Pointer, opt: Pointer)
    fun ncnn_option_set_num_threads(opt: Pointer, numThreads: Int)
    fun ncnn_option_set_use_fp16_packed(opt: Pointer, enable: Int)
    fun ncnn_option_set_use_fp16_storage(opt: Pointer, enable: Int)
    fun ncnn_option_set_use_fp16_arithmetic(opt: Pointer, enable: Int)
    fun ncnn_net_load_param_w(net: Pointer, path: WString): Int
    fun ncnn_net_load_model_w(net: Pointer, path: WString): Int
    fun ncnn_extractor_create(net: Pointer): Pointer
    fun ncnn_extractor_destroy(ex: Pointer)
    fun ncnn_extractor_input(ex: Pointer, name: String, mat: Pointer): Int
    fun ncnn_extractor_extract(ex: Pointer, name: String, mat: PointerByReference): Int
    fun ncnn_mat_create_3d(w: Int, h: Int, c: Int, allocator: Pointer?): Pointer
    fun ncnn_mat_destroy(mat: Pointer)
    fun ncnn_mat_get_data(mat: Pointer): Pointer
    fun ncnn_mat_get_w(mat: Pointer): Int
}

/** 手写文种（与 Android 同名枚举保持同一套模型/字符表划分）。 */
enum class HandwritingScript {
    Chinese,
    Latin;

    companion object {
        fun forAnswer(answer: String): HandwritingScript =
            if (answer.isEmpty() || answer.any { it.code >= 0x2E80 }) Chinese else Latin
    }
}

/**
 * 桌面单字手写识别（NCNN CPU 推理）。
 *
 * 模型与 Android 同源（HWDB 7356 类 + EMNIST 47 类），预处理逐行移植自 `preprocess.c`。
 * 模型与 `ncnn.dll` 放在应用目录 `models/` 下；缺失时 [available] 为 false，练习页回退键盘。
 */
class HandwritingRecognizer {

    data class Candidate(val index: Int, val char: Char, val confidence: Float)

    private class LoadedNet(val net: Pointer, val chars: List<Char>)

    private val nets = HashMap<HandwritingScript, LoadedNet>()
    private val loadLock = Any()
    private val inferLock = Any()

    private val modelsRoot: File? by lazy { resolveModelsRoot() }

    private val api: NcnnApi? by lazy {
        val dll = modelsRoot?.let { File(it, "ncnn.dll") } ?: return@lazy null
        if (!dll.isFile) return@lazy null
        runCatching {
            Native.load(dll.absolutePath, NcnnApi::class.java) as NcnnApi
        }.onFailure { Log.e(TAG, "load ncnn.dll failed", it) }.getOrNull()
    }

    val available: Boolean get() = api != null && modelsRoot != null

    /** 中文模型是否随包存在（设置页/练习页用来决定是否显示手写入口）。 */
    val hasChineseModel: Boolean
        get() = modelsRoot?.let { File(it, "hccr/model.ncnn.param").isFile } == true

    fun isReady(script: HandwritingScript): Boolean = synchronized(loadLock) {
        nets[script]?.chars?.isNotEmpty() == true
    }

    fun ensureLoaded(script: HandwritingScript): Boolean {
        if (isReady(script)) return true
        synchronized(loadLock) {
            if (isReady(script)) return true
            val lib = api ?: return false
            val root = modelsRoot ?: return false
            val dirName = if (script == HandwritingScript.Latin) "hccr_en" else "hccr"
            val paramName = if (script == HandwritingScript.Latin) "model_en.param" else "model.ncnn.param"
            val binName = if (script == HandwritingScript.Latin) "model_en.bin" else "model.ncnn.bin"
            val charsetName = if (script == HandwritingScript.Latin) "charset_en.json" else "charset.json"
            val dir = File(root, dirName)
            val param = File(dir, paramName)
            val bin = File(dir, binName)
            val charset = File(dir, charsetName)
            if (!param.isFile || !bin.isFile || !charset.isFile) return false
            return runCatching {
                val expected = if (script == HandwritingScript.Latin) HandwritingCharset.SIZE_LATIN
                else HandwritingCharset.SIZE
                val chars = HandwritingCharset.parse(charset.readText(Charsets.UTF_8), expected)
                    ?: return false
                val net = lib.ncnn_net_create() ?: return false
                val opt = lib.ncnn_net_get_option(net)
                lib.ncnn_option_set_num_threads(opt, 2)
                lib.ncnn_option_set_use_fp16_packed(opt, 1)
                lib.ncnn_option_set_use_fp16_storage(opt, 1)
                lib.ncnn_option_set_use_fp16_arithmetic(opt, 1)
                lib.ncnn_net_set_option(net, opt)
                val retParam = lib.ncnn_net_load_param_w(net, WString(param.absolutePath))
                val retModel = lib.ncnn_net_load_model_w(net, WString(bin.absolutePath))
                if (retParam != 0 || retModel != 0) {
                    Log.e(TAG, "load model failed param=$retParam model=$retModel ($param / $bin)")
                    lib.ncnn_net_destroy(net)
                    return false
                }
                nets[script] = LoadedNet(net, chars)
                Log.i(TAG, "handwriting ready ($script): ${chars.size} classes")
                true
            }.onFailure { Log.e(TAG, "ensureLoaded failed ($script)", it) }.getOrDefault(false)
        }
    }

    /**
     * 识别一张白底黑字灰度图。
     *
     * @return top-K 候选（按分数降序）；未加载/无前景/推理失败返回空列表。
     */
    fun recognize(
        gray: ByteArray,
        width: Int,
        height: Int,
        script: HandwritingScript,
        topK: Int = 5,
    ): List<Candidate> {
        if (!ensureLoaded(script)) return emptyList()
        val lib = api ?: return emptyList()
        val loaded = synchronized(loadLock) { nets[script] } ?: return emptyList()
        val input = if (script == HandwritingScript.Latin) {
            HandwritingPreprocess.latin(gray, width, height)
        } else {
            HandwritingPreprocess.chinese(gray, width, height)
        } ?: return emptyList()
        val size = if (script == HandwritingScript.Latin) HandwritingPreprocess.LATIN_CANVAS_SIZE
        else HandwritingPreprocess.CANVAS_SIZE

        return synchronized(inferLock) {
            var mat: Pointer? = null
            var ex: Pointer? = null
            var out: Pointer? = null
            try {
                mat = lib.ncnn_mat_create_3d(size, size, 1, null) ?: return emptyList()
                val data = lib.ncnn_mat_get_data(mat) ?: return emptyList()
                data.write(0, input, 0, input.size)
                ex = lib.ncnn_extractor_create(loaded.net) ?: return emptyList()
                if (lib.ncnn_extractor_input(ex, "in0", mat) != 0) return emptyList()
                val outRef = PointerByReference()
                if (lib.ncnn_extractor_extract(ex, "out0", outRef) != 0) return emptyList()
                out = outRef.value ?: return emptyList()
                val n = lib.ncnn_mat_get_w(out)
                if (n <= 0) return emptyList()
                val outData = lib.ncnn_mat_get_data(out) ?: return emptyList()
                val scores = outData.getFloatArray(0, n)
                val k = topK.coerceIn(1, minOf(n, loaded.chars.size))
                val indices = (0 until n).sortedByDescending { scores[it] }.take(k)
                val looksLikeProbability = (scores.maxOrNull() ?: 0f) <= 1.0f &&
                    scores.sumOf { it.toDouble() } in 0.5..1.5
                val values = if (looksLikeProbability) {
                    indices.map { scores[it] }
                } else {
                    val maxScore = scores[indices.first()]
                    val exps = indices.map { kotlin.math.exp((scores[it] - maxScore).toDouble()) }
                    val total = exps.sum().takeIf { it > 0.0 } ?: 1.0
                    exps.map { (it / total).toFloat() }
                }
                indices.mapIndexedNotNull { i, index ->
                    val ch = loaded.chars.getOrNull(index) ?: return@mapIndexedNotNull null
                    Candidate(index, ch, values[i])
                }
            } catch (t: Throwable) {
                Log.e(TAG, "recognize failed", t)
                emptyList()
            } finally {
                if (out != null) runCatching { lib.ncnn_mat_destroy(out) }
                if (ex != null) runCatching { lib.ncnn_extractor_destroy(ex) }
                if (mat != null) runCatching { lib.ncnn_mat_destroy(mat) }
            }
        }
    }

    fun release() = synchronized(loadLock) {
        val lib = api ?: return
        nets.values.forEach { runCatching { lib.ncnn_net_destroy(it.net) } }
        nets.clear()
    }

    private fun resolveModelsRoot(): File? {
        val jarDir = runCatching {
            File(HandwritingRecognizer::class.java.protectionDomain.codeSource.location.toURI())
                .let { if (it.isDirectory) it else it.parentFile }
        }.getOrNull()
        val candidates = buildList {
            System.getProperty("blancall.models.dir")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            add(File(System.getProperty("user.dir"), "models"))
            add(File(com.ilyskyo.blancall.desktop.platform.AppPaths.rootDir, "models"))
            // 便携版：jar 在 <安装目录>/app/，models 与 app 同级
            jarDir?.parentFile?.let { add(File(it, "models")) }
            jarDir?.let { add(File(it, "models")) }
            System.getProperty("compose.application.resources.dir")?.takeIf { it.isNotBlank() }?.let {
                add(File(it, "models"))
                add(File(File(it).parentFile, "models"))
            }
        }
        return candidates.firstOrNull { File(it, "hccr/model.ncnn.param").isFile }
            ?: candidates.firstOrNull { File(it, "ncnn.dll").isFile }
    }

    companion object {
        private const val TAG = "BlancallHCCR-Win"
    }
}
