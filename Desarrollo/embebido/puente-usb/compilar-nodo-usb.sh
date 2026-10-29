#!/usr/bin/env bash
# Compila (y opcionalmente graba) el nodo combinado con el transporte USB, sólo para la expo.
# Mismo procedimiento que el README del firmware (copia plana para arduino-cli), pero con
# transporte_usb/ en lugar de comun/net_wifi.cpp y comun/net_mqtt.cpp.
#
# Uso: ./compilar-nodo-usb.sh                 compila
#      ./compilar-nodo-usb.sh --subir [puerto] compila y graba (por defecto, el CH343 de la placa)
#
# Para volver al Wi-Fi después de la expo, grabá nodo_combinado como dice Desarrollo/embebido/README.md.
set -euo pipefail

FQBN="esp32:esp32:esp32s3:PSRAM=opi,FlashSize=16M,CDCOnBoot=cdc,PartitionScheme=app3M_fat9M_16MB"
EMBEBIDO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SALIDA="${TMPDIR:-/tmp}/yerbanalytics-nodo-usb/nodo_combinado"   # arduino-cli exige carpeta = nombre del .ino

if [ ! -f "$EMBEBIDO/comun/config.h" ]; then
  echo "Falta comun/config.h (copiá config.example.h y completalo)." >&2
  exit 1
fi

rm -rf "$SALIDA" && mkdir -p "$SALIDA"
cp "$EMBEBIDO"/nodo_combinado/nodo_combinado.ino "$SALIDA"/
cp "$EMBEBIDO"/comun/*.h "$EMBEBIDO"/comun/*.cpp "$EMBEBIDO"/sensado/* "$EMBEBIDO"/actuacion/* "$SALIDA"/
rm -f "$SALIDA"/config.example.h "$SALIDA"/net_wifi.cpp "$SALIDA"/net_mqtt.cpp
cp "$EMBEBIDO"/transporte_usb/* "$SALIDA"/

echo "Compilando el nodo combinado con transporte USB..."
arduino-cli compile --fqbn "$FQBN" "$SALIDA"

if [ "${1:-}" = "--subir" ]; then
  PUERTO="${2:-$(ls /dev/serial/by-id/usb-1a86_* 2>/dev/null | head -n1)}"
  PUERTO="${PUERTO:-/dev/ttyACM0}"
  echo "Grabando en $PUERTO (si el puente está corriendo, frenalo antes: comparten el puerto)..."
  arduino-cli upload -p "$PUERTO" --fqbn "$FQBN" "$SALIDA"
fi
