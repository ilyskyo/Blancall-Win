# Changelog

## 0.4.2-win — 2026-11-13

新增可双击安装的 EXE 安装包。

### 新增

- `tools/windows-installer.iss`：Inno Setup 6 安装脚本，当前用户安装（无需管理员），默认目录 `%LOCALAPPDATA%\Programs\Blancall`，可选桌面快捷方式、开始菜单项与「应用和功能」卸载入口；卸载不删除 `%LOCALAPPDATA%\Blancall` 学习数据。
- `tools/package-windows.ps1`：打包末尾自动调用 `ISCC.exe` 生成 `dist\Blancall-7.6.1-win-x64-setup.exe`；未安装 Inno Setup 时跳过并保留两个 ZIP。
- 安装包固定 `AppId`（与 Gradle `upgradeUuid` 一致），后续版本可原地升级；`ArchitecturesAllowed=x64compatible`。
- 应用图标改用 Android 版自有 `ic_launcher`，替换 Compose 默认的 Kotlin 图标；安装包、快捷方式、卸载程序、托盘与窗口标题栏统一使用 `desktop/src/main/resources/icon/blancall.{png,ico}`（16–256 共 8 个尺寸）。

### 验证

- 静默安装到测试目录：退出码 0，`Blancall.vbs`、jar、`runtime`、`ncnn.dll`、`hccr_en`、文档、`unins000.exe` 全部落盘。
- 从安装目录启动：进程窗口标题 `Blancall`，响应正常。
- 静默卸载：退出码 0，程序目录删除，`%LOCALAPPDATA%\Blancall` 保留。
- 当前构建环境限制未签名程序写开始菜单与 HKCU，因此快捷方式与卸载注册项使用「无快捷方式/无注册表」验证变体测试；实际安装包的这两步需在普通用户会话点验（README 已注明）。

## 0.4.1-win — 2026-11-13

分发合规调整：中文手写模型移出公开仓库。

### 变更

- 中文手写模型（`models/hccr`，基于 CASIA-HWDB，仅限非商业学术用途）不再随仓库与便携包分发；改由 GitHub Release 资产 `Blancall-7.6.1-handwriting-pack-win-x64.zip` 单独提供（含 `ncnn.dll`、中文模型、拉丁模型），用户手动解压到应用根目录。
- `.gitignore` 忽略 `models/hccr/`；`tools/package-windows.ps1` 新增 `-ModelDir` 参数，本地构建含中文模型的完整包时显式注入。
- 缺失中文模型时，练习页中文手写入口自动隐藏；拉丁手写与键盘输入不受影响。
- README、PRIVACY、THIRD-PARTY-LICENSES、PARITY 同步更新分发方式与许可边界（MIT 不覆盖模型权重）。

### 仓库

- 重建公开仓库，清除历史提交中的模型文件，避免通过旧提交下载受限制权重。

## 0.4.0-win — 2026-11-13

Phase 4 增量：句子卡、标签主页、PDF 预览、雷达图、历史下钻、动态多语言模板。

### 新增

- 句子卡：新增 `SentenceCardsScreen`，队列 = 今日句 + 到期句 + 全部未学新句；回忆面按「分句首字提示法」挖空（`SentenceCloze`），点「显示答案」揭晓；三键评级（忘记/不熟/记住了）写入句子级 FSRS 并显示间隔预览；支持全量复习、上一张/跳过、完成页评级分布；「遮挡提示」「首次也遮挡」偏好持久化到 `ui_prefs.properties`。首页「今日一句」与任务流、标签主页均可进入。
- 标签主页：新增 `TagHomeScreen`，展示标签下文章数、到期/暂停统计，提供跨文练习、单篇练习、句子卡与数据入口；标签列表整行可点击进入。
- PDF 导入预览：选择 PDF 后展示前 3 页缩略图、总页数与抽取字数，确认后再填入正文（`PdfPreview`）。
- 统计雷达图：六维错因构成（错别字/形近字/漏字/多字/顺序错/内容错），口径为「该类型错误数 ÷ 全部错因数」，Canvas 绘制网格、多边形与顶点；保留原有错误类型分布条。
- 练习历史下钻：点击单篇练习记录进入该篇统计；跨文练习弹出来源文章列表并可回到原文。
- 首页布局编辑：今日计划/今日一句/到期与计划/最近打开/最近文章可上移、下移、显示隐藏与恢复默认，布局持久化到 `ui_prefs.properties`（启动时读取并做未知项归一化）。
- 手写连写拆分：新增 `HandwritingSegmenter`（列投影 + 缝隙阈值 + 碎片合并），手写板支持「连写识别」——整行拆字后逐段 top-1 拼接；5 个 shared 单测覆盖空白/双字/部件窄缝/碎片/非法尺寸。
- 手写识别验证扩展：端到端测试覆盖 永/你/好/学（top-1 97.6%–99.8%）、拉丁 A/7（≈99.9%）与「你好」连写拆字。
- 规模预算测试：50,000 条记录 → 错题聚合 211ms；1,000 篇到期文章 → 今日计划 7ms（阈值 15s / 2s，防复杂度退化）。
- EPUB 导入：按 `META-INF/container.xml → OPF spine` 顺序抽取各章 XHTML 正文，解码 HTML 实体，路径 `..` 归一化并拒绝越界条目；文件对话框与拖拽均支持 `.epub`。
- 旧版 DOC 导入：引入 Apache POI HWPF 抽取 Word 97-2003 正文，替换原先「请另存为 .docx」的提示；测试样本取自 Apache POI test-data。
- 多语言动态模板：新增 `Strings.f("共 {0} 篇", n)` 占位符机制，改写 14 个界面文件约 120 处运行时拼接文案；5 套语言各补齐 163 条模板译文。顺带修复 `java.util.Properties` 把键中空格当分隔符、导致含空格键（如 `Blancall Windows · 无 AI 本地版`、`共 {0} 篇`）整条失效的隐囚 bug——改为仅按首个 `=` 分割的自定义解析器。

