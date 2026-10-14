function Invoke-BoundedDiagnostic {
    param(
        [string]$Executable, [string[]]$Arguments, [string]$OutputFile,
        [ValidateRange(1,600)][int]$TimeoutSeconds=10,
        [ValidateRange(0,600000)][int]$TimeoutMilliseconds=0,
        [ValidateRange(1,10000)][int]$DrainTimeoutMilliseconds=1000,
        [switch]$BinaryOutput
    )
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Executable
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    $cancellation = [Threading.CancellationTokenSource]::new()
    $outputStream = $errorStream = $null
    $errorFile = "$OutputFile.stderr.log"
    try {
        # Copy bytes as they arrive, retaining partial evidence and valid PNG data.
        $outputStream = [IO.File]::Open($OutputFile, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        $errorStream = [IO.File]::Open($errorFile, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::Read)
        if (!$process.Start()) { throw 'Cannot start diagnostic command.' }
        $stdout = $process.StandardOutput.BaseStream.CopyToAsync($outputStream, 81920, $cancellation.Token)
        $stderr = $process.StandardError.BaseStream.CopyToAsync($errorStream, 81920, $cancellation.Token)
        $wait = if ($TimeoutMilliseconds -gt 0) { $TimeoutMilliseconds } else { $TimeoutSeconds * 1000 }
        $timedOut = !$process.WaitForExit($wait)
        $cleanupError = $null
        if ($timedOut) {
            try { if (!$process.HasExited) { $process.Kill($true) } }
            catch { $cleanupError = $_.Exception.Message }
            # Neither process shutdown nor inherited pipe handles may wait forever.
            $null = $process.WaitForExit($DrainTimeoutMilliseconds)
        }
        $drained = $false
        try { $drained = [Threading.Tasks.Task]::WhenAll([Threading.Tasks.Task[]]@($stdout,$stderr)).Wait($DrainTimeoutMilliseconds) }
        catch { $cleanupError = $_.Exception.Message }
        if (!$drained) {
            $cancellation.Cancel()
            $process.StandardOutput.Dispose()
            $process.StandardError.Dispose()
        }
        $outputStream.Dispose(); $outputStream = $null
        $errorStream.Dispose(); $errorStream = $null
        if (!$BinaryOutput -and (Get-Item -LiteralPath $errorFile).Length -gt 0) {
            [IO.File]::AppendAllText($OutputFile, [Environment]::NewLine + [IO.File]::ReadAllText($errorFile))
        }
        [pscustomobject]@{
            timedOut=$timedOut; outputDrainTimedOut=(!$drained)
            exitCode=$(if ($process.HasExited) { $process.ExitCode } else { $null })
            output=$OutputFile; stderr=$errorFile; cleanupError=$cleanupError
        }
    } finally {
        if ($outputStream) { $outputStream.Dispose() }
        if ($errorStream) { $errorStream.Dispose() }
        $cancellation.Cancel(); $cancellation.Dispose()
        $process.Dispose()
    }
}
Export-ModuleMember -Function Invoke-BoundedDiagnostic
