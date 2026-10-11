function Assert-LocalVerificationEvidence {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$EvidencePath,
        [Parameter(Mandatory)][string]$Project,
        [Parameter(Mandatory)][string]$HeadSha,
        [Parameter(Mandatory)][string]$BaseSha,
        [switch]$RequireFull,
        [string]$Python = 'python'
    )
    $arguments = @((Join-Path $PSScriptRoot 'ci/local_verification.py'), 'validate', '--root', $Project,
        '--head', $HeadSha, '--base', $BaseSha, '--evidence', $EvidencePath)
    if ($RequireFull) { $arguments += '--full' }
    & $Python @arguments | Write-Host
    if ($LASTEXITCODE -ne 0) { throw 'Local verification evidence rejected.' }
    Get-Content -LiteralPath $EvidencePath -Raw | ConvertFrom-Json
}
Export-ModuleMember -Function Assert-LocalVerificationEvidence
