function New-ReleaseTransaction {
    param([Parameter(Mandatory)][string]$Project, [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version)
    $root = [IO.Path]::GetFullPath((Join-Path $Project 'dist'))
    New-Item -ItemType Directory -Force -Path $root | Out-Null
    if ((Get-Item -LiteralPath $root).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Release root must not be a link.' }
    # Keep the lock file: deleting it after close races with the next holder on Unix.
    $lock = $null
    try {
        $lock = [IO.File]::Open((Join-Path $root ".$Version.lock"), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        $id = [guid]::NewGuid().ToString('N')
        $transaction = [pscustomobject]@{
            Root = $root; Destination = Join-Path $root $Version
            Stage = Join-Path $root ".$Version-$id.staging"
            Backup = Join-Path $root ".$Version-$id.backup"
            Lock = $lock
        }
        New-Item -ItemType Directory -Path $transaction.Stage | Out-Null
        return $transaction
    } catch {
        if ($null -ne $lock) { $lock.Dispose() }
        throw
    }
}

function Assert-ReleaseTransactionPath {
    param([object]$Transaction, [string]$Path)
    $absolute = [IO.Path]::GetFullPath($Path)
    if ([IO.Path]::GetDirectoryName($absolute) -cne $Transaction.Root) { throw 'Release transaction path escapes dist.' }
    if ((Test-Path -LiteralPath $absolute) -and ((Get-Item -LiteralPath $absolute).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw 'Release transaction directory must not be a link.'
    }
}

function Complete-ReleaseTransaction {
    param([object]$Transaction, [string]$Version)
    foreach ($path in @($Transaction.Stage, $Transaction.Destination, $Transaction.Backup)) {
        Assert-ReleaseTransactionPath -Transaction $Transaction -Path $path
    }
    $manifest = @('Swypetris.apk', "Swypetris-$Version.aab", 'update.json', 'RuStore-icon.png')
    $checksums = foreach ($name in $manifest) {
        $file = Join-Path $Transaction.Stage $name
        if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing release artifact: $name" }
        '{0}  {1}' -f (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant(), $name
    }
    $checksums | Set-Content -Encoding ascii -LiteralPath (Join-Path $Transaction.Stage 'SHA256SUMS.txt')
    # Directory.Move rejects existing destinations instead of nesting the source directory.
    $backedUp = $false
    if (Test-Path -LiteralPath $Transaction.Destination) {
        Move-ReleaseDirectory -Source $Transaction.Destination -Destination $Transaction.Backup
        $backedUp = $true
    }
    try {
        Move-ReleaseDirectory -Source $Transaction.Stage -Destination $Transaction.Destination
    } catch {
        if ($backedUp) {
            try { Move-ReleaseDirectory -Source $Transaction.Backup -Destination $Transaction.Destination }
            catch { throw "Release promotion and rollback failed; previous release retained at $($Transaction.Backup). $($_.Exception.Message)" }
        }
        throw
    }
    if ($backedUp) {
        # Cleanup failure does not invalidate the successfully promoted complete release.
        try { Remove-Item -LiteralPath $Transaction.Backup -Recurse -Force -ErrorAction Stop }
        catch { Write-Warning "Release published locally; backup cleanup required at $($Transaction.Backup)." }
    }
}

function Move-ReleaseDirectory {
    param([string]$Source, [string]$Destination)
    [IO.Directory]::Move($Source, $Destination)
}

function Close-ReleaseTransaction {
    param([object]$Transaction)
    if ($null -eq $Transaction) { return }
    try {
        Assert-ReleaseTransactionPath -Transaction $Transaction -Path $Transaction.Stage
        if (Test-Path -LiteralPath $Transaction.Stage) { Remove-Item -LiteralPath $Transaction.Stage -Recurse -Force }
    } finally { $Transaction.Lock.Dispose() }
}
