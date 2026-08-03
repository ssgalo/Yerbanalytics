# Tasks: add-firmware-esp32

## 1. Scaffolding, firmwares separados y base común (`comun/`)

- [x] 1.1 Crear el proyecto PlatformIO en `Desarrollo/embebido/` con `platformio.ini` y
  tres `env` independientes (`nodo_sensor`, `nodo_actuador`, `nodo_combinado`), cada uno
  con su `build_src_filter` (su `.ino` + `comun/` + las carpetas de su rol), sin build
  flags de perfil; dependencias: PubSubClient, ArduinoJson, DHT sensor library.
- [x] 1.2 Crear `comun/config.example.h` con placeholders (SSID, pass, broker, `zonaId`,
  `sectorId`, pines, `SENSOR_POLLING_INTERVAL`, flag `ENVIAR_METRICAS_EXTENDIDAS`,
  prioridades/stacks de tareas) y agregar `config.h` real a `.gitignore`.
- [x] 1.3 Crear `comun/contrato.h` con los topics (telemetry/command/ack, plantillas
  zona y sector) y las claves JSON, como fuente única (sin literales dispersos).
- [x] 1.4 Crear `comun/tipos.h` con `struct`/`enum` del dominio (`LecturaSuelo`,
  `LecturaAmbiente`, `LecturaLuz`, `PaqueteTelemetria`, `Comando`, `Ack`, `EstadoLectura`,
  `EstadoAck`).
- [x] 1.5 Implementar `comun/net_wifi` (conexión, reconexión, lectura de RSSI).
- [x] 1.6 Implementar `comun/net_mqtt` (wrapper de PubSubClient: connect, publish con QoS,
  subscribe con callback, loop), parametrizable por `clientId`.
- [x] 1.7 Implementar `comun/reloj` (NTP + fallback monotónico con última hora en NVS) y
  `comun/util_json` (armado/parseo con ArduinoJson).

## 2. Arranque FreeRTOS y tarea de red

- [x] 2.1 Implementar el `setup()` de cada sketch (`nodo_sensor.ino`, `nodo_actuador.ino`,
  `nodo_combinado.ino`): crear las colas que su rol usa (`cola_telemetria`/`cola_comandos`/
  `cola_ack`), inicializar sus periféricos y lanzar sus tareas con prioridades/stacks. Cada
  sketch solo arma lo que su rol necesita (sin ramas de perfil).
- [x] 2.2 Implementar `task_red`: WiFi+MQTT con reconexión, `client.loop()`, drenaje de
  `cola_telemetria` (publicar o derivar a buffer offline) y publicación de `cola_ack`.
- [x] 2.3 Verificar en `loop()` vacío / cesión correcta de CPU (`vTaskDelay`), sin
  `delay()` bloqueantes ni deep sleep.

## 3. Subsistema de sensado (`sensado/`)

- [x] 3.1 Portar el driver de suelo Modbus RTU del boceto a `sensor_suelo` con `struct` de
  resultado, validación de longitud/CRC, flag de validez y escalas del datasheet.
- [x] 3.2 Implementar `sensor_dht` (humedad y temperatura de aire) con manejo de `NaN`.
- [x] 3.3 Implementar `sensor_luz` como driver abstraído (LDR hoy) con constante de
  configuración y `TODO` de sensor UV real; alimenta el campo `uv`.
- [x] 3.4 Implementar `task_sensado`: muestreo cada `SENSOR_POLLING_INTERVAL` con
  `vTaskDelay`, ensamblado del `PaqueteTelemetria` y envío a `cola_telemetria`.
- [x] 3.5 Ensamblado JSON: 5 métricas base siempre; extendidas de suelo bajo el flag
  `ENVIAR_METRICAS_EXTENDIDAS`. Incluir `battery` (ADC) y `signal` (RSSI).
- [x] 3.6 Implementar `buffer_offline` en NVS: persistir con timestamp original cuando no
  hay envío, cola FIFO acotada con descarte del más antiguo.
- [x] 3.7 Drenaje del buffer al reconectar (orden cronológico, borrado tras publicación
  confirmada, sin duplicados).

## 4. Subsistema de actuación (`actuacion/`)

- [x] 4.1 Implementar el callback MQTT de comando: validar y empujar a `cola_comandos`
  (sin ejecutar en el contexto del callback).
- [x] 4.2 Implementar `dedup_comandos` (idempotencia por `commandId`, defensa en
  profundidad sobre el in-flight lock del backend).
- [x] 4.3 Implementar `act_valvula`: apertura/cierre por tiempo, lectura de
  caudalímetro/presión y detección de falla hidráulica.
- [x] 4.4 Implementar `act_bomba`: inyección de volumen exacto (ml) con corte al objetivo.
- [x] 4.5 Implementar `act_mediasombra`: movimiento a posición objetivo, fin de carrera y
  corte por sobrecorriente/atasco.
- [x] 4.6 Implementar límites de seguridad locales (tiempo máx, volumen/dosis máx) como
  última barrera.
- [x] 4.7 Implementar `task_actuacion`: consume `cola_comandos`, ejecuta el actuador y
  empuja el resultado a `cola_ack` con `status: SUCCESS|ERROR`.

## 5. Documentación del contrato y arranque

- [x] 5.1 Escribir `Desarrollo/embebido/README.md`: procedimiento por tipo de nodo
  (PlatformIO envs / Arduino IDE), wiring/pines por nodo y guía de `config.h`.
- [x] 5.2 Documentar en el README el **contrato MQTT** (topics telemetry/command/ack,
  payloads, QoS 2 en command, ACK `SUCCESS`/`ERROR`) como fuente única para el
  `ActionExecutor` del backend (`add-rules-engine`).
- [x] 5.3 Documentar el **payload extendido** y el cambio backend acoplado
  (`@JsonIgnoreProperties(ignoreUnknown=true)` o extensión del record + migración del
  receiver a sector-level + front/DB) y la estrategia del flag `ENVIAR_METRICAS_EXTENDIDAS`.

## 6. Validación (requiere hardware/broker real — pendiente)

- [ ] 6.1 Validar `env:nodo_sensor` (topic zona-level, `ENVIAR_METRICAS_EXTENDIDAS=false`)
  contra el broker/backend reales: el paquete se ingesta igual que el del simulador.
- [ ] 6.2 Validar el buffer offline: simular caída de red, verificar persistencia y
  drenaje sin duplicados al reconectar.
- [ ] 6.3 Validar `env:nodo_actuador`/`nodo_combinado` contra comandos MQTT publicados
  manualmente (mosquitto_pub) según el contrato: ejecución, ACK `SUCCESS`/`ERROR`,
  deduplicación y límites de seguridad.
- [ ] 6.4 Prueba de concurrencia/estabilidad: verificar que la caída de WiFi no frena el
  muestreo ni la actuación, y que no hay corrupción de datos entre tareas (colas).
