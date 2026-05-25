# 项目开发规则

本文件是 `安卓背单词` 的项目级规则。项目已进入稳定、验收和发布准备阶段；默认目标不是继续扩 MVP，而是保持现有闭环可靠、可验证、可发布。

## 当前定位

- 产品：本地离线安卓英语背单词 App。
- 目标：简洁、高级、接近 Apple 风格的学习工具；首页就是学习台。
- 核心闭环：词书选择、publish-safe 词库导入、今日队列、四档反馈、FSRS 调度、本地日志、统计、导出、提醒。
- 当前优先级：稳定性、数据正确性、发布合规、设备验收、回归测试。
- 默认不新增账号、云同步、社交排行、AI 例句、付费订阅等二期能力。用户明确要求时，先说明成本和风险，再给更小可交付方案。

## 开发前先看

常规改动先读：

- `README.md`
- `docs/release.md`
- `settings.gradle.kts`
- 相关模块源码和测试

涉及词库、来源、许可证时再读：

- `docs/research/2026-05-16-english-vocabulary-app-open-source-research.md`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\docs\词库来源与许可证.md`
- `D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension\data\sources.json`

不要凭记忆改。先检查代码、脚本、测试和现有实现，再下结论。

## 技术栈

- Kotlin 2.0 + Jetpack Compose + Material 3
- Room SQLite + DataStore Preferences
- Hilt 依赖注入
- WorkManager 每日提醒
- FSRS：`io.github.open-spaced-repetition:fsrs`
- Navigation Compose + Lifecycle ViewModel
- kotlinx-serialization-json
- Vico 统计图表
- Gradle Version Catalog：`gradle/libs.versions.toml`
- ktlint + Detekt
- JDK 17，minSdk 26，targetSdk 35

不得重新切回 WebView、Vue、Capacitor 路线；不得 fork AnkiDroid、ForgetMeNot 等大型项目。它们只能作为参考资料。

## 模块边界

- `app`：MainActivity、Hilt 入口、导航图、应用级权限。
- `core:model`：纯数据模型，无 Android 依赖。
- `core:common`：ClockProvider、日期、ID、通用错误。
- `core:domain`：UseCase 和业务编排，不含 Room/Compose 依赖。
- `core:scheduler`：FSRS 调度器，实现 `ReviewScheduler`。
- `core:database`：Room Entity、DAO、View、Mapper、Migration、Repository 实现。
- `core:datastore`：设置持久化。
- `core:vocabulary`：assets 词库读取和导入。
- `core:stats`：统计查询与聚合。
- `core:designsystem`：主题、颜色、基础组件。
- `core:testing`：测试基础设施。
- `feature:*`：页面 Composable + ViewModel。
- `worker`：WorkManager 提醒。
- `scripts`：词库、验证、发布、设备验收脚本。

新增或移动代码时必须守住边界。核心逻辑优先放纯 Kotlin；Room、文件、通知、系统时间、Android 权限只放边界层。

## 数据不变量

- `WordEntry` 一词一条；多词书归属通过 `WordBookMembership` 表达。
- `ReviewLog` 是复习事实来源，必须不可变追加。
- `ReviewCard` 是从日志推进后的当前状态。
- `DailyStats` 是聚合缓存，不是真实来源。
- 每次复习顺序：先写 `ReviewLog`，再计算 FSRS，最后更新 `ReviewCard` 和必要缓存。
- 今日统计、连续学习、正确率、未来复习负载必须能从 `ReviewLog` / `ReviewCard` 复算。
- 时间相关逻辑必须通过 `ClockProvider` 或可注入时间入口，不直接散落 `Instant.now()`。
- 本地日期统计要避免 UTC 跨天错误。

数据、迁移、调度、统计相关改动必须补测试；不能补时说明缺口和风险。

## 词库与合规

发布版默认只使用 publish-safe 资产：

```powershell
.\scripts\vocab\build-publish-safe.ps1
.\scripts\test\verify-vocab-assets.ps1
```

期望数量：

- CET4 3846
- CET6 5406
- KAOYAN 4801
- IELTS 5038
- TOEFL 6970

合规底线：

- `app/src/main/assets/vocab/sources.json` 必须随包保留。
- 发布构建不得包含 `publishBlocking=true` 来源派生的补充词条或增强字段。
- `KyleBing`、`NETEM` 未取得明确授权前是发布阻断项。
- 不重新抓词库，不手工维护平行词库。
- 不丢弃来源、许可证、hash、manifest 校验信息。

词库改动后必须运行 `verify-vocab-assets.ps1`，并检查导入测试。

## 复习与统计

- 反馈只允许 `again`、`hard`、`good`、`easy`。
- FSRS 状态保留 `difficulty`、`stability`、`retrievability`、`scheduledDays`、`dueAt`、`lastReviewAt`。
- `retrievability()` 必须使用用户设置的 `targetRetention`，不得硬编码。
- 禁止新增不可解释的临时遗忘曲线公式。
- 困难词、保持率、词书进度、未来负载都必须来自日志、卡片或可复算查询。
- 统计表达可参考墨墨，但不复制闭源实现。

## UI/UX

首页就是学习台，不做营销页。核心页面仍是：

- Today
- Review
- Wordbook
- Stats
- Settings

视觉规则：

- 简洁、高级、轻量，接近 Apple 风格但不照搬 iOS。
- 浅色背景优先 `#F5F5F7`，暗色背景优先 `#0B0B0F`。
- 蓝色用于主行动，绿色用于完成，红色只用于错误或风险。
- 使用系统字体栈，不随意分发 SF Pro。
- 卡片半径 8-12dp，阴影极轻。
- 不做大面积渐变、玻璃拟态、装饰光斑、营销式 hero。
- 不在卡片里套卡片。
- 360x800、390x844、412x915、768x1024 下不能文字重叠或关键按钮不可达。

