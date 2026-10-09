param([Parameter(Mandatory)][string]$Workspace)
$ErrorActionPreference = 'Stop'
$sourcePath = Join-Path $PSScriptRoot 'run_windows_android_checks.ps1'
$source = [IO.File]::ReadAllText($sourcePath)
$tokens = $null
$errors = $null
[Management.Automation.Language.Parser]::ParseFile($sourcePath, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors) { throw ($errors | Out-String) }
$start = $source.IndexOf('    $report = ')
$end = $source.IndexOf('    $instrumentArgs = ', $start)
if ($start -lt 0 -or $end -lt $start) { throw 'Smoke setup must precede instrumentation setup' }
$block = [scriptblock]::Create($source.Substring($start, $end - $start))
function Invoke-FakeAdb {
    $global:LASTEXITCODE = $script:smokeCode
    'Events injected: 1'
}
$adb = 'Invoke-FakeAdb'
$serial = 'emulator-5556'
$directory = $Workspace
New-Item -ItemType Directory -Force $directory | Out-Null
Push-Location $directory
try {
    foreach ($script:smokeCode in @(0, 1)) {
        $output = 'app/build/outputs/androidTest-results/windows'
        New-Item -ItemType Directory -Force $output | Out-Null
        foreach ($name in @('environment.json', 'smoke.txt', 'instrumentation.txt')) {
            'stale' | Set-Content (Join-Path $output $name)
        }
        $continued = $false
        $failure = $null
        try { & $block; $continued = $true } catch { $failure = $_.Exception.Message }
        if ($continued -ne ($smokeCode -eq 0)) { throw "Unexpected continuation for exit $smokeCode" }
        if ($smokeCode -eq 1 -and $failure -ne 'Launcher smoke test failed.') { throw 'Smoke failure did not block' }
        if ((Test-Path "$output/environment.json") -or (Test-Path "$output/instrumentation.txt")) {
            throw 'Stale evidence remains'
        }
        if ((Get-Content "$output/smoke.txt") -contains 'stale') { throw 'Old smoke report was accepted' }
        "Smoke exit ${smokeCode}: continuation=$continued; stale evidence removed"
    }
} finally { Pop-Location }
