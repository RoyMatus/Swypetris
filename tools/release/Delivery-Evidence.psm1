function Resolve-DeliveryChild {
    param([string]$Root,[string]$Relative)
    if(!$Relative -or [IO.Path]::IsPathRooted($Relative) -or $Relative.Contains(':')){throw 'Unsafe evidence path.'}
    $rootPath=[IO.Path]::GetFullPath($Root).TrimEnd('\','/')
    $path=[IO.Path]::GetFullPath((Join-Path $rootPath $Relative))
    if(!$path.StartsWith($rootPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe evidence path escape.'}
    $cursor=$path
    while($cursor.Length -gt $rootPath.Length){
        if(([IO.File]::Exists($cursor) -or [IO.Directory]::Exists($cursor)) -and ([IO.File]::GetAttributes($cursor) -band [IO.FileAttributes]::ReparsePoint)){throw 'Evidence path contains a link.'}
        $cursor=Split-Path -Parent $cursor
    }
    $path
}
function Save-DeliveryEvidence {
    param([string]$EvidencePath,[string]$Destination,[string[]]$ArtifactPaths)
    if(Test-Path -LiteralPath $Destination){throw 'Evidence archive already exists.'}
    $source=(Resolve-Path -LiteralPath $EvidencePath).Path
    $document=Get-Content -LiteralPath $source -Raw|ConvertFrom-Json -AsHashtable
    if(!$document.files -or $document.files.Count -eq 0){throw 'Missing bound evidence files.'}
    $inputs=@{}
    foreach($relative in $document.files.Keys){
        $path=Resolve-DeliveryChild (Split-Path -Parent $source) $relative
        if((Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -cne $document.files[$relative]){throw 'Evidence source hash mismatch.'}
        $inputs["reports/$relative"]=$path
    }
    $inputs['reports/evidence.json']=$source
    foreach($path in $ArtifactPaths){
        $item=Get-Item -LiteralPath $path
        $key='artifacts/'+$item.Name
        if($inputs.ContainsKey($key)){throw 'Duplicate artifact name.'};$inputs[$key]=$item.FullName
    }
    $null=New-Item -ItemType Directory -Path $Destination
    $manifest=[ordered]@{schema=1;createdUtc=[datetimeoffset]::UtcNow.ToString('o');files=@{}}
    foreach($relative in $inputs.Keys){
        $target=Resolve-DeliveryChild $Destination $relative
        $null=New-Item -ItemType Directory -Force (Split-Path -Parent $target)
        $before=(Get-FileHash -LiteralPath $inputs[$relative]).Hash.ToLowerInvariant()
        Copy-Item -LiteralPath $inputs[$relative] -Destination $target
        if((Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant() -cne $before){throw 'Evidence copy hash mismatch.'}
        $manifest.files[$relative]=$before
    }
    $manifest|ConvertTo-Json -Depth 20|Set-Content (Join-Path $Destination 'archive.json')
    Test-DeliveryEvidence $Destination
}
function Test-DeliveryEvidence {
    param([string]$Directory)
    $manifest=Get-Content -LiteralPath (Join-Path $Directory 'archive.json') -Raw|ConvertFrom-Json -AsHashtable
    if($manifest.schema -ne 1 -or !$manifest.files -or !$manifest.files.ContainsKey('reports/evidence.json')){throw 'Incomplete evidence archive.'}
    foreach($relative in $manifest.files.Keys){
        $path=Resolve-DeliveryChild $Directory $relative
        if((Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -cne $manifest.files[$relative]){throw 'Archive hash mismatch.'}
    }
    $evidence=Get-Content -LiteralPath (Join-Path $Directory 'reports/evidence.json') -Raw|ConvertFrom-Json -AsHashtable
    if($evidence.kind -cne 'worktree-retention'){
        foreach($relative in $evidence.files.Keys){
            if($manifest.files["reports/$relative"] -cne $evidence.files[$relative]){throw 'Archived evidence input hash differs from original binding.'}
        }
    }
    [pscustomobject]@{verified=$true;files=$manifest.files.Count;manifestSha256=(Get-FileHash -LiteralPath (Join-Path $Directory 'archive.json')).Hash.ToLowerInvariant()}
}
function Get-DeliveryCleanupDecision {
    param([Collections.IDictionary]$State)
    $reasons=@(foreach($flag in @('openPr','dirty','unmerged','active','artifactsUnarchived')){
        if(!$State.Contains($flag) -or $State[$flag] -ne $false){$flag}
    })
    if($State.merged -ne $true){$reasons+='mergeNotConfirmed'}
    [pscustomobject]@{remove=($reasons.Count -eq 0);reasons=$reasons}
}
function Remove-DeliveryWorktree {
    param([string]$Project,[string]$Path)
    # Per-command Windows support; never change global configuration or use force.
    & git -c core.longpaths=true -C $Project worktree remove -- $Path
    if($LASTEXITCODE){throw 'Git refused worktree removal; preserve and inspect partial state.'}
}
function Test-DeliveryPhaseProfiles {
    param([object[]]$Profiles,[ValidatePattern('^[a-f0-9]{32}$')][string]$RunId)
    if($Profiles.Count -ne 2){throw 'Expected exactly two phase profiles.'}
    $names=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($phaseProfile in $Profiles){
        if($phaseProfile.schema -ne 1 -or $phaseProfile.runId -cne $RunId -or
            $phaseProfile.test -cnotin @('next-preview-matrix','theme-native-pool') -or !$names.Add($phaseProfile.test)){throw 'Phase profile identity mismatch.'}
        if($phaseProfile.test -ceq 'next-preview-matrix'){
            if($phaseProfile.phases.capture.count -ne 980 -or $phaseProfile.phases.compare.count -ne 784 -or $phaseProfile.phases.update.count -ne 980){throw 'Preview matrix coverage changed.'}
        } else {
            foreach($phase in @('select-load','load-wait','play-assertions')){
                if($phaseProfile.phases.$phase.count -ne 12){throw 'Native theme coverage changed.'}
            }
        }
    }
    [pscustomobject]@{verified=$true;runId=$RunId;captures=980;comparisons=784;nativeThemes=12}
}
function Save-WorktreeEvidence {
    param([string]$Path,[string]$Destination)
    $root=(Resolve-Path -LiteralPath $Path).Path
    if(Test-Path -LiteralPath $Destination){throw 'Evidence archive already exists.'}
    if([IO.Path]::GetFullPath($Destination).StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Archive must be outside the worktree.'}
    $head=(& git -C $root rev-parse HEAD).Trim();if($LASTEXITCODE){throw 'Cannot resolve worktree HEAD.'}
    $inputs=@(Get-ChildItem -LiteralPath $root -Force -File -Recurse|Where-Object {
        $_.FullName -match '[/\\](verification|dist)[/\\]' -or $_.Extension -in @('.apk','.aab')
    })
    $null=New-Item -ItemType Directory $Destination
    $files=@{}
    foreach($file in $inputs){
        if($file.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Protected worktree file is a link; inspect before archival.'}
        $relative='reports/'+[IO.Path]::GetRelativePath($root,$file.FullName).Replace('\','/')
        $target=Resolve-DeliveryChild $Destination $relative
        $null=New-Item -ItemType Directory -Force (Split-Path -Parent $target)
        $before=(Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()
        Copy-Item -LiteralPath $file.FullName -Destination $target
        if((Get-FileHash -LiteralPath $target).Hash.ToLowerInvariant() -cne $before){throw 'Worktree archive hash mismatch.'}
        $files[$relative]=$before
    }
    $ref='refs/evidence/worktrees/'+$head
    & git -C $root update-ref $ref $head;if($LASTEXITCODE){throw 'Cannot retain source reference.'}
    $bundle=Join-Path $Destination 'source.bundle'
    & git -C $root bundle create $bundle $ref;if($LASTEXITCODE){throw 'Cannot bundle original source history.'}
    & git -C $root bundle verify $bundle;if($LASTEXITCODE){throw 'Source bundle verification failed.'}
    $files['source.bundle']=(Get-FileHash -LiteralPath $bundle).Hash.ToLowerInvariant()
    $null=New-Item -ItemType Directory -Force (Join-Path $Destination 'reports')
    @{kind='worktree-retention';head=$head;sourceRef=$ref;files=$files.Clone()}|ConvertTo-Json -Depth 15|Set-Content (Join-Path $Destination 'reports/evidence.json')
    $files['reports/evidence.json']=(Get-FileHash -LiteralPath (Join-Path $Destination 'reports/evidence.json')).Hash.ToLowerInvariant()
    @{schema=1;kind='worktree-retention';head=$head;files=$files}|ConvertTo-Json -Depth 15|Set-Content (Join-Path $Destination 'archive.json')
    Test-DeliveryEvidence $Destination
}
Export-ModuleMember -Function Save-DeliveryEvidence,Test-DeliveryEvidence,Get-DeliveryCleanupDecision,Save-WorktreeEvidence,Remove-DeliveryWorktree,Test-DeliveryPhaseProfiles
