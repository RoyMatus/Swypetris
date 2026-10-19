#Requires -Version 7.4
<# Explicit phases; neither importing modules nor Prepare performs network writes. #>
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][ValidateSet('Prepare','Inspect','Upload','UploadMedia','Verify','Submit','Publish')][string]$Action,
    [Parameter(Mandatory)][string]$PlanPath,[Parameter(Mandatory)][string]$ReceiptPath,
    [Parameter(Mandatory)][string]$StateDirectory,
    [long]$VersionId,[string]$ProviderPath,[string]$CredentialPath,
    [string]$ClearancePath,[string]$ReviewPath,[string]$StoreArtifactPath
)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'RuStore-Workflow.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'RuStore-Candidate.psm1') -Force
$plan=Read-SubmissionJson $PlanPath;$receipt=Read-SubmissionJson $ReceiptPath
Assert-SubmissionPlan $plan $receipt
$digest=(Get-FileHash -LiteralPath $PlanPath).Hash.ToLowerInvariant()
$candidate=@{versionName=$plan.versionName;versionCode=$plan.versionCode;apkSha256=$plan.apk.sha256}
if($Action -eq 'Prepare') {
    # Release provenance binds exact bytes; GitHub readback corroborates target identity.
    Import-Module (Join-Path $PSScriptRoot '../GitHub-Api.psm1') -Force
    $github=New-GitHubClient RoyMatus/Swypetris
    $release=Invoke-GitHubRest $github Get "releases/tags/v$($plan.versionName)"
    $tag=Invoke-GitHubRest $github Get "git/ref/tags/v$($plan.versionName)"
    $object=$tag.object
    for($depth=0;$object.type -ceq 'tag' -and $depth -lt 3;$depth++){
        if($object.sha -cnotmatch '^[a-f0-9]{40}$'){throw 'Invalid annotated tag object identity.'}
        $object=(Invoke-GitHubRest $github Get "git/tags/$($object.sha)").object
    }
    if($object.type -cne 'commit' -or $object.sha -cne $plan.commit){throw 'Published tag does not match exact signed source commit.'}
    $github=$null
    $asset=@($release.assets|Where-Object name -CEQ 'Swypetris.apk')
    if($release.draft -or $release.prerelease -or $release.tag_name -cne "v$($plan.versionName)" -or
        $asset.Count -ne 1 -or $asset[0].digest -cne "sha256:$($plan.apk.sha256)" -or
        $asset[0].size -ne (Get-Item -LiteralPath $plan.apk.path).Length) {throw 'Published GitHub candidate mismatch.'}
    $update=Invoke-RestMethod -Uri "https://github.com/RoyMatus/Swypetris/releases/download/v$($plan.versionName)/update.json" -TimeoutSec 30
    if($update.versionCode -ne $plan.versionCode -or $update.versionName -cne $plan.versionName -or $update.sha256 -cne $plan.apk.sha256){throw 'Published update identity differs from candidate.'}
    return [pscustomobject]@{prepared=$true;planSha256=$digest;candidate=$candidate;executed=$false}
}
if($VersionId -le 0 -or !$ProviderPath -or !$CredentialPath){throw 'Explicit owned version and private provider/credential paths are required.'}
if(!$WhatIfPreference){$null=New-Item -ItemType Directory -Path $StateDirectory -Force}
Import-Module $ProviderPath -ErrorAction Stop
$client=New-RuStoreClient -CredentialPath $CredentialPath
$provider=Get-Module | Where-Object Path -EQ (Resolve-Path -LiteralPath $ProviderPath).Path
if(@($provider).Count -ne 1){throw 'Ambiguous private provider module.'}

