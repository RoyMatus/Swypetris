# Local evidence is an operator-controlled record, not an independent attestation.
function Assert-LocalReleaseProvenance {
    param([object]$Proof, [string]$Commit, [string]$Version)
    if ($Proof.schema -ne 1 -or $Proof.kind -cne 'swypetris-local-release' -or
        $Proof.repository -cne 'RoyMatus/Swypetris' -or $Proof.result -cne 'passed' -or
        $Commit -cnotmatch '^[a-f0-9]{40}$' -or $Proof.commit -cne $Commit -or
        $Version -cnotmatch '^\d+\.\d+\.\d+$' -or $Proof.version -cne $Version -or
        $Proof.regressionSha256 -cnotmatch '^[a-f0-9]{64}$' -or
        $Proof.smoke.result -cne 'passed' -or $Proof.smoke.serial -cnotmatch '^emulator-\d+$') {
        throw 'Invalid local release provenance identity or gates.'
    }
    $expected = @('Swypetris.apk', "Swypetris-$Version.aab", 'update.json', 'SHA256SUMS.txt')
    if (@($Proof.artifacts).Count -ne $expected.Count) { throw 'Invalid local release artifact inventory.' }
    foreach ($name in $expected) {
        $entries = @($Proof.artifacts | Where-Object name -CEQ $name)
        if ($entries.Count -ne 1 -or $entries[0].sha256 -cnotmatch '^[a-f0-9]{64}$' -or
            $entries[0].size -le 0) { throw "Invalid local release artifact: $name" }
    }
    foreach ($role in @('app','upload')) {
        if ($Proof.certificates.$role -cnotmatch '^[a-f0-9]{64}$') { throw 'Missing release certificate evidence.' }
    }
    if (@($Proof.smoke.files).Count -lt 2) { throw 'Missing signed runtime diagnostics.' }
    foreach ($required in @('observations.json','logcat.txt')) {
        if (@($Proof.smoke.files | Where-Object name -CEQ $required).Count -ne 1) { throw 'Missing signed runtime diagnostics.' }
    }
    foreach ($file in $Proof.smoke.files) {
        if ($file.name -cnotmatch '^[A-Za-z0-9_.-]+$' -or $file.sha256 -cnotmatch '^[a-f0-9]{64}$' -or $file.size -le 0) {
            throw 'Invalid signed runtime diagnostic evidence.'
        }
    }
}

