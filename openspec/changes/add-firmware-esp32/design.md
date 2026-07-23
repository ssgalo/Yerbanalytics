# Design: add-firmware-esp32

## Context

El backend ya define y consume un contrato de telemetría MQTT (`add-mqtt-telemetry`), lo
ejercita con `MqttTelemetrySimulator`/`MqttTelemetryPublisher`, y tiene diseñado un motor
de reglas (`add-rules-engine`) que consolida las decisiones de actuación y define el canal
de comando. Sin embargo:

- El firmware físico **no existe**. El único artefacto es
  `Desarrollo/embebido/sensores_unificados.ino`, un boceto de lectura por serial (luz por
  ADC, DHT11 y sonda de suelo Modbus RTU) en un `loop()` con `delay(10000)`, sin WiFi,
  MQTT, concurrencia ni estructura.
- `Desarrollo/embebido/ejemplo.ino` (de otro proyecto, solo de referencia) muestra el
  patrón deseado: FreeRTOS con tasks, colas (`xQueueCreate`), prioridades y stacks por
  constante, MQTT con `PubSubClient`, callback que empuja eventos a una cola, y reconexión
  en su propia tarea.

Contrato vigente y su evolución (fuentes de verdad):

- **Backend implementado hoy** (`MqttTelemetryReceiver`): suscribe
  `nursery/zone/+/telemetry` a nivel **zona**, extrae `zoneId` de `parts[2]`. Payload =
  `MqttTelemetryPayload(mac, battery, signal, timestamp, metrics{humSus,humAmb,temp,ce,uv})`,
  deserializado con `ObjectMapper` por defecto → **falla ante campos desconocidos**.
- **`add-rules-engine` (design actualizado)**: define el canal a nivel **sector** con tres
  tópicos —`telemetry`, `command`, `ack`— bajo `nursery/zone/{zonaId}/sector/{sectorId}/…`,
  QoS 2 para comandos, ACK con `{"status":"SUCCESS"|"ERROR"}`, e idempotencia por
  in-flight lock + `ACTION_COOLDOWN_MINUTES`. También nombra `SENSOR_POLLING_INTERVAL`
  como el intervalo con que el ESP32 envía telemetría.

Mapeo físico de sensores (del boceto): luz por ADC (GPIO39), DHT11 (GPIO18), sonda de
suelo Modbus RTU por UART2 (RX16/TX17, DE=GPIO4, 4800 8N1) → humedad, temp de suelo, EC,
pH, N, P, K, salinidad, TDS.

## Goals / Non-Goals

**Goals:**
- Firmware basado en FreeRTOS (tasks/colas) que aproveche los núcleos del ESP32, con
  código modular, tipado y sin números mágicos.
- **Tres firmwares independientes**, uno por tipo de nodo: `nodo_sensor` (solo sensado),
  `nodo_actuador` (solo actuación) y `nodo_combinado` (sensado + actuación, el del
  prototipo). Sin condicionales de perfil: cada firmware compila solo su rol. La lógica
  reusable (red/MQTT/JSON/drivers) vive en carpetas compartidas, de modo que separar NO
  implica duplicarla.
- Respetar el contrato de telemetría (5 métricas base) y **extenderlo** con las métricas
  de suelo, protegido por flag y con el cambio backend documentado.
- Alinear el canal de comando/ack con `add-rules-engine` (sector-level, tres tópicos, QoS
  2, ACK `SUCCESS`/`ERROR`).
- Robustez de campo: reconexión WiFi/MQTT y **buffer offline en flash** con reintento sin
  duplicados y timestamp original.
- **Ahorro de energía por scheduling de tareas, sin deep sleep.**

**Non-Goals:**
- NO se implementa el lado backend del canal de comando (`ActionExecutor`/publisher) ni la
  migración del receiver a sector-level: se documentan como trabajo acoplado (ya previstos
  en `add-rules-engine` tareas 1.3, 6.5).
