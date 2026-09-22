<# Проверяет все восемь композиций после генерации, сохраняя JSON измерений декодированного Vorbis. #>
param([string]$Python = 'python')
$ErrorActionPreference = 'Stop'
& $Python (Join-Path $PSScriptRoot 'music/check_music.py')
if ($LASTEXITCODE -ne 0) { throw 'Проверка плейлиста завершилась ошибкой' }
