@echo off
setlocal

set "MAVEN_VERSION=3.9.6"
set "WRAPPER_DIR=%USERPROFILE%\.m2\wrapper"
set "MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%"
set "MAVEN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"
set "MAVEN_ZIP=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%.zip"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [mvnw] Maven %MAVEN_VERSION% no encontrado. Descargando...
    if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri $env:MAVEN_URL -OutFile $env:MAVEN_ZIP -UseBasicParsing"
    if %ERRORLEVEL% neq 0 (
        echo [mvnw] ERROR: Fallo al descargar Maven. Verificar conexion a internet.
        exit /b 1
    )
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path $env:MAVEN_ZIP -DestinationPath $env:WRAPPER_DIR -Force"
    del "%MAVEN_ZIP%" >nul 2>&1
    echo [mvnw] Maven %MAVEN_VERSION% listo.
    echo.
)

set "PATH=%MAVEN_HOME%\bin;%PATH%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
endlocal
