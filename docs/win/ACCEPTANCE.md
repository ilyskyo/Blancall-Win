# Windows 验收记录

- 日期：2026-11-12
- 构建：Kotlin 2.2.20 / Compose 1.10.3 / Gradle 8.14.3 / JDK 21（Android Studio JBR）
- 数据基准：`Blancall-Std 7.6.1-NoAI / vc54`

## 1. 自动化

| 命令 | 结果 |
|---|---|
| `gradlew :shared:test` | BUILD SUCCESSFUL（含移植测试 + 新增 RecordIdCursor/BackupWindowsSafety） |
| `gradlew :desktop:test` | BUILD SUCCESSFUL（练习落库/句子 FSRS/DOCX/PDF/TXT/PDF 导出） |
| `gradlew :desktop:compileKotlin` | BUILD SUCCESSFUL |

## 2. 手工

| 项 | 状态 | 证据/说明 |
|---|---|---|
| 启动应用 | PASS | 进程窗口标题 `Blancall` |
| 窗口适配屏幕 | PASS | Win32 `GetWindowRect` = 1048×640，位于 (40,32)，屏幕 1128×752 |
| 首页渲染 | PASS | 截图 `_shots/home4.png`：导航栏、统计卡、今日计划、文章数=1 |
| 首页读取 Android 格式文章 | PASS | 直接写入 `articles.json` 后显示《劝学（节选）》 |
| 导入（粘贴/TXT/PDF/DOCX） | 自动通过 / UI 未手动验证 | `TextImporter` 测试覆盖 DOCX/PDF/TXT |
| 三种练习模式 UI | 未手动验证 | 代码已实现，判分/落库有自动测试 |
| 断点续练 | 未手动验证 | `PracticeSessionStore` 写读逻辑已接入 |
| PDF 导出 | 自动通过 / UI 未手动验证 | `PdfExporter` 测试校验中文标题与答案页 |
| 备份导出/导入 | 自动通过 / UI 未手动验证 | shared `BackupManagerTest` 全绿 |
| 标签创建/绑定 | 未手动验证 | `TagsScreen` / `TagPickerDialog` 已编译 |
| 关闭询问/托盘 | 未手动验证 | 代码已接入 `onCloseRequest` + `TrayController` |
| 单实例 | 未手动验证 | `SingleInstance` 文件锁已接入 |
| 快捷键 | 未手动验证 | `Ctrl+N/O/F/,`、`Esc`、`F11` 已接入 `onPreviewKeyEvent` |
| DPI 125/150/200% | 未验证 | 仅验证当前缩放下的窗口尺寸 |
| 六语言 | 未实现 | UI 仍为简体中文 |
| 手写 | 未实现 | 无 NCNN/书写板 |
| 提醒调度 | 未实现 | 规则层已迁移，桌面调度器未接 |
| MSI/便携包 | 未实现 | 需 Temurin `jpackage` + WiX |

## 3. 阻塞与下一步

1. 用真实 Android 备份 ZIP 做一次导入→导出→再导入的端到端回归（需用户提供脱敏数据）。
2. 手动走一遍 UI：导入→练习→结果→统计→备份；记录坐标级问题。
3. 补 i18n 资源管线（6 套 strings.xml → JSON）与语言切换。
4. 接提醒调度（托盘常驻）与内容包 UI。
5. 手写阶段：NCNN desktop + 固定画布 + golden 对照（模型许可声明随包）。
6. 打包：Temurin JDK 21 + WiX 3.14，生成 MSI/便携 ZIP。

## 4. 2026-11-13 增量（0.2.0-win）

### 自动化

| 命令 | 结果 |
|---|---|
| `gradlew :shared:test :desktop:test` | BUILD SUCCESSFUL（新增 `ReminderPrefsTest` 5 例、复习模板配置 1 例） |
| `gradlew :desktop:compileKotlin` | BUILD SUCCESSFUL |

### 手工

