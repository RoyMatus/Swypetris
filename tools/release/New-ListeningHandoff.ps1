[CmdletBinding()]
param([Parameter(Mandatory)][string]$ProvenancePath,[Parameter(Mandatory)][string]$OutputPath)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $OutputPath){throw 'Listening handoff already exists.'}
$proof=Get-Content -LiteralPath $ProvenancePath -Raw|ConvertFrom-Json
. (Join-Path $PSScriptRoot 'Local-Release.ps1')
Assert-LocalReleaseProvenance $proof $proof.commit $proof.version
$apk=@($proof.artifacts|Where-Object name -CEQ 'Swypetris.apk')
@{
    schema=1;version=$proof.version;commit=$proof.commit;apkSha256=$apk[0].sha256
    provenanceSha256=(Get-FileHash -LiteralPath $ProvenancePath).Hash.ToLowerInvariant()
    accepted=$false;listener=$null;device=$null;outputRoute=$null;fixedVolume=$null;observedUtc=$null
    procedure='On the exact installed candidate, use a comfortable fixed volume. Check all 12 themes: effects alone, effects with music, drop/clear contrast, clipping/harshness and recognizability. Record speaker/headphone route and actual result; unperformed combinations stay unknown. Automated amplitude checks do not establish perceived quality.'
    observations=@();limitations='No listening performed by generating this file. An explicit global owner acceptance may be recorded as such, without inventing per-case results.'
}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $OutputPath
