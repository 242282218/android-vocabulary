$ErrorActionPreference = 'Stop'

. (Join-Path (Join-Path $PSScriptRoot '..') (Join-Path 'lib' 'android-env.ps1'))
$repoRoot = Get-AndroidVocabularyRepoRoot
Use-AndroidVocabularyJavaHome

try {
    & (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'verify-release-scripts.ps1'))
} catch {
    throw "Release script regression verification failed before local Gradle verification. Cause: $($_.Exception.Message)"
}

try {
    & (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'verify-vocab-assets.ps1'))
} catch {
    throw "Publish-safe vocabulary asset verification failed before local Gradle verification. Cause: $($_.Exception.Message)"
}
Invoke-AndroidVocabularyGradle 'Local Gradle verification' @('ktlintCheck', 'detekt', 'testDebugUnitTest', 'assembleDebug', 'assembleDebugAndroidTest')

Write-Host '[ok] local verification passed'