- NO se implementa un motor de reglas autónomo local completo (decisión offline de
  actuación en el nodo, HU-13): el buffer offline de telemetría sí entra.
- NO se toca frontend ni Modelo_IA.
- NO se define el hardware definitivo del sensor de luz/UV (queda abstraído).

## Decisions

### 1. Arquitectura FreeRTOS (basada en el patrón de `ejemplo.ino`)

Tareas y colas en lugar de `loop()` + `delay()`:

- `task_sensado` (nodos con sensado): cada `SENSOR_POLLING_INTERVAL` lee los sensores,
  arma el `PaqueteTelemetria` y lo empuja a `cola_telemetria`; luego `vTaskDelay`.
- `task_red`: gestiona WiFi + MQTT (`PubSubClient`), mantiene `client.loop()`, reconecta
  ante caídas, drena `cola_telemetria` publicando (o derivando al buffer offline si no hay
  red), y publica los ACK que llegan por `cola_ack`. Cada firmware trae su propia `task_red`
  a medida (el nodo sensor no maneja comandos/ack; el actuador no publica telemetría).
- `task_actuacion` (nodos con actuación): consume `cola_comandos`, ejecuta el actuador,
  aplica límites de seguridad y empuja el resultado a `cola_ack`.
- **Callback MQTT**: al recibir un comando, valida y lo empuja a `cola_comandos` (no
  ejecuta en el contexto del callback), igual que `ejemplo.ino` empuja eventos.
- Toda comunicación entre tareas pasa por colas; cada estado (buffer offline, hora NTP,
  dedup de comandos) tiene un único dueño, evitando accesos concurrentes. Prioridades y
  stacks definidos por constantes (`TASK_PRIORITY_*`, `TASK_STACK_SIZE`).

**Por qué**: desacopla red de sensado/actuación (una caída de WiFi no frena el muestreo ni
la ejecución), aprovecha ambos núcleos y hace cada tarea testeable en aislamiento.
Alternativa descartada: `loop()` secuencial del boceto actual (bloqueante, no concurrente).

### 2. Sin deep sleep — ahorro por scheduling

Se elimina el deep sleep como modo. El ahorro se logra con `vTaskDelay` (cede el núcleo al
planificador entre muestreos) y manteniendo las tareas dormidas salvo cuando hay trabajo.

