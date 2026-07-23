## Why

El sistema tiene todo el software (backend con motor de reglas, frontend, simulador
MQTT) pero **el firmware que corre en el hardware físico no existe**: hoy los nodos
ESP32 solo se representan mediante el `MqttTelemetrySimulator` del backend. Para pasar
del prototipo simulado al vivero real (Hito 3 y siguientes) necesitamos el código
embebido que lea los sensores reales, hable el contrato MQTT definido en
`add-mqtt-telemetry` / `add-rules-engine` y ejecute los actuadores.

El único artefacto embebido existente (`Desarrollo/embebido/sensores_unificados.ino`)
es un boceto que lee los tres sensores en un `loop()` con `delay()`, imprime por serial
y no tiene WiFi, MQTT, concurrencia, buffer offline ni estructura modular. No es
desplegable ni respeta el contrato del backend.

## What Changes

- **Firmware basado en FreeRTOS**: arquitectura de tareas concurrentes (tasks) que se
  comunican por colas (`Queue`), aprovechando los dos núcleos del ESP32 (patrón validado en
  `Desarrollo/embebido/ejemplo.ino`).
- **Tres firmwares independientes**, uno por tipo de nodo: `nodo_sensor` (solo sensado,
  nodo testigo), `nodo_actuador` (solo actuación) y `nodo_combinado` (sensado + actuación
  en un mismo ESP32 — **el que se usa en el prototipo**). Cada uno es su propio sketch con
  su `setup()` y su `task_red` a medida, **sin condicionales de perfil**: el código de cada
  nodo es directo y óptimo. La lógica reusable (red/MQTT/JSON/drivers) vive en carpetas
  compartidas (`comun/`, `sensado/`, `actuacion/`) y `build_src_filter` reparte a cada
  build solo lo que su rol necesita, así separar no duplica código. En producción a campo
  conviene separar sensores de actuadores; en el prototipo va todo junto (`nodo_combinado`).
- **Sin deep sleep**: el ahorro de energía se logra con tareas bien programadas
  (`vTaskDelay` que cede el núcleo entre muestreos) en lugar de un modo de suspensión
  profunda. Se elimina el deep sleep como modo específico del diseño.
- **Subsistema de sensado**: drivers independientes de luz (ADC), ambiente DHT11 y sonda
  de suelo NPK-pH-EC por RS-485/Modbus RTU; ensamblado del paquete de telemetría y
  publicación MQTT, incluyendo `battery` y `signal`.
- **Subsistema de actuación**: suscripción al topic de comando, ejecución de
  electroválvula (riego), bomba peristáltica (insumos) y motor de mediasombra, con
  lectura de feedback físico (caudalímetro, fin de carrera, corriente), límites de
  seguridad locales y publicación de **ACK** con `status: SUCCESS | ERROR`.
- **Contrato MQTT del nodo alineado con `add-rules-engine`**: tres tópicos a nivel de
  sector — `telemetry`, `command` y `ack` — con QoS 2 para comandos. Se documenta como
  fuente única para la implementación pendiente del `ActionExecutor` en el backend.
- **Buffer offline en flash (NVS)**: si no hay red/broker al momento de reportar, el nodo
  persiste la lectura con su timestamp original y la reenvía al reconectar sin duplicados
  (HU-03 CA-03, HU-13 CA-03).
- **Payload de telemetría extendido**: además de las 5 métricas del contrato actual, el
  firmware envía el set completo de la sonda de suelo (temp de suelo, pH, N, P, K,
  salinidad, TDS). **BREAKING** para el backend actual: `MqttTelemetryPayload` se
  deserializa con `ObjectMapper` por defecto (`FAIL_ON_UNKNOWN_PROPERTIES=true`), por lo
  que campos extra rompen el parseo. Se documenta el cambio backend acoplado y se protege
  con el flag `ENVIAR_METRICAS_EXTENDIDAS` (arranca en `false`).
- **Driver de luz abstraído**: el sensor de luz queda detrás de una interfaz con una
  constante de configuración y un `TODO`, pendiente de confirmar si el campo `uv` del
  contrato se alimenta con el fotoresistor (LDR, % de luz) o con un sensor UV real.
