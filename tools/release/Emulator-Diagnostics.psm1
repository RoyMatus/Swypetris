function Invoke-BoundedDiagnostic {
    param([string]$Executable, [string[]]$Arguments, [string]$OutputFile, [int]$TimeoutSeconds=10)
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $Executable
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        if (!$process.Start()) { throw 'Cannot start diagnostic command.' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $timedOut = !$process.WaitForExit($TimeoutSeconds * 1000)
        if ($timedOut) { $process.Kill($true); $process.WaitForExit() }
        @($stdout.GetAwaiter().GetResult(), $stderr.GetAwaiter().GetResult()) | Set-Content -LiteralPath $OutputFile -Encoding utf8
        [pscustomobject]@{ timedOut=$timedOut; exitCode=$process.ExitCode; output=$OutputFile }
    } finally { $process.Dispose() }
}
Export-ModuleMember -Function Invoke-BoundedDiagnostic
