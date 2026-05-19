# 英语单词 App 开源调研报告

日期：2026-05-16  
目标：开发一个安卓英语单词 App，视觉简洁高级、接近 Apple 风格；复用 `Bilibili-Subtitle-Vocabulary-Extension` 词库；提供类似墨墨背单词的统计；按遗忘曲线规划复习。

## 1. 结论先行

1. 不建议 fork AnkiDroid 直接改。AnkiDroid 是成熟开源 SRS Android 项目，可参考调度、离线、统计、导入导出和测试策略，但产品形态太重，且许可证会影响后续闭源/商用选择。
2. 词库应直接复用本地扩展的 `scripts/build-vocab-dataset.js` 与 `data/*.json` 产物，不重新抓词库。当前开发数据覆盖 CET4、CET6、考研、IELTS、TOEFL，五本书去重后约 11,438 个词。
3. 发布版必须处理词库许可证边界。当前扩展已经区分 development 与 publish-safe 数据：KyleBing、NETEM 来源在本地开发数据里增强了核心词表，但发布前要取得授权或使用 publish-safe 构建裁剪。
4. 复习算法建议优先采用 FSRS，而不是自研“遗忘曲线公式”。FSRS 已经是开源社区主流间隔重复算法方向，并被 Anki 生态采用；如果 Android 原生集成成本过高，MVP 可先用简化 SM-2/现有 `LearningState` 策略，但要保留 ReviewLog，以便迁移到 FSRS。
5. “参考墨墨”应参考统计表达，不应照搬闭源实现：重点做每日复习量、未来负载、掌握度分布、记忆保持率、连续学习、词书进度，而不是堆复杂功能。
6. 技术路线推荐两档：长期产品用 Kotlin + Jetpack Compose + Room + WorkManager + Vico/MPAndroidChart；快速 MVP 用 Vue 3 + Capacitor + SQLite + ts-fsrs。不要用 fork 大型背词应用作为底座。

## 2. 本地词库调研

参考仓库：`D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension`

关键文件：

- `bilibili-vocab-extension/scripts/build-vocab-dataset.js`
- `bilibili-vocab-extension/data/cet4.json`
- `bilibili-vocab-extension/data/cet6.json`
- `bilibili-vocab-extension/data/kaoyan.json`
- `bilibili-vocab-extension/data/ielts.json`
- `bilibili-vocab-extension/data/toefl.json`
- `docs/词库来源与许可证.md`
- `bilibili-vocab-extension/data/sources.json`

### 2.1 当前开发数据规模

| 词书 | 开发数据词数 | core 词数 | 含发布阻断来源字段/条目 |
|---|---:|---:|---:|
| CET4 | 4,996 | 4,544 | 4,544 |
| CET6 | 6,035 | 3,991 | 3,991 |
| KAOYAN | 5,361 | 5,222 | 5,222 |
| IELTS | 5,038 | 0 | 0 |
| TOEFL | 6,970 | 0 | 0 |

五本词书去重词数：11,438。  
同时出现在多个词书中的词数：6,877。

### 2.2 publish-safe 构建规模

本地执行 `node bilibili-vocab-extension\scripts\build-vocab-dataset.js --publish-safe --output-dir <temp>` 后得到：

| 词书 | publish-safe 词数 |
|---|---:|
| CET4 | 3,846 |
| CET6 | 5,406 |
| KAOYAN | 4,801 |
| IELTS | 5,038 |
| TOEFL | 6,970 |

结论：如果 App 要发布给外部用户，默认应该使用 publish-safe 数据；如果必须做到“和扩展开发数据完全一样”，需要先解决 KyleBing 与 NETEM 的再分发许可。

### 2.3 词条字段

完整词条包含：

- 基础：`word`、`meaning`、`level`、`phonetic`、`partOfSpeech`、`definition`
- 难度/频率：`cefrLevel`、`cefrRank`、`frequency`
- 召回辅助：`aliases`、`altMeanings`
- 来源与优先级：`coverageTier`、`sourceFlags`、`examFrequencyScore`、`examPriorityScore`
- 短语支持：`isPhraseBacked`、`phraseCount`

App 内建议拆成可查询结构：

- `WordEntry`：词条主体
- `WordBookMembership`：一个词属于哪些词书
- `WordAlias`：中文别名、替代释义
- `ReviewCard`：用户对某词的学习状态
- `ReviewLog`：每次复习记录，后续 FSRS/统计全部依赖它
- `DailyStats`：每日聚合快照，减少统计页实时重算

## 3. 开源项目参考

