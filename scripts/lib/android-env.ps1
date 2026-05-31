$ErrorActionPreference = 'Stop'

function Get-AndroidVocabularyRepoRoot {
    return (Resolve-Path (Join-Path (Join-Path $PSScriptRoot '..') '..')).Path
}

function Join-AndroidVocabularyPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BasePath,
        [Parameter(Mandatory = $true)]
        [string[]]$ChildPath
    )

    $path = $BasePath
    foreach ($child in $ChildPath) {
        $path = Join-Path $path $child
    }
    return $path
}

function Get-GradlePropertyValue {
    param([string]$Name)

    $repoRoot = Get-AndroidVocabularyRepoRoot
    $propertiesPath = Join-Path $repoRoot 'gradle.properties'
    $escapedName = [regex]::Escape($Name)
    $line =
        Get-Content -LiteralPath $propertiesPath |
        Where-Object { $_ -match "^\s*$escapedName\s*=" } |
        Select-Object -First 1
    if ($null -eq $line) {
        throw "Gradle property not found: $Name"
    }
    return $line.Substring($line.IndexOf('=') + 1).Trim()
}

function Get-AndroidVocabularyReleaseApkName {
    $versionName = Get-GradlePropertyValue 'androidVocab.versionName'
    $versionCode = Get-GradlePropertyValue 'androidVocab.versionCode'
    return "AndroidVocabulary-release-v$versionName-$versionCode.apk"
}

function Get-AndroidVocabularyBuildValidationApkName {
    $versionName = Get-GradlePropertyValue 'androidVocab.versionName'
    $versionCode = Get-GradlePropertyValue 'androidVocab.versionCode'
    return "AndroidVocabulary-build-validation-v$versionName-$versionCode.apk"
}

function Get-AndroidVocabularyReleaseBundleName {
    $versionName = Get-GradlePropertyValue 'androidVocab.versionName'
    $versionCode = Get-GradlePropertyValue 'androidVocab.versionCode'
    return "AndroidVocabulary-release-v$versionName-$versionCode.aab"
}

function Get-AndroidVocabularyBuildValidationBundleName {
    $versionName = Get-GradlePropertyValue 'androidVocab.versionName'
    $versionCode = Get-GradlePropertyValue 'androidVocab.versionCode'
    return "AndroidVocabulary-build-validation-v$versionName-$versionCode.aab"
}

function Get-AndroidVocabularyRequiredGitHubActionsChecks {
    return @(
        'verify-release-scripts',
        'verify-vocab-assets',
        'ktlintCheck',
        'detekt',
        'testDebugUnitTest',
        'assembleDebug',
        'assembleDebugAndroidTest',
        'pixel2Api30DebugAndroidTest'
    )
}

function Get-AndroidVocabularyTextTail {
    param(
        [string]$Text,
        [int]$MaxLength = 4000
    )

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return '<no output>'
    }
    if ($Text.Length -le $MaxLength) {
        return $Text
    }
    return "...[truncated]`n$($Text.Substring($Text.Length - $MaxLength))"
}

function ConvertTo-AndroidVocabularyOutputText {
    param(
        [Parameter(ValueFromPipeline = $true)]
        [AllowNull()]
        [object]$InputObject
    )

    process {
        if ($null -eq $InputObject) {
            return ''
        }
        if ($InputObject -is [System.Management.Automation.ErrorRecord]) {
            $message = $InputObject.Exception.Message
            if (-not [string]::IsNullOrWhiteSpace($message)) {
                return $message
            }
            $targetText = "$($InputObject.TargetObject)"
            if (-not [string]::IsNullOrWhiteSpace($targetText)) {
                return $targetText
            }
            return ''
        }
        return $InputObject.ToString()
    }
}

