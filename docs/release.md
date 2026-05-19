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

成功后会把 release APK 复制到：

```text
dist/
```

默认情况下，脚本要求 release APK 通过 `apksigner verify`。如果只是想验证 release 编译链路、暂时没有 keystore，可以显式生成不可安装的未签名产物：

```powershell
.\scripts\release\build-release.ps1 -AllowUnsigned
```

## Android App Bundle

面向 Google Play 或需要 AAB 交付时，使用独立脚本构建并归档：

```powershell
.\scripts\release\build-bundle.ps1
```

脚本会执行：

```powershell
.\scripts\test\verify-vocab-assets.ps1
.\gradlew.bat --no-daemon --console=plain ktlintCheck detekt testDebugUnitTest bundleRelease
jarsigner -verify -certs .\dist\<release-aab-name>.aab
```

默认情况下，AAB 脚本也要求 release signing 环境变量完整。脚本会强制使用英文 `jarsigner` 输出，并且只有输出包含 `jar verified` 且不包含 `jar unsigned` / `no manifest` 时，才打印 `[ok] release AAB signature verified`。

没有 keystore 时可用 `-AllowUnsigned` 只验证 bundle 编译链路，但该产物不可上传应用商店，也不会被视为已验签：

```powershell
.\scripts\release\build-bundle.ps1 -AllowUnsigned
```

## AAB 安装级验证

AAB 上传前不应只停留在构建和签名校验。用 bundletool 把 AAB 转成设备 APK Set，并通过现有首屏 smoke 检查启动状态：

```powershell
.\scripts\tooling\install-bundletool.ps1
.\scripts\test\smoke-release-bundle.ps1 -BuildOnly
.\scripts\test\smoke-release-bundle.ps1 -DeviceSerial <serial>
```

脚本默认使用 release signing 环境变量给 bundletool 生成的 APK Set 签名。没有正式 keystore 时，可用 `-AllowDebugSigning` 做本地编译链路验证，但该结果不能代表发布可用性。

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

## 当前产物状态

签名环境变量齐全时，脚本生成可验签的 release APK，用于测试安装和内部验收。正式分发到应用商店前仍需补齐：

- 签名密钥的离线备份与访问权限。
- `versionCode` 递增流程。
- release notes 与许可证说明。
- 真机安装、启动、词库导入、复习、导出、提醒权限的冒烟验证。

## 安装验证

连接 Android 设备并开启 USB 调试后：

```powershell
adb install -r .\dist\<release-apk-name>.apk
```

推荐优先使用可重复的 smoke 脚本，它会自动查找 `dist/` 或 release 输出目录中的 APK，执行签名校验、安装、启动、首屏文本检查和崩溃日志检查：

```powershell
.\scripts\test\smoke-release-apk.ps1
```

常用参数：

```powershell
.\scripts\test\smoke-release-apk.ps1 -ListDevices
.\scripts\test\smoke-release-apk.ps1 -DeviceSerial <serial>
.\scripts\test\smoke-release-apk.ps1 -ApkPath .\dist\<release-apk-name>.apk
.\scripts\test\smoke-release-apk.ps1 -GrantNotificationPermission
```

`-ResetAppData` 会执行 `pm clear com.zzz.androidvocab`，只在需要覆盖首次导入路径时使用。该参数会清理目标设备上的 App 本地学习数据。

安装后至少验证：

- 首次打开能完成 publish-safe 词库导入。
- Today 页面有学习队列。
- Review 页面四档反馈能保存。
- Stats 页面能显示今日和未来复习负载。
- Settings 能导出 JSON。
- Android 13+ 开启提醒时会请求通知权限。

## 测试机设备验收

有真机或模拟器时，优先使用可重复的设备验收脚本。它会记录设备信息，运行 `connectedDebugAndroidTest`，按需构建 AAB，再执行 AAB 安装 smoke，并把日志归档到 `build/device-verification/`：

```powershell
.\scripts\test\verify-device.ps1 -ListDevices
.\scripts\test\verify-device.ps1 -DeviceSerial <serial>
```

无正式签名环境但需要验证安装链路时，可显式使用本地调试签名路径：

```powershell
.\scripts\test\verify-device.ps1 -DeviceSerial <serial> -AllowUnsignedBundle -AllowDebugSigning
```

只跑 instrumentation tests 时使用：

```powershell
.\scripts\test\verify-device.ps1 -DeviceSerial <serial> -SkipBundleSmoke
```

已有 AAB 并且只想验证应用商店拆包安装链路时使用：

```powershell
.\scripts\test\verify-device.ps1 -DeviceSerial <serial> -SkipConnectedTests -SkipBundleBuild -BundlePath .\dist\<release-aab-name>.aab
```

`-ResetAppData` 会清理目标设备上的 App 本地学习数据，只在需要覆盖首次导入路径时使用。

## 发布阻断项

- `scripts\test\verify-vocab-assets.ps1` 失败。
- `ktlintCheck`、`detekt`、`testDebugUnitTest`、`assembleRelease` 任一失败。
- `app/src/main/assets/vocab/sources.json` 缺失。
- release 产物包含 publish-blocking 来源派生数据。
- 未配置正式签名密钥却对外分发。
- 已签名 APK 未通过 `scripts\test\smoke-release-apk.ps1`。
- 上传应用商店前，AAB 未通过 `scripts\release\build-bundle.ps1` 的签名验证。
- 上传应用商店前，有设备条件但未通过 `scripts\test\smoke-release-bundle.ps1`。
