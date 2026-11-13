# Blancall Windows 依赖审计（Phase 0）

- 参考实现：`D:\Blancall\Blancall-Std`（NoAI），版本 **7.6.1-NoAI / versionCode 54**（`app/build.gradle.kts`）
- 对照实现：`D:\Blancall\Blancall Pro`（仅用于核对共享数据格式与备份结构；Windows 不实现 AI）
- 产品决定：**Windows 只做无 AI 版**，与 `Blancall-Std` 行为对齐；无任何网络请求、无 INTERNET 权限
- 审计方式：只读静态审计；参考工程未做任何修改
- 审计日期：2026-11-11

## 0. 判定口径

| 级别 | 定义 | 迁移动作 |
|---|---|---|
| **A** | 只依赖 `java.*` / `kotlin.*` / `kotlinx.coroutines` / `org.json` / 本仓纯数据模型 | 原样复制，最多改包名/import |
| **A-** | 除一个 Compose 稳定性注解外无平台依赖 | 保留注解（shared 引入 Compose runtime）或去注解 |
| **B** | 依赖 1~3 个平台值类型/工具 API（`Log`、`Context`、`SharedPreferences`、`Uri`、`Offset`、`Color` 等） | 换 1~3 个调用点后进 shared |
| **C** | 逻辑主体由平台渲染/文档/视图/系统服务实现（Compose UI、`Canvas`、`PdfDocument`、`PdfRenderer`、Glance、WorkManager、Keystore） | 重写；可保留纯逻辑与常量 |

行数为 `Get-Content | Measure-Object -Line` 的实测值，与编辑器显示可能相差 1 行。

## 1. 汇总

### 1.1 按包统计（实测 250 个 `.kt`，82,564 行）

| 包 | 文件 | 行数 | A | A- | B | C |
|---|---:|---:|---:|---:|---:|---:|
| `algorithm/` | 58 | 9,575 | 50 | 0 | 4 | 4 |
| `data/`（backup/contentpack/database/handwriting/model/repository） | 29 | 6,332 | 14 | 1 | 13 | 1 |
| `notification/` | 8 | 1,792 | 2 | 0 | 1 | 5 |
| `util/` | 2 | 1,041 | 1 | 0 | 1 | 0 |
| `widget/` | 3 | 325 | 0 | 0 | 0 | 3 |
| `ui/` | 148 | 63,054 | 0 | 0 | 14 | 134 |
| 根（`BlancallApp.kt`、`MainActivity.kt`） | 2 | 445 | 0 | 0 | 0 | 2 |
| **合计** | **250** | **82,564** | **67** | **1** | **33** | **149** |

**结论**：约 27% 的文件（算法 + 数据 + 备份 + 提醒规则）可原样或薄改复用；UI 与平台壳必须重写。重写量大不等于风险大——风险集中在数据格式、id 引用、备份语义与手写一致率。

### 1.2 五个「看起来耦合平台」的文件的实际结论

| 文件 | 实际依赖 | 结论 |
|---|---|---|
| `algorithm/ColorOps.kt` | 无（只 `kotlin.math`） | A，零改动 |
| `algorithm/InkWidthSim.kt` | 无（只 `kotlin.math`） | A，零改动；桌面可直接用真实压感，此文件作为无压感回退 |
| `algorithm/CsvExporter.kt` | `Context`、`Intent`、`Uri`、`FileProvider` | B，拆出 CSV 生成；分享壳重写 |
| `algorithm/PdfExporter.kt` | `PdfDocument`、`Canvas`、`Paint`、`Typeface`、`FileProvider` | C，PDFBox 3.x 重写 |
| `algorithm/PdfTextExtractor.kt` | pdfbox-android、`RectF`、`Size`、`Context` | C，PDFBox 3.x 重写；16 个 import 里 5 个是死 import |
| `algorithm/ShareImageGenerator.kt` | `Bitmap`、`Canvas`、`Paint`、`FileProvider` | C，Skia 重写 |

## 2. `algorithm/` 全量清单（58 个文件）

