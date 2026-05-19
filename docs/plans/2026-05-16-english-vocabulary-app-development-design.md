# 英语单词 App 长期维护版开发文档

日期：2026-05-16  
状态：已确定方案，可直接进入开发  
目标平台：Android  
最终技术路线：Kotlin + Jetpack Compose + Room + DataStore + WorkManager + FSRS-Kotlin  

依据文档：

- `docs/research/2026-05-16-english-vocabulary-app-open-source-research.md`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\docs\词库来源与许可证.md`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\scripts\build-vocab-dataset.js`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\data\sources.json`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\learningState.js`

## 1. 最终决策

本项目不再采用 Vue 3 + Capacitor 快速 MVP 路线，也不再保留多技术方案选择。后续开发全部按长期维护的原生 Android 路线执行：

- 语言：Kotlin。
- UI：Jetpack Compose + Material 3 自定义主题。
- 架构：Clean Architecture + MVVM + Repository + UseCase。
- 数据库：Room。
- 设置存储：DataStore Preferences。
- 后台任务：WorkManager。
- 依赖注入：Hilt。
- 异步：Kotlin Coroutines + Flow。
- 序列化：kotlinx.serialization。
- 复习算法：FSRS-Kotlin；通过 `ReviewScheduler` 接口隔离库 API。
- 图表：Vico Compose。
- 测试：JUnit、Robolectric、Room in-memory、Turbine、Compose UI Test、GitHub Actions。
- 构建：Gradle Kotlin DSL。

明确不做：

- 不使用 Vue、Capacitor、UnoCSS、pnpm、ts-fsrs 作为 App 主技术栈。
- 不 fork AnkiDroid、ForgetMeNot 或其他大型背词项目。
- 不引入账号、云同步、社交排行、付费订阅、AI 例句。
- 不手工维护第二套词库。

开发目标是做一个简洁、高级、接近 Apple 风格但仍符合 Android 交互习惯的本地离线背单词 App。首页就是学习台，不做营销页。

## 2. 第一版产品范围

第一版必须完整闭环：

- CET4、CET6、考研、IELTS、TOEFL 词书选择。
- publish-safe 词库导入。
- 每日新词上限。
- 今日复习队列。
- 单词卡四档反馈：`again`、`hard`、`good`、`easy`。
- 本地 `ReviewLog`。
- `ReviewLog` 可重放生成当前 `ReviewCard`。
- 今日统计。
- 词书进度。
- 未来 14/30 天复习负载。
- 连续学习与月历热力图。
- 困难词统计。
- 数据导出 JSON。
- 深色 / 浅色模式。
- 360px 宽度下无文字重叠。

第一版不做：

- 账号、登录、云同步、多端同步。
- 社交排行、复杂打卡任务。
- AI 生成例句。
- 付费、订阅。
- 自定义词书。
- 数据导入恢复。

## 3. 开源参考与复用边界

| 项目 | 用法 | 决策 |
|---|---|---|
| AnkiDroid | 参考离线卡片、复习事务、导入导出、测试策略 | 不复制代码，不 fork |
| FSRS-Kotlin | Kotlin FSRS v6 调度实现 | 确定接入 |
| open-spaced-repetition / fsrs-rs | 参考 FSRS 官方生态和算法语义 | 不作为第一版 Android 运行时依赖 |
| Vico | Compose 图表库 | 用于复习负载、趋势图、统计图 |
| Apple HIG | 参考克制、清晰、层级感 | 不照搬 iOS 控件，不分发 SF Pro |

许可证规则：

- 所有第三方库必须记录许可证。
- GPL/AGPL 项目只能参考，不复制代码。
- 词库发布版必须使用 publish-safe 数据。

## 4. 项目结构

采用多模块 Android 工程，避免后续功能变多时所有代码挤在 `app` 模块。

```text
android-vocabulary/
  settings.gradle.kts
  build.gradle.kts
  gradle/libs.versions.toml
  app/
  core/
    model/
    common/
    database/
    datastore/
    domain/
    scheduler/
    vocabulary/
    stats/
    designsystem/
    testing/
  feature/
    today/
    review/
    wordbook/
    stats/
    settings/
  worker/
  scripts/
    vocab/
    test/
  docs/
```

