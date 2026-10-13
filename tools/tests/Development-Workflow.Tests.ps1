BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '../release/Development-Review.psm1') -Force
    Import-Module (Join-Path $PSScriptRoot '../release/Emulator-Diagnostics.psm1') -Force
    . (Join-Path $PSScriptRoot '../release/Release-Verification.ps1')
    function New-Event($Action, $Time, $Phase='Implementation') {
        [pscustomobject]@{id='a'; issue=190; action=$Action; phase=$Phase; timeUtc=$Time}
    }
    function Write-PublicFixture($Directory) {
        Set-Content (Join-Path $Directory 'Swypetris.apk') 'fixture'
        $hash = (Get-FileHash (Join-Path $Directory 'Swypetris.apk')).Hash.ToLowerInvariant()
        @{versionName='1.5.0'; versionCode=22; sha256=$hash} | ConvertTo-Json | Set-Content (Join-Path $Directory 'update.json')
        @('Swypetris.apk','update.json') | ForEach-Object {
            '{0}  {1}' -f (Get-FileHash (Join-Path $Directory $_)).Hash.ToLowerInvariant(), $_
        } | Set-Content (Join-Path $Directory 'SHA256SUMS.txt')
    }
}
Describe 'Observed development evidence' {
    It 'counts completed intervals and leaves unfinished work unknown' {
        $start = New-Event Start '2026-10-11T09:00:00Z'
        (Get-DevelopmentIntervals @($start)).unfinished.Count | Should -Be 1
        $result = Get-DevelopmentIntervals @($start, (New-Event Stop '2026-10-11T09:00:05Z'))
        $result.intervals[0].seconds | Should -Be 5
        $result.unfinished.Count | Should -Be 0
    }
    It 'rejects orphaned, duplicate, mismatched and negative events' {
        $start = New-Event Start '2026-10-11T09:00:00Z'
        { Get-DevelopmentIntervals @((New-Event Stop '2026-10-11T09:00:05Z')) } | Should -Throw
        { Get-DevelopmentIntervals @($start,$start) } | Should -Throw
        { Get-DevelopmentIntervals @($start,(New-Event Stop '2026-10-11T08:00:00Z')) } | Should -Throw
        { Get-DevelopmentIntervals @($start,(New-Event Stop '2026-10-11T09:00:05Z' CIWait)) } | Should -Throw
    }
    It 'preserves JSON timestamp instants and fractions in <Culture>' -ForEach @(
        @{Culture='ru-RU'}, @{Culture='en-US'}
    ) {
        $previous = [Threading.Thread]::CurrentThread.CurrentCulture
        try {
            [Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::GetCultureInfo($Culture)
            $events = @((New-Event Start '2026-10-13T09:00:00.125+03:00'),
                (New-Event Stop '2026-10-13T06:00:05.750Z'))
            (Get-DevelopmentIntervals $events).intervals[0].seconds | Should -Be 5.625
            $decoded = $events | ConvertTo-Json | ConvertFrom-Json
            (Get-DevelopmentIntervals $decoded).intervals[0].seconds | Should -Be 5.625
            foreach ($event in $events) { $event.timeUtc = [datetimeoffset]::Parse($event.timeUtc) }
            (Get-DevelopmentIntervals $events).intervals[0].seconds | Should -Be 5.625
        } finally { [Threading.Thread]::CurrentThread.CurrentCulture = $previous }
    }
    It 'reads JSON summary timestamps after the twelfth day in ru-RU' {
        $previous = [Threading.Thread]::CurrentThread.CurrentCulture
        try {
            [Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::GetCultureInfo('ru-RU')
            $file = Join-Path $TestDrive 'dated-summary.json'
            @{suite='Fast'; commit='abc'; startedUtc='2026-10-13T09:00:00.125+03:00';
                result='passed'; durationSeconds=5.625} | ConvertTo-Json | Set-Content $file
            $row = @(Get-DevelopmentCheckRows @($file))
            $row.Count | Should -Be 1
            $row[0].seconds | Should -Be 5.625
        } finally { [Threading.Thread]::CurrentThread.CurrentCulture = $previous }
    }
    It 'retains failures, cached counts, dirty state and release-role outcomes without totaling coverage' {
        $file = Join-Path $TestDrive 'summary.json'
        @{suite='Fast'; commit='abc'; dirty=$true; startedUtc='2026-10-11T09:00:00Z'; result='failed'; durationSeconds=2; reusedTestResults=$true; tests=@{passed=149;skipped=0}} | ConvertTo-Json -Depth 5 | Set-Content $file
        $row = @(Get-DevelopmentCheckRows @($file,$file))
        $row.Count | Should -Be 1
        $row[0].result | Should -Be failed
        $row[0].dirty | Should -BeTrue
        $row[0].counts | Should -Match 'reused=True'
        @{suite='Release'; commit='abc'; startedUtc='2026-10-11T09:00:00Z'; result='passed'; durationSeconds=2; roles=@(@{role='app'; jvmOutcome='UP-TO-DATE'; jvm=@{passed=149;skipped=0}})} | ConvertTo-Json -Depth 5 | Set-Content $file
        @(Get-DevelopmentCheckRows @($file))[0].counts | Should -Match 'outcome=UP-TO-DATE'
    }
    It 'rejects malformed summary input' {
        $file = Join-Path $TestDrive 'bad.json'
        '{}' | Set-Content $file
        { Get-DevelopmentCheckRows @($file) } | Should -Throw
    }
    It 'profiles timed XML cases in descending order without assigning missing timing' {
        $file = Join-Path $TestDrive 'cases.xml'
        '<testsuite><testcase classname="C" name="fast" time="0.2"/><testcase classname="C" name="slow" time="3.4"/><testcase classname="C" name="unknown"/></testsuite>' | Set-Content $file
        $rows = @(Get-SlowestTestCases @($file))
        $rows.Count | Should -Be 2
        $rows[0].test | Should -Be 'C#slow'
        $rows[0].seconds | Should -Be 3.4
    }
    It 'rejects negative XML case timing' {
        $file = Join-Path $TestDrive 'negative.xml'
        '<testsuite><testcase time="-1"/></testsuite>' | Set-Content $file
        { Get-SlowestTestCases @($file) } | Should -Throw
    }
}
Describe 'Bounded diagnostic processes' {
    It 'retains command failure instead of treating output as success' {
        $exe = (Get-Process -Id $PID).Path
        $result = Invoke-BoundedDiagnostic $exe @('-NoProfile','-Command','Write-Output diagnostic; exit 7') (Join-Path $TestDrive 'failure.log')
        $result.exitCode | Should -Be 7
        $result.timedOut | Should -BeFalse
        Get-Content $result.output | Should -Contain diagnostic
    }
    It 'bounds a hung command and preserves the timeout' {
        $exe = (Get-Process -Id $PID).Path
        $result = Invoke-BoundedDiagnostic $exe @('-NoProfile','-Command','Start-Sleep -Seconds 30') (Join-Path $TestDrive 'timeout.log') 1
        $result.timedOut | Should -BeTrue
    }
}
Describe 'Public release consistency' {
    BeforeEach { Write-PublicFixture $TestDrive }
    It 'accepts exact metadata and checksums' {
        (Assert-PublishedReleaseFiles $TestDrive '1.5.0').versionCode | Should -Be 22
    }
    It 'rejects mismatched version and modified APK' {
        { Assert-PublishedReleaseFiles $TestDrive '1.5.1' } | Should -Throw
        Add-Content (Join-Path $TestDrive 'Swypetris.apk') 'corrupt'
        { Assert-PublishedReleaseFiles $TestDrive '1.5.0' } | Should -Throw
    }
    It 'rejects missing and duplicate checksum entries' {
        $file = Join-Path $TestDrive 'SHA256SUMS.txt'
        $lines = @(Get-Content $file)
        $lines[0] | Set-Content $file
        { Assert-PublishedReleaseFiles $TestDrive '1.5.0' } | Should -Throw
        @($lines[0],$lines[0],$lines[1]) | Set-Content $file
        { Assert-PublishedReleaseFiles $TestDrive '1.5.0' } | Should -Throw
    }
    It 'rejects changed metadata even with a regenerated checksum' {
        $file = Join-Path $TestDrive 'update.json'
        $metadata = Get-Content $file -Raw | ConvertFrom-Json
        $metadata.versionCode = 22.5
        $metadata | ConvertTo-Json | Set-Content $file
        { Assert-PublishedReleaseFiles $TestDrive '1.5.0' } | Should -Throw
    }
    It 'binds public release verification to a completed main publication workflow and exact SHA' {
        $run = @{ status='completed'; conclusion='success'; head_sha='abc'; path='.github/workflows/release-check.yml'; event='push'; head_branch='main' }
        { Assert-PublicationRun $run abc } | Should -Not -Throw
        foreach ($property in @('status','conclusion','head_sha','path','event','head_branch')) {
            $invalid = $run.Clone()
            $invalid[$property] = 'wrong'
            { Assert-PublicationRun $invalid abc } | Should -Throw
        }
    }
}
Describe 'Work journal and draft command integration' {
    BeforeEach {
        $root = Join-Path $TestDrive ([guid]::NewGuid().ToString())
        $release = Join-Path $root 'tools/release'
        New-Item -ItemType Directory -Force $release | Out-Null
        foreach ($name in @('Record-EpicWork.ps1','New-EpicReview.ps1','Development-Review.psm1')) {
            Copy-Item (Join-Path $PSScriptRoot "../release/$name") $release
        }
        & git -C $root init --quiet
        & git -C $root -c user.name=Fixture -c user.email=fixture@example.invalid commit --allow-empty -m fixture --quiet
    }
    It 'requires matching stops and never leaves two phases active' {
        $script = Join-Path $release 'Record-EpicWork.ps1'
        & $script -Epic 187 -Issue 190 -Action Start -Phase Implementation | Out-Null
        $journal = Join-Path $root 'app/build/verification/epic-187/work.jsonl'
        $event = Get-Content $journal | ConvertFrom-Json
        { & $script -Epic 187 -Issue 190 -Action Start -Phase Review } | Should -Throw
        { & $script -Epic 187 -Issue 191 -Action Stop -Phase Implementation -Id $event.id } | Should -Throw
        & $script -Epic 187 -Issue 190 -Action Stop -Phase Implementation -Id $event.id | Out-Null
        $events = @(Get-Content $journal | ForEach-Object { $_ | ConvertFrom-Json })
        (Get-DevelopmentIntervals $events).unfinished.Count | Should -Be 0
    }
    It 'creates an ignored evidence draft with incomplete work excluded and without overwriting a public report' {
        & (Join-Path $release 'Record-EpicWork.ps1') -Epic 187 -Issue 190 -Action Start -Phase Implementation | Out-Null
        & (Join-Path $release 'New-EpicReview.ps1') -Epic 187 | Out-Null
        $draft = Get-ChildItem (Join-Path $root 'app/build/verification/epic-187') -Filter 'review-draft-*.md'
        $text = Get-Content $draft.FullName -Raw
        $text | Should -Match 'Unfinished intervals excluded: 1'
        $text | Should -Match 'Unrecorded active work is unknown'
        Test-Path (Join-Path $root 'publishing/reports/epic-187-development-review.md') | Should -BeFalse
    }
}
Describe 'Workflow-run publication provenance' {
    BeforeAll {
        . (Join-Path $PSScriptRoot '../release/Publication-Provenance.ps1')
        function Invoke-GitHubRest { param($Client,$Method,$Path) throw 'Unexpected API request' }
    }
    BeforeEach {
        $sha = 'a' * 40
        $digest = 'sha256:' + ('b' * 64)
        $repo = @{full_name='RoyMatus/Swypetris'}
        $run = @{id=10; run_attempt=2; status='completed'; conclusion='success'; path='.github/workflows/release-check.yml'; event='workflow_run'; head_branch='main'; head_sha=('c'*40); repository=$repo}
        $proof = @{schema=1; repository='RoyMatus/Swypetris'; release_run_id=10; release_run_attempt=2; sha=$sha; source_run_id=20; source_run_attempt=3; source_artifact_id=30; source_artifact_digest=$digest}
        $source = @{id=20; run_attempt=3; workflow_id=40; path='.github/workflows/ci.yml'; repository=$repo; head_repository=$repo; head_sha=$sha; head_branch='main'; event='push'; status='completed'; conclusion='success'}
        $workflow = @{id=40; path='.github/workflows/ci.yml'; state='active'}
        $artifact = @{id=30; expired=$false; name='full-regression-20-3'; digest=$digest; workflow_run=@{id=20; head_sha=$sha; head_branch='main'}}
    }
    It 'binds candidate SHA independently from release workflow control SHA' {
        { Assert-PublicationRun $run $sha $proof $source $workflow $artifact } | Should -Not -Throw
        { Assert-PublicationRun $run $sha } | Should -Throw
    }
    It 'rejects every mismatched provenance identity and malformed identifier' {
        foreach ($key in $proof.Keys) {
            $bad = $proof.Clone()
            $bad[$key] = if ($proof[$key] -is [int]) { 999 } else { 'wrong' }
            { Assert-PublicationRun $run $sha $bad $source $workflow $artifact } | Should -Throw
        }
        foreach ($value in @('20', 20.5, 0, -1)) {
            $bad = $proof.Clone(); $bad.source_run_id = $value
            { Assert-PublicationRun $run $sha $bad $source $workflow $artifact } | Should -Throw
        }
    }
    It 'accepts retired known workflow while keeping historical run identity checks' {
        foreach ($state in @('disabled_manually','disabled_inactivity','deleted')) {
            $retired = $workflow.Clone(); $retired.state = $state
            { Assert-PublicationRun $run $sha $proof $source $retired $artifact } | Should -Not -Throw
        }
    }
    It 'rejects failed, rerun, foreign or unknown source CI' {
        foreach ($key in $source.Keys) {
            $bad = $source.Clone()
            $bad[$key] = if ($source[$key] -is [int]) { 999 } else { 'wrong' }
            { Assert-PublicationRun $run $sha $proof $bad $workflow $artifact } | Should -Throw
        }
        foreach ($key in $workflow.Keys) {
            $bad = $workflow.Clone(); $bad[$key] = 'wrong'
            { Assert-PublicationRun $run $sha $proof $source $bad $artifact } | Should -Throw
        }
    }
    It 'rejects altered, expired, wrong-attempt and wrong-SHA source artifacts' {
        foreach ($key in @('id','name','digest','workflow_run','expired')) {
            $bad = $artifact.Clone(); $bad[$key] = 'wrong'
            { Assert-PublicationRun $run $sha $proof $source $workflow $bad } | Should -Throw
        }
        foreach ($key in @('id','head_sha','head_branch')) {
            $bad = $artifact.Clone(); $bad.workflow_run = $artifact.workflow_run.Clone(); $bad.workflow_run[$key] = 'wrong'
            { Assert-PublicationRun $run $sha $proof $source $workflow $bad } | Should -Throw
        }
    }
    It 'reads only the single expected JSON entry and verifies archive digest' {
        $json = $proof | ConvertTo-Json -Compress
        $stream = [IO.MemoryStream]::new()
        $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $true)
        $writer = [IO.StreamWriter]::new($zip.CreateEntry('publication-provenance.json').Open())
        $writer.Write($json); $writer.Dispose(); $zip.Dispose()
        $bytes = $stream.ToArray(); $stream.Dispose()
        $metadata = @{size_in_bytes=$bytes.Length; digest=('sha256:' + [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant())}
        (Read-PublicationProvenanceArchive $bytes $metadata).sha | Should -BeExactly $sha
        $metadata.digest = 'sha256:' + ('0'*64)
        { Read-PublicationProvenanceArchive $bytes $metadata } | Should -Throw
        $metadata.size_in_bytes = 1
        { Read-PublicationProvenanceArchive $bytes $metadata } | Should -Throw
    }
    It 'rejects traversal and additional entries even with a matching archive hash' {
        foreach ($names in @(@('../publication-provenance.json'), @('publication-provenance.json','unexpected.txt'))) {
            $stream = [IO.MemoryStream]::new()
            $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $true)
            foreach ($name in $names) { $writer = [IO.StreamWriter]::new($zip.CreateEntry($name).Open()); $writer.Write('{}'); $writer.Dispose() }
            $zip.Dispose(); $bytes=$stream.ToArray(); $stream.Dispose()
            $metadata=@{size_in_bytes=$bytes.Length; digest=('sha256:' + [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant())}
            { Read-PublicationProvenanceArchive $bytes $metadata } | Should -Throw
        }
    }
    It 'retrieves the recorded source attempt and rejects a failed recorded result' {
        $publicationArtifact = @{id=50; name='publication-provenance-10-2'; expired=$false; size_in_bytes=100; digest=$digest; workflow_run=@{id=10}}
        $sourceArtifactFixture = $artifact
        Mock Invoke-GitHubRest {
            switch ($Path) {
                'actions/runs/10/artifacts?per_page=100&page=1' { return @{artifacts=@($publicationArtifact)} }
                'actions/runs/20/attempts/3' { return $source }
                'actions/workflows/40' { return $workflow }
                'actions/artifacts/30' { return $sourceArtifactFixture }
                default { throw "Unexpected API request: $Path" }
            }
        }
        Mock Get-PublicationArtifactBytes { return ,([byte[]]@(1,2)) }
        Mock Read-PublicationProvenanceArchive { return $proof }
        (Get-PublicationProvenance @{} $run $sha).source_run_id | Should -Be 20
        Should -Invoke Invoke-GitHubRest -Times 1 -Exactly -ParameterFilter { $Path -eq 'actions/runs/20/attempts/3' }
        Should -Invoke Invoke-GitHubRest -Times 0 -Exactly -ParameterFilter { $Path -eq 'actions/runs/20' }
        $source.conclusion='failure'
        { Get-PublicationProvenance @{} $run $sha } | Should -Throw
    }
}