> 迁移动作中「原样」= 复制进 `shared/`，保持包名 `com.ilyskyo.blancall.algorithm`；「+Locale」= 原样 + 将 `lowercase()`/`format()` 固定为 `Locale.ROOT`（见 §7.1）。

| # | 文件 | 行 | 级 | 平台依赖 | 迁移动作 |
|---|---|---:|---|---|---|
| 1 | AchievementManager.kt | 126 | A | — | 原样 |
| 2 | AnswerChecker.kt | 424 | A | — | 原样 |
| 3 | ArticleMemory.kt | 258 | A | — | 原样 |
| 4 | ArticleMemoryFallback.kt | 27 | A | — | 原样 |
| 5 | ArticleMemoryPredictor.kt | 97 | A | — | 原样 |
| 6 | ArticleMemoryReads.kt | 62 | A | — | 原样 |
| 7 | ArticleMemoryShadow.kt | 103 | A | — | 原样 |
| 8 | BlancallGenerator.kt | 873 | A | — | 原样 + Locale（`:485`） |
| 9 | CalendarMonth.kt | 35 | A | — | 原样 |
| 10 | ClozeTokens.kt | 106 | A | — | 原样 |
| 11 | ColorOps.kt | 170 | A | — | 原样 + Locale（`:44`） |
| 12 | ContentFingerprint.kt | 15 | A | — | 原样 + Locale（`:18`），并补单测 |
| 13 | CrossTextReview.kt | 188 | B | 传递 `android.util.Log`（经 Store） | Log shim 后回 A |
| 14 | CsvExporter.kt | 150 | B | `Context`、`Intent`、`Uri`、`Log`、`FileProvider` | 拆分：CSV 生成进 shared；分享壳重写 |
| 15 | DataInterchange.kt | 255 | A | — | 原样（`Locale.getDefault()` 保留，见 §7.1） |
| 16 | DateRange.kt | 58 | A | — | 原样 |
| 17 | DayBucket.kt | 70 | A | — | 原样（`zone` 已参数化） |
| 18 | DictationMistakes.kt | 60 | A | — | 原样 |
| 19 | DictationScorer.kt | 148 | A | — | 原样 |
| 20 | DictationSentenceStates.kt | 44 | A | — | 原样 |
| 21 | DifficultyCalculator.kt | 62 | A | — | 原样 |
| 22 | DueListModel.kt | 37 | A | — | 原样 |
| 23 | EbbinghausScheduler.kt | 170 | A | — | 原样；Phase 1 加 `zone` 参数（默认不变） |
| 24 | ExamPaper.kt | 73 | A | — | 原样 |
| 25 | ForgettingPredictor.kt | 237 | A | — | 原样；Phase 1 把 `ZoneId.systemDefault()` 提为参数 |
| 26 | FSRS.kt | 276 | A | — | 原样；Phase 1 给同日判定加 `zone` 参数（默认不变） |
| 27 | GaokaoTextCleaner.kt | 338 | C | `Context`、pdfbox-android | PDFBox 3.x；纯文本清理约 90% 可留 |
| 28 | GlyphConfusables.kt | 162 | A | — | 原样 |
| 29 | HeatmapGrid.kt | 192 | A | — | 原样 |
| 30 | InkWidthSim.kt | 97 | A | — | 原样（桌面作为无压感回退） |
| 31 | MaskSpanOps.kt | 37 | B | 传递 `android.util.Log` | Log shim 后回 A |
| 32 | MaskToCloze.kt | 48 | A | — | 原样 |
| 33 | MemoryHeatmap.kt | 237 | B | `androidx.compose.ui.graphics.Color` | `Color` → `Int`/值类；或保留 Compose runtime |
| 34 | MemoryModelConfig.kt | 21 | A | — | 原样 |
| 35 | MistakeNotebook.kt | 237 | A | — | 原样 + Locale（`:173`） |
| 36 | PdfExporter.kt | 370 | C | `PdfDocument`、`Canvas`、`Paint`、`Typeface`、`FileProvider` | PDFBox 3.x 重写；排版常量保留约 60% |
| 37 | PdfTextExtractor.kt | 183 | C | pdfbox-android、`RectF`、`Size`、`Context` | PDFBox 3.x `Loader.loadPDF`；约 85% 可留 |
| 38 | PracticeContentOps.kt | 154 | A | — | 原样 |
| 39 | PracticeCoverage.kt | 13 | A | — | 原样 |
| 40 | RangeOps.kt | 47 | A | — | 原样 |
| 41 | RatingPolicy.kt | 23 | A | — | 原样 |
| 42 | ReadingSelection.kt | 152 | A | — | 原样 |
| 43 | ReadingSpeed.kt | 143 | A | — | 原样 |
| 44 | ReviewTimeline.kt | 95 | A | — | 原样 |
| 45 | SectionSplitter.kt | 304 | A | — | 原样 |
| 46 | SentenceCloze.kt | 63 | A | — | 原样 |
| 47 | SentencePracticeRatings.kt | 44 | A | — | 原样 |
| 48 | SentenceSelector.kt | 190 | A | — | 原样 |
| 49 | SentenceSplitter.kt | 208 | A | — | 原样 + Locale（`:186`） |
| 50 | SessionPlanner.kt | 193 | A | — | 原样 |
| 51 | ShareImageGenerator.kt | 262 | C | `Bitmap`、`Canvas`、`Paint`、`FileProvider` | Skia（`org.jetbrains.skia`）重写；约 70% 可留 |
| 52 | StudyCoach.kt | 692 | A | — | 原样 |
| 53 | StudyFlow.kt | 130 | A | — | 原样 |
| 54 | TagOps.kt | 212 | A | — | 原样 |
| 55 | TextNormalizer.kt | 217 | A | — | 原样 + Locale（`:104`） |
| 56 | TextStatistics.kt | 216 | A | — | 原样 |
| 57 | WeaknessMath.kt | 80 | A | — | 原样 |
| 58 | WeakSentenceRank.kt | 91 | A | — | 原样 |

