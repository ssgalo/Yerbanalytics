# Yerbanalytics — Firmware ESP32

Firmware de los nodos del vivero. **Tres firmwares independientes** —uno por
tipo de nodo— con arquitectura **FreeRTOS** (tareas y colas). Cada uno
compila solo lo que su rol necesita. El código común (red, contrato, tipos, drivers)
se comparte entre firmwares sin duplicarse.

---

## 1. Los tres firmwares

| Firmware (`env` PlatformIO) | Sketch                              | Qué hace                                | Uso                                  |
| --------------------------- | ----------------------------------- | --------------------------------------- | ------------------------------------ |
| `nodo_sensor`               | `nodo_sensor/nodo_sensor.ino`       | Solo sensado + telemetría               | Nodo testigo por macro-zona (campo)  |
| `nodo_actuador`             | `nodo_actuador/nodo_actuador.ino`   | Solo escucha comandos y acciona (+ ACK) | Nodo de actuación por sector (campo) |
| `nodo_combinado`            | `nodo_combinado/nodo_combinado.ino` | Sensado **+** actuación en un ESP32     | **Prototipo**                        |

Cada sketch tiene su propio `setup()` y su propia `task_red` a medida: el nodo
sensor no compila el subsistema de actuación ni su callback de comandos; el nodo
actuador no compila el subsistema de sensado ni el buffer offline.

### Qué compila cada uno

|                  | `comun/` | `sensado/` | `actuacion/` |
| ---------------- | :------: | :--------: | :----------: |
| `nodo_sensor`    |    ✅    |     ✅     |      —       |
| `nodo_actuador`  |    ✅    |     —      |      ✅      |
| `nodo_combinado` |    ✅    |     ✅     |      ✅      |

El reparto lo hace `build_src_filter` en `platformio.ini`; no hay que tocar nada
para que cada entorno tome solo sus carpetas.

---

## 2. Procedimiento por tipo de nodo (PlatformIO)

Paso previo común (una sola vez): copiar la plantilla de configuración y completar
credenciales, broker, identidad (zona/sector) y pines.

```bash
cd Desarrollo/embebido
cp comun/config.example.h comun/config.h
# editar comun/config.h → WiFi, MQTT_HOST, NODO_ZONA_ID, NODO_SECTOR_ID, pines...
```

Luego, según el nodo que quieras flashear, elegí **un** entorno:

**Nodo del prototipo (sensado + actuación):**

```bash
pio run -e nodo_combinado -t upload
pio device monitor          # logs a 115200 baud
```

**Nodo testigo (solo sensado):**

```bash
pio run -e nodo_sensor -t upload
```

**Nodo de actuación (solo actuadores):**

```bash
pio run -e nodo_actuador -t upload
```

> Cada `env` compila un solo sketch; nunca hay dos `setup()`/`loop()` en una build.
> `pio run` (sin `-e`) compila los tres, útil para verificar que todos cierran.

### Arduino IDE

Cada firmware es su propia carpeta-sketch. Para compilar uno, abrí su `.ino`
(p.ej. `nodo_combinado/nodo_combinado.ino`) y asegurate de que el IDE encuentre
los `.h/.cpp` de `comun/` + las carpetas que ese nodo usa (§1). La vía soportada
y recomendada es **PlatformIO**, que arma el include path automáticamente.

---

## 3. Configuración (`comun/config.h`)

Un único archivo, compartido por los tres firmwares (copia de `config.example.h`,
**no versionada**): credenciales WiFi, broker MQTT, **identidad** (`NODO_ZONA_ID`,
`NODO_SECTOR_ID`), pines, `SENSOR_POLLING_INTERVAL_MS`, límites de seguridad,
calibraciones y flags. El tipo de nodo **no** se elige acá (se elige compilando el
sketch); cada nodo usa solo la porción que le aplica.

Flags clave:

- **`TELEMETRIA_NIVEL_SECTOR`**: `0` → publica en `.../zone/{zona}/telemetry`
  (testigo, backend actual); `1` → `.../zone/{zona}/sector/{sector}/telemetry`
  (nodo combinado). Solo afecta a los nodos que sensan.
