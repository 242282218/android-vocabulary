param(
    [string]$ApkPath,
    [string]$DeviceSerial,
    [switch]$ResetAppData,
    [switch]$SkipInstall,
    [switch]$GrantNotificationPermission,
    [switch]$ListDevices
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\lib\android-env.ps1')
$repoRoot = Get-AndroidVocabularyRepoRoot
$packageName = 'com.zzz.androidvocab'

function Get-DefaultApk {
    $candidates =
        @(
            (Join-Path $repoRoot 'dist'),
            (Join-Path $repoRoot 'app\build\outputs\apk\release')
        ) |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem -Path $_ -Filter '*.apk' -File } |
        Sort-Object LastWriteTime -Descending

    if ($candidates.Count -eq 0) {
        throw 'No APK found. Pass -ApkPath, or run scripts/release/build-release.ps1 first.'
    }
    return $candidates[0].FullName
}

function Invoke-AndroidDebugBridge {
    param(
        [string[]]$Arguments,
        [switch]$Capture
    )

    $deviceArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($script:ResolvedDeviceSerial)) {
        $deviceArgs += @('-s', $script:ResolvedDeviceSerial)
    }

    if ($Capture) {
        $output = & $script:AdbPath @deviceArgs @Arguments 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw "adb $($Arguments -join ' ') failed: $output"
        }
        return $output
    }

    & $script:AdbPath @deviceArgs @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Get-ConnectedDeviceSerials {
    $output = & $script:AdbPath devices
    if ($LASTEXITCODE -ne 0) {
        throw "adb devices failed: $output"
    }
    return $output |
        Select-Object -Skip 1 |
        Where-Object { $_ -match '^(\S+)\s+device\b' } |
        ForEach-Object { $Matches[1] }
}

function Resolve-DeviceSerial {
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        return $DeviceSerial
    }

    $devices = @(Get-ConnectedDeviceSerials)
    if ($devices.Count -eq 0) {
        throw 'No online Android device found. Connect a device/emulator, enable USB debugging, then rerun the script.'
    }
    if ($devices.Count -gt 1) {
        throw "Multiple devices found: $($devices -join ', '). Rerun with -DeviceSerial."
    }
    return $devices[0]
}

function Test-ApkSignature {
    param([string]$ResolvedApkPath)

    $apkSigner = Get-AndroidTool -Names @('apksigner.bat', 'apksigner') -RelativeDirs @('build-tools')
    & $apkSigner verify --verbose $ResolvedApkPath
    if ($LASTEXITCODE -ne 0) {
        throw "APK signature verification failed: $ResolvedApkPath"
    }
}

function Wait-ForVisibleAppText {
    $expectedTextPattern = 'Today|Review|Wordbook|Stats|Settings|开始学习|词库导入失败'
    for ($attempt = 1; $attempt -le 15; $attempt++) {
        try {
            Invoke-AndroidDebugBridge @('shell', 'uiautomator', 'dump', '/sdcard/android-vocab-window.xml') | Out-Null
            $windowXml = Invoke-AndroidDebugBridge @('exec-out', 'cat', '/sdcard/android-vocab-window.xml') -Capture
            if (($windowXml -join "`n") -match $expectedTextPattern) {
                return
            }
        } catch {
            Write-Host "[info] UI dump attempt $attempt failed: $($_.Exception.Message)"
        }
        Start-Sleep -Seconds 2
    }
    throw 'App launched, but expected first-screen text was not visible within 30 seconds.'
}

function Assert-NoFatalCrash {
    $crashLog = Invoke-AndroidDebugBridge @('logcat', '-d', '-t', '500', 'AndroidRuntime:E', '*:S') -Capture
    $crashText = $crashLog -join "`n"
    if ($crashText -match $packageName -and $crashText -match 'FATAL EXCEPTION') {
        throw "Fatal crash detected after launch:`n$crashText"
    }
}

$script:AdbPath = Get-AndroidTool -Names @('adb.exe', 'adb') -RelativeDirs @('platform-tools')

if ($ListDevices) {
    & $script:AdbPath devices -l
    exit $LASTEXITCODE
}

$script:ResolvedDeviceSerial = Resolve-DeviceSerial
Write-Host "[info] device=$script:ResolvedDeviceSerial"

if (-not $SkipInstall) {
    $resolvedApkPath =
        if ([string]::IsNullOrWhiteSpace($ApkPath)) {
            Get-DefaultApk
        } else {
            (Resolve-Path -LiteralPath $ApkPath).Path
        }

    Write-Host "[info] verifying APK signature: $resolvedApkPath"
    Test-ApkSignature $resolvedApkPath

    Write-Host '[info] installing APK'
    Invoke-AndroidDebugBridge @('install', '-r', $resolvedApkPath)
} else {
    Write-Host '[info] skipping install; using already installed app'
}

if ($ResetAppData) {
    Write-Warning "Resetting app data for $packageName."
    Invoke-AndroidDebugBridge @('shell', 'pm', 'clear', $packageName)
}

if ($GrantNotificationPermission) {
    $sdkVersion = (Invoke-AndroidDebugBridge @('shell', 'getprop', 'ro.build.version.sdk') -Capture | Select-Object -First 1).Trim()
    if ([int]$sdkVersion -ge 33) {
        Write-Host '[info] granting POST_NOTIFICATIONS'
        Invoke-AndroidDebugBridge @('shell', 'pm', 'grant', $packageName, 'android.permission.POST_NOTIFICATIONS')
    }
}

Invoke-AndroidDebugBridge @('logcat', '-c')
Write-Host '[info] launching app'
Invoke-AndroidDebugBridge @('shell', 'monkey', '-p', $packageName, '-c', 'android.intent.category.LAUNCHER', '1')
Start-Sleep -Seconds 3

$pid = (Invoke-AndroidDebugBridge @('shell', 'pidof', $packageName) -Capture | Select-Object -First 1).Trim()
if ([string]::IsNullOrWhiteSpace($pid)) {
    throw "App process is not running after launch: $packageName"
}
Write-Host "[info] app pid=$pid"

Wait-ForVisibleAppText
Assert-NoFatalCrash

Write-Host '[ok] release APK smoke test passed'
