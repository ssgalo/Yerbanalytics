#!/usr/bin/env bash
# Arranca el puente USB ↔ MQTT del nodo (sólo para la expo). Crea el venv la primera vez.
# Uso: ./iniciar.sh [-v]      (variables opcionales: PUENTE_PUERTO, PUENTE_BAUD, MQTT_HOST, MQTT_PORT)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ ! -x .venv/bin/python ]; then
  echo "Creando el entorno virtual del puente..."
  python3 -m venv .venv
  .venv/bin/pip install -q -r requirements.txt
fi

exec .venv/bin/python puente.py "$@"
