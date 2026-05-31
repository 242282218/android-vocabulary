param(
    [switch]$AllowMissingReleaseArtifacts
)

$ErrorActionPreference = 'Stop'

. (Join-Path (Join-Path $PSScriptRoot '..') (Join-Path 'lib' 'android-env.ps1'))
$repoRoot = Get-AndroidVocabularyRepoRoot
$distDir = Join-Path $repoRoot 'dist'
$artifactDir = Join-AndroidVocabularyPath $repoRoot @('build', 'release-artifacts')
$metadataPath = Join-Path $artifactDir 'release-artifacts-run.txt'

function Invoke-ReleaseArtifactApkSignatureCheck {
    param([string]$Path)

    $apkSigner = Get-AndroidTool -Names @('apksigner.bat', 'apksigner') -RelativeDirs @('build-tools')
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $apkSigner verify --verbose $Path 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [PSCustomObject]@{
        Verified = ($exitCode -eq 0)
        Output = ((@($output | ConvertTo-AndroidVocabularyOutputText) -join "`n").Trim())
    }
}

function Invoke-ReleaseArtifactAabSignatureCheck {
    param([string]$Path)

    $jarSigner = Get-JarSigner
    $jarSignerArgs = @(
        '-J-Dfile.encoding=UTF-8',
        '-J-Dsun.stdout.encoding=UTF-8',
        '-J-Dsun.stderr.encoding=UTF-8',
        '-J-Duser.language=en',
        '-J-Duser.country=US',
        '-verify',
        '-certs',
        $Path
    )
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $jarSigner @jarSignerArgs 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $outputText = @($output | ConvertTo-AndroidVocabularyOutputText) -join "`n"
    $verified = (
        $exitCode -eq 0 -and
        $outputText -match '(?i)\bjar\s+verified\b' -and
        $outputText -notmatch '(?i)jar\s+(is\s+)?unsigned' -and
        $outputText -notmatch '(?i)no\s+manifest'
    )

    return [PSCustomObject]@{
        Verified = $verified
        Output = $outputText.Trim()
    }
}

function Get-ReleaseArtifactStatusText {
    param(
        [string]$Path,
        [scriptblock]$CheckSignature
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return [PSCustomObject]@{
            Status = 'missing'
            SignatureOutput = '<not checked>'
        }
    }

    $signatureResult = & $CheckSignature $Path
    return [PSCustomObject]@{
        Status = if ($signatureResult.Verified) { 'verified' } else { 'failed' }
        SignatureOutput = if ([string]::IsNullOrWhiteSpace($signatureResult.Output)) { '<no output>' } else { $signatureResult.Output }
    }
}

function Get-ReleaseArtifactNames {
    param([string]$Filter)

    if (-not (Test-Path -LiteralPath $distDir)) {
        return @()
    }

    return @(
        Get-ChildItem -Path $distDir -Filter $Filter -File -ErrorAction SilentlyContinue |
            Sort-Object Name |
            ForEach-Object { $_.Name }
    )
}

function Get-ReleaseArtifactSha256 {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return '<missing>'
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-ReleaseArtifactLastWriteTimeValue {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return '<missing>'
    }
    return (Get-Item -LiteralPath $Path).LastWriteTime.ToString('o')
}

function Test-ReleaseArtifactOlderThan {
    param(
        [string]$Path,
        [string]$ReferencePath
    )

    if (-not (Test-Path -LiteralPath $Path) -or -not (Test-Path -LiteralPath $ReferencePath)) {
        return '<unknown>'
    }

    return (((Get-Item -LiteralPath $Path).LastWriteTimeUtc -lt (Get-Item -LiteralPath $ReferencePath).LastWriteTimeUtc).ToString())
}

