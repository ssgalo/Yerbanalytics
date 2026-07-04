$ScriptRoot = $PSScriptRoot

Write-Host "===========================================" -ForegroundColor Green
Write-Host "  Yerbanalytics -- Verificando requisitos... " -ForegroundColor Green
Write-Host "===========================================" -ForegroundColor Green
Write-Host ""

# Verificar Java 17+
$javaOutput = java -version 2>&1 | Select-Object -First 1
if ($javaOutput -match '"([\d\.]+)"') {
    $javaVerStr = $matches[1]
    $javaMajor = [int]($javaVerStr -split '\.')[0]
    if ($javaMajor -lt 17) {
        Write-Host "[ERROR] Se requiere Java 17+. Version detectada: $javaVerStr" -ForegroundColor Red
        Write-Host "        Descarga Java 17 desde: https://adoptium.net" -ForegroundColor Red
        exit 1
    }
    Write-Host "[OK] Java $javaVerStr" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Java no encontrado." -ForegroundColor Red
    Write-Host "        Instala Java 17 desde: https://adoptium.net" -ForegroundColor Red
    exit 1
}

# Verificar Node.js
$nodeVer = node --version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Node.js no encontrado." -ForegroundColor Red
    Write-Host "        Instala desde: https://nodejs.org" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Node.js $nodeVer" -ForegroundColor Green

# Verificar Docker
$null = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Docker no esta corriendo. Inicia Docker Desktop primero." -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Docker" -ForegroundColor Green
Write-Host ""

# npm install si falta node_modules
$frontendDir = Join-Path $ScriptRoot "Desarrollo\frontend"
if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
    Write-Host "[setup] node_modules no encontrado. Instalando dependencias del frontend..." -ForegroundColor Yellow
    Push-Location $frontendDir
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Fallo npm install." -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
    Write-Host "[OK] Dependencias instaladas." -ForegroundColor Green
    Write-Host ""
}

Write-Host "===========================================" -ForegroundColor Green
Write-Host "  Iniciando servicios...                   " -ForegroundColor Green
Write-Host "===========================================" -ForegroundColor Green
Write-Host ""

# 1. Docker
Write-Host "[1/4] Iniciando contenedores Docker (PostgreSQL + Mosquitto)..." -ForegroundColor Cyan
Push-Location $ScriptRoot
docker-compose up -d
$dcExit = $LASTEXITCODE
Pop-Location
if ($dcExit -ne 0) {
    Write-Host "[ERROR] Fallo al iniciar Docker. Asegurate de que Docker Desktop este corriendo." -ForegroundColor Red
    exit 1
}
Write-Host "Esperando 3 segundos para que la base de datos se inicialice..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

# 2. Backend
Write-Host "[2/4] Iniciando Backend (Spring Boot)..." -ForegroundColor Cyan
$backendDir = Join-Path $ScriptRoot "Desarrollo\backend"
Start-Process powershell -WorkingDirectory $backendDir -ArgumentList "-NoExit", "-Command", "Write-Host '--- Yerbanalytics Backend ---' -ForegroundColor Yellow; .\mvnw.cmd spring-boot:run"

# 3. Frontend
Write-Host "[3/4] Iniciando Frontend (React + Vite)..." -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList "-NoExit", "-Command", "Write-Host '--- Yerbanalytics Frontend ---' -ForegroundColor Yellow; npm run dev"

# 4. Simulador de sensores (app aparte)
Write-Host "[4/4] Iniciando Simulador de sensores (React + Vite)..." -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList "-NoExit", "-Command", "Write-Host '--- Yerbanalytics Simulador ---' -ForegroundColor Yellow; npm run dev:sim"

Write-Host ""
Write-Host "===========================================" -ForegroundColor Green
Write-Host "  Servicios iniciando en segundo plano.    " -ForegroundColor Green
Write-Host "  - Backend:    http://localhost:8000      " -ForegroundColor Green
Write-Host "  - Frontend:   http://localhost:5173      " -ForegroundColor Green
Write-Host "  - Simulador:  http://localhost:5180      " -ForegroundColor Green
Write-Host "===========================================" -ForegroundColor Green
Write-Host ""
