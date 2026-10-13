<# Prepare already-built APKs for a direct, explicitly selected emulator instrumentation run. #>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^emulator-\d+$')][string]$Serial,
    [string]$AppApk = 'app/build/outputs/apk/debug/app-debug.apk',
    [string]$TestApk = 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',
    [string]$AdbCommand, [string]$AaptCommand, [string]$ApksignerCommand
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = if ($AdbCommand) { $AdbCommand } else { Join-Path $sdk 'platform-tools/adb.exe' }
if (!$AaptCommand -or !$ApksignerCommand) {
    $buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (!$buildTools) { throw 'Android build tools are unavailable.' }
}
$aapt = if ($AaptCommand) { $AaptCommand } else { Join-Path $buildTools.FullName 'aapt.exe' }
$apksigner = if ($ApksignerCommand) { $ApksignerCommand } else { Join-Path $buildTools.FullName 'apksigner.bat' }
$directory = Join-Path $project ('app/build/verification/direct-instrumentation-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$null = New-Item -ItemType Directory -Path $directory -Force
$manifest = [ordered]@{serial=$Serial;startedUtc=[datetimeoffset]::UtcNow.ToString('o');appApk=$AppApk;testApk=$TestApk;packages=@()}
try {
    $devices = & $adb devices -l
    if ($LASTEXITCODE -ne 0 -or !($devices | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s+device\b" })) {
        throw "Explicit emulator $Serial is not connected."
    }
    foreach ($entry in @(@{name='app';file=$AppApk;expected='ru.itoltec.swypetris'},
                         @{name='test';file=$TestApk;expected='ru.itoltec.swypetris.test'})) {
        $file = if ([IO.Path]::IsPathRooted($entry.file)) { $entry.file } else { Join-Path $project $entry.file }
        if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Already-built $($entry.name) APK is missing: $file" }
        $badging = @(& $aapt dump badging $file)
        if ($LASTEXITCODE -ne 0 -or $badging[0] -notmatch "^package: name='([^']+)'" -or $Matches[1] -cne $entry.expected) {
            throw "Incompatible $($entry.name) APK package."
        }
        $signature = & $apksigner verify --print-certs $file
        if ($LASTEXITCODE -ne 0) {
            throw "Cannot verify $($entry.name) APK signing provenance."
        }
        $digests = @([regex]::Matches(($signature -join "`n"), '(?m)^(?:V\d+ Signer:|Signer #\d+) certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$') |
            ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } | Sort-Object -Unique)
        if ($digests.Count -ne 1) { throw "Cannot verify $($entry.name) APK signing provenance." }
        $certificate = $digests[0]
        $paths = & $adb -s $Serial shell pm path $entry.expected
        $installed = $LASTEXITCODE -eq 0 -and @($paths | Where-Object { $_ -match '^package:' }).Count -eq 1
        $matchesArtifact = $false
        if ($installed) {
            $devicePath = ($paths | Where-Object { $_ -match '^package:' } | Select-Object -First 1) -replace '^package:', ''
            $pulled = Join-Path $directory "$($entry.name)-installed.apk"
            $null = & $adb -s $Serial pull $devicePath $pulled
            if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $pulled)) {
                $matchesArtifact = (Get-FileHash $pulled -Algorithm SHA256).Hash -ceq (Get-FileHash $file -Algorithm SHA256).Hash
            }
        }
        if (!$matchesArtifact) {
            $install = & $adb -s $Serial install -r $file 2>&1
            if ($LASTEXITCODE -ne 0 -or ($install -join "`n") -notmatch '(?m)^Success\s*$') {
                throw "Installation of $($entry.name) APK failed on $Serial. Existing app data was preserved; see diagnostics."
            }
        }
        $manifest.packages += [ordered]@{name=$entry.expected;apkSha256=(Get-FileHash $file -Algorithm SHA256).Hash.ToLowerInvariant();certificateSha256=$certificate;reused=$matchesArtifact}
    }
    $manifest.result = 'passed'
    Write-Output "Direct instrumentation packages ready on $Serial. Evidence: $directory"
} catch {
    $manifest.result = 'failed'
    $manifest.error = $_.Exception.Message
    try { & (Join-Path $project 'tools/release/Save-EmulatorDiagnostics.ps1') -Serial $Serial | Out-File (Join-Path $directory 'diagnostics-path.txt') }
    catch { "Diagnostic capture failed: $($_.Exception.Message)" | Out-File (Join-Path $directory 'diagnostics-path.txt') }
    throw
} finally {
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $directory 'summary.json') -Encoding utf8
}
