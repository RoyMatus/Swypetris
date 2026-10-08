function New-GitHubClient {
    param([Parameter(Mandatory)][string]$Repository)

    if ($Repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') {
        throw 'Repository must have the form owner/name.'
    }
    $token = if ($env:GH_TOKEN) { $env:GH_TOKEN } elseif ($env:GITHUB_TOKEN) { $env:GITHUB_TOKEN } else {
        $credential = "protocol=https`nhost=github.com`n`n" | & git credential fill
        if ($LASTEXITCODE -ne 0) { throw 'Git Credential Manager did not return a GitHub credential.' }
        ($credential | Where-Object { $_ -like 'password=*' } | Select-Object -First 1) -replace '^password=', ''
    }
    if (!$token) { throw 'Set GH_TOKEN/GITHUB_TOKEN or sign in through Git Credential Manager.' }
    [pscustomobject]@{
        Repository = $Repository
        Headers = @{
            Authorization = "Bearer $token"
            Accept = 'application/vnd.github+json'
            'X-GitHub-Api-Version' = '2022-11-28'
        }
    }
}

function Invoke-GitHubRest {
    param(
        [Parameter(Mandatory)]$Client,
        [Parameter(Mandatory)][ValidateSet('Get', 'Post', 'Patch', 'Put', 'Delete')][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [hashtable]$Body
    )

    $uri = "https://api.github.com/repos/$($Client.Repository)/$Path"
    if ($Method -eq 'Get') { return Invoke-RestMethod -Uri $uri -Method Get -Headers $Client.Headers }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $json = $Body | ConvertTo-Json -Depth 12 -Compress
        return Invoke-RestMethod -Uri $uri -Method $Method -Headers $Client.Headers -ContentType 'application/json' -Body $json
    }
    return Invoke-RestMethod -Uri $uri -Method $Method -Headers $Client.Headers
}

function Invoke-GitHubRestPaged {
    param(
        [Parameter(Mandatory)]$Client,
        [Parameter(Mandatory)][string]$Path
    )

    $separator = if ($Path.Contains('?')) { '&' } else { '?' }
    $page = 1
    do {
        $response = @(Invoke-GitHubRest -Client $Client -Method Get -Path "${Path}${separator}per_page=100&page=$page")
        $items = @(foreach ($entry in $response) { foreach ($item in @($entry)) { $item } })
        foreach ($item in $items) { Write-Output $item }
        $page++
    } while ($items.Count -eq 100)
}

function Invoke-GitHubGraphQL {
    param(
        [Parameter(Mandatory)]$Client,
        [Parameter(Mandatory)][string]$Query,
        [Parameter(Mandatory)][hashtable]$Variables
    )

    $body = @{ query = $Query; variables = $Variables } | ConvertTo-Json -Depth 12 -Compress
    $result = Invoke-RestMethod -Uri 'https://api.github.com/graphql' -Method Post -Headers $Client.Headers -ContentType 'application/json' -Body $body
    if ($result.errors) {
        $messages = @($result.errors | ForEach-Object { $_.message })
        if (@($result.errors | Where-Object { $_.type -eq 'INSUFFICIENT_SCOPES' }).Count -gt 0) {
            throw 'GitHub GraphQL access is unavailable (INSUFFICIENT_SCOPES). Check the token permissions.'
        }
        throw ($messages -join '; ')
    }
    return $result.data
}

