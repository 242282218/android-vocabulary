$ErrorActionPreference = 'Stop'

. (Join-Path (Join-Path $PSScriptRoot '..') (Join-Path 'lib' 'android-env.ps1'))
$repoRoot = Get-AndroidVocabularyRepoRoot
$tmpParent = Join-AndroidVocabularyPath $repoRoot @('build', 'tmp')
$tmpRoot = Join-Path $tmpParent 'release-script-regression'

function Assert-Equal {
    param(
        [string]$Name,
        [object]$Actual,
        [object]$Expected
    )

    if ($Actual -ne $Expected) {
        throw "$Name expected '$Expected', actual '$Actual'"
    }
}

function Assert-ContainsLine {
    param(
        [string[]]$Lines,
        [string]$ExpectedLine
    )

    if ($Lines -notcontains $ExpectedLine) {
        throw "Missing expected line: $ExpectedLine`nActual:`n$($Lines -join "`n")"
    }
}

function Assert-ContainsPattern {
    param(
        [string[]]$Lines,
        [string]$Pattern
    )

    $match = $Lines | Where-Object { $_ -match $Pattern } | Select-Object -First 1
    if ($null -eq $match) {
        throw "Missing expected pattern: $Pattern`nActual:`n$($Lines -join "`n")"
    }
}

