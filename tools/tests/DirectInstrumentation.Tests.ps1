BeforeAll {
    $script:preflight = Join-Path $PSScriptRoot '../ci/Prepare-DirectInstrumentation.ps1'
}
Describe 'Direct instrumentation package preflight' {
    BeforeEach {
        $app = Join-Path $TestDrive 'app.apk'
        $test = Join-Path $TestDrive 'test.apk'
        'app artifact' | Set-Content $app
        'test artifact' | Set-Content $test
        $env:SWYPETRIS_FIXTURE_APP = $app
        $env:SWYPETRIS_FIXTURE_TEST = $test
        $env:SWYPETRIS_FIXTURE_INSTALL_LOG = Join-Path $TestDrive 'install.log'
        Remove-Item $env:SWYPETRIS_FIXTURE_INSTALL_LOG -ErrorAction SilentlyContinue
        $env:SWYPETRIS_FIXTURE_INSTALLED = 'missing'
        $adb = Join-Path $TestDrive 'adb.ps1'
        $aapt = Join-Path $TestDrive 'aapt.ps1'
        $signer = Join-Path $TestDrive 'signer.ps1'
        @'
if ($args[0] -eq 'devices') { 'List of devices attached'; 'emulator-5560 device model:Fixture'; exit 0 }
if ($args[2] -eq 'shell' -and $args[3] -eq 'pm') {
    if ($env:SWYPETRIS_FIXTURE_INSTALLED -eq 'installed') { "package:/data/app/$($args[5])/base.apk" }
    exit 0
}
if ($args[2] -eq 'pull') {
    $source = if ($args[3] -match '\.test/') { $env:SWYPETRIS_FIXTURE_TEST } else { $env:SWYPETRIS_FIXTURE_APP }
    Copy-Item $source $args[4]
    exit 0
}
if ($args[2] -eq 'install') { Add-Content $env:SWYPETRIS_FIXTURE_INSTALL_LOG $args[4]; 'Success'; exit 0 }
exit 1
'@ | Set-Content $adb
        @'
if ($args[-1] -like '*test.apk') { "package: name='ru.itoltec.swypetris.test'" }
else { "package: name='ru.itoltec.swypetris'" }
'@ | Set-Content $aapt
        "'V2 Signer: certificate SHA-256 digest: ' + ('a' * 64)" | Set-Content $signer
    }
    AfterEach {
        'SWYPETRIS_FIXTURE_APP','SWYPETRIS_FIXTURE_TEST','SWYPETRIS_FIXTURE_INSTALL_LOG','SWYPETRIS_FIXTURE_INSTALLED' |
            ForEach-Object { Remove-Item "Env:$_" -ErrorAction SilentlyContinue }
    }
    It 'installs both compatible already-built APKs when packages are missing' {
        & $preflight -Serial emulator-5560 -AppApk $app -TestApk $test -AdbCommand $adb -AaptCommand $aapt -ApksignerCommand $signer | Out-Null
        @(Get-Content $env:SWYPETRIS_FIXTURE_INSTALL_LOG).Count | Should -Be 2
    }
    It 'reuses installed APKs with matching bytes' {
        $env:SWYPETRIS_FIXTURE_INSTALLED = 'installed'
        & $preflight -Serial emulator-5560 -AppApk $app -TestApk $test -AdbCommand $adb -AaptCommand $aapt -ApksignerCommand $signer | Out-Null
        Test-Path $env:SWYPETRIS_FIXTURE_INSTALL_LOG | Should -BeFalse
    }
    It 'rejects an absent APK without invoking instrumentation' {
        Remove-Item $app
        { & $preflight -Serial emulator-5560 -AppApk $app -TestApk $test -AdbCommand $adb -AaptCommand $aapt -ApksignerCommand $signer } | Should -Throw '*APK is missing*'
    }
    It 'rejects an incompatible package' {
        '"package: name=''wrong.package''"' | Set-Content $aapt
        { & $preflight -Serial emulator-5560 -AppApk $app -TestApk $test -AdbCommand $adb -AaptCommand $aapt -ApksignerCommand $signer } | Should -Throw '*Incompatible app APK package*'
    }
    It 'rejects conflicting signing certificates' {
        "'V2 Signer: certificate SHA-256 digest: ' + ('a' * 64); 'V3 Signer: certificate SHA-256 digest: ' + ('b' * 64)" | Set-Content $signer
        { & $preflight -Serial emulator-5560 -AppApk $app -TestApk $test -AdbCommand $adb -AaptCommand $aapt -ApksignerCommand $signer } | Should -Throw '*signing provenance*'
    }
}
