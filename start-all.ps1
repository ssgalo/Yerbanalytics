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

# Espera a que un puerto acepte conexiones. Abrir el navegador apenas se lanza el proceso
# muestra un "no se puede conectar": Vite tarda en estar escuchando.
#
# El margen es amplio a proposito. En caliente Vite levanta en ~2 s, pero un arranque en
# frio -la primera vez, o despues de tocar vite.config.ts, cuando reconstruye la cache de
# dependencias- puede tardar bastante mas, y encima compite con Maven compilando el backend
# al mismo tiempo.
# Prueba las DOS direcciones de loopback, y no "localhost", porque cada servidor elige una
# familia distinta: Vite escucha en ::1 (IPv6) y el simulador en ambas. Windows PowerShell 5.1
# —el que ejecuta este script— resuelve "localhost" solo a 127.0.0.1 y nunca prueba ::1, asi
# que dar por caido a Vite era el resultado garantizado. El navegador no tiene el problema:
# prueba las dos.
function Test-PuertoLocal {
    param([int]$Puerto)
    foreach ($ip in @("127.0.0.1", "::1")) {
        try {
            $familia = if ($ip -eq "::1") { [Net.Sockets.AddressFamily]::InterNetworkV6 }
                       else { [Net.Sockets.AddressFamily]::InterNetwork }
            $cliente = New-Object Net.Sockets.TcpClient($familia)
            $cliente.Connect($ip, $Puerto)
            $cliente.Close()
            return $true
        } catch { }
    }
    return $false
}

function Wait-Puerto {
    param([int]$Puerto, [int]$Segundos = 180)
    $limite = (Get-Date).AddSeconds($Segundos)
    $vueltas = 0
    while ((Get-Date) -lt $limite) {
        if (Test-PuertoLocal $Puerto) {
            if ($vueltas -ge 10) { Write-Host "" }
            return $true
        }
        # Un punto cada 5 segundos, para que no parezca colgado.
        if ($vueltas -gt 0 -and $vueltas % 10 -eq 0) { Write-Host "." -NoNewline }
        $vueltas++
        Start-Sleep -Milliseconds 500
    }
    if ($vueltas -ge 10) { Write-Host "" }
    return $false
}

$frontendDir  = Join-Path $ScriptRoot "Desarrollo\frontend"
$backendDir   = Join-Path $ScriptRoot "Desarrollo\backend"
# El simulador es un proyecto aparte y OPCIONAL: si la carpeta no esta, se omite y el
# sistema arranca igual, esperando telemetria de hardware real.
$simuladorDir = Join-Path $ScriptRoot "Desarrollo\simulador"
$haySimulador = Test-Path $simuladorDir

# npm install si falta node_modules
if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
    Write-Host "[setup] Instalando dependencias del frontend..." -ForegroundColor Yellow
    Push-Location $frontendDir
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Fallo npm install del frontend." -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
    Write-Host "[OK] Dependencias del frontend instaladas." -ForegroundColor Green
    Write-Host ""
}

# El simulador tiene sus propias dependencias: no comparte node_modules con el frontend.
if ($haySimulador -and -not (Test-Path (Join-Path $simuladorDir "node_modules"))) {
    Write-Host "[setup] Instalando dependencias del simulador..." -ForegroundColor Yellow
    Push-Location $simuladorDir
    npm install
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[AVISO] Fallo npm install del simulador. Se arranca sin el." -ForegroundColor Yellow
        $haySimulador = $false
    }
    Pop-Location
    if ($haySimulador) {
        Write-Host "[OK] Dependencias del simulador instaladas." -ForegroundColor Green
    }
    Write-Host ""
}

# Sin .env el dashboard arranca en modo demo (mock) y no consultaria al backend que estamos
# levantando. Se crea a partir de la plantilla, que ya viene con VITE_DATA_SOURCE=http.
$frontEnv = Join-Path $frontendDir ".env"
if (-not (Test-Path $frontEnv)) {
    Copy-Item (Join-Path $frontendDir "env.example") $frontEnv
    Write-Host "[setup] .env del frontend creado desde env.example (modo backend real)." -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "===========================================" -ForegroundColor Green
Write-Host "  Iniciando servicios...                   " -ForegroundColor Green
Write-Host "===========================================" -ForegroundColor Green
Write-Host ""

# 1. Docker
Write-Host "[1/5] Iniciando contenedores Docker (PostgreSQL + Mosquitto)..." -ForegroundColor Cyan
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
Write-Host "[2/5] Iniciando Backend (Spring Boot)..." -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory $backendDir -ArgumentList "-NoExit", "-Command", "Write-Host '--- Yerbanalytics Backend ---' -ForegroundColor Yellow; .\mvnw.cmd spring-boot:run"

# 3. Frontend
Write-Host "[3/5] Iniciando Frontend (React + Vite)..." -ForegroundColor Cyan
Start-Process powershell -WorkingDirectory $frontendDir -ArgumentList "-NoExit", "-Command", "Write-Host '--- Yerbanalytics Frontend ---' -ForegroundColor Yellow; npm run dev"

# 4. Simulador de hardware (proyecto aparte, con su propio servidor y su propio MQTT).
#    Es una comodidad de desarrollo: el sistema no depende de el, y borrar su carpeta
#    simplemente hace que este paso se omita.
if ($haySimulador) {
    Write-Host "[4/5] Iniciando Simulador de hardware (Node + MQTT)..." -ForegroundColor Cyan
    Start-Process powershell -WorkingDirectory $simuladorDir -ArgumentList "-NoExit", "-Command", "Write-Host '--- Yerbanalytics Simulador ---' -ForegroundColor Yellow; npm run dev"
} else {
    Write-Host "[4/5] Simulador no instalado (Desarrollo\simulador). Se omite." -ForegroundColor DarkGray
    Write-Host "      El sistema queda esperando telemetria de hardware real." -ForegroundColor DarkGray
}


# 5. Navegador. Se espera a que cada servidor escuche antes de abrir su pestana.
Write-Host ""
Write-Host "[5/5] Esperando a que los frontends respondan para abrir el navegador..." -ForegroundColor Cyan

if (Wait-Puerto 5173) {
    Start-Process "http://localhost:5173"
    Write-Host "      Dashboard abierto." -ForegroundColor Green
    # Un respiro para que, si el navegador estaba cerrado, la segunda URL entre como
    # pestana de la misma ventana y no como una ventana nueva.
    Start-Sleep -Seconds 2
} else {
    Write-Host "[AVISO] El dashboard no respondio a tiempo. Abrilo a mano: http://localhost:5173" -ForegroundColor Yellow
}

if ($haySimulador) {
    if (Wait-Puerto 5180) {
        Start-Process "http://localhost:5180"
        Write-Host "      Simulador abierto." -ForegroundColor Green
    } else {
        Write-Host "[AVISO] El simulador no respondio a tiempo. Abrilo a mano: http://localhost:5180" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "===========================================" -ForegroundColor Green
Write-Host "  Servicios iniciando en segundo plano.    " -ForegroundColor Green
Write-Host "  - Backend:    http://localhost:8000      " -ForegroundColor Green
Write-Host "  - Frontend:   http://localhost:5173      " -ForegroundColor Green
if ($haySimulador) {
    Write-Host "  - Simulador:  http://localhost:5180      " -ForegroundColor Green
}
Write-Host "===========================================" -ForegroundColor Green
Write-Host ""
