param(
    [string]$BundlePath,
    [string]$BundleToolJar,
    [string]$DeviceSerial,
    [string]$ApksPath,
    [switch]$BuildOnly,
    [switch]$ResetAppData,
    [switch]$GrantNotificationPermission,
    [switch]$AllowDebugSigning,
    [switch]$ListDevices
)

$ErrorActionPreference = 'Stop'

. (Join-Path (Join-Path $PSScriptRoot '..') (Join-Path 'lib' 'android-env.ps1'))
$repoRoot = Get-AndroidVocabularyRepoRoot

function Get-DefaultBundle {
    $expectedBundleName = Get-AndroidVocabularyReleaseBundleName
    $candidate = Join-Path (Join-Path $repoRoot 'dist') $expectedBundleName
    if (Test-Path $candidate) {
        return (Resolve-Path -LiteralPath $candidate).Path
    }

    $searchDirs = @(
        (Join-Path $repoRoot 'dist'),
        (Join-AndroidVocabularyPath $repoRoot @('app', 'build', 'outputs', 'bundle', 'release'))
    )
    $availableBundles =
        $searchDirs |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem -Path $_ -Filter '*.aab' -File } |
        ForEach-Object { $_.FullName }

    $availableText = if ($availableBundles.Count -eq 0) { '<none>' } else { $availableBundles -join ', ' }
    throw "No AAB found for current version: $expectedBundleName. Available AABs: $availableText. Pass -BundlePath, or run scripts/release/build-bundle.ps1 first."
}

function Warn-IfExplicitBundlePathIsNotVersioned {
    param([string]$ResolvedBundlePath)

    if ([string]::IsNullOrWhiteSpace($BundlePath)) {
        return
    }

    $expectedBundleName = Get-AndroidVocabularyReleaseBundleName
    $bundleFileName = [IO.Path]::GetFileName($ResolvedBundlePath)
    if ($bundleFileName -eq $expectedBundleName) {
        return
    }

    Write-Warning "Explicit BundlePath is not the current versioned release AAB '$expectedBundleName'. This is an override of the default release artifact: $ResolvedBundlePath"
}

function Test-BundleSignature {
    param([string]$ResolvedBundlePath)

    $jarSigner = Get-JarSigner
    $jarSignerArgs = @(
        '-J-Dfile.encoding=UTF-8',
        '-J-Dsun.stdout.encoding=UTF-8',
        '-J-Dsun.stderr.encoding=UTF-8',
        '-J-Duser.language=en',
        '-J-Duser.country=US',
        '-verify',
        '-certs',
        $ResolvedBundlePath
    )
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $jarSignerOutput = & $jarSigner @jarSignerArgs 2>&1
        $jarSignerExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $jarSignerText = @($jarSignerOutput | ConvertTo-AndroidVocabularyOutputText) -join "`n"
    $verified = (
        $jarSignerExitCode -eq 0 -and
        $jarSignerText -match '(?i)\bjar\s+verified\b' -and
        $jarSignerText -notmatch '(?i)jar\s+(is\s+)?unsigned' -and
        $jarSignerText -notmatch '(?i)no\s+manifest'
    )

    return [PSCustomObject]@{
        Verified = $verified
        Output = $jarSignerText.Trim()
    }
}

function Add-BundleToolSigningArgs {
    param([string[]]$InputArgs)

    $missingSigningVars = @(Test-ReleaseSigningEnvironment)
    if ($missingSigningVars.Count -gt 0) {
        $message = "Release signing is not configured. Missing: $($missingSigningVars -join ', ')."
        if (-not $AllowDebugSigning) {
            throw "$message Set the required environment variables, or rerun with -AllowDebugSigning for local-only bundletool output."
        }
        $script:UsesDebugSigning = $true
        Write-Warning "$message Continuing because -AllowDebugSigning was set."
        return $InputArgs
    }

    # Pass signing passwords via environment variables to avoid writing plaintext to disk.
    $env:BUNDLETOOL_STORE_PASSWORD = $env:ANDROID_VOCAB_RELEASE_STORE_PASSWORD
    $env:BUNDLETOOL_KEY_PASSWORD = $env:ANDROID_VOCAB_RELEASE_KEY_PASSWORD

    return $InputArgs + @(
        "--ks=$env:ANDROID_VOCAB_RELEASE_STORE_FILE",
        "--ks-key-alias=$env:ANDROID_VOCAB_RELEASE_KEY_ALIAS",
        "--ks-pass=env:BUNDLETOOL_STORE_PASSWORD",
        "--key-pass=env:BUNDLETOOL_KEY_PASSWORD"
    )
}

