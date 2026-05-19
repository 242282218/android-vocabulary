param(
    [string]$BundlePath,
    [string]$BundleToolJar,
    [string]$DeviceSerial,
    [string]$ApksPath,
    [switch]$BuildOnly,
    [switch]$ResetAppData,
    [switch]$GrantNotificationPermission,
    [switch]$AllowDebugSigning,
    [switch]$ListDevices
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\lib\android-env.ps1')
$repoRoot = Get-AndroidVocabularyRepoRoot

function Get-DefaultBundle {
    $candidates =
        @(
            (Join-Path $repoRoot 'dist'),
            (Join-Path $repoRoot 'app\build\outputs\bundle\release')
        ) |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem -Path $_ -Filter '*.aab' -File } |
        Sort-Object LastWriteTime -Descending

    if ($candidates.Count -eq 0) {
        throw 'No AAB found. Pass -BundlePath, or run scripts/release/build-bundle.ps1 first.'
    }
    return $candidates[0].FullName
}

function Add-BundleToolSigningArgs {
    param([string[]]$InputArgs)

    $missingSigningVars = @(Test-ReleaseSigningEnvironment)
    if ($missingSigningVars.Count -gt 0) {
        $message = "Release signing is not configured. Missing: $($missingSigningVars -join ', ')."
        if (-not $AllowDebugSigning) {
            throw "$message Set the required environment variables, or rerun with -AllowDebugSigning for local-only bundletool output."
        }
        Write-Warning "$message Continuing because -AllowDebugSigning was set."
        return $InputArgs
    }

    $script:SecretDir = Join-Path $script:WorkDir 'secrets'
    New-Item -ItemType Directory -Force -Path $script:SecretDir | Out-Null
    $storePassFile = Join-Path $script:SecretDir 'store-pass.txt'
    $keyPassFile = Join-Path $script:SecretDir 'key-pass.txt'
    Set-Content -LiteralPath $storePassFile -Value $env:ANDROID_VOCAB_RELEASE_STORE_PASSWORD -NoNewline -Encoding Ascii
    Set-Content -LiteralPath $keyPassFile -Value $env:ANDROID_VOCAB_RELEASE_KEY_PASSWORD -NoNewline -Encoding Ascii

    return $InputArgs + @(
        "--ks=$env:ANDROID_VOCAB_RELEASE_STORE_FILE",
        "--ks-key-alias=$env:ANDROID_VOCAB_RELEASE_KEY_ALIAS",
        "--ks-pass=file:$storePassFile",
        "--key-pass=file:$keyPassFile"
    )
}

if ($ListDevices) {
    & (Join-Path $repoRoot 'scripts\test\smoke-release-apk.ps1') -ListDevices
    exit $LASTEXITCODE
}

Use-AndroidVocabularyJavaHome
$bundleTool = Get-BundleToolJar $BundleToolJar
$resolvedBundlePath =
    if ([string]::IsNullOrWhiteSpace($BundlePath)) {
        Get-DefaultBundle
    } else {
        (Resolve-Path -LiteralPath $BundlePath).Path
    }

$script:WorkDir = Join-Path $repoRoot 'build\bundle-smoke'
New-Item -ItemType Directory -Force -Path $script:WorkDir | Out-Null

$resolvedApksPath =
    if ([string]::IsNullOrWhiteSpace($ApksPath)) {
        Join-Path $script:WorkDir "$([IO.Path]::GetFileNameWithoutExtension($resolvedBundlePath)).apks"
    } else {
        $ApksPath
    }

$buildApksArgs = @(
    'build-apks',
    "--bundle=$resolvedBundlePath",
    "--output=$resolvedApksPath",
    '--overwrite'
)

if ($BuildOnly) {
    $buildApksArgs += '--mode=universal'
} else {
    $buildApksArgs += '--connected-device'
    if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
        $buildApksArgs += "--device-id=$DeviceSerial"
    }
}

$script:SecretDir = $null
try {
    $buildApksArgs = Add-BundleToolSigningArgs -InputArgs $buildApksArgs
    Write-Host "[info] bundletool build-apks: $resolvedBundlePath"
    & (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar $bundleTool @buildApksArgs
    if ($LASTEXITCODE -ne 0) {
        throw "bundletool build-apks failed with exit code $LASTEXITCODE"
    }
} finally {
    if (-not [string]::IsNullOrWhiteSpace($script:SecretDir)) {
        Remove-Item -LiteralPath $script:SecretDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "[ok] APK Set: $resolvedApksPath"

if ($BuildOnly) {
    Write-Host '[ok] bundle smoke build-only passed'
    exit 0
}

$installArgs = @('install-apks', "--apks=$resolvedApksPath")
if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $installArgs += "--device-id=$DeviceSerial"
}

Write-Host '[info] bundletool install-apks'
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar $bundleTool @installArgs
if ($LASTEXITCODE -ne 0) {
    throw "bundletool install-apks failed with exit code $LASTEXITCODE"
}

$smokeArgs = @('-SkipInstall')
if (-not [string]::IsNullOrWhiteSpace($DeviceSerial)) {
    $smokeArgs += @('-DeviceSerial', $DeviceSerial)
}
if ($ResetAppData) {
    $smokeArgs += '-ResetAppData'
}
if ($GrantNotificationPermission) {
    $smokeArgs += '-GrantNotificationPermission'
}

& (Join-Path $repoRoot 'scripts\test\smoke-release-apk.ps1') @smokeArgs
if ($LASTEXITCODE -ne 0) {
    throw "release APK smoke after bundle install failed with exit code $LASTEXITCODE"
}

Write-Host '[ok] release AAB smoke test passed'
