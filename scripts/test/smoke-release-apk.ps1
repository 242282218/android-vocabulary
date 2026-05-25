param(
    [string]$ApkPath,
    [string]$DeviceSerial,
    [switch]$ResetAppData,
    [switch]$SkipInstall,
    [switch]$GrantNotificationPermission,
    [switch]$ListDevices
)

$ErrorActionPreference = 'Stop'

. (Join-Path (Join-Path $PSScriptRoot '..') (Join-Path 'lib' 'android-env.ps1'))
$repoRoot = Get-AndroidVocabularyRepoRoot
$packageName = 'com.zzz.androidvocab'

function Get-DefaultApk {
    $expectedApkName = Get-AndroidVocabularyReleaseApkName
    $searchDirs = @(
        (Join-Path $repoRoot 'dist'),
        (Join-AndroidVocabularyPath $repoRoot @('app', 'build', 'outputs', 'apk', 'release'))
    )
    foreach ($searchDir in $searchDirs) {
        $candidate = Join-Path $searchDir $expectedApkName
        if (Test-Path $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $availableApks =
        $searchDirs |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem -Path $_ -Filter '*.apk' -File } |
        ForEach-Object { $_.FullName }
    $availableText = if ($availableApks.Count -eq 0) { '<none>' } else { $availableApks -join ', ' }
    throw "No APK found for current version: $expectedApkName. Available APKs: $availableText. Pass -ApkPath, or run scripts/release/build-release.ps1 first."
}

function Warn-IfExplicitApkPathIsNotVersioned {
    param([string]$ResolvedApkPath)

    if ([string]::IsNullOrWhiteSpace($ApkPath)) {
        return
    }

    $expectedApkName = Get-AndroidVocabularyReleaseApkName
    $apkFileName = [IO.Path]::GetFileName($ResolvedApkPath)
    if ($apkFileName -eq $expectedApkName) {
        return
    }

    Write-Warning "Explicit ApkPath is not the current versioned release APK '$expectedApkName'. This is an override of the default release artifact: $ResolvedApkPath"
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
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $output = & $script:AdbPath @deviceArgs @Arguments 2>&1
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }

        $outputText = @($output | ConvertTo-AndroidVocabularyOutputText)
        if ($exitCode -ne 0) {
            $outputDisplay = if ($outputText.Count -eq 0) { '<no output>' } else { $outputText -join "`n" }
            throw "adb $($Arguments -join ' ') failed with exit code $($exitCode): $outputDisplay"
        }
        return $outputText
    }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $commandOutput = @()
    try {
        & $script:AdbPath @deviceArgs @Arguments 2>&1 |
            ConvertTo-AndroidVocabularyOutputText |
            Tee-Object -Variable commandOutput
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        $outputText = @($commandOutput | ConvertTo-AndroidVocabularyOutputText) -join "`n"
        throw "adb $($Arguments -join ' ') failed with exit code $exitCode.`nLast output:`n$(Get-AndroidVocabularyTextTail -Text $outputText -MaxLength 2000)"
    }
}

function Invoke-AdbDevicesCommand {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $script:AdbPath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [PSCustomObject]@{
        ExitCode = $exitCode
        Output = @($output | ConvertTo-AndroidVocabularyOutputText)
    }
}

function Get-ConnectedDeviceSerials {
    $result = Invoke-AdbDevicesCommand -Arguments @('devices')
    if ($result.ExitCode -ne 0) {
        $outputText = if ($result.Output.Count -eq 0) { '<no output>' } else { $result.Output -join "`n" }
        throw "adb devices failed with exit code $($result.ExitCode): $outputText"
    }
    return $result.Output |
        Select-Object -Skip 1 |
        Where-Object { $_ -match '^(\S+)\s+device\b' } |
        ForEach-Object { $Matches[1] }
}

function Resolve-DeviceSerial {
    $devices = @(Get-ConnectedDeviceSerials)

    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        if ($devices -notcontains $DeviceSerial) {
            $onlineDevices = if ($devices.Count -eq 0) { '<none>' } else { $devices -join ', ' }
            throw "Device '$DeviceSerial' is not online. Online devices: $onlineDevices."
        }
        return $DeviceSerial
    }

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
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $verifyOutput = & $apkSigner verify --verbose $ResolvedApkPath 2>&1
        $verifyExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($verifyExitCode -ne 0) {
        $verifyText = (@($verifyOutput | ConvertTo-AndroidVocabularyOutputText) -join "`n").Trim()
        throw "APK signature verification failed: $ResolvedApkPath`n$verifyText"
    }
}

