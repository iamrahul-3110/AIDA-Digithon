# Usage: .\scripts\new-token.ps1 -Name demo-token -Projects "SHOP,OPS" -Scopes "mcp,admin"
param (
    [Parameter(Mandatory=$true)]
    [string]$Name,
    [Parameter(Mandatory=$true)]
    [string]$Projects,
    [Parameter(Mandatory=$true)]
    [string]$Scopes
)

$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$raw = -join ($bytes | ForEach-Object { "{0:x2}" -f $_ })

$sha = [System.Security.Cryptography.SHA256]::Create()
$hashBytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($raw))
$hash = -join ($hashBytes | ForEach-Object { "{0:x2}" -f $_ })

Write-Host ""
Write-Host "=== NEW TOKEN ===" -ForegroundColor Green
Write-Host "Raw token (show once, store securely):"
Write-Host "  $raw" -ForegroundColor Yellow
Write-Host ""
Write-Host "Add this to tokens.yml:"
Write-Host "---"
Write-Host "- name: $Name"
Write-Host "  sha256: $hash"
Write-Host "  projects:"
$Projects -split "," | ForEach-Object { Write-Host "    - $($_.Trim())" }
Write-Host "  scopes:"
$Scopes -split "," | ForEach-Object { Write-Host "    - $($_.Trim())" }
Write-Host ""
Write-Host "To use with Claude Code:"
Write-Host "  export AIDA_TOKEN=$raw"
Write-Host "  claude mcp add --transport http aida http://localhost:8080/mcp --header `"Authorization: Bearer `$AIDA_TOKEN`""
