#!/usr/bin/env bash
# Detiene lo que inicio start-all.sh: lee logs/pids/*.pid y mata cada grupo de procesos
# (cada servicio se lanza con setsid, asi caen tambien tsx watch, maven y sus hijos).
# Uso: ./stop-all.sh [--docker]   (--docker tambien baja los contenedores: docker compose down)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_DIR="$ROOT/logs/pids"
DOWN_DOCKER=0
[ "${1:-}" = "--docker" ] && DOWN_DOCKER=1

stop_group() { # nombre pid
  local name="$1" pid="$2" i
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "[--] $name: ya no estaba corriendo (pid $pid)."
    return
  fi
  # Seguridad: solo se mata si el pid es lider de su propio grupo (lo creo setsid).
  if [ "$(ps -o pgid= -p "$pid" | tr -d ' ')" != "$pid" ]; then
    echo "[!!] $name: el pid $pid no lidera su grupo; se omite por seguridad." >&2
    return
  fi
  kill -TERM -- "-$pid" 2>/dev/null || true
  for i in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.5
    [ "$i" -eq 20 ] && kill -KILL -- "-$pid" 2>/dev/null || true
  done
  echo "[OK] $name detenido (grupo $pid)."
}

if [ -d "$PID_DIR" ]; then
  for f in "$PID_DIR"/*.pid; do
    [ -e "$f" ] || continue
    stop_group "$(basename "$f" .pid)" "$(<"$f")"
    rm -f "$f"
  done
else
  echo "No hay PIDs registrados en $PID_DIR."
fi

if [ "$DOWN_DOCKER" -eq 1 ]; then
  cd "$ROOT"
  if docker compose version >/dev/null 2>&1; then docker compose down; else docker-compose down; fi
else
  echo "Contenedores Docker sin tocar (usa --docker para bajarlos)."
fi
