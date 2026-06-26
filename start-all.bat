@echo off
setlocal
pushd "%~dp0"

echo ===========================================
echo   Yerbanalytics -- Verificando requisitos...
echo ===========================================
echo.

:: Verificar Java 17+
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java no encontrado.
    echo         Instala Java 17 desde: https://adoptium.net
    popd & pause & exit /b 1
)
for /f "tokens=3" %%v in ('java -version 2^>^&1') do (
    set "JAVA_VER_STR=%%v"
    goto :check_java_ver
)
:check_java_ver
set "JAVA_VER_STR=%JAVA_VER_STR:"=%"
for /f "delims=." %%m in ("%JAVA_VER_STR%") do set "JAVA_MAJOR=%%m"
if %JAVA_MAJOR% LSS 17 (
    echo [ERROR] Se requiere Java 17+. Version detectada: %JAVA_VER_STR%
    echo         Descarga Java 17 desde: https://adoptium.net
    popd & pause & exit /b 1
)
echo [OK] Java %JAVA_VER_STR%

:: Verificar Node.js
node --version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Node.js no encontrado.
    echo         Instala desde: https://nodejs.org
    popd & pause & exit /b 1
)
for /f %%v in ('node --version') do set "NODE_VER=%%v"
echo [OK] Node.js %NODE_VER%

:: Verificar Docker
docker info >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Docker no esta corriendo. Inicia Docker Desktop primero.
    popd & pause & exit /b 1
)
echo [OK] Docker
echo.

:: npm install si falta node_modules
if not exist "Desarrollo\frontend\node_modules" (
    echo [setup] node_modules no encontrado. Instalando dependencias del frontend...
    pushd "Desarrollo\frontend"
    npm install
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Fallo npm install.
        popd & popd & pause & exit /b 1
    )
    popd
    echo [OK] Dependencias instaladas.
    echo.
)

echo ===========================================
echo   Iniciando servicios...
echo ===========================================
echo.

:: 1. Docker
echo [1/3] Iniciando contenedores Docker (PostgreSQL + Mosquitto)...
docker-compose up -d
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Fallo al iniciar Docker. Asegurate de que Docker Desktop este corriendo.
    popd & pause & exit /b 1
)
echo Esperando 3 segundos para que la base de datos se inicialice...
timeout /t 3 /nobreak > nul

:: 2. Backend
echo [2/3] Iniciando Backend (Spring Boot)...
start "Yerbanalytics Backend" /D "%~dp0Desarrollo\backend" cmd /k "mvnw.cmd spring-boot:run"

:: 3. Frontend
echo [3/3] Iniciando Frontend (React + Vite)...
start "Yerbanalytics Frontend" /D "%~dp0Desarrollo\frontend" cmd /k "npm run dev"

echo.
echo ===========================================
echo   Servicios iniciando en segundo plano.
echo   - Backend:  http://localhost:8000
echo   - Frontend: http://localhost:5173
echo ===========================================
echo.
popd
pause
endlocal
