# Blancall 数据格式（Windows 兼容说明）

- 基准：`Blancall-Std 7.6.1-NoAI / vc54`
- Windows 数据目录：`%LOCALAPPDATA%\Blancall\data\`（等价 Android `filesDir`）
- 原则：文件名、字段名、类型、默认值、稀疏字段规则与 Android 一致；Windows 只新增不破坏兼容的文件。

## 1. 文件清单

| 文件 | 格式 | 说明 |
|---|---|---|
| `articles.json` | JSON 数组 | `id/title/content/createdAt/updatedAt/autoIndent/author` |
| `article_id_seq` | 纯文本 Long | 文章 id 单调游标；删除不回退；不进备份 |
| `record_id_seq` | 纯文本 Long | **Windows 新增**；记录 id 游标；不进备份；Android 忽略 |
| `records.json` | JSONL（兼容旧 JSON 数组） | 每行一条 `PracticeRecord`；末尾换行 |
| `fsrs_state.json` | JSON 对象 | `version=3`；文章级键、句子级 `s:<articleId>:<hash16>`、`paused`、`enrolled` |
| `fsrs_reviews.jsonl` | JSONL | 复习事件；`v=2`；不进备份 |
| `custom_cloze.json` | JSON 对象 | `version=1`；按文章分组的自定义挖空 |
| `mask_config.json` | JSON 对象 | `version=1`；遮挡配置 |
| `home_layout.json` | JSON 对象 | `version=1`；首页卡片 |
| `sentence_card.json` | JSON 对象 | 每日一句快照；`date=yyyy-MM-dd`（本地时区） |
| `reader_prefs.json` | JSON 对象 | 按文章的阅读设置 |
| `reading_marks.json` | JSON 对象 | 划线与批注 + 正文 MD5 |
| `tags.json` | JSON 对象 | `version=1`；`tags[]` + `links{articleId:[tagId]}` |
| `achievements.json` | JSON 对象 | `version=1`；`unlocked{id:epochMillis}` |
| `exam_goal.json` | JSON 对象 | `version=1`；`epochDay`（本地日历日序号） |
| `appearance.json` | JSON 对象 | `version=1`；主题/强调色/字体 |
| `reminder_state.json` | JSON 对象 | 通知去重；不进备份 |
| `practice_state_<articleId>.json` | JSON 对象 | 未完成练习现场；`status="IN_PROGRESS"` |
| `ink/<recordId>.json` | JSON 对象 | 墨迹；坐标 ×10000 归一化 |
| `content_packs/<packId>/pack.json` | JSON 对象 | 内容包元数据 + `e<n>.txt` |
| `hccr/`、`hccr_en/` | 二进制/JSON | 手写模型解包目录；不属于学习数据 |

## 2. 关键实体字段

### Article

| 字段 | 类型 | 默认 |
|---|---|---|
| `id` | Long | 0；加载时 `<=0` 跳过 |
| `title` | String | `""` |
| `content` | String | 必填；`isBlank()` 跳过 |
| `createdAt` / `updatedAt` | Long epoch ms | now |
| `autoIndent` | Boolean | true |
| `author` | String | `""` |

### PracticeRecord（JSONL 一行）

| 字段 | 类型 | 默认/写法 |
|---|---|---|
| `id` | Long | Windows 用 `record_id_seq` 分配 |
| `articleId` | Long | 跨文练习为 `-1` |
| `sourceArticleIds` | Long[] | 空列表不写键 |
| `mode` | String | `SENTENCE` / `WORD` / `REVERSE` |
| `totalBlanks` / `correctCount` | Int | 0 |
| `timestamp` | Long | now |
| `duration` | Long | 0 |
| `similarity` | Double（由 Float 扩） | 0.0 |
| `rating` | Int | 1..4 |
| `weakHints` / `strongHints` | Int | 0 |
| `answeredSentenceStarts` / `mistakeSentenceIndices` / `mistakes` | 数组 | 总是写键 |
| `answeredBlanks` | 数组 | 空列表不写键 |

`MistakeDetail`：`blankIndex/correctAnswer/userAnswer/errorType` 总是写；`sentenceIndex>=0`、`similarity>=0`、`sentenceText` 非空、`articleId>0` 才写，读侧缺省 `-1/-1/""/-1`。

### FSRS

`CardState`：`difficulty(Double)`, `stability(Double)`, `due(Long)`, `lastReview(Long)`, `reviewCount(Int)`, `lapses(Int)`, `lastRating(String)`。
`due=0` 表示无排期，不是 1970 到期。
句子键：`"s:" + articleId + ":" + SHA-256(trim(sentenceText)) 前 8 字节 hex`。

### Tags

`{"version":1,"tags":[{"id":1,"name":"诗词","color":"#EE9DB4"}],"links":{"12":[1]}}`
颜色大写 `#RRGGBB`；`links` 读取时过滤不存在的 tag。

## 3. 备份 ZIP v1

```
manifest.json
files/articles.json
files/records.json
files/fsrs_state.json
...
files/ink/<id>.json
files/content_packs/<id>/...
```

`manifest.json`：

```json
{"formatVersion":1,"createdAt":1758...,"groups":["articles","progress",...],"files":["files/articles.json",...]}
```

- `formatVersion` 必须精确等于 1。
- Windows 导出 7 个类目：`articles/progress/marks/organize/ink/prefs/session`。
- 导入按类目整类替换；类目外文件不动。
- 导入前 Windows 额外写 `backups/pre-import_<ts>.zip`。
- 旧备份无 `groups` 视为全选（与 Android 一致）。
- Pro 备份的 `ai` 类目：Windows 忽略并删除 `data/ai_history`（如果包内存在）。
- 安全校验：拒绝 `.`/`..`/空段/绝对路径/盘符/反斜杠/保留名/尾点/大小写冲突/重复条目；条目数 ≤ 10,000；单条 ≤32MiB；总量 ≤128MiB。

## 4. 字节级规则

1. 读文件先剥 UTF-8 BOM；写文件 UTF-8 无 BOM。
2. JSONL 只用 `\n`；每行一个 JSON；末尾保留换行；不写 CRLF。
3. JSON 库固定 `org.json:json:20240303`；不换 `kotlinx.serialization`。
4. `Float` 字段保持 `Float.toDouble()` 语义（例如 `0.8f` → `0.800000011920929`）。
5. 稀疏字段不补全：空列表不写键、`-1` 哨兵不写。
6. `version` 高于本版本时拒写（`StoreVersion` 语义），不「尽力写」。
7. 时间：epoch millis 原样；`sentence_card.date` 与 `reminder_state.day` 用本地时区 `yyyy-MM-dd`；`exam_goal.epochDay` 是本地日历日序号。
8. `content_packs` 的 `chars` 是 Kotlin `String.length`（UTF-16 代码单元），不是字节数。
9. 原子写：同目录临时文件 + `force` + `.bak` 轮换 + `ATOMIC_MOVE`；Windows 目录 fsync 静默降级。
10. `record_id_seq` 不写入备份；恢复后以 `max(cursor, maxId)+1` 兜底。

## 5. 当前实现与 Android 的差异

| 差异 | 影响 |
|---|---|
| 新增 `record_id_seq` | 记录 id 不复用，墨迹/复习日志不会错挂；Android 忽略 |
| 导入前预备份 | 更安全；占少量磁盘 |
| 更严的 ZIP 校验 | 极少数畸形 Android 包会被拒绝 |
| 无 AI 数据 | 不读写 `ai_history/`、`ai_analysis/`；Pro 备份相应类目忽略 |
| 原子写无目录 fsync | Windows 无对应 API；rename 本身原子 |
