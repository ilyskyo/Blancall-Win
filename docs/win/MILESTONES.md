# Blancall Windows 里程碑计划（Phase 0）

- 基准：`Blancall-Std 7.6.1-NoAI / vc54`
- 产品范围：只做无 AI 版；无网络请求、无遥测
- 每个里程碑一个可回滚提交；提交信息说明范围、验证命令、已知限制
- 不写工期估计；顺序与依赖为准
- 退出标准未满足不得进入下一阶段

## 0. 阶段依赖

```
Phase 0 调研与文档
   └── Phase 1 地基 + 数据/算法
          ├── Phase 2 MVP（可跑通学习闭环）
          │      └── Phase 3 完整 NoAI parity
          ├── Phase 4 不做（Pro/AI 范围外）
          ├── Phase 5 手写（模型随包，许可声明）
          └── Phase 6 发布（依赖 2/3 全绿；5 可并行）
```

## Phase 0 — 调研与对齐（本文件所在阶段）

### 交付物

1. `docs/win/PARITY.md`：100 条功能矩阵（41 P0 / 45 P1 / 14 P2），明确 AI 范围外。
2. `docs/win/ARCHITECTURE.md`：模块、数据、UI、平台适配、ADR-001~020。
3. `docs/win/DEPENDENCY-AUDIT.md`：250 个 `.kt` 的 A/B/C 标注、依赖与许可。
4. `docs/win/MILESTONES.md`、`docs/win/RISKS.md`。
5. `.audit/01~04`：原始审计记录（不随产品发布）。
6. 已确认决策：Compose Desktop、只做无 AI 版、中文手写模型随包、关闭按钮每次询问。

### 退出标准

- 用户确认 Phase 0 文档后，才进入 Phase 1。
- Phase 0 未写入任何参考工程，也未写产品代码。

### 回滚

- 删除 `Blancall-Win/` 不影响 Android 两个工程。

---

## 执行进度（2026-11-12）

| 阶段 | 状态 | 说明 |
|---|---|---|
| Phase 0 | 完成 | 五份文档 + 审计笔记；用户已确认技术路线/NoAI/手写随包/关闭询问 |
| Phase 1 | 完成 | `shared` 87 个主文件、96 个测试文件；`shared:test` 全绿；`record_id_seq`、备份加固已落地 |
| Phase 2 | 部分完成 | 首页/导入/阅读/三种练习/统计/设置/标签/PDF 导出/备份 UI 已可运行；手工验收未走完 |
| Phase 3 | 未开始 | 提示、遮挡、每日一句、热力图、内容包 UI、提醒调度、六语言未做 |
| Phase 4 | 不做 | 用户决定只做无 AI 版 |
| Phase 5 | 未开始 | 手写未接；模型未随包 |
| Phase 6 | 未开始 | 无 `jpackage`/WiX；未生成 MSI/便携包 |

## Phase 1 — 地基、算法与数据

### 目标

在任何 UI 之前，先让「读写 Android 数据 + 跑对算法 + 备份可互通」成立。

### 工作项

1. **工程骨架**
   - `settings.gradle.kts`、`gradle/libs.versions.toml`（锁定所有版本）、Gradle Wrapper 9.8.2。
   - `shared`（纯 JVM）与 `desktop`（Compose）两个模块；`shared` 不允许 Android/Compose UI/HTTP 依赖。
   - JDK 21 toolchain；`org.jetbrains.kotlin.jvm`；JUnit 5 + `kotlinx-coroutines-test`。
   - `tools/sync-from-android.ps1` + `tools/sync-transforms.md`；记录 `Blancall-Std` 基线（commit/版本）。
2. **平台接口**
   - `platform/Log`、`platform/Clock`（可注入 `now`/`zone`）、`platform/Paths`、`platform/Notifier`。
   - 桌面实现：Logback、系统时钟、`%LOCALAPPDATA%`、托盘通知（可先空实现）。
   - **不实现** `SecureStore`（无凭据）、`HttpClient`（无网络）。
3. **算法迁移**
   - 复制 50 个 A + 4 个 B（`CrossTextReview`/`MaskSpanOps` 靠 Log shim 回 A；`MemoryHeatmap`/`CsvExporter` 拆分）。
   - 5 处 Locale 修复；`FSRS`/`ForgettingPredictor`/`EbbinghausScheduler` 的时区参数（默认不变）。
   - 移植 algorithm 相关单测（Std 共 150 个测试文件 / 1,178 个 `@Test`）；新增 5 个无测试文件的测试（`ContentFingerprint` 等）。