function Get-ReleaseArtifactRemediationText {
    param([string[]]$StaleCurrentVersionArtifacts)

    $base = (
        'Configure release signing, run scripts/release/build-release.ps1 and ' +
        'scripts/release/build-bundle.ps1 without -AllowUnsigned, then rerun this audit. ' +
        'If stale release-named dist artifacts are intentional evidence, archive them outside dist before final audit. ' +
        'Build-validation artifacts are local-only and not publishable.'
    )

    if ($null -eq $StaleCurrentVersionArtifacts -or $StaleCurrentVersionArtifacts.Count -eq 0) {
        return $base
    }

    return (
        "$base Current-version release-named artifacts are older than the matching build-validation outputs: " +
        "$($StaleCurrentVersionArtifacts -join ', '). Replace them with a signed release build or archive them outside dist."
    )
}

Use-AndroidVocabularyJavaHome
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

$versionName = Get-GradlePropertyValue 'androidVocab.versionName'
$versionCode = Get-GradlePropertyValue 'androidVocab.versionCode'
$releaseApkName = Get-AndroidVocabularyReleaseApkName
$releaseBundleName = Get-AndroidVocabularyReleaseBundleName
$buildValidationApkName = Get-AndroidVocabularyBuildValidationApkName
$buildValidationBundleName = Get-AndroidVocabularyBuildValidationBundleName
$releaseApkPath = Join-Path $distDir $releaseApkName
$releaseBundlePath = Join-Path $distDir $releaseBundleName
$buildValidationApkPath = Join-Path $distDir $buildValidationApkName
$buildValidationBundlePath = Join-Path $distDir $buildValidationBundleName

$apkStatus = Get-ReleaseArtifactStatusText `
    -Path $releaseApkPath `
    -CheckSignature { param($path) Invoke-ReleaseArtifactApkSignatureCheck $path }
$bundleStatus = Get-ReleaseArtifactStatusText `
    -Path $releaseBundlePath `
    -CheckSignature { param($path) Invoke-ReleaseArtifactAabSignatureCheck $path }
$releaseApkSha256 = Get-ReleaseArtifactSha256 $releaseApkPath
$releaseBundleSha256 = Get-ReleaseArtifactSha256 $releaseBundlePath
$buildValidationApkSha256 = Get-ReleaseArtifactSha256 $buildValidationApkPath
$buildValidationBundleSha256 = Get-ReleaseArtifactSha256 $buildValidationBundlePath
$releaseApkLastWriteTime = Get-ReleaseArtifactLastWriteTimeValue $releaseApkPath
$releaseBundleLastWriteTime = Get-ReleaseArtifactLastWriteTimeValue $releaseBundlePath
$buildValidationApkLastWriteTime = Get-ReleaseArtifactLastWriteTimeValue $buildValidationApkPath
$buildValidationBundleLastWriteTime = Get-ReleaseArtifactLastWriteTimeValue $buildValidationBundlePath
$releaseApkOlderThanBuildValidation = Test-ReleaseArtifactOlderThan -Path $releaseApkPath -ReferencePath $buildValidationApkPath
$releaseBundleOlderThanBuildValidation = Test-ReleaseArtifactOlderThan -Path $releaseBundlePath -ReferencePath $buildValidationBundlePath

$allDistApks = Get-ReleaseArtifactNames '*.apk'
$allDistBundles = Get-ReleaseArtifactNames '*.aab'
$legacyDistArtifacts = @(
    $allDistBundles |
        Where-Object { $_ -eq 'app-release.aab' -or ($_ -like 'AndroidVocabulary-release-v*.aab' -and $_ -ne $releaseBundleName) }
    $allDistApks |
        Where-Object { $_ -like 'AndroidVocabulary-release-v*.apk' -and $_ -ne $releaseApkName }
) | Sort-Object -Unique
$staleCurrentVersionArtifacts = @()
if ($releaseApkOlderThanBuildValidation -eq 'True') {
    $staleCurrentVersionArtifacts += $releaseApkName
}
if ($releaseBundleOlderThanBuildValidation -eq 'True') {
    $staleCurrentVersionArtifacts += $releaseBundleName
}
$staleCurrentVersionArtifacts = @($staleCurrentVersionArtifacts | Sort-Object -Unique)

