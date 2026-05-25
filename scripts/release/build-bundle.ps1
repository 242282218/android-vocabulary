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
        throw "$message Set the required environment variables, or run with -AllowUnsigned for a non-uploadable bundle artifact."
    }
    Write-Warning "$message Continuing because -AllowUnsigned was set."
}

try {
    & (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'verify-vocab-assets.ps1'))
} catch {
    throw "Publish-safe vocabulary asset verification failed before release AAB build. Cause: $($_.Exception.Message)"
}
Invoke-AndroidVocabularyGradle 'Release AAB Gradle build' @('ktlintCheck', 'detekt', 'testDebugUnitTest', 'bundleRelease')

$bundleDir = Join-AndroidVocabularyPath $repoRoot @('app', 'build', 'outputs', 'bundle', 'release')
$bundlePath = Join-Path $bundleDir 'app-release.aab'
if (-not (Test-Path -LiteralPath $bundlePath)) {
    $availableBundles = @(Get-ChildItem -Path $bundleDir -Filter '*.aab' -File -ErrorAction SilentlyContinue | ForEach-Object { $_.Name })
    $availableText = if ($availableBundles.Count -eq 0) { '<none>' } else { $availableBundles -join ', ' }
    throw "Expected Gradle release AAB not found: $bundlePath. Available AABs: $availableText."
}
$extraBundles = @(
    Get-ChildItem -Path $bundleDir -Filter '*.aab' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -ne $bundlePath } |
        ForEach-Object { $_.Name }
)
if ($extraBundles.Count -gt 0) {
    Write-Warning "Ignoring extra release AAB files in ${bundleDir}: $($extraBundles -join ', ')"
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null

$jarSigner = Get-JarSigner
$jarSignerArgs = @(
    '-J-Dfile.encoding=UTF-8',
    '-J-Dsun.stdout.encoding=UTF-8',
    '-J-Dsun.stderr.encoding=UTF-8',
    '-J-Duser.language=en',
    '-J-Duser.country=US',
    '-verify',
    '-certs',
    $bundlePath
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
$signatureVerified = (
    $jarSignerExitCode -eq 0 -and
    $jarSignerText -match '(?i)\bjar\s+verified\b' -and
    $jarSignerText -notmatch '(?i)jar\s+(is\s+)?unsigned' -and
    $jarSignerText -notmatch '(?i)no\s+manifest'
)
$releaseReady = $signatureVerified -and (-not $AllowUnsigned)
if (-not $signatureVerified) {
    if (-not $AllowUnsigned) {
        throw "Release AAB signature verification failed: $bundlePath`n$jarSignerText"
    }
    Write-Warning "Release AAB is unsigned or does not verify. It is for build validation only: $bundlePath`n$jarSignerText"
} else {
    if ($releaseReady) {
        Write-Host '[ok] release AAB signature verified'
    } else {
        Write-Host '[ok] build-validation AAB signature verified, but -AllowUnsigned was set; not uploadable'
    }
}

$targetName =
    if ($releaseReady) {
        Get-AndroidVocabularyReleaseBundleName
    } else {
        Get-AndroidVocabularyBuildValidationBundleName
    }
$target = Join-Path $distDir $targetName
Copy-Item -LiteralPath $bundlePath -Destination $target -Force

if ($releaseReady) {
    Write-Host "[ok] release AAB: $target"
} else {
    $releaseTarget = Join-Path $distDir (Get-AndroidVocabularyReleaseBundleName)
    if (Test-Path -LiteralPath $releaseTarget) {
        Write-Warning "Existing release-named AAB was left untouched: $releaseTarget"
    }
    Write-Host "[ok] build-validation AAB, not uploadable: $target"
}
