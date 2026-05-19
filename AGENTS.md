# 项目开发规则

本文件是 `安卓背单词` 的项目级规则。开发前先读：

- `docs/research/2026-05-16-english-vocabulary-app-open-source-research.md`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\docs\词库来源与许可证.md`

目标：做一个简洁、高级、Apple 风格的安卓英语单词 App；复用参考扩展词库；提供墨墨记单词式统计；用间隔重复规划复习；少造轮子。

## 技术路线

第一阶段默认走快速 MVP：

- Vue 3 + Composition API + TypeScript strict
- Capacitor + SQLite
- UnoCSS + CSS variables
- VueUse 优先，状态管理保持轻量
- 复习调度优先 `ts-fsrs`
- pnpm

不要 fork AnkiDroid、ForgetMeNot 等大型背词项目。它们只能作为调度、离线、统计、导入导出和测试策略参考。

若后续明确要长期原生安卓产品，再单独设计 Kotlin + Jetpack Compose + Room + DataStore + WorkManager 版本；不要在同一阶段混用两套 UI 技术栈。

## 第一阶段范围

必须做：

- CET4、CET6、考研、IELTS、TOEFL 词书选择
- publish-safe 词库导入
- 每日新词上限
- 今日复习队列
- 单词卡四档反馈：`again`、`hard`、`good`、`easy`
- 本地 `ReviewLog`
- 今日统计、词书进度、未来 14/30 天复习负载
- 数据导出 JSON

暂不做：

- 账号、云同步、多端同步
- 社交排行、复杂打卡任务
- AI 生成例句
- 付费/订阅

发现需求超出范围时，先说明成本和风险，再给更小方案。

## 词库规则

词库必须复用：

- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\scripts\build-vocab-dataset.js`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\data\*.json`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\data\sources.json`

发布版默认使用 publish-safe 数据：

```bash
node bilibili-vocab-extension/scripts/build-vocab-dataset.js --publish-safe --output-dir <output>
```

导入后校验 publish-safe 数量：

- CET4 3846
- CET6 5406
- KAOYAN 4801
- IELTS 5038
- TOEFL 6970

合规底线：

- `KyleBing`、`NETEM` 未取得明确授权前是发布阻断项。
- 发布构建不得包含 `publishBlocking=true` 来源派生的补充词条或增强字段。
- 不得丢弃 `sources.json` 和来源许可证信息。
- 不重新抓词库，不手工维护平行词库。

## 数据与复习

核心实体保持简单：

- `WordEntry`：词条主体，一词一条。
- `WordBookMembership`：词条属于哪些词书。
- `ReviewCard`：当前学习状态。
- `ReviewLog`：每次复习的不可变记录。
- `DailyStats`：每日聚合缓存，不是真实来源。

事实来源优先级：

1. 词库 JSON 与 `sources.json`
2. `ReviewLog`
3. 由日志重放得到的 `ReviewCard`
4. 由日志聚合得到的 `DailyStats`

不要只维护计数器而不写日志。统计和算法迁移都必须依赖 `ReviewLog`。

复习调度优先 FSRS：

- 反馈统一为 `again`、`hard`、`good`、`easy`
- 状态保留 `difficulty`、`stability`、`retrievability`、`scheduledDays`、`dueAt`、`lastReviewAt`
- 每次复习：先写 `ReviewLog`，再计算 `dueAt`，最后更新 `ReviewCard`

如果 MVP 暂时不能接入 FSRS，可以用简化 SM-2 或参考扩展 `learningState.js`，但表结构必须为 FSRS 迁移预留。禁止写不可解释的临时遗忘曲线公式。

## 统计功能

参考墨墨的是统计表达，不复制闭源实现。

第一版统计只做：

- 今日概览：新学、复习、完成、正确率、预计耗时
- 记忆保持：FSRS retrievability 或 fallback retention
- 未来 14/30 天复习负载
- 词书进度：总词数、已学、掌握、未学
- 掌握分布
- 连续学习与月历热力图
- 困难词：`again` / `hard` 高频词

统计按本地日期聚合，避免 UTC 跨天错误。今日完成、正确率、连续学习必须能从 `ReviewLog` 复算。

## UI/UX

首页就是学习台，不做营销页。

核心页面：

- `Today`
- `Review`
- `Wordbook`
- `Stats`
- `Settings`

视觉规则：

- 简洁、高级、接近 Apple 风格，但不照搬 iOS。
- 浅色背景优先 `#F5F5F7`，暗色背景优先 `#0B0B0F`。
- 蓝色用于主行动，绿色用于完成，红色只用于错误/风险。
- 使用系统字体栈，不随意分发 SF Pro。
- 卡片半径 8-12px，阴影极轻。
- 不做大面积渐变、玻璃拟态、装饰光斑、营销式 hero。
- 不在卡片里套卡片。
- 360px 宽度下不能文字重叠。

## 架构约定

推荐边界：

- `domain`：词条模型、复习调度、统计计算等纯逻辑。
- `data`：SQLite、词库导入、导出、迁移。
- `features`：Today、Review、Wordbook、Stats、Settings。
- `ui`：基础组件、主题、图标、布局。
- `scripts`：词库构建、导入校验、测试辅助。

核心逻辑优先纯函数；SQLite、文件、通知、时间读取放在边界层。时间相关逻辑要能注入 `now`。

## 验证要求

项目脚手架建立后必须提供：

- `pnpm run lint`
- `pnpm run typecheck`
- `pnpm run test`
- `pnpm run build`

关键测试：

- 词库导入数量与 publish-safe 期望一致。
- 多词书同词不重复创建 `WordEntry`。
- `ReviewLog` 可重放生成当前 `ReviewCard`。
- `again` 进入短间隔，`good/easy` 多次后间隔增长。
- 今日统计和未来复习负载可由日志/卡片复算。
- 360x800、390x844、412x915、768x1024 无明显布局问题。

## 工作流

- 先检查代码、研究文档和参考扩展，再下结论。
- 小 bug 直接定位、修复、验证。
- 新增核心行为时给至少两个方案和 trade-off，再按推荐方案落地。
- 词库、调度、统计、数据迁移改动必须补测试；不能补时说明缺口和风险。
- 不引入未经核验的发布阻断数据。
- 不把第一阶段扩成账号、云同步或社交系统。
