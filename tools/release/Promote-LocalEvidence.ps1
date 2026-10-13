<# Promote a passed Full PR gate to identical squash-merged main, with fresh main Sonar analysis. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateRange(1,1000000)][int]$PullRequestNumber,
    [Parameter(Mandatory)][string]$EvidencePath,
    [Parameter(Mandatory)][string]$Python,
    [Parameter(Mandatory)][string]$JavaHome,
    [ValidateRange(1,64)][int]$MaxWorkers = 4,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
if (!$IsWindows) { throw 'Mandatory promotion requires the Windows local executor.' }
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $project 'tools/GitHub-Api.psm1') -Force
. (Join-Path $PSScriptRoot 'Local-Release.ps1')
$client = New-GitHubClient 'RoyMatus/Swypetris'
$head = (& git -C $project rev-parse HEAD).Trim()
Assert-LocalReleaseMain $client $project $head
$pr = Invoke-GitHubRest -Client $client -Method Get -Path "pulls/$PullRequestNumber"
$source = Get-Content -LiteralPath $EvidencePath -Raw | ConvertFrom-Json
if (!$pr.merged_at -or $pr.state -cne 'closed' -or $pr.base.ref -cne 'main' -or
    $pr.head.sha -cne $source.head -or $pr.merge_commit_sha -cne $head -or
    $source.mode -cne 'Full' -or $source.result -cne 'passed') {
    throw 'PR merge identity or source Full evidence does not match current main.'
}
$helper = Join-Path $project 'tools/ci/local_verification.py'
& $Python $helper preflight-promotion --root $project --head $head --base $source.base --evidence $EvidencePath
if ($LASTEXITCODE -ne 0) { throw 'Source evidence or identical-tree preflight failed.' }
foreach ($name in @('SONAR_ORGANIZATION','SONAR_PROJECT_KEY','SONAR_TOKEN')) {
    if (![Environment]::GetEnvironmentVariable($name)) { throw "Missing $name; configure it locally." }
}
$java = Join-Path $JavaHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $java)) { throw 'JDK 21 was not found under -JavaHome.' }
if (!$OutputDirectory) { $OutputDirectory = Join-Path $project ('app/build/verification/promotion-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')) }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Promotion output directory must be new.' }
$stage = Join-Path $project ('app/build/verification/promotion-stage-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$null = New-Item -ItemType Directory -Path $stage -Force
$log = Join-Path $stage 'sonar.log'
$oldJava = $env:JAVA_HOME
$timer = [Diagnostics.Stopwatch]::StartNew()
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $gradle = Join-Path $project 'gradlew.bat'
    Push-Location $project
    try {
        & $gradle sonar '-Dsonar.qualitygate.wait=true' "-Dsonar.scm.revision=$head" '-Dsonar.branch.name=main' '--build-cache' '--console=plain' "--max-workers=$MaxWorkers" *> $log
        $code = $LASTEXITCODE
    } finally { Pop-Location }
    $timer.Stop()
    if ($code -ne 0) { throw "Main Sonar analysis failed; retained log: $log" }
    Assert-LocalReleaseMain $client $project $head
    & $Python $helper promote --root $project --head $head --base $source.base --evidence $EvidencePath `
        --destination $OutputDirectory --pr-number $PullRequestNumber --sonar-log $log --sonar-seconds $timer.Elapsed.TotalSeconds
    if ($LASTEXITCODE -ne 0) { throw "Promotion validation failed; retained main Sonar log: $log" }
    Write-Output "Promoted exact-main Full evidence: $OutputDirectory/evidence.json"
} finally {
    $env:JAVA_HOME = $oldJava
}
