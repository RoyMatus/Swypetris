function Get-ReleaseGradleOptions {
    param([switch]$NoDaemon)
    @('--console=plain', '--build-cache', $(if ($NoDaemon) { '--no-daemon' } else { '--daemon' }))
}

function Get-ReleaseCertificateDigest {
    param([string[]]$Output, [switch]$Apk)
    $pattern = if ($Apk) { '(?m)^(?:Signer #\d+|V[234] Signer:) certificate SHA-256 digest:\s*([a-fA-F0-9]{64})\s*$' } else { '(?m)^\s*SHA256:\s*([a-fA-F0-9:]+)\s*$' }
    $digests = [regex]::Matches(($Output -join "`n"), $pattern)
    if ($digests.Count -ne 1) { throw 'Expected exactly one signing certificate SHA-256 digest.' }
    $digest = $digests[0].Groups[1].Value.Replace(':', '').ToLowerInvariant()
    if ($digest -notmatch '^[a-f0-9]{64}$') { throw 'Invalid certificate SHA-256 digest.' }
    $digest
}

function Assert-ReleaseCertificate {
    param([string]$Actual, [string]$Expected)
    if ($Expected -notmatch '^[a-f0-9]{64}$' -or $Actual -cne $Expected) {
        throw 'Signed artifact certificate does not match the checked-in public certificate.'
    }
}

function Assert-ReleaseSignature {
    param([string]$Artifact, [string]$Role, [string]$Project, [string]$JavaHome, [string]$BuildTools)
    $keytool = Join-Path $JavaHome 'bin/keytool.exe'
    $public = @(& $keytool '-J-Duser.language=en' -printcert -file (Join-Path $Project "publishing/certificates/swypetris-$Role.pem") 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the checked-in public signing certificate.' }
    $expected = Get-ReleaseCertificateDigest -Output $public
    if ($Role -eq 'app') {
        $output = @(& (Join-Path $BuildTools 'apksigner.bat') verify --print-certs $Artifact 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
        $actual = Get-ReleaseCertificateDigest -Output $output -Apk
    } else {
        $output = @(& (Join-Path $JavaHome 'bin/jarsigner.exe') '-J-Duser.language=en' -verify $Artifact 2>&1)
        if ($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch '(?m)^jar verified\.\s*$') { throw 'AAB signature verification failed.' }
        $output = @(& $keytool '-J-Duser.language=en' -printcert -jarfile $Artifact 2>&1)
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read the AAB signing certificate.' }
        $actual = Get-ReleaseCertificateDigest -Output $output
    }
    Assert-ReleaseCertificate -Actual $actual -Expected $expected
    $actual
}

function Assert-PublishedReleaseFiles {
    param([string]$Directory, [string]$Version)
    $apk = Join-Path $Directory 'Swypetris.apk'
    $metadata = Get-Content -LiteralPath (Join-Path $Directory 'update.json') -Raw | ConvertFrom-Json
    $digest = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($metadata.versionName -cne $Version -or $metadata.sha256 -cne $digest -or
        $metadata.versionCode -isnot [long] -and $metadata.versionCode -isnot [int] -or $metadata.versionCode -le 0) {
        throw 'Public update metadata does not match the APK/version.'
    }
    $seen = @{}
    foreach ($line in (Get-Content -LiteralPath (Join-Path $Directory 'SHA256SUMS.txt'))) {
        if ($line -notmatch '^([a-f0-9]{64})  (Swypetris\.apk|update\.json)$') { throw 'Unexpected public checksum entry.' }
        $hash, $name = $Matches[1], $Matches[2]
        if ($seen.ContainsKey($name)) { throw 'Duplicate public checksum entry.' }
        $seen[$name] = $true
        if ((Get-FileHash -LiteralPath (Join-Path $Directory $name) -Algorithm SHA256).Hash.ToLowerInvariant() -cne $hash) { throw 'Public checksum mismatch.' }
    }
    if (!$seen.ContainsKey('Swypetris.apk') -or !$seen.ContainsKey('update.json')) { throw 'APK and update metadata checksums are required.' }
    [pscustomobject]@{ version=$Version; versionCode=$metadata.versionCode; sha256=$digest; bytes=(Get-Item -LiteralPath $apk).Length }
}

function Assert-PublicationRun {
    param([object]$Run, [string]$Commit, [object]$Provenance, [object]$SourceRun,
          [object]$SourceWorkflow, [object]$SourceArtifact)
    if ($Run.status -cne 'completed' -or $Run.conclusion -cne 'success' -or
        $Run.path -cne '.github/workflows/release-check.yml' -or $Run.head_branch -cne 'main') {
        throw 'Publication run must be a successful main release workflow.'
    }
    if ($Run.event -ceq 'push') {
        if ($Run.head_sha -cne $Commit) { throw 'Legacy publication commit mismatch.' }
        return
    }
    if ($Run.event -cne 'workflow_run' -or !$Provenance) { throw 'Workflow-run publication requires provenance.' }
    foreach ($field in @('release_run_id','release_run_attempt','source_run_id','source_run_attempt','source_artifact_id')) {
        $value = $Provenance.$field
        if (($value -isnot [long] -and $value -isnot [int]) -or $value -le 0) { throw "Invalid provenance identifier: $field" }
    }
    if ($Commit -cnotmatch '^[a-f0-9]{40}$' -or ($Provenance.schema -isnot [int] -and $Provenance.schema -isnot [long]) -or $Provenance.schema -ne 1 -or
        $Provenance.repository -cne 'RoyMatus/Swypetris' -or $Run.repository.full_name -cne $Provenance.repository -or
        $Provenance.sha -cne $Commit -or $Provenance.release_run_id -ne $Run.id -or
        $Provenance.release_run_attempt -ne $Run.run_attempt -or
        $Provenance.source_artifact_digest -cnotmatch '^sha256:[a-f0-9]{64}$') { throw 'Publication provenance identity mismatch.' }
    if (!$SourceWorkflow -or $SourceWorkflow.state -cne 'active' -or
        $SourceWorkflow.path -cne '.github/workflows/ci.yml' -or
        $SourceRun.workflow_id -ne $SourceWorkflow.id -or $SourceRun.path -cne $SourceWorkflow.path -or
        $SourceRun.repository.full_name -cne $Provenance.repository -or
        $SourceRun.head_repository.full_name -cne $Provenance.repository -or
        $SourceRun.id -ne $Provenance.source_run_id -or $SourceRun.run_attempt -ne $Provenance.source_run_attempt -or
        $SourceRun.head_sha -cne $Commit -or $SourceRun.head_branch -cne 'main' -or $SourceRun.event -cne 'push' -or
        $SourceRun.status -cne 'completed' -or $SourceRun.conclusion -cne 'success') { throw 'Source CI identity or result mismatch.' }
    $expectedName = "full-regression-$($SourceRun.id)-$($SourceRun.run_attempt)"
    if (!$SourceArtifact -or $SourceArtifact.expired -ne $false -or
        $SourceArtifact.id -ne $Provenance.source_artifact_id -or $SourceArtifact.name -cne $expectedName -or
        $SourceArtifact.digest -cne $Provenance.source_artifact_digest -or
        $SourceArtifact.workflow_run.id -ne $SourceRun.id -or $SourceArtifact.workflow_run.head_sha -cne $Commit -or
        $SourceArtifact.workflow_run.head_branch -cne 'main') { throw 'Source CI artifact mismatch.' }
}
