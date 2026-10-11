<# Local verification helpers. Never collect environment values or signing arguments. #>
function Resolve-VerificationClasses {
    param([string]$Project, [string]$Suite, [string[]]$TestClass)
    if ($Suite -notin @('FocusedJvm', 'FocusedAndroid')) {
        if ($TestClass.Count) { throw '-TestClass requires FocusedJvm or FocusedAndroid.' }
        return
    }
    if (!$TestClass.Count) { throw 'Focused checks require at least one -TestClass.' }
    $sourceSet = if ($Suite -eq 'FocusedJvm') { 'test' } else { 'androidTest' }
    foreach ($name in $TestClass) {
        if ($name -notmatch '^(?:ru\.itoltec\.swypetris\.)?[A-Za-z][A-Za-z0-9_]*$') {
            throw "Invalid test class: $name. Use a class name, not a method or wildcard."
        }
        $shortName = $name -replace '^ru\.itoltec\.swypetris\.', ''
        $source = Join-Path $Project "app/src/$sourceSet/java/ru/itoltec/swypetris/$shortName.kt"
        if (!(Test-Path -LiteralPath $source)) { throw "Test class does not exist in ${sourceSet}: $name" }
        $content = Get-Content -LiteralPath $source -Raw
        if ($content -notmatch "\bclass\s+$([regex]::Escape($shortName))\b" -or $content -notmatch '(?:@Test\b|BehaviorSpec\s*\()') {
            throw "Source is not a supported test class: $name"
        }
        "ru.itoltec.swypetris.$shortName"
    }
}
function Get-VerificationTasks {
    param([string]$Suite, [string[]]$Classes)
    switch ($Suite) {
        Fast { ':app:assembleDebug'; ':app:lintDebug'; ':app:testDebugUnitTest'; ':app:jacocoDebugUnitTestReport' }
        Android { ':app:connectedDebugAndroidTest' }
        FocusedJvm { ':app:testDebugUnitTest'; foreach ($name in $Classes) { '--tests'; $name } }
        FocusedAndroid { ':app:connectedDebugAndroidTest'; "-Pandroid.testInstrumentationRunnerArguments.class=$($Classes -join ',')" }
        default { throw "Unsupported suite: $Suite" }
    }
}
function Get-VerificationTaskOutcome {
    param([string]$Log, [string]$Task)
    $pattern = '(?m)^> Task ' + [regex]::Escape($Task) + '(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?\s*$'
    $match = [regex]::Match((Get-Content -LiteralPath $Log -Raw), $pattern)
    if (!$match.Success) { throw "No task outcome found for $Task." }
    if ($match.Groups[1].Success) { return $match.Groups[1].Value }
    'EXECUTED'
}
function Read-VerificationReports {
    param([string[]]$Reports, [string[]]$Classes, [datetime]$Started, [bool]$Reused)
    if (!$Reports.Count) { throw 'No XML test reports found.' }
    $cases = @()
    $declared = 0
    foreach ($path in $Reports) {
        if (!$Reused -and (Get-Item -LiteralPath $path).LastWriteTimeUtc -lt $Started.ToUniversalTime().AddSeconds(-5)) {
            throw "Stale XML test report: $path"
        }
        [xml]$xml = Get-Content -LiteralPath $path -Raw
        $suites = @($xml.SelectNodes('//testsuite[not(testsuite)]'))
        if (!$suites.Count) { throw "No test suites in XML report: $path" }
        foreach ($suite in $suites) {
            $declared += [int]$suite.tests
            if ([int]$suite.failures + [int]$suite.errors -gt 0) { throw "Tests failed in XML report: $path" }
        }
        $cases += @($xml.SelectNodes('//testcase'))
    }
    if (!$cases.Count -or $cases.Count -ne $declared) { throw 'Empty or incomplete XML test reports.' }
    if (@($cases | Where-Object { $null -ne $_.failure -or $null -ne $_.error }).Count) { throw 'XML test cases contain failures or errors.' }
    $identities = @($cases | ForEach-Object { "$($_.classname)#$($_.name)" })
    if (@($identities | Select-Object -Unique).Count -ne $cases.Count) { throw 'Duplicate XML test cases.' }
    $actualClasses = @($cases | ForEach-Object { $_.classname } | Sort-Object -Unique)
    if ($Classes.Count -and @(Compare-Object $Classes $actualClasses).Count) { throw 'XML classes do not match the requested classes.' }
    $skipped = @($cases | Where-Object { $null -ne $_.skipped }).Count
    if ($skipped -eq $cases.Count) { throw 'No test case passed; all cases were skipped.' }
    foreach ($name in $Classes) {
        if (!@($cases | Where-Object { $_.classname -eq $name -and $null -eq $_.skipped }).Count) {
            throw "No test case passed in requested class: $name"
        }
    }
    [pscustomobject]@{ tests = $cases.Count; passed = $cases.Count - $skipped; skipped = $skipped; failures = 0; classes = $actualClasses }
}
function New-VerificationSummary {
    param([string]$Project, [string]$Suite, [string[]]$Classes, [string]$Serial, [datetime]$Started)
    $commit = & git -C $Project rev-parse HEAD 2>$null
    $dirty = & git -C $Project status --porcelain 2>$null
    [ordered]@{
        suite = $Suite; requestedClasses = @($Classes); commit = "$commit"; dirty = [bool]$dirty
        startedUtc = $Started.ToUniversalTime().ToString('o'); durationSeconds = 0; result = 'failed'
        os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
        powershell = $PSVersionTable.PSVersion.ToString(); serial = $Serial
    }
}
Export-ModuleMember -Function Resolve-VerificationClasses, Get-VerificationTasks, Get-VerificationTaskOutcome, Read-VerificationReports, New-VerificationSummary
