BeforeAll {
    $runner = Join-Path $PSScriptRoot '../Invoke-LocalChecks.ps1'
    function Invoke-FakeLocalPython {
        $global:LASTEXITCODE = 0
        if ($args -contains 'plan') {
            $toolSelection = if ($global:localGateTestTools) { 'true' } else { 'false' }
            @{mode='none';jvm='';android='';github_scripts=$toolSelection;release_scripts='false';ci_scripts='false';windows_release_tools='false'} | ConvertTo-Json -Compress
        } elseif ($global:localGateTestReject) { $global:LASTEXITCODE = 1 }
    }
    function Invoke-FakeLocalToolProcess { $global:LASTEXITCODE = 0; 'Windows tool fixture passed' }
}
Describe 'Local orchestrator completion transaction' {
    BeforeEach {
        $global:localGateTestReject = $false
        $global:localGateTestFail = $false
        $global:localGateTestTools = $false
        Mock git {
            $global:LASTEXITCODE = 0
            if ($args -contains 'rev-parse') { 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' }
            if ($args -contains 'diff' -and $global:localGateTestFail) { $global:LASTEXITCODE = 1 }
        }
    }
    It 'publishes evidence only after successful final validation' {
        $destination = Join-Path $TestDrive 'success'
        & $runner -BaseSha ('b'*40) -Python Invoke-FakeLocalPython -OutputDirectory $destination
        $doc = Get-Content (Join-Path $destination 'evidence.json') -Raw | ConvertFrom-Json
        $doc.result | Should -Be 'passed'
        $doc.checks.name | Should -Be 'whitespace'
        Test-Path (Join-Path $destination 'candidate.json') | Should -BeFalse
    }
    It 'fails without successful evidence when a command fails' {
        $global:localGateTestFail = $true
        $destination = Join-Path $TestDrive 'command-failure'
        { & $runner -BaseSha ('b'*40) -Python Invoke-FakeLocalPython -OutputDirectory $destination } | Should -Throw '*whitespace failed*'
        Test-Path (Join-Path $destination 'evidence.json') | Should -BeFalse
        (Get-Content (Join-Path $destination 'candidate.json') -Raw | ConvertFrom-Json).result | Should -Be 'failed'
    }
    It 'completes Windows tool checks without a Linux environment or report' {
        $global:localGateTestTools = $true
        Mock Get-Process { @{Path='Invoke-FakeLocalToolProcess'} }
        $destination = Join-Path $TestDrive 'windows-tools'
        & $runner -BaseSha ('b'*40) -Python Invoke-FakeLocalPython -OutputDirectory $destination
        $doc = Get-Content (Join-Path $destination 'evidence.json') -Raw | ConvertFrom-Json
        $doc.platform | Should -Be 'Windows'
        $doc.result | Should -Be 'passed'
        @($doc.checks.name | Sort-Object) | Should -Be @('pester','python','whitespace')
        Test-Path (Join-Path $destination 'linux') | Should -BeFalse
    }
    It 'does not publish passing evidence after a final validation failure' {
        $global:localGateTestReject = $true
        $destination = Join-Path $TestDrive 'validation-failure'
        { & $runner -BaseSha ('b'*40) -Python Invoke-FakeLocalPython -OutputDirectory $destination } | Should -Throw '*evidence validation*'
        Test-Path (Join-Path $destination 'evidence.json') | Should -BeFalse
        (Get-Content (Join-Path $destination 'candidate.json') -Raw | ConvertFrom-Json).result | Should -Be 'failed'
    }
    It 'rejects nonempty output instead of reusing stale artifacts' {
        $destination = Join-Path $TestDrive 'stale'
        $null = New-Item $destination -ItemType Directory
        Set-Content (Join-Path $destination 'old.xml') stale
        { & $runner -BaseSha ('b'*40) -Python Invoke-FakeLocalPython -OutputDirectory $destination } | Should -Throw '*must be empty*'
    }
}
