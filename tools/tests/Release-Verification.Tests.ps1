BeforeAll {
    . (Join-Path $PSScriptRoot '../release/Release-Verification.ps1')
}
Describe 'Local release verification' {
    It 'reuses daemon and cache by default and preserves the explicit fallback' {
        $default = Get-ReleaseGradleOptions
        $default | Should -Contain '--daemon'
        $default | Should -Contain '--build-cache'
        $fallback = Get-ReleaseGradleOptions -NoDaemon
        $fallback | Should -Contain '--no-daemon'
        $fallback | Should -Not -Contain '--daemon'
    }
    It 'normalizes keytool certificate fingerprints' {
        Get-ReleaseCertificateDigest -Output (' SHA256: ' + (('AB' * 32) -replace '..(?!$)', '$0:')) | Should -Be ('ab' * 32)
    }
    It 'reads an APK signer digest' {
        Get-ReleaseCertificateDigest -Output ('Signer #1 certificate SHA-256 digest: ' + ('ab' * 32)) -Apk | Should -Be ('ab' * 32)
        Get-ReleaseCertificateDigest -Output ('V2 Signer: certificate SHA-256 digest: ' + ('ab' * 32)) -Apk | Should -Be ('ab' * 32)
    }
    It 'rejects missing or malformed fingerprints' {
        { Get-ReleaseCertificateDigest -Output 'no certificate' } | Should -Throw
        { Get-ReleaseCertificateDigest -Output ' SHA256: AA:BB' } | Should -Throw
    }
    It 'rejects multiple signers' {
        { Get-ReleaseCertificateDigest -Output @(' SHA256: ' + ('ab' * 32), ' SHA256: ' + ('cd' * 32)) } | Should -Throw
    }
    It 'rejects the wrong signing role' {
        { Assert-ReleaseCertificate -Actual ('ab' * 32) -Expected ('cd' * 32) } | Should -Throw
        { Assert-ReleaseCertificate -Actual '' -Expected '' } | Should -Throw
        { Assert-ReleaseCertificate -Actual ('ab' * 32) -Expected ('ab' * 32) } | Should -Not -Throw
    }
    It 'restores signing environment and writes a failed summary after Gradle failure' -Skip:(!$IsWindows) {
        $root = Join-Path $TestDrive 'project'
        $release = Join-Path $root 'tools/release'
        New-Item -ItemType Directory -Path $release -Force | Out-Null
        Copy-Item (Join-Path $PSScriptRoot '../release/*.ps1') $release
        Copy-Item (Join-Path $PSScriptRoot '../Verification.psm1') (Join-Path $root 'tools')
        Set-Content (Join-Path $root 'gradle.properties') 'swypetrisVersion=1.0.0'
        Set-Content (Join-Path $root 'gradlew.bat') "@echo off`r`nexit /b 7"
        $signing = Join-Path $TestDrive 'signing'
        New-Item -ItemType Directory -Path $signing | Out-Null
        [pscredential]::new('fixture', (ConvertTo-SecureString 'fixture-password' -AsPlainText -Force)) | Export-Clixml (Join-Path $signing 'app.clixml')
        $names = @('SWYPETRIS_STORE_FILE', 'SWYPETRIS_STORE_PASSWORD', 'SWYPETRIS_KEY_ALIAS')
        $before = @{}
        foreach ($name in $names) { $before[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
        try {
            { & (Join-Path $release 'Build-Release.ps1') -SigningDirectory $signing } | Should -Throw '*APK build*'
            foreach ($name in $names) {
                ([Environment]::GetEnvironmentVariable($name, 'Process') -ceq $before[$name]) | Should -BeTrue
            }
            $json = Get-Content (Get-ChildItem (Join-Path $root 'app/build/verification/*.json')).FullName -Raw
            ($json | ConvertFrom-Json).result | Should -Be 'failed'
            $json | Should -Not -Match 'fixture-password'
        } finally {
            foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $before[$name], 'Process') }
        }
    }
}
