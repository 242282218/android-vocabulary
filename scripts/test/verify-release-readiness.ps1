param(
    [string]$GitHubActionsEvidencePath,
    [string]$ReleaseArtifactsEvidencePath,
    [string]$ApkSmokeEvidencePath,
    [string]$DeviceVerificationEvidencePath
)

$ErrorActionPreference = 'Stop'

. (Join-Path (Join-Path $PSScriptRoot '..') (Join-Path 'lib' 'android-env.ps1'))
$repoRoot = Get-AndroidVocabularyRepoRoot
$artifactDir = Join-AndroidVocabularyPath $repoRoot @('build', 'release-readiness')
$metadataPath = Join-Path $artifactDir 'release-readiness-run.txt'

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

function Add-RequirementResult {
    param(
        [string[]]$Results,
        [string]$Name,
        [bool]$Passed,
        [string]$Evidence
    )

    $status = if ($Passed) { 'passed' } else { 'failed' }
    return $Results + "$Name=$status|$Evidence"
}

function Test-MetadataValue {
    param(
        [hashtable]$Metadata,
        [string]$Key,
        [string]$Expected
    )

    return $null -ne $Metadata -and $Metadata.ContainsKey($Key) -and $Metadata[$Key] -eq $Expected
}

function Get-MetadataValueOrDefault {
    param(
        [hashtable]$Metadata,
        [string]$Key,
        [string]$Default = '<missing>'
    )

    if ($null -eq $Metadata -or -not $Metadata.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace($Metadata[$Key])) {
        return $Default
    }
    return $Metadata[$Key]
}

function Get-CurrentGitHeadSha {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & git -C $repoRoot rev-parse HEAD 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        $outputText = @($output | ConvertTo-AndroidVocabularyOutputText) -join "`n"
        throw "git rev-parse HEAD failed with exit code $exitCode. Output: $outputText"
    }
    return (@($output | ConvertTo-AndroidVocabularyOutputText) | Select-Object -First 1).Trim()
}

