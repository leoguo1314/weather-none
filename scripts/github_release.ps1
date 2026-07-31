# GitHub Release: create release, upload APK, verify SHA-256
$ErrorActionPreference = "Stop"

$projectRoot = Join-Path $PSScriptRoot ".."
$token = (Get-Content (Join-Path $projectRoot "local.properties") | Where-Object { $_ -match '^github_token=' } | ForEach-Object { $_ -replace '^github_token=', '' } | Select-Object -First 1).Trim()
$version = $args[0]
if (-not $version) { Write-Error "Usage: github_release.ps1 <version> (e.g. 3.5.13)"; exit 1 }

$tag = "v$version"
$repo = "qnmlgbd250/weather-none"
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\release\skypulse-v$version.apk"
$apkName = "skypulse-v$version.apk"

if (-not (Test-Path $apkPath)) { Write-Error "APK not found: $apkPath"; exit 1 }

$headers = @{ Authorization = "token $token" }

# 1. Local SHA-256 (record BEFORE upload)
$localHash = (Get-FileHash -Algorithm SHA256 -Path $apkPath).Hash
$localSize = (Get-Item $apkPath).Length
Write-Host "[1/5] Local APK: $apkName size=$localSize sha256=$localHash" -ForegroundColor Cyan

# 2. Create release (UTF-8 body)
$desc = -join ([char[]](0x4fee,0x590d,0x5df2,0x77e5,0x95ee,0x9898))  # 修复已知问题
$body = @{ tag_name = $tag; name = $tag; body = $desc; draft = $false; prerelease = $false } | ConvertTo-Json -Compress
$bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Method Post -Headers $headers -Body $bytes -ContentType "application/json; charset=utf-8"
Write-Host "[2/5] Release created: $($release.html_url)" -ForegroundColor Cyan

# 3. Upload APK asset
$uploadUrl = "https://uploads.github.com/repos/$repo/releases/$($release.id)/assets?name=$apkName"
$asset = Invoke-RestMethod -Uri $uploadUrl -Method Post -Headers @{ Authorization = "token $token"; "Content-Type" = "application/vnd.android.package-archive" } -InFile $apkPath
Write-Host "[3/5] Asset uploaded: $($asset.name)" -ForegroundColor Cyan

# 4. Download back and verify SHA-256
$tmp = Join-Path $env:TEMP $apkName
Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $tmp -Headers $headers
$remoteHash = (Get-FileHash -Algorithm SHA256 -Path $tmp).Hash
$remoteSize = (Get-Item $tmp).Length
Remove-Item $tmp
Write-Host "[4/5] Downloaded asset: size=$remoteSize sha256=$remoteHash" -ForegroundColor Cyan

if ($localHash -ne $remoteHash -or $localSize -ne $remoteSize) {
    Write-Error "MISMATCH! Local sha256=$localHash size=$localSize | Remote sha256=$remoteHash size=$remoteSize"
    exit 1
}
Write-Host "[5/5] VERIFIED: local and remote APK are identical" -ForegroundColor Green
Write-Host "Release URL: $($release.html_url)" -ForegroundColor Green
