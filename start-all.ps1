Write-Host "===========================================" -ForegroundColor Green
Write-Host "  Starting Yerbanalytics Services...       " -ForegroundColor Green
Write-Host "===========================================" -ForegroundColor Green
Write-Host ""

# 1. Start Docker
Write-Host "[1/3] Starting Docker containers (PostgreSQL & Mosquitto)..." -ForegroundColor Cyan
docker-compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to start Docker containers. Make sure Docker Desktop is running."
    Exit $LASTEXITCODE
}

# Wait for DB
Write-Host "Waiting 3 seconds for database to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

# 2. Start Backend
Write-Host "[2/3] Launching Spring Boot Backend in a new window..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host '--- Starting Yerbanalytics Backend ---' -ForegroundColor Yellow; cd Desarrollo/backend; mvn spring-boot:run"

# 3. Start Frontend
Write-Host "[3/3] Launching React + Vite Frontend in a new window..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Write-Host '--- Starting Yerbanalytics Frontend ---' -ForegroundColor Yellow; cd Desarrollo/frontend; npm run dev"

Write-Host ""
Write-Host "===========================================" -ForegroundColor Green
Write-Host "  All services are booting up!             " -ForegroundColor Green
Write-Host "  - Backend: http://localhost:8000         " -ForegroundColor Green
Write-Host "  - Frontend: http://localhost:5173        " -ForegroundColor Green
Write-Host "===========================================" -ForegroundColor Green
Write-Host ""
