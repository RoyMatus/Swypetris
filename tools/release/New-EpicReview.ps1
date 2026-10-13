<# Generate an evidence draft locally; never publish or overwrite a completed review. #>
param(
    [Parameter(Mandatory)][ValidateRange(1,1000000)][int]$Epic,
    [string[]]$SummaryFile,
    [string[]]$TestXml,
    [long[]]$RunId
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Development-Review.psm1') -Force
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$directory = Join-Path $project "app/build/verification/epic-$Epic"
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$journal = Join-Path $directory 'work.jsonl'
$events = @(if (Test-Path -LiteralPath $journal) { Get-Content -LiteralPath $journal | ForEach-Object { $_ | ConvertFrom-Json } })
$state = Get-DevelopmentIntervals -Events $events
$rows = @(Get-DevelopmentCheckRows -SummaryFiles $SummaryFile)
$slow = @(Get-SlowestTestCases -XmlFiles $TestXml)
$lines = [Collections.Generic.List[string]]::new()
$lines.Add("# Epic #$Epic development review — DRAFT")
$lines.Add('')
$lines.Add('Generated evidence only. Review scope, failures, overlapping intervals, conclusions and limitations before committing the post-merge report.')
$lines.Add('')
$lines.Add('## Optional observed phase time')
$lines.Add('')
$lines.Add('| Issue | Phase | Completed intervals | Seconds |')
$lines.Add('| --- | --- | ---: | ---: |')
foreach ($group in ($state.intervals | Group-Object issue,phase)) {
    $first = $group.Group[0]
    $seconds = ($group.Group | Measure-Object -Property seconds -Sum).Sum
    $lines.Add("| $($first.issue) | $($first.phase) | $($group.Count) | $seconds |")
}
$lines.Add('')
$lines.Add("Unfinished intervals excluded: $($state.unfinished.Count). Unrecorded active work is unknown. Overlapping intervals must not be added as elapsed epic time.")
$lines.Add('')
$lines.Add('## Selected local verification summaries')
$lines.Add('')
$lines.Add('| Suite | Result | Seconds | Commit | Dirty | Test evidence | Risk/reason |')
$lines.Add('| --- | --- | ---: | --- | --- | --- | --- |')
foreach ($row in $rows) { $lines.Add("| $($row.suite) | $($row.result) | $($row.seconds) | $($row.commit) | $($row.dirty) | $($row.counts) | $($row.reason.Replace('|','/').Replace("`n",' ')) |") }
$lines.Add('')
$lines.Add('Cached results are not fresh executions. Repeated invocations/cases are not unique coverage. Input files must be selected for this epic; no automatic historical inclusion.')
if ($RunId.Count) {
    Import-Module (Join-Path $project 'tools/GitHub-Api.psm1') -Force
    $client = New-GitHubClient -Repository 'RoyMatus/Swypetris'
    $lines.Add('')
    $lines.Add('## Selected CI runs and jobs')
    $lines.Add('')
    $lines.Add('| Run / attempt | SHA | Job | Result | Seconds |')
    $lines.Add('| --- | --- | --- | --- | ---: |')
    foreach ($id in ($RunId | Sort-Object -Unique)) {
        $run = Invoke-GitHubRest -Client $client -Method Get -Path "actions/runs/$id"
        # Query the requested attempt, not jobs silently replaced by a later retry.
        $page = 1
        do {
            $jobs = Invoke-GitHubRest -Client $client -Method Get -Path "actions/runs/$id/attempts/$($run.run_attempt)/jobs?per_page=100&page=$page"
            foreach ($job in $jobs.jobs) {
                $seconds = if ($job.status -eq 'completed' -and $job.started_at -and $job.completed_at) { ([datetimeoffset]::Parse($job.completed_at) - [datetimeoffset]::Parse($job.started_at)).TotalSeconds } else { 'unfinished' }
                $lines.Add("| [$id](https://github.com/RoyMatus/Swypetris/actions/runs/$id) / $($run.run_attempt) | $($run.head_sha) | $($job.name) | $($job.status)/$($job.conclusion) | $seconds |")
            }
            $page++
        } while (@($jobs.jobs).Count -eq 100)
    }
    $lines.Add('Job durations include step overhead; parallel jobs are not summed into wall time. Queue time and active coding are not inferred from job timestamps.')
}
$lines.Add('')
$lines.Add('## Slowest measured test cases')
$lines.Add('')
foreach ($row in $slow) { $lines.Add("- $($row.test): $($row.seconds) s") }
$lines.Add('XML case time excludes setup/build/install overhead; missing timing is unknown. Preserve assertions and required isolation before optimizing any case.')
$lines.Add('')
$lines.Add('## Review before publication')
$lines.Add('')
$lines.Add('- Confirm epic/child closure and final PR/release URLs.')
$lines.Add('- Explain failures/retries and rework from retained logs; distinguish facts, inferences and unknowns.')
$lines.Add('- Propose improvements with evidence, expected benefit and quality-preserving validation.')
$lines.Add('- Compare matching scope/environment; do not claim causality from cold/warm timings.')
$lines.Add('- Remove local absolute paths/device identifiers or sensitive details from the public report.')
$output = Join-Path $directory ("review-draft-{0}.md" -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$lines | Set-Content -LiteralPath $output -Encoding utf8
Write-Output "Draft: $output"
