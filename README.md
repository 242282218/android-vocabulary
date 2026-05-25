# Android Vocabulary

一个本地离线的安卓英语背单词 App。当前路线是 Kotlin + Jetpack Compose + Room + DataStore + WorkManager + FSRS，目标是先把每日学习、复习、统计、导出做成稳定闭环。

## 当前范围

- CET4、CET6、考研、IELTS、TOEFL 词书。
- publish-safe 词库 assets，发布构建不包含阻断来源派生数据。
- 今日学习队列、每日新词上限、四档反馈：again / hard / good / easy。
- ReviewLog 作为复习和统计事实来源。
- 今日统计、词书进度、未来复习负载、困难词。
- DataStore 设置、每日提醒、JSON 导出。

## 本地验证

Windows PowerShell 推荐直接运行：

```powershell
.\scripts\test\verify-local.ps1
```

脚本会自动查找 JDK 17，先校验 release/smoke 脚本回归和 publish-safe 词库，再运行：

```powershell
.\gradlew.bat ktlintCheck detekt testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

本地脚本只编译 androidTest APK，不启动 managed device；`pixel2Api30DebugAndroidTest` 由 GitHub Actions 运行，或在已配置模拟器环境时手动执行。

发布、smoke 和设备验收脚本自身改动后，也可以单独运行轻量回归检查：

```powershell
.\scripts\test\verify-release-scripts.ps1
```

该脚本不需要真机，会校验 PowerShell 解析、失败阶段映射和 metadata 失败归档降级路径。

如果需要手动指定 JDK：

```powershell
$env:JAVA_HOME='D:\AndroidVocabularyTools\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat ktlintCheck detekt testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

## 词库构建

发布安全词库来自参考扩展的构建脚本：

```powershell
.\scripts\vocab\build-publish-safe.ps1
.\scripts\test\verify-vocab-assets.ps1
```

期望数量：

| Book | Count |
|---|---:|
| CET4 | 3846 |
| CET6 | 5406 |
| KAOYAN | 4801 |
| IELTS | 5038 |
| TOEFL | 6970 |

`app/src/main/assets/vocab/sources.json` 必须随包保留，用于来源与许可证说明。

## CI

GitHub Actions 执行：

- `scripts/test/verify-release-scripts.ps1`
- `scripts/test/verify-vocab-assets.ps1`
- `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug assembleDebugAndroidTest pixel2Api30DebugAndroidTest`

有设备或模拟器时再运行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

需要把 instrumentation test、AAB 安装 smoke 和日志归档串起来时，用设备验证脚本：

```powershell
.\scripts\test\verify-device.ps1 -ListDevices
.\scripts\test\verify-device.ps1 -DeviceSerial <serial>
```

只验证 instrumentation tests 时加 `-SkipBundleSmoke`；已有 AAB 只验证安装链路时加 `-SkipConnectedTests -SkipBundleBuild -BundlePath .\dist\AndroidVocabulary-release-v<versionName>-<versionCode>.aab`。

## Release 构建

本地 release 验证与产物归档：

```powershell
.\scripts\release\build-release.ps1
```

