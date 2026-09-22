<# Полная генерация восьми ретро-тем. Нужны PowerShell 7, Python с numpy 2.3.5 и закреплённый FFmpeg. #>
param([string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$report = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../app/build/reports/audio'))
New-Item -ItemType Directory -Force $report | Out-Null
Add-Type -Path (Join-Path $PSScriptRoot 'music/RenderMusic.cs')
[RenderMusic]::GenerateFanfare($report)
Copy-Item -LiteralPath (Join-Path $report 'record_fanfare.wav') -Destination (Join-Path $PSScriptRoot '../app/src/main/res/raw/record_fanfare.wav') -Force
& $Python (Join-Path $PSScriptRoot 'music/render_playlist.py')
if ($LASTEXITCODE -ne 0) { throw 'Не удалось создать плейлист' }
