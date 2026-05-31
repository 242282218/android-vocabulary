# 发布构建说明

## 本地 release 构建

Windows PowerShell：

```powershell
.\scripts\release\build-release.ps1
```

脚本会自动查找 JDK 17，然后执行：

```powershell
.\scripts\test\verify-vocab-assets.ps1
.\gradlew.bat --no-daemon --console=plain ktlintCheck detekt testDebugUnitTest assembleRelease
```

验签通过且未使用 `-AllowUnsigned` 时，会把 release APK 复制到：

```text
dist/AndroidVocabulary-release-v<versionName>-<versionCode>.apk
```

默认情况下，脚本要求 release APK 通过 `apksigner verify`。如果只是想验证 release 编译链路、暂时没有 keystore，可以显式生成不可安装的未签名产物：

```powershell
.\scripts\release\build-release.ps1 -AllowUnsigned
```

该路径只会写入构建验证产物，避免覆盖正式归档名：

```text
dist/AndroidVocabulary-build-validation-v<versionName>-<versionCode>.apk
```

`-AllowUnsigned` 不会替换 `dist/AndroidVocabulary-release-v<versionName>-<versionCode>.apk`。如果 `dist/` 里已有当前版本的旧 release 命名 APK，`verify-release-artifacts.ps1` 仍会审计那个文件，并在未签名或签名不匹配时失败。正式发布前必须用不带 `-AllowUnsigned` 的签名构建替换它；若旧文件只是历史证据，应先有意识地移出或归档到 `dist/` 外，不要把 build-validation 产物当作发布产物。

## Android App Bundle

面向 Google Play 或需要 AAB 交付时，使用独立脚本构建并归档：

```powershell
.\scripts\release\build-bundle.ps1
```

脚本会执行：

```powershell
.\scripts\test\verify-vocab-assets.ps1
.\gradlew.bat --no-daemon --console=plain ktlintCheck detekt testDebugUnitTest bundleRelease
jarsigner -verify -certs .\app\build\outputs\bundle\release\app-release.aab
```

默认情况下，AAB 脚本也要求 release signing 环境变量完整。验签通过且未使用 `-AllowUnsigned` 时，会把 AAB 复制到 `dist/AndroidVocabulary-release-v<versionName>-<versionCode>.aab`。脚本会强制使用英文 `jarsigner` 输出，并且只有输出包含 `jar verified` 且不包含 `jar unsigned` / `no manifest` 时，才打印 `[ok] release AAB signature verified`。
如果本地仍存在旧的 `dist/app-release.aab`，只视为历史产物；默认 AAB smoke 和正式上传都应使用版本化 AAB。

没有 keystore 时可用 `-AllowUnsigned` 只验证 bundle 编译链路，但该产物不可上传应用商店，也不会被视为已验签：

```powershell
.\scripts\release\build-bundle.ps1 -AllowUnsigned
```

该路径只会写入构建验证产物：

```text
dist/AndroidVocabulary-build-validation-v<versionName>-<versionCode>.aab
```

`-AllowUnsigned` 同样不会替换 `dist/AndroidVocabulary-release-v<versionName>-<versionCode>.aab`。如果当前版本 release 命名 AAB 是旧的或未签名，`verify-release-artifacts.ps1` 和最终 readiness 都会继续失败，直到用正式签名的 AAB 覆盖，或人工把旧 release 命名文件移出 `dist/` 后重新执行正式构建。

## AAB 安装级验证

AAB 上传前不应只停留在构建和签名校验。用 bundletool 把 AAB 转成设备 APK Set，并通过现有首屏 smoke 检查启动状态：

```powershell
.\scripts\test\verify-release-scripts.ps1
.\scripts\tooling\install-bundletool.ps1
.\scripts\test\smoke-release-bundle.ps1 -BuildOnly
.\scripts\test\smoke-release-bundle.ps1 -DeviceSerial <serial>
```

脚本会先用 `jarsigner -verify` 校验输入 AAB 本身，再使用 release signing 环境变量给 bundletool 生成的 APK Set 签名。没有正式 keystore 时，可用 `-AllowDebugSigning` 做本地编译链路验证；该参数允许未验签 AAB 继续跑安装链路，但输出只代表 `not release-ready`。
例如：

