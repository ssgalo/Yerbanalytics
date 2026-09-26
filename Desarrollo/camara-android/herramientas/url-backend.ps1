<#
.SYNOPSIS
    Detecta el enlace USB con el teléfono y dice qué URL tipear en la app de cámara.

.DESCRIPTION
    No modifica nada. Sólo mira adaptadores de red y prueba si el backend responde.
    Si PowerShell se niega a ejecutarlo, corré una vez en esta sesión:
        Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

.EXAMPLE
    .\url-backend.ps1
    .\url-backend.ps1 -Puerto 8000
#>

param(
    [int]$Puerto = 8000
)

$Endpoint = "/api/nursery"

function Escribir-Rojo  { param($t) Write-Host $t -ForegroundColor Red }
function Escribir-Verde { param($t) Write-Host $t -ForegroundColor Green }
function Escribir-Gris  { param($t) Write-Host $t -ForegroundColor DarkGray }

Write-Host ""
Write-Host "Buscando el enlace USB con el teléfono…"
Write-Host ""

# --- 1. Adaptadores de red que vienen del teléfono ---------------------------
# Windows monta el tethering de Android como RNDIS ("Remote NDIS based Internet
# Sharing Device"). Se filtra por descripción porque el alias lo renombra el
# usuario y el índice cambia entre reconexiones.
$adaptadores = Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object {
    $_.Status -eq 'Up' -and
    ($_.InterfaceDescription -match 'RNDIS|Remote NDIS|Android|USB.*Ethernet|Ethernet.*USB')
}

if (-not $adaptadores) {
    Escribir-Rojo "No hay ningún adaptador de red del teléfono levantado."
    Write-Host ""
    Write-Host "Revisá, en este orden:"
    Write-Host "  1. ¿El cable transfiere DATOS? Muchos USB-C son sólo de carga."
    Write-Host "     El teléfono tiene que aparecer en el Administrador de dispositivos."
    Write-Host "  2. ¿Está activada la conexión compartida por USB en el teléfono?"
    Write-Host "     Ajustes → Redes e Internet → Zona WiFi y conexión compartida"
    Write-Host "  3. ¿El teléfono está en modo 'sólo carga'? Bajá la notificación de USB."
    Write-Host "  4. Si la opción de tethering está GRIS: varios Android se niegan a"
    Write-Host "     activarla si el teléfono no tiene datos móviles ni WiFi que compartir,"
    Write-Host "     aunque a vos sólo te interese el enlace. Prendé alguna y reintentá."
    Write-Host ""
    Write-Host "Si el adaptador existe pero con otro nombre, mirá la lista completa:"
    Escribir-Gris "     Get-NetAdapter | Format-Table Name, InterfaceDescription, Status"
    Write-Host ""
    Escribir-Gris "Alternativa sin tethering:  adb reverse tcp:$Puerto tcp:$Puerto"
    Escribir-Gris "y en la app tipeás  http://localhost:$Puerto"
    Write-Host ""
    exit 1
}

# --- 2. IP de esta PC en cada adaptador --------------------------------------
$encontrada = $false

foreach ($ad in $adaptadores) {
    $ip = Get-NetIPAddress -InterfaceIndex $ad.ifIndex -AddressFamily IPv4 `
                           -ErrorAction SilentlyContinue |
          Where-Object { $_.IPAddress -notlike '169.254.*' } |
          Select-Object -First 1

    if (-not $ip) {
        Escribir-Rojo "Adaptador '$($ad.Name)': levantado pero SIN IP válida."
        Write-Host "  El DHCP del teléfono no arrancó, o Windows se quedó con una"
        Write-Host "  dirección 169.254.x (APIPA). Desactivá y reactivá la conexión"
        Write-Host "  compartida por USB en el teléfono."
        Write-Host ""
        continue
    }

    $encontrada = $true
    $ipPc = $ip.IPAddress
    Escribir-Verde "Adaptador '$($ad.Name)' → esta PC tiene la IP $ipPc"
    Write-Host ""

    # --- 3. ¿El backend responde POR ESA IP? ---------------------------------
    # Se prueba con la IP del enlace, no con localhost: es exactamente lo que va
    # a hacer el teléfono, y distingue "el backend está caído" de "el backend
    # anda pero el firewall lo tapa".
    $respondeIp = $false
    try {
        $r = Invoke-WebRequest -Uri "http://${ipPc}:${Puerto}${Endpoint}" `
                               -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $respondeIp = $true }
    } catch { }

    if ($respondeIp) {
        Escribir-Verde "El backend responde en esa dirección. Tipeá esto en la app:"
        Write-Host ""
        Write-Host "    http://${ipPc}:${Puerto}"
        Write-Host ""
        continue
    }

    Escribir-Rojo "El backend NO responde en http://${ipPc}:${Puerto}"
    Write-Host ""

    $respondeLocal = $false
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:${Puerto}${Endpoint}" `
                               -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $respondeLocal = $true }
    } catch { }

    if ($respondeLocal) {
        Write-Host "  Pero SÍ responde en localhost. O sea: el backend está vivo y el"
        Write-Host "  problema es de red."
        Write-Host ""
        Escribir-Rojo "  Esto es, casi con seguridad, el FIREWALL DE WINDOWS."
        Write-Host ""
        Write-Host "  Windows clasifica cada red nueva como Pública y bloquea todo lo"
        Write-Host "  entrante, sin decir nada. Abrí PowerShell COMO ADMINISTRADOR y"
        Write-Host "  creá la regla, que abre sólo este puerto:"
        Write-Host ""
        Write-Host "      New-NetFirewallRule -DisplayName 'Yerbanalytics backend $Puerto' ``"
        Write-Host "        -Direction Inbound -LocalPort $Puerto -Protocol TCP -Action Allow"
        Write-Host ""
        Escribir-Gris "  (Marcar la red entera como Privada también funciona, pero abre"
        Escribir-Gris "   mucho más de lo necesario. Preferí la regla por puerto.)"
    }
    else {
        Write-Host "  Tampoco responde en localhost: el backend no está levantado."
        Write-Host ""
        Write-Host "      cd Desarrollo\backend"
        Write-Host "      .\mvnw.cmd spring-boot:run"
    }
    Write-Host ""
}

if (-not $encontrada) { exit 1 }

# --- 4. Aviso sobre la ruta por defecto --------------------------------------
foreach ($ad in $adaptadores) {
    $rutaDefault = Get-NetRoute -DestinationPrefix '0.0.0.0/0' `
                                -InterfaceIndex $ad.ifIndex -ErrorAction SilentlyContinue
    if ($rutaDefault) {
        Write-Host ""
        Escribir-Rojo "Ojo: hay una ruta por defecto saliendo por '$($ad.Name)'."
        Write-Host "  Puede que todo el tráfico de esta PC esté saliendo por el teléfono"
        Write-Host "  (y gastando sus datos móviles). Si la PC navega raro, es esto."
        Write-Host ""
    }
}
