<# Local-only verification. Evidence is created only for a clean commit; no dispatch fallback. #>
[CmdletBinding()]
param(
    [ValidateSet('PR','Full')][string]$Mode = 'PR',
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$BaseSha,
    [string]$Python = 'python', [string]$JavaHome, [string]$Serial,
    [ValidateRange(1,64)][int]$MaxWorkers = 4,
    [string]$OutputDirectory,
    [int]$PullRequestNumber, [string]$BaseBranch = 'main',
    [string]$SdkDirectory, [switch]$EnableCheckpoints, [string]$ResumeFrom
)
$ErrorActionPreference = 'Stop'
if (!$IsWindows) { throw 'Mandatory verification runs on Windows. Unix checks are optional and do not authorize merges.' }
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Import-Module (Join-Path $PSScriptRoot 'Verification-Preflight.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Emulator-Readiness.psm1') -Force
if (!$OutputDirectory) { $OutputDirectory = Join-Path $project ('app/build/verification/local-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff')) }
if ((Test-Path -LiteralPath $OutputDirectory) -and @(Get-ChildItem -LiteralPath $OutputDirectory -Force).Count) { throw 'Output directory must be empty.' }
$null = New-Item -ItemType Directory -Path $OutputDirectory -Force
$OutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$preflightTimer = [Diagnostics.Stopwatch]::StartNew()
try {
    $pythonVersion = & $Python -c 'import sys; assert sys.version_info >= (3, 10); print(sys.version.split()[0])'
    if ($LASTEXITCODE -ne 0) { throw 'Python 3.10 or newer is required; Windows Store aliases are not interpreters.' }
} catch {
    @{result='failed';reason=$_.Exception.Message;durationSeconds=$preflightTimer.Elapsed.TotalSeconds} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'preflight.json') -Encoding utf8
    throw
}
$headSha = (& git -C $project rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve HEAD.' }
$helper = Join-Path $PSScriptRoot 'ci/local_verification.py'
$plan = & $Python $helper plan --root $project --head $headSha --base $BaseSha --mode $Mode
if ($LASTEXITCODE -ne 0) {
    @{result='failed';reason='Cannot select checks for a clean commit';durationSeconds=$preflightTimer.Elapsed.TotalSeconds} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'preflight.json') -Encoding utf8
    throw 'Cannot select checks for a clean commit.'
}
$selection = $plan | ConvertFrom-Json
$checks = [System.Collections.Generic.List[object]]::new()
$started = [datetime]::UtcNow
try {
    if ($selection.mode -ne 'none') {
        $identity = Assert-VerificationAnalysisIdentity -Project $project -Mode $Mode -Head $headSha -Base $BaseSha -PullRequestNumber $PullRequestNumber -BaseBranch $BaseBranch
    }
    $environment = Resolve-VerificationEnvironment -Project $project -RequiresAndroid ($selection.mode -ne 'none') -JavaHome $JavaHome -SdkDirectory $SdkDirectory
    if ($environment.androidRequired) {
        $JavaHome = $environment.javaHome
        $adb = $environment.adb
        $device = Assert-EmulatorReady -AdbCommand $adb -Serial $Serial -TimeoutSeconds 120
    }
    @{result='passed';durationSeconds=$preflightTimer.Elapsed.TotalSeconds;python=$pythonVersion;environment=$environment;analysis=$identity;device=$device} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputDirectory 'preflight.json') -Encoding utf8
} catch {
    $original = $_
    @{result='failed';reason=$_.Exception.Message;durationSeconds=$preflightTimer.Elapsed.TotalSeconds} | ConvertTo-Json | Set-Content (Join-Path $OutputDirectory 'preflight.json') -Encoding utf8
    if ($adb -and $Serial -match '^emulator-\d+$') {
        try { & (Join-Path $PSScriptRoot 'release/Save-EmulatorDiagnostics.ps1') -Serial $Serial -SdkDirectory $environment.sdkDirectory }
        catch { Write-Warning 'Diagnostic capture failed; retaining original preflight failure.' }
    }
    throw $original
}
function Invoke-Check([string]$Name, [string]$Command, [string[]]$Arguments) {
    $dependencies = @{}
    foreach ($dependency in @(switch ($Name) {
        'quality-reports' { 'quality' }
        'sonar' { 'quality'; 'quality-reports' }
        'install-debug' { 'quality' }
        'launcher-smoke' { 'install-debug' }
        'android' { 'quality'; 'launcher-smoke' }
    })) { $dependencies[$dependency] = $checkpointHashes[$dependency] }
    if ($checkpointEnabled) {
        $inputs = Join-Path $OutputDirectory "checkpoint-inputs/$Name"
        $null = New-Item -ItemType Directory -Force $inputs
        $dependencies | ConvertTo-Json | Set-Content (Join-Path $inputs 'dependencies.json') -Encoding utf8
        $previousCheckpoint = "checkpoints/$Name/checkpoint.json"
        $reuseEligible = $resumeDocument -and $Name -in @('python','pester','quality','quality-reports') -and $resumeDocument.files.$previousCheckpoint
        if ($reuseEligible -and $Name -eq 'quality') {
            # Sonar and device stages consume the worktree build outputs. A snapshot
            # alone cannot establish that those live outputs still match the tested APK.
            $prior = Get-Content (Join-Path $resumeDirectory $previousCheckpoint) -Raw | ConvertFrom-Json -AsHashtable
            foreach ($name in @($prior.files.Keys | Where-Object { $_ -like 'app/build/*' })) {
                $live = Join-Path $project $name
                if (!(Test-Path -LiteralPath $live -PathType Leaf) -or (Get-FileHash -LiteralPath $live -Algorithm SHA256).Hash.ToLowerInvariant() -cne $prior.files[$name]) { $reuseEligible = $false; break }
            }
        }
        if ($reuseEligible) {
            $reuse = & $Python $checkpointHelper reuse --checkpoint (Join-Path $resumeDirectory $previousCheckpoint) --checkpoint-sha256 $resumeDocument.files.$previousCheckpoint --fingerprint $fingerprintPath --destination $OutputDirectory --dependencies (Join-Path $inputs 'dependencies.json') 2> (Join-Path $inputs 'reuse-rejected.log')
            if ($LASTEXITCODE -eq 0) {
                $reused = $reuse | ConvertFrom-Json -AsHashtable
                $checks.Add($reused)
                $checkpointHashes[$Name] = $resumeDocument.files.$previousCheckpoint
                Write-Host "Reused $Name (original duration $($reused.durationSeconds)s)."
                return
            }
            Write-Host "Checkpoint $Name is ineligible; running required stage (reason retained)."
        }
    }
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $logName = "$Name.log"
    & $Command @Arguments *> (Join-Path $OutputDirectory $logName)
    $code = $LASTEXITCODE
    $check = @{name=$Name; result=$(if ($code -eq 0) {'passed'} else {'failed'}); durationSeconds=$timer.Elapsed.TotalSeconds; log=$logName}
    $checks.Add($check)
    $reports = @($logName)
    if ($code -eq 0 -and $Name -eq 'quality') {
        foreach ($relative in @('app/build/test-results/testDebugUnitTest','app/build/reports/jacoco','app/build/reports/detekt','app/build/outputs/apk')) {
            $destination = Join-Path $OutputDirectory $relative
            $null = New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force
            Copy-Item -LiteralPath (Join-Path $project $relative) -Destination $destination -Recurse
        }
        Copy-Item app/build/reports/lint-results-debug.xml (Join-Path $OutputDirectory 'app/build/reports/lint-results-debug.xml')
        $reports += @(Get-ChildItem (Join-Path $OutputDirectory 'app/build') -Recurse -File | ForEach-Object { [IO.Path]::GetRelativePath($OutputDirectory,$_.FullName).Replace('\','/') })
    }
    if ($code -eq 0 -and $Name -eq 'android') {
        $android = Join-Path $project 'app/build/outputs/androidTest-results/connected/debug'
        $null = New-Item -ItemType Directory -Path (Join-Path $OutputDirectory 'android') -Force
        Get-ChildItem $android -Recurse -Filter 'TEST-*.xml' | Where-Object LastWriteTimeUtc -ge $started.AddSeconds(-5) | ForEach-Object { Copy-Item $_.FullName (Join-Path $OutputDirectory 'android') }
        $reports += @(Get-ChildItem (Join-Path $OutputDirectory 'android') -File | ForEach-Object { 'android/'+$_.Name })
    }
    if ($checkpointEnabled) {
        $check | ConvertTo-Json | Set-Content (Join-Path $inputs 'check.json') -Encoding utf8
        ConvertTo-Json -InputObject @($reports) | Set-Content (Join-Path $inputs 'files.json') -Encoding utf8
        $snapshot = & $Python $checkpointHelper record --store (Join-Path $OutputDirectory "checkpoints/$Name") --fingerprint $fingerprintPath --stage $Name --check (Join-Path $inputs 'check.json') --reports $OutputDirectory --files (Join-Path $inputs 'files.json') --dependencies (Join-Path $inputs 'dependencies.json')
        if ($LASTEXITCODE -ne 0) { throw "Cannot preserve checkpoint for $Name." }
        $checkpointHashes[$Name] = ($snapshot | ConvertFrom-Json).checkpointSha256
    }
    if ($Name -eq 'install-debug' -and $code -ne 0 -and (Get-Content (Join-Path $OutputDirectory $logName) -Raw) -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        throw "Swypetris signing mismatch on $Serial. Installation diagnostics saved in $OutputDirectory/$logName. Use a fresh owned emulator or explicitly remove only Swypetris from that emulator before rerunning."
    }
    if ($code -ne 0) { throw "Check $Name failed; see $OutputDirectory/$logName" }
}
$oldJava = $env:JAVA_HOME
$oldSerial = $env:ANDROID_SERIAL
$oldSdk = $env:ANDROID_HOME
$oldSdkRoot = $env:ANDROID_SDK_ROOT
$result = 'failed'
$checkpointEnabled = $EnableCheckpoints -or [bool]$ResumeFrom
$checkpointHashes = @{}
$checkpointHelper = Join-Path $PSScriptRoot 'ci/verification_checkpoint.py'
if ($checkpointEnabled) {
    $runtime = @{python="$pythonVersion";powershell=$PSVersionTable.PSVersion.ToString()}
    if ($environment.androidRequired) {
        $runtime.java=$environment.javaVersion; $runtime.adb=(Get-FileHash $adb -Algorithm SHA256).Hash.ToLowerInvariant()
        $runtime.sdkApi=@{platform=$environment.platform;path=$environment.sdkDirectory;platformHash=(Get-FileHash (Join-Path $environment.sdkDirectory "platforms/$($environment.platform)/android.jar") -Algorithm SHA256).Hash.ToLowerInvariant()}
        $runtime.device=@{serial=$Serial;model=$device.model;api=$device.api;build=$device.buildFingerprint}
        $runtime.sonar=@{organization=$env:SONAR_ORGANIZATION;project=$env:SONAR_PROJECT_KEY;pr=$PullRequestNumber;branch=$identity.branch}; $runtime.maxWorkers=$MaxWorkers
    }
    $runtimePath = Join-Path $OutputDirectory 'runtime.json'
    $runtime | ConvertTo-Json -Depth 8 | Set-Content $runtimePath -Encoding utf8
    $fingerprintPath = Join-Path $OutputDirectory 'fingerprint.json'
    $fingerprint = & $Python $checkpointHelper fingerprint --root $project --head $headSha --base $BaseSha --mode $Mode --runtime $runtimePath
    if ($LASTEXITCODE -ne 0) { throw 'Cannot fingerprint checkpoint inputs.' }
    $fingerprint | Set-Content $fingerprintPath -Encoding utf8
    if ($ResumeFrom) {
        $resumePath = (Resolve-Path -LiteralPath $ResumeFrom).Path
        $resumeDirectory = Split-Path -Parent $resumePath
        $resumeDocument = Get-Content $resumePath -Raw | ConvertFrom-Json -AsHashtable
        if ($resumeDocument.schema -ne 1 -or !$resumeDocument.files) { throw 'Resume requires an original schema-one candidate/evidence with bound checkpoint hashes.' }
        Copy-Item -LiteralPath $resumePath -Destination (Join-Path $OutputDirectory 'resume-origin.json')
    }
}
Push-Location $project
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    if ($environment.androidRequired) { $env:ANDROID_HOME = $environment.sdkDirectory; $env:ANDROID_SDK_ROOT = $environment.sdkDirectory }
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
        $gradle = Join-Path $project $(if ($IsWindows) {'gradlew.bat'} else {'gradlew'})
        $common = @('--build-cache','--console=plain',"--max-workers=$MaxWorkers")
        Invoke-Check 'quality' $gradle (@(':app:assembleDebug',':app:assembleDebugAndroidTest',':app:lintDebug',':app:detekt',':app:test',':app:jacocoDebugUnitTestReport') + $common)
        Invoke-Check 'quality-reports' $Python @('tools/ci/verify_quality_results.py')
        $sonar = @('sonar','-Dsonar.qualitygate.wait=true',"-Dsonar.scm.revision=$headSha")
        if ($PullRequestNumber -gt 0) {
            $branch = $identity.branch
            $sonar += @("-Dsonar.pullrequest.key=$PullRequestNumber","-Dsonar.pullrequest.branch=$branch","-Dsonar.pullrequest.base=$BaseBranch")
        } else {
            $sonar += '-Dsonar.branch.name=main'
        }
        Invoke-Check 'sonar' $gradle ($sonar + $common)
        $null = Assert-EmulatorReady -AdbCommand $adb -Serial $Serial -TimeoutSeconds 120
        Invoke-Check 'install-debug' $adb @('-s',$Serial,'install','-r','app/build/outputs/apk/debug/app-debug.apk')
        Invoke-Check 'launcher-smoke' $adb @('-s',$Serial,'shell','monkey','-p','ru.itoltec.swypetris','-c','android.intent.category.LAUNCHER','1')
        if ((Get-Content (Join-Path $OutputDirectory 'launcher-smoke.log') -Raw) -notmatch '(?m)^Events injected: 1\s*$') { throw 'Launcher smoke output incomplete.' }
        $deviceArgs = @('-NoProfile','-File',(Join-Path $PSScriptRoot 'Verify-Tests.ps1'),'-Serial',$Serial,'-MaxWorkers',"$MaxWorkers")
        if ($JavaHome) { $deviceArgs += @('-JavaHome',$JavaHome) }
        if ($selection.mode -eq 'full') { $deviceArgs += @('-Suite','Android') }
        else {
            # Invoke through -Command so PowerShell binds the class array, not one comma-containing class.
            $classLiteral = ($selection.android.Split(',') | ForEach-Object { "'$_'" }) -join ','
            $escapedScript = (Join-Path $PSScriptRoot 'Verify-Tests.ps1').Replace("'","''")
            $deviceArgs = @('-NoProfile','-Command',"& '$escapedScript' -Suite FocusedAndroid -TestClass @($classLiteral) -Serial '$Serial' -MaxWorkers $MaxWorkers")
        }
        $null = Assert-EmulatorReady -AdbCommand $adb -Serial $Serial -TimeoutSeconds 120
        Invoke-Check 'android' (Get-Process -Id $PID).Path $deviceArgs
    }
    $result = 'passed'
} catch {
    $failureReason = $_.Exception.Message
    $original = $_
    if ($environment.androidRequired) {
        try { & (Join-Path $PSScriptRoot 'release/Save-EmulatorDiagnostics.ps1') -Serial $Serial -SdkDirectory $environment.sdkDirectory }
        catch { Write-Warning 'Diagnostic capture failed; retaining original verification failure.' }
    }
    throw $original
} finally {
    Pop-Location
    $env:JAVA_HOME = $oldJava
    $env:ANDROID_SERIAL = $oldSerial
    $env:ANDROID_HOME = $oldSdk
    $env:ANDROID_SDK_ROOT = $oldSdkRoot
    $files = @{}
    Get-ChildItem $OutputDirectory -Recurse -File | Where-Object Name -ne 'evidence.json' | ForEach-Object {
        $files[[IO.Path]::GetRelativePath($OutputDirectory,$_.FullName).Replace('\','/')] = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $evidence = Join-Path $OutputDirectory 'candidate.json'
    @{schema=1; head=$headSha; base=$BaseSha; mode=$Mode; selection=$selection; result=$result; checks=@($checks.ToArray()); files=$files; platform='Windows'; startedUtc=$started.ToString('o');failureReason=$failureReason} | ConvertTo-Json -Depth 12 | Set-Content $evidence -Encoding utf8
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
