<# Evidence helpers: explicit intervals, no inferred coding time or summed overlapping wall time. #>
function ConvertTo-DevelopmentTime {
    param([object]$Value)
    # ConvertFrom-Json may already return a date. Do not stringify it: that loses
    # its offset/fractions and mixes PowerShell invariant formatting with locale parsing.
    if ($Value -is [datetimeoffset] -or $Value -is [datetime]) { return [datetimeoffset]$Value }
    [datetimeoffset]::Parse([string]$Value, [Globalization.CultureInfo]::InvariantCulture)
}

function Get-DevelopmentIntervals {
    param([object[]]$Events)
    $pending = @{}
    $seen = [Collections.Generic.HashSet[string]]::new()
    $intervals = @()
    $lastEnd = $null
    foreach ($event in $Events) {
        if ($event.action -notin @('Start', 'Stop') -or !$event.phase -or !$event.id) { throw 'Invalid work event.' }
        $time = ConvertTo-DevelopmentTime $event.timeUtc
        if ($event.action -eq 'Start') {
            if (!$seen.Add([string]$event.id)) { throw 'Duplicate start event.' }
            if ($pending.Count) { throw 'Overlapping work intervals are not supported.' }
            if ($null -ne $lastEnd -and $time -lt $lastEnd) { throw 'Overlapping or out-of-order work intervals are not supported.' }
            $pending[$event.id] = $event
        } else {
            if (!$pending.ContainsKey($event.id)) { throw 'Stop has no matching start.' }
            $start = $pending[$event.id]
            $seconds = ($time - (ConvertTo-DevelopmentTime $start.timeUtc)).TotalSeconds
            if ($seconds -lt 0 -or $event.phase -ne $start.phase -or $event.issue -ne $start.issue) { throw 'Mismatched or negative interval.' }
            $intervals += [pscustomobject]@{ id=$event.id; issue=$event.issue; phase=$event.phase; seconds=$seconds; start=$start.timeUtc; end=$event.timeUtc }
            $lastEnd = $time
            $pending.Remove($event.id)
        }
    }
    [pscustomobject]@{ intervals=$intervals; unfinished=@($pending.Values) }
}

function Get-DevelopmentTicketTime {
    param([Parameter(Mandatory)][object[]]$Events,
          [Parameter(Mandatory)][int]$Issue,
          [ValidateSet('Complete','Partial','Unknown')][string]$Coverage = 'Partial')
    $state = Get-DevelopmentIntervals -Events $Events
    $rows = @($state.intervals | Where-Object { $_.issue -eq $Issue })
    $unfinished = @($state.unfinished | Where-Object { $_.issue -eq $Issue })
    if ($Coverage -eq 'Complete' -and ($unfinished.Count -or !$rows.Count)) { throw 'Complete coverage requires completed intervals and no unfinished interval.' }
    $active = @($rows | Where-Object { $_.phase -in @('Implementation','Review','ReleasePreparation','Rework') })
    $checks = @($rows | Where-Object { $_.phase -eq 'LocalChecks' })
    $waiting = @($rows | Where-Object { $_.phase -in @('Waiting','CIWait') })
    function Sum-Seconds($Items) { return [double](($Items | Measure-Object seconds -Sum).Sum) }
    [pscustomobject]@{
        issue=$Issue; coverage=$Coverage; activeSeconds=$(if ($active.Count -or $Coverage -eq 'Complete') { Sum-Seconds $active } else { $null })
        localChecksSeconds=(Sum-Seconds $checks); waitingSeconds=(Sum-Seconds $waiting)
        reworkSeconds=(Sum-Seconds @($rows | Where-Object { $_.phase -eq 'Rework' }))
        intervalCount=$rows.Count; unfinishedCount=$unfinished.Count
    }
}

function Get-DevelopmentCheckRows {
    param([string[]]$SummaryFiles)
    $seen = [Collections.Generic.HashSet[string]]::new()
    foreach ($path in ($SummaryFiles | Sort-Object -Unique)) {
        $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256 -ErrorAction Stop).Hash
        if (!$seen.Add($hash)) { continue }
        $s = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
        if (!$s.suite -or !$s.commit -or !$s.startedUtc -or $s.result -notin @('passed','failed') -or
            $null -eq $s.durationSeconds -or [double]$s.durationSeconds -lt 0 -or
            [double]::IsNaN([double]$s.durationSeconds) -or [double]::IsInfinity([double]$s.durationSeconds)) { throw "Invalid verification summary: $path" }
        $null = ConvertTo-DevelopmentTime $s.startedUtc
        $counts = @()
        if ($s.tests) { $counts += "passed=$($s.tests.passed), skipped=$($s.tests.skipped), reused=$([bool]$s.reusedTestResults)" }
        foreach ($role in @($s.roles)) {
            if ($role.jvm) { $counts += "$($role.role): passed=$($role.jvm.passed), skipped=$($role.jvm.skipped), outcome=$($role.jvmOutcome)" }
        }
        [pscustomobject]@{ suite=$s.suite; result=$s.result; seconds=[double]$s.durationSeconds; commit=$s.commit; dirty=[bool]$s.dirty; counts=($counts -join '; '); source=$path; reason=$(if ($s.reason) { $s.reason } else { 'Not recorded' }) }
    }
}

function Get-SlowestTestCases {
    param([string[]]$XmlFiles, [int]$Limit=20)
    $rows = foreach ($path in ($XmlFiles | Sort-Object -Unique)) {
        [xml]$xml = Get-Content -LiteralPath $path -Raw
        foreach ($case in $xml.SelectNodes('//testcase')) {
            if (!$case.HasAttribute('time')) { continue }
            $seconds = [double]::Parse($case.GetAttribute('time'), [Globalization.CultureInfo]::InvariantCulture)
            if ($seconds -lt 0 -or [double]::IsNaN($seconds) -or [double]::IsInfinity($seconds)) { throw 'Invalid testcase duration.' }
            [pscustomobject]@{ test="$($case.classname)#$($case.name)"; seconds=$seconds; source=$path }
        }
    }
    $rows | Sort-Object seconds -Descending | Select-Object -First $Limit
}

Export-ModuleMember -Function Get-DevelopmentIntervals, Get-DevelopmentTicketTime, Get-DevelopmentCheckRows, Get-SlowestTestCases
