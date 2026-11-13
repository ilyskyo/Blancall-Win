# Blancall Windows 架构设计（Phase 0）

- 目标版本：与 Android `Blancall-Std 7.6.1-NoAI / vc54` 功能对等、数据互通
- 产品范围：**只做无 AI 版**；无网络请求、无 INTERNET 能力、无遥测
- 技术路线：Kotlin Multiplatform 的 JVM 侧 + Compose Multiplatform Desktop
- 文档状态：Phase 0 提案；ADR 见 §16
- 参考：`DEPENDENCY-AUDIT.md`、`PARITY.md`、`RISKS.md`

> **实现状态说明（2026-11-13）**：本文是目标架构。当前代码已完成数据/算法/备份/PDF 导出/导入（含 PDF 预览与 EPUB）、
> 六语言（含动态模板）、手写（NCNN + 连写拆字）、提醒调度、内容包 UI、句子卡、标签主页、统计雷达与历史下钻、
> 首页布局编辑、便携包；MSI 在受限环境仍为计划项。实际进度以 `README.md` 与 `ACCEPTANCE.md` 为准。

## 1. 目标与非目标

### 1.1 目标

1. **数据零丢失、可回迁**：直接读写 Android `filesDir` 的 JSON/JSONL 格式；导入/导出备份 ZIP v1；Windows → Android 可恢复。
2. **算法对等**：FSRS-6、判分、错因、挖空选择、统计口径、成就判定与 `Blancall-Std` 一致；优先复用纯 Kotlin 代码与单测。
3. **纯本地**：无账号、无网络请求、无遥测、无崩溃上报、无广告；安装包不声明任何联网能力。
4. **发布级桌面体验**：DPI 感知、键鼠优先、可缩放窗口、深色/浅色、单实例、崩溃可恢复、MSI + 便携版。
5. **可长期维护**：与 Android 上游保持可追踪的同步关系，而不是一次性分叉。

### 1.2 非目标

- 不做 AI 对话、AI 挖空、联网搜索、AI 训练分析（用户决定：只做无 AI 版）。
- 不做 Electron/Tauri/WebView 套壳；不打包 Android 模拟器。
- 不 1:1 复刻手机手势与液态玻璃折射（AGSL）；桌面用静态半透明/可选 Mica 近似。
- 不在首版复刻 Android 16 Live Updates、桌面小组件、Material You 取色。
- 不改动 Android 两个工程的任何文件。

## 2. 关键决策（ADR 摘要）

| ADR | 决策 | 状态 |
|---|---|---|
| 001 | 采用 Compose Multiplatform Desktop（Kotlin/JVM），不选 WinUI3/C# | 已确认 |
| 002 | 复用 Android 包名与源码结构；桌面专属代码放 `com.ilyskyo.blancall.desktop` | 已定 |
| 003 | `shared` 仅依赖纯 JVM；**移除** `Article` 的 Compose `@Immutable` 注解（不引 Compose runtime） | 已实现 |
| 004 | 新增 `record_id_seq` 纯文本游标（不写入备份、Android 忽略） | 已定 |
| 005 | 备份导入前自动生成 Windows 本地预备份（比 Android 更安全） | 已定 |
| 006 | 备份安全校验比 Android 更严（`.` 段、保留名、大小写冲突、重复条目） | 已定 |
| 007 | PDF 读/写用 PDFBox 3.x（预览列为后续项）；PNG 分享图用 Skia | 部分实现 |
| 008 | 手写用 NCNN desktop + 原样 `preprocess.c` + C ABI + JNA；中文模型**随包分发** | 已确认（Phase 5 尚未实施） |
| 009 | 内置素材（philo/gaokao）改为可选内容包，不默认捆绑 | 已定 |
| 010 | i18n 由 Android `strings.xml` 构建期生成 JSON；运行时自有 formatter + plurals | 已定 |
| 011 | 提醒采用托盘常驻自调度；应用未运行不提醒并在 UI 明示 | 已定 |
| 012 | 点击关闭按钮**每次询问**（最小化到托盘/退出/记住选择） | 已确认 |
| 013 | Windows 端记录 id 不复用（修复 Android 隐患），文件格式不变 | 已定 |
| 014 | 不实现任何 AI/联网能力；无凭据存储、无 HTTP 客户端 | 已确认 |
| 015 | 单实例用文件锁 + 本地回环激活 | 已定 |
| 016 | 视觉用静态玻璃/模糊近似，不做实时折射 | 已定 |
| 017 | 界面默认字体 Microsoft YaHei UI；阅读器/PDF 嵌入 OFL 字体 | 已定 |
| 018 | 版本号沿用 `7.6.1` 并加 `-win.N` 后缀，独立版本节奏 | 已定 |
| 019 | 首启保留 `WelcomeScreen` + 5 页引导 + 隐私政策（与 Std 一致） | 已定 |
| 020 | 备份只支持 7 个类目（无 AI）；导入 Pro 备份时忽略 `ai` 类目 | 已定 |

## 3. 技术栈与版本锁定

