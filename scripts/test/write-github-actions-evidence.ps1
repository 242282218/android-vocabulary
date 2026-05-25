param(
    [string]$OutputPath = 'build/release-readiness/github-actions-evidence.txt'
)

$ErrorActionPreference = 'Stop'

. (Join-Path (Join-Path $PSScriptRoot '..') (Join-Path 'lib' 'android-env.ps1'))
$repoRoot = Get-AndroidVocabularyRepoRoot

function Get-RequiredEnvironmentValue {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Name is not set. GitHub Actions evidence must be written from CI."
    }
    return $value
}

function Resolve-OutputPath {
    param([string]$Path)

    if ([IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $repoRoot $Path
}

$workflow = Get-RequiredEnvironmentValue 'GITHUB_WORKFLOW'
$commitSha = Get-RequiredEnvironmentValue 'GITHUB_SHA'
$runId = Get-RequiredEnvironmentValue 'GITHUB_RUN_ID'
$runAttempt = Get-RequiredEnvironmentValue 'GITHUB_RUN_ATTEMPT'
$serverUrl = Get-RequiredEnvironmentValue 'GITHUB_SERVER_URL'
$repository = Get-RequiredEnvironmentValue 'GITHUB_REPOSITORY'
if ($workflow -ne 'Android') {
    throw "Unexpected GitHub workflow: $workflow"
}

$resolvedOutputPath = Resolve-OutputPath $OutputPath
$outputDir = Split-Path -Parent $resolvedOutputPath
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

Set-Content `
    -LiteralPath $resolvedOutputPath `
    -Encoding Ascii `
    -Value @(
        "workflow=$workflow",
        'conclusion=success',
        "commitSha=$commitSha",
        "runId=$runId",
        "runAttempt=$runAttempt",
        "runUrl=$serverUrl/$repository/actions/runs/$runId",
        "checks=$((Get-AndroidVocabularyRequiredGitHubActionsChecks) -join ', ')"
    )

Write-Host "[ok] GitHub Actions evidence written: $resolvedOutputPath"
