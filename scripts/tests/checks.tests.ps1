# Regression checks for the verification gate, isolated from the real repository index.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$testRoot = Join-Path $repoRoot ('.local/tmp/checks-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $testRoot -Force
$passed = 0

function New-Fixture {
    $fixture = Join-Path $testRoot ([guid]::NewGuid().ToString('N'))
    foreach ($folder in @('scripts', '.codex/hooks', '.local/plans')) {
        $null = New-Item -ItemType Directory -Path (Join-Path $fixture $folder) -Force
    }
    Copy-Item -LiteralPath (Join-Path $repoRoot 'scripts/verify.ps1') -Destination (Join-Path $fixture 'scripts/verify.ps1')
    Copy-Item -LiteralPath (Join-Path $repoRoot '.codex/hooks/stop.ps1') -Destination (Join-Path $fixture '.codex/hooks/stop.ps1')
    Copy-Item -LiteralPath (Join-Path $repoRoot '.codex/hooks.json') -Destination (Join-Path $fixture '.codex/hooks.json')
    Copy-Item -LiteralPath (Join-Path $repoRoot '.gitignore') -Destination (Join-Path $fixture '.gitignore')
    & git -C $fixture init --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Fixture git init failed.' }
    return $fixture
}

function Assert-Check {
    param([string]$Fixture, [int]$ExpectedExit, [string]$ExpectedMessage, [switch]$ForceAndroid)
    $extraArgs = @()
    if ($ForceAndroid) { $extraArgs += '-Android' }
    $output = & pwsh -NoProfile -File (Join-Path $Fixture 'scripts/verify.ps1') @extraArgs 2>&1
    if ($LASTEXITCODE -ne $ExpectedExit -or ($output -join "`n") -notmatch [regex]::Escape($ExpectedMessage)) {
        throw "Expected exit $ExpectedExit and '$ExpectedMessage'; got: $($output -join "`n")"
    }
    $script:passed++
}

$fixture = New-Fixture
Assert-Check $fixture 0 'SKIP: Android'
Assert-Check $fixture 1 'gradlew.bat is missing' -ForceAndroid

# The configured command must work from a nested session directory, with JSON-only stdout.
Push-Location (Join-Path $fixture 'scripts')
try {
    $command = (Get-Content -LiteralPath (Join-Path $fixture '.codex/hooks.json') -Raw | ConvertFrom-Json).hooks.Stop[0].hooks[0].command
    $output = '{"hook_event_name":"Stop","stop_hook_active":false}' | & pwsh -NoProfile -Command $command
    if ($LASTEXITCODE -ne 0 -or ($output -join '').Trim() -ne '{}') { throw "Configured hook failed: $output" }
    $passed++
}
finally { Pop-Location }

$fixture = New-Fixture
[IO.File]::WriteAllText((Join-Path $fixture 'new.md'), "trailing space `n")
Assert-Check $fixture 1 'Trailing whitespace'

$fixture = New-Fixture
[IO.File]::WriteAllText((Join-Path $fixture 'broken.ps1'), 'function Broken {')
Assert-Check $fixture 1 'PowerShell syntax error'

$fixture = New-Fixture
[IO.File]::WriteAllText((Join-Path $fixture '.local/plans/private.md'), 'local plan')
& git -C $fixture add --force -- .local/plans/private.md
if ($LASTEXITCODE -ne 0) { throw 'Fixture staging failed.' }
Assert-Check $fixture 1 'Local/generated/sensitive path'

$fixture = New-Fixture
[IO.File]::WriteAllText((Join-Path $fixture '.codex/hooks.json'), 'invalid JSON')
Assert-Check $fixture 1 'JSON'

$fixture = New-Fixture
[IO.File]::WriteAllText((Join-Path $fixture 'build.gradle.kts'), '// new Android build')
Assert-Check $fixture 1 'gradlew.bat is missing'

# Check Stop failure and bounded-retry behavior without an actual failing Android build.
foreach ($retry in @($false, $true)) {
    $eventJson = @{ hook_event_name = 'Stop'; stop_hook_active = $retry } | ConvertTo-Json -Compress
    $output = $eventJson | & pwsh -NoProfile -File (Join-Path $fixture '.codex/hooks/stop.ps1')
    if ($LASTEXITCODE -ne 0) { throw "Stop hook process failed: $output" }
    $result = ($output -join "`n") | ConvertFrom-Json
    if (-not $retry -and ($result.decision -ne 'block' -or $result.reason -notmatch 'gradlew.bat')) {
        throw 'Stop hook did not request a repair for a failed check.'
    }
    if ($retry -and ($result.decision -eq 'block' -or $result.systemMessage -notmatch 'still failing')) {
        throw 'Stop hook must warn after one failed repair rather than loop forever.'
    }
    $passed++
}

# A stub wrapper tests command dispatch and error propagation, not Android behavior.
$fixture = New-Fixture
[IO.File]::WriteAllText((Join-Path $fixture 'gradlew.bat'), "@echo off`r`necho %* > .local\gradle-args.txt`r`nexit /b 0`r`n")
Assert-Check $fixture 0 'PASS: Android assembleDebug'
$actualArgs = [IO.File]::ReadAllText((Join-Path $fixture '.local/gradle-args.txt')).Trim()
if ($actualArgs -ne '--console=plain assembleDebug testDebugUnitTest lintDebug') {
    throw "Incorrect Gradle tasks: $actualArgs"
}
$passed++
[IO.File]::WriteAllText((Join-Path $fixture 'gradlew.bat'), "@echo off`r`nexit /b 7`r`n")
Assert-Check $fixture 1 'Android build/tests/lint failed (exit 7)'

Write-Output "PASS: $passed verification/hook regression checks. Fixtures retained under $testRoot (ignored)."
