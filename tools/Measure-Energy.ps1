<# Run the opt-in gameplay workload on an explicitly selected device with Perfetto power rails.
   Install the chosen application APK and its compatible test APK first. Results are device-wide
   power measurements, not an attribution of all consumed energy to Swypetris. #>
param(
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Label,
    [ValidateRange(5, 3600)][int]$Seconds = 600,
    [ValidateRange(1, 10)][int]$Runs = 3,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
$output = Join-Path $PSScriptRoot "../app/build/energy/$Label"
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
try {
    foreach ($entry in @{screen_brightness_mode='0'; screen_brightness='128'; screen_off_timeout='7200000'; min_refresh_rate='60.0'; peak_refresh_rate='60.0'}.GetEnumerator()) {
        Invoke-Device @('shell', 'settings', 'put', 'system', $entry.Key, $entry.Value) | Out-Null
    }
    Invoke-Device @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
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
        Invoke-Device @('shell', 'dumpsys', 'battery') | Set-Content (Join-Path $output "$run-battery-before.txt")
        Invoke-Device @('shell', 'dumpsys', 'thermalservice') | Set-Content (Join-Path $output "$run-thermal-before.txt")
        Invoke-Device @('push', $config, '/data/local/tmp/swypetris-energy.pbtxt') | Out-Null
        $traceProcess = (Invoke-Device @('shell', "cat /data/local/tmp/swypetris-energy.pbtxt | perfetto --background-wait --txt -c - -o $remote") | Select-Object -Last 1).Trim()
        if ($traceProcess -notmatch '^\d+$') { throw "Perfetto did not return a process ID: $traceProcess" }
        Write-Output "$Label run $run/$Runs started ($Seconds seconds)."
        try {
            $result = Invoke-Device @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                'ru.itoltec.swypetris.EnergyScenarioTest', '-e', 'energyScenario', 'true', '-e',
                'energySeconds', "$Seconds", 'ru.itoltec.swypetris.test/androidx.test.runner.AndroidJUnitRunner')
            $result | Set-Content (Join-Path $output "$run-instrumentation.txt")
            if (($result -join "`n") -notmatch 'OK \(1 test\)') { throw "Energy scenario failed in run $run" }
        } finally {
            Invoke-Device @('shell', "kill -TERM $traceProcess 2>/dev/null; while kill -0 $traceProcess 2>/dev/null; do sleep 1; done") | Out-Null
            Invoke-Device @('pull', $remote, (Join-Path $output "$run.trace")) | Out-Null
            Invoke-Device @('shell', 'dumpsys', 'battery') | Set-Content (Join-Path $output "$run-battery-after.txt")
            Invoke-Device @('shell', 'dumpsys', 'thermalservice') | Set-Content (Join-Path $output "$run-thermal-after.txt")
        }
        Write-Output "$Label run $run/$Runs complete."
    }
} finally {
    foreach ($entry in $settings.GetEnumerator()) {
        if ($entry.Value -eq 'null') { Invoke-Device @('shell', 'settings', 'delete', 'system', $entry.Key) | Out-Null }
        else { Invoke-Device @('shell', 'settings', 'put', 'system', $entry.Key, $entry.Value) | Out-Null }
    }
}
