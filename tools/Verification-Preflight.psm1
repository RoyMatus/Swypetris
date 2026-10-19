Import-Module (Join-Path $PSScriptRoot 'Verification.psm1')
Import-Module (Join-Path $PSScriptRoot 'ExecutionProfile.psm1')
Import-Module (Join-Path $PSScriptRoot 'Emulator-Readiness.psm1')
function Assert-VerificationScmRoot {
    param([string]$Project)
    $common=Invoke-CheckedGit $Project @('rev-parse','--path-format=absolute','--git-common-dir')
    if (![IO.Path]::IsPathRooted($common) -or
        ![string]::Equals([IO.Path]::GetPathRoot([IO.Path]::GetFullPath($Project)),[IO.Path]::GetPathRoot($common),[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Sonar SCM requires worktree and Git common directory on the same volume. Use a compatible local checkout; do not disable SCM.'
    }
    [pscustomobject]@{compatible=$true;commonDirectory=$common}
}
function Assert-VerificationInstallSigner {
    param([string]$Adb,[ValidatePattern('^emulator-\d+$')][string]$Serial,[string]$ApkPath,[string]$ApkSigner,[string]$Directory)
    $installed=Invoke-EmulatorReadinessCommand $Adb @('-s',$Serial,'shell','pm','list','packages','ru.itoltec.swypetris') 10000
    if ($installed -cnotmatch '(?m)^package:ru\.itoltec\.swypetris\s*$') {return}
    $paths=Invoke-EmulatorReadinessCommand $Adb @('-s',$Serial,'shell','pm','path','ru.itoltec.swypetris') 10000
    $base=@($paths -split '\r?\n'|Where-Object {$_ -cmatch '^package:/data/app/[^\r\n]+/base\.apk$'})
    if($base.Count -ne 1){throw 'Cannot identify installed Swypetris base APK.'}
    $destination=Join-Path $Directory ('installed-'+[guid]::NewGuid().ToString('N')+'.apk')
    $null=Invoke-EmulatorReadinessCommand $Adb @('-s',$Serial,'pull',$base[0].Substring(8),$destination) 10000
    $fingerprints=@(foreach($file in @($ApkPath,$destination)){
        $lines=@(& $ApkSigner verify --print-certs $file 2>&1)
        if($LASTEXITCODE -ne 0){throw 'Cannot verify candidate/installed APK signature.'}
        $certMatches=@($lines|Select-String '^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})$')
        if($certMatches.Count -ne 1){throw 'Ambiguous APK signer; inspect before installation.'}
        $certMatches[0].Matches[0].Groups[1].Value
    })
    if($fingerprints[0] -cne $fingerprints[1]){throw "Swypetris signer mismatch on $Serial. Use a dedicated debug emulator or explicitly reset only the owned test app. No automatic uninstall performed."}
}
function Resolve-VerificationSdk {
    param([string]$Project,[string]$SdkDirectory)
    $localSdk=$null
    $properties=Join-Path $Project 'local.properties'
    if (Test-Path -LiteralPath $properties) {
        $match=[regex]::Match((Get-Content -LiteralPath $properties -Raw),'(?m)^\s*sdk\.dir\s*=\s*(.+?)\s*$')
        if ($match.Success) { $localSdk=$match.Groups[1].Value.Replace('\:',':').Replace('\\','\') }
    }
    $locations=@($SdkDirectory,$localSdk,$env:ANDROID_HOME,$env:ANDROID_SDK_ROOT) | Where-Object { $_ }
    $normalized=@($locations | ForEach-Object { [IO.Path]::GetFullPath($_).TrimEnd('\','/') } | Select-Object -Unique)
    if ($normalized.Count -gt 1) { throw 'Android SDK locations conflict; align explicit SDK, local.properties, ANDROID_HOME and ANDROID_SDK_ROOT.' }
    if (!$normalized.Count) { $normalized=@(Join-Path $env:LOCALAPPDATA 'Android/Sdk') }
    if (!(Test-Path -LiteralPath $normalized[0] -PathType Container)) { throw 'Selected Android SDK directory is unavailable.' }
    (Resolve-Path -LiteralPath $normalized[0]).Path
}
function Assert-VerificationAnalysisIdentity {
    param([string]$Project,[string]$Mode,[string]$Head,[string]$Base,[int]$PullRequestNumber,[string]$BaseBranch='main')
    if ($Mode -eq 'PR' -and $PullRequestNumber -le 0) { throw 'App PR verification requires -PullRequestNumber for Sonar analysis.' }
    if ($BaseBranch -ne 'main') { throw 'Verification base branch must be main.' }
    $remote=Invoke-CheckedGit $Project @('ls-remote','origin','refs/heads/main')
    $remoteSha=($remote -split '\s+')[0]
    if ($PullRequestNumber -le 0) {
        if ($remoteSha -cne $Head) { throw 'Full Sonar without a PR number requires exact remote main.' }
        return [pscustomobject]@{branch='main';pullRequestNumber=0;remoteMain=$remoteSha}
    }
    if ($remoteSha -cne $Base) { throw 'PR base is stale; fetch current remote main before verification.' }
    $branch=Invoke-CheckedGit $Project @('branch','--show-current')
    if (!$branch) { throw 'PR analysis requires a named branch.' }
    $prHead=Invoke-CheckedGit $Project @('ls-remote','origin',"refs/pull/$PullRequestNumber/head")
    if (!$prHead -or ($prHead -split '\s+')[0] -cne $Head) { throw 'PR analysis identity does not match the current head; verify PullRequestNumber and push the final commit.' }
    [pscustomobject]@{branch=$branch;pullRequestNumber=$PullRequestNumber;remoteMain=$remoteSha}
}
function Resolve-VerificationEnvironment {
    param([string]$Project,[bool]$RequiresAndroid,[string]$JavaHome,[string]$SdkDirectory)
    if (!$RequiresAndroid) { return [pscustomobject]@{androidRequired=$false} }
    if (!$JavaHome) { $JavaHome=$env:JAVA_HOME }
    if (!$JavaHome -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe')) -or !(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) { throw 'JDK 21 is required; pass -JavaHome or configure JAVA_HOME.' }
    $javaVersion=@(& (Join-Path $JavaHome 'bin/java.exe') -version 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $javaVersion -notmatch 'version "21(?:\.|\")') { throw 'Selected JDK must be Java 21.' }
    $sdk=Resolve-VerificationSdk -Project $Project -SdkDirectory $SdkDirectory
    $platform=Get-ProjectAndroidPlatform (Get-Content (Join-Path $Project 'app/build.gradle.kts') -Raw)
    foreach ($file in @("platforms/$platform/android.jar",'platform-tools/adb.exe','emulator/emulator.exe')) {
        if (!(Test-Path -LiteralPath (Join-Path $sdk $file) -PathType Leaf)) { throw "Selected SDK is missing $file." }
    }
    if (!@(Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue).Count) { throw 'Android build-tools are unavailable.' }
    foreach ($name in @('SONAR_ORGANIZATION','SONAR_PROJECT_KEY','SONAR_TOKEN')) {
        if (![Environment]::GetEnvironmentVariable($name)) { throw "Missing $name; configure locally without committing credentials." }
    }
    [pscustomobject]@{androidRequired=$true;javaHome=(Resolve-Path $JavaHome).Path;sdkDirectory=$sdk;javaVersion=$javaVersion;platform=$platform;adb=(Join-Path $sdk 'platform-tools/adb.exe')}
}
Export-ModuleMember -Function Resolve-VerificationEnvironment,Resolve-VerificationSdk,Assert-VerificationAnalysisIdentity,Assert-VerificationScmRoot,Assert-VerificationInstallSigner