function Invoke-AndroidVocabularyGradle {
    param(
        [string]$Name,
        [string[]]$Tasks,
        [int]$MaxTailLength = 4000
    )

    $gradleWrapper = Get-GradleWrapper
    $arguments = @('--no-daemon', '--console=plain') + $Tasks
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $commandOutput = @()
    try {
        & $gradleWrapper @arguments 2>&1 |
            ConvertTo-AndroidVocabularyOutputText |
            Tee-Object -Variable commandOutput
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        $outputText = @($commandOutput | ConvertTo-AndroidVocabularyOutputText) -join "`n"
        throw "$Name failed with exit code $exitCode.`nLast output:`n$(Get-AndroidVocabularyTextTail -Text $outputText -MaxLength $MaxTailLength)"
    }
}

function Test-JavaHome {
    param([string]$Path)

    return -not [string]::IsNullOrWhiteSpace($Path) -and
        -not [string]::IsNullOrWhiteSpace((Get-JavaToolPath -JavaHome $Path -ToolNames @('java.exe', 'java')))
}

function Use-JavaHome {
    param([string]$Path)

    $env:JAVA_HOME = $Path
    $env:Path = "$(Join-Path $Path 'bin')$([IO.Path]::PathSeparator)$env:Path"
    Write-Host "[info] JAVA_HOME=$env:JAVA_HOME"
}

function Use-AndroidVocabularyJavaHome {
    $repoRoot = Get-AndroidVocabularyRepoRoot
    if (Test-JavaHome $env:JAVA_HOME) {
        Use-JavaHome $env:JAVA_HOME
        return
    }

    $candidates = @(
        (Join-AndroidVocabularyPath $repoRoot @('.tools', 'jdk-17')),
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

    $localPropertiesPath = Join-Path $repoRoot 'local.properties'
    if (Test-Path $localPropertiesPath) {
        $sdkLine = Get-Content $localPropertiesPath | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($null -ne $sdkLine) {
            $path = $sdkLine.Substring('sdk.dir='.Length).Replace('\:', ':').Replace('\\', '\')
            $candidates += $path
        }
    }

    # Prefer explicit SDK roots before the repo-local fallback so emulator/QEMU avoid Unicode path issues.
    $candidates += Join-AndroidVocabularyPath $repoRoot @('.tools', 'android-sdk')

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

function Get-GradleWrapper {
    $repoRoot = Get-AndroidVocabularyRepoRoot
    $candidates =
        if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
            @(
                (Join-Path $repoRoot 'gradlew.bat'),
                (Join-Path $repoRoot 'gradlew')
            )
        } else {
            @(
                (Join-Path $repoRoot 'gradlew'),
                (Join-Path $repoRoot 'gradlew.bat')
            )
        }
    $gradleWrapper = $candidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($gradleWrapper)) {
        throw "Gradle wrapper not found under repo root: $repoRoot"
    }
    return $gradleWrapper
}

function Get-JavaToolPath {
    param(
        [string]$JavaHome,
        [string[]]$ToolNames
    )

    if ([string]::IsNullOrWhiteSpace($JavaHome)) {
        return $null
    }

    $binDir = Join-Path $JavaHome 'bin'
    foreach ($toolName in $ToolNames) {
        $candidate = Join-Path $binDir $toolName
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    return $null
}

function Get-JavaExecutable {
    $java = Get-JavaToolPath -JavaHome $env:JAVA_HOME -ToolNames @('java.exe', 'java')
    if ([string]::IsNullOrWhiteSpace($java)) {
        throw "java executable not found under JAVA_HOME: $env:JAVA_HOME"
    }
    return $java
}

function Get-JarSigner {
    $jarSigner = Get-JavaToolPath -JavaHome $env:JAVA_HOME -ToolNames @('jarsigner.exe', 'jarsigner')
    if ([string]::IsNullOrWhiteSpace($jarSigner)) {
        throw "jarsigner executable not found under JAVA_HOME: $env:JAVA_HOME"
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
    $bundleToolDir = Join-AndroidVocabularyPath $repoRoot @('.tools', 'bundletool')
    $candidates += Join-Path $bundleToolDir 'bundletool.jar'
    $candidates += Get-ChildItem -Path $bundleToolDir -Filter 'bundletool*.jar' -File -ErrorAction SilentlyContinue |
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
