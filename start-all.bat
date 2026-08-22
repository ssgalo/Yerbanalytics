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

:: El simulador es un proyecto aparte y OPCIONAL: si la carpeta no esta, se omite y el
:: sistema arranca igual, esperando telemetria de hardware real.
set "HAY_SIMULADOR=1"
if not exist "Desarrollo\simulador" set "HAY_SIMULADOR=0"

:: npm install si falta node_modules
if not exist "Desarrollo\frontend\node_modules" (
    echo [setup] Instalando dependencias del frontend...
    pushd "Desarrollo\frontend"
    npm install
    rem `if errorlevel` y no `%ERRORLEVEL%`: dentro de un bloque, la variable se expande al
    rem parsear el bloque (o sea, ANTES de que npm corra) y el chequeo no sirve de nada.
    if errorlevel 1 (
        echo [ERROR] Fallo npm install del frontend.
        popd & popd & pause & exit /b 1
    )
    popd
    echo [OK] Dependencias del frontend instaladas.
    echo.
)

:: El simulador tiene sus propias dependencias: no comparte node_modules con el frontend.
if "%HAY_SIMULADOR%"=="1" if not exist "Desarrollo\simulador\node_modules" (
    echo [setup] Instalando dependencias del simulador...
    pushd "Desarrollo\simulador"
    npm install
    if errorlevel 1 (
        echo [AVISO] Fallo npm install del simulador. Se arranca sin el.
        set "HAY_SIMULADOR=0"
    )
    popd
    echo.
)

:: Sin .env el dashboard arranca en modo demo (mock) y no consultaria al backend que estamos
:: levantando. Se crea a partir de la plantilla, que ya viene con VITE_DATA_SOURCE=http.
if not exist "Desarrollo\frontend\.env" (
    copy /y "Desarrollo\frontend\env.example" "Desarrollo\frontend\.env" >nul
    echo [setup] .env del frontend creado desde env.example ^(modo backend real^).
    echo.
)

:: Si el servicio de inferencia existe pero no tiene .env, copiar el ejemplo.
:: Sin .env el contenedor crashea porque MODEL_PATH es obligatoria.
set "HAY_INFERENCIA=0"
if exist "Desarrollo\servicio-inferencia" set "HAY_INFERENCIA=1"
if "%HAY_INFERENCIA%"=="1" if not exist "Desarrollo\servicio-inferencia\.env" (
    copy /y "Desarrollo\servicio-inferencia\.env.example" "Desarrollo\servicio-inferencia\.env" >nul
    echo [setup] .env del servicio de inferencia creado desde .env.example.
    echo         IMPORTANTE: edita MODEL_PATH y MODEL_CLASSES en:
    echo         %~dp0Desarrollo\servicio-inferencia\.env
    echo.
)

echo ===========================================
echo   Iniciando servicios...
echo ===========================================
echo.

:: 1. Docker
echo [1/5] Iniciando contenedores Docker (PostgreSQL + Mosquitto + Inferencia)...
docker-compose up -d
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Fallo al iniciar Docker. Asegurate de que Docker Desktop este corriendo.
    popd & pause & exit /b 1
)
echo Esperando 3 segundos para que la base de datos se inicialice...
timeout /t 3 /nobreak > nul

:: 2. Backend
echo [2/5] Iniciando Backend (Spring Boot)...
start "Yerbanalytics Backend" /D "%~dp0Desarrollo\backend" cmd /k "mvnw.cmd spring-boot:run"

:: 3. Frontend
echo [3/5] Iniciando Frontend (React + Vite)...
start "Yerbanalytics Frontend" /D "%~dp0Desarrollo\frontend" cmd /k "npm run dev"

:: 4. Simulador de hardware (proyecto aparte, con su propio servidor y su propio MQTT).
::    Es una comodidad de desarrollo: el sistema no depende de el, y borrar su carpeta
::    simplemente hace que este paso se omita.
if "%HAY_SIMULADOR%"=="1" (
    echo [4/5] Iniciando Simulador de hardware ^(Node + MQTT^)...
    start "Yerbanalytics Simulador" /D "%~dp0Desarrollo\simulador" cmd /k "npm run dev"
) else (
    echo [4/5] Simulador no instalado ^(Desarrollo\simulador^). Se omite.
    echo       El sistema queda esperando telemetria de hardware real.
)

:: 5. Navegador. Se prueban las DOS direcciones de loopback y no "localhost": Vite escucha en
::    ::1 (IPv6) y Windows PowerShell resuelve "localhost" solo a 127.0.0.1, con lo cual daba
::    a Vite por caido siempre. El navegador no tiene el problema: prueba las dos.
::    Se espera a que cada servidor escuche antes de abrir su pestana: abrirla
::    apenas se lanza el proceso muestra un "no se puede conectar". El margen es amplio a
::    proposito: en caliente Vite levanta en ~2 s, pero un arranque en frio -la primera vez, o
::    despues de tocar vite.config.ts- tarda bastante mas, y compite con Maven compilando.
echo.
echo [5/5] Esperando a que los frontends respondan para abrir el navegador...
powershell -NoProfile -Command "function T([int]$n){foreach($ip in @('127.0.0.1','::1')){try{$f=if($ip -eq '::1'){[Net.Sockets.AddressFamily]::InterNetworkV6}else{[Net.Sockets.AddressFamily]::InterNetwork};$c=New-Object Net.Sockets.TcpClient($f);$c.Connect($ip,$n);$c.Close();return $true}catch{}}return $false}; $p=@(5173); if ('%HAY_SIMULADOR%' -eq '1') { $p += 5180 }; foreach ($x in $p) { $fin=(Get-Date).AddSeconds(180); while ((Get-Date) -lt $fin) { if (T $x) { break }; Start-Sleep -Milliseconds 500 } }"

start "" "http://localhost:5173"
if "%HAY_SIMULADOR%"=="1" (
    rem Un respiro para que, si el navegador estaba cerrado, la segunda URL entre como
    rem pestana de la misma ventana y no como una ventana nueva.
    timeout /t 2 /nobreak > nul
    start "" "http://localhost:5180"
)

echo.
echo ===========================================
echo   Servicios iniciando en segundo plano.
echo   - Backend:    http://localhost:8000
echo   - Frontend:   http://localhost:5173
if "%HAY_SIMULADOR%"=="1" echo   - Simulador:  http://localhost:5180
if "%HAY_INFERENCIA%"=="1" (
    echo   - Inferencia: daemon Docker. Ver logs con:
    echo                 docker logs -f servicio-inferencia
)
echo ===========================================
echo.
popd
pause
endlocal
