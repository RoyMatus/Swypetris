<# Project scheduling heuristics, not universal hardware benchmarks or permission grants. #>
function Get-ExecutionDecision {
    param([Parameter(Mandatory)][System.Collections.IDictionary]$Facts)
    $reasons = @()
    $known = $true
    foreach ($key in @('logicalProcessors','totalMemoryGiB','availableMemoryGiB','freeDiskGiB')) {
        if ($null -eq $Facts[$key] -or [double]::IsNaN([double]$Facts[$key]) -or
            [double]::IsInfinity([double]$Facts[$key]) -or [double]$Facts[$key] -lt 0) { $known = $false }
    }
    $build = $known -and $Facts.logicalProcessors -ge 4 -and $Facts.totalMemoryGiB -ge 8 -and
        $Facts.availableMemoryGiB -ge 4 -and $Facts.freeDiskGiB -ge 10 -and $Facts.javaReady -and $Facts.sdkReady
    $strong = $build -and $Facts.logicalProcessors -ge 8 -and $Facts.totalMemoryGiB -ge 24 -and
        $Facts.availableMemoryGiB -ge 10 -and $Facts.freeDiskGiB -ge 20
    $device = $strong -and $Facts.emulatorReady -and $Facts.accelerationReady
    if (!$known) { $reasons += 'Resource measurements incomplete; do not infer local heavy-work capacity.' }
    if (!$Facts.javaReady -or !$Facts.sdkReady) { $reasons += 'JDK 21 or the project Android SDK platform is unavailable or unverified; configure an existing installation before treating this as weak hardware.' }
    if (!$build) { $reasons += 'Route heavy Android builds/checks to an available cloud executor; lightweight editing/tool tests may remain local.' }
    elseif (!$device) { $reasons += 'Local builds fit the initial budget; route device checks to cloud until resources/acceleration/image readiness is established.' }
    else { $reasons += 'Resources and accelerated API 35 emulator are available; prefer local build and device feedback.' }
    if ($null -ne $Facts.cpuLoadPercent -and $Facts.cpuLoadPercent -ge 85) { $reasons += 'Host is busy; wait/recheck before heavy work instead of oversubscribing or permanently downgrading it.' }
    $workers = if ($strong) { [int][math]::Min(6, [math]::Floor($Facts.logicalProcessors / 2)) } elseif ($build) { 2 } else { 1 }
    [pscustomobject][ordered]@{
        mode = $(if ($device) { 'Local' } elseif ($build) { 'Hybrid' } else { 'Cloud' })
        buildLocation = $(if ($build) { 'Local' } else { 'Cloud' })
        deviceLocation = $(if ($device) { 'Local' } else { 'Cloud' })
        toolsLocation = 'Local'; maxWorkers=$workers; maxGradleProcesses=1
        maxEmulators=$(if ($device) { 1 } else { 0 }); reasons=$reasons
    }
}

function Limit-ExecutionResources {
    param([double]$TotalGiB, [double]$AvailableGiB, [Nullable[double]]$LimitGiB, [Nullable[double]]$UsedGiB)
    if ($null -ne $LimitGiB -and $LimitGiB -gt 0 -and $LimitGiB -lt $TotalGiB) {
        $TotalGiB = $LimitGiB
        # Unknown container usage cannot establish available memory.
        $AvailableGiB = if ($null -ne $UsedGiB) { [math]::Min($AvailableGiB, [math]::Max(0, $LimitGiB-$UsedGiB)) } else { 0 }
    }
    [pscustomobject]@{totalMemoryGiB=$TotalGiB; availableMemoryGiB=$AvailableGiB}
}
function Get-ProjectAndroidPlatform {
    param([string]$BuildScript)
    if ($BuildScript -match '(?s)compileSdk\s*\{\s*version\s*=\s*release\((\d+)\)\s*\{\s*minorApiLevel\s*=\s*(\d+)') {
        return "android-$($Matches[1]).$($Matches[2])"
    }
    if ($BuildScript -match '(?m)^\s*compileSdk\s*=\s*(\d+)\s*$') { return "android-$($Matches[1])" }
    throw 'Unrecognized compileSdk declaration; inspect the project platform instead of guessing.'
}
Export-ModuleMember -Function Get-ExecutionDecision, Limit-ExecutionResources, Get-ProjectAndroidPlatform
