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
    $count = (Get-Content -Raw $filePath | ConvertFrom-Json).Count
    if ($count -ne $expected[$book].count) {
        throw "$book count mismatch: expected $($expected[$book].count), actual $count"
    }
}

if (-not (Test-Path (Join-Path $outputDir 'sources.json'))) {
    throw 'Missing sources.json'
}

$manifest = [ordered]@{
    buildTarget = 'publish'
    generatedAt = (Get-Date -Format 'yyyy-MM-dd')
    books = $expected
}

Write-Utf8NoBom -Path (Join-Path $outputDir 'manifest.json') -Value ($manifest | ConvertTo-Json -Depth 8)
Write-Host '[ok] publish-safe vocabulary assets built and verified'