function Assert-LocalReleaseArtifact {
    param([object]$Entry, [string]$Path)
    if (!(Test-Path -LiteralPath $Path -PathType Leaf) -or
        (Get-Item -LiteralPath $Path).Length -ne $Entry.size -or
        (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $Entry.sha256) {
        throw "Local release artifact mismatch: $([IO.Path]::GetFileName($Path))"
    }
}

function Get-LocalReleaseArtifact {
    param([string]$Path)
    $file = Get-Item -LiteralPath $Path
    return [ordered]@{ name=$file.Name; size=$file.Length; sha256=(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
}

function Copy-LocalReleaseRegression {
    param([string]$EvidencePath, [string]$Destination)
    $source = (Resolve-Path -LiteralPath $EvidencePath).Path
    $root = [IO.Path]::GetDirectoryName($source)
    $document = Get-Content -LiteralPath $source -Raw | ConvertFrom-Json -AsHashtable
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    foreach ($name in $document.files.Keys) {
        $inputFile = [IO.Path]::GetFullPath((Join-Path $root $name))
        $relative = [IO.Path]::GetRelativePath($root, $inputFile)
        if ($relative -eq '..' -or $relative.StartsWith('../') -or $relative.StartsWith('..\') -or [IO.Path]::IsPathRooted($relative)) { throw 'Unsafe regression snapshot path.' }
        if ((Get-FileHash -LiteralPath $inputFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $document.files[$name]) { throw 'Regression snapshot hash mismatch.' }
        $outputFile = Join-Path $Destination $relative
        New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($outputFile)) -Force | Out-Null
        Copy-Item -LiteralPath $inputFile -Destination $outputFile
    }
    if (@($document.checks | Where-Object name -EQ 'linux-tools').Count) {
        Copy-LocalReleaseRegression -EvidencePath (Join-Path $root 'linux/evidence.json') -Destination (Join-Path $Destination 'linux')
    }
    Copy-Item -LiteralPath $source -Destination (Join-Path $Destination 'evidence.json')
}

function Assert-LocalReleaseMain {
    param([object]$Client, [string]$Project, [string]$Commit)
    $head = & git -C $Project rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $head -cne $Commit) { throw 'Release checkout must match the verified commit.' }
    $dirty = & git -C $Project status --porcelain --untracked-files=normal
    if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Release checkout must be clean.' }
    $main = Invoke-GitHubRest -Client $Client -Method Get -Path 'git/ref/heads/main'
    if ($main.object.type -cne 'commit' -or $main.object.sha -cne $Commit) { throw 'Release commit must be current remote main.' }
}

function Reset-LocalReleaseApp {
    param([string]$Adb, [ValidatePattern('^emulator-\d+$')][string]$Serial)
    # pm path uses a nonzero exit for an absent package on some API levels.
    # A successful list operation distinguishes a clean emulator from a broken device.
    $installed = @(& $Adb -s $Serial shell pm list packages ru.itoltec.swypetris)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect installed Swypetris.' }
    if ($installed -ccontains 'package:ru.itoltec.swypetris') {
        $removed = @(& $Adb -s $Serial uninstall ru.itoltec.swypetris)
        if ($LASTEXITCODE -ne 0 -or $removed -cnotcontains 'Success') { throw 'Cannot reset Swypetris on release emulator.' }
    }
}

function Invoke-LocalReleasePublication {
    [CmdletBinding(SupportsShouldProcess)]
    param([object]$Client, [string]$Project, [object]$Proof, [string]$Directory, [string]$Notes)
$tag = 'v' + $proof.version
# Only 404 means absent. Authorization, network and service errors stop publication.
foreach ($path in @("git/ref/tags/$tag", "releases/tags/$tag")) {
    $found = $null
    try { $found = Invoke-GitHubRest -Client $client -Method Get -Path $path }
    catch { if ([int]$_.Exception.Response.StatusCode -ne 404) { throw } }
    if ($found) { throw "Refusing to overwrite existing release or tag: $tag" }
}
if (!$PSCmdlet.ShouldProcess("RoyMatus/Swypetris $tag at $($proof.commit)", 'Create protected tag, upload draft assets, publish stable release and verify public files')) { return }
Assert-LocalReleaseMain $client $project $proof.commit
$null = Invoke-GitHubRest -Client $client -Method Post -Path 'git/refs' -Body @{ ref="refs/tags/$tag"; sha=$proof.commit }
$release = Invoke-GitHubRest -Client $client -Method Post -Path 'releases' -Body @{
    tag_name=$tag; target_commitish=$proof.commit; name="Swypetris $($proof.version)"; body=$notes; draft=$true; prerelease=$false
}
if ($release.id -le 0 -or !$release.draft -or $release.tag_name -cne $tag) { throw 'Draft release creation returned unexpected identity.' }
# Fixed upload host and repository; never trust a response-provided upload URL with credentials.
foreach ($name in @('Swypetris.apk','update.json','SHA256SUMS.txt','local-release-provenance.json')) {
    $file = Join-Path $directory $name
    $asset = Invoke-RestMethod -Method Post -Uri "https://uploads.github.com/repos/RoyMatus/Swypetris/releases/$($release.id)/assets?name=$name" -Headers $client.Headers -ContentType 'application/octet-stream' -InFile $file -MaximumRedirection 0
    $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($asset.name -cne $name -or $asset.size -ne (Get-Item -LiteralPath $file).Length -or $asset.digest -cne "sha256:$hash") {
        throw 'Uploaded draft asset mismatch; draft and tag retained for diagnosis, never overwritten automatically.'
    }
}
Assert-LocalReleaseMain $client $project $proof.commit
$published = Invoke-GitHubRest -Client $client -Method Patch -Path "releases/$($release.id)" -Body @{ draft=$false }
if ($published.draft -or $published.tag_name -cne $tag) { throw 'Publication result mismatch.' }

return $published
}
