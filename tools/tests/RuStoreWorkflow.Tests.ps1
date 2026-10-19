BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '../release/RuStore-Workflow.psm1') -Force
}
Describe 'RuStore fail-closed delivery decisions' {
    BeforeEach {
        $candidate=@{versionCode=36;versionName='1.11.3';apkSha256=('a'*64)}
        $version=@{versionId=123;versionCode=36;versionName='1.11.3';versionStatus='DRAFT';partialValue=100}
        $detail=@{versionId=123;publishType='MANUAL';versionStatus='DRAFT';packages=@()}
    }
    It 'does not interpret a hidden package array as permission to upload again' {
        (Get-RuStoreDeliveryDecision $candidate $version $detail -UploadAttempted).action | Should -Be 'InspectConsole'
    }
    It 'requires console clearance before the first upload into an existing draft' {
        (Get-RuStoreDeliveryDecision $candidate $version $detail).action | Should -Be 'InspectConsole'
        (Get-RuStoreDeliveryDecision $candidate $version $detail -DraftCleared).action | Should -Be 'Upload'
    }
    It 'allows a hidden DRAFT only after this one-time upload was acknowledged and reviewed' {
        (Get-RuStoreDeliveryDecision $candidate $version $detail -UploadAttempted -Acknowledged).action | Should -Be 'Verify'
        (Get-RuStoreDeliveryDecision $candidate $version $detail -UploadAttempted -Acknowledged -Reviewed).action | Should -Be 'Submit'
        $version.versionCode=-1;$version.versionName=$null
        (Get-RuStoreDeliveryDecision $candidate $version $detail -UploadAttempted -Acknowledged -Reviewed).action | Should -Be 'InspectConsole'
    }
    It 'canonicalizes object keys while preserving array order and empty/null values' {
        (Get-RuStoreDigest @{b=@();a=@{y=$null;x=1}}) | Should -Be (Get-RuStoreDigest ([pscustomobject]@{a=[pscustomobject]@{x=1;y=$null};b=@()}))
        (Get-RuStoreDigest @{a=@(1,2)}) | Should -Not -Be (Get-RuStoreDigest @{a=@(2,1)})
        (Get-RuStoreDigest @{a=@()}) | Should -Not -Be (Get-RuStoreDigest @{a=$null})
    }
    It 'rejects materialized duplicate main packages' {
        $p=@{isMainApk=$true;verCode=36;verName='1.11.3'}
        $detail.packages=@($p,$p)
        { Get-RuStoreDeliveryDecision $candidate $version $detail } | Should -Throw '*one main*'
    }
    It 'rejects a draft pinned to an earlier code' {
        $version.versionCode=35
        { Get-RuStoreDeliveryDecision $candidate $version $detail -DraftCleared } | Should -Throw '*version*'
    }
    It 'requires independently reviewed bytes before moderation submission' {
        $detail.packages=@(@{isMainApk=$true;verCode=36;verName='1.11.3'})
        (Get-RuStoreDeliveryDecision $candidate $version $detail).action | Should -Be 'Verify'
        (Get-RuStoreDeliveryDecision $candidate $version $detail -Reviewed).action | Should -Be 'Submit'
    }
    It 'never publishes a version still in moderation' {
        $version.versionStatus='MODERATION';$detail.versionStatus='MODERATION'
        (Get-RuStoreDeliveryDecision $candidate $version $detail -Reviewed).action | Should -Be 'Wait'
    }
    It 'requires both actual approval endpoints and reviewed evidence for publication' {
        $version.versionStatus='READY_FOR_PUBLICATION';$detail.versionStatus='READY_FOR_PUBLICATION'
        (Get-RuStoreDeliveryDecision $candidate $version $detail -Reviewed).action | Should -Be 'Publish'
        (Get-RuStoreDeliveryDecision $candidate $version $detail).action | Should -Be 'Verify'
        $version.versionStatus='MODERATION'
        (Get-RuStoreDeliveryDecision $candidate $version $detail -Reviewed).action | Should -Be 'Wait'
    }
    It 'does not use the v2 detail partialValue sentinel instead of version audience' {
        $detail.partialValue=-1
        $version.partialValue=50
        { Get-RuStoreDeliveryDecision $candidate $version $detail } | Should -Throw '*audience*'
    }
    It 'persists an exclusive attempt before a possibly ambiguous write and refuses repetition' {
        $path=Join-Path $TestDrive 'attempt.json'
        New-RuStoreAttempt -Path $path -Action Upload -CandidateDigest ('a'*64) -VersionId 123
        (Get-Content $path -Raw|ConvertFrom-Json).action | Should -Be 'Upload'
        { New-RuStoreAttempt -Path $path -Action Upload -CandidateDigest ('a'*64) -VersionId 123 } | Should -Throw
    }
}
Describe 'Exact store APK verification' {
    It 'rejects unsafe remote paths and existing output before making a request' {
        {Receive-RuStoreArtifact -ApkUid 'https://evil.invalid/app.zip' -ExpectedBytes 1 -Destination (Join-Path $TestDrive 'bad.zip')}|Should -Throw '*server artifact path*'
        $file=Join-Path $TestDrive 'existing.zip';Set-Content $file 'preserved'
        {Receive-RuStoreArtifact -ApkUid '2026/10/19/6a/0b4e131d-4cf4-4902-8a8d-da5ea33b85fb.zip' -ExpectedBytes 1 -Destination $file}|Should -Throw '*already exists*'
        Get-Content $file|Should -Be 'preserved'
    }
    BeforeEach {
        $apk=Join-Path $TestDrive 'original.apk';[IO.File]::WriteAllText($apk,'reviewed signed bytes')
        $hash=(Get-FileHash $apk).Hash.ToLowerInvariant()
    }
    It 'compares raw APK bytes' {
        (Test-RuStoreArtifact -Path $apk -ExpectedSha256 $hash -ExpectedBytes (Get-Item $apk).Length).apkSha256 | Should -Be $hash
        { Test-RuStoreArtifact -Path $apk -ExpectedSha256 ('b'*64) -ExpectedBytes (Get-Item $apk).Length } | Should -Throw '*identity*'
    }
    It 'verifies the one contained APK rather than comparing compressed ZIP and APK lengths' {
        $zip=Join-Path $TestDrive 'package.zip'
        $z=[IO.Compression.ZipFile]::Open($zip,'Create')
        try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,$apk,'server.apk')|Out-Null } finally {$z.Dispose()}
        (Test-RuStoreArtifact -Path $zip -ExpectedSha256 $hash -ExpectedBytes (Get-Item $apk).Length).apkSha256 | Should -Be $hash
    }
    It 'rejects ambiguous APK archives and unsafe paths without extracting anything' {
        foreach($entry in @('second.apk','../escape.apk')) {
            $zip=Join-Path $TestDrive ([guid]::NewGuid().ToString()+'.zip')
            $z=[IO.Compression.ZipFile]::Open($zip,'Create')
            try {
                [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,$apk,'server.apk')|Out-Null
                [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z,$apk,$entry)|Out-Null
            } finally {$z.Dispose()}
            { Test-RuStoreArtifact -Path $zip -ExpectedSha256 $hash -ExpectedBytes (Get-Item $apk).Length } | Should -Throw
        }
    }
}
