$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $repoRoot "play-assets"
$buckPhotoPath = Join-Path $repoRoot "app\src\main\res\drawable-nodpi\ic_launcher_buck.png"

if (-not (Test-Path -LiteralPath $buckPhotoPath)) {
    throw "Launcher photo not found: $buckPhotoPath"
}

New-Item -ItemType Directory -Force -Path $assetRoot | Out-Null

$deepForest = [System.Drawing.ColorTranslator]::FromHtml("#172119")
$softWhite = [System.Drawing.ColorTranslator]::FromHtml("#F5F2EA")
$mutedSage = [System.Drawing.ColorTranslator]::FromHtml("#C5CFBF")
$copper = [System.Drawing.ColorTranslator]::FromHtml("#B97845")

function New-Canvas([int]$Width, [int]$Height, [System.Drawing.Color]$Color) {
    $bitmap = [System.Drawing.Bitmap]::new(
        $Width,
        $Height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.Clear($Color)
    return @{ Bitmap = $bitmap; Graphics = $graphics }
}

function Draw-Cover(
    [System.Drawing.Graphics]$Graphics,
    [System.Drawing.Image]$Image,
    [System.Drawing.Rectangle]$Destination
) {
    $scale = [Math]::Max($Destination.Width / $Image.Width, $Destination.Height / $Image.Height)
    $sourceWidth = $Destination.Width / $scale
    $sourceHeight = $Destination.Height / $scale
    $sourceX = ($Image.Width - $sourceWidth) / 2
    $sourceY = ($Image.Height - $sourceHeight) / 2

    $Graphics.DrawImage(
        $Image,
        $Destination,
        $sourceX,
        $sourceY,
        $sourceWidth,
        $sourceHeight,
        [System.Drawing.GraphicsUnit]::Pixel
    )
}

$buckPhoto = [System.Drawing.Image]::FromFile($buckPhotoPath)
try {
    $icon = New-Canvas -Width 512 -Height 512 -Color ([System.Drawing.Color]::Transparent)
    try {
        Draw-Cover `
            -Graphics $icon.Graphics `
            -Image $buckPhoto `
            -Destination ([System.Drawing.Rectangle]::new(0, 0, 512, 512))
        $iconPath = Join-Path $assetRoot "app-icon-512.png"
        $icon.Bitmap.Save($iconPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $icon.Graphics.Dispose()
        $icon.Bitmap.Dispose()
    }

    $feature = New-Canvas -Width 1024 -Height 500 -Color $deepForest
    $whiteBrush = [System.Drawing.SolidBrush]::new($softWhite)
    $mutedBrush = [System.Drawing.SolidBrush]::new($mutedSage)
    $copperBrush = [System.Drawing.SolidBrush]::new($copper)
    $titleFont = [System.Drawing.Font]::new(
        "Segoe UI",
        43,
        [System.Drawing.FontStyle]::Bold,
        [System.Drawing.GraphicsUnit]::Pixel
    )
    $subtitleFont = [System.Drawing.Font]::new(
        "Segoe UI",
        23,
        [System.Drawing.FontStyle]::Regular,
        [System.Drawing.GraphicsUnit]::Pixel
    )
    $featureFont = [System.Drawing.Font]::new(
        "Segoe UI",
        19,
        [System.Drawing.FontStyle]::Regular,
        [System.Drawing.GraphicsUnit]::Pixel
    )
    $fadeBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.Rectangle]::new(260, 0, 300, 500),
        [System.Drawing.Color]::FromArgb(0, $deepForest),
        $deepForest,
        0.0
    )
    try {
        Draw-Cover `
            -Graphics $feature.Graphics `
            -Image $buckPhoto `
            -Destination ([System.Drawing.Rectangle]::new(0, 0, 520, 500))
        $feature.Graphics.FillRectangle($fadeBrush, 260, 0, 300, 500)
        $feature.Graphics.FillRectangle($copperBrush, 548, 126, 72, 6)
        $feature.Graphics.DrawString("Withrow DeerDeck", $titleFont, $whiteBrush, 548, 150)
        $feature.Graphics.DrawString(
            "A lifetime of wildlife observations,",
            $subtitleFont,
            $mutedBrush,
            550,
            228
        )
        $feature.Graphics.DrawString(
            "kept in your hands.",
            $subtitleFont,
            $mutedBrush,
            550,
            260
        )
        $feature.Graphics.DrawString(
            "Sightings  |  Conditions  |  Media  |  Trends  |  Backups",
            $featureFont,
            $whiteBrush,
            550,
            326
        )
        $featurePath = Join-Path $assetRoot "feature-graphic-1024x500.png"
        $feature.Bitmap.Save($featurePath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $whiteBrush.Dispose()
        $mutedBrush.Dispose()
        $copperBrush.Dispose()
        $titleFont.Dispose()
        $subtitleFont.Dispose()
        $featureFont.Dispose()
        $fadeBrush.Dispose()
        $feature.Graphics.Dispose()
        $feature.Bitmap.Dispose()
    }
} finally {
    $buckPhoto.Dispose()
}

Write-Host "Generated icon and feature graphic in $assetRoot. Device screenshots are deliberately excluded."
