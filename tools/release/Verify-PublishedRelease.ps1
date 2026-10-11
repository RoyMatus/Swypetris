<# Read-only post-publication check. No build, install, publication or credential-bearing asset downloads. #>
param(
    [Parameter(Mandatory)][ValidatePattern('^v\d+\.\d+\.\d+$')][string]$Tag,
    [Parameter(Mandatory)][ValidateRange(1,[long]::MaxValue)][long]$ReleaseRunId,
    [Parameter(Mandatory)][string]$JavaHome
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Release-Verification.ps1')
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $project 'tools/GitHub-Api.psm1') -Force
$client = New-GitHubClient -Repository 'RoyMatus/Swypetris'
$directory = Join-Path $project ("app/build/verification/published-{0}-{1}" -f $Tag, (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$started = [datetimeoffset]::UtcNow
$summary = [ordered]@{ result='failed'; tag=$Tag; runId=$ReleaseRunId; startedUtc=$started.ToString('o') }
$previousJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
try {
    if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'JavaHome must contain a JDK.' }
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
    $release = Invoke-GitHubRest -Client $client -Method Get -Path "releases/tags/$Tag"
    if ($release.draft -or $release.prerelease -or $release.tag_name -cne $Tag) { throw 'Expected a published stable release.' }
    $ref = Invoke-GitHubRest -Client $client -Method Get -Path "git/ref/tags/$Tag"
    $object = $ref.object
    for ($depth=0; $object.type -eq 'tag' -and $depth -lt 5; $depth++) {
        $annotated = Invoke-GitHubRest -Client $client -Method Get -Path "git/tags/$($object.sha)"
        $object = $annotated.object
    }
    if ($object.type -ne 'commit') { throw 'Release tag does not resolve to a commit.' }
    $run = Invoke-GitHubRest -Client $client -Method Get -Path "actions/runs/$ReleaseRunId"
    Assert-PublicationRun -Run $run -Commit $object.sha
    foreach ($name in @('Swypetris.apk','update.json','SHA256SUMS.txt')) {
        $assets = @($release.assets | Where-Object name -CEQ $name)
        if ($assets.Count -ne 1) { throw "Missing or duplicate public asset: $name" }
        $asset = $assets[0]
        $expectedUrl = "https://github.com/RoyMatus/Swypetris/releases/download/$Tag/$name"
        if ($asset.browser_download_url -cne $expectedUrl) { throw 'Unexpected public asset URL.' }
        $file = Join-Path $directory $name
        # Deliberately no Authorization header on public downloads or redirects.
        Invoke-WebRequest -Uri $expectedUrl -OutFile $file -ConnectionTimeoutSeconds 20 -OperationTimeoutSeconds 180
        if ((Get-Item -LiteralPath $file).Length -ne $asset.size -or
            $asset.digest -cne ('sha256:' + (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant())) { throw 'Public asset size/API digest mismatch.' }
    }
    $summary.files = Assert-PublishedReleaseFiles -Directory $directory -Version $Tag.Substring(1)
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and (Test-Path (Join-Path $_.FullName 'aapt.exe')) } |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (!$buildTools) { throw 'Android build-tools required for APK verification.' }
    $apk = Join-Path $directory 'Swypetris.apk'
    $summary.certificateSha256 = Assert-ReleaseSignature -Artifact $apk -Role app -Project $project -JavaHome $JavaHome -BuildTools $buildTools.FullName
    $badging = @(& (Join-Path $buildTools.FullName 'aapt.exe') dump badging $apk 2>&1)
    if ($LASTEXITCODE -ne 0 -or $badging[0] -notmatch "^package: name='ru\.itoltec\.swypetris' versionCode='(\d+)' versionName='([^']+)'" -or
        $Matches[1] -ne [string]$summary.files.versionCode -or $Matches[2] -cne $summary.files.version) { throw 'Public APK package/version does not match metadata.' }
    $summary.commit = $object.sha
    $summary.releaseUrl = $release.html_url
    $summary.runAttempt = $run.run_attempt
    $summary.result = 'passed'
} catch {
    $summary.error = $_.Exception.Message
    throw
} finally {
    if ($null -eq $previousJavaHome) { Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue }
    else { [Environment]::SetEnvironmentVariable('JAVA_HOME', $previousJavaHome, 'Process') }
    $summary.durationSeconds = ([datetimeoffset]::UtcNow - $started).TotalSeconds
    $summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $directory 'summary.json') -Encoding utf8
    Write-Output "Published-release verification: $($summary.result); evidence: $directory"
}
