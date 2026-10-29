# Puente USB ↔ MQTT (sólo para la expo)

En un salón lleno el Wi-Fi de 2,4 GHz no sirve: el router echa a los clientes a los segundos. Este puente
deja que el nodo combinado hable con el broker **por el cable USB**, sin tocar nada de la plataforma.

```
Siempre:   ESP32 ──Wi-Fi──▶ router ──▶ Mosquitto ◀── backend
Expo:      ESP32 ──USB────▶ puente (PC) ──▶ Mosquitto ◀── backend
```

Para el backend, el puente **es** el nodo: publica en los mismos tópicos, con los mismos payloads. Backend,
broker, dashboard, simulador y contrato MQTT no cambian. Diseño y porqués:
`openspec/changes/add-transporte-usb-nodo/`.

> **Es un workaround.** El transporte de siempre es el Wi-Fi (`nodo_combinado`). Después de la expo hay que
> volver a grabar ese firmware.

## Qué necesitás

- La placa enchufada a la PC por el puerto **UART** (el del chip CH343; aparece como
  `/dev/serial/by-id/usb-1a86_…` o `/dev/ttyACM0`). El protocolo viaja por ahí; los logs del firmware siguen
  saliendo por el otro puerto, el USB nativo.
- Permiso sobre el puerto serie: tu usuario tiene que estar en el grupo `dialout`. Una sola vez:
  ```bash
  sudo usermod -aG dialout "$USER"     # y cerrá sesión y volvé a entrar (o reiniciá)
  ```
- `arduino-cli` con el core `esp32:esp32` (el mismo que pide el README del firmware) y Python 3.
- Mosquitto andando (`docker compose up -d mosquitto`, o `./start-all.sh`).

## 1. Grabar el firmware USB

```bash
cd Desarrollo/embebido
./puente-usb/compilar-nodo-usb.sh --subir       # compila y graba en el CH343 de la placa
```

Usa `comun/config.h` como siempre (la identidad del nodo, los pines y las calibraciones no cambian). El
Wi-Fi queda apagado. Si el puente está corriendo, frenalo antes de grabar: comparten el puerto.

## 2. Arrancar el puente

```bash
./Desarrollo/embebido/puente-usb/iniciar.sh       # -v muestra cada mensaje que pasa
```

La primera vez crea su entorno virtual. Variables opcionales: `PUENTE_PUERTO`, `PUENTE_BAUD` (115200),
`MQTT_HOST` (127.0.0.1), `MQTT_PORT` (1883).

Tiene que quedar así:

```
[INFO] Puerto abierto, sesión 3f9a12c0
[INFO] Conectado al broker 127.0.0.1:1883
[INFO] Suscripción de la placa: nursery/zone/MZ-1/sector/MZ-1-001/command (QoS 1)
...
```

En el broker aparece el cliente `yerbanalytics-puente-usb` (no el ESP32).

## 3. Verificar

1. Una lectura **sin motor** (no evalúa reglas ni mueve nada):
   `POST /api/pulsos {"ejecutarMotor": false}`, o la secuencia de lectura de Demo Expo sin «ejecutar motor».
   En el dashboard, MZ-1 tiene que mostrar valores con la hora actual.
2. Una pasada del riel: tienen que volver los eventos `ACEPTADO` y `LLEGO`.

## Si algo falla

| Síntoma | Causa probable |
|---|---|
| `Permission denied` al abrir el puerto | Falta el grupo `dialout` (ver arriba) |
| El puente abre el puerto pero no llegan suscripciones | La placa tiene grabado el firmware Wi-Fi, o el cable está en el USB nativo y no en el UART |
| `Sin broker: se descarta la publicación` | Mosquitto no está andando |
| La placa se reinicia al arrancar el puente | No debería: el puente abre el puerto sin DTR ni RTS. Revisá que no haya otro programa (monitor serie) usando el puerto |

Si se desenchufa el cable, el puente lo reabre solo y la placa reenvía sus suscripciones; la telemetría que
no se pudo mandar queda en el buffer offline del firmware y sale al reconectar.

## Volver al Wi-Fi

Grabá `nodo_combinado` como dice [`../README.md`](../README.md) (sección «Compilar y flashear») y no
arranques el puente.

## Cómo funciona

Una línea por mensaje, con prefijo `@` (lo que no respeta el formato se descarta: por el mismo UART imprime
la ROM al arrancar):

| Sentido | Línea | Qué es |
|---|---|---|
| placa → PC | `@S <qos> <tópico>` | Suscribirse |
| placa → PC | `@P <qos> <tópico> <payload>` | Publicar |
| PC → placa | `@H <epoch> <sesión>` | Latido cada 2 s, sólo con el broker conectado; trae la hora |
| PC → placa | `@M <tópico> <payload>` | Mensaje de un tópico suscripto |

La placa se da por conectada si recibió un latido en los últimos 6 s, y reenvía sus suscripciones al
conectarse o cuando cambia la sesión (puente reiniciado). Con el latido ajusta su reloj, así la telemetría
lleva la hora actual aunque no haya NTP.

| Archivo | Qué hace |
|---|---|
| `protocolo.py` | Lee y arma las líneas (lógica pura) |
| `nucleo.py` | Ruteo entre la placa y el broker (lógica pura, con dobles en los tests) |
| `puente.py` | E/S: puerto serie y paho-mqtt |
| `../transporte_usb/` | El lado de la placa: implementa `net_wifi.h` y `net_mqtt.h` sin Wi-Fi |

Tests: `.venv/bin/python -m pytest` desde esta carpeta.