### 验证

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :shared:test :desktop:test
```

BUILD SUCCESSFUL；desktop 29 项测试（含 PDF 预览、EPUB spine 抽取、DOC 旧格式、六语言模板填参/兜底/收录守卫、首页布局持久化、手写识别 4 例）、shared 全量测试（含 `HandwritingSegmenterTest` 5 例、`ScaleBudgetTest` 2 例）通过。

### 已知限制

- 手写：压感、标点手势、跨设备同笔迹一致率对照未做（连写整行拆分已完成）。
- MSI 在当前受限令牌环境仍无法构建；便携 ZIP + 用户级安装脚本可用。
- 真实 Android 备份端到端回归、1,000 篇/5 万条性能与 DPI 矩阵需真实桌面会话验收。

## 0.3.0-win — 2026-11-13

Phase 3 收尾：六语言、遮挡/标注、自定义挖空、跨文与集训、桌面体验、打包。

### 新增

- 六语言：6 套资源（简中/繁中/英/日/韩/俄，各 336 条核心文案）+ 运行时 `Strings` + `LocalizedText` 包装（`Text("…")` 自动本地化）+ 设置页即时切换 + `StringsParityTest` 守卫。
- 阅读：背诵遮挡（移植 `ReaderOcclusion` 三种粒度，点按揭示/遮回、全部显示、读取自定义遮挡配置）、划线与批注（鼠标拖选、划线/批注/存为挖空、改/删、正文指纹失配提示）、阅读时长与剩余时间、段落首行缩进、背景风格（米黄/白/灰）、界面字号缩放 85%–130%。
- 练习：自定义挖空（读取 Android `custom_cloze.json` 配置并练习）、选区保存为自定义挖空、跨文混合练习（列表多选 ≥2 篇，逐空带来源文章、按篇写回 FSRS）、错题集训（错题本一键定位并练习）、撤销/重做（`Ctrl+Z/Y`）。
- 手写（Phase 5）：NCNN CPU 离线推理（JNA 调用 `ncnn.dll`）、`preprocess.c` 逐行移植到 Kotlin、中文 7356 类 + 英文/数字 47 类、练习页手写板与候选点选；模型与 `ncnn.dll` 随包（`models/`），缺失时入口自动隐藏。端到端测试：渲染「永」→ top-1 永 97.6%。
- 组织：复习暂停/恢复、最近打开、剪贴板导入、文件拖拽导入、原生菜单栏（文件/查看/帮助）。
- 导出：练习记录 CSV、学习分享图 PNG、诊断包（环境+日志+偏好摘要+数据清单，不含正文）、复制正文到剪贴板。
- 系统：首启欢迎页（可重播）、帮助与引导、`PRIVACY.md`、打包脚本 `tools/package-windows.ps1` 与用户级安装脚本 `tools/install-portable.ps1`、MSI per-user 安装配置。
- 发布产物：`dist\Blancall-7.6.1-win-x64-portable.zip`（127.7 MB，含手写模型与精简 JRE）、`dist\Blancall-7.6.1-win-x64-portable-lite.zip`（100.5 MB，不含 models）；均用安装脚本装到 `%LOCALAPPDATA%\Programs\Blancall` 并验证启动。
- 提醒：复习模板与提醒设置、托盘气泡（无托盘时降级为应用内提示）。

### 验证

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :shared:test :desktop:test
```

BUILD SUCCESSFUL；新增 `StringsParityTest`（5 套 catalog key 一致、≥300 条、切换/回落）、`ReaderOcclusionTest`（6 例）、跨文练习记录与复习模板测试。

### 已知限制

