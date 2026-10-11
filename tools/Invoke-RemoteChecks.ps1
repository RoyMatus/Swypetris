<#
.SYNOPSIS
Rejects retired cloud check requests. All verification runs locally.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$Repository = 'RoyMatus/Swypetris',
    [string]$Head,
    [ValidateSet('FocusedJvm', 'FocusedAndroid')][string]$Suite,
    [string[]]$TestClass,
    [string]$Reason
)
throw 'Remote checks are disabled by project policy. Use tools/Verify-Tests.ps1 for local focused checks or tools/Invoke-LocalChecks.ps1 for the final local gate. If no suitable local executor is available, verification is blocked; do not dispatch GitHub Actions.'
