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
        throw "$message Set the required environment variables, or run with -AllowUnsigned for a non-uploadable bundle artifact."
    }
    Write-Warning "$message Continuing because -AllowUnsigned was set."
}

& (Join-Path $repoRoot 'scripts\test\verify-vocab-assets.ps1')
& (Join-Path $repoRoot 'gradlew.bat') --no-daemon --console=plain ktlintCheck detekt testDebugUnitTest bundleRelease
if ($LASTEXITCODE -ne 0) {
    throw "Release bundle build failed with exit code $LASTEXITCODE"
}

$bundleDir = Join-Path $repoRoot 'app\build\outputs\bundle\release'
$bundleFiles = Get-ChildItem -Path $bundleDir -Filter '*.aab' -File
if ($bundleFiles.Count -ne 1) {
    throw "Expected exactly one release AAB in $bundleDir, found $($bundleFiles.Count)."
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$target = Join-Path $distDir $bundleFiles[0].Name
Copy-Item -LiteralPath $bundleFiles[0].FullName -Destination $target -Force

$jarSigner = Get-JarSigner
$jarSignerOutput = & $jarSigner '-J-Duser.language=en' '-J-Duser.country=US' -verify -certs $target 2>&1
$jarSignerExitCode = $LASTEXITCODE
$jarSignerText = $jarSignerOutput -join "`n"
$signatureVerified = (
    $jarSignerExitCode -eq 0 -and
    $jarSignerText -match '(?i)\bjar\s+verified\b' -and
    $jarSignerText -notmatch '(?i)jar\s+(is\s+)?unsigned' -and
    $jarSignerText -notmatch '(?i)no\s+manifest'
)
if (-not $signatureVerified) {
    if (-not $AllowUnsigned) {
        throw "Release AAB signature verification failed: $target`n$jarSignerText"
    }
    Write-Warning "Release AAB is unsigned or does not verify. It is for build validation only: $target`n$jarSignerText"
} else {
    Write-Host '[ok] release AAB signature verified'
}

Write-Host "[ok] release AAB: $target"
