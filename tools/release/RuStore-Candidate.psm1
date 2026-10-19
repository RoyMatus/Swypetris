#Requires -Version 7.4
# Candidate/media contracts retained from the reviewed epic308 release procedure.
Set-StrictMode -Version 3
. (Join-Path $PSScriptRoot 'Local-Release.ps1')
$script:Package = 'ru.itoltec.swypetris'
$script:CanonicalIconSha = '2e1c7d64feb61a109bbf9c5156202c2f29cb7d7b0c0609271365c5b8e7fe3c16'
function Get-LocalSha([string]$Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
}
function Read-SubmissionJson([string]$Path) {
    if ((Get-Item -LiteralPath $Path -ErrorAction Stop).Length -gt 2MB) { throw 'Receipt exceeds bounded size.' }
    Get-Content -LiteralPath $Path -Raw -ErrorAction Stop | ConvertFrom-Json -AsHashtable -ErrorAction Stop
}
function Get-ValueDigest($Value) {
    $bytes = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Value -Depth 30 -Compress))
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}
function Assert-BoundFile($File) {
    if (!$File.path -or $File.sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-LocalSha $File.path) -cne $File.sha256) {
        throw 'Missing or changed hash-bound local file.'
    }
}
function Get-PngEvidence([string]$Path, [switch]$Icon) {
    $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    $limit = 3MB
    if ($item.Length -le 0 -or $item.Length -gt $limit) { throw 'Image exceeds allowed size.' }
    $bytes = [IO.File]::ReadAllBytes($item.FullName)
    if ([Convert]::ToHexString($bytes[0..7]) -cne '89504E470D0A1A0A') { throw 'Expected actual PNG bytes.' }
    Add-Type -AssemblyName System.Drawing
    $bitmap = [Drawing.Bitmap]::new($item.FullName)
    try {
        $width = $bitmap.Width; $height = $bitmap.Height
        if ($Icon) {
            if ($width -ne 512 -or $height -ne 512) { throw 'Icon must be exactly 512 by 512.' }
            # A fixed bounded image; canonical RGB hash ignores encoder metadata.
            $rgb = [byte[]]::new(512 * 512 * 3); $index = 0
            for ($y = 0; $y -lt 512; $y++) {
                for ($x = 0; $x -lt 512; $x++) {
                    $color = $bitmap.GetPixel($x, $y)
                    if ($color.A -ne 255) { throw 'Icon must be opaque.' }
                    $rgb[$index++] = $color.R; $rgb[$index++] = $color.G; $rgb[$index++] = $color.B
                }
            }
            $rgbSha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($rgb)).ToLowerInvariant()
        } else {
            if ($width -lt 320 -or $height -lt 320 -or $width -gt 2160 -or $height -gt 3840 -or $width * 16 -ne $height * 9) {
                throw 'Mobile screenshot must be portrait 9:16, width320..2160, height320..3840, and at most3MB.'
            }
            $rgbSha = $null
        }
        @{ sha256 = Get-LocalSha $Path; rgbSha256 = $rgbSha; width = $width; height = $height; bytes = $item.Length }
    } finally { $bitmap.Dispose() }
}
function Assert-SubmissionPlan($Plan, $Receipt) {
    if ($Plan.package -cne $script:Package -or $Plan.commit -cnotmatch '^[a-f0-9]{40}$' -or
        $Plan.versionCode -lt 1 -or $Plan.versionName -cnotmatch '^\d+\.\d+\.\d+$' -or !$Plan.whatsNew -or $Plan.whatsNew.Length -gt 400) {
        throw 'Invalid exact-candidate plan or concise release notes.'
    }
    if (!$Plan.fullDescription -or $Plan.fullDescription.Length -gt 4000 -or
        !$Plan.fullDescription.Contains('https://raw.githubusercontent.com/RoyMatus/Swypetris/v' + $Plan.versionName + '/app/src/main/assets/privacy.txt')) {
        throw 'Reviewed listing must include the exact released public privacy policy.'
    }
    Assert-BoundFile $Plan.apk; Assert-BoundFile $Plan.icon
    if ((Get-Item -LiteralPath $Plan.apk.path).Length -gt 5GB -or $Plan.icon.sha256 -cne $script:CanonicalIconSha) {
        throw 'APK limit or canonical project icon mismatch.'
    }
    $icon = Get-PngEvidence $Plan.icon.path -Icon
    foreach ($name in @('exactMainSignedGate', 'artifactCompliance', 'installedCandidate', 'installedIcon', 'screenshots', 'manifestAndSdkData', 'privacyPolicy')) {
        if (!$Receipt.evidence.ContainsKey($name)) { throw "Missing required evidence: $name." }
        Assert-BoundFile $Receipt.evidence[$name]
    }
    $gate = Read-SubmissionJson $Receipt.evidence.exactMainSignedGate.path
    Assert-LocalReleaseProvenance -Proof $gate -Commit $Plan.commit -Version $Plan.versionName
    $gateApk = @($gate.artifacts | Where-Object { $_.name -ceq 'Swypetris.apk' -and $_.sha256 -ceq $Plan.apk.sha256 -and $_.size -eq (Get-Item -LiteralPath $Plan.apk.path).Length })
    if ($gate.kind -cne 'swypetris-local-release' -or $gate.repository -cne 'RoyMatus/Swypetris' -or $gate.result -cne 'passed' -or
        $gate.commit -cne $Plan.commit -or $gate.version -cne $Plan.versionName -or $gate.smoke.result -cne 'passed' -or $gateApk.Count -ne 1) {
        throw 'Actual exact-main signed provenance does not pass or match candidate.'
    }
    if ($Receipt.commit -cne $Plan.commit -or $Receipt.apkSha256 -cne $Plan.apk.sha256 -or
        $Receipt.iconRgbSha256 -cne $icon.rgbSha256 -or $Receipt.versionCode -ne $Plan.versionCode -or
        $Receipt.versionName -cne $Plan.versionName) { throw 'Receipt is bound to a different candidate.' }
    foreach ($flag in @('gatePassed','artifactCompliancePassed','installedPassed','installedIconPassed','screenshotsReviewed','manifestAndSdkDataReviewed','privacyPolicyVerified')) {
        if ($Receipt[$flag] -ne $true) { throw "Final review is incomplete: $flag." }
    }
    if (@($Plan.screenshots).Count -ne 3) { throw 'Replace exactly the three inherited mobile portrait screenshots.' }
    foreach ($shot in $Plan.screenshots) { Assert-BoundFile $shot; $null = Get-PngEvidence $shot.path }
    # No permissions request field is invented. Those classifications are server READ data.
    $allowed = @('privacyExplanation','privacyDataTypes','packageSdkDto','inAppPurchaseTypes')
    foreach ($key in $Plan.declarations.Keys) { if ($key -cnotin $allowed) { throw 'Unsupported declaration write field.' } }
    foreach ($key in $allowed) { if (!$Plan.declarations.ContainsKey($key)) { throw "Explicit reviewed declaration missing: $key." } }
    if (!$Plan.declarations.privacyExplanation -or $Plan.declarations.privacyExplanation.Length -gt 4000 -or
        @($Plan.declarations.privacyDataTypes).Count -lt 1 -or @($Plan.declarations.privacyDataTypes).Count -gt 50) { throw 'Invalid permission rationale or data type count; do not inherit empty SDK declarations.' }
    if ($Receipt.declarationsDigest -cne (Get-ValueDigest $Plan.declarations)) { throw 'Declaration values were not reviewed.' }
    foreach ($id in $Plan.declarations.privacyDataTypes) {
        if (!$Receipt.dataTypeProof.ContainsKey($id) -or $Receipt.dataTypeProof[$id].source -cnotmatch '^https://(?:(?:www|console|web-api|backapi)\.)?rustore\.ru/' -or
            !$Receipt.dataTypeProof[$id].mappingEvidence) { throw 'A data identifier lacks confirmed official mapping.' }
        Assert-BoundFile $Receipt.dataTypeProof[$id].mappingEvidence
    }
    foreach ($field in @('adsSdks','paymentSdks')) {
        if (!$Plan.declarations.packageSdkDto.ContainsKey($field) -or @($Plan.declarations.packageSdkDto[$field]).Count -gt 50) { throw 'Invalid SDK declaration.' }
    }
    foreach ($kind in $Plan.declarations.inAppPurchaseTypes) { if ($kind -cnotin @('PRODUCTS','SUBSCRIPTIONS')) { throw 'Unknown purchase enum.' } }
}
function Get-RemoteMediaEvidence([string]$Url, [string]$Destination, [switch]$Icon) {
    $uri = [uri]$Url
    if ($uri.Scheme -cne 'https' -or !($uri.Host -ceq 'rustore.ru' -or $uri.Host.EndsWith('.rustore.ru')) -or $uri.UserInfo -or (Test-Path -LiteralPath $Destination)) {
        throw 'Unsafe media host or existing readback destination.'
    }
    # Stream is byte bounded; never attach Public-Token to CDN requests.
    $handler = [Net.Http.HttpClientHandler]::new(); $handler.AllowAutoRedirect = $false
    $http = [Net.Http.HttpClient]::new($handler); $http.Timeout = [timespan]::FromSeconds(30)
    $response = $null; $inputStream = $null; $outputStream = $null
    try {
        $response = $http.GetAsync($uri, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
        if (!$response.IsSuccessStatusCode) { throw 'Media readback HTTP failure.' }
        $inputStream = $response.Content.ReadAsStream(); $outputStream = [IO.File]::Open($Destination, [IO.FileMode]::CreateNew)
        $buffer = [byte[]]::new(8192); $total = 0; $limit = 3MB
        $deadline = [datetimeoffset]::UtcNow.AddSeconds(30)
        while (($read = $inputStream.ReadAsync($buffer, 0, $buffer.Length).WaitAsync([timespan]::FromSeconds(30)).GetAwaiter().GetResult()) -gt 0) {
            $total += $read
            if ($total -gt $limit -or [datetimeoffset]::UtcNow -gt $deadline) { throw 'Media readback exceeded byte/time bound.' }
            $outputStream.Write($buffer, 0, $read)
        }
    } finally {
        if ($outputStream) { $outputStream.Dispose() }; if ($inputStream) { $inputStream.Dispose() }
        if ($response) { $response.Dispose() }; $http.Dispose(); $handler.Dispose()
    }
    Get-PngEvidence $Destination -Icon:$Icon
}
Export-ModuleMember -Function Assert-SubmissionPlan,Read-SubmissionJson,Get-PngEvidence,Get-RemoteMediaEvidence
