#!/usr/bin/env bash
# Equivalente Linux de start-all.ps1 / start-all.bat.
# Levanta Docker (PostgreSQL + Mosquitto [+ inferencia]), backend, frontend y simulador en
# segundo plano. Logs en logs/, PIDs en logs/pids/. Para detener todo: ./stop-all.sh
#
# Uso: ./start-all.sh [--local-inference] [--no-inference] [--no-browser] [-h|--help]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$ROOT/logs"
PID_DIR="$LOG_DIR/pids"

LOCAL_INFERENCE=0
NO_INFERENCE=0
OPEN_BROWSER=1

usage() {
  cat <<'USAGE'
Uso: ./start-all.sh [opciones]
  --local-inference  Inferencia con el venv local (Desarrollo/servicio-inferencia/.venv)
                     en lugar del contenedor Docker (que es el modo por defecto, como en el ps1).
  --no-inference     No levanta el servicio de inferencia.
  --no-browser       No abre el navegador (por defecto se abre si existe xdg-open).
  -h, --help         Muestra esta ayuda.
USAGE
}

for arg in "$@"; do
  case "$arg" in
    --local-inference) LOCAL_INFERENCE=1 ;;
    --no-inference) NO_INFERENCE=1 ;;
    --no-browser) OPEN_BROWSER=0 ;;
    -h | --help) usage; exit 0 ;;
    *) echo "[ERROR] Opcion desconocida: $arg" >&2; usage >&2; exit 1 ;;
  esac
done