模块职责：

- `:app`：Application、MainActivity、导航、Hilt 根配置。
- `:core:model`：纯数据模型和枚举。
- `:core:common`：时间、Result、错误类型、ID、调度器工具。
- `:core:database`：Room database、Entity、Dao、Migration。
- `:core:datastore`：用户设置。
- `:core:domain`：UseCase、业务规则、纯函数。
- `:core:scheduler`：FSRS adapter、调度接口、算法版本。
- `:core:vocabulary`：词库导入、词条 Repository。
- `:core:stats`：统计计算。
- `:core:designsystem`：主题、颜色、排版、基础组件。
- `:core:testing`：测试 fixture、fake repository、固定时钟。
- `:feature:*`：页面 UI、ViewModel、页面状态。
- `:worker`：WorkManager 任务，如提醒和统计预计算。
- `scripts/vocab`：调用参考扩展构建 publish-safe 词库并复制到 Android assets。
- `scripts/test`：可重复执行的集成测试脚本。

禁止：

- feature 模块直接访问 Room Dao。
- Compose UI 直接调用数据库。
- domain 模块依赖 Android framework。
- 统计直接读 UI 状态。

## 5. Gradle 与依赖

使用 Gradle Kotlin DSL 和版本目录：

```text
gradle/libs.versions.toml
```

必须使用：

- Android Gradle Plugin。
- Kotlin Android。
- Kotlin Serialization。
- KSP。
- Hilt。
- Room。
- DataStore Preferences。
- WorkManager。
- Jetpack Compose BOM。
- Lifecycle ViewModel Compose。
- Navigation Compose。
- Vico Compose。
- kotlinx-datetime 或 java.time。

测试依赖：

- JUnit。
- Robolectric。
- AndroidX Test。
- Room testing。
- Turbine。
- MockK 或不使用 mocking、优先 fake。
- Compose UI Test。

构建命令：

```bash
./gradlew ktlintFormat
./gradlew ktlintCheck
./gradlew detekt
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
```

Windows PowerShell 可执行：

```powershell
.\gradlew.bat ktlintCheck detekt testDebugUnitTest assembleDebug
```

CI 至少运行：

```bash
./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug
```

真机或模拟器可用时再运行：

```bash
./gradlew connectedDebugAndroidTest
```

## 6. 包名与命名

推荐包名：

```text
com.zzz.androidvocab
```

命名规则：

- Entity：`WordEntryEntity`、`ReviewCardEntity`。
- Domain model：`WordEntry`、`ReviewCard`。
- Dao：`WordDao`、`ReviewDao`。
- Repository interface：`VocabularyRepository`。
- Repository impl：`OfflineVocabularyRepository`。
- UseCase：`GetTodayQueueUseCase`、`SubmitReviewFeedbackUseCase`。
- ViewModel：`TodayViewModel`。
- UiState：`TodayUiState`。
- Event：`ReviewEvent`。

代码注释用英文，只解释 why，不解释 how。

## 7. 词库方案

### 7.1 数据来源

词库必须复用：

- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\scripts\build-vocab-dataset.js`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\data\*.json`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\data\sources.json`

发布构建必须调用：

```bash
node D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\scripts\build-vocab-dataset.js --publish-safe --output-dir <output>
```

publish-safe 数量必须是：

| 词书 | 文件 | 数量 |
|---|---|---:|
| CET4 | `cet4.json` | 3846 |
| CET6 | `cet6.json` | 5406 |
| KAOYAN | `kaoyan.json` | 4801 |
| IELTS | `ielts.json` | 5038 |
| TOEFL | `toefl.json` | 6970 |

### 7.2 发布合规

- 发布版不得包含 `publishBlocking=true` 来源派生的补充词条或增强字段。
- `KyleBing/english-vocabulary` 和 `exam-data/NETEMVocabulary` 未取得授权前不能进入发布数据。
- 必须打包 `sources.json`，并在 Settings 中提供来源与许可证页面。
- 不重新抓词库。
- 不手工维护平行词库。

### 7.3 构建产物

Android assets 目录：

```text
app/src/main/assets/vocab/
  cet4.json
  cet6.json
  kaoyan.json
  ielts.json
  toefl.json
  sources.json
  manifest.json
