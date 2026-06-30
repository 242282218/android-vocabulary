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
    $duplicateKeys = @()
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line -notmatch '=') {
            continue
        }
        $separatorIndex = $line.IndexOf('=')
        $key = $line.Substring(0, $separatorIndex)
        $value = $line.Substring($separatorIndex + 1)
        if ($values.ContainsKey($key)) {
            $duplicateKeys += $key
        }
        $values[$key] = $value
    }
    if ($duplicateKeys.Count -gt 0) {
        $values['__duplicateKeys'] = @($duplicateKeys | Select-Object -Unique) -join ','
    }
    return $values
}

function Get-MetadataDuplicateKeys {
    param([hashtable]$Metadata)

    if ($null -eq $Metadata -or -not $Metadata.ContainsKey('__duplicateKeys')) {
        return '<none>'
    }
    if ([string]::IsNullOrWhiteSpace($Metadata['__duplicateKeys'])) {
        return '<none>'
    }
    return $Metadata['__duplicateKeys']
}

function Test-MetadataHasUniqueKeys {
    param([hashtable]$Metadata)

    return $null -ne $Metadata -and -not $Metadata.ContainsKey('__duplicateKeys')
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

function Get-CurrentGitStatusEntries {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & git -C $repoRoot status --porcelain=v1 --untracked-files=all 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        $outputText = @($output | ConvertTo-AndroidVocabularyOutputText) -join "`n"
        throw "git status failed with exit code $exitCode. Output: $outputText"
    }
    return @(
        $output |
            ConvertTo-AndroidVocabularyOutputText |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Format-GitStatusSummary {
    param(
        [string[]]$Entries,
        [int]$MaxEntries = 20
    )

    if ($Entries.Count -eq 0) {
        return '<clean>'
    }

    $visibleEntries = @($Entries | Select-Object -First $MaxEntries)
    $summary = $visibleEntries -join '; '
    $hiddenCount = $Entries.Count - $visibleEntries.Count
    if ($hiddenCount -gt 0) {
        $summary += "; ... (+$hiddenCount more)"
    }
    return $summary
}

function Get-ReadinessRemediationText {
    param([string]$RequirementName)

    switch ($RequirementName) {
        'gitWorktreeClean' {
            return 'Commit or stash tracked changes and add/remove intentional untracked source files before final readiness.'
        }
        'releaseArtifacts' {
            return 'Build signed APK/AAB without -AllowUnsigned, then run scripts/test/verify-release-artifacts.ps1.'
        }
        'releaseApkSmoke' {
            return 'Run scripts/test/smoke-release-apk.ps1 against the current signed release APK and keep its metadata.'
        }
        'deviceAabVerification' {
            return 'Run scripts/test/verify-device.ps1 with the current release AAB and without local-only release overrides.'
        }
        'deviceBundleSmokeArchive' {
            return 'Keep smoke-release-bundle-run.txt beside verification-completed.txt in the device verification archive.'
        }
        'githubActionsEvidence' {
            return 'Download github-actions-release-evidence from a successful Android workflow run for the current HEAD.'
        }
        default {
            return 'Review the failed readiness requirement evidence and rerun the matching verification step.'
        }
    }
}

function Get-BlockerSummaryValue {
    param(
        [string[]]$Items
    )

    if ($null -eq $Items -or $Items.Count -eq 0) {
        return '<none>'
    }
    return $Items -join '; '
}

function Get-BlockerActionSummaryValue {
    param(
        [string[]]$Items
    )

    if ($null -eq $Items -or $Items.Count -eq 0) {
        return '<none>'
    }
    return $Items -join '; '
}

function Get-DependentRemediationText {
    param(
        [string]$Action,
        [string]$Prerequisite
    )

    if ([string]::IsNullOrWhiteSpace($Prerequisite)) {
        return $Action
    }
    return "after $Prerequisite, $Action"
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
        [string]$RunId,
        [string]$ServerUrl,
        [string]$Repository
    )

    if (
        [string]::IsNullOrWhiteSpace($RunUrl) -or
        [string]::IsNullOrWhiteSpace($RunId) -or
        [string]::IsNullOrWhiteSpace($ServerUrl) -or
        [string]::IsNullOrWhiteSpace($Repository)
    ) {
        return $false
    }

    $normalizedServerUrl = $ServerUrl.TrimEnd('/')
    $expectedRunUrl = "$normalizedServerUrl/$Repository/actions/runs/$RunId"
    return $RunUrl -eq $expectedRunUrl -and
        $Repository -match '^[^/\s]+/[^/\s]+$'
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
$gitStatusEntries = @(Get-CurrentGitStatusEntries)
$gitWorktreeDirtyCount = $gitStatusEntries.Count
$gitWorktreeStatus = if ($gitWorktreeDirtyCount -eq 0) { 'clean' } else { 'dirty' }
$gitWorktreeStatusSummary = Format-GitStatusSummary $gitStatusEntries
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
$releaseArtifactsDuplicateKeys = Get-MetadataDuplicateKeys $releaseArtifacts
$apkSmokeDuplicateKeys = Get-MetadataDuplicateKeys $apkSmoke
$deviceVerificationDuplicateKeys = Get-MetadataDuplicateKeys $deviceVerification
$githubActionsDuplicateKeys = Get-MetadataDuplicateKeys $githubActions
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
$deviceBundleSmokeArchiveDuplicateKeys = Get-MetadataDuplicateKeys $deviceBundleSmokeArchive
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

$gitWorktreeClean = $gitWorktreeDirtyCount -eq 0
$gitWorktreeRemediation =
    if ($gitWorktreeClean) {
        '<none>'
    } else {
        Get-ReadinessRemediationText 'gitWorktreeClean'
    }
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'gitWorktreeClean' `
    -Passed $gitWorktreeClean `
    -Evidence "dirtyCount=$gitWorktreeDirtyCount;entries=$gitWorktreeStatusSummary;remediation=$gitWorktreeRemediation"

$releaseArtifactsApkSha256 = Get-MetadataValueOrDefault $releaseArtifacts 'releaseApkSha256'
$releaseArtifactsBundleSha256 = Get-MetadataValueOrDefault $releaseArtifacts 'releaseBundleSha256'
$releaseArtifactsStaleCurrentVersionArtifacts =
    Get-MetadataValueOrDefault $releaseArtifacts 'staleCurrentVersionArtifacts'
$releaseArtifactsMetadataRemediation = Get-MetadataValueOrDefault $releaseArtifacts 'releaseArtifactsRemediation' '<missing>'
$releaseArtifactsReady =
    (Test-MetadataHasUniqueKeys $releaseArtifacts) -and
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
$releaseArtifactsRemediation =
    if ($releaseArtifactsReady) {
        '<none>'
    } elseif ($releaseArtifactsMetadataRemediation -ne '<missing>') {
        if (
            $releaseArtifactsStaleCurrentVersionArtifacts -notin @('<missing>', '<none>') -and
            $releaseArtifactsMetadataRemediation -notmatch [regex]::Escape($releaseArtifactsStaleCurrentVersionArtifacts)
        ) {
            "$releaseArtifactsMetadataRemediation Stale current-version release artifacts: $releaseArtifactsStaleCurrentVersionArtifacts."
        } else {
            $releaseArtifactsMetadataRemediation
        }
    } else {
        Get-ReadinessRemediationText 'releaseArtifacts'
    }
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'releaseArtifacts' `
    -Passed $releaseArtifactsReady `
    -Evidence "path=$releaseArtifactsPath;archive=$releaseArtifactsEvidenceArchive;archiveSha256=$releaseArtifactsEvidenceArchiveSha256;duplicateKeys=$releaseArtifactsDuplicateKeys;apkSha256=$releaseArtifactsApkSha256;bundleSha256=$releaseArtifactsBundleSha256;staleCurrentVersionArtifacts=$releaseArtifactsStaleCurrentVersionArtifacts;remediation=$releaseArtifactsRemediation"

$apkSmokeSha256 = Get-MetadataValueOrDefault $apkSmoke 'apkSha256'
$apkSmokeReady =
    (Test-MetadataHasUniqueKeys $apkSmoke) -and
    (Test-MetadataValue $apkSmoke 'runStatus' 'completed') -and
    (Test-MetadataValue $apkSmoke 'versionName' $versionName) -and
    (Test-MetadataValue $apkSmoke 'versionCode' $versionCode) -and
    (Test-MetadataValue $apkSmoke 'skipInstall' 'False') -and
    (Test-MetadataValue $apkSmoke 'apkPathMatchesCurrentVersionedName' 'True') -and
    (Test-Sha256String $apkSmokeSha256) -and
    $apkSmokeSha256 -eq $releaseArtifactsApkSha256
$apkSmokeRemediation =
    if ($apkSmokeReady) {
        '<none>'
    } else {
        Get-ReadinessRemediationText 'releaseApkSmoke'
    }
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'releaseApkSmoke' `
    -Passed $apkSmokeReady `
    -Evidence "path=$apkSmokePath;archive=$apkSmokeEvidenceArchive;archiveSha256=$apkSmokeEvidenceArchiveSha256;duplicateKeys=$apkSmokeDuplicateKeys;apkSha256=$apkSmokeSha256;remediation=$apkSmokeRemediation"

$deviceBundleSha256 = Get-MetadataValueOrDefault $deviceVerification 'bundleSha256'
$deviceBundleSmokeSha256 = Get-MetadataValueOrDefault $deviceVerification 'bundleSmokeBundleSha256'
$deviceBundleSmokeArchiveRunStatus = Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'runStatus'
$deviceBundleSmokeArchiveBundleSha256 = Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'bundleSha256'
$deviceBundleSmokeArchiveBundlePathMatchesCurrentVersionedName =
    Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'bundlePathMatchesCurrentVersionedName'
$deviceBundleSmokeArchiveReleaseReady = Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'bundleSmokeReleaseReady'
$deviceBundleSmokeArchiveApkSetSigning = Get-MetadataValueOrDefault $deviceBundleSmokeArchive 'apkSetSigning'
$deviceVerificationReady =
    (Test-MetadataHasUniqueKeys $deviceVerification) -and
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
    (Test-MetadataHasUniqueKeys $deviceBundleSmokeArchive) -and
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
$deviceVerificationRemediation =
    if ($deviceVerificationReady) {
        '<none>'
    } else {
        Get-ReadinessRemediationText 'deviceAabVerification'
    }
$deviceBundleSmokeArchiveRemediation =
    if ($deviceBundleSmokeArchiveReady) {
        '<none>'
    } else {
        Get-ReadinessRemediationText 'deviceBundleSmokeArchive'
    }
$deviceEvidence = if ([string]::IsNullOrWhiteSpace($deviceVerificationPath)) { '<missing>' } else { $deviceVerificationPath }
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'deviceAabVerification' `
    -Passed $deviceVerificationReady `
    -Evidence "path=$deviceEvidence;archive=$deviceVerificationEvidenceArchive;archiveSha256=$deviceVerificationEvidenceArchiveSha256;duplicateKeys=$deviceVerificationDuplicateKeys;bundleSha256=$deviceBundleSha256;bundleSmokeBundleSha256=$deviceBundleSmokeSha256;remediation=$deviceVerificationRemediation"
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'deviceBundleSmokeArchive' `
    -Passed $deviceBundleSmokeArchiveReady `
    -Evidence "path=$deviceBundleSmokeArchiveMetadata;archive=$deviceBundleSmokeEvidenceArchive;archiveSha256=$deviceBundleSmokeEvidenceArchiveSha256;duplicateKeys=$deviceBundleSmokeArchiveDuplicateKeys;bundleSmokeArchiveBundleSha256=$deviceBundleSmokeArchiveBundleSha256;remediation=$deviceBundleSmokeArchiveRemediation"

$githubEvidence = if ([string]::IsNullOrWhiteSpace($GitHubActionsEvidencePath)) { '<missing>' } else { $GitHubActionsEvidencePath }
$requiredGitHubChecks = Get-AndroidVocabularyRequiredGitHubActionsChecks
$githubWorkflow = Get-MetadataValueOrDefault $githubActions 'workflow'
$githubConclusion = Get-MetadataValueOrDefault $githubActions 'conclusion'
$githubCommitSha = Get-MetadataValueOrDefault $githubActions 'commitSha'
$githubServerUrl = Get-MetadataValueOrDefault $githubActions 'serverUrl'
$githubRepository = Get-MetadataValueOrDefault $githubActions 'repository'
$githubRunId = Get-MetadataValueOrDefault $githubActions 'runId'
$githubRunAttempt = Get-MetadataValueOrDefault $githubActions 'runAttempt'
$githubRunUrl = Get-MetadataValueOrDefault $githubActions 'runUrl'
$githubChecks = ConvertTo-ListValue (Get-MetadataValueOrDefault $githubActions 'checks' '')
$githubActionsReady =
    (Test-MetadataHasUniqueKeys $githubActions) -and
    $githubWorkflow -eq 'Android' -and
    $githubConclusion -eq 'success' -and
    $githubCommitSha -eq $currentCommitSha -and
    $githubServerUrl -eq 'https://github.com' -and
    (Test-PositiveIntegerString $githubRunId) -and
    (Test-PositiveIntegerString $githubRunAttempt) -and
    (Test-GitHubActionsRunUrl `
        -RunUrl $githubRunUrl `
        -RunId $githubRunId `
        -ServerUrl $githubServerUrl `
        -Repository $githubRepository) -and
    (Test-ContainsAllValues -Actual $githubChecks -Expected $requiredGitHubChecks)
$githubActionsRemediation =
    if ($githubActionsReady) {
        '<none>'
    } else {
        Get-ReadinessRemediationText 'githubActionsEvidence'
    }
$requirements = Add-RequirementResult `
    -Results $requirements `
    -Name 'githubActionsEvidence' `
    -Passed $githubActionsReady `
    -Evidence "path=$githubEvidence;archive=$githubActionsEvidenceArchive;archiveSha256=$githubActionsEvidenceArchiveSha256;duplicateKeys=$githubActionsDuplicateKeys;workflow=$githubWorkflow;conclusion=$githubConclusion;commitSha=$githubCommitSha;serverUrl=$githubServerUrl;repository=$githubRepository;runId=$githubRunId;runAttempt=$githubRunAttempt;remediation=$githubActionsRemediation"

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
$blockerSummaryItems = @()
if (-not $gitWorktreeClean) {
    $blockerSummaryItems += "git worktree dirty ($gitWorktreeDirtyCount entries)"
}
if (-not $releaseArtifactsReady) {
    if ($releaseArtifactsStaleCurrentVersionArtifacts -notin @('<missing>', '<none>')) {
        $blockerSummaryItems +=
            "release artifacts stale: $releaseArtifactsStaleCurrentVersionArtifacts"
    } else {
        $blockerSummaryItems += 'release artifacts missing, unsigned, or mismatched'
    }
}
if (-not $apkSmokeReady) {
    if ($apkSmokeSha256 -in @('<missing>', '<not used>')) {
        $blockerSummaryItems += 'release APK smoke evidence missing for current release APK'
    } else {
        $blockerSummaryItems += 'release APK smoke evidence does not match current release APK'
    }
}
if (-not $deviceVerificationReady) {
    if ($deviceEvidence -eq '<missing>') {
        $blockerSummaryItems += 'device AAB verification missing for current release bundle'
    } else {
        $blockerSummaryItems += 'device AAB verification does not match current release bundle'
    }
}
if (-not $deviceBundleSmokeArchiveReady) {
    if ($deviceBundleSmokeArchiveMetadata -eq '<missing>') {
        $blockerSummaryItems += 'device bundle smoke archive missing for current release bundle'
    } else {
        $blockerSummaryItems += 'device bundle smoke archive does not match current release bundle'
    }
}
if (-not $githubActionsReady) {
    if ($githubEvidence -eq '<missing>') {
        $blockerSummaryItems += 'GitHub Actions evidence missing for current HEAD'
    } else {
        $blockerSummaryItems += 'GitHub Actions evidence does not match current HEAD'
    }
}
$blockerSummary = Get-BlockerSummaryValue -Items $blockerSummaryItems
$blockerActionSummaryItems = @()
$releaseArtifactsPrerequisite =
    if ($releaseArtifactsReady) {
        $null
    } else {
        'release artifacts pass'
    }
$deviceVerificationPrerequisite =
    if ($deviceVerificationReady) {
        $null
    } else {
        'verify-device.ps1 passes'
    }
if (-not $gitWorktreeClean) {
    $blockerActionSummaryItems +=
        'clean or stash the git worktree before final readiness'
}
if (-not $releaseArtifactsReady) {
    if ($releaseArtifactsStaleCurrentVersionArtifacts -notin @('<missing>', '<none>')) {
        $blockerActionSummaryItems +=
            'replace stale release-named APK/AAB with a signed release build or archive them outside dist'
    } else {
        $blockerActionSummaryItems +=
            'build signed release APK/AAB and rerun verify-release-artifacts.ps1'
    }
}
if (-not $apkSmokeReady) {
    $blockerActionSummaryItems +=
        (Get-DependentRemediationText `
            -Action 'rerun smoke-release-apk.ps1 against the current signed release APK' `
            -Prerequisite $releaseArtifactsPrerequisite)
}
if (-not $deviceVerificationReady) {
    $blockerActionSummaryItems +=
        (Get-DependentRemediationText `
            -Action 'rerun verify-device.ps1 with the current release AAB and keep verification-completed.txt' `
            -Prerequisite $releaseArtifactsPrerequisite)
}
if (-not $deviceBundleSmokeArchiveReady) {
    $blockerActionSummaryItems +=
        (Get-DependentRemediationText `
            -Action 'keep smoke-release-bundle-run.txt beside the matching device verification archive' `
            -Prerequisite $deviceVerificationPrerequisite)
}
if (-not $githubActionsReady) {
    $blockerActionSummaryItems +=
        'download github-actions-release-evidence for the current HEAD'
}
$blockerActionSummary = Get-BlockerActionSummaryValue -Items $blockerActionSummaryItems
$metadata = @(
    "runStatus=started",
    "versionName=$versionName",
    "versionCode=$versionCode",
    "currentCommitSha=$currentCommitSha",
    "gitWorktreeStatus=$gitWorktreeStatus",
    "gitWorktreeDirtyCount=$gitWorktreeDirtyCount",
    "gitWorktreeDirtyEntries=$gitWorktreeStatusSummary",
    "gitWorktreeRemediation=$gitWorktreeRemediation",
    "failedRequirementNames=$failedRequirementNamesValue",
    "blockerSummary=$blockerSummary",
    "blockerActionSummary=$blockerActionSummary",
    "evidenceArchives=$evidenceArchives",
    "releaseArtifactsEvidenceMode=$releaseArtifactsEvidenceMode",
    "releaseArtifactsEvidenceInput=$releaseArtifactsEvidenceInput",
    "releaseArtifactsMetadata=$releaseArtifactsPath",
    "releaseArtifactsEvidenceArchive=$releaseArtifactsEvidenceArchive",
    "releaseArtifactsEvidenceArchiveSha256=$releaseArtifactsEvidenceArchiveSha256",
    "releaseArtifactsDuplicateKeys=$releaseArtifactsDuplicateKeys",
    "releaseArtifactsRemediation=$releaseArtifactsRemediation",
    "releaseArtifactsStaleCurrentVersionArtifacts=$releaseArtifactsStaleCurrentVersionArtifacts",
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
    "apkSmokeDuplicateKeys=$apkSmokeDuplicateKeys",
    "apkSmokeSha256=$apkSmokeSha256",
    "apkSmokeRemediation=$apkSmokeRemediation",
    "deviceVerificationEvidenceMode=$deviceVerificationEvidenceMode",
    "deviceVerificationEvidenceInput=$deviceVerificationEvidenceInput",
    "deviceVerificationMetadata=$deviceEvidence",
    "deviceVerificationEvidenceArchive=$deviceVerificationEvidenceArchive",
    "deviceVerificationEvidenceArchiveSha256=$deviceVerificationEvidenceArchiveSha256",
    "deviceVerificationDuplicateKeys=$deviceVerificationDuplicateKeys",
    "deviceBundleSmokeMetadata=$deviceBundleSmokeMetadata",
    "deviceBundleSmokeEvidenceArchive=$deviceBundleSmokeEvidenceArchive",
    "deviceBundleSmokeEvidenceArchiveSha256=$deviceBundleSmokeEvidenceArchiveSha256",
    "deviceBundleSmokeArchiveMetadata=$deviceBundleSmokeArchiveMetadata",
    "deviceBundleSmokeArchiveDuplicateKeys=$deviceBundleSmokeArchiveDuplicateKeys",
    "deviceBundleSmokeArchiveRunStatus=$deviceBundleSmokeArchiveRunStatus",
    "deviceBundleSmokeArchiveBundleSha256=$deviceBundleSmokeArchiveBundleSha256",
    "deviceBundleSmokeArchiveBundlePathMatchesCurrentVersionedName=$deviceBundleSmokeArchiveBundlePathMatchesCurrentVersionedName",
    "deviceBundleSmokeArchiveReleaseReady=$deviceBundleSmokeArchiveReleaseReady",
    "deviceBundleSmokeArchiveApkSetSigning=$deviceBundleSmokeArchiveApkSetSigning",
    "deviceBundleSha256=$deviceBundleSha256",
    "deviceBundleSmokeSha256=$deviceBundleSmokeSha256",
    "deviceVerificationRemediation=$deviceVerificationRemediation",
    "deviceBundleSmokeArchiveRemediation=$deviceBundleSmokeArchiveRemediation",
    "githubActionsEvidenceMetadata=$githubEvidence",
    "githubActionsEvidenceArchive=$githubActionsEvidenceArchive",
    "githubActionsEvidenceArchiveSha256=$githubActionsEvidenceArchiveSha256",
    "githubActionsDuplicateKeys=$githubActionsDuplicateKeys",
    "githubActionsWorkflow=$githubWorkflow",
    "githubActionsConclusion=$githubConclusion",
    "githubActionsCommitSha=$githubCommitSha",
    "githubActionsServerUrl=$githubServerUrl",
    "githubActionsRepository=$githubRepository",
    "githubActionsRunId=$githubRunId",
    "githubActionsRunAttempt=$githubRunAttempt",
    "githubActionsRunUrl=$githubRunUrl",
    "githubActionsChecks=$(if ($githubChecks.Count -eq 0) { '<missing>' } else { $githubChecks -join ', ' })",
    "githubActionsRemediation=$githubActionsRemediation",
    "githubActionsRequiredChecks=$($requiredGitHubChecks -join ', ')"
) + $requirements

if ($failedRequirements.Count -gt 0) {
    $metadata[0] = 'runStatus=failed'
    $metadata += "failureMessage=$($failedRequirements -join '; ')"
    Set-Content -LiteralPath $metadataPath -Value $metadata
    throw "Release readiness verification failed. See $metadataPath. Blockers: $blockerSummary. Actions: $blockerActionSummary. Failed requirements: $($failedRequirements -join '; ')"
}

$metadata[0] = 'runStatus=completed'
Set-Content -LiteralPath $metadataPath -Value $metadata
Write-Host "[ok] release readiness verified: $metadataPath"
