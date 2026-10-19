Describe 'RuStore command transaction without live credentials or network' {
    BeforeEach {
        $fixture=Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        $null=New-Item -ItemType Directory $fixture
        Copy-Item (Join-Path $PSScriptRoot '../release/Invoke-RuStoreDelivery.ps1') $fixture
        Copy-Item (Join-Path $PSScriptRoot '../release/RuStore-Workflow.psm1') $fixture
        # Isolate transport/state ordering; exact candidate validation is retained in the production module.
        @'
function Read-SubmissionJson($Path) {Get-Content -LiteralPath $Path -Raw|ConvertFrom-Json -AsHashtable}
function Assert-SubmissionPlan($Plan,$Receipt) {}
Export-ModuleMember -Function Read-SubmissionJson,Assert-SubmissionPlan
'@|Set-Content "$fixture/RuStore-Candidate.psm1"
        @'
function New-RuStoreClient($CredentialPath) {@{token=(ConvertTo-SecureString 'fixture-not-a-credential' -AsPlainText -Force)}}
function Get-RuStoreDetail($Client,$Id) {@{versionId=123;publishType='MANUAL';versionStatus='DRAFT';packages=@()}}
function Get-RuStoreVersion($Client,$Id) {@{versionId=123;versionCode=36;versionName='1.11.3';versionStatus='DRAFT';partialValue=100}}
function Invoke-RuStoreRequest($Client,$Path) {@{}}
Export-ModuleMember -Function New-RuStoreClient,Get-RuStoreDetail,Get-RuStoreVersion
'@|Set-Content "$fixture/PrivateFixture.psm1"
        $plan=@{versionCode=36;versionName='1.11.3';apk=@{sha256=('a'*64);path="$fixture/app.apk"}}
        $plan|ConvertTo-Json|Set-Content "$fixture/plan.json"
        '{}'|Set-Content "$fixture/receipt.json"
        'fixture'|Set-Content "$fixture/app.apk"
        @{versionId=123;planSha256=(Get-FileHash "$fixture/plan.json").Hash.ToLowerInvariant();emptyDraftObserved=$true}|ConvertTo-Json|Set-Content "$fixture/clearance.json"
        $parameters=@{Action='Upload';PlanPath="$fixture/plan.json";ReceiptPath="$fixture/receipt.json";StateDirectory="$fixture/state";VersionId=123;ProviderPath="$fixture/PrivateFixture.psm1";CredentialPath='isolated-fixture';ClearancePath="$fixture/clearance.json"}
        Mock Invoke-RestMethod {
            if(!(Test-Path "$fixture/state/123-Upload-attempt.json")){throw 'Attempt was not persisted before transport.'}
            throw 'controlled ambiguous timeout'
        }
    }
    AfterEach {
        Get-Module -All|Where-Object {$_.Path -and $_.Path.StartsWith($fixture,[StringComparison]::OrdinalIgnoreCase)}|Remove-Module -Force
    }
    It 'preserves an ambiguous upload and refuses a second network call' {
        {& "$fixture/Invoke-RuStoreDelivery.ps1" @parameters}|Should -Throw '*write failed or timed out*'
        Test-Path "$fixture/state/123-Upload-attempt.json"|Should -BeTrue
        (Get-Content "$fixture/state/123-Upload-result.json" -Raw|ConvertFrom-Json).acknowledged|Should -BeFalse
        {& "$fixture/Invoke-RuStoreDelivery.ps1" @parameters}|Should -Throw '*Next: InspectConsole*'
        Should -Invoke Invoke-RestMethod -Times 1 -Exactly
    }
    It 'rejects a different server version before transport' {
        $plan.versionCode=37;$plan|ConvertTo-Json|Set-Content "$fixture/plan.json"
        $parameters.Remove('ClearancePath')
        {& "$fixture/Invoke-RuStoreDelivery.ps1" @parameters}|Should -Throw '*version conflicts*'
        Should -Invoke Invoke-RestMethod -Times 0
    }
    It 'retains the bounded server rejection reason without credential-bearing response text' {
        Mock Invoke-RestMethod {
            $errorRecord=[Management.Automation.ErrorRecord]::new([Net.Http.HttpRequestException]::new('rejected'),'fixture',[Management.Automation.ErrorCategory]::InvalidResult,$null)
            $errorRecord.ErrorDetails=[Management.Automation.ErrorDetails]::new('{"code":"error","message":"only one main APK"}')
            throw $errorRecord
        }
        {& "$fixture/Invoke-RuStoreDelivery.ps1" @parameters}|Should -Throw
        Test-Path "$fixture/state/last-transport-error.json"|Should -BeTrue
        (Get-Content "$fixture/state/last-transport-error.json" -Raw|ConvertFrom-Json).message|Should -Be 'only one main APK'
    }
    It 'does not retain a response that echoes the publication token' {
        Mock Invoke-RestMethod {
            $errorRecord=[Management.Automation.ErrorRecord]::new([Net.Http.HttpRequestException]::new('rejected'),'fixture',[Management.Automation.ErrorCategory]::InvalidResult,$null)
            $errorRecord.ErrorDetails=[Management.Automation.ErrorDetails]::new('{"message":"fixture-not-a-credential"}')
            throw $errorRecord
        }
        {& "$fixture/Invoke-RuStoreDelivery.ps1" @parameters}|Should -Throw
        Test-Path "$fixture/state/last-transport-error.json"|Should -BeTrue
        Get-Content "$fixture/state/last-transport-error.json" -Raw|Should -Not -Match 'fixture-not-a-credential'
    }
    It 'records a non-OK API response even when HTTP transport succeeded' {
        Mock Invoke-RestMethod {@{code='error';message='duplicate main APK'}}
        {& "$fixture/Invoke-RuStoreDelivery.ps1" @parameters}|Should -Throw '*did not acknowledge OK*'
        Test-Path "$fixture/state/last-transport-error.json"|Should -BeTrue
        $details=Get-Content "$fixture/state/last-transport-error.json" -Raw|ConvertFrom-Json
        $details.httpStatus|Should -Be 200
        $details.message|Should -Be 'duplicate main APK'
    }
    It 'previews fresh eligibility without creating a journal or sending a write request' {
        & "$fixture/Invoke-RuStoreDelivery.ps1" @parameters -WhatIf
        Test-Path "$fixture/state"|Should -BeFalse
        Should -Invoke Invoke-RestMethod -Times 0
    }
}
