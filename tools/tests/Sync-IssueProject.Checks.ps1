# Offline integration checks: the copied script imports only the fixture module.
$ErrorActionPreference = 'Stop'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('project-sync-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
Copy-Item (Join-Path $PSScriptRoot '../Sync-IssueProject.ps1') $fixtureRoot
$fixtureModule = @'
function New-GitHubClient { param($Repository) @{ Repository = $Repository } }
function Invoke-GitHubRestPaged { param($Client, $Path) $global:ProjectSyncFixture.Issues }
function Invoke-GitHubRest { param($Client, $Method, $Path) @{ state = 'open' } }
function Invoke-GitHubGraphQL {
    param($Client, $Query, $Variables)
    $state = $global:ProjectSyncFixture
    if ($state.ScopeError) { throw 'INSUFFICIENT_SCOPES' }
    if ($Query -match '^mutation') {
        $state.Writes++
        if (!$state.IgnoreWrites) {
            if ($Query -match 'updateProjectV2ItemFieldValue') {
                ($state.Items | Where-Object id -eq $Variables.item).fieldValues = @{ nodes = @(@{ name = 'Backlog'; field = @{ name = 'Status' } }) }
            } elseif ($Query -match 'addProjectV2ItemById') {
                $state.Items += [pscustomobject]@{ id = 'added'; isArchived = $false; content = @{ id = $Variables.content }; fieldValues = @{ nodes = @() } }
            } else {
                ($state.Items | Where-Object id -eq $Variables.item).isArchived = $false
            }
        }
        return @{}
    }
    if ($Query -notmatch 'archivedStates: \[ARCHIVED, NOT_ARCHIVED\]') { throw 'Archived items were not requested.' }
    $nodes = $state.Items
    $hasNext = $false
    $cursor = $null
    if ($state.Paginated -and !$Variables.after) { $nodes = @(); $hasNext = $true; $cursor = 'page2' }
    if ($state.Paginated -and $Variables.after -ne $null -and $Variables.after -ne 'page2') { throw 'Incorrect cursor.' }
    @{ user = @{ projectV2 = @{ id = 'project'; title = 'Fixture';
        fields = @{ nodes = @(@{ id = 'status'; name = 'Status'; options = @(@{ id = 'backlog'; name = 'Backlog' }, @{ id = 'done'; name = 'Done' }) }); pageInfo = @{ hasNextPage = $false } };
        views = @{ nodes = @(@{ number = 2; name = 'Open'; filter = 'is:open' }); pageInfo = @{ hasNextPage = $false } };
        items = @{ nodes = $nodes; pageInfo = @{ hasNextPage = $hasNext; endCursor = $cursor } }
    } } }
}
Export-ModuleMember -Function New-GitHubClient, Invoke-GitHubRest, Invoke-GitHubRestPaged, Invoke-GitHubGraphQL
'@
Set-Content (Join-Path $fixtureRoot 'GitHub-Api.psm1') $fixtureModule
function Reset-Fixture {
    $global:ProjectSyncFixture = @{
        Issues = @([pscustomobject]@{ number = 1; node_id = 'issue1'; pull_request = $null })
        Items = @(); Writes = 0; IgnoreWrites = $false; ScopeError = $false; Paginated = $false
    }
}
function Assert-Fixture([bool]$Condition, [string]$Message) { if (!$Condition) { throw $Message } }
try {
    $scriptPath = Join-Path $fixtureRoot 'Sync-IssueProject.ps1'
    Reset-Fixture
    $result = & $scriptPath -Inspect
    Assert-Fixture ($result.MissingIssues -contains 1 -and $global:ProjectSyncFixture.Writes -eq 0) 'Inspect wrote or missed an issue.'
    $result = & $scriptPath -WhatIf
    Assert-Fixture ($global:ProjectSyncFixture.Writes -eq 0 -and $result.RemainingIssues -contains 1) 'WhatIf wrote or hid the remaining issue.'
    $result = & $scriptPath
    Assert-Fixture ($result.RemainingIssues.Count -eq 0 -and $result.RemainingStatusIssues.Count -eq 0 -and $global:ProjectSyncFixture.Writes -eq 2) 'Missing issue or status was not repaired.'
    $result = & $scriptPath
    Assert-Fixture ($global:ProjectSyncFixture.Writes -eq 2) 'Second run duplicated an existing item.'
    $global:ProjectSyncFixture.Items[0].fieldValues.nodes[0].name = 'Done'
    $result = & $scriptPath -Inspect
    Assert-Fixture ($result.StaleCompletedIssues -contains 1 -and $global:ProjectSyncFixture.Writes -eq 2) 'Inspect did not report a stale completed status.'
    $result = & $scriptPath
    Assert-Fixture ($result.RemainingStatusIssues.Count -eq 0 -and $global:ProjectSyncFixture.Items[0].fieldValues.nodes[0].name -eq 'Backlog') 'Reopened issue retained Done.'
    $global:ProjectSyncFixture.Items[0].fieldValues.nodes[0].name = 'In Progress'
    $result = & $scriptPath
    Assert-Fixture ($global:ProjectSyncFixture.Items[0].fieldValues.nodes[0].name -eq 'In Progress') 'An active status was overwritten.'
    $global:ProjectSyncFixture.Items[0].isArchived = $true
    $result = & $scriptPath
    Assert-Fixture ($result.ArchivedOpenIssues -contains 1 -and !$global:ProjectSyncFixture.Items[0].isArchived) 'Archived issue was not restored.'
    $global:ProjectSyncFixture.Paginated = $true
    $result = & $scriptPath -Inspect
    Assert-Fixture ($result.MissingIssues.Count -eq 0) 'Project pagination lost an item.'
    $global:ProjectSyncFixture.Issues += [pscustomobject]@{ number = 2; node_id = 'pr2'; pull_request = @{} }
    $result = & $scriptPath -Inspect
    Assert-Fixture ($result.OpenIssueCount -eq 1) 'A pull request was counted as an issue.'
    Reset-Fixture
    $global:ProjectSyncFixture.IgnoreWrites = $true
    $message = try { & $scriptPath | Out-Null; '' } catch { $_.Exception.Message }
    Assert-Fixture ($message -match 'not confirmed') 'An unconfirmed write was accepted.'
    Reset-Fixture
    $global:ProjectSyncFixture.ScopeError = $true
    $message = try { & $scriptPath | Out-Null; '' } catch { $_.Exception.Message }
    Assert-Fixture ($message -match 'access is unavailable' -and $global:ProjectSyncFixture.Writes -eq 0) 'Permission failure was not surfaced.'
    '12 project synchronization checks passed.'
} finally {
    Remove-Module GitHub-Api -ErrorAction SilentlyContinue
    Remove-Variable ProjectSyncFixture -Scope Global -ErrorAction SilentlyContinue
    # Verify the exact temporary directory before deleting fixture files.
    $resolved = [System.IO.Path]::GetFullPath($fixtureRoot)
    $tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    if (!$resolved.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Fixture cleanup escaped the temporary directory.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