function Test-BundleSmokeReleaseReady {
    return $script:BundleSignatureStatus -eq 'verified' -and
        (-not $AllowDebugSigning) -and
        (-not $script:UsesDebugSigning)
}

function Get-DefaultApksPath {
    if (Test-BundleSmokeReleaseReady) {
        return Join-Path $script:WorkDir "$([IO.Path]::GetFileNameWithoutExtension($script:ResolvedBundlePath))-$script:SigningLabel.apks"
    }

    $buildValidationBundleName = Get-AndroidVocabularyBuildValidationBundleName
    $buildValidationBaseName = [IO.Path]::GetFileNameWithoutExtension($buildValidationBundleName)
    return Join-Path $script:WorkDir "$buildValidationBaseName-$script:SigningLabel.apks"
}

function Warn-IfAmbiguousBundleSmokeArtifactsExist {
    $artifactDir = Get-BundleSmokeArtifactDir
    if (-not (Test-Path -LiteralPath $artifactDir)) {
        $script:AmbiguousBundleSmokeArtifactNames = @()
        return
    }

    $resolvedApksPath =
        if ([string]::IsNullOrWhiteSpace($script:ResolvedApksPath)) {
            $null
        } else {
            $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($script:ResolvedApksPath)
        }
    $ambiguousFiles = @(
        Get-ChildItem -Path $artifactDir -Filter 'AndroidVocabulary-release-v*.apks' -File -ErrorAction SilentlyContinue
        Get-ChildItem -Path $artifactDir -Filter 'app-release.apks' -File -ErrorAction SilentlyContinue
    ) |
        Sort-Object FullName -Unique |
        Where-Object { $null -eq $resolvedApksPath -or $_.FullName -ne $resolvedApksPath }

    $script:AmbiguousBundleSmokeArtifactNames = @($ambiguousFiles | ForEach-Object { $_.Name })
    if ($script:AmbiguousBundleSmokeArtifactNames.Count -eq 0) {
        return
    }

    Write-Warning "Existing ambiguous bundle smoke APK Set artifacts were left untouched. They are historical intermediates, not upload artifacts: $($script:AmbiguousBundleSmokeArtifactNames -join ', ')"
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

function Invoke-BundleToolCommand {
    param(
        [string]$Name,
        [string[]]$Arguments
    )

    $java = Get-JavaExecutable
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $commandOutput = @()
    try {
        & $java @bundleToolJavaArgs @Arguments 2>&1 |
            ConvertTo-AndroidVocabularyOutputText |
            Tee-Object -Variable commandOutput
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        $outputText = @($commandOutput | ConvertTo-AndroidVocabularyOutputText) -join "`n"
        throw "$Name failed with exit code $exitCode.`nLast output:`n$(Get-AndroidVocabularyTextTail -Text $outputText -MaxLength 3000)"
    }
}

function Get-PowerShellExecutable {
    $candidates = @(
        (Join-Path $PSHOME 'powershell.exe'),
        (Join-Path $PSHOME 'pwsh.exe')
    )
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    $command = Get-Command powershell.exe, pwsh.exe -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $command) {
        throw 'PowerShell executable not found.'
    }
    return $command.Source
}