function Get-FatalCrashText {
    $crashLog = Invoke-AndroidDebugBridge @('logcat', '-d', '-t', '500', 'AndroidRuntime:E', '*:S') -Capture
    $crashText = $crashLog -join "`n"
    if ($crashText -match $packageName -and $crashText -match 'FATAL EXCEPTION') {
        return $crashText
    }
    return $null
}

function Throw-WithFatalCrashContext {
    param([string]$Message)

    try {
        $crashText = Get-FatalCrashText
    } catch {
        throw "$Message`nUnable to read fatal crash context: $($_.Exception.Message)"
    }
    if ([string]::IsNullOrWhiteSpace($crashText)) {
        throw $Message
    }
    throw "$Message`nFatal crash detected after launch:`n$crashText"
}

function Get-TextExcerpt {
    param(
        [string]$Text,
        [int]$MaxLength
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return '<empty>'
    }
    if ($Text.Length -le $MaxLength) {
        return $Text
    }
    return "$($Text.Substring(0, $MaxLength))`n...[truncated]"
}

function Get-ApkSmokeArtifactDir {
    return Join-AndroidVocabularyPath $repoRoot @('build', 'apk-smoke')
}

function Get-LastWindowDumpPath {
    return Join-Path (Get-ApkSmokeArtifactDir) 'last-window.xml'
}

function Get-ApkSmokeRunMetadataPath {
    return Join-Path (Get-ApkSmokeArtifactDir) 'smoke-release-apk-run.txt'
}

function Clear-LastWindowDump {
    Remove-Item -LiteralPath (Get-LastWindowDumpPath) -Force -ErrorAction SilentlyContinue
}

function Save-LastWindowDump {
    param([string]$WindowXmlText)

    if ([string]::IsNullOrWhiteSpace($WindowXmlText)) {
        return $null
    }

    $dumpPath = Get-LastWindowDumpPath
    New-Item -ItemType Directory -Force -Path (Get-ApkSmokeArtifactDir) | Out-Null
    Set-Content -LiteralPath $dumpPath -Value $WindowXmlText -Encoding UTF8
    return $dumpPath
}

function Get-ApkSmokeMetadataValue {
    param(
        [scriptblock]$Resolve,
        [string]$Fallback = '<unresolved>'
    )

    try {
        $value = & $Resolve
        if ([string]::IsNullOrWhiteSpace($value)) {
            return $Fallback
        }
        return $value
    } catch {
        return $Fallback
    }
}

