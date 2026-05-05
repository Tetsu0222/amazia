$root = $PSScriptRoot

& "$root\stop-local.ps1"

Write-Host "Waiting for ports to release..."
Start-Sleep -Seconds 3

& "$root\start-local.ps1"
