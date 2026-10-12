BeforeAll {
    . (Join-Path $PSScriptRoot '../release/Release-Verification.ps1')
}

Describe 'Build release staging integration' {
    It 'handles <Failure> without mixing release files or changing the environment' -ForEach @(
        @{ Failure = 'aab'; Expected = '*AAB build failed*' }
        @{ Failure = 'signature'; Expected = '*fixture signature failure*' }
        @{ Failure = 'promotion'; Expected = '*fixture promotion failure*' }
        @{ Failure = 'success'; Expected = $null }
    ) -Skip:(!$IsWindows) {
        $root = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        $release = Join-Path $root 'tools/release'
        New-Item -ItemType Directory -Path $release -Force | Out-Null
        Copy-Item (Join-Path $PSScriptRoot '../release/*.ps1') $release
        Copy-Item (Join-Path $PSScriptRoot '../Verification.psm1') (Join-Path $root 'tools')
        # Substitute Android executables only in this disposable fixture.
        $build = Join-Path $release 'Build-Release.ps1'
        (Get-Content $build -Raw).Replace('aapt.exe', 'aapt.cmd') | Set-Content $build
        Add-Content (Join-Path $release 'Release-Verification.ps1') @'
function Assert-ReleaseSignature {
    param($Artifact, $Role, $Project, $JavaHome, $BuildTools)
    if ($Role -eq 'upload' -and $env:RELEASE_FIXTURE_FAILURE -eq 'signature') { throw 'fixture signature failure' }
    return ('ab' * 32)
}
'@
        if ($Failure -eq 'promotion') {
            Add-Content (Join-Path $release 'Release-Transaction.ps1') @'
function Move-ReleaseDirectory {
    param($Source, $Destination)
    if ($Source -like '*.staging') { throw 'fixture promotion failure' }
    [IO.Directory]::Move($Source, $Destination)
}
'@
        }
        Set-Content (Join-Path $root 'gradle.properties') 'swypetrisVersion=1.0.0'
        Set-Content (Join-Path $root 'gradlew.bat') @'
@echo off
if "%1"==":app:bundleRelease" if "%RELEASE_FIXTURE_FAILURE%"=="aab" exit /b 7
echo ^> Task :app:testDebugUnitTest UP-TO-DATE
exit /b 0
'@
        foreach ($entry in @{
            'app/build/outputs/apk/release/app-release.apk' = 'new apk'
            'app/build/outputs/bundle/release/app-release.aab' = 'new aab'
            'app/src/main/res/drawable-nodpi/ic_launcher_artwork.png' = 'icon'
            'app/build/test-results/testDebugUnitTest/TEST-fixture.xml' = '<testsuite tests="1" failures="0" errors="0"><testcase classname="Fixture" name="passes" /></testsuite>'
            'dist/1.0.0/Swypetris.apk' = 'previous apk'
            'dist/1.0.0/Swypetris-1.0.0.aab' = 'previous aab'
            'dist/1.0.0/SHA256SUMS.txt' = 'previous checksums'
        }.GetEnumerator()) {
            $file = Join-Path $root $entry.Key
            New-Item -ItemType Directory -Force (Split-Path $file) | Out-Null
            Set-Content -LiteralPath $file $entry.Value
        }
        $sdk = Join-Path $root 'sdk/build-tools/1.0.0'
        New-Item -ItemType Directory -Path $sdk -Force | Out-Null
        Set-Content (Join-Path $sdk 'aapt.cmd') "@echo off`r`necho package: name='ru.itoltec.swypetris' versionCode='10000' versionName='1.0.0'`r`nexit /b 0"
        $signing = Join-Path $root 'signing'
        New-Item -ItemType Directory -Path $signing | Out-Null
        foreach ($role in @('app','upload')) {
            [pscredential]::new($role, (ConvertTo-SecureString 'fixture-secret' -AsPlainText -Force)) | Export-Clixml (Join-Path $signing "$role.clixml")
        }
        $names = @('JAVA_HOME','ANDROID_HOME','RELEASE_FIXTURE_FAILURE','SWYPETRIS_STORE_FILE','SWYPETRIS_STORE_PASSWORD','SWYPETRIS_KEY_ALIAS')
        New-Item -ItemType Directory -Path (Join-Path $root 'bin') | Out-Null
        Set-Content (Join-Path $root 'bin/java.exe') 'toolchain fixture'
        $before = @{}
        foreach ($name in $names) { $before[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
        try {
            $env:JAVA_HOME = $root
            $env:ANDROID_HOME = Join-Path $root 'sdk'
            $env:RELEASE_FIXTURE_FAILURE = $Failure
            $env:SWYPETRIS_STORE_FILE = 'previous store'
            $env:SWYPETRIS_STORE_PASSWORD = 'previous password'
            $env:SWYPETRIS_KEY_ALIAS = 'previous alias'
            if ($Expected) { { & $build -SigningDirectory $signing } | Should -Throw $Expected }
            else { & $build -SigningDirectory $signing }
            $env:SWYPETRIS_STORE_FILE | Should -Be 'previous store'
            $env:SWYPETRIS_STORE_PASSWORD | Should -Be 'previous password'
            $env:SWYPETRIS_KEY_ALIAS | Should -Be 'previous alias'
            if ($Expected) {
                Get-Content (Join-Path $root 'dist/1.0.0/Swypetris.apk') | Should -Be 'previous apk'
                Get-Content (Join-Path $root 'dist/1.0.0/Swypetris-1.0.0.aab') | Should -Be 'previous aab'
                Get-Content (Join-Path $root 'dist/1.0.0/SHA256SUMS.txt') | Should -Be 'previous checksums'
            } else {
                Get-Content (Join-Path $root 'dist/1.0.0/Swypetris.apk') | Should -Be 'new apk'
                Get-Content (Join-Path $root 'dist/1.0.0/Swypetris-1.0.0.aab') | Should -Be 'new aab'
                $metadata = Get-Content (Join-Path $root 'dist/1.0.0/update.json') -Raw | ConvertFrom-Json
                $metadata.sha256 | Should -Be (Get-FileHash (Join-Path $root 'dist/1.0.0/Swypetris.apk')).Hash.ToLowerInvariant()
                @(Get-Content (Join-Path $root 'dist/1.0.0/SHA256SUMS.txt')).Count | Should -Be 4
            }
            @(Get-ChildItem (Join-Path $root 'dist') -Directory -Force).Count | Should -Be 1
            $json = Get-Content (Get-ChildItem (Join-Path $root 'app/build/verification/*.json')).FullName -Raw
            ($json | ConvertFrom-Json).result | Should -Be $(if ($Expected) { 'failed' } else { 'passed' })
            $json | Should -Not -Match 'fixture-secret|previous password'
        } finally {
            foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $before[$name], 'Process') }
        }
    }
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
        $beforeJava = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
        $beforeSdk = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'Process')
        $fixtureSdk = Join-Path $TestDrive 'sdk'
        New-Item -ItemType Directory -Force (Join-Path $TestDrive 'bin') | Out-Null
        Set-Content (Join-Path $TestDrive 'bin/java.exe') 'toolchain fixture'
        New-Item -ItemType Directory -Force (Join-Path $fixtureSdk 'build-tools/1.0.0') | Out-Null
        Set-Content (Join-Path $fixtureSdk 'build-tools/1.0.0/aapt.exe') 'not executed: Gradle fails first'
        try {
            $env:JAVA_HOME = $TestDrive
            $env:ANDROID_HOME = $fixtureSdk
            { & (Join-Path $release 'Build-Release.ps1') -SigningDirectory $signing } | Should -Throw '*APK build*'
            foreach ($name in $names) {
                ([Environment]::GetEnvironmentVariable($name, 'Process') -ceq $before[$name]) | Should -BeTrue
            }
            $json = Get-Content (Get-ChildItem (Join-Path $root 'app/build/verification/*.json')).FullName -Raw
            ($json | ConvertFrom-Json).result | Should -Be 'failed'
            $json | Should -Not -Match 'fixture-password'
        } finally {
            foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $before[$name], 'Process') }
            [Environment]::SetEnvironmentVariable('JAVA_HOME', $beforeJava, 'Process')
            [Environment]::SetEnvironmentVariable('ANDROID_HOME', $beforeSdk, 'Process')
        }
    }
}