| 项目 | 可参考点 | 可复用边界 | 结论 |
|---|---|---|---|
| AnkiDroid | Android 离线卡片、成熟 SRS、导入导出、同步、测试体系 | 许可证与产品体量都较重，不适合直接 fork 成轻量背词 App | 参考架构和测试，不复制代码 |
| FSRS / open-spaced-repetition | 现代间隔重复调度、记忆稳定度/难度/可回忆率建模 | Android 原生可能需要 Rust/FFI 或找 JVM/Kotlin 实现；Vue/Capacitor 可直接考虑 ts-fsrs | 推荐作为长期调度核心 |
| SuperMemo SM-2 | 简单、经典、易实现 | 个性化能力弱于 FSRS | 可作为 MVP fallback |
| ForgetMeNot | 开源 Android 记忆卡片、TTS、导入导出、自定义练习 | GPL-3.0，商业闭源不能直接复用代码 | 参考功能边界 |
| StackSRS | 极简 Android SRS、MIT、TTS、词库管理 | 算法偏简，不是严格遗忘曲线 | 可参考轻量交互 |
| WordEcho | 阅读/查词/背词一体化 | CC BY-NC-SA，不能用于商业代码复用 | 只参考产品思路 |
| LibreLingo / LWT / VocabSieve | 语言学习内容、阅读生词、Anki 工作流 | 不适合作为 Android App 底座 | 参考内容组织与导入导出 |

## 4. 统计功能：参考墨墨，但做自己的统计口径

墨墨类产品的核心不是“图多”，而是让用户知道三件事：

1. 今天要完成什么：今日新词、今日复习、已完成、剩余压力。
2. 最近学得怎么样：连续学习、每日复习量、正确率、掌握词数变化。
3. 以后会不会爆量：未来 7/14/30 天预计复习负载。

建议 App 第一版统计页包含：

- 今日概览：新学词、复习词、正确率、预计耗时
- 记忆保持：按 FSRS 可回忆率或简化 retention 估计显示
- 复习负载：未来 30 天 due count 柱状图
- 词书进度：每本词书总词数、已学、掌握、未学
- 掌握分布：未学、学习中、待复习、熟悉、已掌握
- 连续学习：current streak、max streak、月历热力图
- 错题/困难词：`again/dontknow` 高的词优先展示

不要在第一版做：

- 社交排行
- 云同步
- AI 生成例句
- 多端同步
- 复杂打卡任务系统

这些会明显增加数据一致性、账号、安全和运营成本。

## 5. 复习规划：推荐 FSRS 主线

### 5.1 为什么不直接自研遗忘曲线

“遗忘曲线”是产品语言，工程上需要的是可执行调度器：给定用户反馈和历史记录，输出下一次复习时间。只写 `next = now + f(score)` 会很快遇到两个问题：

- 不同用户、不同词、不同熟悉度的遗忘速度不同。
- 没有完整 ReviewLog，后续算法无法校准，也无法解释统计页。

### 5.2 推荐模型

学习反馈统一成四档：

- `again`：完全不会
- `hard`：想起来但吃力
- `good`：正常想起
- `easy`：很熟

核心状态：

- `difficulty`
- `stability`
- `retrievability`
- `scheduledDays`
- `dueAt`
- `lastReviewAt`

数据流：

1. 用户复习一张词卡。
2. 写入 `ReviewLog`。
3. 调用 FSRS 或 fallback scheduler 得到下一次 `dueAt`。
4. 更新 `ReviewCard`。
5. 聚合写入 `DailyStats`。
6. WorkManager/通知读取下一批 due cards。

### 5.3 MVP fallback

如果第一版不想处理 Rust/JNI 或 FSRS 依赖适配，可以先复用扩展已有思想：

- `know`：提高掌握分、扩大间隔、提高 ease
- `fuzzy`：小幅提高掌握分、缩短间隔、降低 ease
- `dontknow`：降低掌握分、回到 today
- `mastered`：达到阈值后进入长间隔

但 fallback 只能作为过渡。ReviewLog 表结构要按 FSRS 预留，否则后面迁移成本会变高。

## 6. UI/UX 方向：高级、简洁、Apple 感

“苹果风格”不等于照搬 iOS 控件，也不等于玻璃拟态和大渐变。建议落成以下规则：

- 首页就是学习台，不做营销页。
- 背景用接近系统灰：`#F5F5F7` / 暗色 `#0B0B0F`。
- 主色只用一到两个：蓝色用于行动，绿色用于完成，红色只用于风险/错误。
- 字体：Android 原生用 Roboto / system；Web/Capacitor 用 system font stack。不要随意分发 SF Pro。
- 卡片半径 8-12px，阴影极轻，更多依靠留白、层级和字重。
- 统计图用细线、轻网格、明确图例，不做花哨 3D。
- 学习卡片只突出一个单词、一个释义区、四个反馈按钮。
- 重要数字大，但不做夸张 hero。

核心页面：

1. `Today`：今日复习、进度、开始按钮、下一批词。
2. `Review`：单词卡、发音、释义、例句、四档反馈。
3. `Wordbook`：词书筛选、搜索、CEFR/考试等级过滤。
4. `Stats`：复习负载、保持率、词书进度、连续学习。
5. `Settings`：每日新词上限、目标保持率、提醒时间、数据导入导出。