function Get-ApkSmokeSha256Value {
    if ($SkipInstall) {
        return '<not used>'
    }
    if ([string]::IsNullOrWhiteSpace($script:ResolvedApkPath)) {
        return '<unresolved>'
    }
    if (-not (Test-Path -LiteralPath $script:ResolvedApkPath)) {
        return '<missing>'
    }
    return (Get-FileHash -LiteralPath $script:ResolvedApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Save-ApkSmokeRunMetadata {
    param(
        [string]$RunStatus,
        [string]$StartedAt,
        [string]$CompletedAt,
        [string]$FailureMessage
    )

    $versionName = Get-ApkSmokeMetadataValue { Get-GradlePropertyValue 'androidVocab.versionName' }
    $versionCode = Get-ApkSmokeMetadataValue { Get-GradlePropertyValue 'androidVocab.versionCode' }
    $expectedApkName = Get-ApkSmokeMetadataValue { Get-AndroidVocabularyReleaseApkName }
    $deviceSerialValue =
        if ([string]::IsNullOrWhiteSpace($script:ResolvedDeviceSerial)) {
            '<unresolved>'
        } else {
            $script:ResolvedDeviceSerial
        }
    $apkPathSource =
        if ($SkipInstall) {
            'skipped'
        } elseif ([string]::IsNullOrWhiteSpace($ApkPath)) {
            'defaultVersionedDist'
        } else {
            'explicit'
        }
    $apkPathValue =
        if ($SkipInstall) {
            '<not used>'
        } elseif ([string]::IsNullOrWhiteSpace($script:ResolvedApkPath)) {
            '<unresolved>'
        } else {
            $script:ResolvedApkPath
        }
    $apkPathMatchesCurrentVersionedName =
        if ($SkipInstall) {
            '<not used>'
        } elseif ([string]::IsNullOrWhiteSpace($script:ResolvedApkPath)) {
            '<unknown>'
        } elseif ($expectedApkName -eq '<unresolved>') {
            '<unknown>'
        } else {
            ([IO.Path]::GetFileName($script:ResolvedApkPath) -eq $expectedApkName)
        }
    $lastWindowDump =
        if (Test-Path -LiteralPath (Get-LastWindowDumpPath)) {
            'last-window.xml'
        } else {
            '<none>'
        }

    $metadata = @(
        "runStatus=$RunStatus",
        "startedAt=$StartedAt",
        "completedAt=$CompletedAt",
        "versionName=$versionName",
        "versionCode=$versionCode",
        "deviceSerial=$deviceSerialValue",
        "deviceSerialInput=$(if ([string]::IsNullOrWhiteSpace($DeviceSerial)) { '<auto>' } else { $DeviceSerial })",
        "apkPath=$apkPathValue",
        "apkPathInput=$(if ([string]::IsNullOrWhiteSpace($ApkPath)) { '<default>' } else { $ApkPath })",
        "apkPathSource=$apkPathSource",
        "apkPathExpectedName=$expectedApkName",
        "apkPathMatchesCurrentVersionedName=$apkPathMatchesCurrentVersionedName",
        "apkSha256=$(Get-ApkSmokeSha256Value)",
        "skipInstall=$($SkipInstall.IsPresent)",
        "resetAppData=$($ResetAppData.IsPresent)",
        "grantNotificationPermission=$($GrantNotificationPermission.IsPresent)",
        "lastWindowDump=$lastWindowDump",
        "packageName=$packageName"
    )
    if (-not [string]::IsNullOrWhiteSpace($FailureMessage)) {
        $normalizedFailureMessage = $FailureMessage.Replace("`r`n", ' ').Replace("`n", ' ').Replace("`r", ' ')
        $metadata += "failureMessage=$normalizedFailureMessage"
    }

    New-Item -ItemType Directory -Force -Path (Get-ApkSmokeArtifactDir) | Out-Null
    Set-Content -LiteralPath (Get-ApkSmokeRunMetadataPath) -Value $metadata
}

function Wait-ForVisibleAppText {
    $expectedTextPattern = '今日|复习|词书|统计|设置|正在准备词库|开始学习|词库导入失败|Today|Review|Wordbook|Stats|Settings'
    $lastWindowXmlText = $null
    $lastDumpError = $null
    for ($attempt = 1; $attempt -le 15; $attempt++) {
        try {
            Invoke-AndroidDebugBridge @('shell', 'uiautomator', 'dump', '/sdcard/android-vocab-window.xml') | Out-Null
            $windowXml = Invoke-AndroidDebugBridge @('exec-out', 'cat', '/sdcard/android-vocab-window.xml') -Capture
            $lastWindowXmlText = $windowXml -join "`n"
            if ($lastWindowXmlText -match $expectedTextPattern) {
                return
            }
        } catch {
            $lastDumpError = $_.Exception.Message
            Write-Host "[info] UI dump attempt $attempt failed: $($_.Exception.Message)"
        }
        Assert-NoFatalCrash
        Start-Sleep -Seconds 2
    }

    $failureMessage = 'App launched, but expected first-screen text was not visible within 30 seconds.'
    if (-not [string]::IsNullOrWhiteSpace($lastWindowXmlText)) {
        try {
            $dumpPath = Save-LastWindowDump $lastWindowXmlText
            if (-not [string]::IsNullOrWhiteSpace($dumpPath)) {
                $failureMessage = "$failureMessage`nLast UI dump saved to: $dumpPath"
            }
        } catch {
            $failureMessage = "$failureMessage`nFailed to save last UI dump: $($_.Exception.Message)"
        }
        $failureMessage = "$failureMessage`nLast UI dump excerpt:`n$(Get-TextExcerpt $lastWindowXmlText 2000)"
    } elseif (-not [string]::IsNullOrWhiteSpace($lastDumpError)) {
        $failureMessage = "$failureMessage`nLast UI dump error: $lastDumpError"
    }
    Throw-WithFatalCrashContext $failureMessage
}

function Assert-NoFatalCrash {
    $crashText = Get-FatalCrashText
    if (-not [string]::IsNullOrWhiteSpace($crashText)) {
        throw "Fatal crash detected after launch:`n$crashText"
    }
}

function Grant-PostNotificationsPermission {
    $sdkVersionText = (Invoke-AndroidDebugBridge @('shell', 'getprop', 'ro.build.version.sdk') -Capture | Select-Object -First 1).Trim()
    $sdkVersion = 0
    if (-not [int]::TryParse($sdkVersionText, [ref]$sdkVersion)) {
        throw "Unable to parse Android SDK version for notification permission grant: $sdkVersionText"
    }

    if ($sdkVersion -lt 33) {
        Write-Host "[info] POST_NOTIFICATIONS runtime permission is not required on SDK $sdkVersion"
        return
    }

    Write-Host "[info] granting POST_NOTIFICATIONS on SDK $sdkVersion"
    try {
        Invoke-AndroidDebugBridge @('shell', 'pm', 'grant', $packageName, 'android.permission.POST_NOTIFICATIONS') -Capture | Out-Null
    } catch {
        throw "Failed to grant POST_NOTIFICATIONS for $packageName on SDK $sdkVersion. Cause: $($_.Exception.Message)"
    }
}

function Clear-AppData {
    Write-Warning "Resetting app data for $packageName."
    try {
        $clearOutput = Invoke-AndroidDebugBridge @('shell', 'pm', 'clear', $packageName) -Capture
        $clearText = ($clearOutput -join ' ').Trim()
        if (-not [string]::IsNullOrWhiteSpace($clearText)) {
            Write-Host "[info] pm clear result: $clearText"
        }
    } catch {
        throw "Failed to reset app data for $packageName. Cause: $($_.Exception.Message)"
    }
}

$script:AdbPath = Get-AndroidTool -Names @('adb.exe', 'adb') -RelativeDirs @('platform-tools')

if ($ListDevices) {
    $result = Invoke-AdbDevicesCommand -Arguments @('devices', '-l')
    $result.Output | Write-Output
    exit $result.ExitCode
}

Clear-LastWindowDump

$script:StartedAt = Get-Date -Format o
$script:ResolvedApkPath = $null
$script:ResolvedDeviceSerial = $null

try {
    $script:ResolvedDeviceSerial = Resolve-DeviceSerial
    Write-Host "[info] device=$script:ResolvedDeviceSerial"
    Save-ApkSmokeRunMetadata `
        -RunStatus 'started' `
        -StartedAt $script:StartedAt `
        -CompletedAt '<not completed>'

    if (-not $SkipInstall) {
        $script:ResolvedApkPath =
            if ([string]::IsNullOrWhiteSpace($ApkPath)) {
                Get-DefaultApk
            } else {
                (Resolve-Path -LiteralPath $ApkPath).Path
            }

        Warn-IfExplicitApkPathIsNotVersioned $script:ResolvedApkPath

        Write-Host "[info] verifying APK signature: $script:ResolvedApkPath"
        Test-ApkSignature $script:ResolvedApkPath

        Write-Host '[info] installing APK'
        Invoke-AndroidDebugBridge @('install', '-r', $script:ResolvedApkPath)
    } else {
        Write-Host '[info] skipping APK signature verification and install; using already installed app'
    }

    if ($ResetAppData) {
        Clear-AppData
    }

    if ($GrantNotificationPermission) {
        Grant-PostNotificationsPermission
    }

    Invoke-AndroidDebugBridge @('logcat', '-c')
    Write-Host '[info] launching app'
    Invoke-AndroidDebugBridge @('shell', 'monkey', '-p', $packageName, '-c', 'android.intent.category.LAUNCHER', '1')
    Start-Sleep -Seconds 3

    $appProcessIdText = Invoke-AndroidDebugBridge @('shell', 'pidof', $packageName) -Capture | Select-Object -First 1
    $appProcessId = if ($null -eq $appProcessIdText) { '' } else { $appProcessIdText.Trim() }
    if ([string]::IsNullOrWhiteSpace($appProcessId)) {
        Throw-WithFatalCrashContext "App process is not running after launch: $packageName"
    }
    Write-Host "[info] app pid=$appProcessId"

    Wait-ForVisibleAppText
    Assert-NoFatalCrash

    if ($SkipInstall) {
        Write-Host '[ok] installed app launch smoke passed; APK signature verification and install were skipped'
    } else {
        Write-Host '[ok] release APK smoke test passed'
    }
    Save-ApkSmokeRunMetadata `
        -RunStatus 'completed' `
        -StartedAt $script:StartedAt `
        -CompletedAt (Get-Date -Format o)
} catch {
    try {
        Save-ApkSmokeRunMetadata `
            -RunStatus 'failed' `
            -StartedAt $script:StartedAt `
            -CompletedAt (Get-Date -Format o) `
            -FailureMessage $_.Exception.Message
    } catch {
        Write-Warning "Failed to save APK smoke metadata: $($_.Exception.Message)"
    }
    throw
}
