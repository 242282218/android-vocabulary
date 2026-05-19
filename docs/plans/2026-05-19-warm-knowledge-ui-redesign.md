# 安卓背单词全量 UI 重做设计

日期：2026-05-19

## 目标

全量重做 UI 层，让应用从基础 Material 3 界面升级为“温暖知识感”的学习产品。改动聚焦视觉系统、页面信息层级和高频操作反馈，不改数据库、FSRS 算法、Repository、UseCase、导入导出协议和后台任务。

## 设计方向

采用温暖知识感：暖纸色背景、米白纸张卡片、赤陶主色、低阴影、清晰边框、统一圆角。内容本身做主角，避免蓝紫渐变、发光、装饰 blob 和 emoji 图标。

核心 token：

- Background: #F5F1E8
- Surface: #FBF8F1
- SurfaceElevated: #FFFFFF
- TextPrimary: #171412
- TextMuted: #6B625A
- Border: #E6DED2
- Accent: #C96442
- AccentSoft: #EAD2C4

## 页面设计

### Today

首页突出“今天该做什么”。顶部显示问候与学习状态；主卡片展示待复习、新词、完成率；保留一个主操作“开始/继续复习”；连续天数、今日上限、困难词等降为次级卡片。空状态必须给出明确下一步。

### Review

复习页减少干扰。顶部为轻量进度条；中央是大词卡；释义、例句、反馈分层展示；底部固定四档反馈按钮，高度接近 44dp。答题后反馈必须同时使用文字和颜色，不只依赖颜色。

### Wordbook

词书页从列表改为学习资源选择。顶部放搜索/筛选；词书卡片展示名称、词量、进度和当前状态；当前词书突出显示；词条详情用层级卡片降低文本密度。

### Stats

统计页先给结论，再给明细。顶部显示 3-4 个核心 KPI；趋势、热力图、复习负载和困难词下沉为卡片。图表颜色表达数据，不做装饰。

### Settings

设置页按学习偏好、通知提醒、外观主题、数据管理、危险操作分组。危险操作视觉隔离，高风险操作保留确认。

## 组件规则

- 每页只保留一个 primary action。
- 卡片、按钮、输入框统一 radius、padding、border。
- 高频按钮触控高度接近 44dp。
- 加载文案具体化，例如“正在生成今日复习计划”。
- 错误状态给可执行动作，例如“重试”。
- 空状态给下一步，不显示无意义空白。

## 实现范围

会修改：

- core/designsystem
- app 导航 shell
- feature/today
- feature/review
- feature/wordbook
- feature/stats
- feature/settings

不会修改：

- Room schema
- FSRS 算法
- Repository / UseCase 行为
- 导入导出协议
- WorkManager 后台任务

## 验证

完成后执行最小充分验证：

1. .\\gradlew.bat assembleDebug
2. .\\gradlew.bat testDebugUnitTest
3. 如时间允许，执行 .\\scripts\\test\\verify-local.ps1