function Write-Utf8NoBomLines {
    param(
        [string]$Path,
        [string[]]$Lines
    )

    [System.IO.File]::WriteAllLines(
        $Path,
        $Lines,
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Assert-UniqueKeyValueMetadata {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $keys = @(
        Get-Content -LiteralPath $Path |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and $_ -match '=' } |
            ForEach-Object { $_.Substring(0, $_.IndexOf('=')) }
    )
    $duplicates = @(
        $keys |
            Group-Object |
            Where-Object { $_.Count -gt 1 } |
            ForEach-Object { "$($_.Name)($($_.Count))" }
    )
    if ($duplicates.Count -gt 0) {
        throw "Duplicate metadata keys in ${Path}: $($duplicates -join ', ')"
    }
}

function Get-ScriptFunctionText {
    param(
        [string]$ScriptPath,
        [string]$FunctionName
    )

    $tokens = $null
    $errors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile(
        (Resolve-Path -LiteralPath $ScriptPath),
        [ref]$tokens,
        [ref]$errors
    )
    if ($errors.Count -gt 0) {
        throw "$ScriptPath parse failed: $($errors.Message -join '; ')"
    }

    $functionAst = $ast.Find(
        {
            param($node)
            $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -eq $FunctionName
        },
        $true
    )
    if ($null -eq $functionAst) {
        throw "Function not found in ${ScriptPath}: $FunctionName"
    }
    return $functionAst.Extent.Text
}

function Test-PowerShellParse {
    $files = @(
        'scripts/lib/android-env.ps1',
        'scripts/test/verify-local.ps1',
        'scripts/test/verify-vocab-assets.ps1',
        'scripts/release/build-release.ps1',
        'scripts/release/build-bundle.ps1',
        'scripts/test/verify-device.ps1',
        'scripts/test/smoke-release-apk.ps1',
        'scripts/test/smoke-release-bundle.ps1',
        'scripts/test/verify-release-artifacts.ps1',
        'scripts/test/verify-release-readiness.ps1',
        'scripts/test/write-github-actions-evidence.ps1',
        'scripts/test/verify-release-scripts.ps1'
    )

    foreach ($file in $files) {
        $tokens = $null
        $errors = $null
        [System.Management.Automation.Language.Parser]::ParseFile(
            (Resolve-Path -LiteralPath (Join-Path $repoRoot $file)),
            [ref]$tokens,
            [ref]$errors
        ) | Out-Null
        if ($errors.Count -gt 0) {
            throw "$file parse failed: $($errors.Message -join '; ')"
        }
    }
}

function Test-ReleaseManifestBackupPolicy {
    $manifestPath = Join-AndroidVocabularyPath $repoRoot @('app', 'src', 'main', 'AndroidManifest.xml')
    $manifest = Get-Content -LiteralPath $manifestPath -Raw

    if ($manifest -notmatch 'android:allowBackup\s*=\s*"false"') {
        throw 'AndroidManifest.xml must keep android:allowBackup="false" for local-only learning data.'
    }

    foreach ($attribute in @('android:fullBackupContent', 'android:dataExtractionRules')) {
        if ($manifest -match [regex]::Escape($attribute)) {
            throw "AndroidManifest.xml must not declare $attribute while backup is disabled."
        }
    }
}

function Test-ReleaseReadinessMetadataKeys {
    $releaseReadinessScriptPath = Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'verify-release-readiness.ps1')
    $releaseReadinessScript = Get-Content -LiteralPath $releaseReadinessScriptPath -Raw
    foreach ($pattern in @(
            'githubActionsEvidenceMetadata=',
            '-Name ''githubActionsEvidence'''
        )) {
        if ($releaseReadinessScript -notmatch [regex]::Escape($pattern)) {
            throw "verify-release-readiness.ps1 must keep GitHub Actions metadata keys distinct: $pattern"
        }
    }
    if ($releaseReadinessScript -match [regex]::Escape('"githubActionsEvidence=$githubEvidence"')) {
        throw 'verify-release-readiness.ps1 must not reuse githubActionsEvidence as the metadata path key.'
    }

    Assert-UniqueKeyValueMetadata `
        -Path (Join-AndroidVocabularyPath $repoRoot @('build', 'release-readiness', 'release-readiness-run.txt'))
}

function Test-ReleaseReadinessDocumentation {
    $expectedDocumentationPatterns = @(
        'github-actions-release-evidence',
        'workflow=Android',
        'conclusion=success',
        'commitSha=<git rev-parse HEAD>',
        'serverUrl=https://github.com',
        'repository=<owner>/<repo>',
        'runId=<github run id>',
        'runAttempt=<github run attempt>',
        'runUrl=https://github.com/<owner>/<repo>/actions/runs/<github run id>',
        'checks=verify-release-scripts, verify-vocab-assets, dependencyCheckAggregate, ktlintCheck, detekt, testDebugUnitTest, assembleDebug, assembleDebugAndroidTest, pixel2Api30DebugAndroidTest',
        'verify-release-readiness.ps1'
    )

    foreach ($relativePath in @('README.md', 'docs/release.md')) {
        $content = Get-Content `
            -LiteralPath (Join-AndroidVocabularyPath $repoRoot ($relativePath -split '/')) `
            -Raw
        foreach ($pattern in $expectedDocumentationPatterns) {
            if ($content -notmatch [regex]::Escape($pattern)) {
                throw "$relativePath must document current GitHub Actions release evidence format: $pattern"
            }
        }
    }
}

function Test-VerifyDeviceFailureStageCases {
    . ([scriptblock]::Create(
            (Get-ScriptFunctionText `
                -ScriptPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'verify-device.ps1')) `
                -FunctionName 'Resolve-FailureStage')
        ))

    $cases = @(
        @{ Name = 'android tool'; Message = 'Android tool not found: adb.exe, adb'; Fallback = '<unknown>'; Expected = 'androidTool' },
        @{ Name = 'java'; Message = 'JAVA_HOME is not set and no known JDK 17 installation was found.'; Fallback = '<unknown>'; Expected = 'javaEnvironment' },
        @{ Name = 'version'; Message = 'Gradle property not found: androidVocab.versionName'; Fallback = '<unknown>'; Expected = 'versionMetadata' },
        @{ Name = 'device'; Message = "Device 'abc' is not online. Online devices: <none>."; Fallback = '<unknown>'; Expected = 'resolveDevice' },
        @{ Name = 'connected test'; Message = 'connectedDebugAndroidTest failed. See x. Cause: connectedDebugAndroidTest failed with exit code 1'; Fallback = 'connectedDebugAndroidTest'; Expected = 'connectedDebugAndroidTest' },
        @{ Name = 'bundle build'; Message = 'build-release-bundle failed. See x. Cause: build-release-bundle failed with exit code 1'; Fallback = 'buildReleaseBundle'; Expected = 'buildReleaseBundle' },
        @{ Name = 'bundletool build'; Message = 'smoke-release-bundle failed. See x. Cause: bundletool build-apks failed with exit code 1'; Fallback = 'smokeReleaseBundle'; Expected = 'bundletoolBuildApks' },
        @{ Name = 'bundle signature'; Message = 'smoke-release-bundle failed. See x. Cause: AAB signature verification failed: dist/app.aab'; Fallback = 'smokeReleaseBundle'; Expected = 'bundleSignatureVerification' },
        @{ Name = 'installed launch'; Message = 'smoke-release-bundle failed. See x. Cause: installed app launch smoke after bundle install failed with exit code 1'; Fallback = 'smokeReleaseBundle'; Expected = 'installedAppLaunchSmoke' },
        @{ Name = 'diagnostics'; Message = 'adb shell getprop failed with exit code 1. See getprop.txt.'; Fallback = 'deviceDiagnostics'; Expected = 'deviceDiagnostics' },
        @{ Name = 'fallback'; Message = 'unexpected failure'; Fallback = 'smokeReleaseBundle'; Expected = 'smokeReleaseBundle' }
    )

    foreach ($case in $cases) {
        $actual = Resolve-FailureStage -FailureMessage $case.Message -FallbackStage $case.Fallback
        Assert-Equal -Name $case.Name -Actual $actual -Expected $case.Expected
    }
}

function Test-BundleSmokeFailureStageCases {
    . ([scriptblock]::Create(
            (Get-ScriptFunctionText `
                -ScriptPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'smoke-release-bundle.ps1')) `
                -FunctionName 'Resolve-BundleSmokeFailureStage')
        ))

    $cases = @(
        @{ Name = 'java'; Message = 'JAVA_HOME is not set and no known JDK 17 installation was found.'; Fallback = '<unknown>'; Expected = 'javaEnvironment' },
        @{ Name = 'bundletool'; Message = 'bundletool.jar not found. Run scripts/tooling/install-bundletool.ps1 or set BUNDLETOOL_JAR.'; Fallback = '<unknown>'; Expected = 'resolveBundleTool' },
        @{ Name = 'bundle'; Message = 'No AAB found for current version'; Fallback = '<unknown>'; Expected = 'resolveBundle' },
        @{ Name = 'device'; Message = 'No online Android device found. Connect a device/emulator, enable USB debugging, then rerun the script.'; Fallback = '<unknown>'; Expected = 'resolveDevice' },
        @{ Name = 'signing'; Message = 'Release signing is not configured. Missing: ANDROID_VOCAB_RELEASE_STORE_FILE.'; Fallback = '<unknown>'; Expected = 'signingConfiguration' },
        @{ Name = 'bundle signature'; Message = 'AAB signature verification failed: dist/app.aab'; Fallback = '<unknown>'; Expected = 'bundleSignatureVerification' },
        @{ Name = 'build apks'; Message = 'bundletool build-apks failed with exit code 1'; Fallback = 'bundletoolBuildApks'; Expected = 'bundletoolBuildApks' },
        @{ Name = 'install apks'; Message = 'bundletool install-apks failed with exit code 1'; Fallback = 'bundletoolInstallApks'; Expected = 'bundletoolInstallApks' },
        @{ Name = 'launch'; Message = 'installed app launch smoke after bundle install failed with exit code 1'; Fallback = 'installedAppLaunchSmoke'; Expected = 'installedAppLaunchSmoke' },
        @{ Name = 'fallback'; Message = 'unexpected failure'; Fallback = 'smokeReleaseBundle'; Expected = 'smokeReleaseBundle' }
    )

    foreach ($case in $cases) {
        $actual = Resolve-BundleSmokeFailureStage -FailureMessage $case.Message -FallbackStage $case.Fallback
        Assert-Equal -Name $case.Name -Actual $actual -Expected $case.Expected
    }
}

function Test-ReleaseArtifactNaming {
    $versionName = Get-GradlePropertyValue 'androidVocab.versionName'
    $versionCode = Get-GradlePropertyValue 'androidVocab.versionCode'

    Assert-Equal `
        -Name 'build-validation APK name' `
        -Actual (Get-AndroidVocabularyBuildValidationApkName) `
        -Expected "AndroidVocabulary-build-validation-v$versionName-$versionCode.apk"
    Assert-Equal `
        -Name 'build-validation bundle name' `
        -Actual (Get-AndroidVocabularyBuildValidationBundleName) `
        -Expected "AndroidVocabulary-build-validation-v$versionName-$versionCode.aab"
    Assert-Equal `
        -Name 'required GitHub Actions checks' `
        -Actual ((Get-AndroidVocabularyRequiredGitHubActionsChecks) -join ', ') `
        -Expected 'verify-release-scripts, verify-vocab-assets, dependencyCheckAggregate, ktlintCheck, detekt, testDebugUnitTest, assembleDebug, assembleDebugAndroidTest, pixel2Api30DebugAndroidTest'

    $releaseScript = Get-Content `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'release', 'build-release.ps1')) `
        -Raw
    if ($releaseScript -notmatch 'Get-AndroidVocabularyBuildValidationApkName') {
        throw 'build-release.ps1 must copy local validation APKs to a build-validation artifact name.'
    }
    if ($releaseScript -notmatch '\$releaseReady\s*=\s*\$signatureVerified\s*-and\s*\(-not\s+\$AllowUnsigned\)') {
        throw 'build-release.ps1 must not treat -AllowUnsigned output as a release-ready APK.'
    }

    $bundleScript = Get-Content `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'release', 'build-bundle.ps1')) `
        -Raw
    if ($bundleScript -notmatch 'Get-AndroidVocabularyBuildValidationBundleName') {
        throw 'build-bundle.ps1 must copy local validation AABs to a build-validation artifact name.'
    }
    if ($bundleScript -notmatch '\$releaseReady\s*=\s*\$signatureVerified\s*-and\s*\(-not\s+\$AllowUnsigned\)') {
        throw 'build-bundle.ps1 must not treat -AllowUnsigned output as an uploadable AAB.'
    }

    $deviceScript = Get-Content `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'verify-device.ps1')) `
        -Raw
    if ($deviceScript -notmatch 'Get-AndroidVocabularyBuildValidationBundleName') {
        throw 'verify-device.ps1 must pass build-validation AABs to bundle smoke after -AllowUnsignedBundle builds.'
    }
    foreach ($pattern in @(
            'function Get-ResolvedBundleSha256',
            'function Get-BundleSmokeRunMetadataValue',
            'bundleSha256=',
            'bundleSmokeBundleSha256=',
            'bundleSmokeRunStatus=',
            'bundleSmokeReleaseReady=',
            'Get-FileHash -LiteralPath $bundlePath -Algorithm SHA256'
        )) {
        if ($deviceScript -notmatch [regex]::Escape($pattern)) {
            throw "verify-device.ps1 must bind device verification metadata to the AAB SHA-256: $pattern"
        }
    }

    $bundleSmokeScript = Get-Content `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'smoke-release-bundle.ps1')) `
        -Raw
    if ($bundleSmokeScript -notmatch 'function Get-DefaultApksPath') {
        throw 'smoke-release-bundle.ps1 must centralize APK Set output naming.'
    }
    if ($bundleSmokeScript -notmatch 'Get-AndroidVocabularyBuildValidationBundleName') {
        throw 'smoke-release-bundle.ps1 must use build-validation names for local-only APK Set outputs.'
    }
    if ($bundleSmokeScript -notmatch 'function Test-BundleSmokeReleaseReady') {
        throw 'smoke-release-bundle.ps1 must explicitly guard release-ready bundle smoke output.'
    }
    if ($bundleSmokeScript -notmatch 'function Warn-IfAmbiguousBundleSmokeArtifactsExist') {
        throw 'smoke-release-bundle.ps1 must warn about ambiguous historical APK Set artifacts.'
    }
    if ($bundleSmokeScript -notmatch 'existingAmbiguousApks=') {
        throw 'smoke-release-bundle.ps1 must write ambiguous APK Set artifact names to metadata.'
    }
    foreach ($pattern in @(
            'function Get-BundleSmokeSha256Value',
            'bundleSha256=',
            'Get-FileHash -LiteralPath $script:ResolvedBundlePath -Algorithm SHA256'
        )) {
        if ($bundleSmokeScript -notmatch [regex]::Escape($pattern)) {
            throw "smoke-release-bundle.ps1 must bind bundle smoke metadata to the AAB SHA-256: $pattern"
        }
    }

    $apkSmokeScript = Get-Content `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'smoke-release-apk.ps1')) `
        -Raw
    foreach ($pattern in @(
            'function Get-ApkSmokeSha256Value',
            'apkSha256=',
            'Get-FileHash -LiteralPath $script:ResolvedApkPath -Algorithm SHA256'
        )) {
        if ($apkSmokeScript -notmatch [regex]::Escape($pattern)) {
            throw "smoke-release-apk.ps1 must bind APK smoke metadata to the APK SHA-256: $pattern"
        }
    }

    $releaseArtifactsScript = Get-Content `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'verify-release-artifacts.ps1')) `
        -Raw
    if ($releaseArtifactsScript -notmatch 'releaseApkStatus=') {
        throw 'verify-release-artifacts.ps1 must write release APK status to metadata.'
    }
    if ($releaseArtifactsScript -notmatch 'releaseApkSha256=') {
        throw 'verify-release-artifacts.ps1 must write release APK SHA-256 to metadata.'
    }
    if ($releaseArtifactsScript -notmatch 'releaseApkLastWriteTime=') {
        throw 'verify-release-artifacts.ps1 must write release APK last-write time to metadata.'
    }
    if ($releaseArtifactsScript -notmatch 'releaseApkOlderThanBuildValidation=') {
        throw 'verify-release-artifacts.ps1 must record whether the release APK is older than build-validation output.'
    }
    if ($releaseArtifactsScript -notmatch 'releaseBundleStatus=') {
        throw 'verify-release-artifacts.ps1 must write release AAB status to metadata.'
    }
    if ($releaseArtifactsScript -notmatch 'releaseBundleSha256=') {
        throw 'verify-release-artifacts.ps1 must write release AAB SHA-256 to metadata.'
    }
    if ($releaseArtifactsScript -notmatch 'releaseBundleLastWriteTime=') {
        throw 'verify-release-artifacts.ps1 must write release AAB last-write time to metadata.'
    }
    if ($releaseArtifactsScript -notmatch 'releaseBundleOlderThanBuildValidation=') {
        throw 'verify-release-artifacts.ps1 must record whether the release AAB is older than build-validation output.'
    }
    if ($releaseArtifactsScript -notmatch 'Get-ReleaseArtifactSha256') {
        throw 'verify-release-artifacts.ps1 must centralize release artifact SHA-256 calculation.'
    }
    if ($releaseArtifactsScript -notmatch 'Get-ReleaseArtifactLastWriteTimeValue') {
        throw 'verify-release-artifacts.ps1 must centralize release artifact timestamp collection.'
    }
    if ($releaseArtifactsScript -notmatch 'Test-ReleaseArtifactOlderThan') {
        throw 'verify-release-artifacts.ps1 must compare current release artifacts against build-validation timestamps.'
    }
    if ($releaseArtifactsScript -notmatch 'buildValidationApkExists=') {
        throw 'verify-release-artifacts.ps1 must record build-validation APK presence.'
    }
    if ($releaseArtifactsScript -notmatch 'buildValidationApkSha256=') {
        throw 'verify-release-artifacts.ps1 must record build-validation APK SHA-256.'
    }
    if ($releaseArtifactsScript -notmatch 'buildValidationApkLastWriteTime=') {
        throw 'verify-release-artifacts.ps1 must record build-validation APK last-write time.'
    }
    if ($releaseArtifactsScript -notmatch 'buildValidationBundleExists=') {
        throw 'verify-release-artifacts.ps1 must record build-validation AAB presence.'
    }
    if ($releaseArtifactsScript -notmatch 'buildValidationBundleSha256=') {
        throw 'verify-release-artifacts.ps1 must record build-validation AAB SHA-256.'
    }
    if ($releaseArtifactsScript -notmatch 'buildValidationBundleLastWriteTime=') {
        throw 'verify-release-artifacts.ps1 must record build-validation AAB last-write time.'
    }
    if ($releaseArtifactsScript -notmatch 'staleCurrentVersionArtifacts=') {
        throw 'verify-release-artifacts.ps1 must record stale current-version release artifact names.'
    }
    if ($releaseArtifactsScript -notmatch 'releaseArtifactsRemediation=') {
        throw 'verify-release-artifacts.ps1 must write release artifact remediation guidance on failure.'
    }
    if ($releaseArtifactsScript -notmatch 'without -AllowUnsigned') {
        throw 'verify-release-artifacts.ps1 remediation must tell users to rebuild signed release artifacts.'
    }
    if ($releaseArtifactsScript -notmatch 'archive them outside dist before final audit') {
        throw 'verify-release-artifacts.ps1 remediation must tell users how to handle stale release-named artifacts.'
    }
    if ($releaseArtifactsScript -notmatch 'older than the matching build-validation outputs') {
        throw 'verify-release-artifacts.ps1 remediation must explain when current release-named artifacts are stale.'
    }

    $releaseReadinessScript = Get-Content `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'verify-release-readiness.ps1')) `
        -Raw
    if ($releaseReadinessScript -notmatch 'Get-AndroidVocabularyRequiredGitHubActionsChecks') {
        throw 'verify-release-readiness.ps1 must use the shared GitHub Actions required checks list.'
    }
    foreach ($pattern in @(
            "-Name 'gitWorktreeClean'",
            "-Name 'releaseArtifacts'",
            "-Name 'releaseApkSmoke'",
            "-Name 'deviceAabVerification'",
            "-Name 'githubActionsEvidence'"
        )) {
        if ($releaseReadinessScript -notmatch [regex]::Escape($pattern)) {
            throw "verify-release-readiness.ps1 must record readiness requirement: $pattern"
        }
    }
    foreach ($pattern in @(
            'Get-CurrentGitHeadSha',
            'function Get-CurrentGitStatusEntries',
            'status --porcelain=v1 --untracked-files=all',
            'function Format-GitStatusSummary',
            'function Get-ReadinessRemediationText',
            'gitWorktreeStatus=',
            'gitWorktreeDirtyCount=',
            'gitWorktreeDirtyEntries=',
            'gitWorktreeRemediation=',
            '$gitWorktreeClean = $gitWorktreeDirtyCount -eq 0',
            'remediation=$gitWorktreeRemediation',
            'function Copy-ReadinessEvidenceArtifact',
            'Remove-Item -LiteralPath $destinationPath -Force -ErrorAction SilentlyContinue',
            '$resolvedSourcePath -eq $resolvedDestinationPath',
            'function Get-ReadinessEvidenceArchiveSha256',
            'Get-Sha256OrDefault (Join-Path $artifactDir $ArchiveFileName)',
            'function Get-ReadinessEvidenceArchivePath',
            '$deviceBundleSmokeArchiveMetadata =',
            '$deviceBundleSmokeArchive = Read-KeyValueMetadata $deviceBundleSmokeArchivePath',
            'function Format-ReadinessEvidenceArchiveSummary',
            'releaseArtifacts:${ReleaseArtifactsArchive}:${ReleaseArtifactsArchiveSha256}',
            'deviceBundleSmoke:${DeviceBundleSmokeArchive}:${DeviceBundleSmokeArchiveSha256}',
            'evidenceArchives=',
            'releaseArtifactsRemediation=',
            'releaseArtifactsStaleCurrentVersionArtifacts=',
            '$releaseArtifactsMetadataRemediation = Get-MetadataValueOrDefault $releaseArtifacts ''releaseArtifactsRemediation'' ''<missing>''',
            '$releaseArtifactsStaleCurrentVersionArtifacts =',
            'Get-MetadataValueOrDefault $releaseArtifacts ''staleCurrentVersionArtifacts''',
            'Stale current-version release artifacts:',
            'function Get-BlockerSummaryValue',
            'function Get-BlockerActionSummaryValue',
            'blockerSummary=',
            'blockerActionSummary=',
            '$blockerSummaryItems = @(',
            '$blockerSummary = Get-BlockerSummaryValue -Items $blockerSummaryItems',
            '$blockerActionSummaryItems = @(',
            '$blockerActionSummary = Get-BlockerActionSummaryValue -Items $blockerActionSummaryItems',
            'Blockers: $blockerSummary.',
            'Actions: $blockerActionSummary.',
            'evidence-release-artifacts-run.txt',
            'evidence-smoke-release-apk-run.txt',
            'evidence-verification-completed.txt',
            'evidence-smoke-release-bundle-run.txt',
            'evidence-github-actions.txt',
            'releaseArtifactsEvidenceArchive=',
            'releaseArtifactsEvidenceArchiveSha256=',
            'apkSmokeEvidenceArchive=',
            'apkSmokeEvidenceArchiveSha256=',
            'deviceVerificationEvidenceArchive=',
            'deviceVerificationEvidenceArchiveSha256=',
            'deviceBundleSmokeEvidenceArchive=',
            'deviceBundleSmokeEvidenceArchiveSha256=',
            'githubActionsEvidenceArchive=',
            'githubActionsEvidenceArchiveSha256=',
            'githubActionsEvidenceMetadata=',
            'githubActionsServerUrl=',
            'githubActionsRepository=',
            'failedRequirementNames=',
            '$failedRequirementNames = @(',
            'ForEach-Object { $_.Substring(0, $_.IndexOf(''='')) }',
            '$failedRequirementNames -join '', ''',
            '[string]$ReleaseArtifactsEvidencePath',
            'function Get-ReleaseArtifactsMetadataPath',
            'Resolve-Path -LiteralPath $ReleaseArtifactsEvidencePath',
            'releaseArtifactsEvidenceMode=',
            'releaseArtifactsEvidenceInput=',
            '[string]$ApkSmokeEvidencePath',
            'function Get-ApkSmokeMetadataPath',
            'Resolve-Path -LiteralPath $ApkSmokeEvidencePath',
            'apkSmokeEvidenceMode=',
            'apkSmokeEvidenceInput=',
            '[string]$DeviceVerificationEvidencePath',
            'function Get-LatestMatchingDeviceVerificationMetadataPath',
            'Test-MetadataValue $metadata ''bundleSha256'' $ReleaseBundleSha256',
            'function Get-DeviceVerificationMetadataPath',
            'Get-LatestMatchingDeviceVerificationMetadataPath',
            'Resolve-Path -LiteralPath $DeviceVerificationEvidencePath',
            'deviceVerificationEvidenceMode=',
            'deviceVerificationEvidenceInput=',
            'function Get-DeviceBundleSmokeMetadataPath',
            'bundleSmokeRunMetadata',
            '$bundleSmokeMetadata -in @(',
            '''<missing>'', ''<none>'', ''<skipped>'', ''<not used>''',
            'Split-Path -Parent $DeviceVerificationMetadataPath',
            'Join-Path $deviceVerificationDir $bundleSmokeMetadata',
            '$deviceBundleSmokeMetadata =',
            'deviceBundleSmokeMetadata=$deviceBundleSmokeMetadata',
            'Test-Sha256String',
            'Get-Sha256OrDefault',
            'releaseApkSha256Metadata=',
            'releaseApkSha256Current=',
            'releaseBundleSha256Metadata=',
            'releaseBundleSha256Current=',
            'archive=$releaseArtifactsEvidenceArchive;archiveSha256=$releaseArtifactsEvidenceArchiveSha256',
            'Test-Sha256String $releaseArtifactsApkSha256',
            '$releaseArtifactsApkSha256 -eq $currentReleaseApkSha256',
            'Test-Sha256String $releaseArtifactsBundleSha256',
            '$releaseArtifactsBundleSha256 -eq $currentReleaseBundleSha256',
            'apkSmokeSha256=',
            'apkSmokeRemediation=',
            'archive=$apkSmokeEvidenceArchive;archiveSha256=$apkSmokeEvidenceArchiveSha256',
            'Test-Sha256String $apkSmokeSha256',
            '$apkSmokeSha256 -eq $releaseArtifactsApkSha256',
            'remediation=$apkSmokeRemediation',
            'deviceBundleSha256=',
            'archive=$deviceVerificationEvidenceArchive;archiveSha256=$deviceVerificationEvidenceArchiveSha256',
            'Test-Sha256String $deviceBundleSha256',
            '$deviceBundleSha256 -eq $releaseArtifactsBundleSha256',
            'deviceBundleSmokeSha256=',
            'Test-MetadataValue $deviceVerification ''bundleSmokeRunMetadata'' ''smoke-release-bundle-run.txt''',
            'Test-MetadataValue $deviceVerification ''bundleSmokeRunStatus'' ''completed''',
            'Test-MetadataValue $deviceVerification ''bundleSmokeBundlePathMatchesCurrentVersionedName'' ''True''',
            'Test-MetadataValue $deviceVerification ''bundleSmokeReleaseReady'' ''True''',
            'Test-Sha256String $deviceBundleSmokeSha256',
            '$deviceBundleSmokeSha256 -eq $releaseArtifactsBundleSha256',
            '$deviceBundleSmokeSha256 -eq $deviceBundleSha256',
            'deviceBundleSmokeArchiveRunStatus=',
            'deviceBundleSmokeArchiveMetadata=',
            'deviceBundleSmokeArchiveBundleSha256=',
            'deviceBundleSmokeArchiveBundlePathMatchesCurrentVersionedName=',
            'deviceBundleSmokeArchiveReleaseReady=',
            'deviceBundleSmokeArchiveApkSetSigning=',
            'deviceVerificationRemediation=',
            'deviceBundleSmokeArchiveRemediation=',
            '$deviceBundleSmokeArchiveReady =',
            'Test-MetadataValue $deviceBundleSmokeArchive ''runStatus'' ''completed''',
            'Test-MetadataValue $deviceBundleSmokeArchive ''versionName'' $versionName',
            'Test-MetadataValue $deviceBundleSmokeArchive ''versionCode'' $versionCode',
            'Test-MetadataValue $deviceBundleSmokeArchive ''bundlePathMatchesCurrentVersionedName'' ''True''',
            'Test-MetadataValue $deviceBundleSmokeArchive ''bundleSmokeReleaseReady'' ''True''',
            'Test-MetadataValue $deviceBundleSmokeArchive ''apkSetSigning'' ''release-signing''',
            'Test-Sha256String $deviceBundleSmokeArchiveBundleSha256',
            '$deviceBundleSmokeArchiveBundleSha256 -eq $releaseArtifactsBundleSha256',
            '$deviceBundleSmokeArchiveBundleSha256 -eq $deviceBundleSha256',
            '$deviceBundleSmokeArchiveBundleSha256 -eq $deviceBundleSmokeSha256',
            '-Name ''deviceBundleSmokeArchive''',
            'archive=$deviceBundleSmokeEvidenceArchive;archiveSha256=$deviceBundleSmokeEvidenceArchiveSha256',
            'remediation=$deviceVerificationRemediation',
            'remediation=$deviceBundleSmokeArchiveRemediation',
            'path=$deviceBundleSmokeArchiveMetadata;archive=$deviceBundleSmokeEvidenceArchive;archiveSha256=$deviceBundleSmokeEvidenceArchiveSha256;bundleSmokeArchiveBundleSha256=',
            'githubActionsWorkflow=',
            'githubActionsConclusion=',
            'githubActionsCommitSha=',
            'githubActionsRunId=',
            'githubActionsRunAttempt=',
            'githubActionsRunUrl=',
            'githubActionsChecks=',
            'githubActionsRemediation=',
            'githubActionsRequiredChecks=',
            '$githubWorkflow -eq ''Android''',
            '$githubConclusion -eq ''success''',
            '$githubCommitSha -eq $currentCommitSha',
            'Test-PositiveIntegerString $githubRunId',
            'Test-PositiveIntegerString $githubRunAttempt',
            '$githubServerUrl -eq ''https://github.com''',
            'Test-GitHubActionsRunUrl `',
            '-RunUrl $githubRunUrl `',
            '-RunId $githubRunId `',
            '-ServerUrl $githubServerUrl `',
            '-Repository $githubRepository',
            'Test-ContainsAllValues -Actual $githubChecks -Expected $requiredGitHubChecks',
            'archive=$githubActionsEvidenceArchive;archiveSha256=$githubActionsEvidenceArchiveSha256',
            'remediation=$githubActionsRemediation',
            'Get-AndroidVocabularyRequiredGitHubActionsChecks'
        )) {
        if ($releaseReadinessScript -notmatch [regex]::Escape($pattern)) {
            throw "verify-release-readiness.ps1 must validate GitHub Actions evidence field: $pattern"
        }
    }

    $githubEvidenceScript = Get-Content `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', 'write-github-actions-evidence.ps1')) `
        -Raw
    foreach ($pattern in @(
            'GITHUB_WORKFLOW',
            'GITHUB_SHA',
            'GITHUB_RUN_ID',
            'GITHUB_RUN_ATTEMPT',
            'GITHUB_SERVER_URL',
            'GITHUB_REPOSITORY',
            'Unexpected GitHub workflow',
            'serverUrl=',
            'repository=',
            'conclusion=success',
            'runId=',
            'runAttempt=',
            'runUrl=',
            'Get-AndroidVocabularyRequiredGitHubActionsChecks'
        )) {
        if ($githubEvidenceScript -notmatch [regex]::Escape($pattern)) {
            throw "write-github-actions-evidence.ps1 must write release readiness evidence field: $pattern"
        }
    }

    $workflow = Get-Content `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('.github', 'workflows', 'android.yml')) `
        -Raw
    foreach ($pattern in @(
            'name: Android',
            'run: ./scripts/test/verify-release-scripts.ps1',
            'run: ./scripts/test/verify-vocab-assets.ps1',
            'run: ./gradlew --no-daemon --console=plain dependencyCheckAggregate',
            'ktlintCheck detekt testDebugUnitTest assembleDebug assembleDebugAndroidTest pixel2Api30DebugAndroidTest',
            'run: ./scripts/test/write-github-actions-evidence.ps1',
            'name: github-actions-release-evidence',
            'path: build/release-readiness/github-actions-evidence.txt'
        )) {
        if ($workflow -notmatch [regex]::Escape($pattern)) {
            throw "android.yml must keep release readiness evidence aligned with local checks: $pattern"
        }
    }

    $rootBuildScript = Get-Content `
        -LiteralPath (Join-Path $repoRoot 'build.gradle.kts') `
        -Raw
    foreach ($pattern in @(
            'val nvdApiKeyProvider',
            'providers.environmentVariable("NVD_API_KEY")',
            '"dependencyCheckAggregate"',
            'throw GradleException(',
            'NVD API key is required'
        )) {
        if ($rootBuildScript -notmatch [regex]::Escape($pattern)) {
            throw "build.gradle.kts must fail fast when dependency-check runs without NVD API key: $pattern"
        }
    }
}

