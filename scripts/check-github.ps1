$ErrorActionPreference = "Stop"

. "$PSScriptRoot\env.ps1"

$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    throw "GitHub CLI not found. Install it with winget install GitHub.cli, then open a new PowerShell window."
}

Write-Host "GitHub CLI: $($gh.Source)"
& gh auth status

Write-Host ""
Write-Host "Repository:"
& gh repo view wildtwins/DeerDeck-public --json nameWithOwner,visibility,defaultBranchRef,url

Write-Host ""
Write-Host "Raw git remote check:"
$env:GIT_TERMINAL_PROMPT = "0"
& git ls-remote --heads origin
if ($LASTEXITCODE -ne 0) {
    throw "Raw git remote check failed. GitHub CLI auth is working, but git HTTPS credentials/TLS are not ready in this shell."
}
