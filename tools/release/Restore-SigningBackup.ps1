<# Восстанавливает проверенный AES-GCM архив в новый каталог и защищает пароли Windows DPAPI. #>
param(
    [Parameter(Mandatory = $true)][string]$Backup,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.swypetris-signing'),
    [Security.SecureString]$Password
)
$ErrorActionPreference = 'Stop'
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
try {
    $aes.Decrypt([Convert]::FromBase64String($envelope.nonce), $cipher, [Convert]::FromBase64String($envelope.tag), $plain)
    $payload = [Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
    foreach ($role in @('app','upload')) {
        if ($payload.$role.alias -ne "swypetris-$role" -or !$payload.$role.password -or !$payload.$role.pkcs12) { throw 'Invalid signing payload.' }
    }
    New-Item -ItemType Directory -Path $SigningDirectory | Out-Null
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $SigningDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict restored directory permissions.' }
    foreach ($role in @('app','upload')) {
        [IO.File]::WriteAllBytes((Join-Path $SigningDirectory "swypetris-$role.p12"), [Convert]::FromBase64String($payload.$role.pkcs12))
        $secure = ConvertTo-SecureString $payload.$role.password -AsPlainText -Force
        [Management.Automation.PSCredential]::new($payload.$role.alias, $secure) |
            Export-Clixml -LiteralPath (Join-Path $SigningDirectory "$role.clixml")
    }
} finally { $aes.Dispose(); $derive.Dispose(); [Array]::Clear($plain); [Array]::Clear($key); $secret=$null; $payload=$null }
Write-Output "Keys restored to $SigningDirectory."