| 项 | 选择 | 说明 |
|---|---|---|
| 语言 | Kotlin 2.2.20 | 与 Android 源码兼容；算法/数据可原样编译 |
| UI | Compose Multiplatform Desktop 1.10.3 | 已验证可编译运行 |
| JVM | Temurin JDK 21 LTS | 运行与 jpackage；本机 Android Studio JBR 21.0.10 有 `jlink` 但**无 `jpackage`**，打包需另装 Temurin |
| 构建 | Gradle Wrapper 8.14.3 | Kotlin 2.2.20 插件与 Gradle 9.x 不兼容，故用 8.14.3 |
| JSON | `org.json:json:20240303` | 与 Android 单测同版本，最接近 AOSP `org.json` |
| 文件 IO | `java.nio` + 临时文件 + 原子 rename | 对齐 `AtomicFiles` |
| PDF | Apache PDFBox 3.0.x | 读文本、渲染预览、写试卷 |
| Word | 自研 DOCX（zip+XML）；`.doc` 用 POI HWPF（Phase 3） | 减少依赖；`.doc` 首版明确提示不支持 |
| 网络 | **无** | 不引入 Ktor/OkHttp；不声明 INTERNET |
| 本地加密 | **无** | 无 AI Key、无凭据 |
| 通知 | 托盘 `Shell_NotifyIcon` 气泡；Phase 6 可选 AUMID Toast | 未注册 AUMID 不崩 |
| 原生推理 | NCNN desktop + MSVC + C ABI + JNA | x64 first；arm64 视上游 |
| 测试 | JUnit 5 + kotlinx-coroutines-test + Compose UI Test | 表驱动 + golden |
| 打包 | jpackage app-image + WiX 3.14 MSI + 便携 ZIP | per-user 安装，卸载不删数据 |

> 版本策略：`gradle/libs.versions.toml` 是唯一版本源；升级依赖必须单独提交并附兼容测试结论。

## 4. 模块与目录

```
Blancall-Win/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── shared/                                  # 纯 Kotlin/JVM，无 UI、无 Android、无网络
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/ilyskyo/blancall/
│       ├── algorithm/                       # 58 个文件（A/B）
│       ├── model/                           # Article / PracticeRecord / PracticeState / Tag
│       ├── store/                           # AtomicFiles + repository/*
│       ├── backup/                          # BackupManager / LearningDataReloader
│       ├── contentpack/
│       ├── importer/                        # FileTextExtractor 纯解析 + PDF/DOCX 适配
│       ├── notification/                    # ReminderPlan / ReminderSchedule / ReminderStateStore
│       └── platform/                        # 接口：Log / Clock / Paths / Notifier / FileDialog
├── desktop/                                 # Compose Desktop 应用
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/com/ilyskyo/blancall/desktop/
│       │   ├── Main.kt
│       │   ├── app/                         # AppState / Navigator / 依赖装配
│       │   ├── ui/                          # theme / components / screens
│       │   ├── platform/                    # Win32 / 托盘 / 单实例 / 对话框
│       │   └── handwriting/                 # 书写板 / 切分 / NcnnBridge
│       └── resources/
│           ├── i18n/                        # 生成的 6 套 JSON
│           ├── fonts/
│           ├── icons/
│           ├── PRIVACY.md
│           └── assets/                      # 手写模型随包；素材库内容包可选
├── native/                                  # Phase 5
│   ├── CMakeLists.txt
│   ├── include/hccr.h
│   └── src/hccr.cpp                         # C ABI：create/destroy/recognize/recognizeLatin
├── packaging/
│   ├── windows/                             # jpackage 选项、WiX、AUMID
│   └── portable/
├── tools/
│   ├── sync-from-android.ps1                # 上游同步 + 确定性变换
│   ├── gen-i18n.ps1                         # strings.xml -> i18n JSON
│   └── parity/                              # golden 对比工具
├── docs/win/
├── README.md
└── CHANGELOG.md
```

### 4.1 依赖方向规则

```
desktop ──> shared ──> (JDK + org.json + coroutines)
   │
   └──> native (JNA, 仅 Phase 5)
```

- `shared` 的 Gradle 脚本不得出现 `com.android.*`、`org.jetbrains.compose`（Compose runtime 注解除外，见 ADR-003）。
- `shared` 不依赖 `desktop`；`desktop` 不直接读文件，全部经 `shared` 的 Store。
- `native` 只暴露 C ABI，不认识 Kotlin。
- **没有任何模块依赖 HTTP 客户端。**

### 4.2 包名策略

复用 `com.ilyskyo.blancall.{algorithm,model,data,util}`，使 Android 文件复制后 **import 几乎不变**；桌面专属实现放 `com.ilyskyo.blancall.desktop.*`。好处：

1. 上游 `algorithm/` 的改动可以用逐文件 diff 方式同步；
2. 移植测试的 import 不需要批量重写；
3. 代码搜索时能直接对照 Android 同名类。

## 5. 上游同步策略

Android 两个仓库会继续演进（当前 7.6.1 / vc54）。一次性复制会在三个版本后分叉。因此：

1. `tools/sync-from-android.ps1` 按文件清单从 `D:\Blancall\Blancall-Std` 复制 `algorithm/`、`data/model`、`data/repository`、`data/backup`、`data/contentpack`、`notification/Reminder*`、`util/AtomicFiles.kt` 到 `shared/`。
2. 复制后应用**确定性变换**（记录在 `tools/sync-transforms.md`）：
   - `import android.util.Log` → `import com.ilyskyo.blancall.platform.Log`
   - `ReaderPrefsStore` 的 `Context` → `File`
   - `FsrsStateStore` 的 `androidx.annotation.VisibleForTesting` → 删除
   - `Article` 的 `androidx.compose.runtime.Immutable` → 保留（见 ADR-003）
   - 5 处 `lowercase()` / `format()` → `Locale.ROOT`