function Invoke-PowerShellScriptCommand {
    param(
        [string]$Name,
        [string]$ScriptPath,
        [string[]]$Arguments
    )

    $powerShell = Get-PowerShellExecutable
    $commandArgs = @('-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', $ScriptPath) + $Arguments
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $commandOutput = @()
    try {
        & $powerShell @commandArgs 2>&1 |
            ConvertTo-AndroidVocabularyOutputText |
            Tee-Object -Variable commandOutput
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        $outputText = @($commandOutput | ConvertTo-AndroidVocabularyOutputText) -join "`n"
        throw "$Name failed with exit code $exitCode.`nLast output:`n$(Get-AndroidVocabularyTextTail -Text $outputText -MaxLength 3000)"
    }
}

function Get-BundleSmokeArtifactDir {
    return Join-AndroidVocabularyPath $repoRoot @('build', 'bundle-smoke')
}

function Get-BundleSmokeRunMetadataPath {
    return Join-Path (Get-BundleSmokeArtifactDir) 'smoke-release-bundle-run.txt'
}

function Resolve-BundleSmokeFailureStage {
    param(
        [string]$FailureMessage,
        [string]$FallbackStage
    )

    if (-not [string]::IsNullOrWhiteSpace($FailureMessage)) {
        if ($FailureMessage -match 'bundletool build-apks failed') {
            return 'bundletoolBuildApks'
        }
        if ($FailureMessage -match 'bundletool install-apks failed') {
            return 'bundletoolInstallApks'
        }
        if (
            $FailureMessage -match 'installed app launch smoke after bundle install failed' -or
            $FailureMessage -match 'App process is not running after launch' -or
            $FailureMessage -match 'Fatal crash detected after launch' -or
            $FailureMessage -match 'Last UI dump saved to:'
        ) {
            return 'installedAppLaunchSmoke'
        }
        if ($FailureMessage -match 'Release signing is not configured|Release keystore does not exist') {
            return 'signingConfiguration'
        }
        if ($FailureMessage -match 'AAB signature verification failed') {
            return 'bundleSignatureVerification'
        }
        if ($FailureMessage -match 'JAVA_HOME is not set|JDK 17|java\.exe not found') {
            return 'javaEnvironment'
        }
        if ($FailureMessage -match 'bundletool\.jar not found') {
            return 'resolveBundleTool'
        }
        if ($FailureMessage -match 'No AAB found|Cannot find path') {
            return 'resolveBundle'
        }
        if ($FailureMessage -match "Device '.+' is not online|No online Android device found|Multiple devices found|adb devices failed") {
            return 'resolveDevice'
        }
    }

    if ([string]::IsNullOrWhiteSpace($FallbackStage)) {
        return '<unknown>'
    }
    return $FallbackStage
}