$metadata = @(
    "runStatus=started",
    "versionName=$versionName",
    "versionCode=$versionCode",
    "distDir=$distDir",
    "releaseApk=$releaseApkPath",
    "releaseApkStatus=$($apkStatus.Status)",
    "releaseApkSha256=$releaseApkSha256",
    "releaseApkLastWriteTime=$releaseApkLastWriteTime",
    "releaseApkOlderThanBuildValidation=$releaseApkOlderThanBuildValidation",
    "releaseBundle=$releaseBundlePath",
    "releaseBundleStatus=$($bundleStatus.Status)",
    "releaseBundleSha256=$releaseBundleSha256",
    "releaseBundleLastWriteTime=$releaseBundleLastWriteTime",
    "releaseBundleOlderThanBuildValidation=$releaseBundleOlderThanBuildValidation",
    "buildValidationApk=$buildValidationApkPath",
    "buildValidationApkExists=$(Test-Path -LiteralPath $buildValidationApkPath)",
    "buildValidationApkSha256=$buildValidationApkSha256",
    "buildValidationApkLastWriteTime=$buildValidationApkLastWriteTime",
    "buildValidationBundle=$buildValidationBundlePath",
    "buildValidationBundleExists=$(Test-Path -LiteralPath $buildValidationBundlePath)",
    "buildValidationBundleSha256=$buildValidationBundleSha256",
    "buildValidationBundleLastWriteTime=$buildValidationBundleLastWriteTime",
    "staleCurrentVersionArtifacts=$(if ($staleCurrentVersionArtifacts.Count -eq 0) { '<none>' } else { $staleCurrentVersionArtifacts -join ', ' })",
    "legacyDistArtifacts=$(if ($legacyDistArtifacts.Count -eq 0) { '<none>' } else { $legacyDistArtifacts -join ', ' })",
    "allDistApks=$(if ($allDistApks.Count -eq 0) { '<none>' } else { $allDistApks -join ', ' })",
    "allDistBundles=$(if ($allDistBundles.Count -eq 0) { '<none>' } else { $allDistBundles -join ', ' })"
)

$failures = @()
if ($apkStatus.Status -eq 'missing') {
    if (-not $AllowMissingReleaseArtifacts) {
        $failures += "release APK missing: $releaseApkPath"
    }
} elseif ($apkStatus.Status -ne 'verified') {
    $failures += "release APK signature failed: $releaseApkPath"
}

if ($bundleStatus.Status -eq 'missing') {
    if (-not $AllowMissingReleaseArtifacts) {
        $failures += "release AAB missing: $releaseBundlePath"
    }
} elseif ($bundleStatus.Status -ne 'verified') {
    $failures += "release AAB signature failed: $releaseBundlePath"
}

if ($legacyDistArtifacts.Count -gt 0) {
    Write-Warning "Legacy or non-current release-named dist artifacts were left untouched: $($legacyDistArtifacts -join ', ')"
}
if ($staleCurrentVersionArtifacts.Count -gt 0) {
    Write-Warning "Current-version release-named artifacts appear stale relative to build-validation outputs: $($staleCurrentVersionArtifacts -join ', ')"
}

if ($apkStatus.Status -ne 'verified') {
    $metadata += "releaseApkSignatureOutput=$($apkStatus.SignatureOutput.Replace("`r`n", ' ').Replace("`n", ' ').Replace("`r", ' '))"
}
if ($bundleStatus.Status -ne 'verified') {
    $metadata += "releaseBundleSignatureOutput=$($bundleStatus.SignatureOutput.Replace("`r`n", ' ').Replace("`n", ' ').Replace("`r", ' '))"
}

if ($failures.Count -gt 0) {
    $remediation = Get-ReleaseArtifactRemediationText -StaleCurrentVersionArtifacts $staleCurrentVersionArtifacts
    Write-Warning $remediation
    $metadata[0] = 'runStatus=failed'
    $metadata += "failureMessage=$($failures -join '; ')"
    $metadata += "releaseArtifactsRemediation=$remediation"
    Set-Content -LiteralPath $metadataPath -Value $metadata
    throw "Release artifact verification failed. See $metadataPath. Failures: $($failures -join '; ')"
}

$metadata[0] = 'runStatus=completed'
Set-Content -LiteralPath $metadataPath -Value $metadata
Write-Host "[ok] release artifacts verified: $metadataPath"