3. 每次同步后跑 `gradlew :shared:test` + golden 对比；diff 必须逐条 review，禁止「全量覆盖」。
4. 同步基线写入 `docs/win/UPSTREAM.md`（Android commit hash + 版本号 + 日期）。

> 不在 Android 工程里加任何「为桌面准备的抽象」——那会违反只读边界。Windows 只跟 `Blancall-Std`，不跟 Pro 的 AI 分支。

## 6. 数据层设计

### 6.1 Windows 目录布局

```
%LOCALAPPDATA%\Blancall\
├── data\                    # 等价于 Android filesDir
│   ├── articles.json
│   ├── article_id_seq
│   ├── record_id_seq         # 新增，纯文本，Android 忽略
│   ├── records.json
│   ├── fsrs_state.json
│   ├── fsrs_reviews.jsonl
│   ├── custom_cloze.json
│   ├── mask_config.json
│   ├── home_layout.json
│   ├── sentence_card.json
│   ├── reader_prefs.json
│   ├── reading_marks.json
│   ├── tags.json
│   ├── achievements.json
│   ├── exam_goal.json
│   ├── appearance.json
│   ├── reminder_state.json
│   ├── practice_state_<articleId>.json
│   ├── ink\<recordId>.json
│   └── content_packs\<packId>\...
├── backups\                 # 手动导出 + 导入前预备份 + 自动备份
├── logs\                    # 滚动日志、崩溃日志
├── models\                  # 手写模型（随包分发，首次解包）
├── cache\                   # PDF 渲染缓存、缩略图
└── settings\
    ├── window.json           # 窗口位置/大小/显示器
    └── ui_prefs.json         # Android SharedPreferences 的桌面镜像
```

