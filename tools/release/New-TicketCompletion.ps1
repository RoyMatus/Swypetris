<# Prepare a short, local completion draft from explicitly selected evidence. Never posts. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateRange(1,1000000)][int]$Issue,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Behavior,
    [string]$PullRequestUrl,
    [string[]]$SummaryFile,
    [string[]]$EvidenceFile,
    [string]$JournalFile,
    [string]$Limitations,
    [string]$Incident,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Development-Review.psm1') -Force
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (!$OutputDirectory) { $OutputDirectory = Join-Path $project "app/build/verification/ticket-$Issue" }
$null = New-Item -ItemType Directory -Path $OutputDirectory -Force

function Evidence-Label([string]$Path) {
    $full = (Resolve-Path -LiteralPath $Path -ErrorAction Stop).Path
    if ($full.StartsWith($project + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        return $full.Substring($project.Length + 1).Replace('\','/')
    }
    return [IO.Path]::GetFileName($full)
}

$summaries = @(Get-DevelopmentCheckRows -SummaryFiles $SummaryFile)
$gates = @()
$seenGate = [Collections.Generic.HashSet[string]]::new()
foreach ($path in ($EvidenceFile | Sort-Object -Unique)) {
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256 -ErrorAction Stop).Hash
    if (!$seenGate.Add($hash)) { continue }
    $gate = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
    if ($gate.result -notin @('passed','failed') -or $gate.head -notmatch '^[a-f0-9]{40}$' -or
        $gate.base -notmatch '^[a-f0-9]{40}$' -or !$gate.checks -or
        @($gate.checks | Where-Object { !$_.name -or $_.result -notin @('passed','failed') -or
            $null -eq $_.durationSeconds -or [double]$_.durationSeconds -lt 0 -or
            [double]::IsNaN([double]$_.durationSeconds) -or
            [double]::IsInfinity([double]$_.durationSeconds) }).Count) {
        throw "Invalid local gate evidence: $path"
    }
    $gates += [pscustomobject]@{data=$gate; source=(Evidence-Label $path)}
}

$timeLine = 'Recorded active work: Unknown (no journal selected).'
if ($JournalFile) {
    $journalLabel = Evidence-Label $JournalFile
    $events = @(Get-Content -LiteralPath $JournalFile | ForEach-Object { $_ | ConvertFrom-Json })
    $time = Get-DevelopmentTicketTime -Events $events -Issue $Issue -Coverage Partial
    $timeLine = if ($null -eq $time.activeSeconds) {
        "Recorded active work: Unknown (no completed active interval; source: $journalLabel)."
    } else {
        "Recorded active work: $([math]::Round($time.activeSeconds,3)) s lower bound " +
            "(coverage: Partial; source: $journalLabel; unfinished intervals: $($time.unfinishedCount))."
    }
}

$lines = [Collections.Generic.List[string]]::new()
$lines.Add("# Issue #$Issue completion — DRAFT")
$lines.Add('')
$lines.Add('Review the supplied behavior, actual outcome, limits and links before posting once after verified merge/closure. This draft does not validate a PR gate or publish anything.')
$lines.Add('')
$lines.Add("Delivered behavior (review against diff): $Behavior")
$lines.Add($(if ($PullRequestUrl) { "PR: $PullRequestUrl" } else { 'PR: Unknown; add the verified URL.' }))
$lines.Add($timeLine)
$lines.Add('')
$lines.Add('Actual selected checks (durations are independent runner/stage times, not active work or unique coverage):')
if (!$summaries.Count -and !$gates.Count) { $lines.Add('- Unknown: no check evidence selected.') }
foreach ($row in $summaries) {
    $lines.Add("- $($row.suite): reported $($row.result), $([math]::Round($row.seconds,3)) s; " +
        "SHA $($row.commit); dirty=$($row.dirty); $($row.counts); source $(Evidence-Label $row.source).")
}
foreach ($gate in $gates) {
    $failedStages = @($gate.data.checks | Where-Object result -eq 'failed' | ForEach-Object name)
    $stages = "$(@($gate.data.checks | Where-Object result -eq 'passed').Count)/$(@($gate.data.checks).Count) stages passed"
    if ($failedStages.Count) { $stages += "; failed: $($failedStages -join ', ')" }
    $lines.Add("- Local gate: reported $($gate.data.result), head $($gate.data.head), base $($gate.data.base); " +
        "stages $stages; source $($gate.source).")
}
$lines.Add('')
if ($Incident) { $lines.Add("Substantial incident (review evidence and diagnosis): $Incident") }
$lines.Add($(if ($Limitations) { "Limitations: $Limitations" } else { 'Limitations: Unknown; review before posting.' }))
$output = Join-Path $OutputDirectory ("completion-draft-{0}.md" -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$lines | Set-Content -LiteralPath $output -Encoding utf8
Write-Output "Draft: $output"
