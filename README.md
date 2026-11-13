# Blancall for Windows

![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)
![Platform: Windows 10/11 x64](https://img.shields.io/badge/platform-Windows%2010%2F11%20x64-0078D4.svg)
![Kotlin: 2.2](https://img.shields.io/badge/Kotlin-2.2-7F52FF.svg)
![UI: Compose Desktop](https://img.shields.io/badge/UI-Compose%20Desktop-4285F4.svg)
![Network: none](https://img.shields.io/badge/network-none-success.svg)

**不是清空，是召回。** Blancall 把任何文章变成挖空练习，用 FSRS-6 把复习安排在快要忘记的时候。

本仓库是 Android 版 [Blancall NoAI](https://github.com/ilyskyo/Blancall-NoAI) 的 Windows 桌面移植：**数据格式与备份双向兼容**，功能对齐 NoAI 标准版。

- 参考实现：`Blancall-Std 7.6.1-NoAI / versionCode 54`
- 本工程：Kotlin/JVM + Compose Multiplatform Desktop
- 版本：`7.6.1-win.2`
- AI：**不包含**（与 NoAI 一致：无 AI 代码路径、无 AI 设置、无 AI 入口）
- 发布形态：便携 ZIP（标准版 / 精简版）+ 用户级安装脚本；**中文手写模型由 Release 单独提供**；**MSI 尚未产出**（见「已知限制」）

> 与 Android 版逐项功能的对照表、实现状态与差异清单在 [docs/win/PARITY.md](docs/win/PARITY.md)；验收证据在 [docs/win/ACCEPTANCE.md](docs/win/ACCEPTANCE.md)。本 README 的「已知限制」是发布口径，二者不一致时以 PARITY/ACCEPTANCE 中的证据为准。

## 与 Android 版的关系

| 维度 | Android（Blancall-Std / NoAI） | Windows（本仓库） |
|---|---|---|
| 数据格式 | `filesDir` 下 JSON / JSONL | 同构，位于 `%LOCALAPPDATA%\Blancall\data` |
| 备份 ZIP | `formatVersion=1`，`manifest.json` + `files/` | 同格式，可双向导入导出 |
| 算法 | FSRS-6、判分、挖空、统计、成就 | 同源代码移植到 `shared`，有对照测试 |
| 手写模型 | `assets/hccr`、`assets/hccr_en` | 同一份权重（SHA-256 一致），NCNN CPU 推理 |
| AI | 无（NoAI） | 无（NoAI） |
| 联网 | 清单不声明 `INTERNET` | 代码不引入 HTTP 客户端，不发起请求（见「隐私」的边界说明） |
| UI | Jetpack Compose | Compose Multiplatform Desktop |
| 提醒 | 通知渠道 + WorkManager | 托盘常驻 + 进程内调度（应用不运行则不提醒） |
| 平台特性 | 压感、触感、液态玻璃、系统分享 | 无压感/触感/液态玻璃；分享以文件保存对话框代替 |

## 系统要求

- Windows 10 / Windows 11，**x64**；无需管理员权限。
- 未验证：Windows 7 / 8.1、ARM64、Windows Server、远程桌面 / 多用户会话。
- 磁盘：完整版解压约 450 MB（含手写模型），精简版不含 `models/`。

## 安装

### 便携版（推荐）

从 [Releases](https://github.com/ilyskyo/Blancall-Win/releases)（或本地 `dist/`）解压 ZIP 后双击 `Blancall.vbs`：

```text
dist\Blancall-7.6.1-win-x64-portable.zip        约 120 MB（含 ncnn 与拉丁模型，不含中文手写模型）
dist\Blancall-7.6.1-win-x64-portable-lite.zip   约 115 MB（不含 models/，手写入口全部隐藏）
```

### 中文手写模型（单独下载）

受 CASIA-HWDB 授权限制，中文手写模型**不随仓库与便携包分发**。需要中文手写时：

1. 从 [Releases](https://github.com/ilyskyo/Blancall-Win/releases) 下载 `Blancall-7.6.1-handwriting-pack-win-x64.zip`（约 27 MB，含 `ncnn.dll`、中文模型与拉丁模型）。
2. 解压到应用根目录（与 `Blancall.vbs` 同级），得到 `models\ncnn.dll`、`models\hccr\`、`models\hccr_en\`。
3. 重启应用，练习页出现「手写」入口；设置 → 关于会显示模型许可声明。

用户级安装的根目录为 `%LOCALAPPDATA%\Programs\Blancall\`。未安装中文模型时，中文手写入口自动隐藏，键盘与拉丁手写不受影响。

### 安装到当前用户（无需管理员）

```powershell
powershell -ExecutionPolicy Bypass -File tools\install-portable.ps1 -FromZip dist\Blancall-7.6.1-win-x64-portable.zip
```

安装到 `%LOCALAPPDATA%\Programs\Blancall`，创建桌面 / 开始菜单快捷方式。**卸载**：删除该目录与两个快捷方式即可，**不会删除** `%LOCALAPPDATA%\Blancall` 学习数据。

### 自行编译

前置 JDK 21（开发使用 Temurin 21.0.13+9）：

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"

.\gradlew.bat :shared:test :desktop:test
.\gradlew.bat :desktop:run
```

首次构建会下载 Gradle 8.14.3、Compose 1.10.3 与 Skiko Windows runtime。

### 打包

```powershell
powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1

# 需要把中文模型打进包时，先解压手写包，再用 -ModelDir 注入
powershell -ExecutionPolicy Bypass -File tools\package-windows.ps1 -ModelDir D:\path\to\handwriting-pack
```

脚本依次执行测试 → `uberJar` → `jlink` → 组装 app image → 生成标准 / 精简 ZIP，并在末尾尝试 `jpackage` MSI（当前环境无法完成，见「已知限制」）。不传 `-ModelDir` 时，产物不含中文手写模型。

### 关于代码签名

发布包**未使用代码签名证书**，首次运行可能触发 SmartScreen「Windows 已保护你的电脑」。可选择「更多信息 → 仍要运行」，或自行从源码构建并比对产物。本项目不提供自动更新。

## 功能

- **文章导入**：粘贴 / 剪贴板 / 拖拽 / TXT / Markdown / PDF（前 3 页缩略图预览 + 全文抽取）/ DOCX / DOC（Word 97-2003，POI HWPF）/ EPUB（`container.xml` → OPF spine）/ HTML / RTF
- **阅读**：字号、行距、首行缩进、背景风格、界面字号缩放；背诵遮挡（短 / 长 / 混合 + 自定义）；划线批注、存为挖空；阅读时长与剩余时间；PDF 试卷导出
- **练习**：句子 / 字词 / 反向默写 / 自定义挖空；判分与错因分型；两级提示 + 10 秒自动淡显；撤销重做；断点续练；跨文混合练习；错题集训；文章级 + 句子级 FSRS；**离线手写输入**（NCNN CPU，中文 7356 类 + 拉丁 47 类，支持连写整行拆字；中文模型需按「安装」一节单独下载）
- **句子卡**：今日句 + 到期句 + 新句队列、首字挖空回忆面、显示答案、忘记 / 不熟 / 记住了三键评级、间隔预览、全量复习、完成页
- **记忆与统计**：今日计划 / 任务流、每日一句、学习日历、记忆热力图、遗忘曲线、六维错因雷达、错题本、成就、需加强文章、练习记录下钻（单篇 / 跨文来源）、CSV、PNG 分享图
- **首页与标签**：首页布局可上移 / 下移 / 显示隐藏 / 恢复默认；标签主页汇总文章、到期 / 暂停统计与练习入口
- **数据**：与 Android 同格式读写；备份 ZIP v1 导出 / 导入；导入前预备份；每日自动备份（保留最近 7 份）；内容包导入与管理
- **系统**：单实例、窗口几何记忆、关闭询问（托盘 / 退出 / 记住）、托盘与提醒调度（免打扰 / 每日目标 / 测试提醒）、原生菜单栏、最近打开、快捷键（`Ctrl+N/O/F/,`、`Ctrl+Z/Y`、`Esc`、`F11`）、崩溃日志、诊断包
- **语言**：简体中文 / 繁體中文 / English / 日本語 / 한국어 / Русский，设置内即时切换；静态文案与常见动态模板均入 catalog，未收录时按中文兜底

## 数据与备份

```
%LOCALAPPDATA%\Blancall\
├─ data\        # 与 Android filesDir 同构
├─ backups\     # 手动导出、pre-import_*、auto_*.zip
├─ logs\        # 崩溃日志（仅本机）
├─ settings\    # 窗口位置 / 界面偏好 / 提醒偏好
└─ cache\
```

设置页提供「打开数据目录」「打开备份目录」。

**从 Android 迁移**

1. Android：设置 → 数据 → 导出备份（`blancall_backup_*.zip`）。
2. Windows：设置 → 数据 → 导入备份，选择该 ZIP。
3. 导入前会在 `backups\pre-import_*.zip` 生成当前数据预备份。
4. 反向：Windows 导出备份 → Android 导入。

兼容细节：

- 导出 7 个类目（articles / progress / marks / organize / ink / prefs / session），不含 AI。
- 导入 Pro 备份时忽略 `ai` 类目，并删除残留 `ai_history/`。
- Windows 新增 `record_id_seq`（记录 id 单调游标，Android 忽略），避免删除最大记录后 id 复用导致墨迹错挂。
- 备份校验更严：拒绝 `.` / `..`、Windows 保留名、尾点、大小写冲突与重复条目；因此**极少数畸形 Android 包会被拒绝**，错误信息会给出原因，原包不被修改。

## 隐私

- **不收集**：无账号、无遥测、无崩溃上报、无广告、无第三方统计 SDK。
- **不联网**：不引入 HTTP 客户端，不发起任何网络请求；手写识别为本地 NCNN CPU 推理。
- **数据只在本机**：文章、练习记录、FSRS 状态、标签、墨迹、阅读偏好均只写入 `%LOCALAPPDATA%\Blancall\`。
- **诊断包**：仅在设置页手动导出，含环境信息、日志、偏好摘要与数据文件清单，不含文章正文。
- **边界说明**：Windows 没有 Android 清单式的权限模型，无法从系统层面证明「不具备联网能力」；如需验证，可用防火墙 / 资源监视器观察本进程无出站连接。SmartScreen 与 Windows 自身的诊断数据由操作系统产生，不属于本应用。
- 删除 `%LOCALAPPDATA%\Blancall\` 即清除全部本地数据；卸载程序默认不删除该目录。

完整条款见 [PRIVACY.md](PRIVACY.md)。

## 已知限制

> 口径：**未实现** = 仓库中没有该功能；**部分 / 未验收** = 代码存在，但缺少人工或真实数据验收；**平台差异** = 桌面与 Android 机制不同，属预期行为。逐项证据见 [docs/win/PARITY.md](docs/win/PARITY.md) 与 [docs/win/ACCEPTANCE.md](docs/win/ACCEPTANCE.md)。

### A. 尚未实现

| 项目 | 说明 |
|---|---|
| MSI 安装包 | 打包脚本会尝试 `jpackage` + WiX 3.14，但当前构建环境为受限令牌，`light.exe` 创建临时目录被拒绝访问；需在普通用户桌面会话重跑脚本 |
| 代码签名 | 无证书，SmartScreen 会提示未知发布者 |
| 自动更新 | 无网络能力，升级需手动下载替换 |
| 开机自启 | 未实现注册表 / 启动项写入 |
| 素材库（WebView） | 未实现；内容包导入可用，但没有 Android 的素材库浏览页 |
| 任务栏进度 / 托盘迷你窗 | 未实现 |
| 阅读自定义字体导入 | 未实现；仅内置 PDF 导出的字体与系统字体 |
| 内容包拖拽导入、数据互换 | 未实现（内容包 UI 与 ZIP 导入已完成） |
| 遮挡转练习、完整自定义挖空编辑器 | 未实现（练习中自定义挖空、选区存为挖空已完成） |
| 手写压感、标点手势 | 桌面鼠标 / 普通笔事件无压感，仅按 `InkWidthSim` 模拟；标点手势未做 |
| 手写真实笔迹对照 | 未与 Android 做同笔迹 top-1 / top-5 一致率对照 |

### B. 已实现但未完成人工验收

| 项目 | 当前证据 | 缺口 |
|---|---|---|
| 全链路 UI 点验 | 导入 / 练习 / 结果 / 统计 / 备份均有自动化测试 | 未逐页人工点验；流程性缺陷可能未被自动化覆盖 |
| DPI 125% / 150% / 200% | `-Dsun.java2d.uiScale` 启动冒烟通过 | 未做像素级布局矩阵验收 |
| 多显示器 / 热插拔 | 窗口几何有记忆 | 跨屏拖动、缩放变化未验证 |
| 托盘气泡、睡眠唤醒补发 | 调度器、去重、免打扰、每日目标已接 | 真实桌面会话下的气泡与跨睡眠补发未验证 |
| 六语言 | 5 套 catalog 与中文 key 一致，动态模板测试通过 | 未逐语言人工走查；少量运行时拼接仍可能回落中文 |
| 真实 Android 备份回归 | 备份格式、安全边界、往返读写有单测 | **未用真实脱敏备份做端到端导入 → 导出 → 再导入** |
| 手写识别精度 | 合成笔画 top-1：永 97.6%、你 99.3%、好 98.3%、学 99.8%；拉丁 A / 7 ≈ 99.9%；连写「你好」拆分正确 | 仅合成样本；真实笔迹、连笔风格、跨设备一致率未知 |
| 提醒设置 UI | 偏好落盘有单测 | 未逐项点击验证 |
| 结果页提示统计、大屏统计布局 | 逻辑由 shared 测试覆盖 | 未人工验收 |
| 导入前预备份 | 每次导入都会尝试写入 `backups\pre-import_*.zip` | 该预备份为 best-effort：写入失败（如磁盘满）时不会阻断导入，且不会提示；重要导入前请手动「导出备份」 |

### C. 与 Android 的平台差异（预期行为）

| 差异 | 影响 |
|---|---|
| 无压感 | 笔迹粗细按速度 / 宽度模拟，观感与压感屏不同 |
| 无触感反馈、无液态玻璃折射、无 Material You | 视觉与交互简化；主题仍跟随明暗 |
| 提醒依赖托盘常驻 | **应用未运行时不会提醒**；没有 WorkManager 式后台调度 |
| 关闭按钮默认询问 | 多一步交互，可勾选「记住我的选择」 |
| PDF 导出固定嵌入霞鹜文楷 | 与 Android「回落系统字体」观感不同，但结果可复现 |
| 备份校验更严 | 畸形包可能被拒绝（不修改原包） |
| 新增 `record_id_seq` | Android 备份不含该文件；Android 读取时忽略 |
| 分享以文件保存代替系统分享 | CSV / PNG 通过保存对话框落盘，无 Sharesheet |
| 无 AI | 与 NoAI 一致；Pro 的 AI 能力在 Windows 不存在 |

### D. 许可与分发限制

- **代码 MIT，模型例外**：`models/hccr`（中文 7356 类）基于 CASIA-HWDB 训练，**仅限学术研究，禁止任何商业用途**；该权重**不在本仓库内**，只通过 GitHub Release 单独分发，下载即表示接受该限制。`models/hccr_en`（拉丁 47 类，EMNIST）无此限制，随仓库提供。
- **商用前必须处理**：如将本软件用于商业产品，需移除或替换中文手写模型，或向中科院自动化所取得商业授权（联系方式见 [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md)）。
- 再分发时须保留 [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md)、`LICENSE` 与字体的 OFL 声明。
- 便携包内含 jlink 生成的 Temurin 运行时（GPLv2 + Classpath Exception），再分发须保留相应声明。
- 本项目未经 Microsoft Store 分发，也未做任何微软认证。

## 常见问题

**Windows 版和 Android 版的数据能互通吗？**

可以，备份 ZIP 双向兼容（`formatVersion=1`）。但真实 Android 备份的端到端回归尚未完成，首次导入前请保留原始备份，并确认 `backups\pre-import_*.zip` 已生成。

**需要联网吗？**

不需要。应用不引入 HTTP 客户端，手写识别在本机 CPU 完成。

**会上传我的文章或学习数据吗？**

不会。没有账号、遥测与崩溃上报；数据只写入 `%LOCALAPPDATA%\Blancall\`。

**为什么 Windows 提示「未知发布者」？**

发布包未做代码签名，属于预期行为；可从源码自行构建核对。

**没有手写模型还能用吗？**

可以。标准便携包不含中文模型，中文手写入口自动隐藏，键盘与拉丁手写不受影响；需要时按「安装」一节下载手写包解压到应用根目录即可。

**为什么 README 里有「部分完成」项？**

本仓库按 PARITY 的状态口径记录：只有代码 + 自动化测试 + 对应验收证据都具备才标「完成」。未人工验收的项不会被写成已完成。

## 开源许可

本项目源代码以 **MIT 许可证** 发布（见 [LICENSE](LICENSE)）。

> **许可范围说明**：MIT 的商业使用授权适用于本项目源代码。唯一例外是手写识别模型权重（`models/hccr`、`models/hccr_en`）：中文权重因训练数据 CASIA-HWDB 的授权限制，不在商业授权范围内，仅供学习、研究用途；随包的 `models/ncnn.dll` 遵循其自身的 BSD 3-Clause 许可。

### 字体

PDF 试卷导出嵌入 **霞鹜文楷（LXGW WenKai）** Regular 子集，遵循 **SIL Open Font License 1.1**。

- 上游：https://github.com/lxgw/LxgwWenKai

### FSRS 间隔重复算法

采用 **FSRS-6**，移植自 Anki 开源实现 **fsrs-rs**，并使用其官方默认参数（默认目标留存率 90%）。

- 论文：Ye, J., Su, J., & Cao, Y. (2022). *A Stochastic Shortest Path Algorithm for Optimizing Spaced Repetition Scheduling*. https://doi.org/10.1145/3534678.3539081
- 算法文档：https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm
- fsrs-rs：https://github.com/open-spaced-repetition/fsrs-rs

### 手写识别模型

- **中文模型**：7356 类（HWDB 全集：7185 汉字 + 171 字母数字符号），训练数据为 **CASIA-HWDB**（中科院自动化所，仅限非商业学术研究）。
- **拉丁模型**：47 类（EMNIST Balanced），训练数据为 **EMNIST**（NIST 衍生公开数据集，无商业限制）。

> **用途声明**：因训练数据 CASIA-HWDB 仅限非商业学术研究，中文手写模型权重不在 MIT 的商业授权范围内，不得用于任何商业用途。

完整的第三方组件清单、完整许可文本与合规保留要求见 [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md)。

## 免责声明

本软件按「现状」提供，不提供任何明示或暗示的担保。作者不对使用本代码造成的任何数据丢失或业务中断承担责任。

**数据安全提示**：本软件为本地单机应用，没有云端副本。请自行保管备份 ZIP；删除数据目录、磁盘故障或卸载时的误操作均可能导致数据不可恢复。

## AI 生成软件声明

本软件可能包含由人工智能系统（包括但不限于大语言模型、代码生成工具、自动补全系统及智能编程代理）生成的代码或内容。

1. **AI 生成内容**：部分或全部代码可能源自 AI 模型输出，开发者已在能力范围内进行审核与整合。
2. **无担保**：本软件按「现状」（AS IS）提供，不附带任何明示或暗示的担保，包括但不限于适销性、特定用途适用性及不侵权保证。
3. **风险提示**：AI 生成的代码可能存在错误、安全漏洞、幻觉逻辑或不完整推理；开发者无法对完整性、正确性、安全性与特定用途适用性作出保证。
4. **建议**：用于生产环境前，请自行进行全面代码审查、测试与安全评估。

## 文档

| 文档 | 内容 |
|---|---|
| [docs/win/PARITY.md](docs/win/PARITY.md) | 与 Android 的逐项功能对照、已知差异、实现状态 |
| [docs/win/ACCEPTANCE.md](docs/win/ACCEPTANCE.md) | 自动化与手工验收记录（含阻塞项） |
| [docs/win/ARCHITECTURE.md](docs/win/ARCHITECTURE.md) | 模块划分、数据流、桌面适配 |
| [docs/win/DATA-FORMAT.md](docs/win/DATA-FORMAT.md) | 数据文件与备份 ZIP 结构 |
| [docs/win/DEPENDENCY-AUDIT.md](docs/win/DEPENDENCY-AUDIT.md) | Android 源码的迁移分级审计 |
| [docs/win/MILESTONES.md](docs/win/MILESTONES.md) | 阶段计划与退出标准 |
| [docs/win/RISKS.md](docs/win/RISKS.md) | 风险登记与缓解 |
| [docs/win/UPSTREAM.md](docs/win/UPSTREAM.md) | 上游同步约定 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更 |
| [PRIVACY.md](PRIVACY.md) | 隐私说明 |
| [THIRD-PARTY-LICENSES.md](THIRD-PARTY-LICENSES.md) | 第三方组件与许可 |