function Get-BundleSmokeMetadataValue {
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

function Get-BundleSmokeSha256Value {
    if ([string]::IsNullOrWhiteSpace($script:ResolvedBundlePath)) {
        return '<unresolved>'
    }
    if (-not (Test-Path -LiteralPath $script:ResolvedBundlePath)) {
        return '<missing>'
    }
    return (Get-FileHash -LiteralPath $script:ResolvedBundlePath -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Save-BundleSmokeRunMetadata {
    param(
        [string]$RunStatus,
        [string]$StartedAt,
        [string]$CompletedAt,
        [string]$FailureMessage,
        [string]$FailureStage = '<none>'
    )

    $versionName = Get-BundleSmokeMetadataValue { Get-GradlePropertyValue 'androidVocab.versionName' }
    $versionCode = Get-BundleSmokeMetadataValue { Get-GradlePropertyValue 'androidVocab.versionCode' }
    $expectedBundleName = Get-BundleSmokeMetadataValue { Get-AndroidVocabularyReleaseBundleName }
    $deviceSerialValue =
        if ($BuildOnly) {
            '<not used>'
        } elseif ([string]::IsNullOrWhiteSpace($script:ResolvedDeviceSerial)) {
            '<unresolved>'
        } else {
            $script:ResolvedDeviceSerial
        }
    $bundlePathSource =
        if ([string]::IsNullOrWhiteSpace($BundlePath)) {
            'defaultVersionedDist'
        } else {
            'explicit'
        }
    $bundlePathValue =
        if ([string]::IsNullOrWhiteSpace($script:ResolvedBundlePath)) {
            '<unresolved>'
        } else {
            $script:ResolvedBundlePath
        }
    $bundlePathMatchesCurrentVersionedName =
        if ([string]::IsNullOrWhiteSpace($script:ResolvedBundlePath)) {
            '<unknown>'
        } elseif ($expectedBundleName -eq '<unresolved>') {
            '<unknown>'
        } else {
            ([IO.Path]::GetFileName($script:ResolvedBundlePath) -eq $expectedBundleName)
        }
    $apksPathValue =
        if ([string]::IsNullOrWhiteSpace($script:ResolvedApksPath)) {
            '<unresolved>'
        } else {
            $script:ResolvedApksPath
        }
    $bundleToolValue =
        if ([string]::IsNullOrWhiteSpace($script:BundleToolPath)) {
            '<unresolved>'
        } else {
            $script:BundleToolPath
        }
    $signingLabel =
        if ([string]::IsNullOrWhiteSpace($script:SigningLabel)) {
            '<unresolved>'
        } else {
            $script:SigningLabel
        }
    $bundleSignatureStatus =
        if ([string]::IsNullOrWhiteSpace($script:BundleSignatureStatus)) {
            '<unresolved>'
        } else {
            $script:BundleSignatureStatus
        }
    $ambiguousBundleSmokeArtifacts =
        if ($script:AmbiguousBundleSmokeArtifactNames.Count -eq 0) {
            '<none>'
        } else {
            $script:AmbiguousBundleSmokeArtifactNames -join ', '
        }

    $metadata = @(
        "runStatus=$RunStatus",
        "startedAt=$StartedAt",
        "completedAt=$CompletedAt",
        "failureStage=$FailureStage",
        "versionName=$versionName",
        "versionCode=$versionCode",
        "deviceSerial=$deviceSerialValue",
        "deviceSerialInput=$(if ([string]::IsNullOrWhiteSpace($DeviceSerial)) { '<auto>' } else { $DeviceSerial })",
        "bundlePath=$bundlePathValue",
        "bundlePathInput=$(if ([string]::IsNullOrWhiteSpace($BundlePath)) { '<default>' } else { $BundlePath })",
        "bundlePathSource=$bundlePathSource",
        "bundlePathExpectedName=$expectedBundleName",
        "bundlePathMatchesCurrentVersionedName=$bundlePathMatchesCurrentVersionedName",
        "bundleSha256=$(Get-BundleSmokeSha256Value)",
        "apksPath=$apksPathValue",
        "apksPathInput=$(if ([string]::IsNullOrWhiteSpace($ApksPath)) { '<default>' } else { $ApksPath })",
        "apkSetSigning=$signingLabel",
        "bundleSignatureStatus=$bundleSignatureStatus",
        "bundleSignatureRequiredForRelease=$((-not $AllowDebugSigning).ToString())",
        "bundleSmokeReleaseReady=$(Test-BundleSmokeReleaseReady)",
        "existingAmbiguousApks=$ambiguousBundleSmokeArtifacts",
        "bundleToolJar=$bundleToolValue",
        "buildOnly=$($BuildOnly.IsPresent)",
        "allowDebugSigning=$($AllowDebugSigning.IsPresent)",
        "resetAppData=$($ResetAppData.IsPresent)",
        "grantNotificationPermission=$($GrantNotificationPermission.IsPresent)"
    )
    if (-not [string]::IsNullOrWhiteSpace($FailureMessage)) {
        $normalizedFailureMessage = $FailureMessage.Replace("`r`n", ' ').Replace("`n", ' ').Replace("`r", ' ')
        $metadata += "failureMessage=$normalizedFailureMessage"
    }

    New-Item -ItemType Directory -Force -Path (Get-BundleSmokeArtifactDir) | Out-Null
    Set-Content -LiteralPath (Get-BundleSmokeRunMetadataPath) -Value $metadata
}

if ($ListDevices) {
    Invoke-PowerShellScriptCommand `
        'list Android devices' `
        (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'smoke-release-apk.ps1')) `
        @('-ListDevices')
    exit 0
}

$script:StartedAt = Get-Date -Format o
$script:CurrentBundleSmokeStage = 'environment'
$script:ResolvedDeviceSerial = $null
$script:ResolvedBundlePath = $null
$script:ResolvedApksPath = $null
$script:BundleToolPath = $null
$script:SigningLabel = $null
$script:UsesDebugSigning = $false
$script:BundleSignatureStatus = $null
$script:AmbiguousBundleSmokeArtifactNames = @()

try {
    $script:CurrentBundleSmokeStage = 'javaEnvironment'
    Use-AndroidVocabularyJavaHome
    $script:CurrentBundleSmokeStage = 'resolveBundleTool'
    $script:BundleToolPath = Get-BundleToolJar $BundleToolJar
    $bundleToolJavaArgs = @(
        '-Dfile.encoding=UTF-8',
        '-Dsun.stdout.encoding=UTF-8',
        '-Dsun.stderr.encoding=UTF-8',
        '-jar',
        $script:BundleToolPath
    )

    if (-not $BuildOnly) {
        $script:CurrentBundleSmokeStage = 'resolveDevice'
        $script:AdbPath = Get-AndroidTool -Names @('adb.exe', 'adb') -RelativeDirs @('platform-tools')
        $script:ResolvedDeviceSerial = Resolve-DeviceSerial
        Write-Host "[info] device=$script:ResolvedDeviceSerial"
    }

    $script:CurrentBundleSmokeStage = 'resolveBundle'
    $script:ResolvedBundlePath =
        if ([string]::IsNullOrWhiteSpace($BundlePath)) {
            Get-DefaultBundle
        } else {
            (Resolve-Path -LiteralPath $BundlePath).Path
        }
    Warn-IfExplicitBundlePathIsNotVersioned $script:ResolvedBundlePath

    Write-Host "[info] verifying AAB signature: $script:ResolvedBundlePath"
    $script:CurrentBundleSmokeStage = 'bundleSignatureVerification'
    $signatureResult = Test-BundleSignature $script:ResolvedBundlePath
    if ($signatureResult.Verified) {
        $script:BundleSignatureStatus = 'verified'
        Write-Host '[ok] AAB signature verified'
    } elseif ($AllowDebugSigning) {
        $script:BundleSignatureStatus = 'failedAllowedForDebugSigning'
        Write-Warning "AAB signature verification failed. Continuing because -AllowDebugSigning was set; not release-ready: $script:ResolvedBundlePath`n$($signatureResult.Output)"
    } else {
        $script:BundleSignatureStatus = 'failed'
        throw "AAB signature verification failed: $script:ResolvedBundlePath`n$($signatureResult.Output)"
    }

    $script:WorkDir = Get-BundleSmokeArtifactDir
    New-Item -ItemType Directory -Force -Path $script:WorkDir | Out-Null
    Save-BundleSmokeRunMetadata `
        -RunStatus 'started' `
        -StartedAt $script:StartedAt `
        -CompletedAt '<not completed>'

    $buildApksArgs = @(
        'build-apks',
        "--bundle=$script:ResolvedBundlePath",
        '--overwrite'
    )

    if ($BuildOnly) {
        $buildApksArgs += '--mode=universal'
    } else {
        $buildApksArgs += '--connected-device'
        $buildApksArgs += "--device-id=$script:ResolvedDeviceSerial"
        $buildApksArgs += "--adb=$script:AdbPath"
    }

    $script:CurrentBundleSmokeStage = 'signingConfiguration'
    $buildApksArgs = Add-BundleToolSigningArgs -InputArgs $buildApksArgs
    $script:SigningLabel = if ($script:UsesDebugSigning) { 'debug-signing' } else { 'release-signing' }
    $script:ResolvedApksPath =
        if ([string]::IsNullOrWhiteSpace($ApksPath)) {
            Get-DefaultApksPath
        } else {
            $ApksPath
        }
    if (-not [string]::IsNullOrWhiteSpace($ApksPath)) {
        $apksFileName = [IO.Path]::GetFileName($script:ResolvedApksPath)
        if ($apksFileName -notlike "*$script:SigningLabel*") {
            Write-Warning "Custom ApksPath does not include signing label '$script:SigningLabel'. Keep the script output with this APK Set to avoid release-record ambiguity: $script:ResolvedApksPath"
        }
        if ((-not (Test-BundleSmokeReleaseReady)) -and $apksFileName -like 'AndroidVocabulary-release-v*.apks') {
            Write-Warning "Custom ApksPath looks release-named while this is a local-only bundle smoke output: $script:ResolvedApksPath"
        }
    }
    Warn-IfAmbiguousBundleSmokeArtifactsExist
    $buildApksArgs += "--output=$script:ResolvedApksPath"
    Write-Host "[info] APK Set signing=$script:SigningLabel"
    Write-Host "[info] bundletool build-apks: $script:ResolvedBundlePath"
    $script:CurrentBundleSmokeStage = 'bundletoolBuildApks'
    Invoke-BundleToolCommand 'bundletool build-apks' $buildApksArgs

    Write-Host "[ok] APK Set: $script:ResolvedApksPath"

    if ($BuildOnly) {
        if (Test-BundleSmokeReleaseReady) {
            Write-Host '[ok] bundle smoke build-only passed; APK Set signing=release-signing'
        } else {
            Write-Host "[ok] bundle smoke build-only passed; APK Set signing=$script:SigningLabel; not release-ready"
        }
        Save-BundleSmokeRunMetadata `
            -RunStatus 'completed' `
            -StartedAt $script:StartedAt `
            -CompletedAt (Get-Date -Format o)
        exit 0
    }

    $installArgs = @('install-apks', "--apks=$script:ResolvedApksPath")
    $installArgs += "--device-id=$script:ResolvedDeviceSerial"
    $installArgs += "--adb=$script:AdbPath"

    Write-Host '[info] bundletool install-apks'
    $script:CurrentBundleSmokeStage = 'bundletoolInstallApks'
    Invoke-BundleToolCommand 'bundletool install-apks' $installArgs

    $smokeArgs = @('-SkipInstall')
    $smokeArgs += @('-DeviceSerial', $script:ResolvedDeviceSerial)
    if ($ResetAppData) {
        $smokeArgs += '-ResetAppData'
    }
    if ($GrantNotificationPermission) {
        $smokeArgs += '-GrantNotificationPermission'
    }

    Write-Host '[info] verifying launch after bundletool install; APK signature verification and install are intentionally skipped'
    $script:CurrentBundleSmokeStage = 'installedAppLaunchSmoke'
    Invoke-PowerShellScriptCommand `
        'installed app launch smoke after bundle install' `
        (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'smoke-release-apk.ps1')) `
        $smokeArgs

    if (Test-BundleSmokeReleaseReady) {
        Write-Host '[ok] release AAB smoke test passed; APK Set signing=release-signing'
    } else {
        Write-Host "[ok] AAB smoke test passed; APK Set signing=$script:SigningLabel; not release-ready"
    }
    Save-BundleSmokeRunMetadata `
        -RunStatus 'completed' `
        -StartedAt $script:StartedAt `
        -CompletedAt (Get-Date -Format o)
} catch {
    try {
        $failureMessage = $_.Exception.Message
        $failureStage = Resolve-BundleSmokeFailureStage `
            -FailureMessage $failureMessage `
            -FallbackStage $script:CurrentBundleSmokeStage
        Save-BundleSmokeRunMetadata `
            -RunStatus 'failed' `
            -StartedAt $script:StartedAt `
            -CompletedAt (Get-Date -Format o) `
            -FailureMessage $failureMessage `
            -FailureStage $failureStage
    } catch {
        Write-Warning "Failed to save bundle smoke metadata: $($_.Exception.Message)"
    }
    throw
}
