function Get-RuStoreDeliveryDecision {
    param($Candidate,$Version,$Detail,[switch]$UploadAttempted,[switch]$DraftCleared,[switch]$Reviewed,[switch]$Acknowledged)
    if ($Version.versionId -ne $Detail.versionId -or $Detail.versionId -le 0 -or $Detail.publishType -cne 'MANUAL') { throw 'Unexpected version identity or publication mode.' }
    if ($Version.partialValue -ne 100) { throw 'Expected reviewed 100 percent audience.' }
    if (($Version.versionCode -gt 0 -and $Version.versionCode -ne $Candidate.versionCode) -or
        ($Version.versionName -and $Version.versionName -cne $Candidate.versionName)) { throw 'Store version conflicts with candidate version.' }
    $packages=@($Detail.packages)
    if ($packages.Count -gt 1 -or ($packages.Count -eq 1 -and !$packages[0].isMainApk)) { throw 'Expected exactly one main APK.' }
    if ($packages.Count -eq 1 -and ($packages[0].verCode -ne $Candidate.versionCode -or $packages[0].verName -cne $Candidate.versionName)) { throw 'Main APK version mismatch.' }
    $action='Wait'
    if ($Version.versionStatus -ceq $Detail.versionStatus) {
        switch -CaseSensitive ($Detail.versionStatus) {
            'DRAFT' {
                if ($packages.Count -eq 0) {
                    $action=if ($UploadAttempted -and $Acknowledged -and $Version.versionCode -eq $Candidate.versionCode -and $Version.versionName -ceq $Candidate.versionName) {
                        if($Reviewed){'Submit'}else{'Verify'}
                    } elseif (!$UploadAttempted -and $DraftCleared) {'Upload'} else {'InspectConsole'}
                } else { $action=if ($Reviewed) {'Submit'} else {'Verify'} }
            }
            'READY_FOR_PUBLICATION' { $action=if ($Reviewed) {'Publish'} else {'Verify'} }
            'ACTIVE' { $action='VerifyPublic' }
            'REJECTED_BY_MODERATOR' { $action='InspectRejection' }
        }
    }
    [pscustomobject]@{action=$action;status=$Detail.versionStatus;versionId=$Detail.versionId}
}

function New-RuStoreAttempt {
    param([string]$Path,[ValidateSet('Upload','Submit','Publish','Media')][string]$Action,
        [ValidatePattern('^[a-f0-9]{64}$')][string]$CandidateDigest,[long]$VersionId)
    $record=@{schema=1;action=$Action;planSha256=$CandidateDigest;versionId=$VersionId;startedUtc=[datetimeoffset]::UtcNow.ToString('o')}
    # CreateNew provides cross-process exclusion. Never remove an uncertain attempt.
    $stream=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try { $bytes=[Text.Encoding]::UTF8.GetBytes(($record|ConvertTo-Json -Compress));$stream.Write($bytes);$stream.Flush($true) } finally {$stream.Dispose()}
}

