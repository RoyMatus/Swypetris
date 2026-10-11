BeforeAll {
    . (Join-Path $PSScriptRoot '../release/Local-Release.ps1')
    function New-Proof {
        return @{
            schema=1; kind='swypetris-local-release'; repository='RoyMatus/Swypetris'; result='passed'
            commit=('a' * 40); version='1.2.3'; regressionSha256=('b' * 64)
            certificates=@{app=('c' * 64);upload=('d' * 64)}
            artifacts=@(foreach ($name in @('Swypetris.apk','Swypetris-1.2.3.aab','update.json','SHA256SUMS.txt')) {
                @{name=$name;size=1;sha256=('e' * 64)}
            })
            smoke=@{result='passed';serial='emulator-5560';files=@(
                @{name='observations.json';size=1;sha256=('f' * 64)},
                @{name='logcat.txt';size=1;sha256=('f' * 64)}
            )}
        }
    }
    function Invoke-GitHubRest { param($Client,$Method,$Path) }
    function git { param([Parameter(ValueFromRemainingArguments)]$Arguments) }
    function Invoke-ReleaseAdbFixture { param([Parameter(ValueFromRemainingArguments)]$Arguments) }
}
Describe 'Local release provenance' {
    It 'accepts the complete exact-commit artifact and signed runtime record' {
        { Assert-LocalReleaseProvenance (New-Proof) ('a' * 40) '1.2.3' } | Should -Not -Throw
    }
    It 'rejects a different commit or version' {
        { Assert-LocalReleaseProvenance (New-Proof) ('b' * 40) '1.2.3' } | Should -Throw
        { Assert-LocalReleaseProvenance (New-Proof) ('a' * 40) '1.2.4' } | Should -Throw
    }
    It 'rejects incomplete failed or foreign provenance' {
        foreach ($field in @('schema','kind','repository','result','regressionSha256')) {
            $proof = New-Proof
            $proof[$field] = 'invalid'
            { Assert-LocalReleaseProvenance $proof ('a' * 40) '1.2.3' } | Should -Throw
        }
    }
    It 'rejects absent duplicate or path-traversing artifacts' {
        foreach ($name in @('Swypetris.apk','../Swypetris.apk')) {
            $proof = New-Proof
            $proof.artifacts[1].name = $name
            { Assert-LocalReleaseProvenance $proof ('a' * 40) '1.2.3' } | Should -Throw
        }
        $proof = New-Proof
        $proof.artifacts = @($proof.artifacts[0])
        { Assert-LocalReleaseProvenance $proof ('a' * 40) '1.2.3' } | Should -Throw
    }
    It 'rejects malformed hashes empty artifacts and missing signing evidence' {
        $proof = New-Proof
        $proof.artifacts[0].sha256 = 'wrong'
        { Assert-LocalReleaseProvenance $proof ('a' * 40) '1.2.3' } | Should -Throw
        $proof = New-Proof
        $proof.artifacts[0].size = 0
        { Assert-LocalReleaseProvenance $proof ('a' * 40) '1.2.3' } | Should -Throw
        $proof = New-Proof
        $proof.certificates.upload = ''
        { Assert-LocalReleaseProvenance $proof ('a' * 40) '1.2.3' } | Should -Throw
    }
    It 'rejects missing runtime diagnostics failed smoke or a physical device' {
        $proof = New-Proof
        $proof.smoke.files[0].name = 'other.json'
        { Assert-LocalReleaseProvenance $proof ('a' * 40) '1.2.3' } | Should -Throw
        $proof = New-Proof
        $proof.smoke.result = 'failed'
        { Assert-LocalReleaseProvenance $proof ('a' * 40) '1.2.3' } | Should -Throw
        $proof = New-Proof
        $proof.smoke.serial = 'pixel-7'
        { Assert-LocalReleaseProvenance $proof ('a' * 40) '1.2.3' } | Should -Throw
    }
    It 'detects tampered missing or truncated snapshot files' {
        $path = Join-Path $TestDrive 'Swypetris.apk'
        Set-Content -LiteralPath $path -Value 'original'
        $entry = Get-LocalReleaseArtifact $path
        { Assert-LocalReleaseArtifact $entry $path } | Should -Not -Throw
        Set-Content -LiteralPath $path -Value 'modified'
        { Assert-LocalReleaseArtifact $entry $path } | Should -Throw
        Clear-Content -LiteralPath $path
        { Assert-LocalReleaseArtifact $entry $path } | Should -Throw
        Remove-Item -LiteralPath $path
        { Assert-LocalReleaseArtifact $entry $path } | Should -Throw
    }
}
Describe 'Exact main release prerequisite' {
    BeforeEach {
        Mock git { $global:LASTEXITCODE = 0; if ($Arguments -contains 'rev-parse') { 'a' * 40 } }
        Mock Invoke-GitHubRest { @{object=@{type='commit';sha=('a' * 40)}} }
    }
    It 'accepts clean exact remote main' {
        { Assert-LocalReleaseMain @{} $TestDrive ('a' * 40) } | Should -Not -Throw
    }
    It 'rejects changed remote main' {
        Mock Invoke-GitHubRest { @{object=@{type='commit';sha=('b' * 40)}} }
        { Assert-LocalReleaseMain @{} $TestDrive ('a' * 40) } | Should -Throw '*current remote main*'
    }
    It 'rejects dirty checkout' {
        Mock git { $global:LASTEXITCODE = 0; if ($Arguments -contains 'rev-parse') { 'a' * 40 } else { ' M changed.kt' } }
        { Assert-LocalReleaseMain @{} $TestDrive ('a' * 40) } | Should -Throw '*clean*'
    }
}