function ConvertTo-ListValue {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return @()
    }

    return @(
        $Value -split '[,;]' |
            ForEach-Object { $_.Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Test-ContainsAllValues {
    param(
        [string[]]$Actual,
        [string[]]$Expected
    )

    foreach ($item in $Expected) {
        if ($Actual -notcontains $item) {
            return $false
        }
    }
    return $true
}

function Test-PositiveIntegerString {
    param([string]$Value)

    return -not [string]::IsNullOrWhiteSpace($Value) -and $Value -match '^[1-9][0-9]*$'
}

function Test-GitHubActionsRunUrl {
    param(
        [string]$RunUrl,
        [string]$RunId
    )

    return -not [string]::IsNullOrWhiteSpace($RunUrl) -and
        -not [string]::IsNullOrWhiteSpace($RunId) -and
        $RunUrl -match "/actions/runs/$([regex]::Escape($RunId))/?$"
}

function Test-Sha256String {
    param([string]$Value)

    return -not [string]::IsNullOrWhiteSpace($Value) -and $Value -cmatch '^[a-f0-9]{64}$'
}

function Get-Sha256OrDefault {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return '<missing>'
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-LatestMatchingDeviceVerificationMetadataPath {
    param(
        [string]$VersionName,
        [string]$VersionCode,
        [string]$ReleaseBundleSha256
    )

    $deviceVerificationDir = Join-AndroidVocabularyPath $repoRoot @('build', 'device-verification')
    if (-not (Test-Path -LiteralPath $deviceVerificationDir)) {
        return $null
    }

    $completedFiles = @(
        Get-ChildItem `
            -Path $deviceVerificationDir `
            -Filter 'verification-completed.txt' `
            -Recurse `
            -File `
            -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    )
    foreach ($completedFile in $completedFiles) {
        $metadata = Read-KeyValueMetadata $completedFile.FullName
        if (
            (Test-MetadataValue $metadata 'runStatus' 'completed') -and
            (Test-MetadataValue $metadata 'versionName' $VersionName) -and
            (Test-MetadataValue $metadata 'versionCode' $VersionCode) -and
            (Test-MetadataValue $metadata 'bundleSha256' $ReleaseBundleSha256)
        ) {
            return $completedFile.FullName
        }
    }
    return $null
}

function Get-DeviceVerificationMetadataPath {
    param(
        [string]$VersionName,
        [string]$VersionCode,
        [string]$ReleaseBundleSha256
    )

    if ([string]::IsNullOrWhiteSpace($DeviceVerificationEvidencePath)) {
        return Get-LatestMatchingDeviceVerificationMetadataPath `
            -VersionName $VersionName `
            -VersionCode $VersionCode `
            -ReleaseBundleSha256 $ReleaseBundleSha256
    }

    try {
        return (Resolve-Path -LiteralPath $DeviceVerificationEvidencePath -ErrorAction Stop).Path
    } catch {
        return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($DeviceVerificationEvidencePath)
    }
}

function Get-ApkSmokeMetadataPath {
    if ([string]::IsNullOrWhiteSpace($ApkSmokeEvidencePath)) {
        return Join-AndroidVocabularyPath $repoRoot @('build', 'apk-smoke', 'smoke-release-apk-run.txt')
    }

    try {
        return (Resolve-Path -LiteralPath $ApkSmokeEvidencePath -ErrorAction Stop).Path
    } catch {
        return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ApkSmokeEvidencePath)
    }
}

function Get-ReleaseArtifactsMetadataPath {
    if ([string]::IsNullOrWhiteSpace($ReleaseArtifactsEvidencePath)) {
        return Join-AndroidVocabularyPath $repoRoot @('build', 'release-artifacts', 'release-artifacts-run.txt')
    }

    try {
        return (Resolve-Path -LiteralPath $ReleaseArtifactsEvidencePath -ErrorAction Stop).Path
    } catch {
        return $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ReleaseArtifactsEvidencePath)
    }
}

function Copy-ReadinessEvidenceArtifact {
    param(
        [string]$SourcePath,
        [string]$ArchiveFileName
    )

    $destinationPath = Join-Path $artifactDir $ArchiveFileName
    if ([string]::IsNullOrWhiteSpace($SourcePath) -or -not (Test-Path -LiteralPath $SourcePath)) {
        Remove-Item -LiteralPath $destinationPath -Force -ErrorAction SilentlyContinue
        return '<missing>'
    }

    $resolvedSourcePath = (Resolve-Path -LiteralPath $SourcePath).Path
    $resolvedDestinationPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($destinationPath)
    if ($resolvedSourcePath -eq $resolvedDestinationPath) {
        return $ArchiveFileName
    }

    Copy-Item `
        -LiteralPath $resolvedSourcePath `
        -Destination $destinationPath `
        -Force
    return $ArchiveFileName
}

function Get-ReadinessEvidenceArchiveSha256 {
    param([string]$ArchiveFileName)

    if ([string]::IsNullOrWhiteSpace($ArchiveFileName) -or $ArchiveFileName -eq '<missing>') {
        return '<missing>'
    }
    return Get-Sha256OrDefault (Join-Path $artifactDir $ArchiveFileName)
}

function Get-ReadinessEvidenceArchivePath {
    param([string]$ArchiveFileName)

    if ([string]::IsNullOrWhiteSpace($ArchiveFileName) -or $ArchiveFileName -eq '<missing>') {
        return $null
    }
    return Join-Path $artifactDir $ArchiveFileName
}

function Format-ReadinessEvidenceArchiveSummary {
    param(
        [string]$ReleaseArtifactsArchive,
        [string]$ReleaseArtifactsArchiveSha256,
        [string]$ApkSmokeArchive,
        [string]$ApkSmokeArchiveSha256,
        [string]$DeviceVerificationArchive,
        [string]$DeviceVerificationArchiveSha256,
        [string]$DeviceBundleSmokeArchive,
        [string]$DeviceBundleSmokeArchiveSha256,
        [string]$GitHubActionsArchive,
        [string]$GitHubActionsArchiveSha256
    )

    return @(
        "releaseArtifacts:${ReleaseArtifactsArchive}:${ReleaseArtifactsArchiveSha256}",
        "apkSmoke:${ApkSmokeArchive}:${ApkSmokeArchiveSha256}",
        "deviceVerification:${DeviceVerificationArchive}:${DeviceVerificationArchiveSha256}",
        "deviceBundleSmoke:${DeviceBundleSmokeArchive}:${DeviceBundleSmokeArchiveSha256}",
        "githubActions:${GitHubActionsArchive}:${GitHubActionsArchiveSha256}"
    ) -join ';'
}

function Get-DeviceBundleSmokeMetadataPath {
    param(
        [string]$DeviceVerificationMetadataPath,
        [hashtable]$DeviceVerificationMetadata
    )

    $bundleSmokeMetadata = Get-MetadataValueOrDefault $DeviceVerificationMetadata 'bundleSmokeRunMetadata'
    if (
        [string]::IsNullOrWhiteSpace($bundleSmokeMetadata) -or
        $bundleSmokeMetadata -in @('<missing>', '<none>', '<skipped>', '<not used>')
    ) {
        return $null
    }
    if ([IO.Path]::IsPathRooted($bundleSmokeMetadata)) {
        return $bundleSmokeMetadata
    }
    if ([string]::IsNullOrWhiteSpace($DeviceVerificationMetadataPath)) {
        return $null
    }

    $deviceVerificationDir = Split-Path -Parent $DeviceVerificationMetadataPath
    if ([string]::IsNullOrWhiteSpace($deviceVerificationDir)) {
        return $null
    }
    return Join-Path $deviceVerificationDir $bundleSmokeMetadata
}

Use-AndroidVocabularyJavaHome
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

$versionName = Get-GradlePropertyValue 'androidVocab.versionName'
$versionCode = Get-GradlePropertyValue 'androidVocab.versionCode'
$currentCommitSha = Get-CurrentGitHeadSha
$distDir = Join-Path $repoRoot 'dist'
$releaseApkPath = Join-Path $distDir (Get-AndroidVocabularyReleaseApkName)
$releaseBundlePath = Join-Path $distDir (Get-AndroidVocabularyReleaseBundleName)
$currentReleaseApkSha256 = Get-Sha256OrDefault $releaseApkPath
$currentReleaseBundleSha256 = Get-Sha256OrDefault $releaseBundlePath
$releaseArtifactsPath = Get-ReleaseArtifactsMetadataPath
$releaseArtifactsEvidenceMode =
    if ([string]::IsNullOrWhiteSpace($ReleaseArtifactsEvidencePath)) {
        'default'
    } else {
        'explicit'
    }
$releaseArtifactsEvidenceInput =
    if ([string]::IsNullOrWhiteSpace($ReleaseArtifactsEvidencePath)) {
        '<default>'
    } else {
        $ReleaseArtifactsEvidencePath
    }
$apkSmokePath = Get-ApkSmokeMetadataPath
$apkSmokeEvidenceMode =
    if ([string]::IsNullOrWhiteSpace($ApkSmokeEvidencePath)) {
        'default'
    } else {
        'explicit'
    }
$apkSmokeEvidenceInput =
    if ([string]::IsNullOrWhiteSpace($ApkSmokeEvidencePath)) {
        '<default>'
    } else {
        $ApkSmokeEvidencePath
    }
$deviceVerificationPath = Get-DeviceVerificationMetadataPath `
    -VersionName $versionName `
    -VersionCode $versionCode `
    -ReleaseBundleSha256 $currentReleaseBundleSha256
$deviceVerificationEvidenceMode =
    if ([string]::IsNullOrWhiteSpace($DeviceVerificationEvidencePath)) {
        'latestMatching'
    } else {
        'explicit'
    }
$deviceVerificationEvidenceInput =
    if ([string]::IsNullOrWhiteSpace($DeviceVerificationEvidencePath)) {
        '<latestMatching>'
    } else {
        $DeviceVerificationEvidencePath
    }

$releaseArtifacts = Read-KeyValueMetadata $releaseArtifactsPath
$apkSmoke = Read-KeyValueMetadata $apkSmokePath
$deviceVerification = Read-KeyValueMetadata $deviceVerificationPath
$githubActions = Read-KeyValueMetadata $GitHubActionsEvidencePath
$deviceBundleSmokeMetadataPath = Get-DeviceBundleSmokeMetadataPath `
    -DeviceVerificationMetadataPath $deviceVerificationPath `
    -DeviceVerificationMetadata $deviceVerification
$deviceBundleSmokeMetadata =
    if ([string]::IsNullOrWhiteSpace($deviceBundleSmokeMetadataPath)) {
        '<missing>'
    } else {
        $deviceBundleSmokeMetadataPath
    }

$releaseArtifactsEvidenceArchive = Copy-ReadinessEvidenceArtifact `
    -SourcePath $releaseArtifactsPath `
    -ArchiveFileName 'evidence-release-artifacts-run.txt'
$apkSmokeEvidenceArchive = Copy-ReadinessEvidenceArtifact `
    -SourcePath $apkSmokePath `
    -ArchiveFileName 'evidence-smoke-release-apk-run.txt'
$deviceVerificationEvidenceArchive = Copy-ReadinessEvidenceArtifact `
    -SourcePath $deviceVerificationPath `
    -ArchiveFileName 'evidence-verification-completed.txt'
$deviceBundleSmokeEvidenceArchive = Copy-ReadinessEvidenceArtifact `
    -SourcePath $deviceBundleSmokeMetadataPath `
    -ArchiveFileName 'evidence-smoke-release-bundle-run.txt'
$githubActionsEvidenceArchive = Copy-ReadinessEvidenceArtifact `
    -SourcePath $GitHubActionsEvidencePath `
    -ArchiveFileName 'evidence-github-actions.txt'
$releaseArtifactsEvidenceArchiveSha256 = Get-ReadinessEvidenceArchiveSha256 $releaseArtifactsEvidenceArchive
$apkSmokeEvidenceArchiveSha256 = Get-ReadinessEvidenceArchiveSha256 $apkSmokeEvidenceArchive
$deviceVerificationEvidenceArchiveSha256 = Get-ReadinessEvidenceArchiveSha256 $deviceVerificationEvidenceArchive
$deviceBundleSmokeEvidenceArchiveSha256 = Get-ReadinessEvidenceArchiveSha256 $deviceBundleSmokeEvidenceArchive
$githubActionsEvidenceArchiveSha256 = Get-ReadinessEvidenceArchiveSha256 $githubActionsEvidenceArchive
$deviceBundleSmokeArchivePath = Get-ReadinessEvidenceArchivePath $deviceBundleSmokeEvidenceArchive
$deviceBundleSmokeArchiveMetadata =
    if ([string]::IsNullOrWhiteSpace($deviceBundleSmokeArchivePath)) {
        '<missing>'
    } else {
        $deviceBundleSmokeArchivePath
    }
$deviceBundleSmokeArchive = Read-KeyValueMetadata $deviceBundleSmokeArchivePath
$evidenceArchives = Format-ReadinessEvidenceArchiveSummary `
    -ReleaseArtifactsArchive $releaseArtifactsEvidenceArchive `
    -ReleaseArtifactsArchiveSha256 $releaseArtifactsEvidenceArchiveSha256 `
    -ApkSmokeArchive $apkSmokeEvidenceArchive `
    -ApkSmokeArchiveSha256 $apkSmokeEvidenceArchiveSha256 `
    -DeviceVerificationArchive $deviceVerificationEvidenceArchive `
    -DeviceVerificationArchiveSha256 $deviceVerificationEvidenceArchiveSha256 `
    -DeviceBundleSmokeArchive $deviceBundleSmokeEvidenceArchive `
    -DeviceBundleSmokeArchiveSha256 $deviceBundleSmokeEvidenceArchiveSha256 `
    -GitHubActionsArchive $githubActionsEvidenceArchive `
    -GitHubActionsArchiveSha256 $githubActionsEvidenceArchiveSha256

$requirements = @()

$releaseArtifactsApkSha256 = Get-MetadataValueOrDefault $releaseArtifacts 'releaseApkSha256'
$releaseArtifactsBundleSha256 = Get-MetadataValueOrDefault $releaseArtifacts 'releaseBundleSha256'
$releaseArtifactsReady =
    (Test-MetadataValue $releaseArtifacts 'runStatus' 'completed') -and
    (Test-MetadataValue $releaseArtifacts 'versionName' $versionName) -and
    (Test-MetadataValue $releaseArtifacts 'versionCode' $versionCode) -and
    (Test-MetadataValue $releaseArtifacts 'releaseApk' $releaseApkPath) -and
    (Test-MetadataValue $releaseArtifacts 'releaseApkStatus' 'verified') -and
    (Test-Sha256String $releaseArtifactsApkSha256) -and
    $releaseArtifactsApkSha256 -eq $currentReleaseApkSha256 -and
    (Test-MetadataValue $releaseArtifacts 'releaseBundle' $releaseBundlePath) -and
    (Test-MetadataValue $releaseArtifacts 'releaseBundleStatus' 'verified') -and
    (Test-Sha256String $releaseArtifactsBundleSha256) -and
    $releaseArtifactsBundleSha256 -eq $currentReleaseBundleSha256
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'releaseArtifacts' `
    -Passed $releaseArtifactsReady `
    -Evidence "path=$releaseArtifactsPath;archive=$releaseArtifactsEvidenceArchive;archiveSha256=$releaseArtifactsEvidenceArchiveSha256;apkSha256=$releaseArtifactsApkSha256;bundleSha256=$releaseArtifactsBundleSha256"

$apkSmokeSha256 = Get-MetadataValueOrDefault $apkSmoke 'apkSha256'
$apkSmokeReady =
    (Test-MetadataValue $apkSmoke 'runStatus' 'completed') -and
    (Test-MetadataValue $apkSmoke 'versionName' $versionName) -and
    (Test-MetadataValue $apkSmoke 'versionCode' $versionCode) -and
    (Test-MetadataValue $apkSmoke 'skipInstall' 'False') -and
    (Test-MetadataValue $apkSmoke 'apkPathMatchesCurrentVersionedName' 'True') -and
    (Test-Sha256String $apkSmokeSha256) -and
    $apkSmokeSha256 -eq $releaseArtifactsApkSha256
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'releaseApkSmoke' `
    -Passed $apkSmokeReady `
    -Evidence "path=$apkSmokePath;archive=$apkSmokeEvidenceArchive;archiveSha256=$apkSmokeEvidenceArchiveSha256;apkSha256=$apkSmokeSha256"

$deviceBundleSha256 = Get-MetadataValueOrDefault $deviceVerification 'bundleSha256'
$deviceBundleSmokeSha256 = Get-MetadataValueOrDefault $deviceVerification 'bundleSmokeBundleSha256'
$deviceBundleSmokeArchiveRunStatus = Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'runStatus'
$deviceBundleSmokeArchiveBundleSha256 = Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'bundleSha256'
$deviceBundleSmokeArchiveBundlePathMatchesCurrentVersionedName =
    Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'bundlePathMatchesCurrentVersionedName'
$deviceBundleSmokeArchiveReleaseReady = Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'bundleSmokeReleaseReady'
$deviceBundleSmokeArchiveApkSetSigning = Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'apkSetSigning'
$deviceVerificationReady =
    (Test-MetadataValue $deviceVerification 'runStatus' 'completed') -and
    (Test-MetadataValue $deviceVerification 'versionName' $versionName) -and
    (Test-MetadataValue $deviceVerification 'versionCode' $versionCode) -and
    (Test-MetadataValue $deviceVerification 'usesLocalOnlyReleaseOverrides' 'False') -and
    (Test-MetadataValue $deviceVerification 'bundlePathMatchesCurrentVersionedName' 'True') -and
    (Test-MetadataValue $deviceVerification 'bundleSmokeRunMetadata' 'smoke-release-bundle-run.txt') -and
    (Test-MetadataValue $deviceVerification 'bundleSmokeRunStatus' 'completed') -and
    (Test-MetadataValue $deviceVerification 'bundleSmokeBundlePathMatchesCurrentVersionedName' 'True') -and
    (Test-MetadataValue $deviceVerification 'bundleSmokeReleaseReady' 'True') -and
    (Test-MetadataValue $deviceVerification 'bundleSmokeApkSetSigning' 'release-signing') -and
    (Test-Sha256String $deviceBundleSha256) -and
    $deviceBundleSha256 -eq $releaseArtifactsBundleSha256 -and
    (Test-Sha256String $deviceBundleSmokeSha256) -and
    $deviceBundleSmokeSha256 -eq $releaseArtifactsBundleSha256 -and
    $deviceBundleSmokeSha256 -eq $deviceBundleSha256
$deviceBundleSmokeArchiveReady =
    (Test-MetadataValue $deviceBundleSmokeArchive 'runStatus' 'completed') -and
    (Test-MetadataValue $deviceBundleSmokeArchive 'versionName' $versionName) -and
    (Test-MetadataValue $deviceBundleSmokeArchive 'versionCode' $versionCode) -and
    (Test-MetadataValue $deviceBundleSmokeArchive 'bundlePathMatchesCurrentVersionedName' 'True') -and
    (Test-MetadataValue $deviceBundleSmokeArchive 'bundleSmokeReleaseReady' 'True') -and
    (Test-MetadataValue $deviceBundleSmokeArchive 'apkSetSigning' 'release-signing') -and
    (Test-Sha256String $deviceBundleSmokeArchiveBundleSha256) -and
    $deviceBundleSmokeArchiveBundleSha256 -eq $releaseArtifactsBundleSha256 -and
    $deviceBundleSmokeArchiveBundleSha256 -eq $deviceBundleSha256 -and
    $deviceBundleSmokeArchiveBundleSha256 -eq $deviceBundleSmokeSha256
$deviceEvidence = if ([string]::IsNullOrWhiteSpace($deviceVerificationPath)) { '<missing>' } else { $deviceVerificationPath }
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'deviceAabVerification' `
    -Passed $deviceVerificationReady `
    -Evidence "path=$deviceEvidence;archive=$deviceVerificationEvidenceArchive;archiveSha256=$deviceVerificationEvidenceArchiveSha256;bundleSha256=$deviceBundleSha256;bundleSmokeBundleSha256=$deviceBundleSmokeSha256"
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'deviceBundleSmokeArchive' `
    -Passed $deviceBundleSmokeArchiveReady `
    -Evidence "path=$deviceBundleSmokeArchiveMetadata;archive=$deviceBundleSmokeEvidenceArchive;archiveSha256=$deviceBundleSmokeEvidenceArchiveSha256;bundleSmokeArchiveBundleSha256=$deviceBundleSmokeArchiveBundleSha256"

$githubEvidence = if ([string]::IsNullOrWhiteSpace($GitHubActionsEvidencePath)) { '<missing>' } else { $GitHubActionsEvidencePath }
$requiredGitHubChecks = Get-AndroidVocabularyRequiredGitHubActionsChecks
$githubWorkflow = Get-MetadataValueOrDefault $githubActions 'workflow'
$githubConclusion = Get-MetadataValueOrDefault $githubActions 'conclusion'
$githubCommitSha = Get-MetadataValueOrDefault $githubActions 'commitSha'
$githubRunId = Get-MetadataValueOrDefault $githubActions 'runId'
$githubRunAttempt = Get-MetadataValueOrDefault $githubActions 'runAttempt'
$githubRunUrl = Get-MetadataValueOrDefault $githubActions 'runUrl'
$githubChecks = ConvertTo-ListValue (Get-MetadataValueOrDefault $githubActions 'checks' '')
$githubActionsReady =
    $null -ne $githubActions -and
    $githubWorkflow -eq 'Android' -and
    $githubConclusion -eq 'success' -and
    $githubCommitSha -eq $currentCommitSha -and
    (Test-PositiveIntegerString $githubRunId) -and
    (Test-PositiveIntegerString $githubRunAttempt) -and
    (Test-GitHubActionsRunUrl -RunUrl $githubRunUrl -RunId $githubRunId) -and
    (Test-ContainsAllValues -Actual $githubChecks -Expected $requiredGitHubChecks)
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'githubActionsEvidence' `
    -Passed $githubActionsReady `
    -Evidence "path=$githubEvidence;archive=$githubActionsEvidenceArchive;archiveSha256=$githubActionsEvidenceArchiveSha256;workflow=$githubWorkflow;conclusion=$githubConclusion;commitSha=$githubCommitSha;runId=$githubRunId;runAttempt=$githubRunAttempt"

$failedRequirements = @($requirements | Where-Object { $_ -match '=failed\|' })
$failedRequirementNames = @(
    $failedRequirements |
        ForEach-Object { $_.Substring(0, $_.IndexOf('=')) }
)
$failedRequirementNamesValue =
    if ($failedRequirementNames.Count -eq 0) {
        '<none>'
    } else {
        $failedRequirementNames -join ', '
    }
$metadata = @(
    "runStatus=started",
    "versionName=$versionName",
    "versionCode=$versionCode",
    "currentCommitSha=$currentCommitSha",
    "failedRequirementNames=$failedRequirementNamesValue",
    "evidenceArchives=$evidenceArchives",
    "releaseArtifactsEvidenceMode=$releaseArtifactsEvidenceMode",
    "releaseArtifactsEvidenceInput=$releaseArtifactsEvidenceInput",
    "releaseArtifactsMetadata=$releaseArtifactsPath",
    "releaseArtifactsEvidenceArchive=$releaseArtifactsEvidenceArchive",
    "releaseArtifactsEvidenceArchiveSha256=$releaseArtifactsEvidenceArchiveSha256",
    "releaseApk=$releaseApkPath",
    "releaseApkSha256Metadata=$releaseArtifactsApkSha256",
    "releaseApkSha256Current=$currentReleaseApkSha256",
    "releaseBundle=$releaseBundlePath",
    "releaseBundleSha256Metadata=$releaseArtifactsBundleSha256",
    "releaseBundleSha256Current=$currentReleaseBundleSha256",
    "apkSmokeEvidenceMode=$apkSmokeEvidenceMode",
    "apkSmokeEvidenceInput=$apkSmokeEvidenceInput",
    "apkSmokeMetadata=$apkSmokePath",
    "apkSmokeEvidenceArchive=$apkSmokeEvidenceArchive",
    "apkSmokeEvidenceArchiveSha256=$apkSmokeEvidenceArchiveSha256",
    "apkSmokeSha256=$apkSmokeSha256",
    "deviceVerificationEvidenceMode=$deviceVerificationEvidenceMode",
    "deviceVerificationEvidenceInput=$deviceVerificationEvidenceInput",
    "deviceVerificationMetadata=$deviceEvidence",
    "deviceVerificationEvidenceArchive=$deviceVerificationEvidenceArchive",
    "deviceVerificationEvidenceArchiveSha256=$deviceVerificationEvidenceArchiveSha256",
    "deviceBundleSmokeMetadata=$deviceBundleSmokeMetadata",
    "deviceBundleSmokeEvidenceArchive=$deviceBundleSmokeEvidenceArchive",
    "deviceBundleSmokeEvidenceArchiveSha256=$deviceBundleSmokeEvidenceArchiveSha256",
    "deviceBundleSmokeArchiveMetadata=$deviceBundleSmokeArchiveMetadata",
    "deviceBundleSmokeArchiveRunStatus=$deviceBundleSmokeArchiveRunStatus",
    "deviceBundleSmokeArchiveBundleSha256=$deviceBundleSmokeArchiveBundleSha256",
    "deviceBundleSmokeArchiveBundlePathMatchesCurrentVersionedName=$deviceBundleSmokeArchiveBundlePathMatchesCurrentVersionedName",
    "deviceBundleSmokeArchiveReleaseReady=$deviceBundleSmokeArchiveReleaseReady",
    "deviceBundleSmokeArchiveApkSetSigning=$deviceBundleSmokeArchiveApkSetSigning",
    "deviceBundleSha256=$deviceBundleSha256",
    "deviceBundleSmokeSha256=$deviceBundleSmokeSha256",
    "githubActionsEvidenceMetadata=$githubEvidence",
    "githubActionsEvidenceArchive=$githubActionsEvidenceArchive",
    "githubActionsEvidenceArchiveSha256=$githubActionsEvidenceArchiveSha256",
    "githubActionsWorkflow=$githubWorkflow",
    "githubActionsConclusion=$githubConclusion",
    "githubActionsCommitSha=$githubCommitSha",
    "githubActionsRunId=$githubRunId",
    "githubActionsRunAttempt=$githubRunAttempt",
    "githubActionsRunUrl=$githubRunUrl",
    "githubActionsChecks=$(if ($githubChecks.Count -eq 0) { '<missing>' } else { $githubChecks -join ', ' })",
    "githubActionsRequiredChecks=$($requiredGitHubChecks -join ', ')"
) + $requirements

if ($failedRequirements.Count -gt 0) {
    $metadata[0] = 'runStatus=failed'
    $metadata += "failureMessage=$($failedRequirements -join '; ')"
    Set-Content -LiteralPath $metadataPath -Value $metadata
    throw "Release readiness verification failed. See $metadataPath. Failed requirements: $($failedRequirements -join '; ')"
}

$metadata[0] = 'runStatus=completed'
Set-Content -LiteralPath $metadataPath -Value $metadata
Write-Host "[ok] release readiness verified: $metadataPath"