- **`ENVIAR_METRICAS_EXTENDIDAS`**: `0` → solo las 5 métricas base (100% compatible
  con el backend actual). `1` → agrega pH/N/P/K/etc. **Requiere cambio backend** (ver §7).

---

## 4. Wiring (pines por defecto)

Cada nodo usa solo la parte que le corresponde: el `nodo_sensor` no cablea
actuadores; el `nodo_actuador` no cablea sensores; el `nodo_combinado` usa todo.

| Función                   | Pin             | Nodo(s)             | Notas                                             |
| ------------------------- | --------------- | ------------------- | ------------------------------------------------- |
| Sensor de luz (LDR)       | GPIO39          | sensor, combinado   | ADC1. TODO: confirmar si es LDR (% luz) o UV real |
| DHT11 (ambiente)          | GPIO18          | sensor, combinado   | humedad + temp aire                               |
| Sonda suelo RS-485 RX/TX  | GPIO16 / GPIO17 | sensor, combinado   | UART2, Modbus RTU 4800 8N1                        |
| Sonda suelo RS-485 DE/RE  | GPIO4           | sensor, combinado   | control de dirección                              |
| Batería (ADC)             | GPIO34          | sensor, combinado   | divisor resistivo (calibrar)                      |
| LED estado                | GPIO5           | todos               |                                                   |
| Electroválvula (relé)     | GPIO25          | actuador, combinado | riego                                             |
| Caudalímetro              | GPIO35          | actuador, combinado | feedback de flujo (pulsos)                        |
| Bomba peristáltica        | GPIO26          | actuador, combinado | dosificación                                      |
| Motor mediasombra A/B     | GPIO32 / GPIO33 | actuador, combinado | driver puente H                                   |
| Fin de carrera            | GPIO27          | actuador, combinado |                                                   |
| Sensor de corriente motor | GPIO36          | actuador, combinado | ADC, detección de atasco                          |

---

## 5. Librerías

`knolleary/PubSubClient` · `bblanchon/ArduinoJson` · `adafruit/DHT sensor library`
(+ `Adafruit Unified Sensor`). Declaradas en `platformio.ini` (`lib_deps`).

---

## 6. Contrato MQTT (fuente única de verdad)

Alineado con `openspec/changes/add-rules-engine`. **Este firmware ya lo respeta**; cuando
se implemente el `ActionExecutor` y el publisher de comandos en el backend, deben usar
exactamente este contrato.

### Tópicos

| Canal                  | Topic                                           | Publica | Suscribe | QoS | Nodo(s)                                 |
| ---------------------- | ----------------------------------------------- | ------- | -------- | --- | --------------------------------------- |
| Telemetría (combinado) | `nursery/zone/{zona}/sector/{sector}/telemetry` | ESP32   | Backend  | 1   | combinado (`TELEMETRIA_NIVEL_SECTOR=1`) |
| Telemetría (testigo)   | `nursery/zone/{zona}/telemetry`                 | ESP32   | Backend  | 1   | sensor / combinado (`=0`)               |
| Comando                | `nursery/zone/{zona}/sector/{sector}/command`   | Backend | ESP32    | 2   | actuador, combinado                     |
| Ack                    | `nursery/zone/{zona}/sector/{sector}/ack`       | ESP32   | Backend  | 1   | actuador, combinado                     |

### Payload de telemetría (uplink)

```json
{
  "mac": "A4:CF:12:9A:00:01",
  "battery": 85,
  "signal": -63,
  "timestamp": 1782414800,
  "metrics": {
    "humSus": 52.4,
    "humAmb": 72.1,
    "temp": 23.5,
    "ce": 1.2,
    "uv": 40.0,
    "tempSuelo": 22.8,
    "phSuelo": 6.4,
    "n": 12,
    "p": 8,
    "k": 30,
    "salinidad": 120,
    "tds": 210
  }
}
```