UI 改动必须优先复用 `core:designsystem`，避免每个页面单独发明样式。

## 代码规则

- 小函数、单一职责；复杂函数优先拆分。
- UseCase 一个文件一个类。
- Mapper 中 `valueOf()` 等枚举解析必须防御非法值。
- DAO SQL 中重复列投影应提取为 Room View 或共享查询，避免改一处漏多处。
- 魔法数字提取为命名常量；只在原因不明显时写英文注释说明 why。
- 不为单次使用创建抽象，不做顺手重构。
- 不修改无关文件，不清理无关“死代码”。
- 兼容已有迁移；Room schema 变化必须更新 schema JSON 和迁移测试。

## 默认工作流

Bugfix 和小调整：

1. 复现或定位证据。
2. 修根因，不用 workaround 掩盖结构问题。
3. 最小范围修改。
4. 跑最小充分验证。
5. 回报改动、验证结果、剩余风险。

核心行为、数据结构、迁移、发布链路改动：

1. 先给至少两个方案和 trade-off。
2. 推荐更小、更稳、与现有架构一致的方案。
3. 落地代码和测试。
4. 跑本地验证；需要设备时跑设备脚本。

需求超出当前产品阶段时，直接指出成本、风险和更小方案。

## 本地验证

首选完整本地验证：

```powershell
.\scripts\test\verify-local.ps1
```

该脚本会先校验词库，再运行：

```powershell
.\gradlew.bat --no-daemon --console=plain ktlintCheck detekt testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

只改文档时可不跑 Gradle，但必须至少检查文档内容。只改词库时必须跑：

```powershell
.\scripts\test\verify-vocab-assets.ps1
```

有设备或模拟器时，设备验收：

```powershell
.\scripts\test\verify-device.ps1 -ListDevices
.\scripts\test\verify-device.ps1 -DeviceSerial <serial>
```

## 发布流程

release APK：

```powershell
.\scripts\release\build-release.ps1
```

AAB：

```powershell
.\scripts\release\build-bundle.ps1
```

没有正式 keystore 时，`-AllowUnsigned` 只能验证编译链路，不得视为可发布产物。正式分发前必须配置：

- `ANDROID_VOCAB_RELEASE_STORE_FILE`
- `ANDROID_VOCAB_RELEASE_STORE_PASSWORD`
- `ANDROID_VOCAB_RELEASE_KEY_ALIAS`
- `ANDROID_VOCAB_RELEASE_KEY_PASSWORD`

签名 APK smoke：

```powershell
.\scripts\test\smoke-release-apk.ps1
```

AAB 安装级 smoke：

```powershell
.\scripts\tooling\install-bundletool.ps1
.\scripts\test\smoke-release-bundle.ps1 -DeviceSerial <serial>
```

## 发布阻断项

- `verify-vocab-assets.ps1` 失败。
- `ktlintCheck`、`detekt`、`testDebugUnitTest`、`assembleRelease` 任一失败。
- 缺少 `sources.json` 或 manifest/hash 校验不一致。
- release 产物包含 publish-blocking 来源派生数据。
- 复习反馈只更新 `ReviewCard`，没有写 `ReviewLog`。
- 统计不能从 `ReviewLog` / `ReviewCard` 复算。
- 数据库 schema 变化缺少迁移或迁移测试。
- 今日队列、设置、复习状态重启后丢失。
- 未配置正式签名密钥却对外分发。
- 已签名 APK 未通过安装、启动、首屏 smoke。
- 上传应用商店前，AAB 未通过签名验证和安装级 smoke。

## 输出要求

- 用中文沟通，简洁直接。
- 先查证再结论，结论必须落到文件、代码、脚本或验证结果。
- 修改后说明改了什么、跑了什么验证、哪些没跑以及原因。
- 遇到破坏性操作、需求硬冲突、关键上下文缺失且误判成本高时再询问。
