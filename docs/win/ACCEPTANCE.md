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

## 7. 2026-11-13 增量（0.4.2-win：EXE 安装包）

### 产物

| 文件 | 大小 | 说明 |
|---|---:|---|
| `dist\Blancall-7.6.1-win-x64-setup.exe` | 116.5 MB | Inno Setup 6.7.3 编译；当前用户安装，无需管理员 |
| `dist\Blancall-7.6.1-win-x64-portable.zip` | 122.9 MB | 含 ncnn 与拉丁模型，不含中文手写模型 |
| `dist\Blancall-7.6.1-win-x64-portable-lite.zip` | 115.8 MB | 不含 `models/` |

### 自动化/手工

| 项 | 状态 | 证据/说明 |
|---|---|---|
| 安装包编译 | PASS | `ISCC.exe` 退出码 0；`tools/windows-installer.iss`；`ArchitecturesAllowed=x64compatible` |
| 静默安装（验证变体） | PASS | 变体关闭快捷方式与卸载注册项（`/NOICONS` + `CreateUninstallRegKey=no`），`/VERYSILENT` 退出码 0；`Blancall.vbs`、`app\blancall-win.jar`、`runtime\bin\javaw.exe`、`models\ncnn.dll`、`models\hccr_en\model_en.bin`、`README.md`、`THIRD-PARTY-LICENSES.md`、`unins000.exe` 全部存在 |
| 安装后启动 | PASS | 从安装目录运行 `Blancall.vbs`，`javaw` 窗口标题 `Blancall`，`Responding=True` |
| 静默卸载（验证变体） | PASS | `unins000.exe /VERYSILENT` 退出码 0，程序目录删除；`%LOCALAPPDATA%\Blancall` 保持存在 |
| 正式安装包的快捷方式/卸载注册项 | 未验证（环境） | 本环境限制未签名程序写开始菜单与 `HKCU`（PowerShell 与已签名程序可写，自签名仍被拒绝）；需普通用户会话点验 |
| MSI | 阻塞（环境） | 同 0.4.0：`light.exe` 临时目录特权受限；EXE 安装包已替代 |


## 8. 2026-11-14 增量（0.5.0-win：对齐 Android 平板 UI + 自适应断点）

### 设计基准

- 平板实机截图（3048×2032 @400dpi ≈ 1219×813dp）：`D:\Blancall\.screenshots\pad-home.png`、`pad-articles2.png`、`pad-data2.png`、`pad-reader2.png`（仓库外）。
- 默认字体以**系统衬线**为准（与 Android `AppearanceSnapshot.FONT_SERIF` 默认一致）；霞鹜文楷改为设置项，不再是默认。
- 默认底色纯白（米黄关闭），断点 600dp（与 Android `Adaptive.kt` 一致）。

### 自动化

| 命令 | 结果 |
|---|---|
| `gradlew :shared:test :desktop:test` | BUILD SUCCESSFUL |
| 新增 `AdaptiveTest` | 断点 599/600/839/840、首页列数 2–6、双栏 720×480 阈值 |
| 新增 `HomeGridTest` | 钉住优先、首次适配、跨行卡绕行、越界 lock 收敛 |
| `ScreenshotRenderTest` 多档渲染 | 1200×800 / 800×800 / 520×800 / 420×640 / 150% 缩放各页截图 |

### 截图证据（离屏渲染，仓库内 `desktop/_shots/`，不入发布包）

| 窗口 | 壳层 | 截图 |
|---|---|---|
| 1200×800 | 左侧 rail + 首页 2 列卡片 | `render_home.png` / `render_articles.png` / `render_reader.png` / `render_practice.png` / `render_settings.png` |
| 800×800 | 左侧 rail | `render_home_medium.png` |
| 520×800 | 底部导航 + 单列 | `render_home_narrow.png` / `render_articles_narrow.png` / `render_practice_narrow.png` |
| 420×640 | 底部导航 + 单列 | `render_home_min.png` |
| 1200×800 @150% | 800dp 逻辑宽，仍为 rail，无裁切 | `render_home_dpi150.png` |
| 深色 | `#1C1C1E` 底 + 马卡龙暗色卡 | `render_home_dark.png` / `render_articles_dark.png` |

### 安装包

| 文件 | 大小 | 说明 |
|---|---:|---|
| `dist\Blancall-7.6.1-win-x64-setup.exe` | 129.5 MB | 自绘单文件安装向导（WinForms）；payload 直接解到安装目录，预置 `BlancallUninstall.exe` |
| `dist\Blancall-7.6.1-win-x64-portable.zip` | 129.3 MB | 解压后双击 `Blancall.exe`；同样含卸载器 |

### 安装器（自绘向导，规避 Inno 在本机的两类拒绝）