**Por qué**: (a) un nodo con actuadores (`nodo_actuador`/`nodo_combinado`) debe estar
despierto para atender comandos en cualquier momento; (b) deep sleep reinicia el chip y no
conserva tasks/colas, incompatible con el modelo FreeRTOS elegido. El ESP32 aplica de
por sí *automatic light sleep*/*modem sleep* entre tareas ociosas; si más adelante se
requiere consumo aún menor en un nodo solo-sensor a batería, se evaluará light sleep
coordinado (documentado como mejora futura, no como modo del MVP).

### 3. Firmwares separados por tipo de nodo (sin condicionales de perfil)

Tres sketches independientes —`nodo_sensor/nodo_sensor.ino`, `nodo_actuador/…`,
`nodo_combinado/…`—, cada uno con su propio `setup()` y su `task_red` a medida. Un
`platformio.ini` con un `env` por sketch; `build_src_filter` incluye en cada build solo
el `.ino` de ese nodo más las carpetas que su rol necesita (`comun/` los tres; `sensado/`
sensor y combinado; `actuacion/` actuador y combinado). **No hay build flags de perfil ni
`#if` de perfil en el código.** Compatibilidad Arduino IDE: abrir el `.ino` del nodo con
`comun/` + sus carpetas.

**Por qué**: cada nodo se compila y flashea una sola vez, así que separar el código lo
vuelve directo y óptimo (sin ramas de perfil en runtime). La lógica reusable
(red/MQTT/JSON/drivers) vive en carpetas compartidas, de modo que separar **no** duplica
código: solo el `.ino` de arranque es propio de cada nodo. En producción a campo conviene
separar sensores (testigo por macro-zona) de actuadores (por sector); el prototipo usa
`nodo_combinado`. Alternativa descartada: un único código base con perfiles seleccionables
por build flag (agrega condicionales de perfil que no aportan, ya que solo se compila un
rol por nodo).

### 4. Estructura de proyecto

```
Desarrollo/embebido/
  platformio.ini            // un env por firmware; reparte carpetas con build_src_filter
  comun/                    // compartido por los 3
    config.example.h        // credenciales, broker, zonaId/sectorId, pines, intervalos, flags
    contrato.h              // topics (telemetry/command/ack) y claves JSON
    tipos.h                 // structs/enums: LecturaSuelo, LecturaAmbiente, PaqueteTelemetria, Comando, Ack...
    net_wifi.*              // conexión/reconexión WiFi + RSSI
    net_mqtt.*              // wrapper PubSubClient (connect, publish QoS, subscribe, loop)
    reloj.*                 // NTP + fallback monotónico (última hora NTP en NVS)
    util_json.*             // armado/parseo con ArduinoJson
  sensado/                  // sensor + combinado
    sensor_luz.*            // driver abstraído (LDR hoy, UV a futuro)
    sensor_dht.*            // DHT11
    sensor_suelo.*          // Modbus RTU RS-485 (NPK-pH-EC)
    buffer_offline.*        // cola FIFO en NVS + reintento
    task_sensado.*          // tarea de muestreo
  actuacion/                // actuador + combinado
    act_valvula.*           // electroválvula + caudalímetro
    act_bomba.*             // bomba peristáltica + volumen
    act_mediasombra.*       // motor + fin de carrera + sensado de corriente
    dedup_comandos.*        // idempotencia por commandId (defensa en profundidad)
    task_actuacion.*        // tarea ejecutora de comandos
  nodo_sensor/nodo_sensor.ino       // setup() red + sensado
  nodo_actuador/nodo_actuador.ino   // setup() red + actuación
  nodo_combinado/nodo_combinado.ino // setup() red + sensado + actuación (prototipo)
  README.md                 // arranque por nodo, wiring y el CONTRATO MQTT documentado
```

### 5. Librerías

- **PubSubClient** (MQTT) — igual que `ejemplo.ino`; liviana y estándar en ESP32.
- **ArduinoJson** — armado/parseo tipado de payloads (evita concatenar strings).
- **DHT sensor library** (Adafruit) — ya usada en el boceto.
- **Preferences (NVS)** — buffer offline. SPIFFS/LittleFS descartado para el MVP; NVS
  alcanza para una cola FIFO acotada.

### 6. Contrato MQTT alineado con `add-rules-engine`

- **Telemetría**: `nursery/zone/{zonaId}/sector/{sectorId}/telemetry` (nodos con
  `sectorId`, ej. combinado) o `nursery/zone/{zonaId}/telemetry` (testigo puro). QoS 1.
- **Comando**: `nursery/zone/{zonaId}/sector/{sectorId}/command`, QoS 2, payload
  `{ actuador, accion, parametros, commandId? }`.
- **ACK**: `nursery/zone/{zonaId}/sector/{sectorId}/ack`, payload
  `{ status: "SUCCESS"|"ERROR", detalle? }`. El backend lo usa para liberar el in-flight
  lock y decidir el cooldown.
- La idempotencia principal la maneja el backend (in-flight lock + cooldown); el nodo
  deduplica por `commandId` como defensa en profundidad.

### 7. Payload extendido y acoplamiento backend (BREAKING)

El firmware envía las 5 métricas base **más** `tempSuelo`, `phSuelo`, `n`, `p`, `k`,
`salinidad`, `tds`. Como el backend hoy falla ante campos desconocidos, se protege con la
constante `ENVIAR_METRICAS_EXTENDIDAS` (compile-time):

- Se despliega en `false` → solo las 5 base, 100% compatible con la ingesta actual.
- Se pasa a `true` recién cuando el backend aplique una de: (a)
  `@JsonIgnoreProperties(ignoreUnknown = true)` en `MqttTelemetryPayload`/`MetricsPayload`,
  o (b) extender el record con las métricas nuevas (recomendado a mediano plazo: habilita
  persistir/mostrar pH, N, P, K).

### 8. Timestamp: NTP con fallback

Con WiFi, NTP sella la lectura en epoch real. Sin red (para el buffer offline), un contador
monotónico (`esp_timer` + última hora NTP persistida en NVS) estima el timestamp, cumpliendo
HU-13 CA-03 (respetar timestamp original al sincronizar) sin RTC externo. RTC DS3231
documentado como mejora futura de precisión.

### 9. Seguridad de credenciales

`config.example.h` versionado con placeholders; `config.h` real ignorado por `.gitignore`
(coherente con la convención `env`/`env.example` del repo).

## Risks / Trade-offs

- **[Granularidad zona vs sector]** El backend actual es zona-level; `add-rules-engine`
  apunta a sector-level. → Mitigación: topic de telemetría configurable por config
  (`TELEMETRIA_NIVEL_SECTOR`); el nodo combinado del prototipo publica sector-level y se
  documenta que el receiver debe
  migrar (acoplado a `add-rules-engine` 6.5). El testigo puro sigue zona-level y funciona
  con el backend actual sin cambios.
- **[Payload extendido rompe la ingesta viva]** → Mitigación: flag
  `ENVIAR_METRICAS_EXTENDIDAS=false` por defecto; se activa tras la corrección backend.
- **[Doble dosis por reentrega]** → Mitigación: QoS 2 en `command` + dedup por `commandId`
  en el nodo + in-flight lock/cooldown del backend.
- **[Deriva de reloj sin RTC offline]** → Mitigación: NTP al reconectar + contador
  monotónico; RTC externo como mejora. Se acepta imprecisión menor en offline prolongado.
- **[Buffer NVS limitado]** → Mitigación: cola FIFO acotada con descarte del más antiguo y
  traza por serial.
- **[Concurrencia mal protegida]** → Mitigación: datos entre tareas solo por colas y cada
  estado con un único dueño (patrón de `ejemplo.ino`); test de humo de stacks/prioridades.
- **[Sensor de luz vs campo `uv`]** → Mitigación: driver abstraído + `TODO`; decisión de
  hardware pendiente (Open Questions).
- **[Sin hardware para validar]** → Mitigación: el firmware publica al mismo broker/topic
  que el simulador; se valida contra el backend real antes de tener el hardware físico.

## Migration Plan

1. Crear la estructura (`comun/`, `sensado/`, `actuacion/`, `platformio.ini`,
   `config.example.h`) y el arranque FreeRTOS.
2. Desplegar `env:nodo_sensor` con `ENVIAR_METRICAS_EXTENDIDAS=false` y topic zona-level →
   valida ingesta contra el backend actual sin romperlo.
3. En PRs backend aparte (previstos en `add-rules-engine`): tolerar/extender métricas;
   migrar receiver a sector-level; implementar publisher de comandos y ACK.
4. Activar `ENVIAR_METRICAS_EXTENDIDAS=true` y validar `env:nodo_combinado` /
   `env:nodo_actuador` contra comandos reales.

Rollback: al ser firmware, se reflashea la versión previa; el backend no se ve afectado
mientras el flag de métricas extendidas esté en `false` y el testigo publique zona-level.

## Open Questions

1. **Campo `uv`**: ¿el fotoresistor (LDR, % de luz) alimenta `uv`, o se incorpora un
   sensor UV real (GUVA-S12SD/ML8511)? Driver abstraído hasta confirmación.
2. **Migración de telemetría a sector-level en el backend**: ¿se hace dentro de
   `add-rules-engine` o como cambio propio? (afecta cuándo el nodo combinado deja de
   necesitar el modo zona-level).
3. **Persistencia offline del ACK del actuador**: ¿bufferizar acks si el backend está
   caído, o best-effort? (propuesto: best-effort en este PR).