4. **数据迁移**
   - 复制 29 个 data 文件；`AtomicFiles` Windows 版（原子 rename、`.bak`、跨进程锁）。
   - 实现 `record_id_seq`；所有文章写入路径维护 `article_id_seq`。
   - 移植 data 相关 20 个单测；新增格式/损坏/并发测试。
5. **备份**
   - `BackupManager` 移植；导出/导入 v1；7 个类目；孤儿清理。
   - Windows 增强：导入前预备份、更严路径校验、条目数上限、大小写冲突、保留名；忽略 Pro 备份的 `ai` 类目。
   - 移植 `BackupManagerTest`/`BackupGroupSelectionTest`/`LearningDataReloaderTest`/`LearningDataFileCensusTest`；新增攻击样本测试。
6. **黄金数据**
   - 建立 `testdata/android-v1/`：用 Android 生成的最小数据集（文章/记录/FSRS/标签/自定义挖空/备份 ZIP）。
   - `tools/parity`：读取 ZIP/目录，输出规范化 JSON；对比 Android 与 Windows 的读模型。
   - 用真实 Android 备份（用户提供）做一次端到端验收。

### 交付物

- 可编译的 `shared` + 空壳 `desktop`（只显示版本号与数据目录）。
- `docs/win/DATA-FORMAT.md`（Phase 1 从审计中提炼的字段级说明）。
- 测试报告：`gradlew :shared:test` 全绿。

### 退出标准（全部满足）

1. `gradlew :shared:test` 全绿；测试数 ≥ 85 个文件（algorithm + data + notification + 新增）。
2. `tools/parity` 对 `testdata/android-v1/` 的全部文件：读模型逐字段相等；写回后再次读取相等。
3. 备份：Android ZIP → Windows 导入 → 导出 → Android 模拟校验（字段级）通过；Pro 备份的 `ai` 类目被忽略且不报错。
4. 损坏矩阵：坏行/坏文件/双损坏/半截写入 4 类测试通过。
5. 安全：`../escape`、`files/.`、`CON`、`A.json`/`a.json`、重复条目、32MB/128MB 上限测试全部拒绝且不写盘。
6. `record_id_seq`：删除最大记录 → 重启 → 新 id 不复用；`ink/<id>` 不错挂。
7. 代码扫描确认：`shared`/`desktop` 无 `java.net`/`okhttp`/`ktor` 依赖。

### 回滚点

- `phase1-data` 标签；数据层可独立回退，不影响后续 UI 分支。

---

## Phase 2 — MVP（可用的学习闭环）

### 目标

用户能完成：首启欢迎 → 导入文章 → 阅读 → 练习 → 判分 → 结果 → 统计 → 备份恢复。

### 工作项

1. **应用壳**：单实例、窗口状态、主题（明/暗）、NavigationRail/底部导航、路由与返回栈、全局快捷键、关闭询问对话框。
2. **首启**：欢迎页（离线说明/隐私政策/支持开发者）→ 5 页引导 → 首页。
3. **首页**：搜索栏、今日计划卡、待复习卡、最近文章卡、添加文章卡；卡片布局的最小可编辑版。
4. **导入**：粘贴、TXT、PDF（文本抽取+预览）、DOCX；标题建议、分句预览、自动缩进、标签选择；拖拽导入。
5. **列表/阅读**：双栏列表、打开文章、阅读器（字号/行距/字体/背景）、阅读进度断点、阅读时长。
6. **练习**：句子/字词/反向默写；粒度/策略；判分；两级提示；断点续练；结果页；评级写回 FSRS。
7. **统计最小版**：单篇统计、练习记录、错题本入口。
8. **设置最小版**：主题、语言、复习模板、数据目录、备份/恢复、关于。
9. **数据**：全部经 `shared` Store；UI 不直接读写文件。

### 交付物

- 可运行的 `Blancall.exe`（开发版，不打包）。
- `docs/win/ACCEPTANCE.md`：P0 行的手工验收记录模板。

### 退出标准

