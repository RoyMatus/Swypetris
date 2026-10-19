<# Preview by default. Explicit -Remove rechecks live state and retained files. #>
[CmdletBinding(SupportsShouldProcess)]
param([Parameter(Mandatory)][string]$Path,[string]$ArchiveDirectory,[switch]$ConfirmInactive,[switch]$Remove)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot '../GitHub-Api.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'Delivery-Evidence.psm1') -Force
$project=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$target=(Resolve-Path -LiteralPath $Path).Path
$common=(& git -C $project rev-parse --path-format=absolute --git-common-dir).Trim()
if($LASTEXITCODE){throw 'Cannot identify repository.'}
$inventory=@(& git -C $project worktree list --porcelain)
if($LASTEXITCODE -or $inventory -cnotcontains ('worktree '+$target.Replace('\','/'))){throw 'Target is not a registered worktree of this repository.'}
if($target -eq $project -or (Get-Location).Path.StartsWith($target,[StringComparison]::OrdinalIgnoreCase)){throw 'Cannot remove the current active worktree.'}
$head=(& git -C $target rev-parse HEAD).Trim();if($LASTEXITCODE){throw 'Cannot identify worktree HEAD.'}
$branch=(@(& git -C $target branch --show-current) -join '').Trim();if($LASTEXITCODE){throw 'Cannot identify branch.'}
$dirty=@(& git -C $target status --porcelain --untracked-files=all);if($LASTEXITCODE){throw 'Cannot inspect worktree changes.'}
$client=New-GitHubClient RoyMatus/Swypetris
$prs=@(Invoke-GitHubRestPaged $client 'pulls?state=all')
$related=@($prs|Where-Object {$_.head.sha -ceq $head -or ($branch -and $_.head.ref -ceq $branch)})
$open=@($related|Where-Object state -CEQ 'open').Count -gt 0
$main=Invoke-GitHubRest $client Get 'git/ref/heads/main'
& git -C $target merge-base --is-ancestor $head $main.object.sha
$ancestor=$LASTEXITCODE -eq 0
if($LASTEXITCODE -gt 1){throw 'Cannot inspect merge ancestry.'}
$merged=$ancestor -or @($related|Where-Object {$_.merged_at -and $_.head.sha -ceq $head -and $_.base.ref -ceq 'main'}).Count -gt 0
# Retain a source ref even after a squash merge; local provenance may still refer to it.
$sourceRef='refs/evidence/worktrees/'+$head
$archive=$null
if($ArchiveDirectory){$archive=Test-DeliveryEvidence $ArchiveDirectory}
$unarchived=$true
if($archive){
    $manifest=Get-Content -LiteralPath (Join-Path $ArchiveDirectory 'archive.json') -Raw|ConvertFrom-Json -AsHashtable
    if($manifest.kind -cne 'worktree-retention' -or $manifest.head -cne $head -or !$manifest.files.ContainsKey('source.bundle')){throw 'Archive does not retain this exact worktree source and evidence.'}
    & git -C $project bundle verify (Join-Path $ArchiveDirectory 'source.bundle')
    if($LASTEXITCODE){throw 'Source bundle verification failed.'}
    $hashes=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($hash in $manifest.files.Values){$null=$hashes.Add($hash)}
    $required=@(Get-ChildItem -LiteralPath $target -Force -File -Recurse | Where-Object {
        $_.FullName -match '[/\\](verification|dist)[/\\]' -or $_.Extension -in @('.apk','.aab')
    })
    $unarchived=@($required|Where-Object {!$hashes.Contains((Get-FileHash -LiteralPath $_.FullName).Hash.ToLowerInvariant())}).Count -gt 0
    # Unknown ignored local files may contain keys/configuration; never delete them silently.
    $ignored=@(& git -C $target ls-files --others --ignored --exclude-standard)
    if($LASTEXITCODE){throw 'Cannot inspect ignored files.'}
    if(@($ignored|Where-Object {$_ -cnotmatch '^(?:\.gradle/|app/build/|build/|dist/|local\.properties$|\.kotlin/|tools/(?:ci|release)/__pycache__/[^/]+\.pyc$)'}).Count){$unarchived=$true}
    $localProperties=Join-Path $target 'local.properties'
    if(Test-Path -LiteralPath $localProperties){
        if(@(Get-Content -LiteralPath $localProperties|Where-Object {$_ -notmatch '^\s*(?:#.*|sdk\.dir\s*=.*)?$'}).Count){$unarchived=$true}
    }
}
$decision=Get-DeliveryCleanupDecision @{merged=$merged;unmerged=(!$merged);openPr=$open;dirty=($dirty.Count -gt 0);active=(!$ConfirmInactive);artifactsUnarchived=$unarchived}
[pscustomobject]@{path=$target;head=$head;branch=$branch;remove=$decision.remove;reasons=$decision.reasons;retainedSourceRef=$sourceRef;archive=$archive}
if(!$Remove){return}
if(!$decision.remove){throw 'Worktree is not eligible; no removal performed.'}
if($PSCmdlet.ShouldProcess($target,'Retain source ref and remove verified merged unused worktree without force')){
    $null=Test-DeliveryEvidence $ArchiveDirectory
    foreach($file in $required){
        if(!$hashes.Contains((Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant())){throw 'Artifacts changed after cleanup preview.'}
    }
    & git -C $project update-ref $sourceRef $head;if($LASTEXITCODE){throw 'Cannot retain original source commit.'}
    # git refuses concurrent changes/untracked work. Do not use --force or delete recursively.
    Remove-DeliveryWorktree -Project $project -Path $target
    if(Test-Path -LiteralPath $target){throw 'Removal did not complete.'}
    [pscustomobject]@{removed=$true;path=$target;sourceRef=$sourceRef;archiveManifestSha256=$archive.manifestSha256}
    # Branch deletion is deliberately separate: Git squash ancestry is not proof of no remaining work.
}
