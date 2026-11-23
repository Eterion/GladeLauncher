param(
    [switch]$Android
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent

function Invoke-Git {
    param([string[]]$GitArgs)
    $result = @(& git @GitArgs 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "git $($GitArgs -join ' ') failed: $($result -join "`n")"
    }
    return $result
}

Push-Location $repoRoot
try {
    # Also checks staged content on an unborn branch; no HEAD is required.
    $null = Invoke-Git @('diff', '--check')
    $null = Invoke-Git @('diff', '--cached', '--check')
    $tracked = @(Invoke-Git @('ls-files', '--cached'))
    $files = @(Invoke-Git @('ls-files', '--cached', '--others', '--exclude-standard', '--deduplicate'))
    $forbidden = '(^|/)(\.local|\.gradle|\.kotlin|\.idea|build)(/|$)|(^|/)(local\.properties|\.env(?:\..*)?)$|\.(jks|keystore)$'
    foreach ($path in $tracked) {
        if ($path -match $forbidden) { throw "Local/generated/sensitive path is staged or tracked: $path" }
    }
    foreach ($probe in @('.local/plans/check.md', '.local/briefs/check.md', '.local/tmp/check.txt')) {
        $null = Invoke-Git @('check-ignore', '--quiet', '--', $probe)
    }

    $textExtensions = @('.md', '.json', '.toml', '.ps1', '.kt', '.kts', '.java', '.xml', '.properties', '.yml', '.yaml')
    foreach ($path in $files) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { continue }
        if ([IO.Path]::GetExtension($path) -in $textExtensions -or $path -eq '.gitignore') {
            $content = [IO.File]::ReadAllText((Join-Path $repoRoot $path))
            if ($content -match '(?m)^(<<<<<<< |=======\r?$|>>>>>>> )') { throw "Merge conflict marker: $path" }
            if ($content -match '(?m)[\t ]+\r?$') { throw "Trailing whitespace: $path" }
        }
        if ($path.EndsWith('.ps1')) {
            $tokens = $null
            $parseErrors = $null
            $null = [Management.Automation.Language.Parser]::ParseFile((Join-Path $repoRoot $path), [ref]$tokens, [ref]$parseErrors)
            if ($parseErrors.Count -gt 0) { throw "PowerShell syntax error in ${path}: $($parseErrors.Message -join '; ')" }
        }
    }
    $hookConfig = Get-Content -LiteralPath '.codex/hooks.json' -Raw | ConvertFrom-Json
    if (-not $hookConfig.hooks.Stop) { throw 'Missing Codex Stop hook.' }
    Write-Output 'PASS: repository hygiene, local-only exclusions, hook JSON, and PowerShell syntax.'

    # Compare index and working tree with HEAD, including new files. Avoid builds for docs-only work.
    $null = & git rev-parse --verify HEAD 2>$null
    if ($LASTEXITCODE -eq 0) {
        $changed = @(Invoke-Git @('diff', '--name-only', 'HEAD', '--'))
    }
    else {
        $changed = $tracked
    }
    $changed += @(Invoke-Git @('ls-files', '--others', '--exclude-standard'))
    $androidInputs = '(^|/)src/|(^|/)AndroidManifest\.xml$|\.(kt|kts|java|gradle|pro)$|(^|/)gradle(/|\.properties$)|(^|/)gradlew(\.bat)?$'
    $needsAndroid = $Android -or @($changed | Where-Object { $_ -match $androidInputs }).Count -gt 0
    if (-not $needsAndroid) {
        Write-Output 'SKIP: Android build/tests/lint (no changed Android/build inputs; use -Android to force).'
        exit 0
    }
    if (-not (Test-Path -LiteralPath 'gradlew.bat')) {
        throw 'Android checks required, but gradlew.bat is missing. Add the Android project and wrapper before claiming validation.'
    }
    # Use Studio's bundled JDK and the default SDK without changing global settings.
    if (-not $env:JAVA_HOME) {
        $studioJdk = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
        if (Test-Path -LiteralPath $studioJdk) { $env:JAVA_HOME = $studioJdk }
    }
    if (-not $env:ANDROID_HOME) {
        $defaultSdk = Join-Path $env:USERPROFILE 'AppData/Local/Android/Sdk'
        if (Test-Path -LiteralPath $defaultSdk) { $env:ANDROID_HOME = $defaultSdk }
    }
    if (-not $env:GRADLE_USER_HOME) {
        $env:GRADLE_USER_HOME = Join-Path $repoRoot '.local/gradle-user-home'
    }
    & .\gradlew.bat --console=plain assembleDebug testDebugUnitTest lintDebug
    if ($LASTEXITCODE -ne 0) { throw "Android build/tests/lint failed (exit $LASTEXITCODE)." }
    Write-Output 'PASS: Android assembleDebug, testDebugUnitTest, and lintDebug.'
}
catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
}
finally {
    Pop-Location
}
