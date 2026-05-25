$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$sourceRoot = 'D:\PROJECT_ZZZZZZZZZ\Bilibili-Subtitle-Vocabulary-Extension\bilibili-vocab-extension'
$builder = Join-Path $sourceRoot 'scripts\build-vocab-dataset.js'
$outputDir = Join-Path $repoRoot 'app\src\main\assets\vocab'

function Write-Utf8NoBom {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($Path, $Value, $encoding)
}

function Get-FileSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return Get-TextSha256 -Value (Read-Utf8Text -Path $Path)
}

function Get-TextSha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Value
    )

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $normalized = $Value.Replace("`r`n", "`n").Replace("`r", "`n")
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($normalized)
        return (($sha256.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join '')
    }
    finally {
        $sha256.Dispose()
    }
}

function Read-Utf8Text {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
node $builder --publish-safe --output-dir $outputDir

$expected = [ordered]@{
    CET4 = @{ file = 'cet4.json'; count = 3846 }
    CET6 = @{ file = 'cet6.json'; count = 5406 }
    KAOYAN = @{ file = 'kaoyan.json'; count = 4801 }
    IELTS = @{ file = 'ielts.json'; count = 5038 }
    TOEFL = @{ file = 'toefl.json'; count = 6970 }
}

foreach ($book in $expected.Keys) {
    $filePath = Join-Path $outputDir $expected[$book].file
    if (-not (Test-Path $filePath)) {
        throw "Missing vocabulary file: $filePath"
    }
    $count = (Read-Utf8Text -Path $filePath | ConvertFrom-Json).Count
    if ($count -ne $expected[$book].count) {
        throw "$book count mismatch: expected $($expected[$book].count), actual $count"
    }
    $expected[$book]['hash'] = Get-FileSha256 -Path $filePath
}

$sourcesPath = Join-Path $outputDir 'sources.json'
if (-not (Test-Path $sourcesPath)) {
    throw 'Missing sources.json'
}
$sourcesHash = Get-FileSha256 -Path $sourcesPath
$fingerprintInput = (($expected.Keys | ForEach-Object { "$_=$($expected[$_].hash)" }) -join ';') + ";sources=$sourcesHash"

$manifest = [ordered]@{
    buildTarget = 'publish'
    generatedAt = (Get-Date -Format 'yyyy-MM-dd')
    sourcesHash = $sourcesHash
    assetFingerprint = Get-TextSha256 -Value $fingerprintInput
    books = $expected
}

Write-Utf8NoBom -Path (Join-Path $outputDir 'manifest.json') -Value ($manifest | ConvertTo-Json -Depth 8)
Write-Host '[ok] publish-safe vocabulary assets built and verified'
