[CmdletBinding()]
param([Parameter(Mandatory)][ValidatePattern('^emulator-\d+$')][string]$Serial,
    [Parameter(Mandatory)][string]$JavaHome,[Parameter(Mandatory)][string]$SdkDirectory,
    [ValidateRange(2,5)][int]$Runs=3,[ValidateRange(1,64)][int]$MaxWorkers=2)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $root 'tools/Emulator-Readiness.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Delivery-Evidence.psm1') -Force
$adb=Join-Path $SdkDirectory 'platform-tools/adb.exe'
$null=Assert-EmulatorReady -AdbCommand $adb -Serial $Serial
$directory=Join-Path $root ('app/build/verification/phase-profile-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$null=New-Item -ItemType Directory $directory
$runner=(Join-Path $root 'tools/Verify-Tests.ps1').Replace("'","''")
$jdk=$JavaHome.Replace("'","''");$sdk=$SdkDirectory.Replace("'","''")
for($run=1;$run -le $Runs;$run++){
    $runId=[guid]::NewGuid().ToString('N')
    $command="& '$runner' -Suite FocusedAndroid -TestClass @('NextQueueTest','ThemeSoundIntegrationTest') -Serial '$Serial' -JavaHome '$jdk' -SdkDirectory '$sdk' -MaxWorkers $MaxWorkers -ProfilePhases -ProfileRunId '$runId' -Reason 'Compare unchanged full preview matrix and native audio phases'"
    & (Get-Process -Id $PID).Path -NoProfile -Command $command *> (Join-Path $directory "run-$run.log")
    if($LASTEXITCODE){throw "Profiling run$run failed; retain logs and diagnose. No retry-to-green."}
    $outputRoot=Join-Path $root 'app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected'
    $files=@(Get-ChildItem -LiteralPath $outputRoot -Recurse -File -Filter "delivery-profile-*-$runId.json")
    if($files.Count -ne 2){throw 'Expected two fresh phase profiles; stale/missing profiles are not measurements.'}
    $profiles=@($files|ForEach-Object {Get-Content -LiteralPath $_.FullName -Raw|ConvertFrom-Json})
    Test-DeliveryPhaseProfiles -Profiles $profiles -RunId $runId|ConvertTo-Json|Set-Content (Join-Path $directory "run-$run-validation.json")
    foreach($file in $files){
        Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $directory "run-$run-$($file.Name)")
    }
}
Write-Output "Comparable phase profiles: $directory. No speedup is inferred without a validated implementation and matching before/after runs."
