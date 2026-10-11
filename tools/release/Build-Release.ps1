<# Выпускает APK с ключом приложения и AAB с отдельным ключом загрузки Google Play. #>
param([string]$SigningDirectory = (Join-Path $env:USERPROFILE '.swypetris-signing'), [switch]$NoDaemon)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Release-Verification.ps1')
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $project 'tools/Verification.psm1') -Force
$started = Get-Date
$summary = New-VerificationSummary -Project $project -Suite 'Release' -Started $started
$summary.roles = @()
$summary.noDaemon = [bool]$NoDaemon
$logDirectory = Join-Path $project 'app/build/verification'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$logBase = Join-Path $logDirectory ("release-{0}" -f $started.ToString('yyyyMMdd-HHmmss-fff'))
$options = Get-ReleaseGradleOptions -NoDaemon:$NoDaemon
$versionLines = @(Get-Content -LiteralPath (Join-Path $project 'gradle.properties') |
    Where-Object { $_ -match '^swypetrisVersion\s*=' })
if ($versionLines.Count -ne 1 -or $versionLines[0] -notmatch '^swypetrisVersion\s*=\s*(\d+\.\d+\.\d+)\s*$') {
    throw 'gradle.properties must contain one swypetrisVersion in major.minor.patch format.'
}
$version = $Matches[1]
$destination = Join-Path $project "dist/$version"
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$variables = @('SWYPETRIS_STORE_FILE','SWYPETRIS_STORE_PASSWORD','SWYPETRIS_KEY_ALIAS')
$previous = @{}
foreach ($name in $variables) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
Push-Location $project
try {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'aapt.exe') } | Select-Object -First 1
    if (!$buildTools -or !$env:JAVA_HOME) { throw 'Android build-tools and JAVA_HOME are required.' }
    foreach ($role in @('app','upload')) {
        $roleStarted = Get-Date
        $credential = Import-Clixml -LiteralPath (Join-Path $SigningDirectory "$role.clixml")
        $env:SWYPETRIS_STORE_FILE = Join-Path $SigningDirectory "swypetris-$role.p12"
        $env:SWYPETRIS_STORE_PASSWORD = $credential.GetNetworkCredential().Password
        $env:SWYPETRIS_KEY_ALIAS = $credential.UserName
        if ($role -eq 'app') {
            & ./gradlew.bat :app:assembleRelease :app:testDebugUnitTest :app:lintRelease @options 2>&1 | Tee-Object -FilePath "$logBase-app.log"
            if ($LASTEXITCODE -ne 0) { throw 'Release APK build or validation failed.' }
            $digest = Assert-ReleaseSignature -Artifact (Join-Path $project 'app/build/outputs/apk/release/app-release.apk') -Role $role -Project $project -JavaHome $env:JAVA_HOME -BuildTools $buildTools.FullName
            Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination (Join-Path $destination 'Swypetris.apk')
            Copy-Item -LiteralPath 'app/src/main/res/drawable-nodpi/ic_launcher_artwork.png' -Destination (Join-Path $destination 'RuStore-icon.png') -Force
            $aapt = Join-Path $buildTools.FullName 'aapt.exe'
            $apk = Join-Path $destination 'Swypetris.apk'
            $badging = @(& $aapt dump badging $apk)
            $packageLine = $badging | Select-Object -First 1
            if ($LASTEXITCODE -ne 0 -or $packageLine -notmatch "^package: name='ru\.itoltec\.swypetris' versionCode='(\d+)' versionName='([^']+)'" -or
                $Matches[2] -ne $version) { throw 'Signed APK package or version does not match release configuration.' }
            $metadata = [ordered]@{
                versionCode = [int]$Matches[1]
                versionName = $Matches[2]
                sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
            }
            $metadata | ConvertTo-Json | Set-Content -Encoding utf8 -LiteralPath (Join-Path $destination 'update.json')
        } else {
            & ./gradlew.bat :app:bundleRelease @options 2>&1 | Tee-Object -FilePath "$logBase-upload.log"
            if ($LASTEXITCODE -ne 0) { throw 'Release AAB build failed.' }
            $digest = Assert-ReleaseSignature -Artifact (Join-Path $project 'app/build/outputs/bundle/release/app-release.aab') -Role $role -Project $project -JavaHome $env:JAVA_HOME -BuildTools $buildTools.FullName
            Copy-Item -LiteralPath 'app/build/outputs/bundle/release/app-release.aab' -Destination (Join-Path $destination "Swypetris-$version.aab")
        }
        $roleSummary = [ordered]@{ role = $role; durationSeconds = ((Get-Date) - $roleStarted).TotalSeconds; certificateSha256 = $digest }
        if ($role -eq 'app') {
            $outcome = Get-VerificationTaskOutcome -Log "$logBase-app.log" -Task ':app:testDebugUnitTest'
            $roleSummary.jvmOutcome = $outcome
            $roleSummary.jvm = Read-VerificationReports -Reports @(Get-ChildItem (Join-Path $project 'app/build/test-results/testDebugUnitTest/TEST-*.xml') | ForEach-Object FullName) -Started $roleStarted -Reused ($outcome -in @('UP-TO-DATE', 'FROM-CACHE'))
        }
        $summary.roles += $roleSummary
    }
    Get-ChildItem -LiteralPath $destination -File | Where-Object { $_.Extension -in @('.apk','.aab') } |
        ForEach-Object { '{0}  {1}' -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $_.Name } |
        Set-Content -Encoding ascii -LiteralPath (Join-Path $destination 'SHA256SUMS.txt')
    $summary.result = 'passed'
    $summary.version = $version
} finally {
    foreach ($name in $variables) {
        if ($null -eq $previous[$name]) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    }
    Pop-Location
    $summary.durationSeconds = ((Get-Date) - $started).TotalSeconds
    $summary | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 -LiteralPath "$logBase.json"
    Write-Output "Release verification summary: $logBase.json"
}
Write-Output "Release files: $destination"