info() { printf '\033[36m%s\033[0m\n' "$*"; }
ok() { printf '\033[32m%s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*"; }
err() { printf '\033[31m%s\033[0m\n' "$*" >&2; }

# --- Requisitos -------------------------------------------------------------
ok "==========================================="
ok "  Yerbanalytics -- Verificando requisitos..."
ok "==========================================="

if ! command -v java >/dev/null 2>&1; then
  err "[ERROR] Java no encontrado. Instala Java 17 desde: https://adoptium.net"
  exit 1
fi
java_ver="$(java -version 2>&1 | head -n1 | sed -E 's/.*"([0-9][0-9._]*).*/\1/')"
java_major="${java_ver%%.*}"
if ! [[ "$java_major" =~ ^[0-9]+$ ]] || [ "$java_major" -lt 17 ]; then
  err "[ERROR] Se requiere Java 17+. Version detectada: $java_ver"
  exit 1
fi
ok "[OK] Java $java_ver"

if ! command -v node >/dev/null 2>&1; then
  err "[ERROR] Node.js no encontrado. Instala desde: https://nodejs.org"
  exit 1
fi
ok "[OK] Node.js $(node --version)"

if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  err "[ERROR] Docker no esta corriendo (o tu usuario no tiene permisos). Inicia el daemon primero."
  exit 1
fi
if docker compose version >/dev/null 2>&1; then
  compose() { docker compose "$@"; }
elif command -v docker-compose >/dev/null 2>&1; then
  compose() { docker-compose "$@"; }
else
  err "[ERROR] No se encontro 'docker compose' ni 'docker-compose'."
  exit 1
fi
ok "[OK] Docker"
echo

# --- Rutas y flags ----------------------------------------------------------
FRONTEND_DIR="$ROOT/Desarrollo/frontend"
BACKEND_DIR="$ROOT/Desarrollo/backend"
SIM_DIR="$ROOT/Desarrollo/simulador"
INF_DIR="$ROOT/Desarrollo/servicio-inferencia"

# El simulador es opcional: sin carpeta se omite y el sistema espera hardware real.
HAY_SIM=0
[ -d "$SIM_DIR" ] && HAY_SIM=1
HAY_INF=0
if [ "$NO_INFERENCE" -eq 0 ] && [ -d "$INF_DIR" ]; then HAY_INF=1; fi

# Evita pisar una instancia ya iniciada por este script.
if [ -d "$PID_DIR" ]; then
  for f in "$PID_DIR"/*.pid; do
    [ -e "$f" ] || continue
    if kill -0 "$(<"$f")" 2>/dev/null; then
      err "[ERROR] Ya hay servicios iniciados ($(basename "$f" .pid) sigue vivo). Corre ./stop-all.sh primero."
      exit 1
    fi
  done
fi

# --- Dependencias npm -------------------------------------------------------
if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
  warn "[setup] Instalando dependencias del frontend..."
  (cd "$FRONTEND_DIR" && npm install) || { err "[ERROR] Fallo npm install del frontend."; exit 1; }
  ok "[OK] Dependencias del frontend instaladas."
fi
if [ "$HAY_SIM" -eq 1 ] && [ ! -d "$SIM_DIR/node_modules" ]; then
  warn "[setup] Instalando dependencias del simulador..."
  if (cd "$SIM_DIR" && npm install); then
    ok "[OK] Dependencias del simulador instaladas."
  else
    warn "[AVISO] Fallo npm install del simulador. Se arranca sin el."
    HAY_SIM=0
  fi
fi

# --- .env desde plantilla ---------------------------------------------------
copy_env() { # destino plantilla descripcion
  if [ ! -f "$1" ]; then
    cp "$2" "$1"
    warn "[setup] .env $3 creado desde $(basename "$2")."
  fi
}
copy_env "$FRONTEND_DIR/.env" "$FRONTEND_DIR/env.example" "del frontend (modo backend real)"
copy_env "$BACKEND_DIR/.env" "$BACKEND_DIR/env.example" "del backend (credenciales de desarrollo)"
[ "$HAY_SIM" -eq 1 ] && copy_env "$SIM_DIR/.env" "$SIM_DIR/env.example" "del simulador"
[ "$HAY_INF" -eq 1 ] && copy_env "$INF_DIR/.env" "$INF_DIR/.env.example" "del servicio de inferencia"

# --- Inferencia: modelo y modo ----------------------------------------------
# modelo_final.pt no se versiona. Si falta, Docker montaria una carpeta vacia y el contenedor
# se reiniciaria sin fin, asi que no se levanta.
INF_MODE="none"
if [ "$HAY_INF" -eq 1 ]; then
  if [ -f "$INF_DIR/modelo_final.pt" ]; then
    if [ "$LOCAL_INFERENCE" -eq 1 ]; then
      if [ -x "$INF_DIR/.venv/bin/python" ]; then
        INF_MODE="local"
      else
        warn "[AVISO] --local-inference pero no existe $INF_DIR/.venv. Se omite la inferencia."
      fi
    else
      INF_MODE="docker"
    fi
  else
    warn "[AVISO] Falta el modelo de IA: $INF_DIR/modelo_final.pt"
    warn "        El servicio de inferencia no se levanta; el resto arranca igual."
  fi
fi

ok "==========================================="
ok "  Iniciando servicios..."
ok "==========================================="

mkdir -p "$LOG_DIR" "$PID_DIR"

# --- 1. Docker --------------------------------------------------------------
cd "$ROOT"
if [ "$INF_MODE" = "docker" ]; then
  info "[1/5] Iniciando contenedores Docker (PostgreSQL + Mosquitto + Inferencia)..."
  compose up -d --build
else
  info "[1/5] Iniciando contenedores Docker (PostgreSQL + Mosquitto)..."
  compose up -d yerbanalytics-db mosquitto
fi
warn "Esperando 3 segundos para que la base de datos se inicialice..."
sleep 3

# Lanza un servicio en su propio grupo de procesos (setsid) para que stop-all.sh lo mate entero.
# El PID lo escribe el propio proceso ya dentro de su sesion nueva y despues se reemplaza por el
# comando (exec): asi el archivo guarda el lider del grupo, y no queda ningun proceso intermedio
# con la salida de este script abierta (que dejaria colgado a quien lo llama).
start_service() { # nombre directorio comando...
  local name="$1" dir="$2"
  shift 2
  (
    cd "$dir" || exit 1
    # shellcheck disable=SC2016 # $$ y $@ se expanden en el bash hijo, a proposito
    setsid bash -c 'echo $$ >"$0"; exec "$@"' "$PID_DIR/$name.pid" "$@" \
      >"$LOG_DIR/$name.log" 2>&1 </dev/null &
  )
}

# --- 2. Backend -------------------------------------------------------------
info "[2/5] Iniciando Backend (Spring Boot)..."
start_service backend "$BACKEND_DIR" ./mvnw spring-boot:run

# --- 3. Frontend ------------------------------------------------------------
info "[3/5] Iniciando Frontend (React + Vite)..."
start_service frontend "$FRONTEND_DIR" npm run dev

# --- 4. Simulador -----------------------------------------------------------
if [ "$HAY_SIM" -eq 1 ]; then
  info "[4/5] Iniciando Simulador de hardware (Node + MQTT)..."
  start_service simulador "$SIM_DIR" npm run dev
else
  echo "[4/5] Simulador no instalado (Desarrollo/simulador). Se omite."
  echo "      El sistema queda esperando telemetria de hardware real."
fi

# --- Inferencia local (venv). Reintenta sola mientras el backend no responde. ---
if [ "$INF_MODE" = "local" ]; then
  info "[+] Iniciando servicio de inferencia (venv local)..."
  start_service inferencia "$INF_DIR" .venv/bin/python src/main.py
fi

# --- 5. Navegador -----------------------------------------------------------
# Espera a que el puerto acepte conexiones (bash /dev/tcp; prueba IPv4 e IPv6).
wait_port() { # puerto segundos
  local port="$1" limit=$((SECONDS + ${2:-180})) host
  while [ "$SECONDS" -lt "$limit" ]; do
    for host in 127.0.0.1 ::1; do
      if (exec 3<>"/dev/tcp/$host/$port") 2>/dev/null; then return 0; fi
    done
    sleep 0.5
  done
  return 1
}

echo
if [ "$OPEN_BROWSER" -eq 1 ] && command -v xdg-open >/dev/null 2>&1; then
  info "[5/5] Esperando a que los frontends respondan para abrir el navegador..."
  if wait_port 5173; then
    xdg-open "http://localhost:5173" >/dev/null 2>&1 || true
    ok "      Dashboard abierto."
    sleep 2
  else
    warn "[AVISO] El dashboard no respondio a tiempo. Abrilo a mano: http://localhost:5173"
  fi
  if [ "$HAY_SIM" -eq 1 ]; then
    if wait_port 5180; then
      xdg-open "http://localhost:5180" >/dev/null 2>&1 || true
      ok "      Simulador abierto."
    else
      warn "[AVISO] El simulador no respondio a tiempo. Abrilo a mano: http://localhost:5180"
    fi
  fi
else
  echo "[5/5] Navegador omitido (--no-browser o falta xdg-open)."
fi

echo
ok "==========================================="
ok "  Servicios iniciando en segundo plano."
ok "  - Backend:    http://localhost:8000"
ok "  - Frontend:   http://localhost:5173"
[ "$HAY_SIM" -eq 1 ] && ok "  - Simulador:  http://localhost:5180"
[ "$INF_MODE" = "docker" ] && ok "  - Inferencia: daemon Docker: docker logs -f servicio-inferencia"
[ "$INF_MODE" = "local" ] && ok "  - Inferencia: venv local"
ok "  Logs: $LOG_DIR/*.log   |   Detener: ./stop-all.sh"
ok "==========================================="
