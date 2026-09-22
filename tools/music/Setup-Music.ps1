<# Локальные зависимости генератора и проверенный архив FFmpeg; системные установки не меняет. #>
param([string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$bin = Join-Path $PSScriptRoot 'bin'
New-Item -ItemType Directory -Force $bin | Out-Null
$encoder = Join-Path $bin 'ffmpeg-8.1.2-essentials_build/bin/ffmpeg.exe'
if (!(Test-Path -LiteralPath $encoder)) {
    $archive = Join-Path $bin 'ffmpeg-8.1.2.zip'
    Invoke-WebRequest 'https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-8.1.2-essentials_build.zip' -OutFile $archive
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne 'DB580001CAA24AC104C8CB856CD113A87B0A443F7BDF47D8C12B1D740584A2EC') { throw 'Не совпала контрольная сумма FFmpeg' }
    Expand-Archive -LiteralPath $archive -DestinationPath $bin -Force
}
& $Python -m pip install --target (Join-Path $bin 'python_libs') numpy==2.3.5 mido==1.3.3 packaging==26.3 --disable-pip-version-check
if ($LASTEXITCODE -ne 0) { throw 'Не удалось установить зависимости музыки' }
