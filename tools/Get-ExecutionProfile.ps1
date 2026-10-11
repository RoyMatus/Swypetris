<# Cheap read-only diagnosis before an epic, a new-machine resumption or standalone ticket.
No Gradle build, emulator boot, installation, benchmark, signing or network request. #>
param(
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Context,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkDirectory = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'ExecutionProfile.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'release/Emulator-Diagnostics.psm1') -Force
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$directory = Join-Path $project ("app/build/verification/host-{0}" -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$timer = [Diagnostics.Stopwatch]::StartNew()
$facts = [ordered]@{
    logicalProcessors=[Environment]::ProcessorCount; totalMemoryGiB=$null; availableMemoryGiB=$null
    freeDiskGiB=$null; cpuLoadPercent=$null; javaReady=$false; sdkReady=$false
    emulatorReady=$false; accelerationReady=$false
    hostArchitecture=[Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
}
$errors = @()
try {
    if ($IsWindows) {
        $os = Get-CimInstance Win32_OperatingSystem -OperationTimeoutSec 5
        $machine = Get-CimInstance Win32_ComputerSystem -OperationTimeoutSec 5
        $cpu = Get-CimInstance Win32_Processor -OperationTimeoutSec 5
        $facts.totalMemoryGiB = $machine.TotalPhysicalMemory / 1GB
        $facts.availableMemoryGiB = $os.FreePhysicalMemory / 1MB
        $facts.cpuLoadPercent = ($cpu | Measure-Object LoadPercentage -Average).Average
    } elseif ($IsLinux) {
        $mem = Get-Content -LiteralPath '/proc/meminfo' -Raw
        if ($mem -notmatch '(?m)^MemTotal:\s+(\d+) kB') { throw 'MemTotal unavailable.' }
        $total = [double]$Matches[1] / 1MB
        if ($mem -notmatch '(?m)^MemAvailable:\s+(\d+) kB') { throw 'MemAvailable unavailable.' }
        $available = [double]$Matches[1] / 1MB
        $limit = $null; $used = $null
        if (Test-Path -LiteralPath '/sys/fs/cgroup/memory.max') {
            $value = (Get-Content -LiteralPath '/sys/fs/cgroup/memory.max' -Raw).Trim()
            if ($value -match '^\d+$') {
                $limit = [double]$value / 1GB
                if (Test-Path -LiteralPath '/sys/fs/cgroup/memory.current') { $used = [double](Get-Content -LiteralPath '/sys/fs/cgroup/memory.current' -Raw) / 1GB }
            }
        } elseif (Test-Path -LiteralPath '/sys/fs/cgroup/memory/memory.limit_in_bytes') {
            $limit = [double](Get-Content -LiteralPath '/sys/fs/cgroup/memory/memory.limit_in_bytes' -Raw) / 1GB
            $used = [double](Get-Content -LiteralPath '/sys/fs/cgroup/memory/memory.usage_in_bytes' -Raw) / 1GB
        }
        $memory = Limit-ExecutionResources -TotalGiB $total -AvailableGiB $available -LimitGiB $limit -UsedGiB $used
        $facts.totalMemoryGiB = $memory.totalMemoryGiB
        $facts.availableMemoryGiB = $memory.availableMemoryGiB
    } else { $errors += 'Automatic memory probe supports Windows/Linux; measure other hosts explicitly.' }
    $facts.freeDiskGiB = (Get-Item -LiteralPath $project).PSDrive.Free / 1GB
} catch { $errors += $_.Exception.Message }
$java = if ($JavaHome) { Join-Path $JavaHome $(if ($IsWindows) { 'bin/java.exe' } else { 'bin/java' }) } else { (Get-Command java -ErrorAction SilentlyContinue).Source }
if (!$java -and $IsWindows -and !$PSBoundParameters.ContainsKey('JavaHome')) {
    # IntelliJ-managed JDKs are commonly installed but absent from PATH/JAVA_HOME.
    $jdkDirectory = Join-Path $env:USERPROFILE '.jdks'
    $jdk = Get-ChildItem -LiteralPath $jdkDirectory -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(?:jbr|.*jdk)-?21(?:\.|$)' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe')) } |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { $JavaHome=$jdk.FullName; $java=Join-Path $JavaHome 'bin/java.exe' }
}
$javaVersion = $null
if ($java -and (Test-Path -LiteralPath $java)) {
    try {
    $result = Invoke-BoundedDiagnostic -Executable $java -Arguments @('-version') -OutputFile (Join-Path $directory 'java.log')
    $javaVersion = Get-Content -LiteralPath $result.output -Raw
    $facts.javaReady = !$result.timedOut -and $result.exitCode -eq 0 -and $javaVersion -match 'version "21(?:\.|\")' -and
        (Test-Path -LiteralPath (Join-Path (Split-Path $java) $(if ($IsWindows) { 'javac.exe' } else { 'javac' })))
    } catch { $errors += "Java probe: $($_.Exception.Message)" }
}
if (!$SdkDirectory) {
    $SdkDirectory = if ($IsWindows) { Join-Path $env:LOCALAPPDATA 'Android/Sdk' } else { Join-Path $env:HOME 'Android/Sdk' }
}
try {
    $platform = Get-ProjectAndroidPlatform (Get-Content -LiteralPath (Join-Path $project 'app/build.gradle.kts') -Raw)
    $facts.requiredPlatform = $platform
    $facts.sdkReady = (Test-Path -LiteralPath (Join-Path $SdkDirectory "platforms/$platform/android.jar")) -and
        @(Get-ChildItem -LiteralPath (Join-Path $SdkDirectory 'build-tools') -Directory -ErrorAction SilentlyContinue).Count -gt 0
} catch { $errors += $_.Exception.Message }
$emulator = Join-Path $SdkDirectory $(if ($IsWindows) { 'emulator/emulator.exe' } else { 'emulator/emulator' })
$facts.emulatorReady = (Test-Path -LiteralPath $emulator) -and
    (Test-CompatibleEmulatorImage -SdkDirectory $SdkDirectory -Architecture $facts.hostArchitecture)
