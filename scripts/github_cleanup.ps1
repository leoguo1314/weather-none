# Cleanup: keep only the most recent 7 versions (releases + tags), delete the rest
$ErrorActionPreference = "Stop"

$projectRoot = Join-Path $PSScriptRoot ".."
$token = (Get-Content (Join-Path $projectRoot "local.properties") | Where-Object { $_ -match '^github_token=' } | ForEach-Object { $_ -replace '^github_token=', '' } | Select-Object -First 1).Trim()
$repo = "qnmlgbd250/weather-none"
$headers = @{ Authorization = "token $token" }

function Parse-Version($tag) {
    if ($tag -notmatch '^v?(\d+)\.(\d+)\.(\d+)$') { return $null }
    return [PSCustomObject]@{ Major = [int]$Matches[1]; Minor = [int]$Matches[2]; Patch = [int]$Matches[3] }
}

# Collect all tags
$tags = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/tags?per_page=100" -Headers $headers
$tagNames = @($tags | ForEach-Object { $_.name })

# Collect all releases
$releases = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases?per_page=100" -Headers $headers
$releaseByTag = @{}
foreach ($r in $releases) { $releaseByTag[$r.tag_name] = $r.id }

# Sort all versions by semver descending
$allVersions = $tagNames | ForEach-Object {
    $v = Parse-Version $_
    if ($v) { [PSCustomObject]@{ Tag = $_; Major = $v.Major; Minor = $v.Minor; Patch = $v.Patch } }
} | Sort-Object -Property Major, Minor, Patch -Descending

$keep = @($allVersions | Select-Object -First 7)
$remove = @($allVersions | Select-Object -Skip 7)

Write-Host "Keep (7): $($keep.Tag -join ', ')" -ForegroundColor Cyan
Write-Host "Remove: $($remove.Tag -join ', ')" -ForegroundColor Yellow

foreach ($v in $remove) {
    # Delete release if exists
    if ($releaseByTag.ContainsKey($v.Tag)) {
        $rid = $releaseByTag[$v.Tag]
        Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/$rid" -Method Delete -Headers $headers
        Write-Host "Deleted release $($v.Tag)" -ForegroundColor Green
    } else {
        Write-Host "No release for $($v.Tag), skip release deletion" -ForegroundColor DarkYellow
    }
    # Delete tag
    Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/git/refs/tags/$($v.Tag)" -Method Delete -Headers $headers
    Write-Host "Deleted tag $($v.Tag)" -ForegroundColor Green
}

Write-Host "Cleanup done. Remaining versions: $((@($keep.Tag) -join ', '))" -ForegroundColor Cyan