脚本默认要求 release APK 已签名并能通过 `apksigner verify`。验签通过且未使用 `-AllowUnsigned` 时，APK 会以 `AndroidVocabulary-release-v<versionName>-<versionCode>.apk` 复制到 `dist/`。没有 keystore 时可用 `-AllowUnsigned` 只验证编译链路，输出名为 `AndroidVocabulary-build-validation-v<versionName>-<versionCode>.apk`，不得对外分发。正式对外分发前必须配置 release keystore，并按 [发布构建说明](docs/release.md) 做真机冒烟验证。
完整发布顺序见 [发布前 Checklist](docs/release.md#发布前-checklist)，其中 `-AllowUnsigned` 和 `-AllowDebugSigning` 只代表本地验证，不代表可发布。
需要只读审计 `dist/` 中当前版本 release 命名 APK/AAB 是否真实验签时运行 `.\scripts\test\verify-release-artifacts.ps1`；失败会写入 `build/release-artifacts/release-artifacts-run.txt`，不会删除历史产物。该 metadata 会记录 release APK/AAB 的 SHA-256。
正式发布前的最终证据链门禁是 `.\scripts\test\verify-release-readiness.ps1 -GitHubActionsEvidencePath <ci-evidence-file> -ReleaseArtifactsEvidencePath <release-artifacts-run.txt> -ApkSmokeEvidencePath <smoke-release-apk-run.txt> -DeviceVerificationEvidencePath <verification-completed.txt>`，会汇总 dist 审计、APK smoke、设备 AAB 验收和 CI 证据，并重算当前 `dist/` release 产物 SHA-256 与审计记录比对；APK smoke metadata 中的 `apkSha256` 必须和 release APK 一致，设备 AAB 验收 metadata 中的 `bundleSha256`、父日志摘要里的 `bundleSmokeBundleSha256` 以及归档 bundle smoke 子日志原文里的 `bundleSha256` 都必须和 release AAB 一致。未传 `-ReleaseArtifactsEvidencePath` / `-ApkSmokeEvidencePath` 时会沿用默认 metadata；未传 `-DeviceVerificationEvidencePath` 时会从设备验收完成记录中选择最新且匹配当前版本和 release AAB SHA-256 的记录。正式发布建议显式传入本次保留的三个本地 evidence 文件；readiness 会把本次读取到的 evidence 复制到 `build/release-readiness/evidence-*.txt` 并记录归档文件 SHA-256，包括设备验收父日志及其指向的 bundle smoke 子日志，同时在 `evidenceArchives` 中汇总归档清单。
GitHub Actions 成功后会上传 `github-actions-release-evidence` artifact；其中 `github-actions-evidence.txt` 使用每行 `key=value`，示例：

```text
workflow=Android
conclusion=success
commitSha=<git rev-parse HEAD>
runId=<github run id>
runAttempt=<github run attempt>
runUrl=https://github.com/<owner>/<repo>/actions/runs/<github run id>
checks=verify-release-scripts, verify-vocab-assets, ktlintCheck, detekt, testDebugUnitTest, assembleDebug, assembleDebugAndroidTest, pixel2Api30DebugAndroidTest
```

需要生成 Google Play 使用的 Android App Bundle：

```powershell
.\scripts\release\build-bundle.ps1
```

AAB 脚本同样默认要求 release 签名配置。验签通过且未使用 `-AllowUnsigned` 时，产物会以 `AndroidVocabulary-release-v<versionName>-<versionCode>.aab` 复制到 `dist/`，并用 `jarsigner -verify` 校验归档产物。
`-AllowUnsigned` 只用于验证 bundle 编译链路，输出名为 `AndroidVocabulary-build-validation-v<versionName>-<versionCode>.aab`；未签名 AAB 不会被脚本标记为 verified，也不能上传应用商店。

安装 bundletool 后，可以进一步模拟应用商店拆包和安装：

```powershell
.\scripts\tooling\install-bundletool.ps1
.\scripts\test\smoke-release-bundle.ps1 -BuildOnly
.\scripts\test\smoke-release-bundle.ps1 -DeviceSerial <serial>
```

未配置 release signing 环境时，AAB smoke 需要显式加 `-AllowDebugSigning`，该路径只做本地链路验证，不代表可发布。
默认 AAB smoke 只查找 `dist/AndroidVocabulary-release-v<versionName>-<versionCode>.aab`；需要验证其他 AAB 时显式传 `-BundlePath`。
AAB smoke 会先用 `jarsigner -verify` 校验 AAB 本身；验签失败且未显式使用 `-AllowDebugSigning` 时会中止，避免误用历史遗留的 release 命名验证产物。
`build/bundle-smoke/smoke-release-bundle-run.txt` 会记录输入 AAB 的 `bundleSha256`，用于后续发布证据比对。
本地验证生成的 APK Set 会使用 `AndroidVocabulary-build-validation-v<versionName>-<versionCode>-debug-signing.apks` 这类命名，避免和正式 release 记录混淆。
如果 `build/bundle-smoke/` 中仍有旧的 release 命名或 `app-release.apks` 中间产物，脚本只会提示并写入 metadata，不会自动删除历史验证证据。

连接真机或模拟器后，可用脚本验证签名 APK 能安装、启动并渲染首屏：

```powershell
.\scripts\test\smoke-release-apk.ps1
```

多设备场景先运行 `.\scripts\test\smoke-release-apk.ps1 -ListDevices`，再用 `-DeviceSerial <serial>` 指定设备。
完整设备验收优先使用 `.\scripts\test\verify-device.ps1 -DeviceSerial <serial>`，日志会写入 `build/device-verification/v<versionName>-<versionCode>-<timestamp>/`，其中 `verification-run.txt` 记录启动态元数据；成功完成后还会写入 `verification-completed.txt`，记录完成态 AAB 路径状态、`bundleSha256`、归档 bundle smoke 子日志摘要、设备诊断日志、APK Set 签名类型和关键开关。

## 发布阻断项

- 词库不是 publish-safe。
- 缺少 `sources.json` 或来源许可证展示。
- 复习反馈只更新 ReviewCard，没有写 ReviewLog。
- 统计不能从 ReviewLog / ReviewCard 复算。
- 今日队列或设置重启后丢失。
- `ktlintCheck`、`detekt`、`testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest` 任一失败。
- GitHub Actions 中 `pixel2Api30DebugAndroidTest` 失败。
- 已签名 release APK 未通过真机/模拟器 smoke。
- 上传应用商店前，release AAB 未通过签名验证或 AAB 安装级验收。