- 托盘气泡/睡眠唤醒补发、DPI 矩阵、内容包拖拽导入仍需真实桌面会话手工验收。
- 运行时拼接的文案（含变量）未进入 catalog，非简中界面下按中文兜底；后续按模板键补齐。
- 手写：压感、连写自动拆分、标点手势、跨设备同笔迹一致率对照未做；完整包 127.7 MB 略超 120 MB 预算（模型 40 MB）。

## 0.2.0-win — 2026-11-13

Phase 3 parity 增量：提醒、今日计划、错题本、统计看板、内容包、标签筛选、练习提示。

### 新增

- 学习提醒：桌面 `ReminderScheduler`（复用 `ReminderPlan`/`ReminderSchedule`/`ReminderStateStore`），托盘气泡 + 应用内提示；设置页支持开关、时间、频率预设/自定义提醒日、每日目标、免打扰、测试提醒、下次提醒时间；提醒日与连续天数落盘 `settings/reminder.properties`，考试目标镜像 `data/exam_goal.json`（进备份）。
- 复习模板：sprint/standard/deep 会实际配置 FSRS 目标留存率（90/95/85），启动时应用。
- 首页：改用 `StudyCoach` 统一取数，新增今日目标进度、今日计划（最多 3 条：续练/复习/开新篇/薄弱/错题/句子卡/补次数）、今日一句卡、到期与计划列表、连续学习。
- 统计页：学习日历（12 周热力图）、单篇记忆热力图（句级留存率 + 图例）、需加强文章、错题本（按句聚合、只看未攻克、错误/答案/原篇入口）、错误类型分布、成就（14 枚，派生 + 解锁时刻）、练习记录 CSV 导出（UTF-8 BOM）。
- 内容包：设置 → 内容管理 → 内容包；导入 zip（txt/md + 可选 manifest）、查看篇目、整包/逐篇导入为文章、删除。
- 文章列表：标签筛选与文章标签展示。
- 练习：两级提示（字数弱提示 + 首字强提示）、10 秒无输入自动淡显、Enter 提交；提示次数写入练习记录。
- 数据层：`ArticleRepository.snapshot`（提醒等非收集型消费者取当前内存快照）。

### 验证

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :shared:test :desktop:test
```

BUILD SUCCESSFUL；新增 `ReminderPrefsTest`（默认值/落盘/自定义提醒日/考试目标镜像/连续天数）与复习模板配置测试。

### 已知限制

- 托盘气泡与睡眠唤醒补发、内容包拖拽导入、每日一句翻面/评级、衰减曲线、雷达图、分享图未完成。
- 六语言、手写、MSI/便携包仍未开始。

## 0.1.0-win — 2026-11-12

首个可运行的 Windows MVP（基于 `Blancall-Std 7.6.1-NoAI / vc54`）。

### 新增

- Gradle 工程：`shared`（纯 JVM）+ `desktop`（Compose Multiplatform Desktop），Kotlin 2.2.20 / Compose 1.10.3 / Gradle 8.14.3 / JDK 21。
- 迁移 `algorithm/` 58 个文件（50 A、4 B、4 C 暂缓）；迁移 `data/` 29 个文件与 `util/AtomicFiles`；`android.util.Log` 统一替换为 `platform.Log`。
- 共享测试：移植 96 个测试文件，`shared:test` 全绿（约 790+ 用例）。
- 数据：`article_id_seq` 语义保持；新增 `record_id_seq`（记录 id 不复用）；备份 ZIP v1 导入/导出；导入前预备份；每日自动备份。
- 备份安全加固：拒绝 `.` 段、Windows 保留名、尾点、大小写冲突、重复条目；条目数上限 10,000。
- 桌面 UI：首页、文章列表/搜索/编辑/删除、标签管理/绑定、导入（粘贴/TXT/MD/PDF/DOCX/HTML/RTF）、阅读器、三种练习模式、统计、设置。
- 练习：`AnswerChecker` 判分、练习记录落库、文章级 + 句子级 FSRS 更新、断点续练。
- 导出：PDF 试卷（嵌入霞鹜文楷 OFL 字体 + 答案页）。
- 系统：单实例、窗口几何记忆、关闭询问（托盘/退出/记住）、托盘、`Ctrl+N/O/F/,`、`Esc`、`F11` 快捷键、崩溃日志。
- 文档：`docs/win/` 五份 Phase 0 文档 + `DATA-FORMAT.md`、`UPSTREAM.md`、`ACCEPTANCE.md`、`README.md`。

### 已知限制

- 手写、六语言 UI、提示、遮挡/批注、每日一句、热力图、成就、内容包 UI、素材库、提醒调度、MSI 打包未实现。
- 未做真实 Android 备份的端到端回归（需要用户提供脱敏数据）。
- PDF 只做文本排版，不做原 PDF 预览。

### 验证

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :shared:test :desktop:test
```

均 BUILD SUCCESSFUL。
