$ErrorActionPreference = 'Stop'
$eventData = $null
try {
    $eventData = [Console]::In.ReadToEnd() | ConvertFrom-Json
    if ($null -eq $eventData -or $eventData.hook_event_name -ne 'Stop') {
        throw 'Expected a Codex Stop event on stdin.'
    }
    $repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
    $messages = & pwsh -NoProfile -File (Join-Path $repoRoot 'scripts/verify.ps1') 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ($messages -join "`n")
    }
    # Stop accepts JSON only. Do not send verifier output directly to stdout.
    Write-Output '{}'
}
catch {
    $reason = "GladeLauncher verification failed. Fix the failure or report the external blocker and skipped checks. Details: $($_.Exception.Message)"
    if ($null -ne $eventData -and $eventData.stop_hook_active) {
        @{ systemMessage = "$reason Automatic repair was already requested; verification is still failing." } | ConvertTo-Json -Compress
    }
    else {
        @{ decision = 'block'; reason = $reason } | ConvertTo-Json -Compress
    }
}
