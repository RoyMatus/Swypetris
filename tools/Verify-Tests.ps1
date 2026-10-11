<# Run local checks with timed, XML-backed results. Logs/JSON stay under app/build. #>
param(
    [ValidateSet('Fast', 'Android', 'FocusedJvm', 'FocusedAndroid')][string]$Suite = 'Fast',
    [string[]]$TestClass,
    [string]$Serial,
    [string]$JavaHome
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'Verification.psm1') -Force
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$classes = @(Resolve-VerificationClasses -Project $project -Suite $Suite -TestClass $TestClass | Sort-Object -Unique)
$deviceSuite = $Suite -in @('Android', 'FocusedAndroid')
$gradle = Join-Path $project 'gradlew.bat'
if ($JavaHome) {
    if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw "Java was not found under -JavaHome: $JavaHome" }
    $JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
} elseif (!$env:JAVA_HOME -and !(Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java is unavailable. Set JAVA_HOME or pass -JavaHome.'
}
if ($deviceSuite) {
    if ($Serial -notmatch '^emulator-\d+$') { throw 'Android checks require an explicit emulator serial.' }
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $adb = Join-Path $sdk 'platform-tools/adb.exe'
    if (!(Test-Path -LiteralPath $adb)) { $adb = (Get-Command adb -ErrorAction Stop).Source }
    $devices = & $adb devices -l
    if ($LASTEXITCODE -ne 0 -or !($devices | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s+device\b" })) {
        throw "Emulator $Serial is not connected and ready."
    }
}
$directory = Join-Path $project 'app/build/verification'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$stem = Join-Path $directory ("{0}-{1}" -f $Suite.ToLowerInvariant(), (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$log = "$stem.log"
$started = Get-Date
$timer = [System.Diagnostics.Stopwatch]::StartNew()
$summary = New-VerificationSummary -Project $project -Suite $Suite -Classes $classes -Serial $Serial -Started $started
$summary.log = $log
$previousJavaHome = $env:JAVA_HOME
$previousSerial = $env:ANDROID_SERIAL
$exitCode = 1
try {
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java).Source }
    $javaVersion = @(& $java -version 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Java version.' }
    $summary.java = $javaVersion[0].ToString()
    if ($deviceSuite) {
        $env:ANDROID_SERIAL = $Serial
        $summary.deviceModel = (& $adb -s $Serial shell getprop ro.product.model).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read emulator model.' }
        $summary.deviceApi = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read emulator API.' }
    }
    $tasks = @(Get-VerificationTasks -Suite $Suite -Classes $classes)
    Push-Location $project
    try {
        & $gradle @tasks --build-cache --console=plain *> $log
        $gradleExit = $LASTEXITCODE
    } finally { Pop-Location }
    if ($gradleExit -ne 0) { throw "Gradle failed (exit $gradleExit)." }
    $task = if ($deviceSuite) { ':app:connectedDebugAndroidTest' } else { ':app:testDebugUnitTest' }
    $outcome = Get-VerificationTaskOutcome -Log $log -Task $task
    $summary.testTaskOutcome = $outcome
    $reused = $outcome -in @('UP-TO-DATE', 'FROM-CACHE')
    if ($outcome -notin @('EXECUTED', 'UP-TO-DATE', 'FROM-CACHE')) { throw "Tests were not successful: $outcome" }
    if ($deviceSuite -and $reused) { throw 'Device tests did not execute.' }
    $reportDirectory = if ($deviceSuite) { 'app/build/outputs/androidTest-results/connected/debug' } else { 'app/build/test-results/testDebugUnitTest' }
    $reports = @(Get-ChildItem (Join-Path $project $reportDirectory) -Recurse -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
    if ($deviceSuite) { $reports = @($reports | Where-Object { $_.LastWriteTimeUtc -ge $started.ToUniversalTime().AddSeconds(-5) }) }
    $result = Read-VerificationReports -Reports @($reports.FullName) -Classes $classes -Started $started -Reused $reused
    $summary.tests = $result
    $summary.reusedTestResults = $reused
    if ($Suite -eq 'Fast') {
        $coverageFile = Join-Path $project 'app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml'
        if (!(Test-Path -LiteralPath $coverageFile)) { throw 'No JaCoCo XML report found.' }
        [xml]$coverage = Get-Content -LiteralPath $coverageFile -Raw
        foreach ($kind in @('LINE', 'BRANCH')) {
            $counter = $coverage.report.counter | Where-Object type -eq $kind | Select-Object -First 1
            if ($counter) {
                $covered = [int]$counter.covered
                $total = $covered + [int]$counter.missed
                if ($total -gt 0) { Write-Output ("JaCoCo {0}: {1}/{2} ({3:P1})" -f $kind.ToLowerInvariant(), $covered, $total, ($covered / $total)) }
            }
        }
    }
    $summary.result = 'passed'
    $exitCode = 0
    $execution = if ($reused) { "reused $outcome results; no fresh test execution" } else { 'executed' }
    Write-Output "$Suite checks passed: $($result.passed) passed, $($result.skipped) skipped ($execution)."
} catch {
    $exitCode = 1
    $summary.result = 'failed'
    $summary.error = $_.Exception.Message
    Write-Output "Verification failed: $($_.Exception.Message)"
    if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 40 }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:ANDROID_SERIAL = $previousSerial
    $timer.Stop()
    $summary.durationSeconds = [math]::Round($timer.Elapsed.TotalSeconds, 3)
    $summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath "$stem.json" -Encoding utf8
    Write-Output "Duration: $($summary.durationSeconds)s. Log: $log. Summary: $stem.json"
}
exit $exitCode
