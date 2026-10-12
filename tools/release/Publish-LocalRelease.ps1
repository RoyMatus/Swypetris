<# Explicit publication only. -WhatIf performs read-only validation and no GitHub writes. #>
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$ProvenancePath,
    [Parameter(Mandatory)][string]$NotesFile,
    [Parameter(Mandatory)][string]$Python,
    [Parameter(Mandatory)][string]$JavaHome
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $project 'tools/GitHub-Api.psm1') -Force
Import-Module (Join-Path $project 'tools/LocalVerification.psm1') -Force
. (Join-Path $PSScriptRoot 'Local-Release.ps1')
. (Join-Path $PSScriptRoot 'Release-Verification.ps1')
$JavaHome = Resolve-ReleaseJavaHome $JavaHome
$proof = Get-Content -LiteralPath $ProvenancePath -Raw | ConvertFrom-Json
Assert-LocalReleaseProvenance $proof $proof.commit $proof.version
$directory = Split-Path -Parent (Resolve-Path -LiteralPath $ProvenancePath).Path
if ([IO.Path]::GetFileName($ProvenancePath) -cne 'local-release-provenance.json') { throw 'Use the original local release provenance filename.' }
$client = New-GitHubClient 'RoyMatus/Swypetris'
Assert-LocalReleaseMain $client $project $proof.commit
foreach ($entry in $proof.artifacts) { Assert-LocalReleaseArtifact $entry (Join-Path $directory $entry.name) }
foreach ($entry in $proof.smoke.files) { Assert-LocalReleaseArtifact $entry (Join-Path (Join-Path $directory 'smoke') $entry.name) }
$regressionPath = Join-Path $directory 'regression/evidence.json'
if ((Get-FileHash -LiteralPath $regressionPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $proof.regressionSha256) { throw 'Regression evidence hash mismatch.' }
$regression = Get-Content -LiteralPath $regressionPath -Raw | ConvertFrom-Json
$null = Assert-LocalVerificationEvidence -EvidencePath $regressionPath -Project $project -HeadSha $proof.commit -BaseSha $regression.base -RequireFull -Python $Python
$null = Assert-PublishedReleaseFiles -Directory $directory -Version $proof.version
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and (Test-Path (Join-Path $_.FullName 'aapt.exe')) } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (!$buildTools) { throw 'Android build-tools required.' }
foreach ($role in @('app','upload')) {
    $name = if ($role -eq 'app') { 'Swypetris.apk' } else { "Swypetris-$($proof.version).aab" }
    $digest = Assert-ReleaseSignature -Artifact (Join-Path $directory $name) -Role $role -Project $project -JavaHome $JavaHome -BuildTools $buildTools.FullName
    if ($digest -cne $proof.certificates.$role) { throw 'Signed artifact certificate differs from evidence.' }
}
$notes = Get-Content -LiteralPath $NotesFile -Raw
if ([string]::IsNullOrWhiteSpace($notes)) { throw 'Release notes must not be empty.' }
$tag = 'v' + $proof.version
$published = Invoke-LocalReleasePublication -Client $client -Project $project -Proof $proof -Directory $directory -Notes $notes -WhatIf:$WhatIfPreference
if (!$published) { return }
& (Join-Path $PSScriptRoot 'Verify-PublishedRelease.ps1') -Tag $tag -LocalProvenance -JavaHome $JavaHome
Write-Output "Published and verified: $($published.html_url)"
