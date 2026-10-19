[CmdletBinding()]
param([Parameter(Mandatory)][string]$JavaHome,[ValidateRange(1,64)][int]$MaxWorkers=2,[switch]$RefreshDependencies)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$previous=$env:JAVA_HOME
try {
    $java=Join-Path $JavaHome 'bin/java.exe'
    if(!(Test-Path -LiteralPath $java)){throw 'Explicit local JDK is required.'}
    $env:JAVA_HOME=$JavaHome
    $arguments=@('-I',(Join-Path $PSScriptRoot 'verify-gradle-sources.init.gradle'),'verifyIdeGradleSources','--dependency-verification','strict','--console=plain',"--max-workers=$MaxWorkers")
    if($RefreshDependencies){$arguments+='--refresh-dependencies'}
    Push-Location $root
    try {& (Join-Path $root 'gradlew.bat') @arguments;if($LASTEXITCODE){throw 'Strict IDE Gradle source resolution failed; review publisher hashes, never regenerate automatically.'}}
    finally {Pop-Location}
} finally {$env:JAVA_HOME=$previous}