function Test-GitHubActionsEvidenceWriter {
    $testRoot = Join-Path $tmpRoot 'github-actions-evidence-writer'
    Remove-TestRootSafely $testRoot
    Copy-TestScriptRepo -TargetRoot $testRoot -TestScripts @('write-github-actions-evidence.ps1')

    $oldWorkflow = $env:GITHUB_WORKFLOW
    $oldSha = $env:GITHUB_SHA
    $oldRunId = $env:GITHUB_RUN_ID
    $oldRunAttempt = $env:GITHUB_RUN_ATTEMPT
    $oldServerUrl = $env:GITHUB_SERVER_URL
    $oldRepository = $env:GITHUB_REPOSITORY
    $env:GITHUB_WORKFLOW = 'Android'
    $env:GITHUB_SHA = '0123456789abcdef0123456789abcdef01234567'
    $env:GITHUB_RUN_ID = '123456789'
    $env:GITHUB_RUN_ATTEMPT = '2'
    $env:GITHUB_SERVER_URL = 'https://github.com'
    $env:GITHUB_REPOSITORY = 'zzz/android-vocab'
    try {
        & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'write-github-actions-evidence.ps1'))
    } finally {
        $env:GITHUB_WORKFLOW = $oldWorkflow
        $env:GITHUB_SHA = $oldSha
        $env:GITHUB_RUN_ID = $oldRunId
        $env:GITHUB_RUN_ATTEMPT = $oldRunAttempt
        $env:GITHUB_SERVER_URL = $oldServerUrl
        $env:GITHUB_REPOSITORY = $oldRepository
    }

    $evidencePath = Join-AndroidVocabularyPath $testRoot @('build', 'release-readiness', 'github-actions-evidence.txt')
    if (-not (Test-Path -LiteralPath $evidencePath)) {
        throw "GitHub Actions evidence was not written: $evidencePath"
    }
    $metadata = Get-Content -LiteralPath $evidencePath
        foreach ($line in @(
                'workflow=Android',
                'conclusion=success',
                'commitSha=0123456789abcdef0123456789abcdef01234567',
                'serverUrl=https://github.com',
                'repository=zzz/android-vocab',
                'runId=123456789',
                'runAttempt=2',
                'runUrl=https://github.com/zzz/android-vocab/actions/runs/123456789',
                "checks=$((Get-AndroidVocabularyRequiredGitHubActionsChecks) -join ', ')"
            )) {
        Assert-ContainsLine -Lines $metadata -ExpectedLine $line
    }
}

