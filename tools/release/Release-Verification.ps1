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