## 7. 技术路线对比

### 方案 A：Kotlin + Jetpack Compose（推荐长期产品）

组成：

- UI：Jetpack Compose
- 数据：Room
- 设置：DataStore
- 后台提醒：WorkManager
- 图表：Vico 或 MPAndroidChart
- 调度：FSRS Rust/JNI 或 Kotlin/JVM 实现；fallback 为 SM-2

优点：

- Android 体验、通知、离线数据库、性能最好。
- 长期维护稳定。
- 更适合真正发布的安卓 App。

缺点：

- 初始开发慢。
- FSRS 依赖适配比 Web 路线复杂。

### 方案 B：Vue 3 + Capacitor（推荐快速 MVP）

组成：

- UI：Vue 3 + TypeScript + UnoCSS
- App 壳：Capacitor
- 数据：SQLite 插件
- 调度：ts-fsrs
- 图表：ECharts / uPlot / Chart.js

优点：

- 符合现有前端技术栈。
- Apple 风格 UI 更容易快速打磨。
- FSRS 的 TypeScript 集成更直接。

缺点：

- Android 原生通知、后台任务、性能和系统一致性弱于原生。
- 后续如果要做复杂提醒和桌面小组件，成本会上升。

### 方案 C：Fork AnkiDroid

优点：

- SRS、同步、导入导出已有大量成熟实现。

缺点：

- 产品太重，不适合“简洁高级背单词”。
- 学习成本高，改造成词书型 App 成本不低于重写核心界面。
- 许可证和代码边界需要非常谨慎。

结论：不要选 C。若目标是正式 Android 产品，选 A；若目标是最快做出可用 MVP，选 B。

## 8. 建议的第一阶段范围

第一阶段只做“能每天背词，并能正确复习”：

- 导入 publish-safe 词库
- 词书选择：CET4/CET6/考研/IELTS/TOEFL
- 每日新词上限
- 今日复习队列
- 单词卡四档反馈
- 本地 ReviewLog
- 今日统计与词书进度
- 未来 14/30 天复习负载
- 数据导出 JSON

成功标准：

- 词库导入数量与本地构建结果一致。
- 每次反馈都能稳定生成下一次复习时间。
- 关闭 App 后再打开，复习队列不丢失。
- 统计页的数字可由 ReviewLog 复算验证。
- 首页/复习页/统计页在 360px 宽度下无文字重叠。

## 9. 验证计划

数据层：

- 单元测试：导入 CET4/CET6/KAOYAN/IELTS/TOEFL 数量。
- 单元测试：同一个词属于多个词书时不重复创建词条。
- 单元测试：许可证来源字段保留。

调度层：

- 固定时间、固定反馈，断言 `dueAt` 和间隔单调性。
- `again` 后必须进入短间隔。
- `easy/good` 多次后间隔应增长。
- ReviewLog 可重放生成当前 ReviewCard。

统计层：

- 用构造日志验证今日复习数、完成数、正确率、未来 due count。
- 月历热力图按本地日期聚合，不受 UTC 偏移影响。

UI 层：

- 360x800、390x844、412x915、768x1024 截图检查。
- 深色/浅色模式。
- 长单词、长中文释义、无音标数据的兜底。

## 10. 来源

本地来源：

- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\docs\词库来源与许可证.md`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\data\sources.json`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\learningState.js`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\react-ui\src\learning-dashboard.ts`

联网来源：

- AnkiDroid：https://github.com/ankidroid/Anki-Android
- Anki FSRS FAQ：https://faqs.ankiweb.net/what-spaced-repetition-algorithm.html
- FSRS Rust：https://github.com/open-spaced-repetition/fsrs-rs
- ts-fsrs：https://github.com/open-spaced-repetition/ts-fsrs
- SuperMemo SM-2：https://www.supermemo.com/en/blog/the-true-history-of-spaced-repetition
- ECDICT：https://github.com/skywind3000/ECDICT
- Words-CEFR-Dataset：https://github.com/Maximax67/Words-CEFR-Dataset
- CC-CEDICT：https://cc-cedict.org/wiki/
- 墨墨背单词 App Store：https://apps.apple.com/cn/app/id888483369
- Android Jetpack Compose：https://developer.android.com/jetpack/compose
- Android Room：https://developer.android.com/training/data-storage/room
- Android DataStore：https://developer.android.com/topic/libraries/architecture/datastore
- Android WorkManager：https://developer.android.com/topic/libraries/architecture/workmanager
- Apple Human Interface Guidelines：https://developer.apple.com/design/human-interface-guidelines/
- Vico Charts：https://github.com/patrykandpatrick/vico
- MPAndroidChart：https://github.com/PhilJay/MPAndroidChart