```powershell
.\scripts\test\smoke-release-bundle.ps1 -BuildOnly -AllowDebugSigning
.\scripts\test\smoke-release-bundle.ps1 -DeviceSerial <serial> -AllowDebugSigning
```

APK Set 会写入 `build\bundle-smoke\`，文件名包含 `release-signing` 或 `debug-signing`。本地验证路径默认使用 `AndroidVocabulary-build-validation-v<versionName>-<versionCode>-debug-signing.apks` 这类命名；只有 AAB 已验签且未使用本地覆盖参数时，才保留 release 命名前缀。APK Set 只是 bundletool 的安装验证中间产物，不是应用商店上传产物；正式上传只使用已通过 AAB 验签的 `dist\AndroidVocabulary-release-v<versionName>-<versionCode>.aab`。如果显式传入 `-ApksPath`，文件名也应包含签名类型，或保留脚本输出作为验证记录。历史 `AndroidVocabulary-release-v*.apks` 或 `app-release.apks` 中间产物只会被脚本提示并写入 metadata，不会自动删除。`build\bundle-smoke\smoke-release-bundle-run.txt` 会记录输入 AAB 的 `bundleSha256`。
如果显式传入 `-BundlePath` 且文件名不是当前版本化 AAB，脚本会输出 warning；这是手动覆盖默认发布产物路径，不能误记为正式发布归档验证。

## 签名配置

Android 要求 APK 必须签名后才能安装或更新；上传应用商店的 AAB 也应使用 release signing 配置。正式 release 构建前，先准备 keystore，并设置以下环境变量：

```powershell
$env:ANDROID_VOCAB_RELEASE_STORE_FILE='D:\secure\android-vocab-release.jks'
$env:ANDROID_VOCAB_RELEASE_STORE_PASSWORD='<store-password>'
$env:ANDROID_VOCAB_RELEASE_KEY_ALIAS='<key-alias>'
$env:ANDROID_VOCAB_RELEASE_KEY_PASSWORD='<key-password>'
```

可用 `keytool` 生成 keystore：

```powershell
keytool -genkeypair `
  -v `
  -keystore D:\secure\android-vocab-release.jks `
  -alias android-vocab `
  -keyalg RSA `
  -keysize 4096 `
  -validity 10000
```

不要把 keystore 或密码提交到仓库。

## Release Readiness 证据速查

最终门禁是：

```powershell
.\scripts\test\verify-release-readiness.ps1
```

这个脚本不会自己生成发布证据，它只会读取并归档 4 类上游 evidence。正式 release 前，建议按下面顺序准备：

1. `release artifacts`
   - 生成方式：

```powershell
.\scripts\release\build-release.ps1
.\scripts\release\build-bundle.ps1
.\scripts\test\verify-release-artifacts.ps1
```

   - 关键文件：
     - `dist/AndroidVocabulary-release-v<versionName>-<versionCode>.apk`
     - `dist/AndroidVocabulary-release-v<versionName>-<versionCode>.aab`
     - `build/release-artifacts/release-artifacts-run.txt`
   - 用途：
     - 证明当前版本 release 命名 APK/AAB 已签名
     - 记录 APK/AAB 的 SHA-256
     - 识别 `staleCurrentVersionArtifacts`

2. `APK smoke`
   - 生成方式：

```powershell
.\scripts\test\smoke-release-apk.ps1 -DeviceSerial <serial>
```

   - 关键文件：
     - `build/apk-smoke/smoke-release-apk-run.txt`
   - 用途：
     - 证明当前 release APK 可安装、启动、首屏可见
     - 记录 `apkSha256`，供 readiness 比对当前 release APK

3. `device verification`
   - 生成方式：

```powershell
.\scripts\test\verify-device.ps1 -DeviceSerial <serial> -SkipBundleBuild -BundlePath .\dist\AndroidVocabulary-release-v<versionName>-<versionCode>.aab
```

   - 关键文件：
     - `build/device-verification/v<versionName>-<versionCode>-<timestamp>/verification-completed.txt`
     - 同目录下的 `smoke-release-bundle-run.txt`
   - 用途：
     - 证明当前 release AAB 通过 instrumentation、bundle 安装和首屏 smoke
     - 记录 `bundleSha256`、`bundleSmokeBundleSha256` 和 APK Set 签名类型

4. `GitHub Actions evidence`
   - 生成方式：
     - 从当前 `HEAD` 对应的成功 Android workflow 下载 artifact：`github-actions-release-evidence`
   - 关键文件：
     - `build/release-readiness/github-actions-evidence.txt`
   - 来源：
     - `.github/workflows/android.yml` 中的 `Write release readiness evidence`
     - `scripts/test/write-github-actions-evidence.ps1`
   - 用途：
     - 证明当前提交已通过 CI 质量门

四类 evidence 就绪后，再运行：

```powershell
.\scripts\test\verify-release-readiness.ps1 `
  -GitHubActionsEvidencePath .\build\release-readiness\github-actions-evidence.txt `
  -ReleaseArtifactsEvidencePath .\build\release-artifacts\release-artifacts-run.txt `
  -ApkSmokeEvidencePath .\build\apk-smoke\smoke-release-apk-run.txt `
  -DeviceVerificationEvidencePath .\build\device-verification\v<versionName>-<versionCode>-<timestamp>\verification-completed.txt