| 项 | 状态 | 说明 |
|---|---|---|
| Inno 失败复现 A | 已复现 | 非管理员运行 Inno：`%TEMP%\is-*.tmp` 创建失败（Error 5，exit 1） |
| Inno 失败复现 B | 已复现 | 绕过 Temp 后，Inno 在「保存卸载信息」创建卸载器文件失败（`CreateFile` code 5） |
| 自绘向导编译 | PASS | `tools/installer/BlancallInstaller.cs` + `BlancallUninstall.cs`；csc exit 0 |
| 静默安装 | PASS | `setup.exe /SILENT /NOICONS /DIR=C:\Users\ilysk\AppData\Local\BlancallTest2` exit 0；`Blancall.exe`、`BlancallUninstall.exe`、`LICENSE`、jar、runtime、models、docs 全部落盘 |
| 程序运行中安装 | PASS | 独占打开 `app\blancall-win.jar` 模拟运行占用：`InstallCore` 在解包前抛出六语 `ErrLocked`（英文实测：`Blancall is running, or an installation file is in use...`），不再落到裸 `UnauthorizedAccessException` |
| 高 DPI 向导 | PASS | 本机 2256×1504 @200%；向导为 1286×991 物理 / 643×496 逻辑，欢迎/目录/完成三页截图无裁切；仅缩放布局，字号交由 GDI 按 DPI 渲染（避免 4 倍字） |
| 六语言向导 | PASS | 系统语言自动选中简体中文；用测试钩子分别渲染简中 / 俄 / 日 / 韩欢迎页截图，长俄文在 230dp 信息区内完整换行 |
| 旧启动脚本清理 | PASS | payload 解包前删除安装目录的 `Blancall.vbs` / `Blancall.bat` |
| 预置卸载器 | PASS | `BlancallUninstall.exe /SILENT` exit 0；程序目录删除；`%LOCALAPPDATA%\Blancall` 学习数据保留 |
| MIT 许可证 | PASS | 欢迎页以信息文本 +「查看完整 MIT 许可证」弹窗展示，无「同意 / 拒绝」；`LICENSE` 随程序安装 |
| 桌面/开始菜单快捷方式 | 部分验证（环境） | 开始菜单快捷方式在受限变体通过；桌面 `.lnk` 与 HKCU 卸载项在当前沙箱返回 `0x80070005`，普通用户会话可用 |
| UI 向导 | PASS | PrintWindow 实测欢迎/目录/完成页：`setup-prod-welcome.png`、`setup-2x-zh-options.png`、`setup-2x-ko-finish.png`；语言下拉与 MIT 信息页、无「同意 / 拒绝」均可见 |
| 发布产物 SHA-256 | 记录 | `Blancall-7.6.1-win-x64-setup.exe`：`0F603A7E5BC56B9712BADF60120B947C83D92658DABFC2F29E87FB7E05448D73`；`Blancall-7.6.1-win-x64-portable.zip`：`0B27B9E38C5BA82258BBC30DDA1D7DB7E6F38341E9E30E8255EB438C35E5515A` |

## 5. 2026-11-15 增量（0.6.0-win）

### 自动化

| 命令 | 结果 |
|---|---|
| `gradlew :shared:test :desktop:test` | BUILD SUCCESSFUL；新增 `NavigationPolicyTest`、`I18nUiGuardTest`、`I18nCoverageTest`、`ArticleStorageRollbackTest`、`AtomicFilesTest` 目录错误用例 |
| `gradlew :desktop:uberJar` | BUILD SUCCESSFUL |
| 离屏截图 | `ScreenshotRenderTest` 全绿，产出详情/阅读模式/阅读设置/五分类设置/英文设置/深色/保存失败横幅 |

### 手工

| 项 | 状态 | 证据/说明 |
|---|---|---|
| 开发 jar 实际启动 | PASS | 200% DPI；原生菜单栏（文件/视图/帮助）、雅黑界面、左侧 rail、右侧滚动条均可见；截图 `_tools/ui-check/real-home.png` |
| 窗口最小尺寸 | PASS | 改为 900×600，不再缩到手机宽度 |
| 阅读设置宽/窄形态 | PASS（离屏） | 宽窗右侧 Inspector、窄窗底部面板；`render_reading_settings.png` / `render_reading_settings_narrow.png` |
| 英文设置页 | PASS（离屏） | 顶栏/分类/设置行/分段/下拉均为英文，语言名保留各自写法；`render_settings_en.png` |
| 深色模式 | PASS（离屏） | 详情/阅读模式/设置无浅色不透明底；`render_detail_dark.png` / `render_reading_mode_dark.png` / `render_settings_dark.png` |
| 点按级走查 | 未完成（环境） | 当前沙箱的鼠标输入与交互桌面隔离，`WindowFromPoint` 返回 0，无法自动点按；需普通用户会话按 README 的 UI 点验清单走一遍 |

