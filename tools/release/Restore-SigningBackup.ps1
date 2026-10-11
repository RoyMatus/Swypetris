<# Восстанавливает проверенный AES-GCM архив в новый каталог и защищает пароли Windows DPAPI. #>
param(
    [Parameter(Mandatory = $true)][string]$Backup,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.swypetris-signing'),
    [Security.SecureString]$Password
)
$ErrorActionPreference = 'Stop'
if (!$IsWindows) { throw 'Signing restore requires Windows DPAPI credential protection.' }
$SigningDirectory = [IO.Path]::TrimEndingDirectorySeparator([IO.Path]::GetFullPath($SigningDirectory))
if (Test-Path -LiteralPath $SigningDirectory) { throw 'Restore only into a new directory; existing signing keys must not be overwritten.' }
if (!$Password) { $Password = Read-Host 'Backup password' -AsSecureString }
$secret = [Management.Automation.PSCredential]::new('backup', $Password).GetNetworkCredential().Password
$envelope = Get-Content -LiteralPath $Backup -Raw | ConvertFrom-Json
if ($envelope.version -ne 1 -or $envelope.algorithm -ne 'AES-256-GCM' -or $envelope.kdf -ne 'PBKDF2-SHA256' -or $envelope.iterations -ne 600000) { throw 'Unsupported backup format.' }
$derive = [Security.Cryptography.Rfc2898DeriveBytes]::new($secret, [Convert]::FromBase64String($envelope.salt), 600000, [Security.Cryptography.HashAlgorithmName]::SHA256)
$key = $derive.GetBytes(32)
$cipher = [Convert]::FromBase64String($envelope.ciphertext)
$plain = [byte[]]::new($cipher.Length)
$aes = [Security.Cryptography.AesGcm]::new($key, 16)
$decoded = @{}
$stage = $null
$ownsStage = $false
try {
    $aes.Decrypt([Convert]::FromBase64String($envelope.nonce), $cipher, [Convert]::FromBase64String($envelope.tag), $plain)
    $payload = [Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
    foreach ($role in @('app','upload')) {
        if ($payload.$role.alias -ne "swypetris-$role" -or !$payload.$role.password -or !$payload.$role.pkcs12) { throw 'Invalid signing payload.' }
        $decoded[$role] = [Convert]::FromBase64String($payload.$role.pkcs12)
        if (!$decoded[$role].Length) { throw 'Empty signing key payload.' }
    }
    $parent = [IO.Path]::GetDirectoryName($SigningDirectory)
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $stage = Join-Path $parent ('.signing-restore-' + [guid]::NewGuid().ToString('N') + '.staging')
    New-Item -ItemType Directory -Path $stage | Out-Null
    $ownsStage = $true
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $stage /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict restored directory permissions.' }
    foreach ($role in @('app','upload')) {
        [IO.File]::WriteAllBytes((Join-Path $stage "swypetris-$role.p12"), $decoded[$role])
        $secure = ConvertTo-SecureString $payload.$role.password -AsPlainText -Force
        [Management.Automation.PSCredential]::new($payload.$role.alias, $secure) |
            Export-Clixml -LiteralPath (Join-Path $stage "$role.clixml")
    }
    # Directory.Move atomically rejects an existing destination, including a
    # concurrent restore winner; Move-Item could nest the staging directory.
    [IO.Directory]::Move($stage, $SigningDirectory)
    $ownsStage = $false
} finally {
    $aes.Dispose(); $derive.Dispose(); [Array]::Clear($plain); [Array]::Clear($key)
    foreach ($bytes in $decoded.Values) { [Array]::Clear($bytes) }
    $secret=$null; $payload=$null
    if ($ownsStage -and (Test-Path -LiteralPath $stage)) {
        # Delete only this invocation's exact sibling staging directory.
        $actual = Get-Item -LiteralPath $stage -Force
        if ([IO.Path]::GetDirectoryName($actual.FullName) -ne $parent -or
            $actual.FullName -ne [IO.Path]::GetFullPath($stage) -or
            ($actual.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Refusing cleanup of an unexpected signing staging path.'
        }
        Remove-Item -LiteralPath $actual.FullName -Recurse -Force
    }
}
Write-Output "Keys restored to $SigningDirectory."
