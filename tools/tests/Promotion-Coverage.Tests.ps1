Describe 'Main promotion scanner consumes bound PR coverage' -Skip:(!$IsWindows) {
    BeforeEach {
        $fixture = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        $coverageRelative = 'app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml'
        $bound = Join-Path $fixture "evidence/$coverageRelative"
        $live = Join-Path $fixture $coverageRelative
        New-Item -ItemType Directory -Force -Path "$fixture/tools/release",(Split-Path $bound),(Split-Path $live) | Out-Null
        Copy-Item (Join-Path $PSScriptRoot '../release/Promote-LocalEvidence.ps1') "$fixture/tools/release"
        @'
function New-GitHubClient { param($Repository) @{} }
function Invoke-GitHubRest {
    param($Client,$Method,$Path)
    @{merged_at='fixture';state='closed';base=@{ref='main'};head=@{sha=('b'*40)};merge_commit_sha=('a'*40)}
}
Export-ModuleMember -Function New-GitHubClient,Invoke-GitHubRest
'@ | Set-Content "$fixture/tools/GitHub-Api.psm1"
        'function Assert-LocalReleaseMain { param($Client,$Project,$Head) }' | Set-Content "$fixture/tools/release/Local-Release.ps1"
        $python = Join-Path $fixture 'python-fixture.ps1'
        'if ($args[1] -eq ''promote'') { Set-Content (Join-Path $PSScriptRoot ''promote-called.txt'') invoked }; $global:LASTEXITCODE=0' | Set-Content $python
        Set-Content $bound '<report name="verified-pr"><package name="fixture"/></report>'
        Set-Content $live '<report name="stale-release"/>'
        $hash = (Get-FileHash $bound -Algorithm SHA256).Hash.ToLowerInvariant()
        $proof = @{head=('b'*40);base=('c'*40);mode='Full';result='passed';files=@{$coverageRelative=$hash}}
        $evidence = Join-Path $fixture 'evidence/evidence.json'
        $proof | ConvertTo-Json -Depth 5 | Set-Content $evidence
        Set-Content "$fixture/gradlew.bat" "@echo off`r`necho invoked > scanner-called.txt`r`nfindstr /c:verified-pr `"$live`" > nul`r`nif errorlevel 1 exit /b 7`r`nexit /b 0"
        New-Item -ItemType Directory -Force "$fixture/jdk/bin" | Out-Null
        Set-Content "$fixture/jdk/bin/java.exe" 'fixture; scanner is isolated batch adapter'
        $environmentBefore = @{}
        foreach ($name in @('SONAR_ORGANIZATION','SONAR_PROJECT_KEY','SONAR_TOKEN','JAVA_HOME')) {
            $environmentBefore[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
        }
        $env:SONAR_ORGANIZATION='fixture'
        $env:SONAR_PROJECT_KEY='fixture'
        $env:SONAR_TOKEN='fixture-not-a-credential'
        Mock git { $global:LASTEXITCODE=0; 'a'*40 }
        $invoke = {
            & "$fixture/tools/release/Promote-LocalEvidence.ps1" -PullRequestNumber 1 -EvidencePath $evidence -Python $python -JavaHome "$fixture/jdk" -OutputDirectory "$fixture/promoted"
        }
    }
    AfterEach {
        foreach ($name in $environmentBefore.Keys) { [Environment]::SetEnvironmentVariable($name,$environmentBefore[$name],'Process') }
        Get-Module -All | Where-Object { $_.Path -and $_.Path.StartsWith($fixture,[StringComparison]::OrdinalIgnoreCase) } | Remove-Module -Force
    }
    It 'replaces stale live coverage with exact bound bytes before native scanner invocation and retains diagnostics' {
        & $invoke | Out-Null
        Get-FileHash $live -Algorithm SHA256 | Select-Object -ExpandProperty Hash | Should -Be $hash.ToUpperInvariant()
        (Get-Content "$fixture/scanner-called.txt").Trim() | Should -Be invoked
        $old = @(Get-ChildItem "$fixture/app/build/verification" -Recurse -Filter 'previous-jacoco.xml')
        $old.Count | Should -Be 1
        Get-Content $old[0].FullName | Should -Be '<report name="stale-release"/>'
    }
    It 'rejects a missing bound report before scanner invocation' {
        Remove-Item $bound
        { & $invoke } | Should -Throw '*Bound PR JaCoCo report is missing*'
        Test-Path "$fixture/scanner-called.txt" | Should -BeFalse
        Get-Content $live | Should -Be '<report name="stale-release"/>'
    }
    It 'rejects a tampered bound report before scanner invocation' {
        Set-Content $bound '<report name="modified"/>'
        { & $invoke } | Should -Throw '*Bound PR JaCoCo report hash does not match*'
        Test-Path "$fixture/scanner-called.txt" | Should -BeFalse
        Get-Content $live | Should -Be '<report name="stale-release"/>'
    }
    It 'rejects absent coverage binding before scanner invocation' {
        $proof.files=@{}
        $proof | ConvertTo-Json -Depth 5 | Set-Content $evidence
        { & $invoke } | Should -Throw '*Full evidence must bind the PR JaCoCo report*'
        Test-Path "$fixture/scanner-called.txt" | Should -BeFalse
    }
    It 'rejects changed staging bytes before scanner invocation' {
        Mock Copy-Item { [IO.File]::Copy($LiteralPath,$Destination,$true) }
        Mock Copy-Item { Set-Content -LiteralPath $Destination '<report name="modified-copy"/>' } -ParameterFilter { $Destination -eq $live }
        { & $invoke } | Should -Throw '*Staged main Sonar JaCoCo report hash does not match*'
        Test-Path "$fixture/scanner-called.txt" | Should -BeFalse
    }
    It 'rejects scanner-time report mutation before promoting evidence and restores Java environment' {
        Set-Content "$fixture/gradlew.bat" "@echo off`r`necho invoked > scanner-called.txt`r`nfindstr /c:verified-pr `"$live`" > nul`r`nif errorlevel 1 exit /b 7`r`necho changed-during-scan > `"$live`"`r`nexit /b 0"
        $env:JAVA_HOME = 'previous-fixture-java'
        { & $invoke } | Should -Throw '*JaCoCo report changed during analysis*'
        Test-Path "$fixture/scanner-called.txt" | Should -BeTrue
        Test-Path "$fixture/promote-called.txt" | Should -BeFalse
        $env:JAVA_HOME | Should -Be 'previous-fixture-java'
    }
}