Describe 'Release regression snapshot relocation' {
    It 'copies bound Windows report paths without requiring Linux evidence' {
        $source = Join-Path $TestDrive 'source'
        $target = Join-Path $TestDrive 'release/regression'
        New-Item -ItemType Directory -Path "$source/android" -Force | Out-Null
        Set-Content "$source/android/result.xml" '<testsuite/>'
        Set-Content "$source/pester.log" 'passed'
        @{files=@{'android/result.xml'=(Get-FileHash "$source/android/result.xml").Hash.ToLowerInvariant();'pester.log'=(Get-FileHash "$source/pester.log").Hash.ToLowerInvariant()};checks=@(@{name='pester'})} |
            ConvertTo-Json -Depth 6 | Set-Content "$source/evidence.json"
        Copy-LocalReleaseRegression "$source/evidence.json" $target
        (Get-FileHash "$target/evidence.json").Hash | Should -Be (Get-FileHash "$source/evidence.json").Hash
        (Get-FileHash "$target/android/result.xml").Hash | Should -Be (Get-FileHash "$source/android/result.xml").Hash
        (Get-FileHash "$target/pester.log").Hash | Should -Be (Get-FileHash "$source/pester.log").Hash
        Test-Path "$target/linux" | Should -BeFalse
    }
    It 'rejects changed report bytes during copying' {
        $source = Join-Path $TestDrive 'bad'
        New-Item -ItemType Directory -Path $source -Force | Out-Null
        Set-Content "$source/report.log" 'changed'
        @{files=@{'report.log'=('a' * 64)};checks=@()} | ConvertTo-Json | Set-Content "$source/evidence.json"
        { Copy-LocalReleaseRegression "$source/evidence.json" (Join-Path $TestDrive 'bad-copy') } | Should -Throw '*hash mismatch*'
    }
}

