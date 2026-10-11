BeforeAll {
    $restore = Join-Path $PSScriptRoot '../release/Restore-SigningBackup.ps1'
    $password = ConvertTo-SecureString 'synthetic-backup-password' -AsPlainText -Force
    function Write-RestoreFixture {
        param([string]$Path, [hashtable]$Payload)
        $salt = [byte[]]::new(32)
        $nonce = [byte[]]::new(12)
        [Security.Cryptography.RandomNumberGenerator]::Fill($salt)
        [Security.Cryptography.RandomNumberGenerator]::Fill($nonce)
        $derive = [Security.Cryptography.Rfc2898DeriveBytes]::new('synthetic-backup-password', $salt, 600000, [Security.Cryptography.HashAlgorithmName]::SHA256)
        $key = $derive.GetBytes(32)
        $plain = [Text.Encoding]::UTF8.GetBytes(($Payload | ConvertTo-Json -Depth 4 -Compress))
        $cipher = [byte[]]::new($plain.Length)
        $tag = [byte[]]::new(16)
        $aes = [Security.Cryptography.AesGcm]::new($key, 16)
        try {
            $aes.Encrypt($nonce, $plain, $cipher, $tag)
            @{ version=1; algorithm='AES-256-GCM'; kdf='PBKDF2-SHA256'; iterations=600000
                salt=[Convert]::ToBase64String($salt); nonce=[Convert]::ToBase64String($nonce)
                tag=[Convert]::ToBase64String($tag); ciphertext=[Convert]::ToBase64String($cipher) } |
                ConvertTo-Json | Set-Content -LiteralPath $Path
        } finally { $aes.Dispose(); $derive.Dispose(); [Array]::Clear($key); [Array]::Clear($plain) }
    }
    # Real disposable PKCS12 data; never touch the user's signing store.
    $rsa = [Security.Cryptography.RSA]::Create(2048)
    $request = [Security.Cryptography.X509Certificates.CertificateRequest]::new('CN=Restore test only', $rsa,
        [Security.Cryptography.HashAlgorithmName]::SHA256, [Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $certificate = $request.CreateSelfSigned([datetimeoffset]::UtcNow.AddMinutes(-1), [datetimeoffset]::UtcNow.AddDays(1))
    try { $pkcs12 = $certificate.Export([Security.Cryptography.X509Certificates.X509ContentType]::Pkcs12, 'synthetic-key-password') }
    finally { $certificate.Dispose(); $rsa.Dispose() }
    $payload = @{}
    foreach ($role in @('app','upload')) {
        $payload[$role] = @{ alias="swypetris-$role"; password='synthetic-key-password'; pkcs12=[Convert]::ToBase64String($pkcs12) }
    }
    $backup = Join-Path $TestDrive 'synthetic-backup.json'
    Write-RestoreFixture -Path $backup -Payload $payload
}

Describe 'Protected signing backup restoration' {
    BeforeEach {
        $root = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $root | Out-Null
        $destination = Join-Path $root 'keys'
    }
    AfterEach { Remove-Variable -Name signingRestoreTestState -Scope Global -ErrorAction SilentlyContinue }

    It 'fails before writing credentials on platforms without Windows DPAPI' -Skip:($IsWindows) {
        { & $restore -Backup $backup -SigningDirectory $destination -Password $password } | Should -Throw '*Windows DPAPI*'
        @(Get-ChildItem -LiteralPath $root -Force).Count | Should -Be 0
    }

    It 'publishes both keys and protected credentials as one complete directory' -Skip:(!$IsWindows) {
        & $restore -Backup $backup -SigningDirectory ($destination + [IO.Path]::DirectorySeparatorChar) -Password $password
        @(Get-ChildItem -LiteralPath $destination -File).Count | Should -Be 4
        foreach ($role in @('app','upload')) {
            [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $destination "swypetris-$role.p12"))) | Should -Be $payload[$role].pkcs12
            $credential = Import-Clixml -LiteralPath (Join-Path $destination "$role.clixml")
            $credential.UserName | Should -Be "swypetris-$role"
            $credential.GetNetworkCredential().Password | Should -Be 'synthetic-key-password'
            Get-Content -LiteralPath (Join-Path $destination "$role.clixml") -Raw | Should -Not -Match 'synthetic-key-password'
        }
        (Get-Acl -LiteralPath $destination).AreAccessRulesProtected | Should -BeTrue
        @(Get-ChildItem -LiteralPath $root -Force -Filter '*.staging').Count | Should -Be 0
    }

    It 'rejects a malformed second role before creating staging or final directories' -Skip:(!$IsWindows) {
        $invalid = @{ app=$payload.app; upload=@{ alias='swypetris-upload'; password='synthetic-key-password'; pkcs12='not base64!' } }
        $badBackup = Join-Path $root 'invalid.json'
        Write-RestoreFixture -Path $badBackup -Payload $invalid
        { & $restore -Backup $badBackup -SigningDirectory $destination -Password $password } | Should -Throw
        Test-Path -LiteralPath $destination | Should -BeFalse
        @(Get-ChildItem -LiteralPath $root -Force -Directory).Count | Should -Be 0
    }

    It 'cleans only owned staging after a second-role write failure and allows retry' -Skip:(!$IsWindows) {
        $unrelated = Join-Path $root '.unrelated.staging'
        New-Item -ItemType Directory -Path $unrelated | Out-Null
        Set-Content -LiteralPath (Join-Path $unrelated 'keep.txt') 'preserve'
        $global:signingRestoreTestState = @{ FailSecond=$true }
        Mock Export-Clixml {
            param($InputObject, $LiteralPath)
            if ($LiteralPath.EndsWith('upload.clixml') -and $global:signingRestoreTestState.FailSecond) { throw 'synthetic second-role failure' }
            & (Get-Command Export-Clixml -CommandType Cmdlet) -InputObject $InputObject -LiteralPath $LiteralPath
        }
        { & $restore -Backup $backup -SigningDirectory $destination -Password $password } | Should -Throw '*second-role failure*'
        Test-Path -LiteralPath $destination | Should -BeFalse
        @(Get-ChildItem -LiteralPath $root -Force -Filter '.signing-restore-*.staging').Count | Should -Be 0
        Get-Content -LiteralPath (Join-Path $unrelated 'keep.txt') | Should -Be 'preserve'
        $global:signingRestoreTestState.FailSecond = $false
        & $restore -Backup $backup -SigningDirectory $destination -Password $password
        @(Get-ChildItem -LiteralPath $destination -File).Count | Should -Be 4
    }

    It 'preserves an existing destination without attempting restoration' -Skip:(!$IsWindows) {
        New-Item -ItemType Directory -Path $destination | Out-Null
        Set-Content -LiteralPath (Join-Path $destination 'keep.txt') 'existing'
        { & $restore -Backup $backup -SigningDirectory $destination -Password $password } | Should -Throw '*must not be overwritten*'
        Get-Content -LiteralPath (Join-Path $destination 'keep.txt') | Should -Be 'existing'
        @(Get-ChildItem -LiteralPath $root -Force -Filter '*.staging').Count | Should -Be 0
    }

    It 'does not overwrite a concurrent destination created during restoration' -Skip:(!$IsWindows) {
        $global:signingRestoreTestState = @{ Destination=$destination }
        Mock Export-Clixml {
            param($InputObject, $LiteralPath)
            & (Get-Command Export-Clixml -CommandType Cmdlet) -InputObject $InputObject -LiteralPath $LiteralPath
            if ($LiteralPath.EndsWith('upload.clixml')) {
                New-Item -ItemType Directory -Path $global:signingRestoreTestState.Destination | Out-Null
                Set-Content -LiteralPath (Join-Path $global:signingRestoreTestState.Destination 'winner.txt') 'other restore'
            }
        }
        { & $restore -Backup $backup -SigningDirectory $destination -Password $password } | Should -Throw
        @(Get-ChildItem -LiteralPath $destination -Force).Count | Should -Be 1
        Get-Content -LiteralPath (Join-Path $destination 'winner.txt') | Should -Be 'other restore'
        @(Get-ChildItem -LiteralPath $root -Force -Filter '*.staging').Count | Should -Be 0
    }
}
