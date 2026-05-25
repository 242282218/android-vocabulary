$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path (Join-Path $PSScriptRoot '..') '..')).Path
$assetDir = Join-Path (Join-Path (Join-Path (Join-Path (Join-Path $repoRoot 'app') 'src') 'main') 'assets') 'vocab'

function Read-Utf8Text {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

$manifest = Read-Utf8Text -Path (Join-Path $assetDir 'manifest.json') | ConvertFrom-Json

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

if ($manifest.buildTarget -ne 'publish') {
    throw "Expected publish manifest, actual: $($manifest.buildTarget)"
}

if ([string]::IsNullOrWhiteSpace($manifest.assetFingerprint)) {
    throw 'manifest assetFingerprint is missing'
}

if ([string]::IsNullOrWhiteSpace($manifest.sourcesHash)) {
    throw 'manifest sourcesHash is missing'
}

$sourcesPath = Join-Path $assetDir 'sources.json'
if (-not (Test-Path $sourcesPath)) {
    throw 'sources.json is missing'
}

$sourcesText = Read-Utf8Text -Path $sourcesPath
try {
    $sourcesManifest = $sourcesText | ConvertFrom-Json
} catch {
    throw "sources.json is invalid JSON: $($_.Exception.Message)"
}

if ([string]::IsNullOrWhiteSpace($sourcesManifest.generatedAt)) {
    throw 'sources.json generatedAt is missing'
}

$sources = @($sourcesManifest.sources)
if ($sources.Count -eq 0) {
    throw 'sources.json sources are missing'
}

foreach ($source in $sources) {
    foreach ($field in @('name', 'url', 'license', 'licenseStatus', 'redistributable', 'publishBlocking', 'notes')) {
        if ($null -eq $source.PSObject.Properties[$field]) {
            throw "sources.json source field is missing: $field"
        }
    }
}

$blockingSources = @($sources | Where-Object { $_.publishBlocking -eq $true })
$blockingSourceNames = @($blockingSources | ForEach-Object { $_.name })
foreach ($requiredBlockingSource in @('KyleBing/english-vocabulary', 'exam-data/NETEMVocabulary')) {
    if ($blockingSourceNames -notcontains $requiredBlockingSource) {
        throw "sources.json missing publish-blocking source metadata: $requiredBlockingSource"
    }
}
foreach ($blockingSource in $blockingSources) {
    if ($blockingSource.redistributable -eq $true) {
        throw "publish-blocking source must not be marked redistributable: $($blockingSource.name)"
    }
    if ([string]::IsNullOrWhiteSpace($blockingSource.reviewAction)) {
        throw "publish-blocking source reviewAction is missing: $($blockingSource.name)"
    }
}

$actualSourcesHash = Get-TextSha256 -Value $sourcesText
if ($actualSourcesHash -ne $manifest.sourcesHash.ToLowerInvariant()) {
    throw "sources hash mismatch: expected $($manifest.sourcesHash), actual $actualSourcesHash"
}

$bookHashes = [ordered]@{}

foreach ($book in @('CET4', 'CET6', 'KAOYAN', 'IELTS', 'TOEFL')) {
    $entry = $manifest.books.$book
    $filePath = Join-Path $assetDir $entry.file
    $actualHash = Get-FileSha256 -Path $filePath
    if ([string]::IsNullOrWhiteSpace($entry.hash)) {
        throw "$book manifest hash is missing"
    }
    if ($actualHash -ne $entry.hash.ToLowerInvariant()) {
        throw "$book hash mismatch: expected $($entry.hash), actual $actualHash"
    }
    $bookHashes[$book] = $actualHash
    $rows = Read-Utf8Text -Path $filePath | ConvertFrom-Json
    if ($rows.Count -ne $entry.count) {
        throw "$book count mismatch: expected $($entry.count), actual $($rows.Count)"
    }
    $blocking = $rows | Where-Object {
        $flags = @($_.sourceFlags | ForEach-Object { "$_".Trim().ToLowerInvariant() })
        $flags -contains 'kylebing' -or $flags -contains 'netem'
    } | Select-Object -First 1
    if ($null -ne $blocking) {
        throw "$book contains publish-blocking source flag: $($blocking.word)"
    }
}

$fingerprintInput = (($bookHashes.Keys | ForEach-Object { "$_=$($bookHashes[$_])" }) -join ';') + ";sources=$actualSourcesHash"
$actualFingerprint = Get-TextSha256 -Value $fingerprintInput
if ($actualFingerprint -ne $manifest.assetFingerprint.ToLowerInvariant()) {
    throw "asset fingerprint mismatch: expected $($manifest.assetFingerprint), actual $actualFingerprint"
}

Write-Host '[ok] vocabulary assets are publish-safe'