```

`manifest.json`：

```json
{
  "buildTarget": "publish",
  "generatedAt": "2026-05-16",
  "books": {
    "CET4": { "file": "cet4.json", "count": 3846 },
    "CET6": { "file": "cet6.json", "count": 5406 },
    "KAOYAN": { "file": "kaoyan.json", "count": 4801 },
    "IELTS": { "file": "ielts.json", "count": 5038 },
    "TOEFL": { "file": "toefl.json", "count": 6970 }
  }
}
```

### 7.4 导入流程

`ImportVocabularyUseCase` 执行：

1. 读取 assets `manifest.json`。
2. 校验五本词书文件存在。
3. 校验 `sources.json` 存在。
4. 对每本词书解析 JSON。
5. 标准化 `word`：trim、lowercase。
6. 在 Room transaction 中 upsert `word_entries`。
7. 写入 `wordbook_memberships`。
8. 写入 `word_aliases`。
9. 写入 `source_manifest` 和 `vocabulary_import_runs`。
10. 校验每本词书导入数量。

失败规则：

- 任一词书数量不匹配：事务回滚。
- `sources.json` 缺失：阻断导入。
- 发现 publish-blocking 来源：阻断发布构建。
- 单条缺失 `word` 或 `meaning`：跳过并记录错误。
- 可选字段缺失：保留空值，不阻断。

## 8. 数据模型

事实来源优先级：

1. 词库 JSON 与 `sources.json`。
2. `ReviewLog`。
3. 由日志重放得到的 `ReviewCard`。
4. 由日志聚合得到的 `DailyStats`。

### 8.1 Domain 模型

```kotlin
enum class BookCode { CET4, CET6, KAOYAN, IELTS, TOEFL }

enum class ReviewRating { Again, Hard, Good, Easy }

enum class ReviewState { New, Learning, Review, Relearning, Mastered }

