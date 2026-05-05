$ports = @(8080, 8000, 5173, 5174)

foreach ($port in $ports) {
    $connections = netstat -ano | Select-String ":$port\s" | Where-Object { $_ -match "LISTENING" }
    foreach ($line in $connections) {
        $parts = ($line.ToString().Trim() -split '\s+') | Where-Object { $_ -ne '' }
        $procId = $parts[-1]
        if ($procId -match '^\d+$') {
            Stop-Process -Id ([int]$procId) -Force -ErrorAction SilentlyContinue
            Write-Host "Stopped PID $procId (port $port)"
        }
    }
}

Write-Host "Done."