function Test-ReleaseReadinessPositiveFixture {
    $testRoot = Join-Path $tmpRoot 'release-readiness-positive'
    Remove-TestRootSafely $testRoot
    Copy-TestScriptRepo -TargetRoot $testRoot -TestScripts @('verify-release-readiness.ps1')

    Set-Content `
        -LiteralPath (Join-Path $testRoot 'gradle.properties') `
        -Encoding Ascii `
        -Value @(
            'androidVocab.versionCode=1',
            'androidVocab.versionName=0.1.0'
        )

    $distDir = Join-Path $testRoot 'dist'
    New-Item -ItemType Directory -Force -Path $distDir | Out-Null
    $releaseApk = Join-Path $distDir 'AndroidVocabulary-release-v0.1.0-1.apk'
    $releaseBundle = Join-Path $distDir 'AndroidVocabulary-release-v0.1.0-1.aab'
    Set-Content -LiteralPath $releaseApk -Encoding Ascii -Value 'release apk fixture'
    Set-Content -LiteralPath $releaseBundle -Encoding Ascii -Value 'release bundle fixture'
    $releaseApkSha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $releaseBundleSha256 = (Get-FileHash -LiteralPath $releaseBundle -Algorithm SHA256).Hash.ToLowerInvariant()

    $releaseArtifactsPath = Join-AndroidVocabularyPath $testRoot @('build', 'release-artifacts', 'release-artifacts-run.txt')
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $releaseArtifactsPath) | Out-Null
    $successfulReleaseArtifactsMetadata = @(
        'runStatus=completed',
        'versionName=0.1.0',
        'versionCode=1',
        "releaseApk=$releaseApk",
        'releaseApkStatus=verified',
        "releaseApkSha256=$releaseApkSha256",
        "releaseBundle=$releaseBundle",
        'releaseBundleStatus=verified',
        "releaseBundleSha256=$releaseBundleSha256",
        'staleCurrentVersionArtifacts=<none>'
    )
    Write-Utf8NoBomLines -Path $releaseArtifactsPath -Lines $successfulReleaseArtifactsMetadata

    $apkSmokePath = Join-AndroidVocabularyPath $testRoot @('build', 'apk-smoke', 'smoke-release-apk-run.txt')
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $apkSmokePath) | Out-Null
    $successfulApkSmokeMetadata = @(
        'runStatus=completed',
        'versionName=0.1.0',
        'versionCode=1',
        'skipInstall=False',
        'apkPathMatchesCurrentVersionedName=True',
        "apkSha256=$releaseApkSha256"
    )
    Write-Utf8NoBomLines -Path $apkSmokePath -Lines $successfulApkSmokeMetadata

    $deviceDir = Join-AndroidVocabularyPath $testRoot @('build', 'device-verification', 'v0.1.0-1-fixture')
    New-Item -ItemType Directory -Force -Path $deviceDir | Out-Null
    $deviceCompletedPath = Join-Path $deviceDir 'verification-completed.txt'
    $bundleSmokePath = Join-Path $deviceDir 'smoke-release-bundle-run.txt'
    $successfulBundleSmokeMetadata = @(
        'runStatus=completed',
        'versionName=0.1.0',
        'versionCode=1',
        'bundlePathMatchesCurrentVersionedName=True',
        'bundleSmokeReleaseReady=True',
        'apkSetSigning=release-signing',
        "bundleSha256=$releaseBundleSha256"
    )
    Write-Utf8NoBomLines -Path $bundleSmokePath -Lines $successfulBundleSmokeMetadata
    $successfulDeviceVerificationMetadata = @(
        'runStatus=completed',
        'versionName=0.1.0',
        'versionCode=1',
        'usesLocalOnlyReleaseOverrides=False',
        'bundlePathMatchesCurrentVersionedName=True',
        "bundleSha256=$releaseBundleSha256",
        'bundleSmokeRunMetadata=smoke-release-bundle-run.txt',
        'bundleSmokeRunStatus=completed',
        "bundleSmokeBundleSha256=$releaseBundleSha256",
        'bundleSmokeBundlePathMatchesCurrentVersionedName=True',
        'bundleSmokeReleaseReady=True',
        'bundleSmokeApkSetSigning=release-signing'
    )
    Write-Utf8NoBomLines -Path $deviceCompletedPath -Lines $successfulDeviceVerificationMetadata

    $fakeCommitSha = '0123456789abcdef0123456789abcdef01234567'
    $githubEvidencePath = Join-Path $testRoot 'github-actions-evidence.txt'
    Write-Utf8NoBomLines -Path $githubEvidencePath -Lines @(
        'workflow=Android',
        'conclusion=success',
        "commitSha=$fakeCommitSha",
        'serverUrl=https://github.com',
        'repository=zzz/android-vocab',
        'runId=123456789',
        'runAttempt=2',
        'runUrl=https://github.com/zzz/android-vocab/actions/runs/123456789',
        "checks=$((Get-AndroidVocabularyRequiredGitHubActionsChecks) -join ', ')"
    )

    $oldGitFunction = Get-Item -LiteralPath function:\git -ErrorAction SilentlyContinue
    $oldFakeGitSha = $env:ANDROID_VOCAB_TEST_GIT_SHA
    $oldFakeGitStatus = $env:ANDROID_VOCAB_TEST_GIT_STATUS
    $env:ANDROID_VOCAB_TEST_GIT_SHA = $fakeCommitSha
    $env:ANDROID_VOCAB_TEST_GIT_STATUS = ''
    Set-Item -LiteralPath function:\git -Value {
        param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

        if (
            $Arguments.Count -ge 4 -and
            $Arguments[0] -eq '-C' -and
            $Arguments[2] -eq 'rev-parse' -and
            $Arguments[3] -eq 'HEAD'
        ) {
            Write-Output $env:ANDROID_VOCAB_TEST_GIT_SHA
            return
        }
        if (
            $Arguments.Count -ge 4 -and
            $Arguments[0] -eq '-C' -and
            $Arguments[2] -eq 'status'
        ) {
            if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_VOCAB_TEST_GIT_STATUS)) {
                $env:ANDROID_VOCAB_TEST_GIT_STATUS -split '\|' | Write-Output
            }
            return
        }
        throw "unsupported fake git args: $($Arguments -join ' ')"
    }
    $readinessMetadataPath = Join-AndroidVocabularyPath $testRoot @('build', 'release-readiness', 'release-readiness-run.txt')

    try {
        & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'verify-release-readiness.ps1')) `
            -GitHubActionsEvidencePath $githubEvidencePath `
            -ReleaseArtifactsEvidencePath $releaseArtifactsPath `
            -ApkSmokeEvidencePath $apkSmokePath `
            -DeviceVerificationEvidencePath $deviceCompletedPath

        if (-not (Test-Path -LiteralPath $readinessMetadataPath)) {
            throw "Release readiness metadata was not written: $readinessMetadataPath"
        }
        Assert-UniqueKeyValueMetadata -Path $readinessMetadataPath
        $metadata = Get-Content -LiteralPath $readinessMetadataPath
        foreach ($line in @(
                'runStatus=completed',
                'failedRequirementNames=<none>',
                'gitWorktreeStatus=clean',
                'gitWorktreeDirtyCount=0',
                'gitWorktreeDirtyEntries=<clean>',
                'gitWorktreeRemediation=<none>',
                'gitWorktreeClean=passed',
                'blockerSummary=<none>',
                'blockerActionSummary=<none>',
                'releaseArtifactsRemediation=<none>',
                'releaseArtifactsStaleCurrentVersionArtifacts=<none>',
                'apkSmokeRemediation=<none>',
                'deviceVerificationRemediation=<none>',
                'deviceBundleSmokeArchiveRemediation=<none>',
                'githubActionsRemediation=<none>',
                'releaseArtifacts=passed',
                'releaseApkSmoke=passed',
                'deviceAabVerification=passed',
                'deviceBundleSmokeArchive=passed',
                'githubActionsEvidence=passed'
            )) {
            Assert-ContainsPattern -Lines $metadata -Pattern "^$([regex]::Escape($line))"
        }
        foreach ($archiveName in @(
                'evidence-release-artifacts-run.txt',
                'evidence-smoke-release-apk-run.txt',
                'evidence-verification-completed.txt',
                'evidence-smoke-release-bundle-run.txt',
                'evidence-github-actions.txt'
            )) {
            if (-not (Test-Path -LiteralPath (Join-AndroidVocabularyPath $testRoot @('build', 'release-readiness', $archiveName)))) {
                throw "Release readiness evidence archive was not written: $archiveName"
            }
        }

        $staleDeviceDir = Join-AndroidVocabularyPath $testRoot @('build', 'device-verification', 'v0.1.0-1-stale')
        New-Item -ItemType Directory -Force -Path $staleDeviceDir | Out-Null
        Write-Utf8NoBomLines -Path (Join-Path $staleDeviceDir 'verification-completed.txt') -Lines @(
            'runStatus=completed',
            'versionName=0.1.0',
            'versionCode=1',
            'usesLocalOnlyReleaseOverrides=False',
            'bundlePathMatchesCurrentVersionedName=True',
            'bundleSha256=0000000000000000000000000000000000000000000000000000000000000000',
            'bundleSmokeRunMetadata=smoke-release-bundle-run.txt',
            'bundleSmokeRunStatus=completed',
            'bundleSmokeBundleSha256=0000000000000000000000000000000000000000000000000000000000000000',
            'bundleSmokeBundlePathMatchesCurrentVersionedName=True',
            'bundleSmokeReleaseReady=True',
            'bundleSmokeApkSetSigning=release-signing'
        )

        & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'verify-release-readiness.ps1')) `
            -GitHubActionsEvidencePath $githubEvidencePath `
            -ReleaseArtifactsEvidencePath $releaseArtifactsPath `
            -ApkSmokeEvidencePath $apkSmokePath

        Assert-UniqueKeyValueMetadata -Path $readinessMetadataPath
        $metadata = Get-Content -LiteralPath $readinessMetadataPath
        foreach ($line in @(
                'runStatus=completed',
                'failedRequirementNames=<none>',
                'deviceVerificationEvidenceMode=latestMatching',
                'deviceVerificationEvidenceInput=<latestMatching>',
                "deviceVerificationMetadata=$deviceCompletedPath",
                'deviceAabVerification=passed',
                'deviceBundleSmokeArchive=passed'
            )) {
            Assert-ContainsPattern -Lines $metadata -Pattern "^$([regex]::Escape($line))"
        }

        $staleArtifactNames = 'AndroidVocabulary-release-v0.1.0-1.aab, AndroidVocabulary-release-v0.1.0-1.apk'
        Write-Utf8NoBomLines -Path $releaseArtifactsPath -Lines @(
            'runStatus=failed',
            'versionName=0.1.0',
            'versionCode=1',
            "releaseApk=$releaseApk",
            'releaseApkStatus=failed',
            "releaseApkSha256=$releaseApkSha256",
            "releaseBundle=$releaseBundle",
            'releaseBundleStatus=failed',
            "releaseBundleSha256=$releaseBundleSha256",
            "staleCurrentVersionArtifacts=$staleArtifactNames",
            'releaseArtifactsRemediation=Build signed APK/AAB without -AllowUnsigned, then run scripts/test/verify-release-artifacts.ps1.'
        )
        Invoke-ExpectedFailure `
            -ExpectedPattern 'releaseArtifacts=failed' `
            -Command {
                & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'verify-release-readiness.ps1')) `
                    -GitHubActionsEvidencePath $githubEvidencePath `
                    -ReleaseArtifactsEvidencePath $releaseArtifactsPath `
                    -ApkSmokeEvidencePath $apkSmokePath `
                    -DeviceVerificationEvidencePath $deviceCompletedPath
            }

        Assert-UniqueKeyValueMetadata -Path $readinessMetadataPath
        $metadata = Get-Content -LiteralPath $readinessMetadataPath
        foreach ($line in @(
                'runStatus=failed',
                'failedRequirementNames=releaseArtifacts',
                "releaseArtifactsStaleCurrentVersionArtifacts=$staleArtifactNames",
                "blockerSummary=release artifacts stale: $staleArtifactNames",
                'blockerActionSummary=replace stale release-named APK/AAB with a signed release build or archive them outside dist'
            )) {
            Assert-ContainsPattern -Lines $metadata -Pattern "^$([regex]::Escape($line))"
        }
        Assert-ContainsPattern `
            -Lines $metadata `
            -Pattern "^releaseArtifacts=failed\|.*staleCurrentVersionArtifacts=$([regex]::Escape($staleArtifactNames));remediation="
        Assert-ContainsPattern `
            -Lines $metadata `
            -Pattern "^releaseArtifactsRemediation=.*Stale current-version release artifacts: $([regex]::Escape($staleArtifactNames))\."

        Write-Utf8NoBomLines -Path $releaseArtifactsPath -Lines $successfulReleaseArtifactsMetadata

        Write-Utf8NoBomLines -Path $releaseArtifactsPath -Lines @(
            'runStatus=failed',
            'versionName=0.1.0',
            'versionCode=1',
            "releaseApk=$releaseApk",
            'releaseApkStatus=failed',
            "releaseApkSha256=$releaseApkSha256",
            "releaseBundle=$releaseBundle",
            'releaseBundleStatus=failed',
            "releaseBundleSha256=$releaseBundleSha256",
            'staleCurrentVersionArtifacts=<none>',
            'releaseArtifactsRemediation=Build signed APK/AAB without -AllowUnsigned, then run scripts/test/verify-release-artifacts.ps1.'
        )
        Write-Utf8NoBomLines -Path $apkSmokePath -Lines @(
            'runStatus=failed',
            'versionName=0.1.0',
            'versionCode=1',
            'skipInstall=False',
            'apkPathMatchesCurrentVersionedName=False',
            'apkSha256=0000000000000000000000000000000000000000000000000000000000000000'
        )
        Write-Utf8NoBomLines -Path $deviceCompletedPath -Lines @(
            'runStatus=failed',
            'versionName=0.1.0',
            'versionCode=1',
            'usesLocalOnlyReleaseOverrides=False',
            'bundlePathMatchesCurrentVersionedName=False',
            'bundleSha256=0000000000000000000000000000000000000000000000000000000000000000',
            'bundleSmokeRunMetadata=smoke-release-bundle-run.txt',
            'bundleSmokeRunStatus=failed',
            'bundleSmokeBundleSha256=0000000000000000000000000000000000000000000000000000000000000000',
            'bundleSmokeBundlePathMatchesCurrentVersionedName=False',
            'bundleSmokeReleaseReady=False',
            'bundleSmokeApkSetSigning=debug-signing'
        )
        Write-Utf8NoBomLines -Path $bundleSmokePath -Lines @(
            'runStatus=failed',
            'versionName=0.1.0',
            'versionCode=1',
            'bundlePathMatchesCurrentVersionedName=False',
            'bundleSmokeReleaseReady=False',
            'apkSetSigning=debug-signing',
            'bundleSha256=0000000000000000000000000000000000000000000000000000000000000000'
        )
        Invoke-ExpectedFailure `
            -ExpectedPattern 'releaseArtifacts=failed' `
            -Command {
                & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'verify-release-readiness.ps1')) `
                    -GitHubActionsEvidencePath $githubEvidencePath `
                    -ReleaseArtifactsEvidencePath $releaseArtifactsPath `
                    -ApkSmokeEvidencePath $apkSmokePath `
                    -DeviceVerificationEvidencePath $deviceCompletedPath
            }

        Assert-UniqueKeyValueMetadata -Path $readinessMetadataPath
        $metadata = Get-Content -LiteralPath $readinessMetadataPath
        foreach ($line in @(
                'runStatus=failed',
                'failedRequirementNames=releaseArtifacts, releaseApkSmoke, deviceAabVerification, deviceBundleSmokeArchive',
                'blockerSummary=release artifacts missing, unsigned, or mismatched; release APK smoke evidence does not match current release APK; device AAB verification does not match current release bundle; device bundle smoke archive does not match current release bundle',
                'blockerActionSummary=build signed release APK/AAB and rerun verify-release-artifacts.ps1; after release artifacts pass, rerun smoke-release-apk.ps1 against the current signed release APK; after release artifacts pass, rerun verify-device.ps1 with the current release AAB and keep verification-completed.txt; after verify-device.ps1 passes, keep smoke-release-bundle-run.txt beside the matching device verification archive'
            )) {
            Assert-ContainsPattern -Lines $metadata -Pattern "^$([regex]::Escape($line))"
        }

        Write-Utf8NoBomLines -Path $releaseArtifactsPath -Lines $successfulReleaseArtifactsMetadata
        Write-Utf8NoBomLines -Path $apkSmokePath -Lines $successfulApkSmokeMetadata
        Write-Utf8NoBomLines -Path $deviceCompletedPath -Lines $successfulDeviceVerificationMetadata
        Write-Utf8NoBomLines -Path $bundleSmokePath -Lines $successfulBundleSmokeMetadata
        Write-Utf8NoBomLines -Path $githubEvidencePath -Lines @(
            'workflow=Android',
            'conclusion=success',
            "commitSha=$fakeCommitSha",
            'serverUrl=https://github.com',
            'repository=zzz/other-repo',
            'runId=123456789',
            'runAttempt=2',
            'runUrl=https://github.com/zzz/android-vocab/actions/runs/123456789',
            "checks=$((Get-AndroidVocabularyRequiredGitHubActionsChecks) -join ', ')"
        )
        Invoke-ExpectedFailure `
            -ExpectedPattern 'githubActionsEvidence=failed' `
            -Command {
                & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'verify-release-readiness.ps1')) `
                    -GitHubActionsEvidencePath $githubEvidencePath `
                    -ReleaseArtifactsEvidencePath $releaseArtifactsPath `
                    -ApkSmokeEvidencePath $apkSmokePath `
                    -DeviceVerificationEvidencePath $deviceCompletedPath
            }

        Assert-UniqueKeyValueMetadata -Path $readinessMetadataPath
        $metadata = Get-Content -LiteralPath $readinessMetadataPath
        foreach ($line in @(
                'runStatus=failed',
                'failedRequirementNames=githubActionsEvidence',
                'blockerSummary=GitHub Actions evidence does not match current HEAD',
                'blockerActionSummary=download github-actions-release-evidence for the current HEAD',
                'githubActionsServerUrl=https://github.com',
                'githubActionsRepository=zzz/other-repo',
                'githubActionsEvidence=failed'
            )) {
            Assert-ContainsPattern -Lines $metadata -Pattern "^$([regex]::Escape($line))"
        }

        Write-Utf8NoBomLines -Path $githubEvidencePath -Lines @(
            'workflow=Android',
            'conclusion=success',
            "commitSha=$fakeCommitSha",
            'serverUrl=https://github.com',
            'repository=zzz/android-vocab',
            'runId=123456789',
            'runAttempt=2',
            'runUrl=https://github.com/zzz/android-vocab/actions/runs/123456789',
            "checks=$((Get-AndroidVocabularyRequiredGitHubActionsChecks) -join ', ')"
        )

        $env:ANDROID_VOCAB_TEST_GIT_STATUS = ' M README.md|?? app/proguard-rules.pro'
        Invoke-ExpectedFailure `
            -ExpectedPattern 'gitWorktreeClean=failed' `
            -Command {
                & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'verify-release-readiness.ps1')) `
                    -GitHubActionsEvidencePath $githubEvidencePath `
                    -ReleaseArtifactsEvidencePath $releaseArtifactsPath `
                    -ApkSmokeEvidencePath $apkSmokePath `
                    -DeviceVerificationEvidencePath $deviceCompletedPath
            }
        $env:ANDROID_VOCAB_TEST_GIT_STATUS = ''

        Assert-UniqueKeyValueMetadata -Path $readinessMetadataPath
        $metadata = Get-Content -LiteralPath $readinessMetadataPath
        foreach ($line in @(
                'runStatus=failed',
                'failedRequirementNames=gitWorktreeClean',
                'blockerSummary=git worktree dirty (2 entries)',
                'blockerActionSummary=clean or stash the git worktree before final readiness',
                'gitWorktreeStatus=dirty',
                'gitWorktreeDirtyCount=2',
                'gitWorktreeRemediation=Commit or stash',
                'gitWorktreeClean=failed'
            )) {
            Assert-ContainsPattern -Lines $metadata -Pattern "^$([regex]::Escape($line))"
        }
    } finally {
        if ($null -eq $oldGitFunction) {
            Remove-Item -LiteralPath function:\git -Force -ErrorAction SilentlyContinue
        } else {
            Set-Item -LiteralPath function:\git -Value $oldGitFunction.ScriptBlock
        }
        $env:ANDROID_VOCAB_TEST_GIT_SHA = $oldFakeGitSha
        $env:ANDROID_VOCAB_TEST_GIT_STATUS = $oldFakeGitStatus
    }
}

