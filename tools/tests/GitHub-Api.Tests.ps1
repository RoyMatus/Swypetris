BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '..\GitHub-Api.psm1') -Force

    function New-ReadyPullRequest {
        [pscustomobject]@{ number = 81; state = 'open'; draft = $false; merged = $false; mergeable = $true; mergeable_state = 'clean' }
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
