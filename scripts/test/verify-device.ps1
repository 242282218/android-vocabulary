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

. (Join-Path (Join-Path $PSScriptRoot '..') (Join-Path 'lib' 'android-env.ps1'))
$repoRoot = Get-AndroidVocabularyRepoRoot
$adb = $null
$packageName = 'com.zzz.androidvocab'

function Invoke-AdbDevicesCommand {
    param([string[]]$Arguments)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $adb @Arguments 2>&1
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

function Invoke-LoggedStep {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    $safeName = $Name -replace '[^A-Za-z0-9_.-]', '-'
    $logPath = Join-Path $script:ResolvedLogDir "$safeName.log"
    Write-Host "[info] $Name"
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & $Command *>&1 | ConvertTo-AndroidVocabularyOutputText | Tee-Object -FilePath $logPath
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
        if ($exitCode -ne 0) {
            throw "$Name failed with exit code $exitCode"
        }
    } catch {
        $logTail = ''
        if (Test-Path -LiteralPath $logPath) {
            $logText = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
            if (-not [string]::IsNullOrWhiteSpace($logText)) {
                $logTail = "`nLast output:`n$(Get-AndroidVocabularyTextTail -Text $logText -MaxLength 3000)"
            }
        }
        throw "$Name failed. See $logPath. Cause: $($_.Exception.Message)$logTail"
    }
}

function Save-DeviceDiagnostics {
    param([string]$ResolvedDeviceSerial)

    Set-Content -LiteralPath (Join-Path $script:ResolvedLogDir 'selected-device.txt') -Value $ResolvedDeviceSerial
    Save-DeviceDiagnosticCommand 'adb devices -l' 'devices.txt' @('devices', '-l')
    Save-DeviceDiagnosticCommand 'adb shell getprop' 'getprop.txt' @('-s', $ResolvedDeviceSerial, 'shell', 'getprop')
}

