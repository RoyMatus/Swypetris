BeforeAll {
    Import-Module (Join-Path $PSScriptRoot '../Verification.psm1') -Force
    $project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
    function Write-TestXml([string]$Path, [string]$Body) { Set-Content -LiteralPath $Path -Value $Body -Encoding utf8; $Path }
    $passing = '<testsuite tests="1" failures="0" errors="0"><testcase classname="ru.itoltec.swypetris.SmokeTest" name="smoke"/></testsuite>'
}
Describe 'Focused verification selection' {
    It 'resolves short and qualified classes' {
        @(Resolve-VerificationClasses $project FocusedJvm @('BackgroundCropTest', 'ru.itoltec.swypetris.GameEngineTest')) | Should -HaveCount 2
        @(Resolve-VerificationClasses $project FocusedAndroid @('SmokeTest')) | Should -Contain 'ru.itoltec.swypetris.SmokeTest'
    }
    It 'rejects missing, empty, wildcard, method and fixture selections' {
        foreach ($names in @(@(), @('MissingTest'), @('*Test'), @('SmokeTest#smoke'), @('IsolatedStorageRule'), @('SmokeTest,GameUiTest'))) {
            { Resolve-VerificationClasses $project FocusedAndroid $names } | Should -Throw
        }
        { Resolve-VerificationClasses $project Fast @('GameEngineTest') } | Should -Throw
    }
    It 'passes each JVM class as a separate filter and does not leak into Fast' {
        $tasks = @(Get-VerificationTasks FocusedJvm @('A', 'B'))
        ($tasks -join '|') | Should -Be ':app:testDebugUnitTest|--tests|A|--tests|B'
        (Get-VerificationTasks Fast @() -join '|') | Should -Not -Match 'tests\||Arguments.class'
    }
    It 'passes a comma-separated Android class filter' {
        @(Get-VerificationTasks FocusedAndroid @('A', 'B')) | Should -Contain '-Pandroid.testInstrumentationRunnerArguments.class=A,B'
        (Get-VerificationTasks Android @() -join '|') | Should -Not -Match 'Arguments.class'
    }
}
Describe 'Verification evidence' {
    It 'accepts a fresh matching report' {
        $report = Write-TestXml "$TestDrive/fresh.xml" $passing
        $result = Read-VerificationReports @($report) @('ru.itoltec.swypetris.SmokeTest') (Get-Date) $false
        $result.passed | Should -Be 1
    }
    It 'rejects missing and incomplete reports' {
        { Read-VerificationReports @() @() (Get-Date) $false } | Should -Throw
        $report = Write-TestXml "$TestDrive/incomplete.xml" ($passing -replace 'tests="1"', 'tests="2"')
        { Read-VerificationReports @($report) @() (Get-Date) $false } | Should -Throw
    }
    It 'rejects stale executed evidence but labels cached task outcomes separately' {
        $report = Write-TestXml "$TestDrive/stale.xml" $passing
        (Get-Item $report).LastWriteTimeUtc = [datetime]::UtcNow.AddMinutes(-5)
        { Read-VerificationReports @($report) @() (Get-Date) $false } | Should -Throw
        (Read-VerificationReports @($report) @() (Get-Date) $true).passed | Should -Be 1
        Set-Content "$TestDrive/task.log" '> Task :app:testDebugUnitTest UP-TO-DATE'
        Get-VerificationTaskOutcome "$TestDrive/task.log" ':app:testDebugUnitTest' | Should -Be 'UP-TO-DATE'
    }
    It 'rejects XML failure nodes even if the suite totals claim success' {
        $report = Write-TestXml "$TestDrive/failure.xml" ($passing -replace '/></testsuite>', '><failure message="failed"/></testcase></testsuite>')
        { Read-VerificationReports @($report) @() (Get-Date) $false } | Should -Throw
    }
    It 'rejects missing and unexpected requested classes' {
        $report = Write-TestXml "$TestDrive/classes.xml" $passing
        { Read-VerificationReports @($report) @('ru.itoltec.swypetris.GameUiTest') (Get-Date) $false } | Should -Throw
        { Read-VerificationReports @($report) @('ru.itoltec.swypetris.SmokeTest','ru.itoltec.swypetris.GameUiTest') (Get-Date) $false } | Should -Throw
    }
    It 'rejects duplicate and all-skipped results' {
        $report = Write-TestXml "$TestDrive/duplicates.xml" ('<testsuites>' + $passing + $passing + '</testsuites>')
        { Read-VerificationReports @($report) @() (Get-Date) $false } | Should -Throw
        $report = Write-TestXml "$TestDrive/skipped.xml" ($passing -replace '/></testsuite>', '><skipped/></testcase></testsuite>')
        { Read-VerificationReports @($report) @() (Get-Date) $false } | Should -Throw
    }
    It 'rejects a selected class with only skipped cases even when another class passes' {
        $report = Write-TestXml "$TestDrive/selected-skipped.xml" '<testsuite tests="2" failures="0" errors="0"><testcase classname="A" name="passes"/><testcase classname="B" name="optional"><skipped/></testcase></testsuite>'
        { Read-VerificationReports @($report) @('A', 'B') (Get-Date) $false } | Should -Throw '*requested class: B*'
    }
    It 'accepts multiple selected classes with a passing case in each' {
        $report = Write-TestXml "$TestDrive/selected-passing.xml" '<testsuite tests="2" failures="0" errors="0"><testcase classname="A" name="passes"/><testcase classname="B" name="passes"/></testsuite>'
        $result = Read-VerificationReports @($report) @('A', 'B') (Get-Date) $false
        $result.passed | Should -Be 2
        $result.skipped | Should -Be 0
    }
    It 'accepts partial skips within a selected class that also passes' {
        $report = Write-TestXml "$TestDrive/selected-partial.xml" '<testsuite tests="3" failures="0" errors="0"><testcase classname="A" name="passes"/><testcase classname="B" name="passes"/><testcase classname="B" name="optional"><skipped/></testcase></testsuite>'
        $result = Read-VerificationReports @($report) @('A', 'B') (Get-Date) $false
        $result.passed | Should -Be 2
        $result.skipped | Should -Be 1
    }
    It 'retains optional all-skipped classes in an unfiltered run with passing tests' {
        $report = Write-TestXml "$TestDrive/unfiltered-skips.xml" '<testsuite tests="2" failures="0" errors="0"><testcase classname="A" name="passes"/><testcase classname="B" name="optional"><skipped/></testcase></testsuite>'
        $result = Read-VerificationReports @($report) @() (Get-Date) $false
        $result.passed | Should -Be 1
        $result.skipped | Should -Be 1
        $result.classes | Should -Contain 'B'
    }
    It 'detects execution and rejects an absent task' {
        Set-Content "$TestDrive/executed.log" '> Task :app:testDebugUnitTest'
        Get-VerificationTaskOutcome "$TestDrive/executed.log" ':app:testDebugUnitTest' | Should -Be 'EXECUTED'
        { Get-VerificationTaskOutcome "$TestDrive/executed.log" ':app:connectedDebugAndroidTest' } | Should -Throw
    }
}
Describe 'Verification runner failure handling' {
    It 'restores process environment and writes a failed timed summary' -Skip:(!$IsWindows) {
        $fixture = Join-Path $TestDrive 'fixture'
        New-Item -ItemType Directory -Path "$fixture/tools" -Force | Out-Null
        Copy-Item "$project/tools/Verify-Tests.ps1","$project/tools/Verification.psm1" "$fixture/tools"
        Set-Content "$fixture/gradlew.bat" "@echo off`r`necho %*`r`necho deliberate fixture failure`r`nexit /b 7"
        $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-ChildItem "$env:USERPROFILE/.jdks/*/bin/java.exe" | Where-Object FullName -Match 'jbr-21' | Select-Object -First 1).FullName }
        if (!$java) { throw 'This Windows integration fixture needs the local JDK 21.' }
        $jdk = Split-Path (Split-Path $java)
        $previousJava = $env:JAVA_HOME
        $previousSerial = $env:ANDROID_SERIAL
        try {
            $env:JAVA_HOME = $jdk
            $env:ANDROID_SERIAL = 'verification-sentinel'
            $output = & "$fixture/tools/Verify-Tests.ps1" -Suite Fast -JavaHome $jdk -MaxWorkers 2
            $LASTEXITCODE | Should -Be 1
            $env:JAVA_HOME | Should -Be $jdk
            $env:ANDROID_SERIAL | Should -Be 'verification-sentinel'
            $summary = Get-ChildItem "$fixture/app/build/verification" -Filter '*.json' | ForEach-Object { Get-Content $_.FullName -Raw | ConvertFrom-Json }
            $summary.result | Should -Be 'failed'
            $summary.error | Should -Match 'exit 7'
            $summary.durationSeconds | Should -BeGreaterThan 0
            $summary.maxWorkers | Should -Be 2
            ($output -join ' ') | Should -Match '--max-workers=2'
            ($output -join ' ') | Should -Match 'deliberate fixture failure'
        } finally {
            $env:JAVA_HOME = $previousJava
            $env:ANDROID_SERIAL = $previousSerial
        }
    }
    It 'runs the Unix launcher with focused filters and records fresh results' -Skip:($IsWindows) {
        $fixture = Join-Path $TestDrive 'unix-fixture'
        New-Item -ItemType Directory -Path "$fixture/tools","$fixture/jdk/bin","$fixture/app/src/test/java/ru/itoltec/swypetris" -Force | Out-Null
        Copy-Item "$project/tools/Verify-Tests.ps1","$project/tools/Verification.psm1" "$fixture/tools"
        Set-Content "$fixture/app/src/test/java/ru/itoltec/swypetris/ExampleTest.kt" "package ru.itoltec.swypetris`nclass ExampleTest { @Test fun works() {} }"
        Set-Content "$fixture/jdk/bin/java" "#!/bin/sh`necho 'openjdk version 21'"
        $launcher = @'
#!/bin/sh
printf '%s\n' "$@" > arguments.txt
mkdir -p app/build/test-results/testDebugUnitTest
echo '<testsuite tests="1" failures="0" errors="0"><testcase classname="ru.itoltec.swypetris.ExampleTest" name="works"/></testsuite>' > app/build/test-results/testDebugUnitTest/TEST-ExampleTest.xml
echo '> Task :app:testDebugUnitTest'
'@
        # .gitattributes checks out .ps1 with CRLF even on Linux; shell shebangs require LF.
        Set-Content "$fixture/gradlew" ($launcher -replace "`r`n", "`n")
        & chmod +x "$fixture/gradlew" "$fixture/jdk/bin/java"
        $previousJava = $env:JAVA_HOME
        & "$fixture/tools/Verify-Tests.ps1" -Suite FocusedJvm -TestClass ExampleTest -JavaHome "$fixture/jdk" -MaxWorkers 2 | Out-Host
        $LASTEXITCODE | Should -Be 0
        $env:JAVA_HOME | Should -Be $previousJava
        $arguments = Get-Content "$fixture/arguments.txt"
        $arguments | Should -Contain '--max-workers=2'
        $arguments | Should -Contain 'ru.itoltec.swypetris.ExampleTest'
        $summary = Get-ChildItem "$fixture/app/build/verification" -Filter '*.json' | ForEach-Object { Get-Content $_.FullName -Raw | ConvertFrom-Json }
        $summary.result | Should -Be 'passed'
        $summary.tests.passed | Should -Be 1
        $summary.reusedTestResults | Should -BeFalse
    }
}