## 9. 2026-11-15 增量（0.7.0-win）

### 自动化

| 命令 | 结果 |
|---|---|
| `gradlew :shared:test :desktop:test` | BUILD SUCCESSFUL；desktop 51 项（新增 `MaskToClozeTest` 3 例），shared 全量通过 |
| `gradlew :desktop:compileKotlin` | BUILD SUCCESSFUL（含 Logback 1.5.16） |

### 实现与证据

| 项 | 状态 | 证据/说明 |
|---|---|---|
| 内容包拖拽 ZIP | PASS（代码 + 单测覆盖存储边界） | 拖入 ZIP 先判别备份包（`manifest.json` + `files/`），备份包只提示到设置页导入；内容包走 `ContentPackStore.import` |
| 素材库篇目预览 | PASS（编译） | 内容包展开后可预览正文、仅导入、导入并阅读；正文仍由 shared Store 读取 |
| 遮挡转练习 | PASS（自动化） | `MaskToClozeTest`：本地长遮挡坐标合法、重叠/相邻合并、跨句按句裁剪；UI 入口在阅读设置「用当前遮挡开始练习」 |
| 托盘菜单 | PASS（编译） | 「打开 Blancall / 今日复习 / 设置 / 退出」；双击托盘图标打开主窗 |
| 开机自启 | PASS（代码） | `Autostart` 写 HKCU Run，安装版指向 `Blancall.exe`，开发运行指向 `java -jar`；受限环境无权限写 HKCU，需真实会话点验 |
| 任务栏进度 | PASS（代码） | 练习页进入时 `TaskbarProgress.indeterminate()`，退出清理；不支持任务栏的 JDK 静默降级 |
| 自定义字体导入 | PASS（编译） | 阅读设置导入 TTF/OTF，复制到 `fonts/`；偏好只存字体 id，缺失自动回落 |
| 滚动日志 | PASS（代码） | Logback `logs/blancall.log` 10 MB × 5；`shared` 的 `Log.sink` 汇入；异常另写 `crash-<ts>.log` |
| 六语言词表 | PASS（自动化） | 五套非简中词表各 964 条 key 集合一致；新增 UI 文案均有英文词条（`I18nCoverageTest`） |
| 设置日期行布局 | PASS（离屏截图） | `render_settings_study.png`：修复日期字段吃掉整行宽度导致「考试日期」竖排；触发器 170–200dp，标题与说明正常横排 |
| MSI 打包链路 | 阻塞（环境） | `jpackage + WiX 3.14` 已写入 `tools/package-windows.ps1`；探针复现 `light.exe` `CreateTempDirectoryWithAce` 返回「客户端没有所需的特权」；普通用户会话可生成 |
| 便携/EXE 包 | PASS | `dist\Blancall-7.6.1-win-x64-setup.exe`（125.4 MB）与 `-portable.zip`（125.0 MB）；app image 启动冒烟通过 |

## 10. 2026-11-15 产物（0.7.0-win）

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `dist\Blancall-7.6.1-win-x64-setup.exe` | 125.4 MB | `70A7B6F0320CBD4BC913460C04A5F19A8161C41BB27BB5C1C27C257073A6660C` |
| `dist\Blancall-7.6.1-win-x64-portable.zip` | 125.0 MB | `F59583CA26A9CA89E3B65FF2D6DAC7DE9B6D3D71CEBDB3BF0C07C378FDF2530A` |
| `dist\Blancall-7.6.1-win-x64.msi` | — | 仅当 `light.exe` 可运行时生成；受限环境跳过 |

启动冒烟：app image 的 `Blancall.exe` 在隔离 `%LOCALAPPDATA%` 下启动成功（窗口标题 `Blancall`、`Responding=True`），
`logs\blancall.log` 写入 `startup: data=...`，验证 Logback 文件日志与共享日志 sink 均在运行态生效。

### 待真实会话点验

1. 设置 → 外观 → 界面语言切 English，确认导航/顶栏/设置行即时变英文。
2. 首页打开文章 → 详情页 →「阅读模式」进入全屏阅读 → Aa 打开 Inspector → Esc 关闭。
3. 断网/只读目录下导入文章，确认保存失败横幅常驻且列表不出现假文章。
4. 窄窗口与 125% / 150% / 200% 缩放下检查设置 master-detail 与阅读 Inspector 不裁切。
5. 阅读 → 打开遮挡 →「用当前遮挡开始练习」，确认生成的空位与挡片一致。
6. 拖入一个内容包 ZIP 与一个备份 ZIP，确认前者进素材库、后者只提示到设置页导入。
7. 托盘菜单「今日复习 / 设置」与设置 → 开机自启开关在普通用户会话点验。