1. 一条完整闭环可跑通（首启→导入→阅读→练习→结果→统计→备份→恢复）；录屏/截图存档。
2. P0 41 行全部通过；未通过项明确列出。
3. 冷启动 ≤ 2s（SSD，无模型预加载）；1000 篇文章 / 50,000 条记录下列表滚动与搜索流畅；搜索 ≤100ms。
4. `gradlew :shared:test :desktop:test` 全绿；关键路径有 Compose UI 测试。
5. 无阻塞性 bug；崩溃日志可落盘；关闭询问三选一生效。
6. 抓包/防火墙验证：运行期间无任何出网连接。

### 回滚点

- `phase2-mvp` 标签；UI 与数据层解耦，可单独回退 UI。

---

## Phase 3 — 完整 NoAI parity

### 目标

把 `PARITY.md` 的 P1 全部做完，并补齐 P0 的边界行为。

### 工作项

1. **组织**：标签管理/详情/筛选、搜索全量、首页布局编辑、今日计划/任务流、待复习清单。
2. **练习增强**：自定义挖空编辑/练习、跨文混合、薄弱集训、撤销/重做、沉浸。
3. **阅读增强**：遮挡（本地+自定义）、划线批注、阅读速度、字体导入、自定义遮挡配置。
4. **统计**：全局大屏、雷达/错误分布/热力图/衰减曲线、学习日历、成就、需加强推荐、分享图。
5. **导出**：PDF 试卷（字体嵌入）、CSV、复制文本。
6. **提醒**：托盘常驻调度、频率/免打扰/目标、关闭询问、开机自启、崩溃日志/诊断包。
7. **内容**：内容包、数据互换、素材库（WebView2 或等价方案）、可选素材。
8. **多语言**：6 套资源生成/运行时/守卫测试（每套 1,656 条）；硬编码中文棘轮。
9. **文档**：`README.md`、`CHANGELOG.md`、已知差异。

### 退出标准

1. `PARITY.md` 中 P0/P1 全绿（含自动化或手工验收记录）。
2. 6 套语言可切换；`LocaleParityGuard` 通过；新代码无硬编码中文（白名单除外）。
3. 备份双向：Windows → Android（真实设备恢复验证）；Android → Windows；Pro 备份的 `ai` 类目被忽略。
4. 性能预算全部达标（见 `ARCHITECTURE.md` §12）。
5. 无 P0/P1 未关闭的崩溃/数据问题。
6. 无网络依赖：代码扫描 + 抓包复核。

### 回滚点

- `phase3-parity` 标签。

---

## Phase 4 — 不做（Pro / AI 范围外）

### 决定

用户选择「只做无 AI 版」，因此 Phase 4 的 Pro/AI 功能**不实施**：

- 无 AI 总开关、无 AI 配置、无 AI 挖空、无 AI 对话/历史/分析、无联网搜索。
- 不引入 HTTP 客户端、不声明 INTERNET 能力、无凭据存储。
- 设置页没有 AI 分组；路由表没有 `ai_*`；数据目录没有 `ai_history/`、`ai_analysis/`。
- 导入 Pro 备份时忽略 `ai` 类目；不写入、不清理、不报错。

### 验收（作为范围守卫）

1. 代码扫描：无 `ktor`/`okhttp`/`HttpURLConnection`/`java.net.Socket`。
2. 路由表与设置项扫描：无 `ai` 前缀入口。
3. 安装包/进程：无出网连接。
4. 与 `Blancall-Std` 的页面/设置项清单逐项对照，无多余 AI 项。

> 如果未来要恢复 AI，必须重新评估许可、密钥存储（DPAPI）、网络策略与 Phase 4 工作项；不能直接复用 Android 的 KeyStore 代码。

---

## Phase 5 — 手写

### 目标

离线手写识别，识别率与 Android 对齐；不可用时明确降级。

### 前置条件

- 用户已确认中文模型随包分发；README/关于页/安装向导的许可声明文案就绪。
- NCNN desktop 构建链可用（MSVC/CMake）；x64 优先。

### 工作项