data class WordEntry(
    val id: WordId,
    val word: String,
    val meaning: String,
    val phonetic: String?,
    val partOfSpeech: String?,
    val definition: String?,
    val cefrLevel: String?,
    val cefrRank: Int,
    val frequency: Double,
    val sourceFlags: List<String>,
    val coverageTier: String?
)
```

### 8.2 Room 表

必须建立这些表：

- `word_entries`
- `wordbook_memberships`
- `word_aliases`
- `review_cards`
- `review_logs`
- `daily_stats`
- `app_settings_snapshot`
- `source_manifest`
- `vocabulary_import_runs`

`word_entries`：

```kotlin
@Entity(
    tableName = "word_entries",
    indices = [Index(value = ["word"], unique = true)]
)
data class WordEntryEntity(
    @PrimaryKey val id: String,
    val word: String,
    val meaning: String,
    val phonetic: String?,
    val partOfSpeech: String?,
    val definition: String?,
    val cefrLevel: String?,
    val cefrRank: Int,
    val frequency: Double,
    val sourceFlagsJson: String,
    val coverageTier: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

`wordbook_memberships`：

```kotlin
@Entity(
    tableName = "wordbook_memberships",
    primaryKeys = ["wordId", "bookCode"],
    indices = [
        Index(value = ["bookCode", "orderIndex"]),
        Index(value = ["wordId"])
    ]
)
data class WordBookMembershipEntity(
    val wordId: String,
    val bookCode: String,
    val orderIndex: Int,
    val examFrequencyScore: Double,
    val examPriorityScore: Double,
    val isPhraseBacked: Boolean,
    val phraseCount: Int
)
```

`review_cards`：

```kotlin
@Entity(
    tableName = "review_cards",
    indices = [
        Index(value = ["wordId", "bookCode"], unique = true),
        Index(value = ["bookCode", "dueAt"]),
        Index(value = ["state"])
    ]
)
data class ReviewCardEntity(
    @PrimaryKey val id: String,
    val wordId: String,
    val bookCode: String,
    val state: String,
    val difficulty: Double?,
    val stability: Double?,
    val retrievability: Double?,
    val scheduledDays: Int,
    val dueAt: Instant?,
    val lastReviewAt: Instant?,
    val reviewCount: Int,
    val lapseCount: Int,
    val firstReviewedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

`review_logs`：

```kotlin
@Entity(
    tableName = "review_logs",
    indices = [
        Index(value = ["localDay"]),
        Index(value = ["wordId", "reviewedAt"]),
        Index(value = ["cardId", "reviewedAt"])
    ]
)
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val wordId: String,
    val bookCode: String,
    val rating: String,
    val reviewedAt: Instant,
    val localDay: String,
    val elapsedDays: Int?,
    val scheduledDaysBefore: Int?,
    val scheduledDaysAfter: Int?,
    val difficultyBefore: Double?,
    val difficultyAfter: Double?,
    val stabilityBefore: Double?,
    val stabilityAfter: Double?,
    val retrievabilityBefore: Double?,
    val retrievabilityAfter: Double?,
    val durationMs: Long,
    val algorithm: String,
    val algorithmVersion: String
)
```

`daily_stats` 是缓存，不是真实来源：

```kotlin
@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey val localDay: String,
    val newCount: Int,
    val reviewCount: Int,
    val againCount: Int,
    val hardCount: Int,
    val goodCount: Int,
    val easyCount: Int,
    val completedCount: Int,
    val recallAccuracy: Double,
    val passRate: Double,
    val estimatedMinutes: Int,
    val updatedAt: Instant
)
```

### 8.3 TypeConverter

Room 需要提供：

- `Instant` <-> ISO string。
- `List<String>` <-> JSON string。
- `BookCode` <-> string。
- `ReviewRating` <-> string。
- `ReviewState` <-> string。

时间统一：

- 数据库存 UTC `Instant`。
- 统计聚合用本地日期 `localDay`。
- domain 逻辑通过 `ClockProvider` 注入当前时间。

## 9. Repository 与 UseCase

### 9.1 Repository

接口放在 `:core:domain`，实现放在数据模块。

```kotlin
interface VocabularyRepository {
    fun observeBookProgress(): Flow<List<BookProgress>>
    fun searchWords(query: String, bookCode: BookCode?): Flow<List<WordEntry>>
    suspend fun importPublishSafeVocabulary(): ImportResult
}

interface ReviewRepository {
    fun observeTodayQueue(now: Instant): Flow<TodayQueue>
    suspend fun getOrCreateNewCard(wordId: WordId, bookCode: BookCode): ReviewCard
    suspend fun submitFeedback(command: SubmitFeedbackCommand): ReviewResult
    suspend fun replayLogs(cardId: CardId): ReviewCard
}

interface StatsRepository {
    fun observeTodayStats(localDay: LocalDate): Flow<TodayStats>
    fun observeBookStats(): Flow<List<BookStats>>
    fun observeReviewLoad(days: Int): Flow<List<DailyReviewLoad>>
    fun observeDifficultWords(days: Int): Flow<List<DifficultWord>>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateDailyNewLimit(value: Int)
    suspend fun updateSelectedBooks(bookCodes: Set<BookCode>)
    suspend fun updateThemeMode(themeMode: ThemeMode)
}
```

### 9.2 UseCase

必须实现：

- `ImportVocabularyUseCase`
- `GetTodayOverviewUseCase`
- `GetTodayQueueUseCase`
- `StartReviewSessionUseCase`
- `SubmitReviewFeedbackUseCase`
- `ReplayReviewLogsUseCase`
- `GetBookProgressUseCase`
- `GetReviewLoadUseCase`
- `GetRetentionStatsUseCase`
- `GetStreakUseCase`
- `GetDifficultWordsUseCase`
- `ExportUserDataUseCase`

UseCase 规则：

- 单个 UseCase 只处理一个业务动作。
- UseCase 不依赖 Compose。
- UseCase 不直接读系统时间，必须依赖 `ClockProvider`。
- 有写操作的 UseCase 必须明确事务边界。

## 10. 复习调度

### 10.1 反馈映射

| UI 文案 | 内部值 | FSRS rating | 含义 |
|---|---|---|---|
| 忘了 | `again` | `Again` | 完全想不起来 |
| 模糊 | `hard` | `Hard` | 想起来但吃力 |
| 记得 | `good` | `Good` | 正常想起 |
| 熟悉 | `easy` | `Easy` | 很熟 |

### 10.2 Scheduler 接口

`ReviewScheduler` 放在 `:core:scheduler`：

```kotlin
interface ReviewScheduler {
    val algorithm: SchedulerAlgorithm
    fun schedule(input: ScheduleInput): ScheduleResult
}