```

输出文件：

- `build/release-readiness/release-readiness-run.txt`
- `build/release-readiness/evidence-*.txt`

失败时优先看这两个字段：

- `blockerSummary`
  - 用一句话概括当前阻断点
- `blockerActionSummary`
  - 用一句话列出建议的下一步动作顺序

如果 `blockerSummary` 已经包含 `staleCurrentVersionArtifacts`、missing smoke、missing device verification 或 missing GitHub Actions evidence，就先按 `blockerActionSummary` 的顺序处理，不要直接从长串 `failureMessage` 开始排查。

## 发布前 Checklist

正式对外分发前按以下顺序执行，避免把本地编译验证误当成可发布结果：

1. 确认 `gradle.properties` 中 `androidVocab.versionCode` 已递增，`androidVocab.versionName` 与本次 release 一致。
2. 确认 release keystore 已离线备份，且当前 shell 已设置完整签名环境变量。
3. 运行 `.\scripts\test\verify-local.ps1`，确认 release 脚本回归、publish-safe 词库、静态检查、单元测试、debug APK 和 androidTest APK 编译全部通过。
4. 确认 GitHub Actions 对当前提交通过，尤其是 `verify-release-scripts.ps1` 和 `pixel2Api30DebugAndroidTest` 质量门。
5. 运行 `.\scripts\release\build-release.ps1`，不得使用 `-AllowUnsigned`；输出必须包含 `[ok] release APK signature verified`。
6. 连接目标真机或模拟器，运行 `.\scripts\test\smoke-release-apk.ps1 -DeviceSerial <serial>`，确认已签名 APK 可安装、启动、首屏渲染且无崩溃日志。
7. 运行 `.\scripts\release\build-bundle.ps1`，不得使用 `-AllowUnsigned`；输出必须包含 `[ok] release AAB signature verified`。
8. 运行 `.\scripts\test\verify-release-artifacts.ps1`，确认 `dist/` 中当前版本 release 命名 APK/AAB 都已验签；输出必须包含 `[ok] release artifacts verified`，并在 `build\release-artifacts\release-artifacts-run.txt` 中记录 APK/AAB SHA-256。如果这一步失败且 metadata 出现 `releaseArtifactsRemediation`，按提示重新执行不带 `-AllowUnsigned` 的签名构建；旧 release 命名产物只应在确认是历史证据后人工移出或归档到 `dist/` 外。
9. 安装 bundletool 后运行 `.\scripts\test\verify-device.ps1 -DeviceSerial <serial> -SkipBundleBuild -BundlePath .\dist\AndroidVocabulary-release-v<versionName>-<versionCode>.aab`，不得使用 `-AllowUnsignedBundle` 或 `-AllowDebugSigning`；确认 `connectedDebugAndroidTest`、AAB 拆包安装和首屏 smoke 均通过。
10. 从 GitHub Actions 成功运行中下载 `github-actions-release-evidence` artifact，并将其中的 `github-actions-evidence.txt` 保存到本地，例如 `build\release-readiness\github-actions-evidence.txt`。文件格式为每行 `key=value`：

```text
workflow=Android
conclusion=success
commitSha=<git rev-parse HEAD>
serverUrl=https://github.com
repository=<owner>/<repo>
runId=<github run id>
runAttempt=<github run attempt>
runUrl=https://github.com/<owner>/<repo>/actions/runs/<github run id>
checks=verify-release-scripts, verify-vocab-assets, ktlintCheck, detekt, testDebugUnitTest, assembleDebug, assembleDebugAndroidTest, pixel2Api30DebugAndroidTest
```

`verify-release-readiness.ps1` 会同时校验 `serverUrl`、`repository`、`runId` 和 `runUrl` 是否一致，并要求 `serverUrl=https://github.com`，避免误用其他仓库或其他 GitHub 实例的 workflow 结果。

