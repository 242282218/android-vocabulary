param(
    [switch]$AllowUnsigned
)

$ErrorActionPreference = 'Stop'

. (Join-Path (Join-Path $PSScriptRoot '..') (Join-Path 'lib' 'android-env.ps1'))
$repoRoot = Get-AndroidVocabularyRepoRoot
$distDir = Join-Path $repoRoot 'dist'

Use-AndroidVocabularyJavaHome

$missingSigningVars = @(Test-ReleaseSigningEnvironment)
if ($missingSigningVars.Count -gt 0) {
    $message = "Release signing is not configured. Missing: $($missingSigningVars -join ', ')."
    if (-not $AllowUnsigned) {
        throw "$message Set the required environment variables, or run with -AllowUnsigned for a non-installable build artifact."
    }
    Write-Warning "$message Continuing because -AllowUnsigned was set."
}

try {
    & (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'verify-vocab-assets.ps1'))
} catch {
    throw "Publish-safe vocabulary asset verification failed before release APK build. Cause: $($_.Exception.Message)"
}
Invoke-AndroidVocabularyGradle 'Release APK Gradle build' @('ktlintCheck', 'detekt', 'testDebugUnitTest', 'assembleRelease')

$releaseDir = Join-AndroidVocabularyPath $repoRoot @('app', 'build', 'outputs', 'apk', 'release')
$releaseApkName = Get-AndroidVocabularyReleaseApkName
$releaseApkPath = Join-Path $releaseDir $releaseApkName
if (-not (Test-Path $releaseApkPath)) {
    $availableApks = @(Get-ChildItem -Path $releaseDir -Filter '*.apk' -File -ErrorAction SilentlyContinue | ForEach-Object { $_.Name })
    $availableText = if ($availableApks.Count -eq 0) { '<none>' } else { $availableApks -join ', ' }
    throw "Expected current release APK not found: $releaseApkPath. Available APKs: $availableText."
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null

$apkSigner = Get-AndroidTool -Names @('apksigner.bat', 'apksigner') -RelativeDirs @('build-tools')
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $signatureOutput = & $apkSigner verify --verbose $releaseApkPath 2>&1
    $signatureExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
$signatureText = (@($signatureOutput | ConvertTo-AndroidVocabularyOutputText) -join "`n").Trim()
$signatureVerified = $signatureExitCode -eq 0
$releaseReady = $signatureVerified -and (-not $AllowUnsigned)
if (-not $signatureVerified) {
    if (-not $AllowUnsigned) {
        throw "Release APK signature verification failed: $releaseApkPath`n$signatureText"
    }
    Write-Warning "Release APK is unsigned or does not verify. It is for build validation only: $releaseApkPath`n$signatureText"
} else {
    if (-not [string]::IsNullOrWhiteSpace($signatureText)) {
        Write-Host $signatureText
    }
    if ($releaseReady) {
        Write-Host "[ok] release APK signature verified"
    } else {
        Write-Host "[ok] build-validation APK signature verified, but -AllowUnsigned was set; not release-ready"
    }
}

$targetName =
    if ($releaseReady) {
        $releaseApkName
    } else {
        Get-AndroidVocabularyBuildValidationApkName
    }
$target = Join-Path $distDir $targetName
Copy-Item -LiteralPath $releaseApkPath -Destination $target -Force

if ($releaseReady) {
    Write-Host "[ok] release APK: $target"
} else {
    $releaseTarget = Join-Path $distDir $releaseApkName
    if (Test-Path -LiteralPath $releaseTarget) {
        Write-Warning "Existing release-named APK was left untouched: $releaseTarget"
    }
    Write-Host "[ok] build-validation APK, not release-ready: $target"
}
