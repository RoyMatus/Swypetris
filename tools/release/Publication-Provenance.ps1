function Read-PublicationProvenanceArchive {
    param([byte[]]$Bytes, [object]$Artifact)
    if ($Bytes.Length -gt 1MB -or $Bytes.Length -ne $Artifact.size_in_bytes) { throw 'Provenance archive size mismatch.' }
    $digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
    if ($Artifact.digest -cne "sha256:$digest") { throw 'Provenance archive digest mismatch.' }
    $stream = [IO.MemoryStream]::new($Bytes, $false)
    $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read)
    try {
        if ($zip.Entries.Count -ne 1 -or $zip.Entries[0].FullName -cne 'publication-provenance.json' -or
            $zip.Entries[0].Length -gt 64KB) { throw 'Unexpected provenance archive entries.' }
        $reader = [IO.StreamReader]::new($zip.Entries[0].Open())
        try {
            $characters = [char[]]::new(65537)
            $count = $reader.ReadBlock($characters, 0, $characters.Length)
            if ($count -gt 65536) { throw 'Provenance JSON exceeded size bound.' }
            return ([string]::new($characters, 0, $count) | ConvertFrom-Json -Depth 10)
        } finally { $reader.Dispose() }
    } finally { $zip.Dispose(); $stream.Dispose() }
}

function Get-PublicationArtifactBytes {
    param([object]$Client, [long]$ArtifactId)
    # Only the fixed GitHub API request receives credentials. Redirects are
    # followed explicitly with fresh requests, HTTPS only, and no credentials.
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $http = [Net.Http.HttpClient]::new($handler)
    $deadline = [Threading.CancellationTokenSource]::new([timespan]::FromSeconds(90))
    try {
        $uri = [uri]"https://api.github.com/repos/RoyMatus/Swypetris/actions/artifacts/$ArtifactId/zip"
        for ($hop = 0; $hop -lt 6; $hop++) {
            $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, $uri)
            $request.Headers.UserAgent.ParseAdd('Swypetris-release-verifier')
            if ($hop -eq 0) {
                foreach ($header in $Client.Headers.GetEnumerator()) {
                    [void]$request.Headers.TryAddWithoutValidation($header.Key, [string]$header.Value)
                }
            }
            $response = $null
            try {
                $response = $http.SendAsync($request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead, $deadline.Token).GetAwaiter().GetResult()
                if ([int]$response.StatusCode -in @(301,302,303,307,308)) {
                    $location = $response.Headers.Location
                    if (!$location -or !$location.IsAbsoluteUri -or $location.Scheme -cne 'https' -or $location.UserInfo) { throw 'Invalid provenance download redirect.' }
                    $uri = $location
                    continue
                }
                if ([int]$response.StatusCode -ne 200 -or $response.Content.Headers.ContentLength -gt 1MB) { throw 'Provenance download failed or exceeded size bound.' }
                $inputStream = $response.Content.ReadAsStreamAsync($deadline.Token).GetAwaiter().GetResult()
                $output = [IO.MemoryStream]::new()
                try {
                    $buffer = [byte[]]::new(8192)
                    while (($count = $inputStream.ReadAsync($buffer, 0, $buffer.Length, $deadline.Token).GetAwaiter().GetResult()) -gt 0) {
                        if ($output.Length + $count -gt 1MB) { throw 'Provenance download exceeded size bound.' }
                        $output.Write($buffer, 0, $count)
                    }
                    return ,$output.ToArray()
                } finally { $inputStream.Dispose(); $output.Dispose() }
            } finally { if ($response) { $response.Dispose() }; $request.Dispose() }
        }
        throw 'Too many provenance download redirects.'
    } catch {
        # Avoid placing signed redirect URLs or request details in saved logs.
        throw 'Unable to download bounded publication provenance archive.'
    } finally { $deadline.Dispose(); $http.Dispose(); $handler.Dispose() }
}

function Get-PublicationProvenance {
    param([object]$Client, [object]$Run, [string]$Commit)
    $name = "publication-provenance-$($Run.id)-$($Run.run_attempt)"
    $found = @()
    $page = 1
    do {
        $result = Invoke-GitHubRest -Client $Client -Method Get -Path "actions/runs/$($Run.id)/artifacts?per_page=100&page=$page"
        $items = @($result.artifacts)
        $found += @($items | Where-Object name -CEQ $name)
        $page++
    } while ($items.Count -eq 100)
    if ($found.Count -ne 1) { throw 'Missing or duplicate publication provenance artifact.' }
    $artifact = $found[0]
    if ($artifact.expired -ne $false -or $artifact.size_in_bytes -le 0 -or $artifact.size_in_bytes -gt 1MB -or
        $artifact.id -le 0 -or $artifact.digest -cnotmatch '^sha256:[a-f0-9]{64}$' -or
        $artifact.workflow_run.id -ne $Run.id) { throw 'Invalid publication provenance artifact metadata.' }
    $bytes = Get-PublicationArtifactBytes -Client $Client -ArtifactId $artifact.id
    $proof = Read-PublicationProvenanceArchive -Bytes $bytes -Artifact $artifact
    # Validate identifiers before constructing API paths from artifact data.
    foreach ($field in @('source_run_id','source_run_attempt','source_artifact_id')) {
        if (($proof.$field -isnot [long] -and $proof.$field -isnot [int]) -or $proof.$field -le 0) { throw 'Invalid source provenance identifier.' }
    }
    $source = Invoke-GitHubRest -Client $Client -Method Get -Path "actions/runs/$($proof.source_run_id)/attempts/$($proof.source_run_attempt)"
    $workflow = Invoke-GitHubRest -Client $Client -Method Get -Path 'actions/workflows/ci.yml'
    $sourceArtifact = Invoke-GitHubRest -Client $Client -Method Get -Path "actions/artifacts/$($proof.source_artifact_id)"
    Assert-PublicationRun -Run $Run -Commit $Commit -Provenance $proof -SourceRun $source -SourceWorkflow $workflow -SourceArtifact $sourceArtifact
    return $proof
}
