<# Record only observed work boundaries. Stop the active phase before leaving or waiting. #>
param(
    [Parameter(Mandatory)][ValidateRange(1,1000000)][int]$Epic,
    [Parameter(Mandatory)][ValidateRange(1,1000000)][int]$Issue,
    [Parameter(Mandatory)][ValidateSet('Start','Stop')][string]$Action,
    [Parameter(Mandatory)][ValidateSet('Implementation','LocalChecks','CIWait','Review','ReleasePreparation','Rework')][string]$Phase,
    [string]$Id
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Development-Review.psm1') -Force
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$directory = Join-Path $project "app/build/verification/epic-$Epic"
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$journal = Join-Path $directory 'work.jsonl'
$events = @(if (Test-Path -LiteralPath $journal) { Get-Content -LiteralPath $journal | ForEach-Object { $_ | ConvertFrom-Json } })
$state = Get-DevelopmentIntervals -Events $events
if ($Action -eq 'Start') {
    if ($state.unfinished.Count) { throw 'Stop the current phase before starting another.' }
    $Id = [guid]::NewGuid().ToString()
} elseif (!$Id -or @($state.unfinished | Where-Object { $_.id -eq $Id -and $_.issue -eq $Issue -and $_.phase -eq $Phase }).Count -ne 1) {
    throw 'Stop requires the matching active Id, issue and phase.'
}
$event = [ordered]@{ id=$Id; action=$Action; issue=$Issue; phase=$Phase; timeUtc=[datetimeoffset]::UtcNow.ToString('o'); commit=(& git -C $project rev-parse HEAD) }
$event | ConvertTo-Json -Compress | Add-Content -LiteralPath $journal -Encoding utf8
Write-Output "Id: $Id; journal: $journal"
