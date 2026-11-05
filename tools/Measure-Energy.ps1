<# Run the opt-in gameplay workload on an explicitly selected device with Perfetto power rails.
   Install the chosen application APK and its compatible test APK first. Results are device-wide
   power measurements, not an attribution of all consumed energy to Swypetris. #>
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label,
    [ValidateRange(5, 3600)][int]$Seconds = 600,
    [ValidateRange(1, 10)][int]$Runs = 3,
    [switch]$Menu,
    [switch]$SeededClears,
    [switch]$NoMusic,
    [ValidatePattern('^[a-z0-9_]+$')][string]$Palette,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
if ($Menu -and $SeededClears) { throw 'SeededClears requires gameplay, not Menu.' }
$emulator = $Serial -match '^emulator-\d+$'
if ($emulator) { Write-Output 'Emulator harness validation only; not physical-device battery evidence.' }
$output = Join-Path $PSScriptRoot "../app/build/energy/$Label"
if ((Test-Path -LiteralPath $output) -and @(Get-ChildItem -LiteralPath $output -Force).Count) {
    throw 'Choose a new Label; existing measurement evidence must not be overwritten.'
}
New-Item -ItemType Directory -Force -Path $output | Out-Null
function Invoke-Device([string[]]$Arguments) {
    $result = & $Adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Arguments -join ' ')" }
    return $result
}
$settings = @{}
foreach ($name in @('screen_brightness_mode', 'screen_brightness', 'screen_off_timeout', 'min_refresh_rate', 'peak_refresh_rate')) {
    $settings[$name] = (Invoke-Device @('shell', 'settings', 'get', 'system', $name)).Trim()
}
$settings | ConvertTo-Json | Set-Content (Join-Path $output 'original-display-settings.json')
@{ seconds=$Seconds; runs=$Runs; menu=[bool]$Menu; seededClears=[bool]$SeededClears;
    music=(!$NoMusic); palette=$Palette } | ConvertTo-Json | Set-Content (Join-Path $output 'scenario.json')
try {
    foreach ($entry in @{screen_brightness_mode='0'; screen_brightness='128'; screen_off_timeout='7200000'; min_refresh_rate='60.0'; peak_refresh_rate='60.0'}.GetEnumerator()) {
        Invoke-Device @('shell', 'settings', 'put', 'system', $entry.Key, $entry.Value) | Out-Null
    }
    Invoke-Device @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
    Invoke-Device @('shell', 'dumpsys', 'display') | Set-Content (Join-Path $output 'display.txt')
    Invoke-Device @('shell', 'getprop', 'ro.build.fingerprint') | Set-Content (Join-Path $output 'device-build.txt')
    Invoke-Device @('shell', 'cmd', 'wifi', 'status') | Set-Content (Join-Path $output 'wifi.txt')
    for ($run = 1; $run -le $Runs; $run++) {
        $name = "swypetris-$Label-$run"
        $remote = "/data/misc/perfetto-traces/$name.trace"
        $config = Join-Path $output "$run.pbtxt"
        @"
buffers { size_kb: 32768 fill_policy: RING_BUFFER }
duration_ms: $(($Seconds + 15) * 1000)
write_into_file: true
file_write_period_ms: 5000
flush_period_ms: 5000
data_sources { config { name: "linux.ftrace" ftrace_config { ftrace_events: "sched/sched_switch" ftrace_events: "sched/sched_waking" ftrace_events: "power/cpu_frequency" atrace_categories: "gfx" atrace_categories: "view" atrace_apps: "ru.itoltec.swypetris" } } }
data_sources { config { name: "linux.process_stats" process_stats_config { scan_all_processes_on_start: true proc_stats_poll_ms: 1000 } } }
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
data_sources { config { name: "android.power" android_power_config { battery_poll_ms: 1000 battery_counters: BATTERY_COUNTER_CAPACITY_PERCENT battery_counters: BATTERY_COUNTER_CURRENT battery_counters: BATTERY_COUNTER_CHARGE collect_power_rails: true } } }
"@ | Set-Content -LiteralPath $config -Encoding ascii
        $battery = Invoke-Device @('shell', 'dumpsys', 'battery')
        $battery | Set-Content (Join-Path $output "$run-battery-before.txt")
        if (!$emulator -and ($battery -join "`n") -match '(AC|USB|Wireless|Dock) powered: true') {
            throw 'Battery-drain comparisons require an unplugged device.'
        }
        Invoke-Device @('shell', 'dumpsys', 'thermalservice') | Set-Content (Join-Path $output "$run-thermal-before.txt")
        Invoke-Device @('push', $config, '/data/local/tmp/swypetris-energy.pbtxt') | Out-Null
        $session = "$name-$([guid]::NewGuid().ToString('N'))"
        Invoke-Device @('shell', "cat /data/local/tmp/swypetris-energy.pbtxt | perfetto --detach=$session --txt -c - -o $remote") | Out-Null
        Write-Output "$Label run $run/$Runs started ($Seconds seconds)."
        try {
            $arguments = @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                'ru.itoltec.swypetris.EnergyScenarioTest', '-e', 'energyScenario', 'true', '-e',
                'energySeconds', "$Seconds", '-e', 'energyMenu', "$Menu".ToLowerInvariant(),
                '-e', 'energySeededClears', "$SeededClears".ToLowerInvariant(),
                '-e', 'energyMusic', "$(!$NoMusic)".ToLowerInvariant())
            if ($Palette) { $arguments += @('-e', 'energyPalette', $Palette) }
            $arguments += 'ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner'
            $result = Invoke-Device $arguments
            $result | Set-Content (Join-Path $output "$run-instrumentation.txt")
            if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw "Energy scenario failed in run $run" }
        } finally {
            try {
                Invoke-Device @('shell', 'perfetto', "--attach=$session", '--stop') | Out-Null
            } finally {
                Invoke-Device @('shell', 'dumpsys', 'battery') | Set-Content (Join-Path $output "$run-battery-after.txt")
                Invoke-Device @('shell', 'dumpsys', 'thermalservice') | Set-Content (Join-Path $output "$run-thermal-after.txt")
                Invoke-Device @('pull', '-z', 'zstd', $remote, (Join-Path $output "$run.trace")) | Out-Null
            }
        }
        Write-Output "$Label run $run/$Runs complete."
    }
} finally {
    foreach ($entry in $settings.GetEnumerator()) {
        if ($entry.Value -eq 'null') { Invoke-Device @('shell', 'settings', 'delete', 'system', $entry.Key) | Out-Null }
        else { Invoke-Device @('shell', 'settings', 'put', 'system', $entry.Key, $entry.Value) | Out-Null }
    }
}
