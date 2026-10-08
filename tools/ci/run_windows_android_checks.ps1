param(
    [Parameter(Mandatory)][ValidateSet('selected', 'full')][string]$CheckMode,
    [Parameter(Mandatory)][AllowEmptyString()][string]$AndroidClasses
)

$ErrorActionPreference = 'Stop'
if ($CheckMode -eq 'selected' -and [string]::IsNullOrWhiteSpace($AndroidClasses)) {
    throw 'Selected Android checks require at least one test class.'
}
$serial = 'emulator-5556'
$avd = 'SwypetrisCI35'
$sdk = $env:ANDROID_HOME

if (-not $sdk) { throw 'ANDROID_HOME is required on the Windows runner.' }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$emulator = Join-Path $sdk 'emulator/emulator.exe'
if (-not (Test-Path -LiteralPath $adb) -or -not (Test-Path -LiteralPath $emulator)) {
    throw 'The configured Android SDK lacks adb or the emulator.'
}
if (-not (Test-Path -LiteralPath (Join-Path $env:USERPROFILE ".android/avd/$avd.ini"))) {
    throw "The isolated $avd AVD must be prepared before registering the runner."
}
if ((& $adb -s $serial get-state 2>$null) -join '' -eq 'device') {
    throw "$serial is already in use; the CI emulator must be started by this step."
}

$process = Start-Process -FilePath $emulator -ArgumentList @(
    "@$avd", '-port', '5556', '-no-window', '-no-audio', '-no-snapshot', '-wipe-data'
) -WindowStyle Hidden -PassThru
try {
    $deadline = (Get-Date).AddMinutes(4)
    do {
        if ($process.HasExited) { throw "The $avd emulator exited during startup ($($process.ExitCode))." }
        $state = (& $adb -s $serial get-state 2>$null) -join ''
        if ($state -eq 'device') {
            $booted = (& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join ''
            if ($booted.Trim() -eq '1') { break }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if ((Get-Date) -ge $deadline) { throw "The $avd emulator did not boot within four minutes." }

    & ./gradlew.bat :app:assembleDebugAndroidTest --build-cache --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Android test APK build failed ($LASTEXITCODE)." }
    & $adb -s $serial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Application APK installation failed.' }
    & $adb -s $serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Android test APK installation failed.' }

    $instrumentArgs = @('-s', $serial, 'shell', 'am', 'instrument', '-w', '-r')
    if ($CheckMode -eq 'selected') {
        $instrumentArgs += @('-e', 'class', $AndroidClasses)
    }
    $instrumentArgs += 'ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner'
    $report = 'app/build/outputs/androidTest-results/windows/instrumentation.txt'
    New-Item -ItemType Directory -Force -Path (Split-Path $report) | Out-Null
    # Never let a report from an earlier checkout/attempt serve as release evidence.
    Remove-Item -LiteralPath 'app/build/outputs/androidTest-results/windows/environment.json' -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath 'app/build/outputs/androidTest-results/windows/smoke.txt' -ErrorAction SilentlyContinue
    & $adb @instrumentArgs | Tee-Object -FilePath $report
    if ($LASTEXITCODE -ne 0) { throw "Android instrumentation command failed ($LASTEXITCODE)." }
    & python3 tools/ci/verify_instrumentation_output.py $CheckMode $AndroidClasses $report
    if ($LASTEXITCODE -ne 0) { throw "Android instrumentation verification failed ($LASTEXITCODE)." }

    & $adb -s $serial shell monkey -p ru.itoltec.swypetris -c android.intent.category.LAUNCHER 1 |
        Tee-Object -FilePath 'app/build/outputs/androidTest-results/windows/smoke.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Launcher smoke test failed.' }
    $environment = @{
        os = 'Windows'; mode = $CheckMode; android_classes = $AndroidClasses
        serial = $serial; avd = $avd; instrumentation = 'success'; smoke = 'success'
        api = ((& $adb -s $serial shell getprop ro.build.version.sdk) -join '').Trim()
        abi = ((& $adb -s $serial shell getprop ro.product.cpu.abi) -join '').Trim()
        fingerprint = ((& $adb -s $serial shell getprop ro.build.fingerprint) -join '').Trim()
        java = ((& java -version 2>&1) -join "`n")
        emulator = ((& $emulator -version) -join "`n")
        emulator_options = "@$avd -port 5556 -no-window -no-audio -no-snapshot -wipe-data"
        instrumentation_command = "adb $($instrumentArgs -join ' ')"
    }
    $environment | ConvertTo-Json | Set-Content -Encoding utf8 'app/build/outputs/androidTest-results/windows/environment.json'
} finally {
    & $adb -s $serial emu kill 2>$null | Out-Null
    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
}