> `Blancall-Std` 不含 `AiClozeGenerator.kt`、`AiEquivalenceJudge.kt`（Pro 独有，Windows 不迁移）。

## 3. `data/` 全量清单（29 个文件）

| 文件 | 行 | 级 | 平台依赖 | 迁移动作 | 风险 |
|---|---:|---|---|---|---|
| `backup/BackupManager.kt` | 500+ | A | — | 原样 + Windows 安全加固（见 ARCHITECTURE §6.6）；移除 AI 类目 | **高**：误删/越界 |
| `backup/LearningDataReloader.kt` | — | B | `Context`、UI 单例 | 改为 `reloadAll(filesDir, hooks)` | 中 |
| `contentpack/ContentPackStore.kt` | 292 | A | — | 原样 | 低 |
| `database/ArticleStorage.kt` | 324 | B | `android.util.Log` | Log shim；后台首载改显式 `awaitLoaded()` | 中：id 游标 |
| `handwriting/HandwritingCharset.kt` | — | A | — | 原样 | 低 |
| `handwriting/HandwritingRecognizer.kt` | — | C | `Context`、`Bitmap/Canvas/Matrix`、JNI | NCNN desktop + C ABI + JNA；灰度/softmax 逻辑保留 | **高**：一致率 |
| `handwriting/HandwritingResult.kt` | — | A | — | 原样（阈值 0.80/0.15） | 低 |
| `model/Article.kt` | 60 | A- | `androidx.compose.runtime.Immutable` | 保留注解（shared 引入 Compose runtime）或去注解 | 低 |
| `model/PracticeRecord.kt` | — | A | — | 原样 | 低 |
| `model/PracticeState.kt` | — | A | — | 原样 | 低 |
| `model/Tag.kt` | — | A | — | 原样 | 低 |
| `repository/AchievementTimelineStore.kt` | — | B | `Log` | Log shim | 低 |
| `repository/ArticleRepository.kt` | — | A | — | 原样 | 低 |
| `repository/CustomClozeStore.kt` | — | B | `Log` | Log shim | 低 |
| `repository/DailySentenceCoordinator.kt` | — | A | — | 原样 | 低 |
| `repository/FsrsStateStore.kt` | 390+ | B | `Log`、`VisibleForTesting` | Log shim；删注解 | 中 |
| `repository/HomeLayoutStore.kt` | — | B | `Log` | Log shim | 低 |
| `repository/InkStore.kt` | — | B | `Log`、Compose `Offset` | 自研 `Point` 或 Compose Desktop `Offset` | 低 |
| `repository/InProgressPracticeStore.kt` | — | A | — | 原样 | 低 |
| `repository/MaskConfigStore.kt` | — | B | `Log` | Log shim | 低 |
| `repository/OneShotCloze.kt` | — | A | — | 原样 | 低 |
| `repository/ReaderPrefsStore.kt` | — | B | `Context` | 改为接收 `File filesDir` | 低 |
| `repository/ReadingMarkStore.kt` | — | B | `Log` | Log shim | 低 |
| `repository/RecordRepository.kt` | 400+ | B | `Log` | Log shim；新增 `record_id_seq`（见 ADR-004） | **高**：id 复用 |
| `repository/ReviewLogStore.kt` | — | A | — | 原样 | 低 |
| `repository/SentenceCardStore.kt` | — | A | — | 原样 | 低 |
| `repository/StatsRepository.kt` | — | A | — | 原样 | 低 |
| `repository/StoreVersion.kt` | — | B | `Log` | Log shim | 低 |
| `repository/TagStore.kt` | — | B | `Log` | Log shim | 低 |

