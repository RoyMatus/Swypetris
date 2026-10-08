BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '..\GitHub-Api.psm1') -Force

    function New-ReadyPullRequest {
        [pscustomobject]@{ number = 81; state = 'open'; draft = $false; merged = $false; mergeable = $true; mergeable_state = 'clean' }
    }
}

Describe 'Issue project synchronization' {
    It 'passes offline membership, pagination, preview, and permission checks' {
        $output = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'Sync-IssueProject.Checks.ps1')
        $LASTEXITCODE | Should -Be 0
        $output | Should -Contain '12 project synchronization checks passed.'
    }
}

Describe 'Assert-GitHubPullRequestReady' {
    It 'accepts a successful required check from the specified app' {
        $checks = @([pscustomobject]@{ name = 'SonarCloud Code Analysis'; status = 'completed'; conclusion = 'success'; app = [pscustomobject]@{ id = 12526 } })
        $required = @([pscustomobject]@{ context = 'SonarCloud Code Analysis'; app_id = 12526 })
        Assert-GitHubPullRequestReady -PullRequest (New-ReadyPullRequest) -Checks $checks -Statuses @() -RequiredChecks $required
    }

    It 'rejects a missing required check' {
        $checks = @([pscustomobject]@{ name = 'android'; status = 'completed'; conclusion = 'success'; app = [pscustomobject]@{ id = 1 } })
        $required = @([pscustomobject]@{ context = 'SonarCloud Code Analysis'; app_id = 12526 })
        $message = try { Assert-GitHubPullRequestReady -PullRequest (New-ReadyPullRequest) -Checks $checks -Statuses @() -RequiredChecks $required; '' } catch { $_.Exception.Message }
        $message | Should -Match 'missing required check'
    }

    It 'rejects a required check from a different app' {
        $checks = @([pscustomobject]@{ name = 'SonarCloud Code Analysis'; status = 'completed'; conclusion = 'success'; app = [pscustomobject]@{ id = 1 } })
        $required = @([pscustomobject]@{ context = 'SonarCloud Code Analysis'; app_id = 12526 })
        $message = try { Assert-GitHubPullRequestReady -PullRequest (New-ReadyPullRequest) -Checks $checks -Statuses @() -RequiredChecks $required; '' } catch { $_.Exception.Message }
        $message | Should -Match 'missing required check'
    }

    It 'rejects a failed classic commit status' {
        $checks = @([pscustomobject]@{ name = 'android'; status = 'completed'; conclusion = 'success' })
        $statuses = @([pscustomobject]@{ context = 'lint'; state = 'failure' })
        $message = try { Assert-GitHubPullRequestReady -PullRequest (New-ReadyPullRequest) -Checks $checks -Statuses $statuses -RequiredChecks @(); '' } catch { $_.Exception.Message }
        $message | Should -Match 'failed commit statuses'
    }

    It 'accepts a successful classic status without check runs' {
        $statuses = @([pscustomobject]@{ context = 'ci'; state = 'success' })
        $required = @([pscustomobject]@{ context = 'ci'; app_id = $null })
        Assert-GitHubPullRequestReady -PullRequest (New-ReadyPullRequest) -Checks @() -Statuses $statuses -RequiredChecks $required
    }

    It 'rejects a pull request without CI evidence' {
        $message = try { Assert-GitHubPullRequestReady -PullRequest (New-ReadyPullRequest) -Checks @() -Statuses @() -RequiredChecks @(); '' } catch { $_.Exception.Message }
        $message | Should -Match 'no check runs or commit statuses'
    }
}