function Test-RuStoreArtifact {
    param([string]$Path,[ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedSha256,
        [ValidateRange(1,5368709120)][long]$ExpectedBytes)
    $archive=$null;$stream=$null
    try {
        if ([IO.Path]::GetExtension($Path) -ceq '.zip') {
            $archive=[IO.Compression.ZipFile]::OpenRead($Path)
            if ($archive.Entries.Count -gt 10) { throw 'Unexpected ZIP inventory.' }
            if(@($archive.Entries.FullName|Select-Object -Unique).Count -ne $archive.Entries.Count){throw 'Duplicate ZIP entries.'}
            foreach($entry in $archive.Entries) {
                if ($entry.FullName -cnotmatch '^[A-Za-z0-9_-]+\.apk$|^assets/dexopt/baseline\.(prof|profm)$') { throw 'Unsafe or unexpected ZIP entry.' }
                if (!$entry.FullName.EndsWith('.apk') -and $entry.Length -gt 1MB) { throw 'Oversized dexopt sidecar.' }
            }
            $apks=@($archive.Entries|Where-Object {$_.FullName.EndsWith('.apk')})
            if ($apks.Count -ne 1 -or $apks[0].Length -ne $ExpectedBytes) { throw 'Ambiguous APK or size identity mismatch.' }
            $stream=$apks[0].Open()
        } else {
            if ((Get-Item -LiteralPath $Path).Length -ne $ExpectedBytes) { throw 'APK size identity mismatch.' }
            $stream=[IO.File]::OpenRead($Path)
        }
        $hash=[Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA256)
        try {
            $buffer=[byte[]]::new(65536);$total=0L
            while(($n=$stream.Read($buffer,0,$buffer.Length)) -gt 0) {
                $total+=$n;if($total -gt $ExpectedBytes){throw 'APK size identity mismatch.'};$hash.AppendData($buffer,0,$n)
            }
            $digest=[Convert]::ToHexString($hash.GetHashAndReset()).ToLowerInvariant()
            if($total -ne $ExpectedBytes -or $digest -cne $ExpectedSha256){throw 'APK byte identity mismatch.'}
            [pscustomobject]@{apkSha256=$digest;apkBytes=$total;archiveBytes=(Get-Item -LiteralPath $Path).Length;archiveSha256=(Get-FileHash -LiteralPath $Path).Hash.ToLowerInvariant()}
        } finally {$hash.Dispose()}
    } finally {if($stream){$stream.Dispose()};if($archive){$archive.Dispose()}}
}

function ConvertTo-RuStoreCanonical {
    param($Value)
    if ($Value -is [Collections.IDictionary]) {
        $result=[ordered]@{}
        foreach($key in @($Value.Keys|Sort-Object -CaseSensitive)) {$result[$key]=ConvertTo-RuStoreCanonical $Value[$key]}
        return $result
    }
    if($Value -is [pscustomobject]){
        $result=[ordered]@{}
        foreach($p in @($Value.PSObject.Properties|Sort-Object Name -CaseSensitive)){$result[$p.Name]=ConvertTo-RuStoreCanonical $p.Value}
        return $result
    }
    if ($Value -is [Collections.IEnumerable] -and $Value -isnot [string]) {
        $result=@(foreach($item in $Value){ConvertTo-RuStoreCanonical $item});return ,$result
    }
    return $Value
}
function Get-RuStoreDigest {
    param($Value)
    $json=ConvertTo-Json -InputObject (ConvertTo-RuStoreCanonical $Value) -Depth 50 -Compress
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($json))).ToLowerInvariant()
}
function Receive-RuStoreArtifact {
    param([string]$ApkUid,[ValidateRange(1,5368709120)][long]$ExpectedBytes,[string]$Destination)
    $pathMatch=[regex]::Match($ApkUid,'^\d{4}/\d{2}/\d{2}/[a-f0-9]{2}/[a-f0-9-]{36}\.(zip|apk)$')
    if(!$pathMatch.Success){throw 'Unknown server artifact path; inspect the official contract.'}
    if([IO.Path]::GetExtension($Destination) -cne ('.'+$pathMatch.Groups[1].Value)){throw 'Destination extension must match server archive format.'}
    if(Test-Path -LiteralPath $Destination){throw 'Artifact download destination already exists.'}
    $handler=[Net.Http.HttpClientHandler]::new();$handler.AllowAutoRedirect=$false
    $http=[Net.Http.HttpClient]::new($handler);$http.Timeout=[timespan]::FromSeconds(120)
    $response=$null;$inputStream=$null;$output=$null;$timer=[Diagnostics.Stopwatch]::StartNew()
    try {
        # Fixed official CDN, anonymous request: never attach a publication token.
        $response=$http.GetAsync(('https://static.rustore.ru/'+$ApkUid),[Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        if(!$response.IsSuccessStatusCode){throw 'Store artifact download HTTP failure.'}
        if($response.Content.Headers.ContentLength -and $response.Content.Headers.ContentLength -ne $ExpectedBytes){throw 'Store download length differs from server package.'}
        $inputStream=$response.Content.ReadAsStream();$output=[IO.File]::Open($Destination,[IO.FileMode]::CreateNew)
        $buffer=[byte[]]::new(65536);$total=0L
        while(($n=$inputStream.ReadAsync($buffer,0,$buffer.Length).WaitAsync([timespan]::FromSeconds(30)).GetAwaiter().GetResult()) -gt 0){
            $total+=$n;if($total -gt $ExpectedBytes -or $timer.Elapsed.TotalSeconds -gt 120){throw 'Store download exceeded byte/time bound.'}
            $output.Write($buffer,0,$n)
        }
        if($total -ne $ExpectedBytes){throw 'Incomplete Store artifact download.'}
    } finally {if($output){$output.Dispose()};if($inputStream){$inputStream.Dispose()};if($response){$response.Dispose()};$http.Dispose();$handler.Dispose()}
    [pscustomobject]@{apkUid=$ApkUid;bytes=$total;seconds=$timer.Elapsed.TotalSeconds;sha256=(Get-FileHash -LiteralPath $Destination).Hash.ToLowerInvariant()}
}
Export-ModuleMember -Function Get-RuStoreDeliveryDecision,New-RuStoreAttempt,Test-RuStoreArtifact,Get-RuStoreDigest,Receive-RuStoreArtifact