Describe 'Explicit local publication' {
    BeforeEach {
        $proof = New-Proof
        $directory = Join-Path $TestDrive 'release'
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
        foreach ($name in @('Swypetris.apk','update.json','SHA256SUMS.txt','local-release-provenance.json')) { Set-Content (Join-Path $directory $name) 'fixture' }
        Mock Assert-LocalReleaseMain {}
        Mock Invoke-GitHubRest {
            param($Client,$Method,$Path,$Body)
            if ($Method -eq 'Get') {
                $response = [Net.Http.HttpResponseMessage]::new([Net.HttpStatusCode]::NotFound)
                throw [Microsoft.PowerShell.Commands.HttpResponseException]::new('Not found', $response)
            }
            if ($Path -eq 'releases') { return @{id=23;draft=$true;tag_name='v1.2.3'} }
            if ($Method -eq 'Patch') { return @{id=23;draft=$false;tag_name='v1.2.3'} }
        }
        Mock Invoke-RestMethod {
            param($Uri,$InFile)
            @{name=[IO.Path]::GetFileName($InFile);size=(Get-Item $InFile).Length;digest=('sha256:' + (Get-FileHash $InFile).Hash.ToLowerInvariant())}
        }
    }
    It 'WhatIf performs no tag release or asset writes' {
        Invoke-LocalReleasePublication @{} $TestDrive $proof $directory 'notes' -WhatIf
        Should -Invoke Invoke-GitHubRest -Times 0 -ParameterFilter { $Method -ne 'Get' }
        Should -Invoke Invoke-RestMethod -Times 0
    }
    It 'refuses existing tags without any writes' {
        Mock Invoke-GitHubRest { @{object=@{sha=('a' * 40)}} } -ParameterFilter { $Method -eq 'Get' }
        { Invoke-LocalReleasePublication @{} $TestDrive $proof $directory 'notes' } | Should -Throw '*Refusing to overwrite*'
        Should -Invoke Invoke-GitHubRest -Times 0 -ParameterFilter { $Method -ne 'Get' }
    }
    It 'does not treat service errors as absent releases' {
        Mock Invoke-GitHubRest {
            $response = [Net.Http.HttpResponseMessage]::new([Net.HttpStatusCode]::ServiceUnavailable)
            throw [Microsoft.PowerShell.Commands.HttpResponseException]::new('Unavailable', $response)
        } -ParameterFilter { $Method -eq 'Get' }
        { Invoke-LocalReleasePublication @{} $TestDrive $proof $directory 'notes' } | Should -Throw '*Unavailable*'
        Should -Invoke Invoke-GitHubRest -Times 0 -ParameterFilter { $Method -ne 'Get' }
    }
    It 'uploads all four assets before making the draft public' {
        $published = Invoke-LocalReleasePublication @{} $TestDrive $proof $directory 'notes'
        $published.draft | Should -BeFalse
        Should -Invoke Invoke-RestMethod -Times 4 -Exactly
        Should -Invoke Invoke-GitHubRest -Times 1 -Exactly -ParameterFilter { $Method -eq 'Patch' }
    }
    It 'leaves the draft unpublished if any upload digest mismatches' {
        Mock Invoke-RestMethod { @{name='Swypetris.apk';size=1;digest='sha256:bad'} }
        { Invoke-LocalReleasePublication @{} $TestDrive $proof $directory 'notes' } | Should -Throw '*Uploaded draft asset mismatch*'
        Should -Invoke Invoke-GitHubRest -Times 0 -ParameterFilter { $Method -eq 'Patch' }
    }
}

Describe 'Signed release clean-device preparation' {
    It 'accepts an emulator with no installed app without uninstalling anything' {
        Mock Invoke-ReleaseAdbFixture { $global:LASTEXITCODE=0 }
        { Reset-LocalReleaseApp 'Invoke-ReleaseAdbFixture' 'emulator-5560' } | Should -Not -Throw
        Should -Invoke Invoke-ReleaseAdbFixture -Times 1 -Exactly -ParameterFilter { $Arguments -contains 'list' -and $Arguments -contains 'emulator-5560' }
        Should -Invoke Invoke-ReleaseAdbFixture -Times 0 -ParameterFilter { $Arguments -contains 'uninstall' }
    }
    It 'removes only the existing Swypetris app on the explicit emulator' {
        Mock Invoke-ReleaseAdbFixture {
            $global:LASTEXITCODE=0
            if ($Arguments -contains 'list') { 'package:ru.itoltec.swypetris' } else { 'Success' }
        }
        { Reset-LocalReleaseApp 'Invoke-ReleaseAdbFixture' 'emulator-5560' } | Should -Not -Throw
        Should -Invoke Invoke-ReleaseAdbFixture -Times 1 -Exactly -ParameterFilter { ($Arguments -join ' ') -ceq '-s emulator-5560 uninstall ru.itoltec.swypetris' }
    }
    It 'does not uninstall a similarly named package' {
        Mock Invoke-ReleaseAdbFixture { $global:LASTEXITCODE=0; 'package:ru.itoltec.swypetris.unrelated' }
        Reset-LocalReleaseApp 'Invoke-ReleaseAdbFixture' 'emulator-5560'
        Should -Invoke Invoke-ReleaseAdbFixture -Times 0 -ParameterFilter { $Arguments -contains 'uninstall' }
    }
    It 'fails before installation when package manager inspection fails' {
        Mock Invoke-ReleaseAdbFixture { $global:LASTEXITCODE=1 }
        { Reset-LocalReleaseApp 'Invoke-ReleaseAdbFixture' 'emulator-5560' } | Should -Throw '*Cannot inspect*'
        Should -Invoke Invoke-ReleaseAdbFixture -Times 0 -ParameterFilter { $Arguments -contains 'uninstall' }
    }
    It 'fails if uninstall reports failure even with native exit zero' {
        Mock Invoke-ReleaseAdbFixture {
            $global:LASTEXITCODE=0
            if ($Arguments -contains 'list') { 'package:ru.itoltec.swypetris' } else { 'Failure' }
        }
        { Reset-LocalReleaseApp 'Invoke-ReleaseAdbFixture' 'emulator-5560' } | Should -Throw '*Cannot reset*'
    }
}