| 项 | 状态 | 证据/说明 |
|---|---|---|
| 启动应用（含提醒调度初始化） | PASS | 窗口标题 `Blancall`；`logs/` 无新增崩溃日志 |
| 提醒设置 UI | 未手动逐项点验 | 代码路径：设置 → 复习与提醒；偏好落盘有单测 |
| 托盘气泡 | 未验证 | 依赖 SystemTray；无托盘环境自动降级为应用内提示 |
| 首页今日计划/今日一句 | 未手动验证 | 取数与提醒共用 `AppState.studySnapshot` |
| 错题本/成就/学习日历 | 未手动验证 | 聚合与判定逻辑由 shared 测试覆盖 |
| 内容包导入 | 未手动验证 | `ContentPackStoreTest` 覆盖安全边界 |
| 标签筛选 | 未手动验证 | UI 过滤基于 `TagStore.data` |
| 10 秒自动淡显 | 未手动验证 | 计时循环在练习页；无输入 10 秒后逐字淡显 |

### 遗留

- 六语言、手写、MSI/便携包为下一步主线；托盘气泡/睡眠唤醒补发需在真实桌面会话验收。

## 5. 2026-11-13 增量（0.3.0-win）

### 自动化

| 命令 | 结果 |
|---|---|
| `gradlew :shared:test :desktop:test` | BUILD SUCCESSFUL |
| 新增用例 | `ReaderOcclusionTest` 6 例；`StringsParityTest` 5 例；跨文练习 / 复习模板 / 提醒偏好 |

### 手工

| 项 | 状态 | 证据/说明 |
|---|---|---|
| 六语言切换 | 未手动逐语言点验 | 设置 → 界面语言；`StringsParityTest` 校验 5 套 key 一致 |
| 遮挡/划线/批注 | 未手动验证 | 阅读页工具条；遮挡算法有 shared 测试 |
| 自定义挖空 / 跨文 / 集训 | 未手动验证 | 练习页与统计页入口已编译；跨文记录有单测 |
| 拖拽 / 剪贴板导入 | 未手动验证 | AWT DropTarget + 系统剪贴板 |
| 菜单栏/最近打开/欢迎页 | 未手动验证 | 代码路径已接 |
| 诊断包/分享图/CSV | 未手动验证 | 导出实现已编译；诊断包不含正文 |
| 便携包 | PASS | `dist\Blancall-7.6.1-win-x64-portable.zip`（127.7 MB，含手写模型）与 `-lite.zip`（100.5 MB）；解压后 `Blancall.vbs` 启动，窗口标题 `Blancall` |
| 手写识别 | PASS（端到端） | `HandwritingRecognizerTest`：Java2D 渲染「永」→ NCNN 推理 top-1 永 97.6%，top-5 含汞/示/亦/禾；模型 7356 类加载成功 |
| 手写 UI | 未手动验证 | 练习页「手写」按钮 → 白底画布 → 候选点选；模型缺失时入口隐藏 |
| 用户级安装 | PASS | `tools/install-portable.ps1` 安装到 `%LOCALAPPDATA%\Programs\Blancall`，桌面/开始菜单快捷方式已创建并可启动 |
| MSI | 阻塞 | jpackage/WiX 在当前受限令牌下失败（`Files.isWritable` 误判 / light.exe 临时目录特权）；普通用户会话可用 `tools/package-windows.ps1` 尝试 |

### 遗留

- 手写压感/连写拆分/跨设备一致率未做；运行时拼接文案未进翻译 catalog；MSI 需普通用户会话 + WiX 3.14。

## 6. 2026-11-13 增量（0.4.0-win）

### 自动化

| 命令 | 结果 |
|---|---|
| `gradlew :desktop:test :shared:test` | BUILD SUCCESSFUL |
| desktop 用例 | 29 项：DesktopFlowTest 12（含 PDF 预览、EPUB spine 抽取、DOC 旧格式、首页布局持久化）、HandwritingRecognizerTest 4、ReminderPrefsTest 5、StringsParityTest 8（含动态模板填参/兜底/六套收录守卫） |
| shared 用例 | 全量通过（FSRS / 句子队列 / 遮挡 / 备份 / 内容包 / 工具类）；新增 `HandwritingSegmenterTest` 5 例、`ScaleBudgetTest` 2 例 |

