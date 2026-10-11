<# Verify exact-main full regression, signed artifacts and actual signed runtime locally; never publishes. #>
param(
    [Parameter(Mandatory)][string]$EvidencePath,
    [Parameter(Mandatory)][ValidatePattern('^emulator-\d+$')][string]$Serial,
    [Parameter(Mandatory)][string]$Python,
    [Parameter(Mandatory)][string]$JavaHome,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.swypetris-signing'),
    [ValidateRange(1,64)][int]$MaxWorkers = 4
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $project 'tools/GitHub-Api.psm1') -Force
Import-Module (Join-Path $project 'tools/LocalVerification.psm1') -Force
. (Join-Path $PSScriptRoot 'Local-Release.ps1')
$client = New-GitHubClient 'RoyMatus/Swypetris'
$commit = & git -C $project rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve release commit.' }
Assert-LocalReleaseMain $client $project $commit
$evidence = Get-Content -LiteralPath $EvidencePath -Raw | ConvertFrom-Json
$null = Assert-LocalVerificationEvidence -EvidencePath $EvidencePath -Project $project -HeadSha $commit -BaseSha $evidence.base -RequireFull -Python $Python
$regressionHash = (Get-FileHash -LiteralPath $EvidencePath -Algorithm SHA256).Hash.ToLowerInvariant()
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$devices = @(& $adb devices -l)
if ($LASTEXITCODE -ne 0 -or !($devices | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s+device\s" })) { throw 'Explicit emulator is not online.' }
$deviceModel = & $adb -s $Serial shell getprop ro.product.model
if ($LASTEXITCODE -ne 0) { throw 'Cannot identify release emulator.' }
Write-Output "Signed release smoke device: $Serial ($deviceModel). Only Swypetris data on this emulator will be reset."
$previousJava = $env:JAVA_HOME
$previousPath = $env:PATH
$started = [datetimeoffset]::UtcNow
$directory = Join-Path $project ('app/build/verification/local-release-' + $started.ToString('yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$summary = [ordered]@{ result='failed'; commit=$commit; startedUtc=$started.ToString('o') }
try {
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $env:PATH = (Join-Path $sdk 'platform-tools') + [IO.Path]::PathSeparator + $previousPath
    & (Join-Path $PSScriptRoot 'Build-Release.ps1') -SigningDirectory $SigningDirectory -MaxWorkers $MaxWorkers
    $versionLine = @(Get-Content (Join-Path $project 'gradle.properties') | Where-Object { $_ -match '^swypetrisVersion\s*=' })
    if ($versionLine.Count -ne 1 -or $versionLine[0] -notmatch '^swypetrisVersion\s*=\s*(\d+\.\d+\.\d+)\s*$') { throw 'Invalid release version.' }
    $version = $Matches[1]
    $dist = Join-Path $project "dist/$version"
    # Snapshot verified inputs; subsequent builds cannot silently replace this release candidate.
    foreach ($name in @('Swypetris.apk', "Swypetris-$version.aab", 'update.json')) {
        Copy-Item -LiteralPath (Join-Path $dist $name) -Destination $directory
    }
    $buildSummary = Get-ChildItem (Join-Path $project 'app/build/verification/release-*.json') |
        Where-Object { $_.LastWriteTimeUtc -ge $started.UtcDateTime } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (!$buildSummary) { throw 'Missing signed build summary.' }
    $build = Get-Content -LiteralPath $buildSummary.FullName -Raw | ConvertFrom-Json
    if ($build.result -cne 'passed' -or $build.commit -cne $commit -or $build.version -cne $version) { throw 'Signed build summary identity mismatch.' }
    Copy-Item -LiteralPath $buildSummary.FullName -Destination (Join-Path $directory 'signed-build.json')
    Reset-LocalReleaseApp -Adb $adb -Serial $Serial
    $smoke = Join-Path $directory 'smoke'
    & $Python (Join-Path $project 'tools/ci/release_smoke.py') --serial $Serial --apk (Join-Path $directory 'Swypetris.apk') --output $smoke 2>&1 | Tee-Object -FilePath (Join-Path $directory 'smoke.log')
    if ($LASTEXITCODE -ne 0) { throw 'Actual signed release smoke failed; inspect preserved diagnostics.' }
    $sums = foreach ($name in @('Swypetris.apk','update.json')) {
        '{0}  {1}' -f (Get-FileHash -LiteralPath (Join-Path $directory $name) -Algorithm SHA256).Hash.ToLowerInvariant(), $name
    }
    $sums | Set-Content -LiteralPath (Join-Path $directory 'SHA256SUMS.txt') -Encoding utf8NoBOM
    $null = Assert-LocalVerificationEvidence -EvidencePath $EvidencePath -Project $project -HeadSha $commit -BaseSha $evidence.base -RequireFull -Python $Python
    if ((Get-FileHash -LiteralPath $EvidencePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $regressionHash) { throw 'Regression evidence changed during release verification.' }
    Assert-LocalReleaseMain $client $project $commit
    Copy-LocalReleaseRegression -EvidencePath $EvidencePath -Destination (Join-Path $directory 'regression')
    $null = Assert-LocalVerificationEvidence -EvidencePath (Join-Path $directory 'regression/evidence.json') -Project $project -HeadSha $commit -BaseSha $evidence.base -RequireFull -Python $Python
    $proof = [ordered]@{
        schema=1; kind='swypetris-local-release'; repository='RoyMatus/Swypetris'; result='passed'
        commit=$commit; version=$version; verifiedUtc=[datetimeoffset]::UtcNow.ToString('o'); regressionSha256=$regressionHash
        certificates=@{ app=($build.roles | Where-Object role -EQ app).certificateSha256; upload=($build.roles | Where-Object role -EQ upload).certificateSha256 }
        artifacts=@(foreach ($name in @('Swypetris.apk', "Swypetris-$version.aab", 'update.json', 'SHA256SUMS.txt')) { Get-LocalReleaseArtifact (Join-Path $directory $name) })
        smoke=@{ result='passed'; serial=$Serial; model=[string]$deviceModel; files=@(Get-ChildItem -LiteralPath $smoke -File | ForEach-Object { Get-LocalReleaseArtifact $_.FullName }) }
    }
    Assert-LocalReleaseProvenance $proof $commit $version
    $proof | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $directory 'local-release-provenance.json') -Encoding utf8NoBOM
    $summary.result = 'passed'
} finally {
    $env:JAVA_HOME = $previousJava
    $env:PATH = $previousPath
    $summary.durationSeconds = ([datetimeoffset]::UtcNow - $started).TotalSeconds
    $summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'summary.json') -Encoding utf8
    Write-Output "Local signed release verification: $($summary.result); evidence: $directory"
}
