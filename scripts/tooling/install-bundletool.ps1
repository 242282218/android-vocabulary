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
$expectedSha256ByVersion = @{
    '1.18.3' = 'a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29'
}

function Assert-BundletoolChecksum {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Version
    )

    if (-not $expectedSha256ByVersion.ContainsKey($Version)) {
        throw "No bundletool SHA-256 is pinned for version $Version. Add it before installing a new version."
    }

    $actual = (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
    $expected = $expectedSha256ByVersion[$Version]
    if ($actual -ne $expected) {
        Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
        throw "bundletool SHA-256 mismatch for $Version. Expected $expected, actual $actual."
    }
}

New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

if ((Test-Path $target) -and -not $Force) {
    Write-Host "[ok] bundletool already installed: $target"
} else {
    Write-Host "[info] downloading bundletool $Version"
    Invoke-WebRequest -Uri $downloadUrl -OutFile $target
    Assert-BundletoolChecksum -Path $target -Version $Version
    Write-Host "[ok] downloaded: $target"
}

Assert-BundletoolChecksum -Path $target -Version $Version
Copy-Item -LiteralPath $target -Destination $stableAlias -Force
$env:BUNDLETOOL_JAR = $stableAlias
Write-Host "[ok] BUNDLETOOL_JAR=$stableAlias"
