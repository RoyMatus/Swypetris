BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '../Verification.psm1') -Force
    . (Join-Path $PSScriptRoot '../release/Release-Verification.ps1')
}
Describe 'Explicit signing Java environment' {
    It 'rejects a missing JDK before invoking signing tools' {
        { Resolve-ReleaseJavaHome (Join-Path $TestDrive 'missing') } | Should -Throw '*Windows JDK*'
    }
    It 'passes explicit Java to apksigner and restores the previous environment (<Failure>, <Previous>)' -ForEach @(
        @{ Failure=$false; Previous=$null }, @{ Failure=$true; Previous=$null },
        @{ Failure=$false; Previous='previous-jdk' }, @{ Failure=$true; Previous='previous-jdk' }
    ) -Skip:(!$IsWindows) {
        $root = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        $bin = Join-Path $root 'bin'
        New-Item -ItemType Directory $bin -Force | Out-Null
        Set-Content (Join-Path $bin 'java.exe') 'fixture'
        $digest = 'ab' * 32
        Set-Content (Join-Path $bin 'keytool.cmd') "@echo off`r`necho SHA256: $digest`r`nexit /b 0"
        Set-Content (Join-Path $root 'apksigner.bat') @"
@echo off
if not "%JAVA_HOME%"=="$root" exit /b 8
echo Signer #1 certificate SHA-256 digest: $digest
exit /b $(if ($Failure) { 7 } else { 0 })
"@
        # Only replace the executable extension in this isolated test copy.
        $source = Get-Content (Join-Path $PSScriptRoot '../release/Release-Verification.ps1') -Raw
        . ([scriptblock]::Create($source.Replace('keytool.exe', 'keytool.cmd')))
        $before = $env:JAVA_HOME
        try {
            if ($null -eq $Previous) { Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue }
            else { $env:JAVA_HOME = $Previous }
            if ($Failure) {
                { Assert-ReleaseSignature 'fixture.apk' app $root $root $root } | Should -Throw '*APK signature verification failed*'
            } else {
                Assert-ReleaseSignature 'fixture.apk' app $root $root $root | Should -Be $digest
            }
            $env:JAVA_HOME | Should -Be $Previous
        } finally { [Environment]::SetEnvironmentVariable('JAVA_HOME', $before, 'Process') }
    }
}
Describe 'Checked Git execution' {
    It 'returns successful output and rejects nonzero results' {
        Invoke-CheckedGit $TestDrive @('--version') | Should -Match '^git version '
        { Invoke-CheckedGit $TestDrive @('not-a-real-git-command') } | Should -Throw '*exit*'
    }
    It 'terminates a stalled owned process without retrying it' {
        $hook = Join-Path $TestDrive 'slow.ps1'
        Set-Content $hook 'Start-Sleep -Seconds 30'
        $command = '!pwsh -NoProfile -File "' + $hook.Replace('\','/') + '"'
        $watch = [Diagnostics.Stopwatch]::StartNew()
        { Invoke-CheckedGit $TestDrive @('-c', "alias.slow=$command", 'slow') -TimeoutSeconds 1 } | Should -Throw '*timed out*'
        $watch.Elapsed.TotalSeconds | Should -BeLessThan 10
    }
}