> `Blancall-Std` 不含 `data/ai/*`（Pro 独有）。`BackupManager` 里仍保留 `Group.AI` 常量，但 `defaultSelection()` 排除 AI；Windows 直接删除该组，导入 Pro 备份时忽略 `ai` 类目。

## 4. `util/`（2 个文件）

| 文件 | 行 | 级 | 平台依赖 | 迁移动作 |
|---|---:|---|---|---|
| `AtomicFiles.kt` | 217 | A | 纯 `java.io` / `java.nio` | 原样复制；补 Windows 路径/锁/目录 fsync 降级测试 |
| `FileTextExtractor.kt` | 824 | B | `Uri`、`ContentResolver`、`OpenableColumns` | 解析主体（TXT/HTML/RTF/EPUB/DOCX/DOC）原样；文件入口改为 `File`/`InputStream`；PDF 段换 PDFBox 3 |

> `Blancall-Std` 不含 `SecurePrefs.kt`（无 AI Key），桌面也**不需要**任何凭据存储。

## 5. `notification/`（8 个文件）

| 文件 | 行 | 级 | 平台依赖 | 迁移动作 |
|---|---:|---|---|---|
| `ReminderPlan.kt` | 312 | A | — | 原样（纯函数：11 种原因、优先级、免打扰） |
| `ReminderSchedule.kt` | 94 | A | — | 原样（`java.time`） |
| `ReminderStateStore.kt` | 140 | B | `Log` | Log shim；加跨进程文件锁 |
| `NotificationChannels.kt` | 159 | C | `NotificationManager`、渠道 | 桌面无渠道概念，删除；应用内保留开关 |
| `NotificationHelper.kt` | 368 | C | `NotificationCompat`、`PendingIntent`、深链 | 托盘气泡/Toast + 激活参数重写 |
| `PracticeLiveUpdate.kt` | 269 | C | Android 16 Live Updates、`Notification.ProgressStyle` | 任务栏进度 `ITaskbarList3` 近似或砍掉 |
| `ReminderNotifications.kt` | 35 | C | `NotificationManager` | 桌面降级为托盘气泡 |
| `ReminderWorker.kt` | 415 | C | `WorkManager`、自调度 | 桌面 `ReminderScheduler`（托盘常驻）重写；规则层复用 |

## 6. `widget/`（3 个文件）

| 文件 | 行 | 级 | 平台依赖 | 迁移动作 |
|---|---:|---|---|---|
| `BlancallWidget.kt` | 275 | C | Glance、Compose for appwidget | 托盘迷你窗/置顶小窗（P2） |
| `BlancallWidgetReceiver.kt` | 9 | C | Glance receiver | 删除 |
| `WidgetRefreshReceiver.kt` | 41 | C | 系统广播 | 删除；桌面由调度器刷新 |