if (Test-Path -LiteralPath $emulator) {
    try {
    $result = Invoke-BoundedDiagnostic -Executable $emulator -Arguments @('-accel-check') -OutputFile (Join-Path $directory 'acceleration.log')
    $facts.accelerationReady = !$result.timedOut -and $result.exitCode -eq 0
    } catch { $errors += "Acceleration probe: $($_.Exception.Message)" }
}
$adb = Join-Path $SdkDirectory $(if ($IsWindows) { 'platform-tools/adb.exe' } else { 'platform-tools/adb' })
if (Test-Path -LiteralPath $adb) {
    try { $null = Invoke-BoundedDiagnostic -Executable $adb -Arguments @('devices','-l') -OutputFile (Join-Path $directory 'devices.log') }
    catch { $errors += "Device inventory: $($_.Exception.Message)" }
}
$hostKey = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(
    [Environment]::MachineName + '/' + [Runtime.InteropServices.RuntimeInformation]::OSDescription))).ToLowerInvariant()
$profile = [ordered]@{
    schema=1; context=$Context; capturedUtc=[datetimeoffset]::UtcNow.ToString('o'); hostKey=$hostKey
    executionEnvironment=$(if ($env:GITHUB_ACTIONS -eq 'true') { 'GitHubActions' } elseif ($env:CODESPACES -eq 'true') { 'Codespaces' } else { 'CurrentShell' })
    toolchain=@{ javaHome=$(if ($java) { Split-Path (Split-Path $java) } else { $null }); sdkDirectory=$SdkDirectory }
    facts=$facts; decision=(Get-ExecutionDecision -Facts $facts); probeErrors=$errors
    diagnosticSeconds=$timer.Elapsed.TotalSeconds
}
$file = Join-Path $directory 'profile.json'
$profile | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $file -Encoding utf8
$profile.decision | ConvertTo-Json -Depth 3
Write-Output "Execution profile: $file"
