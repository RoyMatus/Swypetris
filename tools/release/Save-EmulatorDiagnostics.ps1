<# Read-only bounded snapshot. Never restart a device, retry tests or stop a shared daemon. #>
param([Parameter(Mandatory)][ValidatePattern('^emulator-\d+$')][string]$Serial)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Emulator-Diagnostics.psm1') -Force
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($IsWindows) { Join-Path $env:LOCALAPPDATA 'Android/Sdk' } else { Join-Path $env:HOME 'Android/Sdk' }
$adb = Join-Path $sdk $(if ($IsWindows) { 'platform-tools/adb.exe' } else { 'platform-tools/adb' })
if (!(Test-Path -LiteralPath $adb)) { $adb = (Get-Command adb -ErrorAction Stop).Source }
$directory = Join-Path $project ("app/build/verification/emulator-diagnostics-{0}" -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$commands = @(
    @{name='devices'; args=@('devices','-l')},
    @{name='state'; args=@('-s',$Serial,'get-state')},
    @{name='boot'; args=@('-s',$Serial,'shell','getprop','sys.boot_completed')},
    @{name='model'; args=@('-s',$Serial,'shell','getprop','ro.product.model')},
    @{name='api'; args=@('-s',$Serial,'shell','getprop','ro.build.version.sdk')},
    @{name='memory'; args=@('-s',$Serial,'shell','dumpsys','meminfo','ru.itoltec.swypetris')}
)
$results = foreach ($command in $commands) {
    $result = Invoke-BoundedDiagnostic -Executable $adb -Arguments $command.args -OutputFile (Join-Path $directory "$($command.name).log")
    [ordered]@{ command=$command.name; timedOut=$result.timedOut; exitCode=$result.exitCode }
}
$processes = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '^(emulator|qemu-system.*)$' } |
    Select-Object Id, ProcessName, WorkingSet64, CPU)
[ordered]@{ serial=$Serial; capturedUtc=[datetimeoffset]::UtcNow.ToString('o'); commands=$results; emulatorProcesses=$processes; cause='Unknown; snapshot is diagnostic evidence, not a root-cause assertion.' } |
    ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $directory 'summary.json') -Encoding utf8
Write-Output "Emulator diagnostics: $directory"