- 路径来自 `%LOCALAPPDATA%`（`SHGetKnownFolderPath` 或环境变量）；支持长路径（manifest `longPathAware`，必要时 `\\?\` 前缀）。
- `data\` 下文件必须与 Android 同名、同格式；任何新增文件（`record_id_seq`）都不得让 Android 误判为数据文件。
- **没有** `ai_history/`、`ai_analysis/`、`secrets.json`。

### 6.2 原子写与并发

移植 `AtomicFiles`，并补 Windows 语义：

1. 同目录唯一临时名 `<file>.<uuid8>.tmp`，写完 `FileChannel.force(true)`。
2. `.bak` 轮换：优先 `Files.createLink`（NTFS 硬链接），失败退回复制；复制前先断开旧硬链接。
3. `Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING)`；失败退回 `File.renameTo`，再失败退回复制。
4. 目录 fsync 在 Windows 不可用：静默跳过，并在文档中声明「rename 原子，但目录元数据无 fsync」。
5. **跨进程锁**：`data\.lock` 上 `FileChannel.tryLock`；单实例策略是第二道防线。
6. 追加写（`records.jsonl` 语义）保持「一行一次 write + force」，崩溃最多半行，读侧跳过坏行。
7. 每个 Store 保持 Android 的内存锁语义（`Mutex`/`synchronized`）；桌面不引入新的写线程池。

### 6.3 JSON 兼容规则

| 规则 | 要求 |
|---|---|
| 编码 | 读：先剥 UTF-8 BOM；写：UTF-8 **无 BOM** |
| 换行 | JSONL 只用 `\n`；写文件末尾保留一个换行；不写 CRLF |
| 库 | `org.json:json:20240303`，禁止 `kotlinx.serialization` 直接读写既有文件 |
| 数字 | `similarity`/`fontPx`/`uiFontScale` 保持 `Float.toDouble()` 语义；不要按十进制字符串比较 |
| 稀疏字段 | 保持 Android 的「空列表不写键」「`-1` 哨兵不写」规则，不补全 |
| 键顺序 | 不承诺字节级完全相等；对确定性有要求的 `tags.links` 复刻排序 |
| 未知字段 | 读取时忽略；写回时若版本更高则拒写（`StoreVersion` 语义） |
| 时间 | epoch millis 原样；本地日期 `yyyy-MM-dd` 用系统时区；`exam_goal` 用本地 epochDay |

### 6.4 id 游标

| 对象 | Android | Windows |
|---|---|---|
| `Article.id` | `article_id_seq` 持久游标，删除不回收 | 相同；所有写入路径（新增/导入/内容包/备份恢复后）都维护 |
| `PracticeRecord.id` | 内存 `max+1`，删除最大 id 后重启会复用 | 新增 `record_id_seq`：`next = max(next, cursor+1, maxId+1)`；删除不回退 |
| `Tag` / 配置 / 标注 id | 表内 `max+1` | 相同 |
| 内容包 id | 毫秒时间戳 | 毫秒 + 同毫秒序号，保证唯一；目录名仍为纯数字 |

> `record_id_seq` 不写入备份：恢复后若游标低于备份中的最大 id，用 `maxId+1` 兜底即可，语义与 Android 一致且不会错挂墨迹。

### 6.5 损坏恢复

| 文件 | 策略 |
|---|---|
| `articles.json` / `records.json` | 与 Android 一致：坏条/坏行跳过，整文件坏回读 `.bak`；两者都坏则空起步。**Windows 额外**在首次覆盖前写 `<file>.corrupt-<ts>` 副本 |
| `fsrs_state.json` / `tags.json` / `custom_cloze.json` / `mask_config.json` / `home_layout.json` / `reading_marks.json` / `reader_prefs.json` / `achievements.json` / `reminder_state.json` | 与 Android 一致：整文件坏 → `.corrupt-*` + 回读 `.bak`；盘上版本更高 → 拒写 |
| `sentence_card.json` | 可再生；损坏后重抽 |
| `practice_state_*.json` / `ink/*.json` / `pack.json` | 单文件损坏 → 忽略该文件，不影响其他 |

恢复流程绝不静默删除用户数据；`data\.corrupt\` 留存副本，设置页提供「查看损坏文件」。

### 6.6 备份导入/导出

#### 导出

- 格式与 Android 完全一致：`manifest.json` + `files/`；`formatVersion=1`；`groups` 为类目 key 数组；`files` 为条目名数组。
- 类目（**7 个，无 AI**）：`articles` / `progress` / `marks` / `organize` / `ink` / `prefs` / `session`。
- 压缩：ZIP DEFLATE；只写文件条目；路径分隔符 `/`。
- 默认文件名 `blancall_backup_yyyyMMdd_HHmm.zip`。

#### 导入

```
选择 ZIP
  → 校验版本 == 1
  → 解析 manifest（groups/files）
  → 预检全部条目（路径、大小、数量、重复、大小写冲突、保留名）
  → 在 backups\pre-import_<ts>.zip 生成当前数据预备份
  → 逐条写 <name>.import（fsync）
  → 逐条 promoteStaged（.bak 轮换 + 原子替换）
  → 失败则用 .bak 回滚已替换文件，删除新增文件
  → 按类目清理孤儿
  → reloadAll()
  → 报告 restored / removed / groups
```

**比 Android 更严的校验**（不改变格式，只拒绝危险包）：

1. 拒绝 `.`、`..`、空段、绝对路径、盘符、反斜杠、NUL。
2. 拒绝 Windows 保留名（`CON`、`NUL`、`COM1`…）、尾点/尾空格。
3. 大小写折叠后冲突检测（`A.json` 与 `a.json`）。
4. 重复条目检测。
5. 条目数量上限（默认 10,000），单条 32 MiB，总量 128 MiB。
6. `keep` 集合改为「manifest 中所有条目」而不是仅 `files/` 前缀，避免把真实文件当孤儿删掉。
7. 旧备份无 `groups` 时按全选处理（与 Android 一致），但 UI 明确警告会覆盖本机同类目数据。
8. **Pro 备份的 `ai` 类目**：未知类目忽略，`ai_history` 条目不写入；不因它触发任何清理。

**不做的事**：不把 `article_id_seq`、`fsrs_reviews.jsonl`、`reminder_state.json`、`record_id_seq` 放进备份（与 Android 一致）；不合并同类目文件，按类目整类替换。

### 6.7 内容包

- 复用 `ContentPackStore` 的导入/卸载语义：`.tmp-<id>` 暂存 + 目录改名提交；条目上限 500、单条 2 MiB、总量 20 MiB。
- ZIP 内 `manifest.json`（`format=blancall-pack`）与 Android 相同。
- `chars` 用 Kotlin `String.length`（UTF-16 代码单元），不按字节重算。
- 删除内容包不影响已导入的文章（与 Android 一致）。

## 7. 算法层设计

### 7.1 不变量

1. **FSRS-6**：`due=0` 表示无排期，不是 1970 年到期；同日复习判定按本地日历日；评级推进公式不改。
2. **判分**：`AnswerChecker` 是唯一产出 `CORRECT` 的地方；标点容错、全半角、大小写、数字小数点规则不改。
3. **挖空生成**：句子/字词/反向默写三套生成器与策略权重不改；自定义挖空按坐标切片。
4. **统计口径**：`DayBucket` 是「天」的唯一入口；`TextStatistics` 是字数口径。
5. **成就**：`AchievementManager` 的判定条件不改。

### 7.2 允许的改动（必须证明等价）

| 改动 | 理由 | 证明方式 |
|---|---|---|
| `lowercase()` → `lowercase(Locale.ROOT)`（5 处） | 土耳其语等 locale 会破坏判分/指纹 | 在 `zh_CN`/`en_US`/`tr_TR` 三组 locale 下跑同一批测试；结果应与 Android 在 `zh_CN` 下一致 |
| `FSRS.review` / `ForgettingPredictor` 增加 `zone` 参数（默认 `systemDefault()`） | 让测试与跨时区行为可注入 | 默认值路径的输出与 Android 逐字段相等；新增 UTC 固定测试 |
| `EbbinghausScheduler` 的 `Calendar` → `java.time` | 避免 DST 的 23/25 小时「天」 | 对同一输入对照 Android 的 `nextReviewTime`；不一致则只在文档记录，不改默认 |
| `FSRS` 的随机 fuzz 注入 `Random`（默认 `Random.Default`） | 让 golden 测试可复现 | 默认路径分布不变；种子路径仅测试使用 |

> 任何算法改动都必须先在 `tools/parity` 里生成「同一输入 → Android 输出」的基准表，Windows 侧逐行比对；不得只凭单测通过就宣称对齐。

## 8. UI 架构

### 8.1 导航

- 保留 Android 的路由语义，集中定义在 `desktop/app/Routes.kt`（31 条 NavHost 路由 + 首启欢迎/引导）：

```
home / list / overview / settings / import / reader/{id} / practice/{id}?...
custom_cloze_list/{id} / custom_cloze_edit/{id} / cross/{ids}
today_plan / due_list / sentence_cards / statistics/{id} / mistakes
search / tag_manager / tag/{id} / practice_history / content_packs
data_interchange / guide / help / onboarding / philo / pdf_preview
welcome（首启，NavHost 外）
```

- 自研 `Navigator`：`ArrayDeque<Route>` + `StateFlow`；`Esc` 返回上一层；根页 `Esc` 不退出（与 Android 根页返回=退出不同，桌面更安全）。
- 顶部提供面包屑/返回按钮；所有路由有可见入口，快捷键只是加速。
- 窗口状态（位置/大小/最大化/所在显示器）持久化到 `settings/window.json`；启动时夹到可见工作区。

### 8.2 布局

| 宽度 | 结构 |
|---|---|
| ≥ 1100 dp | NavigationRail + 列表/详情双栏；阅读器可全宽；练习单栏居中 |
| 840–1100 dp | NavigationRail + 单栏，列表进入详情页 |
| < 840 dp | 底部导航（首页/文章/数据/设置，素材库条件显示） |

- 默认窗口 1280×800，最小 960×640；多显示器记忆位置，DPI 变化时重新计算布局。
- 弹层：设置/模式选择/标签选择用 Dialog 或右侧 Inspector；不使用手机底部 Sheet。
- 首页：搜索框 + 今日计划卡 + 卡片画布（2 列，可拖拽/缩放/置顶/删除）；桌面增加「最近打开」和「快捷开始」。
- 练习：单栏大输入区；顶部进度与模式；右侧可折叠提示/统计；`Enter` 提交、`Space` 翻面/下一张、`←/→` 切题。
- 阅读：正文最大宽度约 720–820 dp，居中；右侧 Inspector 放字号/行距/字体/遮挡/标注；`F11` 全屏沉浸。
- 设置：独立页面 + 左侧分组（外观/阅读/练习/提醒/数据/拓展/关于），搜索设置项（`Ctrl+,` 打开）。
- 首启：欢迎页（离线说明/隐私政策/支持开发者）→ 5 页引导 → 首页；与 `Blancall-Std` 的 `WelcomeScreen`/`OnboardingScreen` 对应，但不复用 Compose Android 代码。

### 8.3 状态管理

- 不引 AndroidX ViewModel；用普通类 + `MutableStateFlow`：
  - `AppState`：全局数据快照（文章、标签、设置、当前路由）
  - `PracticeController`：一次练习的题目、答案、提示、计时、提交
  - `ReaderController`：阅读位置、字号、遮挡、标注
  - `StatisticsController`：筛选、聚合
- 所有 Store 操作在 `Dispatchers.IO`；UI 通过 `collectAsState` 订阅；主线程用 `Dispatchers.Swing`（`kotlinx-coroutines-swing`）。
- 撤销/重做：练习答案与文章编辑各维护 `UndoStack<T>`（快照，最多 50 步）；`Ctrl+Z`/`Ctrl+Y`。
- 一次性事件（Toast、文件对话框结果）用 `Channel`/`SharedFlow`，不用全局可变单例。

### 8.4 主题与视觉

- 设计令牌从 `ui/theme/{Color,Dimens,Type,Macaron}.kt` 翻译为 `DesktopTheme`；保留品牌色、圆角、留白与「安静」气质。
- 明/暗主题 + 强调色预设 + 自定义色；不复刻 Material You 取色。
- 玻璃效果：卡片/导航用静态半透明 + 细边框 + 顶部高光；Windows 11 可选 `DwmSetWindowAttribute` 开启 Mica/Acrylic（JNA），关闭时纯色兜底。**不做实时背景采样/折射**。
- 动效：保留页面转场、卡片按压、评级飞出；统一 `MotionSpec`，可在设置里降低动效（尊重系统「减少动画」）。
- 图标：复用 Android 的矢量语义，重绘为 Compose `ImageVector` 或 SVG→Painter。

### 8.5 键鼠、触控、IME

| 输入 | 设计 |
|---|---|
| 鼠标 | 悬停高亮、右键上下文菜单、滚轮滚动、双击打开、拖拽导入文件、框选文本 |
| 键盘 | `Ctrl+N/O/F/S/Z/Y`、`Esc`、`Enter`、`Space`、`←/→`、`F11`、`Ctrl+,`；全部有菜单/按钮入口 |
| 触控板 | 双指滚动；`Ctrl+滚轮`/捏合缩放阅读字号；不与页面滚动冲突 |
| 手写笔 | Compose Desktop 指针事件 + 压感（若可用）；无压感时用 `InkWidthSim` 回退；保留笔间时间戳供切分 |
| 中文 IME | Compose Desktop 文本框；Phase 2 用 Microsoft Pinyin 实测；若 IME 组合串异常，降级为 `SwingPanel` + `JTextField`（待验证） |

### 8.6 多语言

- `Blancall-Std` 共 6 套资源，每套 **1,583 条 string + 73 条 plurals = 1,656 条**，合计 9,936 条。
- 构建期 `tools/gen-i18n.ps1` 读取 6 套 `strings.xml`，生成 `desktop/src/main/resources/i18n/{zh-Hans,zh-Hant,en,ja,ko,ru}.json`：
  - `strings`：key → 原文（保留 `%1$s` 等占位符）
  - `plurals`：key → quantity → 原文
- 运行时 `Strings` 对象：`format(key, args)` 用 `String.format(Locale.ROOT, ...)`；复数按语言规则选择（zh/ja/ko 只用 `other`；en 用 `one/other`；ru 用 `one/few/many/other`）。
- 语言切换即时生效，持久化到 `ui_prefs.json`；默认跟随系统，系统语言不在 6 套内时用简中。
- 守卫测试（移植 `LocaleParityGuardTest`）：6 套 key 集合相等、占位符一致、非中文语言无中文残留、繁简互斥、plurals 量档齐全。
- 硬编码中文棘轮：只扫描 `desktop/src/main/kotlin` 的字符串字面量；素材正文/隐私政策进白名单，其余必须进资源。
- 日期/数字格式按语言，但统计口径仍用 `java.time` 的本地日期。

### 8.7 字体

- UI 默认：`Microsoft YaHei UI`（Windows 自带），缺失时回落 `Segoe UI`/系统默认。
- 阅读器：内置 `LXGW WenKai Regular`（OFL 1.1，Android 已有），另列系统字体（微软雅黑、宋体、黑体、等线）。
- 字体导入：用户选择 `.ttf/.otf` 复制到 `%LOCALAPPDATA%\Blancall\fonts\`，校验可解析后加入列表。
- PDF 导出：用 `LXGW WenKai Regular` 或 `Noto Sans SC`（OFL）嵌入子集；不依赖系统字体。

## 9. 平台适配层

| 能力 | 实现 |
|---|---|
| 单实例 | `%LOCALAPPDATA%\Blancall\.instance.lock` 文件锁；第二实例通过本地回环端口发送 `activate` 参数后退出 |
| 窗口 | Compose `Window`；保存位置/大小；DPI 变化监听；最小尺寸 960×640 |
| 关闭按钮 | **每次询问**：Dialog 三选一「最小化到托盘 / 退出 / 记住我的选择」；记住后写入 `ui_prefs.json`（`close_action`），设置页可改回「每次询问」 |
| 托盘 | AWT `SystemTray`；菜单：打开 Blancall / 今日复习 / 设置 / 退出；双击打开主窗 |
| 通知 | `Shell_NotifyIcon` 气泡（NIF_INFO）；Phase 6 可选注册 AUMID 的 Toast；失败静默降级 |
| 文件对话框 | AWT `FileDialog`（原生）优先；需要现代化体验时换 JNA `IFileOpenDialog` |
| 拖拽导入 | Compose `onExternalDrag`；接受 `.txt/.md/.pdf/.docx/.doc/.epub/.html/.rtf/.zip` |
| 剪贴板 | `Toolkit.getDefaultToolkit().systemClipboard`；分享文本=复制 |
| 打开所在文件夹 | `explorer.exe /select,<path>`；打开数据目录用 `Desktop.open` |
| 开机自启 | HKCU `Software\Microsoft\Windows\CurrentVersion\Run`；默认关闭；设置页可开关 |
| 日志 | Logback 滚动文件（`logs/blancall.log`，10 MB × 5）；崩溃处理器写 `logs/crash-<ts>.log` |
| 诊断包 | 导出 `logs/` + 版本/系统信息 + 数据文件清单（文件名/大小/条目数，不含正文）；不上传 |
| 提醒调度 | `ReminderScheduler`（协程）：启动/唤醒/跨天时重算 `nextAt`；到点弹通知；`reminder_state.json` 去重；应用未运行不提醒 |
| 深链 | 命令行 `--route=practice --article=123`；第二实例转发给已运行实例 |
| 网络 | **无**：不引入 HTTP 客户端，不申请/声明联网权限 |

## 10. 手写架构（Phase 5）

```
UI 书写板（Skia）
  → 采集点序列（x, y, t, pressure）
  → 切分（StrokeGestures，Kotlin 原样）
  → 渲染到固定画布（1024×512，白底，黑笔，6 px，抗锯齿）
  → 灰度（白底合成 + Rec.601 整数亮度）
  → C ABI (JNA) → preprocess.c（原样）→ NCNN → logits
  → softmax（Kotlin 原样）→ top-5 + 阈值 0.80/0.15
  → 半角→全角标点映射（PunctuationMap 原样）→ 上屏
```

- 中文模型与拉丁模型**随包分发**（用户确认）；首次启动解包到 `%LOCALAPPDATA%\Blancall\models\`，用 sha256 校验完整性。
- 固定画布是「同一笔迹一致率」的前提；Android 端也用同一画布尺寸回放同一份笔迹做 golden 对照。
- 模型完整性：`release_metadata.json` 的 sha256；缺失/不匹配时禁用并提示。
- 模型不可用/推理异常：书写按钮禁用或降级文本，明确提示「手写暂时不可用，可使用文本作答」；绝不假装识别成功。
- LICENSE/关于页必须显著声明：中文权重基于 CASIA-HWDB，仅限学术研究，禁止商业使用。
- x64 默认关闭 ncnn fp16（软件模拟差异），单独做对照；arm64 视 Skiko/JDK/NCNN 支持情况，不支持就在 README 说明。

## 11. PDF 与图片导出

| 能力 | 实现 |
|---|---|
| PDF 文本提取 | PDFBox 3 `Loader.loadPDF` + 自定义 `PDFTextStripper`；复用 Android 的上标注释清洗规则 |
| PDF 预览 | PDFBox `PDFRenderer.renderImageWithDPI` → `BufferedImage` → Compose `ImageBitmap`；按页缓存，缩放重渲染；串行 `Mutex` |
| 试卷导出 | PDFBox `PDDocument` + `PDPageContentStream`；`PDType0Font` 嵌入 OFL 字体；用 `PDFont.getStringWidth` 实现 `measureText`/`breakText`；保留 A4 595×842、页边距、行高、答案页分组 |
| PNG 分享图 | `org.jetbrains.skia.Surface` 绘制 → `Image.encodeToData(PNG)`；字体用 Skia `Typeface` |
| 导出后 | 另存为对话框 + 「打开所在文件夹」；不做系统分享面板 |
| 打印 | Phase 6 用 `PrintDialog`/XPS；首版可「导出 PDF 后用户自行打印」 |

## 12. 性能设计

| 预算 | 设计 |
|---|---|
| 冷启动 ≤ 2s | 启动只加载 `articles.json` 元数据与设置；`records/fsrs` 后台加载；首屏不阻塞；避免模型预加载 |
| 内存 ≤ 500MB | 50k 记录用紧凑索引（articleId → 记录区间）；阅读页位图缓存上限 128MB；离开阅读器释放 |
| 搜索 ≤ 100ms | 启动后台构建标题+正文小写索引；`Ctrl+F` 首查若索引未就绪则显示进度，不冻结 UI |
| 练习输入 ≤ 16ms | 每个空独立状态；输入只重绘当前 `BlankCard`；避免整页重组；提示动画用 `Animatable` |
| 安装包 ≤ 120MB | 自定义 jlink 运行时；不捆绑 philo/gaokao；手写模型 24MB 随包；字体只留 Regular；Skiko 是最大变量。若超标，优先裁素材/字体，不裁模型 |
| 列表 1000 篇 / 50k 记录 | `LazyColumn` + 分页索引；统计页聚合在后台计算并缓存；避免每次重组重新解析 JSON |

## 13. 测试架构

| 层 | 工具 | 范围 |
|---|---|---|
| `shared` 单测 | JUnit 5 + `@TempDir` | 移植 algorithm 58 个文件对应测试 + data 20 个 + notification 3 个；新增格式/备份/游标测试 |
| 黄金文件 | `tools/parity` | Android 生成的 `articles.json`/`records.json`/备份 ZIP 作为 fixture；Windows 读写后逐字段比对 |
| 差分测试 | 表驱动 | FSRS/判分/挖空：同一输入 → Android 输出 vs Windows 输出 |
| 安全测试 | 单测 | ZIP 路径穿越（含 `.`）、炸弹、重复条目、大小写冲突、保留名、超大 JSON、损坏文件 |
| 崩溃恢复 | 单测 + 手工 | 写入中途杀进程；验证无半截 JSON、`.bak` 可恢复 |
| UI 测试 | Compose UI Test Desktop | 欢迎页→首页→导入→练习→结果→统计的关键路径；快捷键；对话框；关闭询问 |
| 手工验收 | 检查表 | DPI 125/150/200%、多显示器、中文 IME、拖拽、托盘、通知、安装/卸载/升级 |

## 14. 打包与发布

- `jpackage --type app-image` + `--type msi`（WiX 3.14）；`--win-per-user-install`、`--win-menu`、`--win-shortcut`、`--win-dir-chooser`。
- 安装目录 `%LOCALAPPDATA%\Programs\Blancall`；用户数据固定在 `%LOCALAPPDATA%\Blancall`，卸载不删除。
- 便携版 ZIP：app-image + `Blancall.exe`；数据仍写 `%LOCALAPPDATA%`（避免 U 盘只读/权限问题），设置里可改数据目录（Phase 6）。
- 版本：`7.6.1-win.1`；产品名 `Blancall`（无 AI 后缀）；`CHANGELOG.md` 每个里程碑一条。
- 签名：无证书时提供 SHA256 与「未知发布者」说明；SmartScreen 风险写入 RISKS。
- 自动备份：每日首次启动/退出时若距上次 >24h，写 `backups/auto_yyyyMMdd.zip`（保留最近 7 份）。

## 15. 安全与隐私

1. 无遥测、无崩溃上报、无广告、**无网络请求**；安装包无 INTERNET 能力。
2. 无账号、无云同步、无 AI、无 API Key、无凭据存储。
3. 导入的 ZIP/PDF/DOCX：大小上限、路径校验、解析异常捕获；不执行宏/脚本。
4. Markdown 链接：只允许 `http/https`，点击前确认；禁止 `file://`/`javascript:`。
5. 文件写入原子；崩溃/断电不产生半截 JSON；损坏文件留副本。
6. 诊断包只含日志与元数据，不含文章正文/笔记。

## 16. ADR 详情

### ADR-001 技术栈：Compose Multiplatform Desktop

- **背景**：算法、数据、备份、FSRS、判分都是 Kotlin；UI 是 Compose Android。
- **选项**：CMP Desktop / WinUI3+C# / Avalonia+C#。
- **决定**：CMP Desktop（已确认）。
- **理由**：算法与测试可原样复用；PDFBox/JNA 都在 JVM；UI 可用 Compose 重写；单一语言降低长期维护成本。
- **代价**：Skiko 启动与包体；Windows 原生集成需 JNA；UI 仍需重写。

### ADR-002 包名与源码结构

- 复用 `com.ilyskyo.blancall.*`；桌面代码 `.desktop.*`。
- 理由：上游同步 diff 最小；测试 import 不变。

### ADR-003 `Article` 的 `@Immutable`

- Android `Article` 带 `androidx.compose.runtime.Immutable`。
- **实际选择**：迁移时直接去掉该注解，保持 `shared` 零 Compose 依赖；桌面 UI 使用自己的 `ArticleUi`/状态包装，稳定性由 Compose 编译器推断。
- 影响：仅是编译期优化提示，不改变数据语义；`shared` 的 96 个测试文件不依赖 Compose。
- 回退：若未来需要该注解，可在 `shared` 引 `org.jetbrains.compose.runtime`（仅注解），不影响数据格式。

### ADR-004 `record_id_seq`

- 问题：Android 的记录 id 无持久游标，删除最大 id 后重启会复用，导致 `ink/<recordId>.json` 错挂。
- 决定：Windows 写 `record_id_seq` 纯文本；读取时 `max(cursor, maxId)+1`。
- 兼容：Android 忽略该文件；备份不包含；恢复后仍用 `maxId+1` 兜底。

### ADR-005 导入前预备份

- 问题：Android 导入前不自动备份，失败/误选类目会丢数据。
- 决定：Windows 在写入任何 `.import` 前生成 `backups/pre-import_<ts>.zip`，保留最近 5 份。
- 兼容：只是本地文件，不影响备份格式。

### ADR-006 更严的备份校验

- 见 §6.6；不改变 ZIP 格式，只拒绝危险包。
- 与 Android 的差异记录在 `PARITY.md` 的「已知差异」小节。

### ADR-007 PDF 用 PDFBox 3.x

- 读/写/预览统一，避免 PDFium 原生 dll 与额外许可；预览性能用页缓存与 150 DPI 控制。
- 字体嵌入 OFL 字体，解决 Android「回落系统字体」的不确定性。

### ADR-008 手写模型随包分发

- 用户确认：中文模型与拉丁模型随安装包分发（**尚未实施**，当前版本无手写入口）。
- NCNN desktop + `preprocess.c` 原样 + C ABI + JNA。
- 必须在 README/关于页/安装向导显著声明 CASIA-HWDB 仅限学术、禁商用；商用前需授权或重训。
- 固定画布/笔宽/灰度/阈值，否则无法承诺一致率。

### ADR-009 内置素材改内容包

- philo/gaokao 体积 20.7 MB，且是内容型资源；改为可选内容包，减小安装包并避免一次性打包所有 PDF。
- 内容包格式与 Android 相同；首次启动不自动安装。

### ADR-010 i18n 管线

- 见 §8.6；保留 6 套资源与守卫测试；不硬编码新中文。

### ADR-011 提醒采用托盘常驻

- 默认托盘常驻时提醒可用；应用未运行时提醒不可用，UI 明确说明「需保持应用运行（可最小化到托盘）」。
- 计划任务作为 Phase 6 可选项，不做默认。

### ADR-012 关闭按钮每次询问

- 点击关闭 → Dialog：「最小化到托盘 / 退出 / 记住我的选择」。
- 勾选「记住」后写 `ui_prefs.json` 的 `close_action`；设置页可改回「每次询问」。
- 首次使用不预设值，避免静默改变用户预期。

### ADR-013 记录 id 不复用

- 与 ADR-004 配套；属于安全性改进，不改变文件格式。

### ADR-014 无 AI/无联网

- Windows 版不包含任何 AI 入口、设置、代码与依赖；不引入 HTTP 客户端；安装包无 INTERNET 能力。
- 与 `Blancall-Std` 的 NoAI 定位一致；不存在「关闭 AI」开关，因为没有 AI。

### ADR-015 单实例

- 文件锁 + 回环激活；避免多实例同时写 `data/`。

### ADR-016 视觉降级

- 不做 AGSL 折射；用静态玻璃 + 可选 Mica；保留品牌观感，优先性能与可读性。

### ADR-017 字体

- UI 用系统雅黑；阅读器/PDF 用 OFL 字体；不打包商业字体。

### ADR-018 版本

- `7.6.1-win.N`；与 Android 功能版本对齐，但独立递增；`CHANGELOG.md` 记录差异。

### ADR-019 首启欢迎与引导

- 对齐 `Blancall-Std`：欢迎页（离线说明/隐私政策/支持开发者）→ 5 页引导 → 首页。
- 隐私政策读取 `PRIVACY.md`（随包），不联网。
- Windows 额外提供「打开数据目录」「导入 Android 备份」入口。

### ADR-020 备份类目

- 只支持 7 个类目（无 AI）；导入 Pro 备份时忽略 `ai` 类目与 `ai_history` 条目。
- 旧备份无 `groups` 时按 7 类全选处理，并警告覆盖。
