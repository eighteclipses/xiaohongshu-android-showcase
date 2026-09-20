# Text poster renderer: replicates the Android TextToImageConverter style.
# All Chinese literals are built from [char] codes to avoid ps1 encoding issues.
param(
  [string]$OutFile = "",
  [string]$Title = "",
  [string]$Content = "",
  [string]$Author = ""
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$W = 1080; $H = 1440
$body = ($Title.Trim() + "`n" + $Content.Trim()).Trim()

function Get-StableHash([string]$s) {
  $h = 0
  foreach ($ch in $s.ToCharArray()) { $h = ($h * 31 + [int]$ch) -band 0x7FFFFFFF }
  return $h
}

$paletteIndex = (Get-StableHash $body) % 6
$palettes = @(
  @('CBEDE2', '9AD4BE'), @('FFE1EB', 'F6AFC8'), @('FFF1C7', 'F3D488'),
  @('E7E1F8', 'C6B8EA'), @('D9EAFA', 'A9CCEE'), @('E2F3D6', 'B5DA9C')
)
$bgHex = '#' + $palettes[$paletteIndex][0]
$decoHex = '#' + $palettes[$paletteIndex][1]

$bmp = New-Object System.Drawing.Bitmap($W, $H)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias

$bgBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml($bgHex))
$g.FillRectangle($bgBrush, 0, 0, $W, $H)

# 1. large quote decoration
$decoColor = [System.Drawing.ColorTranslator]::FromHtml($decoHex)
$decoBrush = New-Object System.Drawing.SolidBrush($decoColor)
$quoteFont = New-Object System.Drawing.Font('Times New Roman', 150, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString([string][char]0x201C, $quoteFont, $decoBrush, 90, 140)

# 2. body text: size by length, wrap by measured width
$len = $body.Length
$fontSize = 84.0
if ($len -gt 160) { $fontSize = 56.0 } elseif ($len -gt 60) { $fontSize = 68.0 }
$bodyFont = New-Object System.Drawing.Font('Microsoft YaHei UI', $fontSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$textColor = [System.Drawing.ColorTranslator]::FromHtml('#3D4144')
$textBrush = New-Object System.Drawing.SolidBrush($textColor)

$padding = 120
$maxWidth = $W - $padding * 2
$lines = New-Object System.Collections.Generic.List[string]
$current = ""
foreach ($ch in $body.ToCharArray()) {
  if ($ch -eq "`n") { $lines.Add($current); $current = ""; continue }
  $candidate = $current + $ch
  if ($g.MeasureString($candidate, $bodyFont).Width -gt $maxWidth -and $current.Length -gt 0) {
    $lines.Add($current); $current = "$ch"
  } else { $current = $candidate }
}
if ($current.Length -gt 0) { $lines.Add($current) }

$maxLines = 12
if ($lines.Count -gt $maxLines) {
  $kept = $lines.GetRange(0, $maxLines)
  $kept[$maxLines - 1] = $kept[$maxLines - 1].TrimEnd() + [string][char]0x2026
  $lines = $kept
}
$lineHeight = [int]($fontSize * 1.45)
$totalHeight = $lines.Count * $lineHeight
$startY = 330
if ($totalHeight -lt ($H - 330 - 250)) { $startY = 330 + [int](($H - 330 - 250 - $totalHeight) / 2) }

# 3. highlight: deterministic pick of 1-3 in-text segments, pink rounded rect behind text
$highlightBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(204, 255, 158, 194))
$seed = Get-StableHash ($body + 'hl')
$count = 1 + ($seed % 3)
$gap = [Math]::Max(1, [int]($body.Length / ($count + 1)))
$hlRanges = @()
for ($k = 0; $k -lt $count; $k++) {
  $start = ($seed + $k * $gap * 3) % [Math]::Max(1, $body.Length)
  $hlRanges += ,@($start, 4)
}

$y = $startY
$globalIndex = 0
foreach ($line in $lines) {
  if ($line.Length -gt 0) {
    $lineWidth = $g.MeasureString($line, $bodyFont).Width
    # highlight segments that intersect this line
    $hlStarts = @()
    foreach ($range in $hlRanges) {
      $s = $range[0]; $e = $range[0] + $range[1]
      if ($s -lt ($globalIndex + $line.Length) -and $e -gt $globalIndex) {
        $from = [Math]::Max($s, $globalIndex) - $globalIndex
        $to = [Math]::Min($e, $globalIndex + $line.Length) - $globalIndex
        if ($from -lt $to -and $from -lt $line.Length) { $hlStarts += ,@($from, $to) }
      }
    }
    if ($hlStarts.Count -gt 0) {
      foreach ($hl in $hlStarts) {
        $before = $line.Substring(0, $hl[0])
        $mid = $line.Substring($hl[0], [Math]::Min($hl[1], $line.Length) - $hl[0])
        $xBefore = $g.MeasureString($before, $bodyFont).Width - 8
        $xMid = $g.MeasureString($mid, $bodyFont).Width + 16
        $g.FillRectangle($highlightBrush, [single]($padding + $xBefore), [single]($y + 4), [single]$xMid, [single]($lineHeight - 8))
      }
    }
    $g.DrawString($line, $bodyFont, $textBrush, $padding, $y)
  }
  $globalIndex += $line.Length
  $y += $lineHeight
}

# 4. bottom-right deco block
$g.FillRectangle($decoBrush, $W - 236, $H - 158, 88, 22)

# 5. watermark pill: white rounded pill + red brand + translucent account line
$brand = [string][char]0x5C0F + [string][char]0x7EA2 + [string][char]0x4E66  # xiao-hong-shu
$pillFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 34, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$pillTextSize = $g.MeasureString($brand, $pillFont)
$pillW = [int]($pillTextSize.Width + 52); $pillH = 66
$pillRight = $W - 76; $pillTop = $H - 250 + 46
$pillBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$pillPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$pillRadius = $pillH / 2
$pillPath.AddArc($pillRight - $pillW, $pillTop, $pillRadius * 2, $pillRadius * 2, 180, 90)
$pillPath.AddArc($pillRight - $pillRadius * 2, $pillTop, $pillRadius * 2, $pillRadius * 2, 270, 90)
$pillPath.AddArc($pillRight - $pillRadius * 2, $pillTop + $pillH - $pillRadius * 2, $pillRadius * 2, $pillRadius * 2, 0, 90)
$pillPath.AddArc($pillRight - $pillW, $pillTop + $pillH - $pillRadius * 2, $pillRadius * 2, $pillRadius * 2, 90, 90)
$pillPath.CloseFigure()
$g.FillPath($pillBrush, $pillPath)
$redBrush = New-Object System.Drawing.SolidBrush([System.Drawing.ColorTranslator]::FromHtml('#FF2442'))
$g.DrawString($brand, $pillFont, $redBrush, $pillRight - $pillW + 26, $pillTop + 10)

$idFont = New-Object System.Drawing.Font('Microsoft YaHei UI', 30, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$idBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(179, 255, 255, 255))
$idPrefix = [string][char]0x5C0F + [string][char]0x7EA2 + [string][char]0x4E66 + [string][char]0x53F7 + ' '
$authorText = $Author.Trim()
if ($authorText.Length -eq 0) { $authorText = '5541459619' }
$idText = $idPrefix + (Get-StableHash $authorText)
$g.DrawString($idText, $idFont, $idBrush, $pillRight - 300, $pillTop + $pillH + 40)

$bmp.Save($OutFile, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Output "OK $OutFile"
