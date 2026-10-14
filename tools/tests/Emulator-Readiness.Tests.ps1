BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '../Emulator-Readiness.psm1') -Force
    Import-Module (Join-Path $PSScriptRoot '../release/Emulator-Diagnostics.psm1') -Force
}
Describe 'Explicit API 35 emulator readiness' {
    BeforeEach {
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness {
            switch ($Arguments -join ' ') {
                'devices -l' { "List of devices attached`nemulator-5560 device model:Fixture" }
                '-s emulator-5560 get-state' { 'device' }
                '-s emulator-5560 shell getprop sys.boot_completed' { '1' }
                '-s emulator-5560 shell getprop ro.build.version.sdk' { '35' }
                '-s emulator-5560 shell getprop ro.product.model' { 'Fixture' }
                '-s emulator-5560 shell getprop ro.build.fingerprint' { 'fixture/api35/image' }
                '-s emulator-5560 shell dumpsys window windows' { 'mCurrentFocus=Window{abc u0 ru.itoltec.swypetris/ru.itoltec.swypetris.MainActivity}' }
                default { throw "Unexpected command: $Arguments" }
            }
        }
    }
    It 'checks explicit serial, API and model before authorizing instrumentation' {
        $result = Assert-EmulatorReady -AdbCommand fixture -Serial emulator-5560
        $result.api | Should -Be 35
        $result.model | Should -Be Fixture
        $result.buildFingerprint | Should -Be 'fixture/api35/image'
        Should -Invoke Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness -Times 7 -Exactly
    }
    It 'rejects physical devices and absent emulators' {
        { Assert-EmulatorReady fixture Pixel7 } | Should -Throw
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness { 'List of devices attached' } -ParameterFilter { $Arguments[0] -eq 'devices' }
        { Assert-EmulatorReady fixture emulator-5560 } | Should -Throw '*not listed*'
    }
    It 'rejects the wrong API' {
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness { '34' } -ParameterFilter { $Arguments[-1] -eq 'ro.build.version.sdk' }
        { Assert-EmulatorReady fixture emulator-5560 } | Should -Throw '*API 35*'
    }
    It 'bounds an offline emulator instead of rebooting it' {
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness { 'offline' } -ParameterFilter { $Arguments[-1] -eq 'get-state' }
        $watch = [Diagnostics.Stopwatch]::StartNew()
        { Assert-EmulatorReady fixture emulator-5560 -TimeoutSeconds 1 -PollIntervalMilliseconds 10 } | Should -Throw '*readiness timed out*'
        $watch.Elapsed.TotalSeconds | Should -BeLessThan 3
    }
    It 'waits for completed boot within the same bounded budget' {
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness { '0' } -ParameterFilter { $Arguments[-1] -eq 'sys.boot_completed' }
        { Assert-EmulatorReady fixture emulator-5560 -TimeoutSeconds 1 -PollIntervalMilliseconds 10 } | Should -Throw '*readiness timed out*'
    }
    It 'waits through transient adb offline failures and then observes completed boot' {
        $script:stateReads = 0
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness {
            $script:stateReads++
            if ($script:stateReads -lt 2) { throw 'adb get-state failed: device offline' }
            'device'
        } -ParameterFilter { $Arguments[-1] -eq 'get-state' }
        (Assert-EmulatorReady fixture emulator-5560 -PollIntervalMilliseconds 10).ready | Should -BeTrue
        Should -Invoke Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness -Times 2 -Exactly -ParameterFilter { $Arguments[-1] -eq 'get-state' }
    }
    It 'rejects a visible unfocused error window' {
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness {
            "Window #0 Window{err u0 Application Error: com.android.systemui}:`n  isVisible=true`nmCurrentFocus=Window{abc u0 ru.itoltec.swypetris/MainActivity}"
        } -ParameterFilter { $Arguments[-1] -eq 'windows' }
        { Assert-EmulatorReady fixture emulator-5560 } | Should -Throw '*Blocking system window*'
    }
    It 'rejects a blocking <Title> window without dismissing it' -ForEach @(
        @{ Title='Application Not Responding: com.android.systemui' },
        @{ Title='Application Error: ru.itoltec.swypetris' }
    ) {
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness { "mCurrentFocus=Window{abc u0 $Title}" }.GetNewClosure() -ParameterFilter { $Arguments[-1] -eq 'windows' }
        { Assert-EmulatorReady fixture emulator-5560 } | Should -Throw '*Blocking system window*'
        Should -Invoke Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness -Times 0 -ParameterFilter { ($Arguments -join ' ') -match 'input|reboot|kill' }
    }
    It 'does not mistake a hidden historical error window for an active blocker' {
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness {
            "Window #0 Window{old u0 Application Error: old.app}:`n  mViewVisibility=0x8`n  isOnScreen=false`n  isVisible=false`nmCurrentFocus=Window{abc u0 ru.itoltec.swypetris/MainActivity}"
        } -ParameterFilter { $Arguments[-1] -eq 'windows' }
        (Assert-EmulatorReady fixture emulator-5560).ready | Should -BeTrue
    }
    It 'fails closed when the current window snapshot is unavailable' {
        Mock Invoke-EmulatorReadinessCommand -ModuleName Emulator-Readiness { '' } -ParameterFilter { $Arguments[-1] -eq 'windows' }
        { Assert-EmulatorReady fixture emulator-5560 } | Should -Throw '*window snapshot*'
    }
}
Describe 'Bounded diagnostic output' {
    It 'does not start a child when diagnostic output cannot be opened' {
        $marker=Join-Path $TestDrive 'unexpected-child.txt'
        $fixture=Join-Path $TestDrive 'output-open.ps1'
        Set-Content $fixture "Start-Sleep -Milliseconds 100; Set-Content -LiteralPath '$($marker.Replace("'","''"))' 'started'"
        { Invoke-BoundedDiagnostic -Executable (Get-Process -Id $PID).Path -Arguments @('-NoProfile','-File',$fixture) -OutputFile $TestDrive -TimeoutSeconds 1 } | Should -Throw
        Start-Sleep -Seconds 1
        Test-Path -LiteralPath $marker | Should -BeFalse
    }
    It 'bounds a process timeout while retaining partial stdout' {
        $exe = (Get-Process -Id $PID).Path
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $result = Invoke-BoundedDiagnostic -Executable $exe -Arguments @('-NoProfile','-Command','Write-Output partial; Start-Sleep -Seconds 30') -OutputFile (Join-Path $TestDrive 'hung.log') -TimeoutSeconds 1
        $result.timedOut | Should -BeTrue
        $watch.Elapsed.TotalSeconds | Should -BeLessThan 4
        Get-Content $result.output | Should -Contain partial
    }
    It 'bounds draining inherited stdout after the command exits' {
        $exe = (Get-Process -Id $PID).Path
        $childPid = Join-Path $TestDrive 'child.pid'
        $fixture = Join-Path $TestDrive 'inherited-output.ps1'
        @'
param([string]$PidFile)
$start = [Diagnostics.ProcessStartInfo]::new()
$start.FileName = (Get-Process -Id $PID).Path
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
foreach ($arg in @('-NoProfile','-Command','Start-Sleep -Seconds 30')) { $start.ArgumentList.Add($arg) }
$child = [Diagnostics.Process]::Start($start)
$child.Id | Set-Content -LiteralPath $PidFile
Write-Output retained
exit 0
'@ | Set-Content -LiteralPath $fixture
        $watch = [Diagnostics.Stopwatch]::StartNew()
        try {
            $result = Invoke-BoundedDiagnostic -Executable $exe -Arguments @('-NoProfile','-File',$fixture,$childPid) -OutputFile (Join-Path $TestDrive 'inherited.log') -TimeoutSeconds 2 -DrainTimeoutMilliseconds 200
            $result.timedOut | Should -BeFalse
            $result.outputDrainTimedOut | Should -BeTrue
            $watch.Elapsed.TotalSeconds | Should -BeLessThan 4
            Get-Content $result.output | Should -Contain retained
        } finally {
            if (Test-Path -LiteralPath $childPid) { Stop-Process -Id ([int](Get-Content -LiteralPath $childPid)) -ErrorAction SilentlyContinue }
        }
    }
    It 'preserves binary screenshot bytes without text conversion' {
        $file = Join-Path $TestDrive 'screenshot.png'
        $exe = (Get-Process -Id $PID).Path
        $result = Invoke-BoundedDiagnostic -Executable $exe -Arguments @('-NoProfile','-Command','[Console]::OpenStandardOutput().Write([byte[]](137,80,78,71,13,10,26,10))') -OutputFile $file -BinaryOutput
        $result.exitCode | Should -Be 0
        [Convert]::ToHexString([IO.File]::ReadAllBytes($file)) | Should -Be '89504E470D0A1A0A'
        $result.outputDrainTimedOut | Should -BeFalse
    }
}
