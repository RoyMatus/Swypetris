<# Run local checks with a short, report-backed summary. Full Gradle output stays under app/build. #>
param(
    [ValidateSet('Fast', 'Android')][string]$Suite = 'Fast',
    [string]$Serial,
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$logDirectory = Join-Path $project 'app/build/verification'
$gradle = Join-Path $project 'gradlew.bat'
$previousJavaHome = $env:JAVA_HOME
$previousSerial = $env:ANDROID_SERIAL

if ($JavaHome) {
    if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
        throw "Java was not found under -JavaHome: $JavaHome"
    }
    $resolvedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
} elseif (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java is unavailable. Set JAVA_HOME or pass -JavaHome.'
}

if ($Suite -eq 'Android') {
    if ($Serial -notmatch '^emulator-\d+$') {
        throw 'Android checks require an explicit emulator serial, for example -Serial emulator-5554.'
    }
    $adbPath = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
    $adb = if (Test-Path -LiteralPath $adbPath) { $adbPath } else {
        $command = Get-Command adb -ErrorAction SilentlyContinue
        if ($command) { $command.Source } else { throw 'adb was not found in the Android SDK or PATH.' }
    }
    $devices = & $adb devices
    if ($LASTEXITCODE -ne 0 -or -not ($devices | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s+device\b" })) {
        throw "Emulator $Serial is not connected and ready."
    }
}

if ($Suite -eq 'Fast') {
    $tasks = @(':app:assembleDebug', ':app:lintDebug', ':app:testDebugUnitTest', ':app:jacocoDebugUnitTestReport')
} else {
    $tasks = @(':app:connectedDebugAndroidTest')
}

New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$log = Join-Path $logDirectory ("{0}-{1}.log" -f $Suite.ToLowerInvariant(), (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$started = Get-Date
try {
    if ($resolvedJavaHome) { $env:JAVA_HOME = $resolvedJavaHome }
    if ($Suite -eq 'Android') { $env:ANDROID_SERIAL = $Serial }
    Push-Location $project
    try {
        & $gradle @tasks --console=plain *> $log
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:ANDROID_SERIAL = $previousSerial
}

if ($exitCode -ne 0) {
    Write-Output "Gradle failed (exit $exitCode). Log: $log"
    Get-Content -LiteralPath $log -Tail 40
    exit $exitCode
}

if ($Suite -eq 'Fast') {
    $reportDirectory = Join-Path $project 'app/build/test-results/testDebugUnitTest'
    $reports = @(Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue)
} else {
    $reportDirectory = Join-Path $project 'app/build/outputs/androidTest-results/connected/debug'
    $reports = @(Get-ChildItem -LiteralPath $reportDirectory -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1)
    if ($reports.Count -gt 0 -and $reports[0].LastWriteTime -lt $started.AddSeconds(-5)) {
        $reports = @()
    }
}

if ($reports.Count -eq 0) {
    Write-Output "Gradle passed, but no current $Suite XML test report was found. Log: $log"
    exit 1
}

$tests = 0
$failures = 0
$skipped = 0
foreach ($report in $reports) {
    [xml]$xml = Get-Content -LiteralPath $report.FullName -Raw
    $summary = if ($Suite -eq 'Fast') { $xml.testsuite } else { $xml.testsuites }
    $tests += [int]$summary.tests
    $failures += [int]$summary.failures + [int]$summary.errors
    $skipped += [int]$summary.skipped
}
if ($tests -eq 0 -or $failures -gt 0) {
    Write-Output "$Suite checks failed: $tests tests, $failures failures, $skipped skipped. Log: $log"
    exit 1
}
Write-Output "$Suite checks passed: $tests tests, $failures failures, $skipped skipped. Log: $log"

if ($Suite -eq 'Fast') {
    $coverageFile = Join-Path $project 'app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml'
    if (Test-Path -LiteralPath $coverageFile) {
        [xml]$coverage = Get-Content -LiteralPath $coverageFile -Raw
        foreach ($kind in @('LINE', 'BRANCH')) {
            $counter = $coverage.report.counter | Where-Object { $_.type -eq $kind } | Select-Object -First 1
            if ($counter) {
                $covered = [int]$counter.covered
                $total = $covered + [int]$counter.missed
                if ($total -gt 0) {
                    Write-Output ("JaCoCo {0}: {1}/{2} ({3:P1})" -f $kind.ToLowerInvariant(), $covered, $total, ($covered / $total))
                }
            }
        }
    }
}
