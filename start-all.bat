@echo off
echo ===========================================
echo   Starting Yerbanalytics Services...
echo ===========================================
echo.

echo [1/3] Starting Docker containers (PostgreSQL & Mosquitto)...
docker-compose up -d
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to start Docker containers. Make sure Docker Desktop is running.
    pause
    exit /b %ERRORLEVEL%
)

echo Waiting 3 seconds for the database to warm up...
timeout /t 3 /nobreak > nul

echo [2/3] Launching Spring Boot Backend in a new window...
start "Yerbanalytics Backend" cmd /k "echo Starting Backend... && cd Desarrollo\backend && mvn spring-boot:run"

echo [3/3] Launching React + Vite Frontend in a new window...
start "Yerbanalytics Frontend" cmd /k "echo Starting Frontend... && cd Desarrollo\frontend && npm run dev"

echo.
echo ===========================================
echo   All services are booting up!
echo   - Backend: http://localhost:8000
echo   - Frontend: http://localhost:5173 (usually)
echo ===========================================
echo.
pause
