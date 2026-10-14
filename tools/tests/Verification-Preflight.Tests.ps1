BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '../Verification-Preflight.psm1') -Force
}
Describe 'Scope-aware verification prerequisites' {
    It 'keeps documentation scope free of Android and Sonar requirements' {
        $result = Resolve-VerificationEnvironment -Project $TestDrive -RequiresAndroid $false
        $result.androidRequired | Should -BeFalse
    }
    It 'rejects conflicting SDK environment locations instead of selecting another adb' {
        $beforeHome=$env:ANDROID_HOME; $beforeRoot=$env:ANDROID_SDK_ROOT
        try {
            $env:ANDROID_HOME=Join-Path $TestDrive 'one'; $env:ANDROID_SDK_ROOT=Join-Path $TestDrive 'two'
            { Resolve-VerificationSdk -Project $TestDrive } | Should -Throw '*conflict*'
        } finally { $env:ANDROID_HOME=$beforeHome; $env:ANDROID_SDK_ROOT=$beforeRoot }
    }
    It 'rejects a local.properties SDK that differs from an explicit choice' {
        $one=Join-Path $TestDrive 'sdk-one'; $two=Join-Path $TestDrive 'sdk-two'
        New-Item -ItemType Directory -Path $one,$two -Force | Out-Null
        Set-Content (Join-Path $TestDrive 'local.properties') ('sdk.dir='+$one.Replace('\','/'))
        { Resolve-VerificationSdk -Project $TestDrive -SdkDirectory $two } | Should -Throw '*conflict*'
    }
    It 'requires PR context before any environment or expensive check for PR app runs' {
        { Assert-VerificationAnalysisIdentity -Project $TestDrive -Mode PR -Head ('a'*40) -Base ('b'*40) } | Should -Throw '*PullRequestNumber*'
    }
    It 'requires a named branch for Full-on-PR analysis' {
        Mock Invoke-CheckedGit -ModuleName Verification-Preflight {
            if ($Arguments -contains 'ls-remote') { ('b'*40)+"`trefs/heads/main" } else { '' }
        }
        { Assert-VerificationAnalysisIdentity -Project $TestDrive -Mode Full -PullRequestNumber 309 -Head ('a'*40) -Base ('b'*40) } | Should -Throw '*named branch*'
    }
    It 'rejects stale main before exact-main Full work' {
        Mock Invoke-CheckedGit -ModuleName Verification-Preflight { ('c'*40)+"`trefs/heads/main" }
        { Assert-VerificationAnalysisIdentity -Project $TestDrive -Mode Full -Head ('a'*40) -Base ('b'*40) } | Should -Throw '*exact remote main*'
    }
    It 'accepts exact remote main Full without PR requirements' {
        Mock Invoke-CheckedGit -ModuleName Verification-Preflight { ('a'*40)+"`trefs/heads/main" }
        $identity=Assert-VerificationAnalysisIdentity -Project $TestDrive -Mode Full -Head ('a'*40) -Base ('b'*40)
        $identity.branch | Should -Be 'main'
        $identity.pullRequestNumber | Should -Be 0
    }
    It 'accepts Full-on-PR only for current base and matching remote PR head' {
        Mock Invoke-CheckedGit -ModuleName Verification-Preflight {
            if ($Arguments -contains 'refs/heads/main') { ('b'*40)+"`trefs/heads/main" }
            elseif ($Arguments -contains 'refs/pull/309/head') { ('a'*40)+"`trefs/pull/309/head" }
            else { 'codex/issue-306' }
        }
        (Assert-VerificationAnalysisIdentity -Project $TestDrive -Mode Full -PullRequestNumber 309 -Head ('a'*40) -Base ('b'*40)).branch | Should -Be 'codex/issue-306'
    }
    It 'rejects an unrelated PR head before starting checks' {
        Mock Invoke-CheckedGit -ModuleName Verification-Preflight {
            if ($Arguments -contains 'refs/heads/main') { ('b'*40)+"`trefs/heads/main" }
            elseif ($Arguments -contains 'ls-remote') { ('c'*40)+"`trefs/pull/309/head" }
            else { 'codex/issue-306' }
        }
        { Assert-VerificationAnalysisIdentity -Project $TestDrive -Mode PR -PullRequestNumber 309 -Head ('a'*40) -Base ('b'*40) } | Should -Throw '*identity does not match*'
    }
    It 'rejects missing JDK before probing SDK or Sonar' {
        { Resolve-VerificationEnvironment -Project $TestDrive -RequiresAndroid $true -JavaHome (Join-Path $TestDrive 'absent-jdk') } | Should -Throw '*JDK 21*'
    }
}