Las claves extendidas (`tempSuelo`…`tds`) solo aparecen si `ENVIAR_METRICAS_EXTENDIDAS=1`.

### Payload de comando (downlink)

```json
{ "commandId": "abc-123", "actuador": "valve", "accion": "open", "parametros": { "durationSec": 30 } }
{ "commandId": "abc-124", "actuador": "pump",  "accion": "inject", "parametros": { "ml": 12 } }
{ "commandId": "abc-125", "actuador": "shade", "accion": "move",  "parametros": { "targetPct": 40 } }
```

`commandId` es opcional (deduplicación defensiva en el nodo). La idempotencia principal la
garantiza el backend (in-flight lock + `ACTION_COOLDOWN_MINUTES`).

### Payload de ack (uplink)

```json
{ "commandId": "abc-123", "status": "SUCCESS", "detalle": { "durationSec": 30 } }
{ "commandId": "abc-123", "status": "ERROR",   "detalle": { "tipo": "falla_hidraulica" } }
```

El backend usa `status`: `SUCCESS` libera el lock e inicia el cooldown; `ERROR` libera el
lock y habilita reintento.

### ⚠️ Limitación de QoS conocida

`PubSubClient` no soporta QoS 2 (ni QoS 1 en `publish`). El canal de comando **debería** ser
QoS 2 (evita doble dosis/riego). Mitigación actual: deduplicación por `commandId` en el nodo

- in-flight lock del backend. Para QoS 2 real, migrar a un cliente async (`async-mqtt-client`
  o `esp-mqtt`). Documentado como mejora futura.

---

## 7. Cambios backend acoplados (pendientes, fuera del embebido)

Para poder recibir el **payload extendido** (`ENVIAR_METRICAS_EXTENDIDAS=1`) sin romper
la ingesta actual, el backend debe (una de dos):

1. Anotar `MqttTelemetryPayload`/`MetricsPayload` con `@JsonIgnoreProperties(ignoreUnknown = true)`, o
2. Extender el record con las métricas nuevas (recomendado: habilita persistir/mostrar pH, N, P, K)
   y propagarlas a DB y frontend.

Además, para el **nodo combinado** (telemetría sector-level) el `MqttTelemetryReceiver`
—hoy suscrito a `nursery/zone/+/telemetry` (nivel zona)— debe extenderse a sector-level.
Y falta implementar el **publisher de comandos** + evaluación de **ack** (previsto en
`add-rules-engine`, tareas 1.3 y 6.5).

Mientras esos cambios no estén, desplegar con `ENVIAR_METRICAS_EXTENDIDAS=0` y
`TELEMETRIA_NIVEL_SECTOR=0`: el nodo es indistinguible del simulador actual.

---

## 8. Estructura

```
embebido/
  platformio.ini            tres envs (uno por firmware); reparte carpetas con build_src_filter
  comun/                    compartido por los 3: config, contrato, tipos, red (wifi/mqtt), reloj, util_json
  sensado/                  drivers (luz/dht/suelo), buffer_offline, task_sensado   (sensor + combinado)
  actuacion/                drivers (valvula/bomba/mediasombra), dedup, task_actuacion (actuador + combinado)
  nodo_sensor/    nodo_sensor.ino      setup() red + sensado
  nodo_actuador/  nodo_actuador.ino    setup() red + actuación
  nodo_combinado/ nodo_combinado.ino   setup() red + sensado + actuación (prototipo)
```

---

## 9. Validación sin hardware

El nodo publica al mismo broker/topic que el `MqttTelemetrySimulator` del backend, así que
se puede validar la ingesta **antes** de tener el hardware. Para comandos, publicar
manualmente:

```bash
mosquitto_pub -h <broker> -t 'nursery/zone/MZ-1/sector/S-001/command' \
  -m '{"commandId":"t1","actuador":"valve","accion":"open","parametros":{"durationSec":10}}'
mosquitto_sub -h <broker> -t 'nursery/zone/MZ-1/sector/S-001/ack'
```