## 7. `ui/`（148 个文件 / 63,054 行）

### 7.1 按包汇总

| 包 | 文件 | 行 | 级 | 迁移策略 |
|---|---:|---:|---|---|
| `ui/cloze` | 2 | 1,281 | C | 重写编辑器；区间选择用鼠标拖选 + 键盘微调 |
| `ui/common` | 47 | 9,460 | C | 重建设计系统：玻璃卡、按钮、菜单、图表、Toast；14 个纯工具文件可复用；含 `WelcomeScreen` |
| `ui/handwriting` | 7 | 3,845 | C | 书写板重写（Skia）；`PunctuationMap` 可复用；切分逻辑在 `StrokeGestures`，纯算法可搬 |
| `ui/home` | 10 | 9,009 | C | 首页卡片画布重写；布局数据与卡片类型复用 |
| `ui/import` | 2 | 964 | C | 重写导入页；解析逻辑复用 `FileTextExtractor` |
| `ui/list` | 2 | 1,709 | C | 双栏列表 + 多选（Ctrl/Shift）+ 右键菜单 |
| `ui/navigation` | 3 | 1,022 | C | 自研 Navigator + 路由常量；两个 Dispatcher 可复用 |
| `ui/onboarding` | 2 | 653 | C | 重写为桌面首次启动向导（欢迎页 + 5 页引导） |
| `ui/practice` | 11 | 4,375 | C | 重写练习页；判分/提示/状态持久化复用 |
| `ui/reader` | 19 | 8,201 | C | 重写阅读器；遮挡/标注/进度逻辑复用 |
| `ui/search` | 1 | 716 | C | 重写搜索页；`Ctrl+F` |
| `ui/settings` | 8 | 5,151 | C | 重写设置页；备份/数据交换逻辑复用 |
| `ui/statistics` | 9 | 7,721 | C | 重写统计页；图表用自绘 Canvas；3 个纯逻辑文件可复用 |
| `ui/tag` | 6 | 1,962 | C | 重写标签管理；TagOps 复用 |
| `ui/theme` | 11 | 2,426 | C | 重建设计令牌；`ColorMath` 可复用；`AppPrefs`/`AppearanceStore` 改为桌面设置存储 |
| `ui/viewmodel` | 5 | 2,563 | C | 重写为 Controller/StateFlow；判分与练习控制逻辑可搬 |
| `ui/western` | 3 | 1,996 | C | 素材库 + PDF 预览重写；PDFBox 渲染 |

> `Blancall-Std` 不含 `ui/ai/*`、`ui/common/MarkdownLinks.kt`、`ui/practice/LocalClozeCollectPage.kt`、`ui/practice/PracticeAiPages.kt`、`ui/viewmodel/AiViewModel.kt`、`ui/viewmodel/PracticeAiController.kt`。

### 7.2 `ui/` 中无 Android/Compose import 的文件（14 个，可直接复用）

`ui/common/GlassBlur.kt`、`GlassThemeTint.kt`；
`ui/handwriting/PunctuationMap.kt`；
`ui/navigation/NavigationDispatcher.kt`、`ShareTextDispatcher.kt`；
`ui/reader/ReaderPrefsSession.kt`、`ReadingMarkMerge.kt`、`ReadingSelectionSpans.kt`；
`ui/settings/LanguageLabelFont.kt`（1 个 androidx 注解）；
`ui/statistics/HistoryGrouping.kt`、`NextStepDecider.kt`、`TaskDisplayLogic.kt`；
`ui/theme/ColorMath.kt`；
`ui/viewmodel/PracticeHints.kt`（1 个 androidx 注解）。

> 注意：`ui/theme/ColorMath.kt` 与 `algorithm/ColorOps.kt` 是两套并存的颜色数学；Windows 主题只搬 `ColorOps` 会导致取色结果与 Android 主题不一致，主题令牌必须单独对照。

## 8. 原生层（`app/src/main/cpp/`）

