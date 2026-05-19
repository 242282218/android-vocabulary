$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$assetDir = Join-Path $repoRoot 'app\src\main\assets\vocab'
$manifest = Get-Content -Raw (Join-Path $assetDir 'manifest.json') | ConvertFrom-Json

if ($manifest.buildTarget -ne 'publish') {
    throw "Expected publish manifest, actual: $($manifest.buildTarget)"
}

foreach ($book in @('CET4', 'CET6', 'KAOYAN', 'IELTS', 'TOEFL')) {
    $entry = $manifest.books.$book
    $rows = Get-Content -Raw (Join-Path $assetDir $entry.file) | ConvertFrom-Json
    if ($rows.Count -ne $entry.count) {
        throw "$book count mismatch: expected $($entry.count), actual $($rows.Count)"
    }
    $blocking = $rows | Where-Object { $_.sourceFlags -contains 'kylebing' -or $_.sourceFlags -contains 'netem' } | Select-Object -First 1
    if ($null -ne $blocking) {
        throw "$book contains publish-blocking source flag: $($blocking.word)"
    }
}

if (-not (Test-Path (Join-Path $assetDir 'sources.json'))) {
    throw 'sources.json is missing'
}

Write-Host '[ok] vocabulary assets are publish-safe'
