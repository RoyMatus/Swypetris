Import-Module (Join-Path $PSScriptRoot 'release/Emulator-Diagnostics.psm1')

function Invoke-EmulatorReadinessCommand {
    param([string]$AdbCommand, [string[]]$Arguments, [int]$TimeoutMilliseconds)
    $file = [IO.Path]::GetTempFileName()
    try {
        $result = Invoke-BoundedDiagnostic -Executable $AdbCommand -Arguments $Arguments -OutputFile $file -TimeoutMilliseconds $TimeoutMilliseconds
        if ($result.timedOut -or $result.outputDrainTimedOut -or $result.exitCode -ne 0) {
            throw "Emulator readiness command failed: adb $($Arguments -join ' ') (exit=$($result.exitCode), timeout=$($result.timedOut), drainTimeout=$($result.outputDrainTimedOut))."
        }
        [IO.File]::ReadAllText($file).Trim()
    } finally {
        Remove-Item -LiteralPath $file,"$file.stderr.log" -Force -ErrorAction SilentlyContinue
    }
}

function Assert-EmulatorReady {
    param(
        [Parameter(Mandatory)][string]$AdbCommand,
        [Parameter(Mandatory)][ValidatePattern('^emulator-\d+$')][string]$Serial,
        [ValidateRange(1,600)][int]$TimeoutSeconds=120,
        [ValidateRange(1,10000)][int]$PollIntervalMilliseconds=1000,
        [ValidateRange(35,35)][int]$ExpectedApi=35
    )
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $budget = $TimeoutSeconds * 1000
    $read = {
        param([string[]]$CommandArguments)
        $remaining = $budget - [int]$timer.ElapsedMilliseconds
        if ($remaining -le 0) { throw "Emulator readiness timed out after $TimeoutSeconds seconds: $Serial." }
        Invoke-EmulatorReadinessCommand -AdbCommand $AdbCommand -Arguments $CommandArguments -TimeoutMilliseconds ([Math]::Min(10000,$remaining))
    }
    $devices = & $read @('devices','-l')
    if ($devices -notmatch "(?m)^$([regex]::Escape($Serial))\s") { throw "Explicit emulator $Serial is not listed by adb devices -l." }
    while ($true) {
        try {
            $state = & $read @('-s',$Serial,'get-state')
            if ($state -eq 'device') {
                $boot = & $read @('-s',$Serial,'shell','getprop','sys.boot_completed')
                if ($boot -eq '1') { break }
            }
        } catch {
            # adb get-state returns a failure while an otherwise listed device is offline.
            # Poll only readiness inside this budget; never retry instrumentation or reboot.
            $state = $_.Exception.Message
        }
        $remaining = $budget - [int]$timer.ElapsedMilliseconds
        if ($remaining -le 0) { throw "Emulator readiness timed out after $TimeoutSeconds seconds: $Serial (state=$state)." }
        Start-Sleep -Milliseconds ([Math]::Min($PollIntervalMilliseconds,$remaining))
    }
    $api = & $read @('-s',$Serial,'shell','getprop','ro.build.version.sdk')
    if ($api -ne [string]$ExpectedApi) { throw "Emulator $Serial requires API $ExpectedApi; observed '$api'." }
    $model = & $read @('-s',$Serial,'shell','getprop','ro.product.model')
    if ([string]::IsNullOrWhiteSpace($model)) { throw "Emulator $Serial has no model identity." }
    $fingerprint = & $read @('-s',$Serial,'shell','getprop','ro.build.fingerprint')
    if ([string]::IsNullOrWhiteSpace($fingerprint)) { throw "Emulator $Serial has no build fingerprint." }
    $windows = & $read @('-s',$Serial,'shell','dumpsys','window','windows')
    if ([string]::IsNullOrWhiteSpace($windows) -or $windows -notmatch '(?m)\bmCurrentFocus=|Window #\d+') {
        throw "Emulator $Serial window snapshot is unavailable or unrecognized."
    }
    # API 35 AOSP AppErrorDialog/AppNotRespondingDialog set these unlocalized titles.
    $errorTitle = 'Application (?:Not Responding|Error):'
    if ($windows -match "(?m)^\s*mCurrentFocus=.*$errorTitle") { throw "Blocking system window on $Serial (ANR/crash); retain diagnostics before any retry." }
    foreach ($block in [regex]::Split($windows, '(?m)(?=^\s*Window #\d+)')) {
        if ($block -match "(?m)^\s*Window #\d+.*$errorTitle" -and $block -match '\b(?:isOnScreen|isVisible)=true\b') {
            throw "Blocking system window on $Serial (visible ANR/crash); retain diagnostics before any retry."
        }
    }
    [pscustomobject]@{ serial=$Serial; api=[int]$api; model=$model; buildFingerprint=$fingerprint; ready=$true; durationSeconds=$timer.Elapsed.TotalSeconds }
}
Export-ModuleMember -Function Assert-EmulatorReady