function Save-DeviceDiagnosticCommand {
    param(
        [string]$Name,
        [string]$FileName,
        [string[]]$Arguments
    )

    $outputPath = Join-Path $script:ResolvedLogDir $FileName
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $adb @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $outputText = @($output | ConvertTo-AndroidVocabularyOutputText)
    Set-Content -LiteralPath $outputPath -Value $outputText
    if ($exitCode -ne 0) {
        throw "$Name failed with exit code $exitCode. See $outputPath. Output: $($outputText -join "`n")"
    }
}

function Save-LogcatTail {
    param([string]$ResolvedDeviceSerial)

    $logcatPath = Join-Path $script:ResolvedLogDir 'logcat-tail.txt'
    try {
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $logcatOutput = & $adb -s $ResolvedDeviceSerial logcat -d -t 1000 2>&1
            $logcatExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }

        $logcatText = @($logcatOutput | ConvertTo-AndroidVocabularyOutputText)
        $logcatText | Set-Content -LiteralPath $logcatPath
        if ($logcatExitCode -ne 0) {
            Write-Warning "adb logcat failed with exit code $logcatExitCode. Partial output was saved to $logcatPath"
        }
    } catch {
        Write-Warning "Failed to save logcat tail: $($_.Exception.Message)"
    }
}

function Uninstall-AppIfPresent {
    param([string]$ResolvedDeviceSerial)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $adb -s $ResolvedDeviceSerial uninstall $packageName 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $outputText = @($output | ConvertTo-AndroidVocabularyOutputText)
    $combinedOutput = ($outputText -join ' ').Trim()
    if ($exitCode -eq 0) {
        Write-Host "[info] uninstalled existing $packageName before release bundle smoke"
        return
    }
    if ($combinedOutput -match 'not installed|Unknown package') {
        Write-Host "[info] $packageName was not installed before release bundle smoke"
        return
    }
    throw "Failed to uninstall $packageName before release bundle smoke: $combinedOutput"
}

function Save-ApkSmokeFailureArtifacts {
    param([string]$FailureMessage)

    if ([string]::IsNullOrWhiteSpace($FailureMessage)) {
        return
    }
    if ($FailureMessage -notmatch 'Last UI dump saved to:\s*(.+?)(?:\r?\n|$)') {
        return
    }

    $sourcePath = $Matches[1].Trim()
    if (-not (Test-Path -LiteralPath $sourcePath)) {
        Write-Warning "APK smoke UI dump was referenced but not found: $sourcePath"
        return
    }

    Copy-Item `
        -LiteralPath $sourcePath `
        -Destination (Join-Path $script:ResolvedLogDir 'apk-smoke-last-window.xml') `
        -Force
}

function Get-BundleSmokeRunMetadataPath {
    return Join-AndroidVocabularyPath $repoRoot @('build', 'bundle-smoke', 'smoke-release-bundle-run.txt')
}

function Clear-BundleSmokeRunMetadataArtifact {
    Remove-Item -LiteralPath (Get-BundleSmokeRunMetadataPath) -Force -ErrorAction SilentlyContinue
}

function Save-BundleSmokeRunMetadataArtifact {
    $sourcePath = Get-BundleSmokeRunMetadataPath
    if (-not (Test-Path -LiteralPath $sourcePath)) {
        Write-Warning "Bundle smoke metadata was not found: $sourcePath"
        return
    }

    Copy-Item `
        -LiteralPath $sourcePath `
        -Destination (Join-Path $script:ResolvedLogDir 'smoke-release-bundle-run.txt') `
        -Force
}

function Read-KeyValueMetadata {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line -notmatch '=') {
            continue
        }
        $separatorIndex = $line.IndexOf('=')
        $key = $line.Substring(0, $separatorIndex)
        $value = $line.Substring($separatorIndex + 1)
        $values[$key] = $value
    }
    return $values
}

function Get-ArchivedBundleSmokeRunMetadataPath {
    return Join-Path $script:ResolvedLogDir 'smoke-release-bundle-run.txt'
}

function Get-BundleSmokeRunMetadataValue {
    param(
        [string]$Key,
        [string]$Default = '<missing>'
    )

    if ($SkipBundleSmoke) {
        return '<not used>'
    }

    $metadata = Read-KeyValueMetadata (Get-ArchivedBundleSmokeRunMetadataPath)
    if ($null -eq $metadata -or -not $metadata.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace($metadata[$Key])) {
        return $Default
    }
    return $metadata[$Key]
}

function Get-VerificationLogDir {
    param([string]$ReleaseVersionLabel)

    if (-not [string]::IsNullOrWhiteSpace($LogDir)) {
        return $LogDir
    }

    $safeLabel =
        if ([string]::IsNullOrWhiteSpace($ReleaseVersionLabel)) {
            'unresolved'
        } else {
            $ReleaseVersionLabel -replace '[^A-Za-z0-9_.-]', '-'
        }
    if ([string]::IsNullOrWhiteSpace($safeLabel)) {
        $safeLabel = 'unresolved'
    }

    return Join-AndroidVocabularyPath $repoRoot @(
        'build',
        'device-verification',
        "$safeLabel-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    )
}

function Ensure-VerificationLogDir {
    param([string]$ReleaseVersionLabel)

    if ([string]::IsNullOrWhiteSpace($script:ResolvedLogDir)) {
        $script:ResolvedLogDir = Get-VerificationLogDir $ReleaseVersionLabel
    }
    New-Item -ItemType Directory -Force -Path $script:ResolvedLogDir | Out-Null
}

function Resolve-FailureStage {
    param(
        [string]$FailureMessage,
        [string]$FallbackStage
    )

    if (-not [string]::IsNullOrWhiteSpace($FailureMessage)) {
        if ($FailureMessage -match 'Android SDK not found|Android tool not found') {
            return 'androidTool'
        }
        if ($FailureMessage -match 'JAVA_HOME is not set|JDK 17|java\.exe not found') {
            return 'javaEnvironment'
        }
        if ($FailureMessage -match 'Gradle property not found|gradle\.properties') {
            return 'versionMetadata'
        }
        if ($FailureMessage -match "Device '.+' is not online|No online Android device found|Multiple devices found|adb devices failed") {
            return 'resolveDevice'
        }
        if ($FailureMessage -match 'bundletool build-apks failed') {
            return 'bundletoolBuildApks'
        }
        if ($FailureMessage -match 'bundletool install-apks failed') {
            return 'bundletoolInstallApks'
        }
        if (
            $FailureMessage -match 'installed app launch smoke after bundle install failed' -or
            $FailureMessage -match 'Last UI dump saved to:' -or
            $FailureMessage -match 'App process is not running after launch' -or
            $FailureMessage -match 'Fatal crash detected after launch'
        ) {
            return 'installedAppLaunchSmoke'
        }
        if ($FailureMessage -match 'AAB signature verification failed') {
            return 'bundleSignatureVerification'
        }
        if ($FailureMessage -match '^smoke-release-bundle failed') {
            return 'smokeReleaseBundle'
        }
        if ($FailureMessage -match '^build-release-bundle failed') {
            return 'buildReleaseBundle'
        }
        if ($FailureMessage -match '^connectedDebugAndroidTest failed') {
            return 'connectedDebugAndroidTest'
        }
        if ($FailureMessage -match 'adb devices -l failed|adb shell getprop failed') {
            return 'deviceDiagnostics'
        }
    }

    if ([string]::IsNullOrWhiteSpace($FallbackStage)) {
        return '<unknown>'
    }
    return $FallbackStage
}

function Get-ResolvedBundleMetadata {
    param([string]$ExpectedBundleName)

    if ($SkipBundleSmoke) {
        return @{
            Source = 'skipped'
            Input = if ([string]::IsNullOrWhiteSpace($BundlePath)) { '<default>' } else { $BundlePath }
            Path = '<not used>'
            Status = 'skipped'
            MatchesCurrentVersionedName = '<not used>'
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($BundlePath)) {
        try {
            $resolvedPath = (Resolve-Path -LiteralPath $BundlePath -ErrorAction Stop).Path
            $status = 'exists'
        } catch {
            $resolvedPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($BundlePath)
            $status = 'missing'
        }
        return @{
            Source = 'explicit'
            Input = $BundlePath
            Path = $resolvedPath
            Status = $status
            MatchesCurrentVersionedName =
                if ($ExpectedBundleName -eq '<unresolved>') {
                    '<unknown>'
                } else {
                    ([IO.Path]::GetFileName($resolvedPath) -eq $ExpectedBundleName)
                }
        }
    }

    if ($ExpectedBundleName -eq '<unresolved>') {
        return @{
            Source = 'defaultVersionedDist'
            Input = '<default>'
            Path = '<unresolved>'
            Status = '<unknown>'
            MatchesCurrentVersionedName = '<unknown>'
        }
    }

    $expectedPath = Join-Path (Join-Path $repoRoot 'dist') $ExpectedBundleName
    $status =
        if (Test-Path -LiteralPath $expectedPath) {
            'exists'
        } elseif (-not $SkipBundleBuild) {
            'willBeBuilt'
        } else {
            'missing'
        }
    return @{
        Source = 'defaultVersionedDist'
        Input = '<default>'
        Path = $expectedPath
        Status = $status
        MatchesCurrentVersionedName = $true
    }
}

function Get-ResolvedBundleSha256 {
    param([hashtable]$BundleMetadata)

    if ($SkipBundleSmoke) {
        return '<not used>'
    }
    $bundlePath = $BundleMetadata.Path
    if ([string]::IsNullOrWhiteSpace($bundlePath) -or $bundlePath -eq '<unresolved>') {
        return '<unresolved>'
    }
    if (-not (Test-Path -LiteralPath $bundlePath)) {
        return '<missing>'
    }
    return (Get-FileHash -LiteralPath $bundlePath -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-VerificationDefaultBundleName {
    if (
        [string]::IsNullOrWhiteSpace($BundlePath) -and
        (-not $SkipBundleBuild) -and
        $AllowUnsignedBundle
    ) {
        return Get-AndroidVocabularyBuildValidationBundleName
    }
    return Get-AndroidVocabularyReleaseBundleName
}

function Get-VerificationBundlePathOverride {
    if (-not [string]::IsNullOrWhiteSpace($BundlePath)) {
        return $BundlePath
    }
    if (-not $SkipBundleBuild -and $AllowUnsignedBundle) {
        return Join-Path (Join-Path $repoRoot 'dist') (Get-AndroidVocabularyBuildValidationBundleName)
    }
    return $null
}

function Save-RunMetadata {
    param(
        [string]$ResolvedDeviceSerial,
        [string]$VersionName,
        [string]$VersionCode,
        [string]$ExpectedBundleName,
        [bool]$UsesLocalOnlyReleaseOverrides,
        [string]$RunStatus,
        [string]$StartedAt,
        [string]$CompletedAt,
        [string]$FileName,
        [string]$FailureMessage,
        [string]$FailureStage = '<none>'
    )

    $bundleMetadata = Get-ResolvedBundleMetadata $ExpectedBundleName
    $bundleSha256 = Get-ResolvedBundleSha256 $bundleMetadata
    $connectedTestsLog = if ($SkipConnectedTests) { '<skipped>' } else { 'connectedDebugAndroidTest.log' }
    $bundleBuildLog =
        if ($SkipBundleSmoke -or $SkipBundleBuild) {
            '<skipped>'
        } else {
            'build-release-bundle.log'
        }
    $bundleSmokeLog = if ($SkipBundleSmoke) { '<skipped>' } else { 'smoke-release-bundle.log' }
    $bundleSmokeRunMetadata =
        if (Test-Path -LiteralPath (Join-Path $script:ResolvedLogDir 'smoke-release-bundle-run.txt')) {
            'smoke-release-bundle-run.txt'
        } else {
            '<none>'
        }
    $bundleSmokeRunStatus = Get-BundleSmokeRunMetadataValue 'runStatus'
    $bundleSmokeBundleSha256 = Get-BundleSmokeRunMetadataValue 'bundleSha256'
    $bundleSmokeBundlePathMatchesCurrentVersionedName =
        Get-BundleSmokeRunMetadataValue 'bundlePathMatchesCurrentVersionedName'
    $bundleSmokeReleaseReady = Get-BundleSmokeRunMetadataValue 'bundleSmokeReleaseReady'
    $apkSmokeLastWindowDump =
        if (Test-Path -LiteralPath (Join-Path $script:ResolvedLogDir 'apk-smoke-last-window.xml')) {
            'apk-smoke-last-window.xml'
        } else {
            '<none>'
        }
    $bundleSmokeApkSetSigning =
        if ($SkipBundleSmoke) {
            '<skipped>'
        } elseif ($AllowDebugSigning) {
            'debug-signing'
        } else {
            'release-signing'
        }

    $metadata = @(
        "runStatus=$RunStatus",
        "startedAt=$StartedAt",
        "completedAt=$CompletedAt",
        "failureStage=$FailureStage",
        "versionName=$VersionName",
        "versionCode=$VersionCode",
        "deviceSerial=$ResolvedDeviceSerial",
        "bundlePath=$($bundleMetadata.Path)",
        "bundlePathInput=$($bundleMetadata.Input)",
        "bundlePathSource=$($bundleMetadata.Source)",
        "bundlePathStatus=$($bundleMetadata.Status)",
        "bundlePathExpectedName=$ExpectedBundleName",
        "bundlePathMatchesCurrentVersionedName=$($bundleMetadata.MatchesCurrentVersionedName)",
        "bundleSha256=$bundleSha256",
        "connectedTestsLog=$connectedTestsLog",
        "bundleBuildLog=$bundleBuildLog",
        "bundleSmokeLog=$bundleSmokeLog",
        "bundleSmokeRunMetadata=$bundleSmokeRunMetadata",
        "bundleSmokeRunStatus=$bundleSmokeRunStatus",
        "bundleSmokeBundleSha256=$bundleSmokeBundleSha256",
        "bundleSmokeBundlePathMatchesCurrentVersionedName=$bundleSmokeBundlePathMatchesCurrentVersionedName",
        "bundleSmokeReleaseReady=$bundleSmokeReleaseReady",
        "apkSmokeLastWindowDump=$apkSmokeLastWindowDump",
        "selectedDeviceLog=selected-device.txt",
        "devicesLog=devices.txt",
        "getpropLog=getprop.txt",
        "logcatTailLog=logcat-tail.txt",
        "bundleSmokeApkSetSigning=$bundleSmokeApkSetSigning",
        "skipConnectedTests=$($SkipConnectedTests.IsPresent)",
        "skipBundleBuild=$($SkipBundleBuild.IsPresent)",
        "skipBundleSmoke=$($SkipBundleSmoke.IsPresent)",
        "allowUnsignedBundle=$($AllowUnsignedBundle.IsPresent)",
        "allowDebugSigning=$($AllowDebugSigning.IsPresent)",
        "resetAppData=$($ResetAppData.IsPresent)",
        "grantNotificationPermission=$($GrantNotificationPermission.IsPresent)",
        "usesLocalOnlyReleaseOverrides=$UsesLocalOnlyReleaseOverrides"
    )
    if (-not [string]::IsNullOrWhiteSpace($FailureMessage)) {
        $normalizedFailureMessage = $FailureMessage.Replace("`r`n", ' ').Replace("`n", ' ').Replace("`r", ' ')
        $metadata += "failureMessage=$normalizedFailureMessage"
    }
    Set-Content -LiteralPath (Join-Path $script:ResolvedLogDir $FileName) -Value $metadata
}

if ($ListDevices) {
    $adb = Get-AndroidTool -Names @('adb.exe', 'adb') -RelativeDirs @('platform-tools')
    $result = Invoke-AdbDevicesCommand -Arguments @('devices', '-l')
    $result.Output | Write-Output
    exit $result.ExitCode
}

$script:StartedAt = Get-Date -Format o
$script:CurrentVerificationStage = 'androidTool'
$script:ResolvedLogDir = $null
$versionName = '<unresolved>'
$versionCode = '<unresolved>'
$releaseVersionLabel = 'unresolved'
$expectedBundleName = '<unresolved>'
$resolvedDeviceSerial = '<unresolved>'
$usesLocalOnlyReleaseOverrides =
    -not $SkipBundleSmoke -and
    (
        $AllowDebugSigning -or
        (-not $SkipBundleBuild -and $AllowUnsignedBundle)
    )

try {
    $adb = Get-AndroidTool -Names @('adb.exe', 'adb') -RelativeDirs @('platform-tools')

    $script:CurrentVerificationStage = 'javaEnvironment'
    Use-AndroidVocabularyJavaHome

    $script:CurrentVerificationStage = 'versionMetadata'
    $versionName = Get-GradlePropertyValue 'androidVocab.versionName'
    $versionCode = Get-GradlePropertyValue 'androidVocab.versionCode'
    $releaseVersionLabel = "v$versionName-$versionCode"
    $expectedBundleName = Get-VerificationDefaultBundleName
    Ensure-VerificationLogDir $releaseVersionLabel

    $script:CurrentVerificationStage = 'resolveDevice'
    $resolvedDeviceSerial = Resolve-DeviceSerial

    Write-Host "[info] version=$releaseVersionLabel"
    Write-Host "[info] device=$resolvedDeviceSerial"
    Write-Host "[info] logs=$script:ResolvedLogDir"
    Save-RunMetadata `
        -ResolvedDeviceSerial $resolvedDeviceSerial `
        -VersionName $versionName `
        -VersionCode $versionCode `
        -ExpectedBundleName $expectedBundleName `
        -UsesLocalOnlyReleaseOverrides $usesLocalOnlyReleaseOverrides `
        -RunStatus 'started' `
        -StartedAt $script:StartedAt `
        -CompletedAt '<not completed>' `
        -FileName 'verification-run.txt'

    try {
        $script:CurrentVerificationStage = 'deviceDiagnostics'
        Save-DeviceDiagnostics $resolvedDeviceSerial

        if (-not $SkipConnectedTests) {
            $script:CurrentVerificationStage = 'connectedDebugAndroidTest'
            $previousAndroidSerial = $env:ANDROID_SERIAL
            $env:ANDROID_SERIAL = $resolvedDeviceSerial
            try {
                Invoke-LoggedStep 'connectedDebugAndroidTest' {
                    & (Get-GradleWrapper) --no-daemon --console=plain connectedDebugAndroidTest
                }
            } finally {
                $env:ANDROID_SERIAL = $previousAndroidSerial
            }
        }

        if (-not $SkipBundleSmoke) {
            if (-not $SkipConnectedTests) {
                Uninstall-AppIfPresent $resolvedDeviceSerial
            }

            if (-not $SkipBundleBuild) {
                $script:CurrentVerificationStage = 'buildReleaseBundle'
                $bundleBuildParams = @{}
                if ($AllowUnsignedBundle) {
                    $bundleBuildParams.AllowUnsigned = $true
                }
                Invoke-LoggedStep 'build-release-bundle' {
                    & (Join-AndroidVocabularyPath $repoRoot @('scripts', 'release', 'build-bundle.ps1')) @bundleBuildParams
                }
            }

            $script:CurrentVerificationStage = 'smokeReleaseBundle'
            $bundleSmokeParams = @{
                DeviceSerial = $resolvedDeviceSerial
            }
            $bundlePathOverride = Get-VerificationBundlePathOverride
            if (-not [string]::IsNullOrWhiteSpace($bundlePathOverride)) {
                $bundleSmokeParams.BundlePath = $bundlePathOverride
            }
            if ($AllowDebugSigning) {
                $bundleSmokeParams.AllowDebugSigning = $true
            }
            if ($ResetAppData) {
                $bundleSmokeParams.ResetAppData = $true
            }
            if ($GrantNotificationPermission) {
                $bundleSmokeParams.GrantNotificationPermission = $true
            }

            Clear-BundleSmokeRunMetadataArtifact
            Invoke-LoggedStep 'smoke-release-bundle' {
                & (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'smoke-release-bundle.ps1')) @bundleSmokeParams
            }
            Save-BundleSmokeRunMetadataArtifact
        }
    } finally {
        Save-LogcatTail $resolvedDeviceSerial
    }

    Save-RunMetadata `
        -ResolvedDeviceSerial $resolvedDeviceSerial `
        -VersionName $versionName `
        -VersionCode $versionCode `
        -ExpectedBundleName $expectedBundleName `
        -UsesLocalOnlyReleaseOverrides $usesLocalOnlyReleaseOverrides `
        -RunStatus 'completed' `
        -StartedAt $script:StartedAt `
        -CompletedAt (Get-Date -Format o) `
        -FileName 'verification-completed.txt'

    if ($usesLocalOnlyReleaseOverrides) {
        Write-Host '[ok] device verification passed with local-only release overrides; not release-ready'
    } else {
        Write-Host '[ok] device verification passed'
    }
    Write-Host "[ok] logs: $script:ResolvedLogDir"
} catch {
    try {
        $failureMessage = $_.Exception.Message
        $failureStage = Resolve-FailureStage `
            -FailureMessage $failureMessage `
            -FallbackStage $script:CurrentVerificationStage
        Ensure-VerificationLogDir $releaseVersionLabel
        if ($script:CurrentVerificationStage -eq 'smokeReleaseBundle') {
            Save-BundleSmokeRunMetadataArtifact
        }
        Save-ApkSmokeFailureArtifacts $failureMessage
        Save-RunMetadata `
            -ResolvedDeviceSerial $resolvedDeviceSerial `
            -VersionName $versionName `
            -VersionCode $versionCode `
            -ExpectedBundleName $expectedBundleName `
            -UsesLocalOnlyReleaseOverrides $usesLocalOnlyReleaseOverrides `
            -RunStatus 'failed' `
            -StartedAt $script:StartedAt `
            -CompletedAt (Get-Date -Format o) `
            -FileName 'verification-failed.txt' `
            -FailureMessage $failureMessage `
            -FailureStage $failureStage
        Write-Warning "Verification failure metadata saved to: $script:ResolvedLogDir"
    } catch {
        Write-Warning "Failed to save verification failure metadata: $($_.Exception.Message)"
    }
    throw
}
