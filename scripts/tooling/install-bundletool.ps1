param(
    [string]$Version = '1.18.3',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..\lib\android-env.ps1')
$repoRoot = Get-AndroidVocabularyRepoRoot
$targetDir = Join-Path $repoRoot '.tools\bundletool'
$jarName = "bundletool-all-$Version.jar"
$target = Join-Path $targetDir $jarName
$stableAlias = Join-Path $targetDir 'bundletool.jar'
$downloadUrl = "https://github.com/google/bundletool/releases/download/$Version/$jarName"

New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

if ((Test-Path $target) -and -not $Force) {
    Write-Host "[ok] bundletool already installed: $target"
} else {
    Write-Host "[info] downloading bundletool $Version"
    Invoke-WebRequest -Uri $downloadUrl -OutFile $target
    Write-Host "[ok] downloaded: $target"
}

Copy-Item -LiteralPath $target -Destination $stableAlias -Force
$env:BUNDLETOOL_JAR = $stableAlias
Write-Host "[ok] BUNDLETOOL_JAR=$stableAlias"