- **Estructura modular y buenas prácticas**: módulos separados (config, contrato, red,
  drivers, buffer), constantes nombradas, `struct`/`enum` para el modelo de datos, sin
  números mágicos, pensada para PlatformIO (un `env` por firmware) y compatible con Arduino
  IDE.

## Capabilities

### New Capabilities
- `firmware-nodo`: arquitectura del firmware — modelo de concurrencia FreeRTOS (tasks,
  colas), los tres firmwares independientes por tipo de nodo (sensor / actuador /
  combinado) con código compartido sin duplicar, conectividad WiFi/MQTT con reconexión,
  ahorro de energía por scheduling de tareas (sin deep sleep) y configuración centralizada.
- `firmware-sensado`: subsistema de sensado — lectura de los tres sensores mediante
  drivers tipados, ensamblado del paquete de telemetría (5 métricas base + extendidas),
  publicación MQTT, reporte de batería/señal y buffer offline en flash con reintento sin
  duplicados.
- `firmware-actuacion`: subsistema de actuación — suscripción al topic de comando,
  ejecución de riego/insumo/mediasombra con feedback físico, límites de seguridad locales
  y publicación de ACK (`SUCCESS`/`ERROR`).
- `contrato-mqtt-nodo`: contrato MQTT completo del nodo — tópicos a nivel de sector
  (`telemetry`, `command`, `ack`), payloads, QoS y semántica del ACK, como contrato de
  integración con el backend y su futuro `ActionExecutor`.

### Modified Capabilities
<!-- El contrato de telemetría MQTT vive como diseño en add-mqtt-telemetry / add-rules-engine
     pero no hay un spec vivo en openspec/specs/ que versione sus requisitos, así que la
     extensión del payload y el cambio de granularidad (zona → sector) se documentan como
     impacto/seguimiento en este cambio, no como delta de un spec existente. -->

## Impact

- **Código nuevo**: `Desarrollo/embebido/` — `comun/` (config, contrato, red, tipos,
  utils), `sensado/` (drivers + buffer), `actuacion/` (drivers de actuadores), tres sketches
  de arranque FreeRTOS (`nodo_sensor/`, `nodo_actuador/`, `nodo_combinado/`),
  `platformio.ini` con un `env` por firmware, y documentación del contrato.
- **Contrato consumido (alineado con `add-rules-engine`)**: tópicos sector-level
  `nursery/zone/{zonaId}/sector/{sectorId}/{telemetry|command|ack}`, QoS 2 en `command`,
  ACK con `status: SUCCESS|ERROR`.
- **Conflicto a reconciliar (documentado)**: el backend **ya implementado** suscribe
  telemetría a nivel **zona** (`nursery/zone/+/telemetry`, `MqttTelemetryReceiver`),
  mientras el design actualizado de `add-rules-engine` la define a nivel **sector**. El
  firmware usa un topic de telemetría configurable por config (zona para el testigo puro,
  sector para el combinado) y se documenta que el receiver del backend debe migrar a
  sector-level (trabajo acoplado, relacionado con `add-rules-engine` tarea 6.5).
- **Seguimiento backend acoplado (fuera de este PR)**:
  - Extender `MqttTelemetryPayload.MetricsPayload` con las métricas nuevas y/o configurar
    `@JsonIgnoreProperties(ignoreUnknown = true)`, para no romper la ingesta actual.
  - Implementar el canal de comando (`ActionExecutor` + publisher MQTT) según el
    `contrato-mqtt-nodo` (ya previsto en `add-rules-engine` tareas 1.3, 6.5).
  - Migrar el receiver de telemetría de zona a sector donde aplique.
  - Frontend/DB: exponer/persistir las métricas nuevas (pH, N, P, K, etc.).
- **Hardware**: ESP32 (ADC, UART2/RS-485, WiFi, PWM), sonda de suelo Modbus RTU, DHT11,
  sensor de luz; relés/drivers para electroválvula, bomba peristáltica y motor de
  mediasombra en los nodos con actuación (`nodo_actuador`, `nodo_combinado`).
- **Sin cambios** en frontend ni Modelo_IA en este PR.