function Remove-TestRootSafely {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $resolvedParent = (Resolve-Path -LiteralPath $tmpParent).Path
    $isAllowed =
        $resolvedPath -eq $resolvedParent -or
        $resolvedPath.StartsWith(
            $resolvedParent + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase
        )
    if (-not $isAllowed) {
        throw "Refusing to remove test path outside build tmp: $resolvedPath"
    }
    Remove-Item -LiteralPath $resolvedPath -Recurse -Force
}

function Copy-TestScriptRepo {
    param(
        [string]$TargetRoot,
        [string[]]$TestScripts
    )

    New-Item -ItemType Directory -Force -Path (Join-AndroidVocabularyPath $TargetRoot @('scripts', 'lib')) | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-AndroidVocabularyPath $TargetRoot @('scripts', 'test')) | Out-Null
    Copy-Item `
        -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'lib', 'android-env.ps1')) `
        -Destination (Join-AndroidVocabularyPath $TargetRoot @('scripts', 'lib', 'android-env.ps1'))

    foreach ($scriptName in $TestScripts) {
        Copy-Item `
            -LiteralPath (Join-AndroidVocabularyPath $repoRoot @('scripts', 'test', $scriptName)) `
            -Destination (Join-AndroidVocabularyPath $TargetRoot @('scripts', 'test', $scriptName))
    }
}

