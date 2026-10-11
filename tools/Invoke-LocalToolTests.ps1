[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Import-Module Pester -RequiredVersion 6.2.0
$result = Invoke-Pester -Path (Join-Path $PSScriptRoot 'tests/*.Tests.ps1') -PassThru
$allowed = if ($IsWindows) {
    @('runs the Unix launcher with focused filters and records fresh results', 'fails before writing credentials on platforms without Windows DPAPI')
} else {
    @('restores process environment and writes a failed timed summary',
      'publishes both keys and protected credentials as one complete directory',
      'rejects a malformed second role before creating staging or final directories',
      'cleans only owned staging after a second-role write failure and allows retry',
      'preserves an existing destination without attempting restoration',
      'does not overwrite a concurrent destination created during restoration',
      'supports hidden root, staging and destination directories on Windows',
      'handles <Failure> without mixing release files or changing the environment',
      'restores signing environment and writes a failed summary after Gradle failure')
}
$unexpected = @($result.Tests | Where-Object { $_.Result -eq 'Skipped' -and $_.Name -notin $allowed })
if ($unexpected.Count) { $unexpected | ForEach-Object { Write-Error "Unexpected skipped test: $($_.Name)" } }
if ($result.FailedCount -gt 0 -or $result.PassedCount -eq 0 -or $result.NotRunCount -gt 0 -or $unexpected.Count) { exit 1 }
