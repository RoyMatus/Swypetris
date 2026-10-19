BeforeAll { Import-Module (Join-Path $PSScriptRoot '../release/Delivery-Evidence.psm1') -Force }
Describe 'Portable evidence and conservative cleanup' {
    It 'accepts only matching run IDs and the complete measured matrices' {
        $preview=@{schema=1;test='next-preview-matrix';runId=('a'*32);phases=@{capture=@{count=980};compare=@{count=784};update=@{count=980}}}
        $native=@{schema=1;test='theme-native-pool';runId=('a'*32);phases=@{'select-load'=@{count=12};'load-wait'=@{count=12};'play-assertions'=@{count=12}}}
        (Test-DeliveryPhaseProfiles @($preview,$native) ('a'*32)).verified|Should -BeTrue
        {Test-DeliveryPhaseProfiles @($preview,$native) ('b'*32)}|Should -Throw '*identity*'
        {Test-DeliveryPhaseProfiles @($preview,$preview) ('a'*32)}|Should -Throw '*identity*'
        $preview.phases.capture.count=979
        {Test-DeliveryPhaseProfiles @($preview,$native) ('a'*32)}|Should -Throw '*coverage*'
        $preview.phases.capture.count=980;$native.phases.'load-wait'.count=11
        {Test-DeliveryPhaseProfiles @($preview,$native) ('a'*32)}|Should -Throw '*coverage*'
    }
    It 'removes an owned fixture worktree with long Windows build paths without force' -Skip:(!$IsWindows) {
        $repo=Join-Path $TestDrive 'repository';$tree=Join-Path $TestDrive 'long-path-tree'
        $null=New-Item -ItemType Directory $repo
        & git -C $repo init --quiet
        & git -C $repo -c user.name=Fixture -c user.email=fixture@example.invalid commit --allow-empty -m base --quiet
        & git -C $repo worktree add --detach $tree --quiet
        if($LASTEXITCODE){throw 'Cannot create owned Git fixture.'}
        $folder=Join-Path $tree ('app/build/'+('nested/'*40))
        $null=New-Item -ItemType Directory -Force $folder
        [IO.File]::WriteAllText((Join-Path $folder 'generated.txt'),'regenerable fixture')
        'app/build/'|Set-Content (Join-Path $tree '.gitignore')
        & git -C $tree add .gitignore
        & git -C $tree -c user.name=Fixture -c user.email=fixture@example.invalid commit -m ignore --quiet
        & git -C $repo config core.longpaths false
        {Remove-DeliveryWorktree -Project $repo -Path $tree}|Should -Not -Throw
        Test-Path $tree|Should -BeFalse
    }
    It 'rejects changed evidence and path escape before declaring the archive complete' {
        $root=Join-Path $TestDrive 'source';$null=New-Item -ItemType Directory $root
        Set-Content (Join-Path $root 'log.txt') 'passing check'
        @{files=@{'log.txt'=('a'*64)}}|ConvertTo-Json|Set-Content (Join-Path $root 'evidence.json')
        { Save-DeliveryEvidence -EvidencePath (Join-Path $root 'evidence.json') -Destination (Join-Path $TestDrive 'copy') } | Should -Throw '*hash*'
        @{files=@{'../outside.txt'=('a'*64)}}|ConvertTo-Json|Set-Content (Join-Path $root 'evidence.json')
        { Save-DeliveryEvidence -EvidencePath (Join-Path $root 'evidence.json') -Destination (Join-Path $TestDrive 'escape') } | Should -Throw '*path*'
    }
    It 'revalidates archived bytes and never overwrites an archive' {
        $root=Join-Path $TestDrive 'good';$null=New-Item -ItemType Directory $root
        $file=Join-Path $root 'log.txt';Set-Content $file 'passed'
        @{files=@{'log.txt'=(Get-FileHash $file).Hash.ToLowerInvariant()}}|ConvertTo-Json|Set-Content (Join-Path $root 'evidence.json')
        $dest=Join-Path $TestDrive 'archive'
        Save-DeliveryEvidence -EvidencePath (Join-Path $root 'evidence.json') -Destination $dest
        (Test-DeliveryEvidence $dest).verified | Should -BeTrue
        { Save-DeliveryEvidence -EvidencePath (Join-Path $root 'evidence.json') -Destination $dest } | Should -Throw '*exists*'
        Set-Content (Join-Path $dest 'reports/log.txt') 'changed'
        {Test-DeliveryEvidence $dest} | Should -Throw '*hash*'
    }
    It 'preserves open PRs, changes, unmerged work, active directories and unarchived artifacts' {
        foreach($flag in @('openPr','dirty','unmerged','active','artifactsUnarchived')) {
            $state=@{openPr=$false;dirty=$false;unmerged=$false;active=$false;artifactsUnarchived=$false;merged=$true}
            $state[$flag]=$true
            (Get-DeliveryCleanupDecision $state).remove | Should -BeFalse
        }
    }
    It 'permits a confirmed squash-merged clean unused tree only after archival' {
        (Get-DeliveryCleanupDecision @{merged=$true;openPr=$false;dirty=$false;unmerged=$false;active=$false;artifactsUnarchived=$false}).remove | Should -BeTrue
        (Get-DeliveryCleanupDecision @{}).remove | Should -BeFalse
    }
}