Describe 'Get-GitHubPullRequestBlockers' {
    It 'returns no blockers for a ready pull request' {
        $checks = @([pscustomobject]@{ name = 'android'; status = 'completed'; conclusion = 'success' })
        $blockers = @(Get-GitHubPullRequestBlockers -PullRequest (New-ReadyPullRequest) -Checks $checks)
        $blockers.Count | Should -Be 0
    }

    It 'reports all applicable blockers and matches the merge assertion' {
        $pr = New-ReadyPullRequest
        $pr.draft = $true
        $pr.mergeable_state = 'blocked'
        $checks = @([pscustomobject]@{ name = 'android'; status = 'in_progress'; conclusion = $null })
        $required = @([pscustomobject]@{ context = 'SonarCloud Code Analysis'; app_id = 12526 })
        $blockers = @(Get-GitHubPullRequestBlockers -PullRequest $pr -Checks $checks -RequiredChecks $required)
        $blockers.Count | Should -Be 4
        $message = try { Assert-GitHubPullRequestReady -PullRequest $pr -Checks $checks -RequiredChecks $required; '' } catch { $_.Exception.Message }
        $message | Should -Be ($blockers -join '; ')
    }

    It 'reports a merged pull request without requesting CI evidence' {
        $pr = New-ReadyPullRequest
        $pr.state = 'closed'
        $pr.merged = $true
        $blockers = @(Get-GitHubPullRequestBlockers -PullRequest $pr)
        $blockers.Count | Should -Be 1
        $blockers[0] | Should -Match 'already merged'
    }

    It 'reports a closed unmerged pull request' {
        $pr = New-ReadyPullRequest
        $pr.state = 'closed'
        $blockers = @(Get-GitHubPullRequestBlockers -PullRequest $pr)
        $blockers.Count | Should -Be 1
        $blockers[0] | Should -Match 'closed'
    }
}

Describe 'Get-GitHubRequiredChecks' {
    BeforeEach {
        Mock Invoke-GitHubRestPaged -ModuleName GitHub-Api {
            @([pscustomobject]@{ type = 'required_status_checks'; parameters = [pscustomobject]@{
                required_status_checks = @([pscustomobject]@{ context = 'android'; integration_id = 15368 })
            } })
        }
        Mock Invoke-GitHubRest -ModuleName GitHub-Api {
            [pscustomobject]@{ required_status_checks = [pscustomobject]@{
                checks = @([pscustomobject]@{ context = 'android'; app_id = 15368 }); contexts = @('android', 'legacy')
            } }
        }
    }
    It 'unions classic and effective ruleset checks without duplicate sources' {
        $checks = @(Get-GitHubRequiredChecks -Client @{} -Branch main)
        $checks.Count | Should -Be 2
        ($checks | Where-Object context -eq android).app_id | Should -Be 15368
        ($checks | Where-Object context -eq legacy).app_id | Should -BeNullOrEmpty
    }
    It 'supports ruleset-only protection after a genuine classic 404' {
        Mock Invoke-GitHubRest -ModuleName GitHub-Api {
            $ex = [Exception]::new('Not Found')
            $ex | Add-Member -NotePropertyName Response -NotePropertyValue ([pscustomobject]@{ StatusCode = 404 })
            throw $ex
        }
        $checks = @(Get-GitHubRequiredChecks -Client @{} -Branch main)
        $checks.Count | Should -Be 1
        $checks[0].app_id | Should -Be 15368
    }
    It 'fails closed on an unavailable classic protection API' {
        Mock Invoke-GitHubRest -ModuleName GitHub-Api { throw 'Service unavailable' }
        { Get-GitHubRequiredChecks -Client @{} -Branch main } | Should -Throw '*Service unavailable*'
    }
    It 'fails closed on an unavailable effective rules API' {
        Mock Invoke-GitHubRestPaged -ModuleName GitHub-Api { throw 'Forbidden' }
        { Get-GitHubRequiredChecks -Client @{} -Branch main } | Should -Throw '*Forbidden*'
    }
}

Describe 'Mandatory CI evidence' {
    It 'rejects a skipped or neutral mandatory check' -ForEach @('skipped', 'neutral') {
        $checks = @([pscustomobject]@{ name='android'; status='completed'; conclusion=$_; app=[pscustomobject]@{ id=15368 } })
        $required = @([pscustomobject]@{context='android';app_id=15368})
        { Assert-GitHubPullRequestReady -PullRequest (New-ReadyPullRequest) -Checks $checks -RequiredChecks $required } | Should -Throw '*no successful result*'
    }
    It 'retains different required sources for the same context' {
        Mock Invoke-GitHubRestPaged -ModuleName GitHub-Api {
            @([pscustomobject]@{type='required_status_checks';parameters=[pscustomobject]@{
                required_status_checks=@([pscustomobject]@{context='android';integration_id=15368})
            }})
        }
        Mock Invoke-GitHubRest -ModuleName GitHub-Api {
            [pscustomobject]@{required_status_checks=[pscustomobject]@{
                checks=@([pscustomobject]@{context='android';app_id=42});contexts=@('android')
            }}
        }
        @(Get-GitHubRequiredChecks -Client @{} -Branch main).Count | Should -Be 2
    }
}
