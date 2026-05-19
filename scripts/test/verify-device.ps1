param(
    [string]$DeviceSerial,
    [string]$BundlePath,
    [string]$LogDir,
    [switch]$ListDevices,
    [switch]$SkipConnectedTests,
    [switch]$SkipBundleBuild,
    [switch]$SkipBundleSmoke,
    [switch]$AllowUnsignedBundle,
    [switch]$AllowDebugSigning,
    [switch]$ResetAppData,
    [switch]$GrantNotificationPermission
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\lib\android-env.ps1')
$repoRoot = Get-AndroidVocabularyRepoRoot
$adb = Get-AndroidTool -Names @('adb.exe', 'adb') -RelativeDirs @('platform-tools')

function Get-ConnectedDeviceSerials {
    $output = & $adb devices
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

function Invoke-LoggedStep {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    $safeName = $Name -replace '[^A-Za-z0-9_.-]', '-'
    $logPath = Join-Path $script:ResolvedLogDir "$safeName.log"
    Write-Host "[info] $Name"
    try {
        & $Command *>&1 | Tee-Object -FilePath $logPath
        if ($LASTEXITCODE -ne 0) {
            throw "$Name failed with exit code $LASTEXITCODE. See $logPath"
        }
    } catch {
        Write-Error "$Name failed. See $logPath"
        throw
    }
}

function Save-DeviceDiagnostics {
    param([string]$ResolvedDeviceSerial)

    Set-Content -LiteralPath (Join-Path $script:ResolvedLogDir 'selected-device.txt') -Value $ResolvedDeviceSerial
    & $adb devices -l | Set-Content -LiteralPath (Join-Path $script:ResolvedLogDir 'devices.txt')
    & $adb -s $ResolvedDeviceSerial shell getprop | Set-Content -LiteralPath (Join-Path $script:ResolvedLogDir 'getprop.txt')
}

if ($ListDevices) {
    & $adb devices -l
    exit $LASTEXITCODE
}

Use-AndroidVocabularyJavaHome
$resolvedDeviceSerial = Resolve-DeviceSerial
$script:ResolvedLogDir =
    if ([string]::IsNullOrWhiteSpace($LogDir)) {
        Join-Path $repoRoot "build\device-verification\$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    } else {
        $LogDir
    }
New-Item -ItemType Directory -Force -Path $script:ResolvedLogDir | Out-Null

Write-Host "[info] device=$resolvedDeviceSerial"
Write-Host "[info] logs=$script:ResolvedLogDir"
Save-DeviceDiagnostics $resolvedDeviceSerial

if (-not $SkipConnectedTests) {
    $previousAndroidSerial = $env:ANDROID_SERIAL
    $env:ANDROID_SERIAL = $resolvedDeviceSerial
    try {
        Invoke-LoggedStep 'connectedDebugAndroidTest' {
            & (Join-Path $repoRoot 'gradlew.bat') --no-daemon --console=plain connectedDebugAndroidTest
        }
    } finally {
        $env:ANDROID_SERIAL = $previousAndroidSerial
    }
}

if (-not $SkipBundleSmoke) {
    if (-not $SkipBundleBuild) {
        $bundleBuildArgs = @()
        if ($AllowUnsignedBundle) {
            $bundleBuildArgs += '-AllowUnsigned'
        }
        Invoke-LoggedStep 'build-release-bundle' {
            & (Join-Path $repoRoot 'scripts\release\build-bundle.ps1') @bundleBuildArgs
        }
    }

    $bundleSmokeArgs = @('-DeviceSerial', $resolvedDeviceSerial)
    if (-not [string]::IsNullOrWhiteSpace($BundlePath)) {
        $bundleSmokeArgs += @('-BundlePath', $BundlePath)
    }
    if ($AllowDebugSigning) {
        $bundleSmokeArgs += '-AllowDebugSigning'
    }
    if ($ResetAppData) {
        $bundleSmokeArgs += '-ResetAppData'
    }
    if ($GrantNotificationPermission) {
        $bundleSmokeArgs += '-GrantNotificationPermission'
    }

    Invoke-LoggedStep 'smoke-release-bundle' {
        & (Join-Path $repoRoot 'scripts\test\smoke-release-bundle.ps1') @bundleSmokeArgs
    }
}

& $adb -s $resolvedDeviceSerial logcat -d -t 1000 > (Join-Path $script:ResolvedLogDir 'logcat-tail.txt')
Write-Host '[ok] device verification passed'
Write-Host "[ok] logs: $script:ResolvedLogDir"
