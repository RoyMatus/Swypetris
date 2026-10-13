<# Local-only verification. Evidence is created only for a clean commit; no dispatch fallback. #>
[CmdletBinding()]
param(
    [ValidateSet('PR','Full')][string]$Mode = 'PR',
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$BaseSha,
    [string]$Python = 'python', [string]$JavaHome, [string]$Serial,
    [ValidateRange(1,64)][int]$MaxWorkers = 4,
    [string]$OutputDirectory,
    [int]$PullRequestNumber, [string]$BaseBranch = 'main'
)
$ErrorActionPreference = 'Stop'
if (!$IsWindows) { throw 'Mandatory verification runs on Windows. Unix checks are optional and do not authorize merges.' }
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$headSha = (& git -C $project rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve HEAD.' }
$helper = Join-Path $PSScriptRoot 'ci/local_verification.py'
$plan = & $Python $helper plan --root $project --head $headSha --base $BaseSha --mode $Mode
if ($LASTEXITCODE -ne 0) { throw 'Cannot select checks for a clean commit.' }
$selection = $plan | ConvertFrom-Json
if (!$OutputDirectory) { $OutputDirectory = Join-Path $project ('app/build/verification/local-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')) }
if ((Test-Path -LiteralPath $OutputDirectory) -and @(Get-ChildItem -LiteralPath $OutputDirectory -Force).Count) { throw 'Output directory must be empty.' }
$null = New-Item -ItemType Directory -Path $OutputDirectory -Force
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
if (Test-Path (Join-Path $OutputDirectory 'evidence.json')) { throw 'Refusing to overwrite completed evidence.' }
$checks = [System.Collections.Generic.List[object]]::new()
function Invoke-Check([string]$Name, [string]$Command, [string[]]$Arguments) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $logName = "$Name.log"
    & $Command @Arguments *> (Join-Path $OutputDirectory $logName)
    $code = $LASTEXITCODE
    if ($Name -eq 'install-debug' -and $code -ne 0 -and (Get-Content (Join-Path $OutputDirectory $logName) -Raw) -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        throw "Swypetris signing mismatch on $Serial. Installation diagnostics saved in $OutputDirectory/$logName. Use a fresh owned emulator or explicitly remove only Swypetris from that emulator before rerunning."
    }
    $checks.Add(@{name=$Name; result=$(if ($code -eq 0) {'passed'} else {'failed'}); durationSeconds=$timer.Elapsed.TotalSeconds; log=$logName})
    if ($code -ne 0) { throw "Check $Name failed; see $OutputDirectory/$logName" }
}
$oldJava = $env:JAVA_HOME
$oldSerial = $env:ANDROID_SERIAL
$started = [datetime]::UtcNow
$result = 'failed'
Push-Location $project
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    if ($Serial) { $env:ANDROID_SERIAL = $Serial }
    $toolsRequired = $Mode -eq 'Full' -or @('github_scripts','release_scripts','ci_scripts','windows_release_tools').Where({$selection.$_ -eq 'true'}).Count -gt 0
    Invoke-Check 'whitespace' 'git' @('diff','--check',"$BaseSha..$headSha")
    if ($selection.mode -ne 'none') {
        Invoke-Check 'source-inventory' $Python @($helper,'source-inventory','--root',$project,'--head',$headSha,'--base',$BaseSha,'--mode',$Mode)
    }
    if ($toolsRequired) {
        Invoke-Check 'python' $Python @('tools/ci/run_local_tool_tests.py')
        Invoke-Check 'pester' (Get-Process -Id $PID).Path @('-NoProfile','-File',(Join-Path $PSScriptRoot 'Invoke-LocalToolTests.ps1'))
    }
    if ($selection.mode -ne 'none') {
        if ($Serial -notmatch '^emulator-\d+$') { throw 'An explicit headless emulator serial is required.' }
        foreach ($name in @('SONAR_ORGANIZATION','SONAR_PROJECT_KEY','SONAR_TOKEN')) {
            if (![Environment]::GetEnvironmentVariable($name)) { throw "Missing $name; configure locally without committing credentials." }
        }
        if ($Mode -eq 'PR' -and $PullRequestNumber -le 0) { throw 'App PR verification requires -PullRequestNumber for Sonar analysis.' }
        $gradle = Join-Path $project $(if ($IsWindows) {'gradlew.bat'} else {'gradlew'})
        $common = @('--build-cache','--console=plain',"--max-workers=$MaxWorkers")
        Invoke-Check 'quality' $gradle (@(':app:assembleDebug',':app:assembleDebugAndroidTest',':app:lintDebug',':app:detekt',':app:test',':app:jacocoDebugUnitTestReport') + $common)
        Invoke-Check 'quality-reports' $Python @('tools/ci/verify_quality_results.py')
        $sonar = @('sonar','-Dsonar.qualitygate.wait=true',"-Dsonar.scm.revision=$headSha")
        if ($PullRequestNumber -gt 0) {
            $branch = (& git branch --show-current).Trim()
            if (!$branch) { throw 'PR analysis requires a named branch.' }
            $sonar += @("-Dsonar.pullrequest.key=$PullRequestNumber","-Dsonar.pullrequest.branch=$branch","-Dsonar.pullrequest.base=$BaseBranch")
        } else {
            $remoteMain = & git ls-remote origin refs/heads/main
            if ($LASTEXITCODE -ne 0 -or !$remoteMain -or ($remoteMain -split '\s+')[0] -ne $headSha) { throw 'Full Sonar without a PR number requires exact remote main.' }
            $sonar += '-Dsonar.branch.name=main'
        }
        Invoke-Check 'sonar' $gradle ($sonar + $common)
        $sdk = if ($env:ANDROID_HOME) {$env:ANDROID_HOME} else {Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
        $adb = Join-Path $sdk $(if ($IsWindows) {'platform-tools/adb.exe'} else {'platform-tools/adb'})
        $deviceState = & $adb -s $Serial get-state
        if ($LASTEXITCODE -ne 0 -or ($deviceState -join '').Trim() -ne 'device') { throw 'Explicit emulator is unavailable.' }
        $api = & $adb -s $Serial shell getprop ro.build.version.sdk
        if ($LASTEXITCODE -ne 0 -or ($api -join '').Trim() -ne '35') { throw 'Required emulator API is 35.' }
        Invoke-Check 'install-debug' $adb @('-s',$Serial,'install','-r','app/build/outputs/apk/debug/app-debug.apk')
        Invoke-Check 'launcher-smoke' $adb @('-s',$Serial,'shell','monkey','-p','ru.itoltec.swypetris','-c','android.intent.category.LAUNCHER','1')
        if ((Get-Content (Join-Path $OutputDirectory 'launcher-smoke.log') -Raw) -notmatch '(?m)^Events injected: 1\s*$') { throw 'Launcher smoke output incomplete.' }
        if ($selection.mode -eq 'full') {
            $script = (Join-Path $PSScriptRoot 'Verify-Tests.ps1').Replace("'","''")
            Invoke-Check 'picker-preflight' (Get-Process -Id $PID).Path @('-NoProfile','-Command',"& '$script' -Suite FocusedAndroid -TestClass 'ru.itoltec.swypetris.BackgroundPickerTest' -Serial '$Serial' -MaxWorkers $MaxWorkers")
            Invoke-Check 'ui-preflight' (Get-Process -Id $PID).Path @('-NoProfile','-Command',"& '$script' -Suite FocusedAndroid -TestClass @('ru.itoltec.swypetris.VictoryThemeIntegrationTest','ru.itoltec.swypetris.AbsoluteVictoryUiTest','ru.itoltec.swypetris.SmokeTest') -Serial '$Serial' -MaxWorkers $MaxWorkers")
        }

        $deviceArgs = @('-NoProfile','-File',(Join-Path $PSScriptRoot 'Verify-Tests.ps1'),'-Serial',$Serial,'-MaxWorkers',"$MaxWorkers")
        if ($JavaHome) { $deviceArgs += @('-JavaHome',$JavaHome) }
        if ($selection.mode -eq 'full') { $deviceArgs += @('-Suite','Android') }
        else {
            # Invoke through -Command so PowerShell binds the class array, not one comma-containing class.
            $classLiteral = ($selection.android.Split(',') | ForEach-Object { "'$_'" }) -join ','
            $escapedScript = (Join-Path $PSScriptRoot 'Verify-Tests.ps1').Replace("'","''")
            $deviceArgs = @('-NoProfile','-Command',"& '$escapedScript' -Suite FocusedAndroid -TestClass @($classLiteral) -Serial '$Serial' -MaxWorkers $MaxWorkers")
        }
        Invoke-Check 'android' (Get-Process -Id $PID).Path $deviceArgs
        $android = Join-Path $project 'app/build/outputs/androidTest-results/connected/debug'
        $null = New-Item -ItemType Directory -Path (Join-Path $OutputDirectory 'android') -Force
        Get-ChildItem $android -Recurse -Filter 'TEST-*.xml' | Where-Object LastWriteTimeUtc -ge $started.AddSeconds(-5) | ForEach-Object { Copy-Item $_.FullName (Join-Path $OutputDirectory 'android') }
        foreach ($relative in @('app/build/test-results/testDebugUnitTest','app/build/reports/jacoco','app/build/reports/detekt','app/build/outputs/apk')) {
            $destination = Join-Path $OutputDirectory $relative
            $null = New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force
            Copy-Item -LiteralPath (Join-Path $project $relative) -Destination $destination -Recurse
        }
        Copy-Item app/build/reports/lint-results-debug.xml (Join-Path $OutputDirectory 'app/build/reports/lint-results-debug.xml')
    }
    $result = 'passed'
} finally {
    Pop-Location
    $env:JAVA_HOME = $oldJava
    $env:ANDROID_SERIAL = $oldSerial
    $files = @{}
    Get-ChildItem $OutputDirectory -Recurse -File | Where-Object Name -ne 'evidence.json' | ForEach-Object {
        $files[[IO.Path]::GetRelativePath($OutputDirectory,$_.FullName).Replace('\','/')] = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $evidence = Join-Path $OutputDirectory 'candidate.json'
    @{schema=1; head=$headSha; base=$BaseSha; mode=$Mode; selection=$selection; result=$result; checks=@($checks.ToArray()); files=$files; platform='Windows'; startedUtc=$started.ToString('o')} | ConvertTo-Json -Depth 12 | Set-Content $evidence -Encoding utf8
    Write-Host "Evidence: $evidence"
}
$validation = @($helper,'validate','--root',$project,'--head',$headSha,'--base',$BaseSha,'--evidence',$evidence)
& $Python @validation
if ($LASTEXITCODE -ne 0) {
    $failed = Get-Content $evidence -Raw | ConvertFrom-Json
    $failed.result = 'failed'
    $failed | ConvertTo-Json -Depth 12 | Set-Content $evidence -Encoding utf8
    throw 'Completed run failed evidence validation.'
}
Move-Item -LiteralPath $evidence -Destination (Join-Path $OutputDirectory 'evidence.json')
Write-Host "Validated evidence: $OutputDirectory/evidence.json"