function New-FakeAndroidSdk {
    param([string]$TargetRoot)

    $platformTools = Join-Path $TargetRoot 'platform-tools'
    New-Item -ItemType Directory -Force -Path $platformTools | Out-Null
    $adbBatchPath = Join-Path $platformTools 'adb.bat'
    Set-Content `
        -LiteralPath $adbBatchPath `
        -Encoding Ascii `
        -Value @(
            '@echo off',
            'if "%1"=="devices" (',
            '  echo List of devices attached',
            '  echo.',
            '  exit /b 0',
            ')',
            'echo fake adb received: %*',
            'exit /b 0'
        )
    $adbShellPath = Join-Path $platformTools 'adb'
    Set-Content `
        -LiteralPath $adbShellPath `
        -Encoding Ascii `
        -Value @(
            '#!/usr/bin/env sh',
            'if [ "$1" = "devices" ]; then',
            '  printf "List of devices attached\n\n"',
            '  exit 0',
            'fi',
            'printf "fake adb received: %s\n" "$*"',
            'exit 0'
        )
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
        & chmod +x $adbShellPath
    }
    return $TargetRoot
}

function New-FakeBundleToolJar {
    param([string]$TargetRoot)

    New-Item -ItemType Directory -Force -Path $TargetRoot | Out-Null
    $bundleToolJar = Join-Path $TargetRoot 'bundletool.jar'
    Set-Content -LiteralPath $bundleToolJar -Encoding Ascii -Value 'placeholder'
    return $bundleToolJar
}

