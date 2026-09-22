<# Генерирует оригинальные мягкие эффекты в PCM WAV: 44,1 кГц, моно, 16 бит. #>
$ErrorActionPreference = 'Stop'
$outputDirectory = Join-Path $PSScriptRoot '../app/src/main/res/raw'
New-Item -ItemType Directory -Force $outputDirectory | Out-Null

# Записывает один звук с плавной атакой и затуханием без разрывов на границах.
function Write-Effect([string]$Name, [double]$Duration, [bool]$Clear) {
    $rate = 44100
    $count = [int]($rate * $Duration)
    $path = Join-Path $outputDirectory $Name
    $writer = [System.IO.BinaryWriter]::new([System.IO.File]::Create($path))
    try {
        $writer.Write([System.Text.Encoding]::ASCII.GetBytes('RIFF'))
        $writer.Write([int](36 + $count * 2))
        $writer.Write([System.Text.Encoding]::ASCII.GetBytes('WAVEfmt '))
        $writer.Write([int]16); $writer.Write([short]1); $writer.Write([short]1)
        $writer.Write([int]$rate); $writer.Write([int]($rate * 2))
        $writer.Write([short]2); $writer.Write([short]16)
        $writer.Write([System.Text.Encoding]::ASCII.GetBytes('data'))
        $writer.Write([int]($count * 2))
        for ($i = 0; $i -lt $count; $i++) {
            $t = $i / [double]$rate
            if ($Clear) {
                $sample = 0.0
                $frequencies = @(261.63, 329.63, 392.0)
                for ($note = 0; $note -lt 3; $note++) {
                    $local = $t - $note * 0.055
                    if ($local -ge 0) {
                        $envelope = (1 - [Math]::Exp(-$local / 0.009)) * [Math]::Exp(-$local / 0.080)
                        $sample += 0.18 * $envelope * [Math]::Sin(2 * [Math]::PI * $frequencies[$note] * $local)
                    }
                }
            } else {
                $envelope = (1 - [Math]::Exp(-$t / 0.004)) * [Math]::Exp(-$t / 0.030)
                $phase = 2 * [Math]::PI * (180 * $t - 250 * $t * $t)
                $sample = 0.38 * $envelope * ([Math]::Sin($phase) + 0.12 * [Math]::Sin(2 * $phase))
            }
            $fade = [Math]::Min(1.0, ($Duration - $t) / 0.025)
            $writer.Write([short]([Math]::Clamp($sample * $fade * 3, -0.9, 0.9) * 32767))
        }
    } finally { $writer.Dispose() }
}
Write-Effect 'drop_soft.wav' 0.12 $false
Write-Effect 'clear_soft.wav' 0.30 $true

