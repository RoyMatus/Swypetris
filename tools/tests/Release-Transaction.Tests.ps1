BeforeAll {
    . (Join-Path $PSScriptRoot '../release/Release-Transaction.ps1')
    function Write-FixtureRelease {
        param([string]$Directory)
        foreach ($name in @('Swypetris.apk', 'Swypetris-1.0.0.aab', 'update.json', 'RuStore-icon.png')) {
            Set-Content -LiteralPath (Join-Path $Directory $name) -Value "new-$name"
        }
    }
}
Describe 'Local release transactions' {
    BeforeEach {
        $project = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        $transaction = New-ReleaseTransaction -Project $project -Version '1.0.0'
        New-Item -ItemType Directory -Path $transaction.Destination | Out-Null
        Set-Content -LiteralPath (Join-Path $transaction.Destination 'previous.txt') 'old release'
    }
    AfterEach { Close-ReleaseTransaction -Transaction $transaction }

    It 'retains the previous release when a required artifact is missing' {
        Set-Content -LiteralPath (Join-Path $transaction.Stage 'Swypetris.apk') 'new APK'
        { Complete-ReleaseTransaction -Transaction $transaction -Version '1.0.0' } | Should -Throw '*Missing release artifact*'
        Get-Content -LiteralPath (Join-Path $transaction.Destination 'previous.txt') | Should -Be 'old release'
    }
    It 'replaces the complete directory and checksums only the explicit manifest' {
        Write-FixtureRelease $transaction.Stage
        Set-Content -LiteralPath (Join-Path $transaction.Stage 'unrelated.apk') 'not in manifest'
        Complete-ReleaseTransaction -Transaction $transaction -Version '1.0.0'
        Test-Path -LiteralPath (Join-Path $transaction.Destination 'previous.txt') | Should -BeFalse
        $lines = @(Get-Content -LiteralPath (Join-Path $transaction.Destination 'SHA256SUMS.txt'))
        $lines.Count | Should -Be 4
        ($lines -join "`n") | Should -Not -Match 'unrelated'
        foreach ($line in $lines) {
            $hash, $name = $line -split '  ', 2
            $hash | Should -Be (Get-FileHash -LiteralPath (Join-Path $transaction.Destination $name)).Hash.ToLowerInvariant()
        }
        Test-Path -LiteralPath $transaction.Backup | Should -BeFalse
    }
    It 'restores the previous directory when promotion fails' {
        Write-FixtureRelease $transaction.Stage
        Mock Move-ReleaseDirectory {
            param($Source, $Destination)
            if ($Source -like '*.staging') { throw 'fixture promotion failure' }
            [IO.Directory]::Move($Source, $Destination)
        }
        { Complete-ReleaseTransaction -Transaction $transaction -Version '1.0.0' } | Should -Throw '*promotion failure*'
        Get-Content -LiteralPath (Join-Path $transaction.Destination 'previous.txt') | Should -Be 'old release'
        Test-Path -LiteralPath $transaction.Backup | Should -BeFalse
    }
    It 'preserves the backup and reports its location if rollback also fails' {
        Write-FixtureRelease $transaction.Stage
        Mock Move-ReleaseDirectory {
            param($Source, $Destination)
            if ($Source -like '*.staging' -or $Source -like '*.backup') { throw 'fixture move failure' }
            [IO.Directory]::Move($Source, $Destination)
        }
        { Complete-ReleaseTransaction -Transaction $transaction -Version '1.0.0' } | Should -Throw '*previous release retained at*'
        Get-Content -LiteralPath (Join-Path $transaction.Backup 'previous.txt') | Should -Be 'old release'
    }
    It 'rejects concurrent work for the same version and releases its lock after cleanup' {
        { New-ReleaseTransaction -Project $project -Version '1.0.0' } | Should -Throw
        Close-ReleaseTransaction -Transaction $transaction
        $transaction = New-ReleaseTransaction -Project $project -Version '1.0.0'
        Test-Path -LiteralPath $transaction.Stage | Should -BeTrue
    }
    It 'rejects transaction paths outside the release root' {
        $outside = Join-Path $project 'outside'
        { Assert-ReleaseTransactionPath -Transaction $transaction -Path $outside } | Should -Throw '*escapes dist*'
    }
}
