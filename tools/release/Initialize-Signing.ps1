<# Создаёт постоянные ключи вне проекта; пароли защищены Windows DPAPI и не выводятся. #>
param([string]$SigningDirectory = (Join-Path $env:USERPROFILE '.swypetris-signing'))
$ErrorActionPreference = 'Stop'
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
if (!(Test-Path -LiteralPath $keytool)) { throw 'Set JAVA_HOME to a JDK 21 installation.' }
New-Item -ItemType Directory -Force -Path $SigningDirectory | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $SigningDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict signing directory permissions.' }
foreach ($role in @('app','upload')) {
    $store = Join-Path $SigningDirectory "swypetris-$role.p12"
    $credential = Join-Path $SigningDirectory "$role.clixml"
    if ((Test-Path -LiteralPath $store) -or (Test-Path -LiteralPath $credential)) {
        if ((Test-Path -LiteralPath $store) -and (Test-Path -LiteralPath $credential)) { continue }
        throw "Incomplete existing signing material for $role; restore the missing file instead of replacing the key."
    }
    $bytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $secret = [Convert]::ToBase64String($bytes)
    $secure = ConvertTo-SecureString $secret -AsPlainText -Force
    [Management.Automation.PSCredential]::new("swypetris-$role", $secure) | Export-Clixml -LiteralPath $credential
    try {
        $env:SWYPETRIS_KEYTOOL_PASSWORD = $secret
        & $keytool -genkeypair -keystore $store -storetype PKCS12 -alias "swypetris-$role" `
            -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 36500 `
            -dname 'CN=Roy Matus, OU=Swypetris, O=Independent Developer, C=RU' `
            -storepass:env SWYPETRIS_KEYTOOL_PASSWORD -keypass:env SWYPETRIS_KEYTOOL_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw "Key generation failed for $role." }
    } finally {
        Remove-Item Env:SWYPETRIS_KEYTOOL_PASSWORD -ErrorAction SilentlyContinue
        $secret = $null
    }
}
Write-Output "Signing material ready in $SigningDirectory. Back up both keys and export their passwords to your password manager before changing Windows or computers."
