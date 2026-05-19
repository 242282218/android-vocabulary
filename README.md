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

脚本会自动查找 JDK 17，先校验 publish-safe 词库，再运行：

```powershell
.\gradlew.bat ktlintCheck detekt testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

如果需要手动指定 JDK：

```powershell
$env:JAVA_HOME='D:\AndroidVocabularyTools\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat ktlintCheck detekt testDebugUnitTest assembleDebug
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

- `scripts/test/verify-vocab-assets.ps1`
- `./gradlew ktlintCheck detekt testDebugUnitTest assembleDebug`

有设备或模拟器时再运行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

需要把 instrumentation test、AAB 安装 smoke 和日志归档串起来时，用设备验证脚本：

```powershell
.\scripts\test\verify-device.ps1 -ListDevices
.\scripts\test\verify-device.ps1 -DeviceSerial <serial>
```

只验证 instrumentation tests 时加 `-SkipBundleSmoke`；已有 AAB 只验证安装链路时加 `-SkipConnectedTests -SkipBundleBuild -BundlePath .\dist\<release-aab-name>.aab`。

## Release 构建

本地 release 验证与产物归档：

```powershell
.\scripts\release\build-release.ps1
```

脚本默认要求 release APK 已签名并能通过 `apksigner verify`。没有 keystore 时可用 `-AllowUnsigned` 只验证编译链路。成功后 APK 会复制到 `dist/`。正式对外分发前必须配置 release keystore，并按 [发布构建说明](docs/release.md) 做真机冒烟验证。

需要生成 Google Play 使用的 Android App Bundle：

```powershell
.\scripts\release\build-bundle.ps1
```

AAB 脚本同样默认要求 release 签名配置，并用 `jarsigner -verify` 校验归档产物。
`-AllowUnsigned` 只用于验证 bundle 编译链路；未签名 AAB 不会被脚本标记为 verified，也不能上传应用商店。

安装 bundletool 后，可以进一步模拟应用商店拆包和安装：

```powershell
.\scripts\tooling\install-bundletool.ps1
.\scripts\test\smoke-release-bundle.ps1 -BuildOnly
.\scripts\test\smoke-release-bundle.ps1 -DeviceSerial <serial>
```

连接真机或模拟器后，可用脚本验证签名 APK 能安装、启动并渲染首屏：

```powershell
.\scripts\test\smoke-release-apk.ps1
```

多设备场景先运行 `.\scripts\test\smoke-release-apk.ps1 -ListDevices`，再用 `-DeviceSerial <serial>` 指定设备。
完整设备验收优先使用 `.\scripts\test\verify-device.ps1 -DeviceSerial <serial>`，日志会写入 `build/device-verification/`。

## 发布阻断项

- 词库不是 publish-safe。
- 缺少 `sources.json` 或来源许可证展示。
- 复习反馈只更新 ReviewCard，没有写 ReviewLog。
- 统计不能从 ReviewLog / ReviewCard 复算。
- 今日队列或设置重启后丢失。
- `ktlintCheck`、`detekt`、`testDebugUnitTest`、`assembleDebug` 任一失败。
- 已签名 release APK 未通过真机/模拟器 smoke。
