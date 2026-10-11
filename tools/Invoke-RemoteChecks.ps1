<#
.SYNOPSIS
Dispatches optional focused feedback for a pushed branch when local execution is unsuitable.
.DESCRIPTION
Requires a clean checkout of Head and refuses an existing open PR, whose required CI
should be reused. A dispatch acceptance is not a completed or passing test result.
No retries, credentials, required-check changes, signing, or publication are performed.
.EXAMPLE
./tools/Invoke-RemoteChecks.ps1 -Head fix/example -Suite FocusedJvm -TestClass GameEngineTest -Reason 'No local JDK; changed collision rules' -WhatIf
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Repository = 'RoyMatus/Swypetris',
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Head,
    [Parameter(Mandatory)][ValidateSet('FocusedJvm', 'FocusedAndroid')][string]$Suite,
    [Parameter(Mandatory)][string[]]$TestClass,
    [Parameter(Mandatory)][ValidateLength(1, 1000)][string]$Reason
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($Reason)) { throw 'Describe the observed host limitation and test risk in -Reason.' }
if ($Head.StartsWith('-') -or $Head.StartsWith('refs/')) { throw '-Head must be a branch name.' }
& git -C $project check-ref-format "refs/heads/$Head"
if ($LASTEXITCODE -ne 0) { throw '-Head must be a valid branch name.' }
$currentBranch = & git -C $project branch --show-current
if ($LASTEXITCODE -ne 0 -or $currentBranch -cne $Head) { throw 'Run this command from the checked-out -Head branch.' }
$dirty = & git -C $project status --porcelain
if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Commit the intended changes before dispatching; the worktree must be clean.' }
$localSha = & git -C $project rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $localSha -notmatch '\A[0-9a-fA-F]{40}\z') { throw 'Cannot resolve the local commit.' }
Import-Module (Join-Path $PSScriptRoot 'Verification.psm1') -Force
$classes = @(Resolve-VerificationClasses -Project $project -Suite $Suite -TestClass $TestClass | Sort-Object -Unique)
# ValidateSet is case-insensitive; use the workflow choice's canonical spelling.
$Suite = if ($Suite -ieq 'FocusedJvm') { 'FocusedJvm' } else { 'FocusedAndroid' }
Import-Module (Join-Path $PSScriptRoot 'GitHub-Api.psm1') -Force
$client = New-GitHubClient -Repository $Repository
$encodedHead = [uri]::EscapeDataString($Head)
$remote = Invoke-GitHubRest -Client $client -Method Get -Path "git/ref/heads/$encodedHead"
if ($remote.object.type -ne 'commit' -or $remote.object.sha -ne $localSha) {
    throw 'The remote branch differs from the local commit. Push the intended branch before dispatching.'
}
$owner = $Repository.Split('/')[0]
$filter = [uri]::EscapeDataString("${owner}:$Head")
$pulls = @(Invoke-GitHubRest -Client $client -Method Get -Path "pulls?state=open&head=$filter&per_page=1")
if ($pulls.Count -gt 0) { throw 'An open PR already exists for this branch. Reuse its required CI instead of dispatching duplicate tests.' }
$workflow = Invoke-GitHubRest -Client $client -Method Get -Path 'actions/workflows/developer-check.yml'
if ($workflow.state -ne 'active') { throw 'The developer-check.yml workflow must be active on the default branch before dispatch.' }
$body = @{
    ref = $Head
    inputs = @{
        expected_sha = $localSha
        suite = $Suite
        test_classes = $classes -join ','
        reason = $Reason
    }
}
$status = 'not-dispatched'
if ($PSCmdlet.ShouldProcess("$Repository branch $Head at $localSha", "Dispatch $Suite ($($classes -join ', '))")) {
    # One request only: after an ambiguous network error inspect runs before any manual retry.
    $null = Invoke-GitHubRest -Client $client -Method Post -Path 'actions/workflows/developer-check.yml/dispatches' -Body $body
    $status = 'accepted-not-completed'
}
[pscustomobject]@{
    Status = $status
    Repository = $Repository
    Head = $Head
    Sha = $localSha
    Suite = $Suite
    TestClasses = $classes
    Reason = $Reason
    RunsUrl = "https://github.com/$Repository/actions/workflows/developer-check.yml"
    Evidence = 'Inspect the run for this exact SHA and its result artifacts; dispatch acceptance is not test success.'
}
