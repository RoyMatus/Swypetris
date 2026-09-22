<# Создаёт переносимый зашифрованный архив ключей; пароль запрашивается только локально. #>
param(
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.swypetris-signing'),
    [Parameter(Mandatory = $true)][string]$Destination,
    [Security.SecureString]$Password
)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $Destination) { throw 'Destination already exists; choose another backup filename.' }
if (!$Password) {
    $Password = Read-Host 'Backup password (store in your password manager)' -AsSecureString
    $confirm = Read-Host 'Repeat backup password' -AsSecureString
} else { $confirm = $Password }
$first = [Management.Automation.PSCredential]::new('backup', $Password).GetNetworkCredential().Password
$second = [Management.Automation.PSCredential]::new('backup', $confirm).GetNetworkCredential().Password
if ($first.Length -lt 16 -or $first -cne $second) { throw 'Use matching passwords of at least 16 characters.' }
$payload = @{}
foreach ($role in @('app','upload')) {
    $credential = Import-Clixml -LiteralPath (Join-Path $SigningDirectory "$role.clixml")
    $payload[$role] = @{
        alias = $credential.UserName
        password = $credential.GetNetworkCredential().Password
        pkcs12 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $SigningDirectory "swypetris-$role.p12")))
    }
}
$salt = [byte[]]::new(32); [Security.Cryptography.RandomNumberGenerator]::Fill($salt)
$nonce = [byte[]]::new(12); [Security.Cryptography.RandomNumberGenerator]::Fill($nonce)
$derive = [Security.Cryptography.Rfc2898DeriveBytes]::new($first, $salt, 600000, [Security.Cryptography.HashAlgorithmName]::SHA256)
$key = $derive.GetBytes(32)
$plain = [Text.Encoding]::UTF8.GetBytes(($payload | ConvertTo-Json -Depth 4 -Compress))
$cipher = [byte[]]::new($plain.Length); $tag = [byte[]]::new(16)
$aes = [Security.Cryptography.AesGcm]::new($key, 16)
try {
    $aes.Encrypt($nonce, $plain, $cipher, $tag)
    @{ version=1; algorithm='AES-256-GCM'; kdf='PBKDF2-SHA256'; iterations=600000
        salt=[Convert]::ToBase64String($salt); nonce=[Convert]::ToBase64String($nonce)
        tag=[Convert]::ToBase64String($tag); ciphertext=[Convert]::ToBase64String($cipher) } |
        ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath $Destination
} finally { $aes.Dispose(); $derive.Dispose(); [Array]::Clear($plain); [Array]::Clear($key); $first=$null; $second=$null; $payload=$null }
Write-Output "Encrypted portable backup saved to $Destination. Keep it offline and separate from its password."