function Get-GitHubPullRequestBlockers {
    param(
        [Parameter(Mandatory)]$PullRequest,
        [array]$Checks = @(),
        [array]$Statuses = @(),
        [array]$RequiredChecks = @()
    )

    $number = $PullRequest.number
    $blockers = [System.Collections.Generic.List[string]]::new()
    if ($PullRequest.merged) {
        $blockers.Add("PR #$number is already merged.")
        return $blockers.ToArray()
    }
    if ($PullRequest.state -ne 'open') {
        $blockers.Add("PR #$number is closed.")
        return $blockers.ToArray()
    }
    if ($PullRequest.draft) {
        $blockers.Add("PR #$number is a draft.")
    }
    if ($PullRequest.mergeable -ne $true -or $PullRequest.mergeable_state -ne 'clean') {
        $blockers.Add("PR #$number is not ready to merge (state: $($PullRequest.mergeable_state)).")
    }
    if ($Checks.Count -eq 0 -and $Statuses.Count -eq 0) {
        $blockers.Add("PR #$number has no check runs or commit statuses; refusing to merge without CI evidence.")
    }
    $badChecks = @($Checks | Where-Object { $_.status -ne 'completed' -or $_.conclusion -notin @('success', 'neutral', 'skipped') })
    if ($badChecks.Count -gt 0) {
        $summary = @($badChecks | ForEach-Object { "$($_.name): $($_.status)/$($_.conclusion)" }) -join '; '
        $blockers.Add("PR #$number has incomplete or failed checks: $summary")
    }
    $badStatuses = @($Statuses | Where-Object { $_.state -ne 'success' })
    if ($badStatuses.Count -gt 0) {
        $summary = @($badStatuses | ForEach-Object { "$($_.context): $($_.state)" }) -join '; '
        $blockers.Add("PR #$number has pending or failed commit statuses: $summary")
    }
    foreach ($required in $RequiredChecks) {
        $matchingChecks = @($Checks | Where-Object {
            $_.name -eq $required.context -and
            ($null -eq $required.app_id -or $required.app_id -eq -1 -or $_.app.id -eq $required.app_id)
        })
        $matchingStatuses = if ($null -eq $required.app_id -or $required.app_id -eq -1) {
            @($Statuses | Where-Object { $_.context -eq $required.context })
        } else { @() }
        if ($matchingChecks.Count -eq 0 -and $matchingStatuses.Count -eq 0) {
            $blockers.Add("PR #$number is missing required check '$($required.context)' (app: $($required.app_id)).")
        } elseif (@($matchingChecks | Where-Object { $_.status -eq 'completed' -and $_.conclusion -eq 'success' }).Count -eq 0 -and
                  @($matchingStatuses | Where-Object state -eq 'success').Count -eq 0) {
            $blockers.Add("PR #$number required check '$($required.context)' has no successful result.")
        }
    }
    return $blockers.ToArray()
}

function Assert-GitHubPullRequestReady {
    param(
        [Parameter(Mandatory)]$PullRequest,
        [array]$Checks = @(),
        [array]$Statuses = @(),
        [array]$RequiredChecks = @()
    )

    $blockers = @(Get-GitHubPullRequestBlockers -PullRequest $PullRequest -Checks $Checks -Statuses $Statuses -RequiredChecks $RequiredChecks)
    if ($blockers.Count -gt 0) { throw ($blockers -join '; ') }
}


function Get-GitHubRequiredChecks {
    param([Parameter(Mandatory)]$Client, [Parameter(Mandatory)][string]$Branch)

    $encoded = [uri]::EscapeDataString($Branch)
    # This endpoint includes active repository/inherited rulesets, not classic protection.
    $rules = @(Invoke-GitHubRestPaged -Client $Client -Path "rules/branches/$encoded")
    $checks = @(foreach ($rule in $rules) {
        if ($rule.type -eq 'required_status_checks') {
            foreach ($check in $rule.parameters.required_status_checks) {
                [pscustomobject]@{ context = $check.context; app_id = $check.integration_id }
            }
        }
    })
    try {
        $classic = Invoke-GitHubRest -Client $Client -Method Get -Path "branches/$encoded/protection"
    } catch {
        if ([int]$_.Exception.Response.StatusCode -ne 404) { throw }
        $classic = $null
    }
    $checks += @($classic.required_status_checks.checks | Where-Object { $_ })
    foreach ($context in @($classic.required_status_checks.contexts | Where-Object { $_ })) {
        if (@($checks | Where-Object context -eq $context).Count -eq 0) {
            $checks += [pscustomobject]@{ context = $context; app_id = $null }
        }
    }
    # Keep distinct sources for the same name: every applicable restriction must hold.
    $checks | Sort-Object context, app_id -Unique
}

Export-ModuleMember -Function Get-GitHubRequiredChecks, New-GitHubClient, Invoke-GitHubRest, Invoke-GitHubRestPaged, Invoke-GitHubGraphQL, Get-GitHubPullRequestBlockers, Assert-GitHubPullRequestReady