1. NCNN desktop 静态库；`preprocess.c` 原样编译；C ABI（create/destroy/recognize/recognizeLatin）。
2. JNA 桥接；模型完整性 sha256；缺失/不匹配提示。
3. 书写板：指针事件、压感（可选）、`InkWidthSim` 回退、撤销/清空、半角→全角标点。
4. 连写拆分：`StrokeGestures` 原样；保留笔间时间戳。
5. 固定画布（1024×512、6px、白底黑笔、抗锯齿）；灰度 Rec.601；softmax/阈值原样。
6. golden 对照：≥300 条 Android 墨迹（坐标+时间戳+画布尺寸），两端回放。
7. 降级：模型/推理不可用 → 禁用书写按钮 + 明确提示 + 文本作答。
8. 许可声明：关于页、README、安装向导、`THIRD-PARTY-LICENSES` 四处一致。

### 退出标准

1. 同一批笔迹，Windows 与 Android 的 top-1 一致率 ≥99%，top-5 顺序一致率 ≥99%（按字符与整词分别统计）。
2. logits L∞ ≤ 1e-4（固定线程/fp16 配置下）；不一致样本有记录与归因。
3. 全程离线；无云 OCR；断网可用。
4. 无模型/损坏模型：不崩溃、不假装识别成功。
5. 安装包仍 ≤120MB；若超标，先裁素材/字体，不裁模型。

### 回滚点

- `phase5-handwriting` 标签；书写板是独立模块，可关闭入口。

---

## Phase 6 — 发布

### 目标

可安装、可升级、可卸载、数据可回迁的 Windows 发布件。

### 工作项

1. jpackage app-image + MSI（WiX 3.14）+ 便携 ZIP；per-user 安装；卸载保留数据。
2. 自定义 jlink 运行时；体积优化（不捆绑 philo/gaokao、字体只留 Regular、手写模型随包）。
3. 自动备份（每日首次启动/退出，保留 7 份）；崩溃日志；诊断包。
4. 签名：有证书则签名；无证书提供 SHA256 + 说明。SmartScreen 风险写入 README。
5. 安装/升级/卸载测试：干净 Windows 10 22H2 / Windows 11 虚拟机；数据目录不丢。
6. 可访问性：键盘全路径、焦点可见、缩放 200% 不截断、屏幕阅读器基本可用。
7. 性能复测；安装包体积复测。
8. 发布说明、LICENSE、THIRD-PARTY-LICENSES（补 commonmark 等）、手写模型许可声明。

### 退出标准

1. 干净虚拟机：安装→使用→升级→卸载→重装，数据完整；备份可回迁 Android。
2. 安装包 ≤120MB（含手写模型，不含可选素材包）。
3. 冷启动/内存/搜索/输入延迟达标。
4. 无 P0 崩溃；诊断包可导出且不含正文。
5. `CHANGELOG.md`、`README.md`、`PARITY.md` 状态一致；未完成项明确。
6. 无 INTERNET 能力/无出网连接。

### 回滚点

- 发布标签 `v7.6.1-win.1`；安装包与便携包归档。

---

## 跨阶段质量门禁

每个里程碑必须提交：

1. `gradlew test` 实际命令与输出摘要（不是「应该能过」）。
2. 更新 `CHANGELOG.md`；一个里程碑一个可回滚提交。
3. 更新 `PARITY.md` 状态；未完成不得标完成。
4. 说明已知限制与下一步。
5. 新增/修改数据格式必须同时更新 `docs/win/DATA-FORMAT.md` 与 `RISKS.md`。

**禁止**：

- 用 TODO 静默占位、假数据冒充功能、把「能点」当「能用」。
- 删除/弱化测试来通过；修改 Android 参考实现来「对齐」。
- 硬编码中文 UI 文案；引入任何网络/遥测代码。
- 未经验证声称「已对齐」「已完美」。

## 环境前置（Phase 1 开始前）

| 项 | 要求 | 当前状态 |
|---|---|---|
| JDK 21 | Temurin 21（含 `jpackage`） | 本机有 Android Studio JBR 21.0.10（有 `jlink`，**无 `jpackage`**），需另装 Temurin |
| Gradle | Wrapper 9.8.2 | 由工程自带 |
| MSVC / CMake | Phase 5 需要 | 待确认（Visual Studio Build Tools） |
| WiX | 3.14（MSI） | 未安装 |
| 网络 | 首次构建拉依赖 | 可用 |
| 测试数据 | 一份真实 Android 备份 ZIP + `filesDir` 副本 | 需用户提供（脱敏可选） |
