<# Публикуемая HTML-страница использует тот же текст, что офлайн-экран приложения. #>
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$text = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/assets/privacy.txt')).Replace("`r`n", "`n").Trim()
$paragraphs = $text.Split("`n`n", [StringSplitOptions]::RemoveEmptyEntries)
$body = '<h1>' + [Net.WebUtility]::HtmlEncode($paragraphs[0]) + '</h1>'
foreach ($paragraph in $paragraphs[1..($paragraphs.Length-1)]) { $body += "`n<p>" + [Net.WebUtility]::HtmlEncode($paragraph) + '</p>' }
$html = @"
<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Swypetris — политика конфиденциальности</title>
<style>body{max-width:760px;margin:40px auto;padding:0 20px;font:18px/1.65 system-ui,sans-serif;color:#1f2328;background:#fff}h1{font-size:30px;line-height:1.2}p{margin:1.3em 0}</style>
</head><body><main>$body</main></body></html>
"@
$destination = Join-Path $project 'publishing/privacy.html'
New-Item -ItemType Directory -Force (Split-Path $destination) | Out-Null
[IO.File]::WriteAllText($destination, $html, [Text.UTF8Encoding]::new($false))
Write-Output "Public HTML prepared: $destination (not deployed)"