`dependencyCheckAggregate` 依赖 NVD API key。CI 配置 `NVD_API_KEY` repository secret 后会执行该可选漏洞扫描；未配置时 GitHub Actions 会跳过它，避免把缺少外部 secret 误判为代码验证失败。本地可设置同名环境变量，或传入 `-PnvdApiKey=<key>`；直接运行 dependency-check 任务但未设置 key 时，Gradle 仍会 fail-fast，避免退化为慢速 NVD 403/404。

11. 运行 `.\scripts\test\verify-release-readiness.ps1 -GitHubActionsEvidencePath .\build\release-readiness\github-actions-evidence.txt -ReleaseArtifactsEvidencePath .\build\release-artifacts\release-artifacts-run.txt -ApkSmokeEvidencePath .\build\apk-smoke\smoke-release-apk-run.txt -DeviceVerificationEvidencePath .\build\device-verification\v<versionName>-<versionCode>-<timestamp>\verification-completed.txt`，确认输出 `[ok] release readiness verified`；该门禁会要求当前 Git worktree 干净，避免本地 release 产物来自未经 CI 验证的源码状态；同时会重算当前 `dist/` release 产物 SHA-256 并与第 8 步审计记录比对，也会要求 APK smoke metadata 中的 `apkSha256` 与 release APK 一致、设备 AAB 验收 metadata 中的 `bundleSha256`、父日志摘要里的 `bundleSmokeBundleSha256` 以及归档 bundle smoke 子日志原文里的 `bundleSha256` 都与 release AAB 一致。未传 `-ReleaseArtifactsEvidencePath` / `-ApkSmokeEvidencePath` 时会沿用默认 metadata；未传 `-DeviceVerificationEvidencePath` 时会从设备验收完成记录中选择最新且匹配当前版本和 release AAB SHA-256 的记录。正式发布建议显式传入本次保留的三个本地 evidence 文件；readiness 会把本次读取到的 evidence 复制到 `build\release-readiness\evidence-*.txt` 并记录归档文件 SHA-256，包括设备验收父日志及其指向的 bundle smoke 子日志，同时在 `evidenceArchives` 中汇总归档清单。失败时 metadata 会按 requirement 写入 `*Remediation` 字段，指出下一步修复动作。
12. 保留 `dist/AndroidVocabulary-release-v<versionName>-<versionCode>.apk`、`dist/AndroidVocabulary-release-v<versionName>-<versionCode>.aab`、`build/release-artifacts/release-artifacts-run.txt`、`build/release-readiness/release-readiness-run.txt`、`build/release-readiness/evidence-*.txt`、GitHub Actions 结果和 `build/device-verification/v<versionName>-<versionCode>-<timestamp>/` 日志作为本次 release 验证记录。

`-AllowUnsigned` 和 `-AllowDebugSigning` 只用于本地构建链路验证。出现 `not release-ready` 或 `not uploadable` 的输出时，该产物不得对外分发或上传应用商店。

## 当前产物状态

签名环境变量齐全时，脚本生成可验签的 release APK，用于测试安装和内部验收。正式分发到应用商店前仍需补齐：

- 签名密钥的离线备份与访问权限。
- `versionCode` 递增流程。
- release notes 与许可证说明。
- 真机安装、启动、词库导入、复习、导出、提醒权限的冒烟验证。

## 安装验证

连接 Android 设备并开启 USB 调试后：

```powershell
adb install -r .\dist\AndroidVocabulary-release-v<versionName>-<versionCode>.apk
```

