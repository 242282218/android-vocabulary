$ErrorActionPreference = 'Stop'

function Get-AndroidVocabularyRepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}

function Test-JavaHome {
    param([string]$Path)

    return -not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path (Join-Path $Path 'bin\java.exe'))
}

function Use-JavaHome {
    param([string]$Path)

    $env:JAVA_HOME = $Path
    $env:Path = "$(Join-Path $Path 'bin');$env:Path"
    Write-Host "[info] JAVA_HOME=$env:JAVA_HOME"
}

function Use-AndroidVocabularyJavaHome {
    $repoRoot = Get-AndroidVocabularyRepoRoot
    if (Test-JavaHome $env:JAVA_HOME) {
        Use-JavaHome $env:JAVA_HOME
        return
    }

    $candidates = @(
        (Join-Path $repoRoot '.tools\jdk-17'),
        'D:\AndroidVocabularyTools\jdk-17',
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Java\jdk-17',
        'C:\Program Files\Eclipse Adoptium\jdk-17'
    )
    $javaHome = $candidates | Where-Object { Test-JavaHome $_ } | Select-Object -First 1
    if ($null -eq $javaHome) {
        throw 'JAVA_HOME is not set and no known JDK 17 installation was found.'
    }
    Use-JavaHome $javaHome
}

function Get-AndroidSdkDir {
    $repoRoot = Get-AndroidVocabularyRepoRoot
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidates += $env:ANDROID_HOME
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidates += $env:ANDROID_SDK_ROOT
    }
    $candidates += Join-Path $repoRoot '.tools\android-sdk'

    $localPropertiesPath = Join-Path $repoRoot 'local.properties'
    if (Test-Path $localPropertiesPath) {
        $sdkLine = Get-Content $localPropertiesPath | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($null -ne $sdkLine) {
            $path = $sdkLine.Substring('sdk.dir='.Length).Replace('\:', ':').Replace('\\', '\')
            $candidates += $path
        }
    }

    return $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}

function Get-CommandPath {
    param([string[]]$Names)

    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $command) {
            return $command.Source
        }
    }
    return $null
}

function Get-AndroidTool {
    param(
        [string[]]$Names,
        [string[]]$RelativeDirs
    )

    $fromPath = Get-CommandPath $Names
    if (-not [string]::IsNullOrWhiteSpace($fromPath)) {
        return $fromPath
    }

    $sdkDir = Get-AndroidSdkDir
    if ([string]::IsNullOrWhiteSpace($sdkDir)) {
        throw 'Android SDK not found. Set ANDROID_HOME or sdk.dir in local.properties.'
    }

    foreach ($relativeDir in $RelativeDirs) {
        $tool =
            Get-ChildItem -Path (Join-Path $sdkDir $relativeDir) -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $Names -contains $_.Name } |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($null -ne $tool) {
            return $tool.FullName
        }
    }

    throw "Android tool not found: $($Names -join ', ')"
}

function Get-JarSigner {
    $jarSigner = Join-Path $env:JAVA_HOME 'bin\jarsigner.exe'
    if (-not (Test-Path $jarSigner)) {
        throw "jarsigner.exe not found under JAVA_HOME: $env:JAVA_HOME"
    }
    return $jarSigner
}

function Get-BundleToolJar {
    param([string]$ExplicitPath)

    $repoRoot = Get-AndroidVocabularyRepoRoot
    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        $candidates += $ExplicitPath
    }
    if (-not [string]::IsNullOrWhiteSpace($env:BUNDLETOOL_JAR)) {
        $candidates += $env:BUNDLETOOL_JAR
    }
    $candidates += Join-Path $repoRoot '.tools\bundletool\bundletool.jar'
    $candidates += Get-ChildItem -Path (Join-Path $repoRoot '.tools\bundletool') -Filter 'bundletool*.jar' -File -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        ForEach-Object { $_.FullName }

    $bundleToolJar = $candidates | Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and (Test-Path $_) } | Select-Object -First 1
    if ($null -eq $bundleToolJar) {
        throw 'bundletool.jar not found. Run scripts/tooling/install-bundletool.ps1 or set BUNDLETOOL_JAR.'
    }
    return $bundleToolJar
}

function Test-ReleaseSigningEnvironment {
    $required = @(
        'ANDROID_VOCAB_RELEASE_STORE_FILE',
        'ANDROID_VOCAB_RELEASE_STORE_PASSWORD',
        'ANDROID_VOCAB_RELEASE_KEY_ALIAS',
        'ANDROID_VOCAB_RELEASE_KEY_PASSWORD'
    )
    $missing = $required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) }
    if ($missing.Count -gt 0) {
        return $missing
    }
    if (-not (Test-Path $env:ANDROID_VOCAB_RELEASE_STORE_FILE)) {
        throw "Release keystore does not exist: $env:ANDROID_VOCAB_RELEASE_STORE_FILE"
    }
    return @()
}
