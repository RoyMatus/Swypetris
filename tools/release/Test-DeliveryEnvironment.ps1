<# Read-only preflight. No build, signing, Git write, credential output or access expansion. #>
[CmdletBinding()]
param([Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$Python,
    [string]$SdkDirectory = (Join-Path $env:LOCALAPPDATA 'Android/Sdk'),
    [switch]$CheckSigning,
    [string]$SigningDirectory = (Join-Path $env:USERPROFILE '.swypetris-signing'))
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $project 'tools/Verification.psm1') -Force
Import-Module (Join-Path $project 'tools/GitHub-Api.psm1') -Force
. (Join-Path $PSScriptRoot 'Release-Verification.ps1')
$jdk = Resolve-ReleaseJavaHome $JavaHome
$null = & (Join-Path $jdk 'bin/java.exe') -version 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Selected Java cannot start.' }
$null = & $Python -c 'import sys; assert sys.version_info >= (3, 10)'
if ($LASTEXITCODE -ne 0) { throw 'Python 3.10 or newer is required; Windows Store aliases are not interpreters.' }
foreach ($path in @('platform-tools/adb.exe','emulator/emulator.exe')) {
    if (!(Test-Path -LiteralPath (Join-Path $SdkDirectory $path) -PathType Leaf)) { throw "Missing Android SDK tool: $path" }
}
if ($CheckSigning) {
    foreach ($role in @('app','upload')) {
        foreach ($name in @("$role.clixml", "swypetris-$role.p12")) {
            if (!(Test-Path -LiteralPath (Join-Path $SigningDirectory $name) -PathType Leaf)) { throw "Missing private signing file: $name" }
        }
        $credential = Import-Clixml -LiteralPath (Join-Path $SigningDirectory "$role.clixml")
        if ($credential -isnot [pscredential]) { throw 'Signing credential is not a protected PSCredential.' }
        $credential = $null
    }
}
$client = New-GitHubClient 'RoyMatus/Swypetris'
$capabilities = Get-GitHubRepositoryCapabilities $client
$main = Invoke-GitHubRest -Client $client -Method Get -Path 'git/ref/heads/main'
$head = Invoke-CheckedGit $project @('rev-parse','HEAD')
$localMain = Invoke-CheckedGit $project @('rev-parse','refs/remotes/origin/main')
[pscustomobject]@{ checkout=$project; head=$head;
    branch=(Invoke-CheckedGit $project @('branch','--show-current'));
    dirty=[bool](Invoke-CheckedGit $project @('status','--porcelain'));
    remoteMain=$main.object.sha; trackingMain=$localMain;
    trackingMainCurrent=($localMain -ceq $main.object.sha);
    javaReady=$true; pythonReady=$true; sdkReady=$true;
    signingFilesChecked=[bool]$CheckSigning; github=$capabilities }
