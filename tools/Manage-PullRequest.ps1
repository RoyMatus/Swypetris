<#
.SYNOPSIS
Creates, inspects, or merges a Swypetris pull request through the GitHub API.
.EXAMPLE
./tools/Manage-PullRequest.ps1 -Action Create -IssueNumber 70 -Head feat/issue-70-launcher-icon -Title 'Launcher icon' -BodyFile .\pr-body.md -WhatIf
.EXAMPLE
./tools/Manage-PullRequest.ps1 -Action Inspect -PullRequestNumber 72
.EXAMPLE
./tools/Manage-PullRequest.ps1 -Action Merge -PullRequestNumber 72 -WhatIf
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][ValidateSet('Create', 'Inspect', 'Merge')][string]$Action,
    [string]$Repository = 'RoyMatus/Swypetris',
    [ValidateRange(1, 1000000)][int]$IssueNumber,
    [string]$Head,
    [string]$Base = 'main',
    [string]$Title,
    [string]$BodyFile,
    [ValidateRange(1, 1000000)][int]$PullRequestNumber
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'GitHub-Api.psm1') -Force
$client = New-GitHubClient -Repository $Repository

function Get-PullRequestChecks([string]$Sha) {
    $response = Invoke-GitHubRest -Client $client -Method Get -Path "commits/$Sha/check-runs?per_page=100"
    $checks = @($response.check_runs | Where-Object { $_ })
    if ($response.total_count -gt $checks.Count) { throw 'More than 100 check runs exist; refusing to assess an incomplete list.' }
    return $checks
}

if ($Action -eq 'Create') {
    if (!$IssueNumber -or !$Head -or !$Title -or !$BodyFile) {
        throw 'Create requires -IssueNumber, -Head, -Title, and -BodyFile.'
    }
    if ($Head -eq $Base) { throw 'Head and base branches must differ.' }
    $path = (Resolve-Path -LiteralPath $BodyFile -ErrorAction Stop).Path
    $body = [System.IO.File]::ReadAllText($path).Trim()
    if (!$body) { throw 'BodyFile must contain a pull request description.' }
    $null = Invoke-GitHubRest -Client $client -Method Get -Path "issues/$IssueNumber"
    $null = Invoke-GitHubRest -Client $client -Method Get -Path "git/ref/heads/$Head"
    if ($body -notmatch "(?im)\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#$IssueNumber\b") {
        $body = "$body`n`nCloses #$IssueNumber"
    }
    if ($PSCmdlet.ShouldProcess("$Repository $Head -> $Base", 'Create pull request')) {
        $pr = Invoke-GitHubRest -Client $client -Method Post -Path 'pulls' -Body @{ title = $Title; head = $Head; base = $Base; body = $body }
        [pscustomobject]@{ Number = $pr.number; Url = $pr.html_url; State = $pr.state; Draft = $pr.draft }
    }
    return
}

if (!$PullRequestNumber) { throw "$Action requires -PullRequestNumber." }
$pr = Invoke-GitHubRest -Client $client -Method Get -Path "pulls/$PullRequestNumber"
$checks = @(Get-PullRequestChecks $pr.head.sha)
$checkSummary = @($checks | ForEach-Object { "$($_.name): $($_.status)/$($_.conclusion)" })

if ($Action -eq 'Inspect') {
    [pscustomobject]@{
        Number = $pr.number
        Url = $pr.html_url
        State = $pr.state
        Draft = $pr.draft
        Merged = $pr.merged
        Head = $pr.head.sha
        Mergeable = $pr.mergeable
        MergeableState = $pr.mergeable_state
        Checks = $checkSummary
    }
    return
}

Assert-GitHubPullRequestReady -PullRequest $pr -Checks $checks

if ($PSCmdlet.ShouldProcess("$Repository PR #$PullRequestNumber", 'Merge via squash')) {
    $result = Invoke-GitHubRest -Client $client -Method Put -Path "pulls/$PullRequestNumber/merge" -Body @{ merge_method = 'squash' }
    if ($result.merged -ne $true) { throw "GitHub did not confirm the merge of PR #$PullRequestNumber." }
    [pscustomobject]@{ Number = $PullRequestNumber; Merged = $result.merged; Sha = $result.sha; Message = $result.message }
}
