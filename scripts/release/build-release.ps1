param(
    [switch]$AllowUnsigned
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\lib\android-env.ps1')
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

& (Join-Path $repoRoot 'scripts\test\verify-vocab-assets.ps1')
& (Join-Path $repoRoot 'gradlew.bat') --no-daemon --console=plain ktlintCheck detekt testDebugUnitTest assembleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Release build failed with exit code $LASTEXITCODE"
}

$releaseDir = Join-Path $repoRoot 'app\build\outputs\apk\release'
$apkFiles = Get-ChildItem -Path $releaseDir -Filter '*.apk' -File
if ($apkFiles.Count -ne 1) {
    throw "Expected exactly one release APK in $releaseDir, found $($apkFiles.Count)."
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$target = Join-Path $distDir $apkFiles[0].Name
Copy-Item -LiteralPath $apkFiles[0].FullName -Destination $target -Force

$apkSigner = Get-AndroidTool -Names @('apksigner.bat', 'apksigner') -RelativeDirs @('build-tools')
& $apkSigner verify --verbose $target
if ($LASTEXITCODE -ne 0) {
    if (-not $AllowUnsigned) {
        throw "Release APK signature verification failed: $target"
    }
    Write-Warning "Release APK is unsigned or does not verify. It is for build validation only: $target"
} else {
    Write-Host "[ok] release APK signature verified"
}

Write-Host "[ok] release APK: $target"