function Invoke-ExpectedFailure {
    param(
        [scriptblock]$Command,
        [string]$ExpectedPattern
    )

    try {
        & $Command 2>&1 | Out-String | Out-Null
        throw 'Expected command to fail.'
    } catch {
        if ($_.Exception.Message -notmatch $ExpectedPattern) {
            throw "Unexpected failure message: $($_.Exception.Message)"
        }
    }
}

function Test-BundleSmokeMetadataFallback {
    param([string]$BundleToolJar)

    $testRoot = Join-Path $tmpRoot 'bundle-smoke-metadata-fallback'
    Remove-TestRootSafely $testRoot
    Copy-TestScriptRepo -TargetRoot $testRoot -TestScripts @('smoke-release-bundle.ps1')

    $oldBundleToolJar = $env:BUNDLETOOL_JAR
    $env:BUNDLETOOL_JAR = $BundleToolJar
    try {
        Invoke-ExpectedFailure `
            -ExpectedPattern 'Cannot find path|No AAB found' `
            -Command {
                & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'smoke-release-bundle.ps1')) `
                    -BuildOnly `
                    -BundlePath (Join-Path $testRoot 'missing.aab')
            }
    } finally {
        $env:BUNDLETOOL_JAR = $oldBundleToolJar
    }

    $metadataPath = Join-AndroidVocabularyPath $testRoot @('build', 'bundle-smoke', 'smoke-release-bundle-run.txt')
    if (-not (Test-Path -LiteralPath $metadataPath)) {
        throw "Bundle smoke metadata was not written: $metadataPath"
    }
    $metadata = Get-Content -LiteralPath $metadataPath
    foreach ($line in @(
            'runStatus=failed',
            'failureStage=resolveBundle',
            'versionName=<unresolved>',
            'versionCode=<unresolved>',
            'bundlePathExpectedName=<unresolved>',
            'bundlePathMatchesCurrentVersionedName=<unknown>',
            'bundleSha256=<unresolved>'
        )) {
        Assert-ContainsLine -Lines $metadata -ExpectedLine $line
    }
}