data class ScheduleInput(
    val card: ReviewCard,
    val rating: ReviewRating,
    val reviewedAt: Instant,
    val targetRetention: Double
)

data class ScheduleResult(
    val nextCard: ReviewCard,
    val logPatch: ReviewLogPatch,
    val algorithmVersion: String
)
```

第一版实现：

- `FsrsKotlinReviewScheduler`
- `algorithm = fsrs`
- `algorithmVersion = fsrs-kotlin-v6`

第一版只实现 FSRS-Kotlin。其他调度算法不进入代码实现；如果 FSRS-Kotlin API 发生变化，只允许在 `FsrsKotlinReviewScheduler` 内适配，不切换算法。

### 10.3 复习事务

`SubmitReviewFeedbackUseCase` 必须在 Room transaction 中执行：

1. 读取当前 `ReviewCard`。
2. 调用 `ReviewScheduler.schedule`。
3. 插入不可变 `ReviewLog`。
4. 更新 `ReviewCard`。
5. 标记 `DailyStats` 需要刷新，或直接增量更新。
6. 返回下一张卡片摘要。

事务要求：

- 不允许只更新 `ReviewCard` 而没有 `ReviewLog`。
- 不允许 UI 先乐观删除卡片但落库失败。
- scheduler 抛错时不写任何学习数据。

### 10.4 今日队列

`GetTodayQueueUseCase` 生成：

- 到期复习词：`dueAt <= now`。
- 当日新词：选中词书中尚未创建卡片的词，数量不超过每日新词上限。

排序：

1. 到期更久的复习词。
2. 近 30 天 `again` / `hard` 高频词。
3. 当日新词。
4. 同级按 `bookCode` 和 `orderIndex`。

每日新词上限：

- 默认 20。
- 最小 0。
- 最大 100。
- 按本地日期计算当天已新学数量。

## 11. 统计口径

统计参考墨墨背单词的表达，但所有指标使用本项目自己的可解释口径。

### 11.1 今日概览

来源：当天 `review_logs` + 当前 `review_cards`。

- 新学：当天第一次复习的卡片数。
- 复习：当天非第一次复习的卡片数。
- 完成：当天日志总数。
- 剩余：今日队列未完成数量。
- 正确率：`(good + easy) / total`。
- 通过率：`(hard + good + easy) / total`。
- 预计耗时：剩余卡片数乘以近 7 天平均单卡耗时。

### 11.2 词书进度

来源：`wordbook_memberships` + `review_cards`。

- 总词数：词书 membership 数。
- 未学：没有 `ReviewCard`。
- 学习中：`state in (New, Learning, Relearning)`。
- 待复习：`state = Review` 且 `dueAt <= now`。
- 熟悉：`state = Review` 且未到期。
- 掌握：`scheduledDays >= 21` 且 `retrievability >= 0.85`。

### 11.3 未来复习负载

来源：`review_cards.dueAt`。

- Today 页显示未来 14 天。
- Stats 页显示未来 30 天。
- 不为了图表修改真实 dueAt。

### 11.4 记忆保持

FSRS 主线：

- 使用 `retrievability` 平均值、P25、P50 展示保持情况。
- 支持近 7 天、近 30 天、按词书筛选。

### 11.5 连续学习

来源：`review_logs.localDay`。

- 当天至少一条 `ReviewLog` 记为活跃。
- current streak 从本地今天向前连续计算。
- max streak 从全部活跃日计算。
- 月历热力图按复习次数分 4 档颜色。

### 11.6 困难词

来源：近 30 天 `review_logs`。

```text
difficultyScore = againCount * 3.0 + hardCount * 1.5
```

展示前 10 个。第一版不做独立错题本。

## 12. UI/UX 规格

### 12.1 总原则

- 简洁、高级、接近 Apple 风格，但不照搬 iOS。
- 使用 Jetpack Compose 和 Material 3，自定义颜色、圆角、间距和字重。
- 首页就是学习台，不做营销页。
- 不做大面积渐变、玻璃拟态、装饰光斑。
- 不在卡片里套卡片。
- 卡片半径 8-12dp。
- 阴影极轻，优先用留白和分割线。
- 360px 宽度下文本和按钮不能重叠。
- 使用 Android 系统字体，不分发 SF Pro。

### 12.2 设计 Token

`core/designsystem` 定义：

```kotlin
object VocabColors {
    val LightBackground = Color(0xFFF5F5F7)
    val DarkBackground = Color(0xFF0B0B0F)
    val Primary = Color(0xFF007AFF)
    val Success = Color(0xFF34C759)
    val Danger = Color(0xFFFF3B30)
}
```

尺寸：

- Page horizontal padding：20dp，窄屏可降到 16dp。
- Card radius：12dp。
- Control radius：10dp。
- Bottom navigation height：64dp + system inset。
- Review feedback button min height：48dp。

排版：

- 大数字：32sp，最多用于 Today 核心数字。
- 单词：40sp 起，长词自动降到 30sp 或换行。
- 页面标题：24sp。
- 卡片标题：17sp。
- 正文：15sp。
- 辅助信息：13sp。

### 12.3 导航

底部 5 个 Tab：

- Today
- Review
- Wordbook
- Stats
- Settings

实现：

- Navigation Compose。
- `VocabNavHost` 放在 `:app`。
- 每个 feature 暴露 `navigation` 扩展函数。

### 12.4 Today 页面

功能：

- 今日剩余卡片数。
- 今日新词 / 复习词。
- 主按钮：开始学习。
- 今日完成进度。
- 预计耗时。
- 未来 14 天复习负载小图。
- 当前词书。

状态：

- `Loading`
- `Ready`
- `Empty`
- `ImportRequired`
- `Error`

### 12.5 Review 页面

功能：

- 复习进度。
- 单词展示。
- 音标。
- 发音按钮，第一版可用 Android TTS。
- 翻面显示释义。
- 词性、别名、CEFR、词书标签。
- 四档反馈。

交互：

- 初始只显示单词。
- 点击卡片或“显示释义”翻面。
- 翻面后显示四档反馈。
- 点击反馈后提交事务，成功再进入下一张。
- 失败时保留当前卡片并提示重试。

窄屏要求：

- 四个按钮可以 2x2 排列。
- 长释义最多展示 3 行，可展开。
- 长单词不能撑破屏幕。

### 12.6 Wordbook 页面

功能：

- 词书选择。
- 每本词书进度。
- 搜索词条。
- 状态筛选：未学、学习中、待复习、熟悉、掌握。
- 词条详情。

第一版不做：

- 自定义词书。
- 批量编辑。

### 12.7 Stats 页面

功能：

- 今日概览。
- 连续学习。
- 近 7/30 天复习量。
- 记忆保持。
- 未来 30 天复习负载。
- 词书进度。
- 掌握分布。
- 困难词 Top 10。

图表：

- Vico 用于柱状图和趋势图。
- 月历热力图用自绘 Compose grid。
- 颜色克制，避免高饱和大面积色块。

### 12.8 Settings 页面

功能：

- 每日新词上限。
- 默认词书。
- 目标保持率。
- 提醒开关和提醒时间。
- 主题：跟随系统、浅色、深色。
- 数据导出 JSON。
- 词库来源与许可证。
- App 版本、词库版本、算法版本。

第一版只做导出，不做导入恢复。

## 13. ViewModel 与 UI 状态

ViewModel 规则：

- ViewModel 只依赖 UseCase。
- UiState 必须是不可变 data class。
- 页面事件用 sealed interface。
- 长任务用 `viewModelScope`。
- Flow 使用 `stateIn` 转成 `StateFlow`。

示例：

```kotlin
data class TodayUiState(
    val isLoading: Boolean,
    val overview: TodayOverview?,
    val reviewLoad: List<DailyReviewLoad>,
    val selectedBooks: List<BookCode>,
    val errorMessage: String?
)