| 文件 | 级 | 处理 |
|---|---|---|
| `preprocess.c` / `preprocess.h` | A（C 代码） | **原样复用**；编进 Windows DLL，常量（FG 220、content 56、canvas 64；拉丁 20/28）不得改 |
| `hccr_jni.cpp` | C | 改为 C ABI：`hccr_create/recognize/destroy` + JNA；张量/blob 名（`in0`/`out0`）与 softmax 位置不变 |
| `CMakeLists.txt` | C | 重写为 MSVC/NCNN desktop 构建 |
| `third_party/ncnn-android/` | — | 不迁移；改用 NCNN Windows 预编译/源码静态编（BSD-3） |

## 9. Android 依赖 → Windows 替代

| Android 依赖 | 版本 | 用途 | Windows 方案 | 许可 |
|---|---|---|---|---|
| Compose BOM / Material3 | 2026.09.00 | UI | Compose Multiplatform Desktop（Material3） | Apache-2.0 |
| navigation-compose | 2.10.2 | 导航 | 自研 Navigator（路由常量 1:1） | — |
| lifecycle-viewmodel-compose | 2.11.0 | 状态 | `StateFlow` + Controller（不引 AndroidX） | — |
| pdfbox-android | 2.0.27.0 | PDF 文本 | Apache PDFBox 3.0.x（桌面） | Apache-2.0 |
| work-runtime-ktx | 2.12.0 | 提醒 | 托盘常驻自调度（Phase 3）；计划任务（可选） | — |
| glance-appwidget | 1.2.0 | 小组件 | 托盘迷你窗（P2） | — |
| commonmark | 0.31.0 | Markdown | 原样（纯 JVM） | BSD-2（需补 THIRD-PARTY 声明） |
| input-motionprediction | 1.0.0 | 笔迹预测 | 删除 | — |
| dynamicanimation / backdrop / LiquidGlassView | 1.1.0 / 1.0.0 / 1.0.5 | 液态玻璃 | 静态半透明 + 可选 Mica/Acrylic；不复刻 AGSL | MIT/Apache |
| junit / androidx.test | 4.13.2 / 1.3.0 | 测试 | JUnit 5.11.x + kotlinx-coroutines-test | EPL 2.0 / Apache |
| org.json（test only） | 20240303 | JSON | 提升为 `shared` 运行时依赖，**锁定同版本** | Public Domain |

### 9.1 新增依赖（候选，Phase 1 首次构建时锁定）

| 依赖 | 候选版本 | 用途 | 许可 |
|---|---|---|---|
| Kotlin | 2.2.10 | 与 Android 一致 | Apache-2.0 |
| Compose Multiplatform | 与 Kotlin 2.2.10 兼容的 1.9.x | UI | Apache-2.0 |
| Gradle Wrapper | 9.8.2 | 构建 | Apache-2.0 |
| JDK | Temurin 21 LTS | 运行 + jpackage | GPLv2+CE |
| kotlinx-coroutines | 1.10.x | 并发 | Apache-2.0 |
| org.json | 20240303 | JSON 兼容 | Public Domain |
| Apache PDFBox | 3.0.x | PDF 读/写/预览 | Apache-2.0 |
| Apache POI（HWPF） | 5.3.x | 旧 `.doc`（Word 97-2003）导入 | Apache-2.0 |
| JNA | 5.15.x | 原生推理/托盘/单实例/可选 Mica（Phase 5） | Apache-2.0 / LGPL 双许可（选 Apache） |
| Logback | 1.5.x | 本地日志 | EPL 1.0 / LGPL 2.1 |
| JUnit Jupiter | 5.11.x | 测试 | EPL 2.0 |
| NCNN | 20240820 | 手写推理（Phase 5） | BSD-3 |

> **不需要** Ktor / OkHttp / 任何 HTTP 客户端：Windows 无 AI、无联网、无遥测。
> 规则：`libs.versions.toml` 中禁止 `+`、`latest.release`、`[1.0,)` 等动态版本；升级必须单独提交并跑兼容测试。

## 10. 资源与资产

