# 上游同步基线

- 参考工程：`D:\Blancall\Blancall-Std`（NoAI）
- 版本：`7.6.1-NoAI` / `versionCode 54`
- 同步日期：2026-11-12
- 同步方式：只读复制 + 确定性变换；未修改 Android 工程任何文件

## 已迁移

| 目录 | 文件数 | 说明 |
|---|---:|---|
| `algorithm/` | 53 | 58 个中排除 4 个 C（PdfExporter/PdfTextExtractor/GaokaoTextCleaner/ShareImageGenerator）与 1 个 B（CsvExporter，待桌面封装） |
| `data/model` | 4 | Article 去掉 `@Immutable` |
| `data/repository` | 18 | `Log` 替换；`ReaderPrefsStore` Context→File；`InkStore` Offset→InkPoint |
| `data/database` | 1 | ArticleStorage（Log 替换） |
| `data/backup` | 1 | BackupManager + Windows 安全加固 |
| `data/contentpack` | 1 | ContentPackStore |
| `data/handwriting` | 2 | Charset / Result（Recognizer 未迁移） |
| `util` | 1 | AtomicFiles |
| `notification` | 3 | ReminderPlan / ReminderSchedule / ReminderStateStore |
| 测试 | 96 | algorithm / data / util / notification；JUnit4 + vintage |

## 确定性变换

1. `import android.util.Log` → `import com.ilyskyo.blancall.platform.Log`
2. 删除 `androidx.annotation.VisibleForTesting` 注解
3. 删除 `Article` 的 `androidx.compose.runtime.Immutable` 注解
4. `ReaderPrefsStore.getInstance(context)` → `getInstance(filesDir: File)`
5. `InkStore` 的 Compose `Offset` → 本地 `InkPoint`
6. `MemoryHeatmap` 的 Compose `Color` → 本地 `HeatColor`
7. `ReminderFrequency` 从 `ui.theme` 抽到 `notification`（去资源 ID）

## 未迁移（C 类，后续阶段）

- `PdfExporter` / `PdfTextExtractor` / `GaokaoTextCleaner` / `ShareImageGenerator`
- `data/handwriting/HandwritingRecognizer` + `cpp/`
- `notification/ReminderWorker` 等 WorkManager/通知类
- `widget/`
- `ui/` 全部 Compose Android 页面（桌面重写）
- `data/ai/`（NoAI 版不存在）

## 测试基线

- Android Std：150 个测试文件 / 1,178 个 `@Test`
- Windows shared：96 个测试文件；`gradlew :shared:test` 全绿
- Windows desktop：`DesktopFlowTest`（练习落库 + 句子 FSRS + DOCX/PDF/TXT 导入 + PDF 导出）全绿

## 升级流程

1. 更新 Android Std 后，重跑 `tools/sync-from-android.ps1`（待实现）。
2. 按上表变换逐文件 diff；禁止整目录覆盖。
3. 跑 `:shared:test :desktop:test` + golden 对比。
4. 更新本文件的版本/日期与 `CHANGELOG.md`。
