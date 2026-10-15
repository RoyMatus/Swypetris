BeforeAll {
    $runner = Join-Path $PSScriptRoot '../Invoke-LocalChecks.ps1'
    $parseTokens = $null
    $parseErrors = $null
    $runnerAst = [System.Management.Automation.Language.Parser]::ParseFile($runner, [ref]$parseTokens, [ref]$parseErrors)
    if ($parseErrors.Count -gt 0) { throw "Invoke-LocalChecks.ps1 has PowerShell parse errors: $($parseErrors.Message -join '; ')" }
    $invokeCheckDefinition = @($runnerAst.FindAll({ param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-Check' }, $true))
    if ($invokeCheckDefinition.Count -ne 1) { throw "Expected one production Invoke-Check definition, found $($invokeCheckDefinition.Count)." }
    . ([scriptblock]::Create($invokeCheckDefinition[0].Extent.Text))
    function Invoke-FakeLocalPython {
        $global:LASTEXITCODE = 0
        if ($args -contains 'plan') {
            $toolSelection = if ($global:localGateTestTools) { 'true' } else { 'false' }
            @{mode=$(if($global:localGateTestApp){'full'}else{'none'});jvm='';android='';github_scripts=$toolSelection;release_scripts='false';ci_scripts='false';windows_release_tools='false'} | ConvertTo-Json -Compress
        } elseif ($args -contains 'validate' -and $global:localGateTestReject) { $global:LASTEXITCODE = 1 }
    }
    function Invoke-FakeLocalToolProcess { $global:LASTEXITCODE = 0; 'Windows tool fixture passed' }
    function Invoke-FakeCheckpointCommand {
        $global:LASTEXITCODE = 0
        $operation = $args[1]
        if ($operation -eq 'reuse') {
            $global:localGateReuseCommandCount++
            $dependencyPath = $args[([array]::IndexOf($args, '--dependencies') + 1)]
            $global:localGateCheckpointDependencies.Add((Get-Content -LiteralPath $dependencyPath -Raw | ConvertFrom-Json -AsHashtable))
            @{name=$global:localGateExpectedReusedStage;result='passed';durationSeconds=17;log="$($global:localGateExpectedReusedStage).log";origin='prior'} | ConvertTo-Json -Compress
        } elseif ($operation -eq 'record') {
            $stage = $args[([array]::IndexOf($args, '--stage') + 1)]
            $store = $args[([array]::IndexOf($args, '--store') + 1)]
            $checkPath = $args[([array]::IndexOf($args, '--check') + 1)]
            $global:localGateRecordedCheck = Get-Content -LiteralPath $checkPath -Raw | ConvertFrom-Json -AsHashtable
            $global:localGateRecordedStage = $stage
            $global:localGateRecordedStore = $store
            @{checkpointSha256=$global:localGateRecordedHash} | ConvertTo-Json -Compress
        } else {
            throw "Unexpected fake checkpoint operation: $operation"
        }
    }
    function Invoke-FakeQualityCommand {
        $global:localGateQualityCommandCount++
        $build = Join-Path $global:localGateProject 'app/build'
        foreach ($relative in @('test-results/testDebugUnitTest','reports/jacoco','reports/detekt','outputs/apk')) {
            $directory = Join-Path $build $relative
            $null = New-Item -ItemType Directory -Path $directory -Force
            Set-Content -LiteralPath (Join-Path $directory 'fixture.txt') 'quality report'
        }
        $null = New-Item -ItemType Directory -Path (Join-Path $build 'reports') -Force
        Set-Content -LiteralPath (Join-Path $build 'reports/lint-results-debug.xml') '<lint/>'
        $global:LASTEXITCODE = 0
    }
    function Initialize-QualityCheckpointFixture {
        param([switch]$Mismatch)
        $global:localGateProject = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        $global:localGateOutput = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        $global:localGateResume = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        $null = New-Item -ItemType Directory -Path $global:localGateProject,$global:localGateOutput,$global:localGateResume -Force
        $global:localGateReportPaths = @('app/build/outputs/apk/debug/app-debug.apk','app/build/reports/detekt/fixture.sarif')
        foreach ($relative in $global:localGateReportPaths) {
            $path = Join-Path $global:localGateProject $relative
            $null = New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force
            Set-Content -LiteralPath $path "prior $relative"
            $diagnosticParent = Join-Path $global:localGateOutput (Split-Path -Parent $relative)
            $null = New-Item -ItemType Directory -Path $diagnosticParent -Force
        }
        if ($Mismatch) { Set-Content -LiteralPath (Join-Path $global:localGateProject $global:localGateReportPaths[0]) 'changed live APK' }
        $boundReports = @{}
        foreach ($relative in $global:localGateReportPaths) {
            $boundReports[$relative] = (Get-FileHash -LiteralPath (Join-Path $global:localGateProject $relative) -Algorithm SHA256).Hash.ToLowerInvariant()
        }
        if ($Mismatch) { $boundReports[$global:localGateReportPaths[0]] = 'a' * 64 }
        $global:localGatePriorCheckpoint = Join-Path $global:localGateResume 'checkpoints/quality/checkpoint.json'
        $null = New-Item -ItemType Directory -Path (Split-Path -Parent $global:localGatePriorCheckpoint) -Force
        @{files=$boundReports} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $global:localGatePriorCheckpoint
        $global:localGateExpectedQualityDependency = 'd' * 64
        $global:localGateRecordedHash = 'e' * 64
        $global:localGateCheckpointDependencies = [System.Collections.Generic.List[hashtable]]::new()
        $global:localGateReuseCommandCount = 0
        $global:localGateQualityCommandCount = 0
        $global:localGateRecordedCheck = $null
        $global:localGateRecordedStage = $null
        $global:localGateRecordedStore = $null
        $global:localGateExpectedReusedStage = 'quality'
        $global:localGateCheckpointHashes = @{}
        $global:localGateChecks = [System.Collections.Generic.List[object]]::new()
        $global:localGateCheckpointEnabled = $true
        $global:localGateResumeDirectory = $global:localGateResume
        $global:localGateResumeDocument = @{files=@{'checkpoints/quality/checkpoint.json'=$global:localGateExpectedQualityDependency;'checkpoints/quality-reports/checkpoint.json'=('f' * 64)}}
        $global:localGateOutputDirectory = $global:localGateOutput
        $global:localGatePython = 'Invoke-FakeCheckpointCommand'
        $global:localGateCheckpointHelper = 'fake-checkpoint-helper'
        $global:localGateFingerprintPath = Join-Path $global:localGateOutput 'fingerprint.json'
        Set-Content -LiteralPath $global:localGateFingerprintPath '{}'
        $global:localGateStarted = [datetime]::UtcNow
        $global:project = $global:localGateProject
        $global:OutputDirectory = $global:localGateOutput
        $global:resumeDirectory = $global:localGateResume
        $global:resumeDocument = $global:localGateResumeDocument
        $global:checkpointHashes = $global:localGateCheckpointHashes
        $global:checks = $global:localGateChecks
        $global:checkpointEnabled = $true
        $global:Python = $global:localGatePython
        $global:checkpointHelper = $global:localGateCheckpointHelper
        $global:fingerprintPath = $global:localGateFingerprintPath
        $global:started = $global:localGateStarted
        Push-Location $global:localGateProject
    }
}
Describe 'Local orchestrator completion transaction' {
    BeforeEach {
        $global:localGateTestReject = $false
        $global:localGateTestFail = $false
        $global:localGateTestTools = $false
        $global:localGateTestApp = $false
        Mock git {
            $global:LASTEXITCODE = 0
            if ($args -contains 'rev-parse') { 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' }
            if ($args -contains 'diff' -and $global:localGateTestFail) { $global:LASTEXITCODE = 1 }
        }
    }
    It 'rejects missing PR identity before tool suites or Gradle' {
        $global:localGateTestApp = $true
        $global:localGateTestTools = $true
        Mock Get-Process { throw 'Tool suite ran before preflight' }
        { & $runner -BaseSha ('b'*40) -Python Invoke-FakeLocalPython -OutputDirectory (Join-Path $TestDrive 'early-pr') } | Should -Throw '*PullRequestNumber*'
        Should -Invoke Get-Process -Times 0 -Exactly
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

Describe 'Quality checkpoint stage identity' {
    BeforeEach {
        $global:localGateSavedGlobals = @{}
        foreach ($variableName in @('project','OutputDirectory','resumeDirectory','resumeDocument','checkpointHashes',
            'checks','checkpointEnabled','Python','checkpointHelper','fingerprintPath','started')) {
            $previous = Get-Variable -Name $variableName -Scope Global -ErrorAction SilentlyContinue
            $global:localGateSavedGlobals[$variableName] = @{exists=($null -ne $previous);value=$previous.Value}
        }
    }
    AfterEach {
        if ((Get-Location).Path -eq $global:localGateProject) { Pop-Location }
        foreach ($saved in $global:localGateSavedGlobals.GetEnumerator()) {
            if ($saved.Value.exists) { Set-Variable -Name $saved.Key -Value $saved.Value.value -Scope Global }
            else { Remove-Variable -Name $saved.Key -Scope Global -ErrorAction SilentlyContinue }
        }
        Remove-Variable -Name localGateSavedGlobals -Scope Global
    }

    It 'reuses quality and passes its exact checkpoint hash to quality-reports' {
        Initialize-QualityCheckpointFixture
        $global:localGateExpectedReusedStage = 'quality'
        Invoke-Check -Name quality -Command Invoke-FakeQualityCommand -Arguments @()
        $global:localGateExpectedReusedStage = 'quality-reports'
        Invoke-Check -Name quality-reports -Command Invoke-FakeQualityCommand -Arguments @()

        $global:localGateCheckpointHashes['quality'] | Should -Be $global:localGateExpectedQualityDependency -ErrorAction Continue
        foreach ($reportPath in $global:localGateReportPaths) { $global:localGateCheckpointHashes.ContainsKey($reportPath) | Should -BeFalse -ErrorAction Continue }
        $global:localGateChecks[0].name | Should -Be 'quality' -ErrorAction Continue
        $global:localGateChecks[0].durationSeconds | Should -Be 17 -ErrorAction Continue
        $global:localGateChecks[0].log | Should -Be 'quality.log' -ErrorAction Continue
        $global:localGateCheckpointDependencies[1].quality | Should -Be $global:localGateExpectedQualityDependency -ErrorAction Continue
        $global:localGateCheckpointHashes['quality-reports'] | Should -Be ('f' * 64) -ErrorAction Continue
        $global:localGateChecks[1].name | Should -Be 'quality-reports' -ErrorAction Continue
        $global:localGateReuseCommandCount | Should -Be 2 -ErrorAction Continue
        $global:localGateQualityCommandCount | Should -Be 0 -ErrorAction Continue
    }

    It 'reruns quality under its own identity when live report bytes mismatch' {
        Initialize-QualityCheckpointFixture -Mismatch
        Invoke-Check -Name quality -Command Invoke-FakeQualityCommand -Arguments @()

        $global:localGateReuseCommandCount | Should -Be 0
        $global:localGateQualityCommandCount | Should -Be 1
        $global:localGateRecordedStage | Should -Be 'quality'
        $global:localGateRecordedStore | Should -Be (Join-Path $global:localGateOutput 'checkpoints/quality')
        $global:localGateRecordedCheck.name | Should -Be 'quality'
        $global:localGateChecks[0].name | Should -Be 'quality'
        $global:localGateChecks[0].log | Should -Be 'quality.log'
        $global:localGateCheckpointHashes['quality'] | Should -Be $global:localGateRecordedHash
        foreach ($reportPath in $global:localGateReportPaths) { $global:localGateCheckpointHashes.ContainsKey($reportPath) | Should -BeFalse }
        Test-Path (Join-Path $global:localGateOutput 'app/build/reports/lint-results-debug.xml') | Should -BeTrue
        Test-Path (Join-Path $global:localGateOutput 'app/build/outputs/apk') | Should -BeTrue
    }
}
