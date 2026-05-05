$root = $PSScriptRoot

Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\amazia-core'; mvn spring-boot:run '-Dspring-boot.run.profiles=local'"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\amazia-console'; php artisan serve"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\amazia-market'; npm run dev"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$root\amazia-console\resources\vue'; npm run dev"