function Invoke-DeliveryWrite($Route,$Body=$null,$File='') {
    $root='/public/v1/application/ru.itoltec.swypetris/version/'+$VersionId
    if($Route -cnotmatch ('^'+[regex]::Escape($root)+'/(apk\?isMainApk=true&servicesType=Unknown|image/icon|image/screenshot/PORTRAIT/[0-2]|commit\?priorityUpdate=0|publish)$')){throw 'Unsupported write route.'}
    # Refresh through existing protected provider. Never change its read-only allowlist.
    $null=& $provider {param($c) Invoke-RuStoreRequest $c '/public/v1/application/ru.itoltec.swypetris/version?page=0&size=100&filterTestingType=ALL'} $client
    $token=[pscredential]::new('unused',$client.token).GetNetworkCredential().Password
    try {
        $http=@{Uri=('https://public-api.rustore.ru'+$Route);Method='POST';Headers=@{'Public-Token'=$token};ConnectionTimeoutSeconds=20;OperationTimeoutSeconds=300;MaximumRedirection=0;ErrorAction='Stop';Verbose=$false;Debug=$false}
        if($File){$http.Form=@{file=Get-Item -LiteralPath $File}}
        elseif($null -ne $Body){$http.Body=[Text.Encoding]::UTF8.GetBytes(($Body|ConvertTo-Json -Depth 30 -Compress));$http.ContentType='application/json; charset=utf-8'}
        try {$answer=Invoke-RestMethod @http} catch {
            $failure=@{httpStatus=0;code=$null;message=$null;versionId=$VersionId;route=$Route;capturedUtc=[datetimeoffset]::UtcNow.ToString('o')}
            if($_.Exception.PSObject.Properties['Response'] -and $_.Exception.Response){$failure.httpStatus=[int]$_.Exception.Response.StatusCode}
            $raw=$_.ErrorDetails.Message
            if($raw -and $raw.Length -le 32768 -and !$raw.Contains($token)){
                try {
                    $structured=$raw|ConvertFrom-Json -ErrorAction Stop
                    if($structured.code -is [string] -and $structured.code -cmatch '^[A-Za-z0-9_.-]{1,80}$' -and !$structured.code.Contains($token)){$failure.code=$structured.code}
                    if($structured.message -is [string] -and $structured.message.Length -le 2048 -and !$structured.message.Contains($token)){$failure.message=$structured.message}
                } catch { # Keep only the bounded HTTP category when the response is not safe structured data.
                }
            }
            Save-DeliveryFailure $failure
            throw 'RuStore write failed or timed out. Attempt retained; inspect server/console before any further write. Safe bounded error details are in the local journal.'
        }
        if(!$answer -or $answer.code -cne 'OK'){
            $failure=@{httpStatus=200;code=$null;message=$null;versionId=$VersionId;route=$Route;capturedUtc=[datetimeoffset]::UtcNow.ToString('o')}
            if($answer.code -is [string] -and $answer.code -cmatch '^[A-Za-z0-9_.-]{1,80}$' -and !$answer.code.Contains($token)){$failure.code=$answer.code}
            if($answer.message -is [string] -and $answer.message.Length -le 2048 -and !$answer.message.Contains($token)){$failure.message=$answer.message}
            Save-DeliveryFailure $failure
            throw 'RuStore did not acknowledge OK. Attempt retained; inspect server/console.'
        }
    } finally {$token=$null;$http=$null;$answer=$null}
}
function Save-DeliveryJson($Value,$Name){
    if(!$WhatIfPreference){$Value|ConvertTo-Json -Depth 40|Set-Content -LiteralPath (Join-Path $StateDirectory $Name)}
}
function Save-DeliveryFailure($Failure){
    Save-DeliveryJson $Failure ('transport-error-'+[guid]::NewGuid().ToString('N')+'.json')
    Save-DeliveryJson $Failure 'last-transport-error.json'
}
function Get-ReviewBinding($Detail,$Version) {
    Get-RuStoreDigest @{planSha256=$digest;versionId=$VersionId;versionName=$Version.versionName;versionCode=$Version.versionCode;audience=$Version.partialValue;mode=$Detail.publishType;packages=$Detail.packages;icon=$Detail.iconUrl;screenshots=$Detail.fileUids;description=$Detail.fullDescription;whatsNew=$Detail.whatsNew;permissions=$Detail.permissions;privacyDataTypes=$Detail.privacyDataTypes;privacyExplanation=$Detail.privacyExplanation}
}
try {
    $detail=Get-RuStoreDetail $client $VersionId;$version=Get-RuStoreVersion $client $VersionId
    Save-DeliveryJson $detail 'latest-detail.json';Save-DeliveryJson $version 'latest-version.json'
    $attempt=Join-Path $StateDirectory "${VersionId}-Upload-attempt.json"
    $ackPath=Join-Path $StateDirectory "$VersionId-Upload-result.json"
    $acknowledged=$false
    if((Test-Path -LiteralPath $attempt) -and (Test-Path -LiteralPath $ackPath)){
        $ack=Read-SubmissionJson $ackPath;$start=Read-SubmissionJson $attempt
        $acknowledged=$ack.acknowledged -eq $true -and $ack.planSha256 -ceq $digest -and $ack.versionId -eq $VersionId -and
            $start.planSha256 -ceq $digest -and $start.versionId -eq $VersionId -and $start.action -ceq 'Upload'
    }
    $clear=$false
    if($ClearancePath){
        $clearance=Read-SubmissionJson $ClearancePath
        $clear=$clearance.versionId -eq $VersionId -and $clearance.planSha256 -ceq $digest -and $clearance.emptyDraftObserved -eq $true
        if(!$clear){throw 'Console clearance must bind this plan and exact empty draft.'}
    }
    $reviewed=$false
    if($ReviewPath){
        $review=Read-SubmissionJson $ReviewPath
        $reviewed=$review.reviewed -eq $true -and $review.binding -ceq (Get-ReviewBinding $detail $version)
        if(!$reviewed){throw 'Review is missing or stale; run Verify and review actual declarations/media.'}
    }
    $decision=Get-RuStoreDeliveryDecision $candidate $version $detail -UploadAttempted:(Test-Path -LiteralPath $attempt) -DraftCleared:$clear -Reviewed:$reviewed -Acknowledged:$acknowledged
    if($Action -eq 'Inspect'){return $decision}
    $root='/public/v1/application/ru.itoltec.swypetris/version/'+$VersionId
    if($Action -eq 'Verify'){
        if(!$PSCmdlet.ShouldProcess("Swypetris version$VersionId",'Download and verify the exact Store package and media')){return}
        $proof=$null
        if(@($detail.packages).Count -eq 0){
            if($decision.action -notin @('Verify','Submit') -or !$acknowledged){throw 'Hidden packages without an acknowledged one-time upload: inspect console.'}
        } else {
            if(!$StoreArtifactPath){$StoreArtifactPath=Join-Path $StateDirectory ('package-'+[guid]::NewGuid().ToString('N')+[IO.Path]::GetExtension($detail.packages[0].apkUid))}
            $download=Receive-RuStoreArtifact -ApkUid $detail.packages[0].apkUid -ExpectedBytes $detail.packages[0].size -Destination $StoreArtifactPath
            Save-DeliveryJson $download 'artifact-download.json'
            $proof=Test-RuStoreArtifact $StoreArtifactPath $plan.apk.sha256 (Get-Item -LiteralPath $plan.apk.path).Length
            if($detail.packages[0].size -ne $proof.archiveBytes){throw 'Server size does not match downloaded artifact.'}
        }
        if($detail.fullDescription -cne $plan.fullDescription -or $detail.whatsNew -cne $plan.whatsNew){throw 'Listing differs from reviewed plan.'}
        $readback=Join-Path $StateDirectory ('media-'+[guid]::NewGuid().ToString('N'));$null=New-Item -ItemType Directory $readback
        $icon=Get-RemoteMediaEvidence $detail.iconUrl (Join-Path $readback 'icon.png') -Icon
        if($icon.rgbSha256 -cne (Get-PngEvidence $plan.icon.path -Icon).rgbSha256){throw 'Store icon mismatch.'}
        $shots=@($detail.fileUids|Where-Object type -CEQ 'SCREENSHOT')
        if($shots.Count -ne 3){throw 'Unexpected screenshot count.'}
        for($n=0;$n -lt 3;$n++){
            $shot=@($shots|Where-Object {$_.ordinal -eq $n -and $_.orientation -ceq 'PORTRAIT'})
            if($shot.Count -ne 1){throw 'Screenshot order mismatch.'}
            $image=Get-RemoteMediaEvidence $shot[0].fileUrl (Join-Path $readback "screen-$n.png")
            if($image.sha256 -cne $plan.screenshots[$n].sha256){throw 'Store screenshot mismatch.'}
        }
        Save-DeliveryJson @{binding=(Get-ReviewBinding $detail $version);reviewed=$false;artifact=$proof;planSha256=$digest;versionId=$VersionId;reviewInstructions='Review latest-detail.json permissions/data/rationale/SDKs and media, then set reviewed=true. Never alter the binding.'} 'review-candidate.json'
        return [pscustomobject]@{remoteArtifactVerified=($null -ne $proof);uploadAcknowledged=$acknowledged;next='Review exact declarations and media; use review-candidate.json as ReviewPath. Materialized APK verification remains required after moderation submission.';executed=$false}
    }
    if($Action -eq 'UploadMedia'){
        if($detail.versionStatus -cne 'DRAFT' -or !$acknowledged){throw 'Media upload requires an acknowledged owned APK upload and DRAFT; inspect APK outcome first.'}
        $files=@(@{route='image/icon';path=$plan.icon.path})
        for($n=0;$n -lt 3;$n++){$files+=@{route="image/screenshot/PORTRAIT/$n";path=$plan.screenshots[$n].path}}
        foreach($file in $files){
            $name=$file.route.Replace('/','-')
            if(!$PSCmdlet.ShouldProcess("$VersionId $name",'Upload exact reviewed media once')){continue}
            New-RuStoreAttempt -Path (Join-Path $StateDirectory "$VersionId-$name-attempt.json") -Action Media -CandidateDigest $digest -VersionId $VersionId
            Invoke-DeliveryWrite "$root/$($file.route)" -File $file.path
        }
        return
    }
    if($decision.action -cne $Action){throw "Requested $Action is not allowed. Next: $($decision.action); actual status: $($decision.status)."}
    if(!$PSCmdlet.ShouldProcess("Swypetris $($plan.versionName)/$($plan.versionCode) version$VersionId",$Action)){return}
    New-RuStoreAttempt -Path (Join-Path $StateDirectory "$VersionId-$Action-attempt.json") -Action $Action -CandidateDigest $digest -VersionId $VersionId
    $timer=[Diagnostics.Stopwatch]::StartNew()
    try {
        switch($Action){
            'Upload' {Invoke-DeliveryWrite "$root/apk?isMainApk=true&servicesType=Unknown" -File $plan.apk.path}
            'Submit' {Invoke-DeliveryWrite "$root/commit?priorityUpdate=0"}
            'Publish' {Invoke-DeliveryWrite "$root/publish"}
        }
        Save-DeliveryJson @{action=$Action;acknowledged=$true;seconds=$timer.Elapsed.TotalSeconds;planSha256=$digest;versionId=$VersionId} "$VersionId-$Action-result.json"
    } catch {
        Save-DeliveryJson @{action=$Action;acknowledged=$false;seconds=$timer.Elapsed.TotalSeconds;planSha256=$digest;versionId=$VersionId;next='Inspect server/console; do not repeat write'} "$VersionId-$Action-result.json"
        throw
    }
    Save-DeliveryJson (Get-RuStoreDetail $client $VersionId) 'latest-detail.json'
    [pscustomobject]@{action=$Action;acknowledged=$true;publicationVerified=$false;next='Inspect actual status; VerifyPublic is a separate acceptance gate'}
} finally {$client=$null}