sealed interface TodayEvent {
    data object StartReview : TodayEvent
    data object RetryImport : TodayEvent
}
```

Compose 规则：

- 页面函数只接收 `uiState` 和事件回调。
- 不在 Composable 中启动数据库写操作。
- 预览使用 fake state。
- 组件可复用但不过度抽象。

## 14. 后台任务与提醒

使用 WorkManager：

- `DailyReminderWorker`：按设置时间提醒用户复习。
- `StatsRefreshWorker`：低频刷新统计缓存。
- `VocabularyIntegrityCheckWorker`：开发或调试构建中校验词库完整性。

第一版只要求：

- 设置中能保存提醒开关和时间。
- 通知权限处理清晰。
- App 内能根据设置安排或取消提醒。

不要求：

- 精准闹钟。
- 桌面小组件。
- 跨设备同步。

## 15. 数据导出

导出 JSON 必须包含：

- `exportedAt`
- `appVersion`
- `databaseVersion`
- `vocabularyManifest`
- `algorithm`
- `settings`
- `reviewCards`
- `reviewLogs`
- `dailyStats`

第一版导出不加密。文件名：

```text
android-vocab-export-YYYYMMDD-HHmmss.json
```

导出失败要显示明确原因。

## 16. 错误处理

统一错误类型：

```kotlin
sealed interface AppError {
    data class VocabularyImportFailed(val reason: String) : AppError
    data class DatabaseWriteFailed(val reason: String) : AppError
    data class SchedulerFailed(val reason: String) : AppError
    data class ExportFailed(val reason: String) : AppError
}
```

规则：

- 导入失败：阻断学习入口，允许重试。
- 复习写入失败：当前反馈不生效，卡片不跳过。
- scheduler 失败：不写日志，不更新卡片。
- 统计失败：退回实时计算或显示空状态。
- 导出失败：显示失败原因。

## 17. 测试计划

### 17.1 必跑命令

```powershell
.\gradlew.bat ktlintCheck detekt testDebugUnitTest assembleDebug
```

有 Android 设备或模拟器时：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

### 17.2 词库测试

- publish-safe 构建后五本词书数量匹配：
  - CET4 3846
  - CET6 5406
  - KAOYAN 4801
  - IELTS 5038
  - TOEFL 6970
- 多词书同词只创建一个 `WordEntry`。
- `sources.json` 被完整打包和展示。
- 发布构建不包含 `publishBlocking=true` 派生数据。
- 导入失败能回滚。

### 17.3 调度测试

- 固定 `ClockProvider`，同一输入得到稳定输出。
- `again` 进入短间隔。
- 连续 `good/easy` 后 `scheduledDays` 增长。
- 每次反馈先写 `ReviewLog`，再更新 `ReviewCard`。
- `ReviewLog` 可重放生成当前 `ReviewCard`。
- 算法版本写入日志。

### 17.4 统计测试

- 今日新学、复习、完成、正确率可由日志复算。
- 未来 14/30 天 due count 正确。
- 连续学习按本地日期计算。
- 月历热力图不受 UTC 跨天影响。
- 困难词排序稳定。
- 删除 `DailyStats` 后可重建。

### 17.5 UI 测试

Compose screenshot 或 UI test 覆盖：

- 360x800
- 390x844
- 412x915
- 768x1024

检查：

- Today、Review、Wordbook、Stats、Settings 无重叠。
- 长单词、长释义、无音标数据正常。
- 深色 / 浅色模式可读。
- Review 四个反馈按钮窄屏可点击。

### 17.6 集成测试

Room in-memory 测试：

- 词库导入事务。
- 复习事务。
- ReviewLog replay。
- 统计聚合。

Android instrumented test：

- Room migration。
- DataStore 设置保存。
- WorkManager 任务调度。
- Compose 主路径。

## 18. 开发阶段

### 阶段 0：原生工程脚手架

交付：

- Gradle Kotlin DSL 多模块工程。
- Hilt 配置。
- Compose + Material 3。
- ktlint、detekt、测试框架。
- 五个页面空壳和底部导航。

验收：

- `ktlintCheck` 通过。
- `detekt` 通过。
- `testDebugUnitTest` 通过。
- `assembleDebug` 通过。

### 阶段 1：词库导入

交付：

- `scripts/vocab/build-publish-safe.ps1`。
- assets 词库产物。
- Room schema。
- `ImportVocabularyUseCase`。
- 来源与许可证页面。

验收：

- 五本词书数量匹配。
- 多词书同词去重。
- `sources.json` 可展示。

### 阶段 2：复习核心

交付：

- `ReviewCard`。
- `ReviewLog`。
- FSRS-Kotlin adapter。
- 今日队列。
- Review 页面主路径。

验收：

- 四档反馈能生成下一次复习时间。
- 每次反馈都有日志。
- App 重启后队列不丢。

### 阶段 3：统计

交付：

- 今日概览。
- 词书进度。
- 未来 14/30 天复习负载。
- 记忆保持。
- 连续学习。
- 月历热力图。
- 困难词。

验收：

- 统计可由 `ReviewLog` 和 `ReviewCard` 复算。
- `DailyStats` 删除后可重建。

### 阶段 4：设置、提醒、导出

交付：

- DataStore 设置。
- 每日新词上限。
- 主题模式。
- 提醒开关和时间。
- WorkManager 通知。
- JSON 导出。

验收：

- 设置持久化。
- 提醒可安排和取消。
- 导出 JSON 包含必要数据。

### 阶段 5：UI 打磨与真机验证

交付：

- Apple 风格视觉统一。
- 深色模式。
- 窄屏适配。
- Android 真机主路径验证。

验收：

- 360px 宽度无重叠。
- Review 主路径流畅。
- 统计图可读。
- 崩溃率为 0。

## 19. 发布阻断项

以下任一项未满足，不允许发布：

- 使用了非 publish-safe 数据。
- 缺失 `sources.json` 或来源许可证页面。
- `ReviewLog` 未完整记录。
- 统计不能从日志复算。
- 今日队列重启后丢失。
- Review 事务可能只写卡片不写日志。
- 360px 宽度有文字或按钮重叠。
- `ktlintCheck`、`detekt`、`testDebugUnitTest`、`assembleDebug` 任一失败。
- 发布包包含 GPL/AGPL 项目的复制代码。

## 20. AI 开发执行规则

后续 AI 直接按本文件开发，不再重新讨论技术栈。

执行顺序：

1. 先建 Gradle 多模块工程。
2. 再建 core model、database、datastore。
3. 再实现词库导入。
4. 再实现 scheduler 和 review transaction。
5. 再做 Today / Review 页面。
6. 再做 Stats / Wordbook / Settings。
7. 最后做 WorkManager、导出和 UI 打磨。

每个阶段必须：

- 写代码。
- 补测试。
- 跑最小验证命令。
- 给出通过 / 失败结论。

禁止：

- 回到 Vue / Capacitor 路线。
- 只写 UI 不写 ReviewLog。
- 只做计数器统计。
- 绕过 publish-safe。
- 为单次使用创建复杂抽象。
- 把第一版扩展成账号、同步或社交系统。

## 21. 参考链接

本地参考：

- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\docs\词库来源与许可证.md`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\data\sources.json`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\learningState.js`

外部参考：

- AnkiDroid: https://github.com/ankidroid/Anki-Android
- FSRS-Kotlin: https://github.com/open-spaced-repetition/FSRS-Kotlin
- Open Spaced Repetition: https://github.com/open-spaced-repetition
- Vico: https://github.com/patrykandpatrick/vico
- Android Jetpack Compose: https://developer.android.com/jetpack/compose
- Android Room: https://developer.android.com/training/data-storage/room
- Android DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Android WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager
- Apple Human Interface Guidelines: https://developer.apple.com/design/human-interface-guidelines/
