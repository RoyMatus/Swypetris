<# Выпускает APK с ключом приложения и AAB с отдельным ключом загрузки Google Play. #>
param([string]$SigningDirectory = (Join-Path $env:USERPROFILE '.swypetris-signing'))
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$destination = Join-Path $project 'dist/1.0.0'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$variables = @('SWYPETRIS_STORE_FILE','SWYPETRIS_STORE_PASSWORD','SWYPETRIS_KEY_ALIAS')
$previous = @{}
foreach ($name in $variables) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
Push-Location $project
try {
    foreach ($role in @('app','upload')) {
        $credential = Import-Clixml -LiteralPath (Join-Path $SigningDirectory "$role.clixml")
        $env:SWYPETRIS_STORE_FILE = Join-Path $SigningDirectory "swypetris-$role.p12"
        $env:SWYPETRIS_STORE_PASSWORD = $credential.GetNetworkCredential().Password
        $env:SWYPETRIS_KEY_ALIAS = $credential.UserName
        if ($role -eq 'app') {
            & ./gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease --console=plain --no-daemon
            if ($LASTEXITCODE -ne 0) { throw 'Release APK build or validation failed.' }
            Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination (Join-Path $destination 'Swypetris-1.0.0.apk')
        } else {
            & ./gradlew.bat :app:bundleRelease --console=plain --no-daemon
            if ($LASTEXITCODE -ne 0) { throw 'Release AAB build failed.' }
            Copy-Item -LiteralPath 'app/build/outputs/bundle/release/app-release.aab' -Destination (Join-Path $destination 'Swypetris-1.0.0.aab')
        }
    }
    Get-ChildItem -LiteralPath $destination -File | Where-Object { $_.Extension -in @('.apk','.aab') } |
        ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name } |
        Set-Content -Encoding ascii -LiteralPath (Join-Path $destination 'SHA256SUMS.txt')
} finally {
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    Pop-Location
}
Write-Output "Release files: $destination"