推荐优先使用可重复的 smoke 脚本，它会自动查找当前版本的 release APK，执行签名校验、安装、启动、首屏文本检查和崩溃日志检查：

```powershell
.\scripts\test\smoke-release-apk.ps1
```

`-SkipInstall` 只用于 AAB 已经通过 bundletool 安装后的复用检查，或手动确认目标设备已安装正确版本时的启动检查。它会跳过 APK 文件签名校验和安装，因此不能单独代表完整 release APK smoke。

常用参数：

```powershell
.\scripts\test\smoke-release-apk.ps1 -ListDevices
.\scripts\test\smoke-release-apk.ps1 -DeviceSerial <serial>
.\scripts\test\smoke-release-apk.ps1 -ApkPath .\dist\AndroidVocabulary-release-v<versionName>-<versionCode>.apk
.\scripts\test\smoke-release-apk.ps1 -GrantNotificationPermission
```

`-ResetAppData` 会执行 `pm clear com.zzz.androidvocab`，只在需要覆盖首次导入路径时使用。该参数会清理目标设备上的 App 本地学习数据。

安装后至少验证：

- 首次打开能完成 publish-safe 词库导入。
- Today 页面有学习队列。
- Review 页面四档反馈能保存。
- Stats 页面能显示今日和未来复习负载。
- Settings 能导出 JSON，且导出文件包含 `dataIntegrity` 诊断摘要。
- Android 13+ 开启提醒时会请求通知权限。

## 测试机设备验收

有真机或模拟器时，优先使用可重复的设备验收脚本。它会记录设备信息，运行 `connectedDebugAndroidTest`，按需构建 AAB，再执行 AAB 安装 smoke，并把日志归档到 `build/device-verification/v<versionName>-<versionCode>-<timestamp>/`：

```powershell
.\scripts\test\verify-device.ps1 -ListDevices
.\scripts\test\verify-device.ps1 -DeviceSerial <serial>
```

日志目录会包含 `verification-run.txt`，记录本次验证的启动态元数据；成功完成后还会包含 `verification-completed.txt`，记录完成态 AAB 路径状态、`bundleSha256`、归档 bundle smoke 子日志摘要、设备诊断日志、APK Set 签名类型和关键开关。正式发布记录应保留该目录。

无正式签名环境但需要验证安装链路时，可显式使用本地调试签名路径：

```powershell
.\scripts\test\verify-device.ps1 -DeviceSerial <serial> -AllowUnsignedBundle -AllowDebugSigning
```

该命令成功时会输出 `not release-ready`，并使用 `AndroidVocabulary-build-validation-v<versionName>-<versionCode>.aab` 作为本地 AAB 输入；它只说明本地安装链路可用，不代表 AAB 可上传。

只跑 instrumentation tests 时使用：

```powershell
.\scripts\test\verify-device.ps1 -DeviceSerial <serial> -SkipBundleSmoke
```

已有 AAB 并且只想验证应用商店拆包安装链路时使用：

```powershell
.\scripts\test\verify-device.ps1 -DeviceSerial <serial> -SkipConnectedTests -SkipBundleBuild -BundlePath .\dist\AndroidVocabulary-release-v<versionName>-<versionCode>.aab
```

`-ResetAppData` 会清理目标设备上的 App 本地学习数据，只在需要覆盖首次导入路径时使用。

## 发布阻断项

- `scripts\test\verify-vocab-assets.ps1` 失败。
- `ktlintCheck`、`detekt`、`testDebugUnitTest`、`assembleRelease` 任一失败。
- GitHub Actions 中 `assembleDebugAndroidTest` 或 `pixel2Api30DebugAndroidTest` 失败。
- `app/src/main/assets/vocab/sources.json` 缺失。
- release 产物包含 publish-blocking 来源派生数据。
- 未配置正式签名密钥却对外分发。
- 已签名 APK 未通过 `scripts\test\smoke-release-apk.ps1`。
- 上传应用商店前，AAB 未通过 `scripts\release\build-bundle.ps1` 的签名验证。
- 上传应用商店前，有设备条件但未通过 `scripts\test\verify-device.ps1` 的 AAB 安装级验收；只做 AAB 安装链路复核时，至少必须通过 `scripts\test\smoke-release-bundle.ps1`。