### 手工/代码证据

| 项 | 状态 | 证据/说明 |
|---|---|---|
| PDF 预览 | PASS（自动化） | `PdfPreviewTest`（DesktopFlowTest 内）：2 页 PDF → 页图 2 张、总页数 2、文本含 `Preview page 1` |
| 句子卡 | PASS（逻辑） | `SentenceCardsScreen` 复用 shared `DailySentenceCoordinator.buildQueue` + `FsrsStateStore.saveSentence`；队列/句键/评级由 shared 测试覆盖；UI 交互待真实桌面点验 |
| 标签主页 | PASS（编译 + 逻辑） | 标签过滤复用 `TagStore.links`；到期判定复用 `FsrsEngine.isDue` |
| 六维雷达 | PASS（编译） | 口径与 Android `WeaknessProfileCard` 一致：类型错误数 ÷ 全部错因数 |
| 练习历史下钻 | PASS（编译） | 单篇 → `Screen.Statistics(articleId)`；跨文 → 来源文章弹窗 |
| 动态多语言模板 | PASS（自动化） | `StringsParityTest`：`Strings.f` 填参、缺失兜底、占位符不吞；5 套 catalog key 一致 |
| 属性解析修复 | PASS（自动化） | 自定义解析器仅按首个 `=` 分割；含空格键（`{0} 篇` 等）可命中 |
| 首页布局编辑 | PASS（自动化） | `DesktopFlowTest`：顺序/隐藏项落盘后重读一致，未知 key 丢弃、缺失项补默认，恢复默认可用 |
| 手写连写拆分 | PASS（自动化） | `HandwritingSegmenterTest` 5 例；`HandwritingRecognizerTest` 端到端：两字渲染 → 拆成 2 段 → 识别为「你好」 |
| 手写识别精度 | PASS（自动化） | 合成笔画 top-1：永 97.6%、你 99.3%、好 98.3%、学 99.8%；拉丁 A 99.97%、7 99.9% |
| 规模预算 | PASS（自动化） | `ScaleBudgetTest`：5 万条记录错题聚合 211ms（预算 15s）；1,000 篇到期文章今日计划 7ms（预算 2s） |
| EPUB 导入 | PASS（自动化） | `DesktopFlowTest`：最小 EPUB → 两章按 spine 顺序输出、`&amp;` 解码、正文不含标签 |
| DOC 导入 | PASS（自动化） | `DesktopFlowTest`：Apache POI `simple.doc` → 抽取到 `Word 97-SR2` 正文 |
| DPI 缩放启动 | PASS（冒烟） | `-Dsun.java2d.uiScale=1.25/1.5/2.0` 均成功创建 `Blancall` 窗口且进程响应；像素级布局仍需人工验收 |
| 便携包 | PASS | `dist\Blancall-7.6.1-win-x64-portable.zip`（142.6 MB，含手写模型）与 `-lite.zip`（115.4 MB）；app-image 启动后窗口标题 `Blancall`、进程响应正常，无崩溃日志 |
| MSI | 阻塞（环境） | `jpackage` 已能找到 `.tools\wix314` 的 WiX 3.14，但 `light.exe` 在本受限令牌下创建临时目录时被拒绝访问（`Win32Exception: 拒绝访问`）；脚本已自动把 WiX 加入 PATH，普通用户会话重跑即可 |

### 遗留

- 手写压感/标点手势/跨设备一致率未做；MSI 需普通用户会话（脚本已自动接入 WiX）。
- 真实 Android 备份端到端回归与 DPI 像素级布局矩阵需真实桌面会话人工验收（125/150/200% 启动冒烟已通过，算法规模预算已自动化覆盖）。
