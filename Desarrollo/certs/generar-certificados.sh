#!/usr/bin/env bash
# ============================================================================
#  Certificados TLS para la LAN del vivero
# ----------------------------------------------------------------------------
#  getUserMedia exige origen seguro: sin HTTPS no hay cámara, salvo en localhost
#  —que no sirve, porque el iPhone no es la máquina donde corre Vite.
#
#  Este script crea una CA local propia y un certificado de servidor para las
#  direcciones de esta máquina. La CA se instala UNA VEZ en el iPhone y a partir
#  de ahí Safari confía en la app de cámara y en el backend.
#
#  Reemplaza a `mkcert` (que no está instalado) usando sólo openssl.
#
#  USO:
#      ./generar-certificados.sh 192.168.1.56 [otra-ip-o-host ...]
#
#  Los certificados NO se versionan (ver .gitignore). Regeneralos si cambia la
#  IP de la máquina en la red del vivero.
# ============================================================================
set -euo pipefail

cd "$(dirname "$0")"

# Git Bash (MSYS) convierte cualquier argumento que empiece con "/" en una ruta de Windows,
# y rompe los -subj de openssl ("/CN=..." termina como "C:/Program Files/Git/CN=..."). Sin
# esto el script falla con "subject name is expected to be in the format /type0=value0".
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL='*'

# --- Detección de la IP de la LAN ----------------------------------------
# Sin argumentos, se detecta sola. Que ande sin parámetros importa: es lo primero que corre
# alguien que acaba de clonar el repo, y pedirle que averigüe su IP es una barrera tonta.
#
# Se detectan TODAS las direcciones de la máquina, no una sola: con varias redes activas
# (WiFi, cable, VPN, adaptadores de Docker o VirtualBox) elegir "la buena" automáticamente es
# adivinar, y equivocarse produce un certificado que el teléfono rechaza sin explicar por qué.
# Incluirlas todas cuesta nada y funciona en cualquiera de esas redes.
detectar_ips() {
  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -Command "
      Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { \$_.IPAddress -notmatch '^(127\.|169\.254\.)' } |
        Where-Object { (Get-NetAdapter -InterfaceIndex \$_.InterfaceIndex -ErrorAction SilentlyContinue).Status -eq 'Up' } |
        Select-Object -ExpandProperty IPAddress" 2>/dev/null | tr -d '\r'
  elif command -v ip >/dev/null 2>&1; then
    ip -4 -o addr show scope global 2>/dev/null | awk '{split($4,a,"/"); print a[1]}'
  else
    ifconfig 2>/dev/null | awk '/inet /{print $2}' | grep -v '^127\.'
  fi
}

if [ $# -eq 0 ]; then
  mapfile -t IPS < <(detectar_ips) 2>/dev/null || IPS=($(detectar_ips))
  if [ ${#IPS[@]} -eq 0 ]; then
    echo "No se pudo detectar ninguna dirección de red en esta máquina." >&2
    echo "Pasala a mano:  $0 <ip-o-hostname> [más...]" >&2
    exit 1
  fi
  echo "→ Direcciones detectadas: ${IPS[*]}"
  echo "  El certificado va a servir para todas, así que no importa por cuál entre el teléfono."
  set -- "${IPS[@]}" "$(hostname)"
fi

DIAS_CA=3650
# iOS rechaza certificados de servidor con más de 398 días de validez.
DIAS_SERVIDOR=397

# --- Subject Alternative Names -------------------------------------------
# iOS ignora el Common Name desde iOS 13: si el nombre no está en el SAN, el
# certificado se rechaza aunque la CA sea de confianza.
SAN="DNS:localhost,IP:127.0.0.1"
for h in "$@"; do
  if [[ "$h" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    SAN="$SAN,IP:$h"
  else
    SAN="$SAN,DNS:$h"
  fi
done
echo "SAN: $SAN"

# --- Restricciones de nombre de la CA -------------------------------------
# Esto es lo que acota el daño si la clave de la CA se filtra.
#
# Una CA raíz de confianza puede avalar CUALQUIER dominio: es la razón por la que el iPhone
# muestra advertencias al instalarla. Con `nameConstraints` la CA queda limitada a los
# nombres del vivero, así que un certificado falso para —digamos— un banco es rechazado por
# el validador aunque esté firmado con esta misma clave.
#
# Se permite la /24 completa de cada IP para que un cambio de dirección dentro de la misma
# red no obligue a reinstalar el perfil en el teléfono.
PERMITIDOS="permitted;DNS:localhost,permitted;IP:127.0.0.1/255.255.255.255"
for h in "$@"; do
  if [[ "$h" =~ ^([0-9]+\.[0-9]+\.[0-9]+)\.[0-9]+$ ]]; then
    PERMITIDOS="$PERMITIDOS,permitted;IP:${BASH_REMATCH[1]}.0/255.255.255.0"
  else
    PERMITIDOS="$PERMITIDOS,permitted;DNS:$h"
  fi
done

# --- CA -------------------------------------------------------------------
if [ ! -f ca-key.pem ]; then
  echo "→ Creando la CA local…"
  echo "  Restringida a: $PERMITIDOS"
  openssl req -x509 -newkey rsa:4096 -sha256 -days "$DIAS_CA" -nodes \
    -keyout ca-key.pem -out ca.pem \
    -subj "/CN=Yerbanalytics CA local/O=Yerbanalytics/C=AR" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -addext "nameConstraints=critical,$PERMITIDOS" 2>/dev/null

  # La clave de la CA es lo único verdaderamente sensible acá: quien la tenga puede emitir
  # certificados para los nombres permitidos. Se restringe a su dueño.
  chmod 600 ca-key.pem 2>/dev/null || true
else
  echo "→ Reutilizando la CA existente (ca.pem). Borrala si querés una nueva."
fi

# --- Certificado de servidor ---------------------------------------------
echo "→ Emitiendo el certificado de servidor…"
openssl req -newkey rsa:2048 -sha256 -nodes \
  -keyout servidor-key.pem -out servidor.csr \
  -subj "/CN=yerbanalytics-lan/O=Yerbanalytics/C=AR" 2>/dev/null

cat > .ext <<EOF
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=$SAN
EOF

openssl x509 -req -in servidor.csr -CA ca.pem -CAkey ca-key.pem -CAcreateserial \
  -out servidor.pem -days "$DIAS_SERVIDOR" -sha256 -extfile .ext 2>/dev/null

rm -f servidor.csr .ext

# --- Keystore para el backend (Spring Boot / Tomcat) ---------------------
echo "→ Empaquetando el keystore PKCS12 para el backend…"
openssl pkcs12 -export \
  -in servidor.pem -inkey servidor-key.pem -certfile ca.pem \
  -out servidor.p12 -name yerbanalytics \
  -passout pass:yerbanalytics 2>/dev/null

echo
echo "Listo. Archivos generados en $(pwd):"
echo "  ca.pem            ← instalar ESTE en el iPhone (y en Windows)"
echo "  servidor.pem      ← certificado del servidor (Vite)"
echo "  servidor-key.pem  ← clave privada (Vite)"
echo "  servidor.p12      ← keystore del backend (contraseña: yerbanalytics)"
echo
echo "Vence: $(openssl x509 -enddate -noout -in servidor.pem | cut -d= -f2)"
