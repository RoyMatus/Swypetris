<# Evidence helpers: explicit intervals, no inferred coding time or summed overlapping wall time. #>
function Get-DevelopmentIntervals {
    param([object[]]$Events)
    $pending = @{}
    $intervals = @()
    foreach ($event in $Events) {
        if ($event.action -notin @('Start', 'Stop') -or !$event.phase -or !$event.id) { throw 'Invalid work event.' }
        $time = [datetimeoffset]::Parse($event.timeUtc)
        if ($event.action -eq 'Start') {
            if ($pending.ContainsKey($event.id)) { throw 'Duplicate start event.' }
            $pending[$event.id] = $event
        } else {
            if (!$pending.ContainsKey($event.id)) { throw 'Stop has no matching start.' }
            $start = $pending[$event.id]
            $seconds = ($time - [datetimeoffset]::Parse($start.timeUtc)).TotalSeconds
            if ($seconds -lt 0 -or $event.phase -ne $start.phase -or $event.issue -ne $start.issue) { throw 'Mismatched or negative interval.' }
            $intervals += [pscustomobject]@{ id=$event.id; issue=$event.issue; phase=$event.phase; seconds=$seconds; start=$start.timeUtc; end=$event.timeUtc }
            $pending.Remove($event.id)
        }
    }
    [pscustomobject]@{ intervals=$intervals; unfinished=@($pending.Values) }
}

function Get-DevelopmentCheckRows {
    param([string[]]$SummaryFiles)
    foreach ($path in ($SummaryFiles | Sort-Object -Unique)) {
        $s = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
        if (!$s.suite -or !$s.commit -or !$s.startedUtc -or $s.result -notin @('passed','failed') -or
            $null -eq $s.durationSeconds -or [double]$s.durationSeconds -lt 0 -or
            [double]::IsNaN([double]$s.durationSeconds) -or [double]::IsInfinity([double]$s.durationSeconds)) { throw "Invalid verification summary: $path" }
        $null = [datetimeoffset]::Parse($s.startedUtc)
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

Export-ModuleMember -Function Get-DevelopmentIntervals, Get-DevelopmentCheckRows, Get-SlowestTestCases
