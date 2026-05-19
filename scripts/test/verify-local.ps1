$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\lib\android-env.ps1')
$repoRoot = Get-AndroidVocabularyRepoRoot
Use-AndroidVocabularyJavaHome

& (Join-Path $repoRoot 'scripts\test\verify-vocab-assets.ps1')
& (Join-Path $repoRoot 'gradlew.bat') --no-daemon --console=plain ktlintCheck detekt testDebugUnitTest assembleDebug assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) {
    throw "Gradle verification failed with exit code $LASTEXITCODE"
}

Write-Host '[ok] local verification passed'