function Test-ApkSmokeMetadataFallback {
    param([string]$FakeAndroidSdk)

    $testRoot = Join-Path $tmpRoot 'apk-smoke-metadata-fallback'
    Remove-TestRootSafely $testRoot
    Copy-TestScriptRepo -TargetRoot $testRoot -TestScripts @('smoke-release-apk.ps1')

    $oldAndroidHome = $env:ANDROID_HOME
    $oldAndroidSdkRoot = $env:ANDROID_SDK_ROOT
    $oldPath = $env:Path
    $platformTools = Join-Path $FakeAndroidSdk 'platform-tools'
    $env:ANDROID_HOME = $FakeAndroidSdk
    $env:ANDROID_SDK_ROOT = $FakeAndroidSdk
    $env:Path = "$platformTools$([IO.Path]::PathSeparator)$env:Path"
    try {
        Invoke-ExpectedFailure `
            -ExpectedPattern "Device 'REGRESSION_MISSING_DEVICE' is not online|adb devices failed" `
            -Command {
                & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'smoke-release-apk.ps1')) `
                    -DeviceSerial 'REGRESSION_MISSING_DEVICE'
            }
    } finally {
        $env:ANDROID_HOME = $oldAndroidHome
        $env:ANDROID_SDK_ROOT = $oldAndroidSdkRoot
        $env:Path = $oldPath
    }

    $metadataPath = Join-AndroidVocabularyPath $testRoot @('build', 'apk-smoke', 'smoke-release-apk-run.txt')
    if (-not (Test-Path -LiteralPath $metadataPath)) {
        throw "APK smoke metadata was not written: $metadataPath"
    }
    $metadata = Get-Content -LiteralPath $metadataPath
    foreach ($line in @(
            'runStatus=failed',
            'versionName=<unresolved>',
            'versionCode=<unresolved>',
            'apkPathExpectedName=<unresolved>',
            'apkPathMatchesCurrentVersionedName=<unknown>',
            'apkSha256=<unresolved>',
            'deviceSerialInput=REGRESSION_MISSING_DEVICE'
        )) {
        Assert-ContainsLine -Lines $metadata -ExpectedLine $line
    }
}

function Test-VerifyDeviceEarlyFailureMetadata {
    param([string]$FakeAndroidSdk)

    $testRoot = Join-Path $tmpRoot 'verify-device-version-metadata-failure'
    Remove-TestRootSafely $testRoot
    Copy-TestScriptRepo -TargetRoot $testRoot -TestScripts @('verify-device.ps1')

    $oldAndroidHome = $env:ANDROID_HOME
    $oldAndroidSdkRoot = $env:ANDROID_SDK_ROOT
    $oldPath = $env:Path
    $platformTools = Join-Path $FakeAndroidSdk 'platform-tools'
    $env:ANDROID_HOME = $FakeAndroidSdk
    $env:ANDROID_SDK_ROOT = $FakeAndroidSdk
    $env:Path = "$platformTools$([IO.Path]::PathSeparator)$env:Path"
    try {
        Invoke-ExpectedFailure `
            -ExpectedPattern 'Gradle property not found|Cannot find path' `
            -Command {
                & (Join-AndroidVocabularyPath $testRoot @('scripts', 'test', 'verify-device.ps1')) `
                    -DeviceSerial 'REGRESSION_DEVICE' `
                    -SkipConnectedTests `
                    -SkipBundleSmoke
            }
    } finally {
        $env:ANDROID_HOME = $oldAndroidHome
        $env:ANDROID_SDK_ROOT = $oldAndroidSdkRoot
        $env:Path = $oldPath
    }

    $failedFiles = @(
        Get-ChildItem `
            -Path (Join-AndroidVocabularyPath $testRoot @('build', 'device-verification')) `
            -Filter 'verification-failed.txt' `
            -Recurse `
            -File
    )
    if ($failedFiles.Count -ne 1) {
        throw "Expected one verification-failed.txt, found $($failedFiles.Count)."
    }
    $metadata = Get-Content -LiteralPath $failedFiles[0].FullName
    foreach ($line in @(
            'runStatus=failed',
            'failureStage=versionMetadata',
            'versionName=<unresolved>',
            'versionCode=<unresolved>',
            'deviceSerial=<unresolved>',
            'bundlePath=<not used>',
            'bundlePathExpectedName=<unresolved>',
            'bundlePathMatchesCurrentVersionedName=<not used>',
            'bundleSha256=<not used>',
            'bundleSmokeRunStatus=<not used>',
            'bundleSmokeBundleSha256=<not used>',
            'bundleSmokeBundlePathMatchesCurrentVersionedName=<not used>',
            'bundleSmokeReleaseReady=<not used>'
        )) {
        Assert-ContainsLine -Lines $metadata -ExpectedLine $line
    }
}

New-Item -ItemType Directory -Force -Path $tmpParent | Out-Null
Remove-TestRootSafely $tmpRoot

try {
    Use-AndroidVocabularyJavaHome
    $bundleToolJar = New-FakeBundleToolJar (Join-Path $tmpRoot 'fake-bundletool')
    $fakeAndroidSdk = New-FakeAndroidSdk (Join-Path $tmpRoot 'fake-android-sdk')

    Test-PowerShellParse
    Test-ReleaseManifestBackupPolicy
    Test-ReleaseArtifactNaming
    Test-ReleaseReadinessMetadataKeys
    Test-ReleaseReadinessDocumentation
    Test-ReleaseReadinessPositiveFixture
    Test-VerifyDeviceFailureStageCases
    Test-BundleSmokeFailureStageCases
    Test-BundleSmokeMetadataFallback -BundleToolJar $bundleToolJar
    Test-ApkSmokeMetadataFallback -FakeAndroidSdk $fakeAndroidSdk
    Test-VerifyDeviceEarlyFailureMetadata -FakeAndroidSdk $fakeAndroidSdk
    Test-GitHubActionsEvidenceWriter
} finally {
    Remove-TestRootSafely $tmpRoot
}

Write-Host '[ok] release script regression checks passed'