| 资产 | 体积 | 处理 |
|---|---:|---|
| `assets/hccr/` 中文模型 + charset | 22.33 MB | **随包分发**（用户已确认，接受 CASIA-HWDB 仅学术、禁商用的限制）；安装包体积风险见 RISKS R-16 |
| `assets/hccr_en/` 拉丁模型 | 1.53 MB | 随包分发 |
| `assets/philo/` 哲学素材 | 10.87 MB | 改为可选内容包，不默认捆绑（ADR-009） |
| `assets/gaokao/` 高考素材 | 9.83 MB | 同上 |
| `assets/PRIVACY.md` | 小 | 随包；欢迎页/关于页可查看 |
| `res/font/xwwk_*.ttf` | 11.27 MB | 仅保留 Regular 供阅读器/PDF 嵌入（OFL 1.1）；UI 用 Microsoft YaHei UI |
| `res/font/lang_*.ttf` | 0.03 MB | 语言名固定字形，可保留 |
| `res/values*/strings.xml` | 6 × 1,656 条（1,583 string + 73 plurals） | 构建期生成 i18n JSON（ADR-010） |

## 11. 迁移批次

| 批次 | 内容 | 前置条件 |
|---|---|---|
| P0 地基 | 建 Gradle 工程；`shared` 引 `org.json`；`platform.Log`；`Article` 注解处理；JUnit5 骨架 | 用户确认 Phase 0 |
| P1 算法 | 50 个 A + 2 个 Log shim 回 A + 5 处 Locale 修复 + algorithm 单测 | P0 |
| P2 数据 | 29 个 data 文件；`AtomicFiles` Windows 版；备份导入导出；格式黄金测试 | P1 |
| P3 通知规则 | `ReminderPlan`/`ReminderSchedule`/`ReminderStateStore` + 单测 | P2 |
| P4 PDF/图片 | `PdfExporter`/`PdfTextExtractor`/`GaokaoTextCleaner`/`ShareImageGenerator` | P2 |
| P5 UI | 17 个 UI 包按页面重写；14 个纯工具文件先搬；欢迎页/引导 | P2 |
| P6 手写 | `preprocess.c` + C ABI + JNA + 书写板 + golden 对照 | P5 + 模型随包 |

## 12. 许可与合规

| 项 | 许可 | 结论 |
|---|---|---|
| 项目本体 | MIT | 保留原版权头；新工程 MIT |
| 中文手写权重（CASIA-HWDB 训练） | 仅学术、禁商用 | 用户选择随包分发；必须在 README/关于页显著声明，商用前需授权或重训 |
| 英文手写权重 | EMNIST/NIST + Apache-2.0 | 可分发 |
| NCNN | BSD-3 | 保留版权/免责声明 |
| PDFBox / POI / JNA / Compose | Apache-2.0 | 可分发 |
| Logback | EPL 1.0 / LGPL 2.1 | 可分发（选 EPL 并声明） |
| commonmark | BSD-2 | Android 的 `THIRD-PARTY-LICENSES` 未列入，Windows 必须补 |
| 霞鹜文楷 / Noto 语言名子集 | OFL 1.1 | 可嵌入；保留声明，不改字体名 |
| 图标字形（霞鹜新晰黑） | OFL 1.1 + IPA 1.0 | 保留声明 |

## 13. 禁止事项（审计结论）

1. 不把 `androidx.compose.*`（Android 变体）引入 `shared`；`shared` 的 `build.gradle.kts` 必须没有 Android 插件。
2. 不把 `org.json` 换成 `kotlinx.serialization` 来读写既有数据文件（字段缺失/稀疏/数字类型语义不同，见 RISKS R-05）。
3. 不复制 `local.properties`、`keystore/`、`apk-output/`、用户 `filesDir` 数据进新工程。
4. 不携带 `pdfbox-android`、Glance、WorkManager、Android Keystore、`android.util.Log`。
5. 不引入任何 HTTP 客户端/网络库；Windows 版不得声明 INTERNET 能力。
6. 不引入 GPL/AGPL/许可证不明依赖；NCNN 若选预编译包需确认其 LICENSE 随包。
