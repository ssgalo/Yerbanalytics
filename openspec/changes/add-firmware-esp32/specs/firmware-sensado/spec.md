## ADDED Requirements

### Requirement: Muestreo periódico de sensores en tarea dedicada

El subsistema de sensado SHALL ejecutar el muestreo de los sensores en una tarea FreeRTOS
que se repite según un intervalo configurable (`SENSOR_POLLING_INTERVAL`, alineado con el
env var homónimo del backend), usando `vTaskDelay` entre ciclos. El intervalo SHALL estar
expresado como constante nombrada única.

#### Scenario: Ciclo de muestreo
- **WHEN** transcurre el intervalo configurado
- **THEN** la tarea lee los sensores, arma el paquete de telemetría y lo entrega a la cola
  de publicación, luego cede el núcleo con `vTaskDelay`.

### Requirement: Lectura de los sensores del nodo

El subsistema SHALL leer los tres sensores mediante drivers independientes: sensor de luz
(ADC), sensor ambiental DHT11 (humedad y temperatura del aire) y sonda de suelo NPK-pH-EC
por RS-485/Modbus RTU. Cada driver SHALL exponer su resultado en un `struct` tipado y
SHALL reportar un estado de validez (éxito/fallo) por sensor.

#### Scenario: Lectura Modbus válida
- **WHEN** la sonda de suelo responde con una trama Modbus de longitud y CRC correctos
- **THEN** el driver decodifica humedad, temperatura de suelo, EC, pH, N, P, K, salinidad
  y TDS aplicando la escala del datasheet y marca la lectura como válida.

#### Scenario: Sensor sin respuesta
- **WHEN** un sensor no responde o entrega datos inválidos (`NaN` del DHT11, o timeout/CRC
  inválido del Modbus)
- **THEN** el driver marca esa lectura como inválida SIN abortar el ciclo, y el paquete se
  arma con el resto de las métricas disponibles señalando las faltantes.

### Requirement: Ensamblado del payload de telemetría

El subsistema SHALL construir un payload JSON compatible con el contrato de ingesta,
incluyendo `mac`, `battery`, `signal`, `timestamp` y el objeto `metrics`. El objeto
`metrics` SHALL incluir las 5 métricas del contrato (`humSus`, `humAmb`, `temp`, `ce`,
`uv`) y, bajo el flag `ENVIAR_METRICAS_EXTENDIDAS`, las métricas extendidas de suelo
(`tempSuelo`, `phSuelo`, `n`, `p`, `k`, `salinidad`, `tds`).

#### Scenario: Compatibilidad con las 5 métricas base
- **WHEN** se serializa el payload
- **THEN** las claves `humSus`, `humAmb`, `temp`, `ce`, `uv` SHALL estar presentes con el
  mismo nombre y unidad que consume `MqttTelemetryPayload` en el backend.

#### Scenario: Métricas extendidas detrás de flag
- **WHEN** `ENVIAR_METRICAS_EXTENDIDAS` está en `false`
- **THEN** el payload incluye SOLO las 5 métricas base (100% compatible con el backend
  actual, que falla ante campos desconocidos); al activarse el flag se agregan las
  extendidas.

#### Scenario: Timestamp propio del nodo
- **WHEN** el nodo arma el paquete
- **THEN** el `timestamp` SHALL corresponder al instante real de la lectura (hora
  sincronizada por NTP cuando hay red, o contador monotónico persistido cuando no la hay),
  no al instante de recepción en el backend.

### Requirement: Reporte de estado del propio nodo

El subsistema SHALL incluir en cada paquete el nivel de batería (`battery`, porcentaje) y
la calidad de señal WiFi (`signal`, dBm RSSI) del nodo, para habilitar el panel de estado
técnico del hardware (HU-21).

#### Scenario: Telemetría de salud del nodo
- **WHEN** se publica un paquete
- **THEN** `battery` y `signal` reflejan la medición actual del nodo en el momento del
  ciclo.

### Requirement: Publicación MQTT con topic derivado de la configuración

El subsistema SHALL publicar el paquete por MQTT (QoS 1 para telemetría) en el topic
correspondiente a su ubicación, tomando `zonaId`/`sectorId` de la configuración del nodo.
El nivel del topic SHALL depender del flag `TELEMETRIA_NIVEL_SECTOR` de la configuración
(ver capability `contrato-mqtt-nodo`).

#### Scenario: Topic según configuración
- **WHEN** el nodo publica telemetría
- **THEN** usa el topic configurado (zona para el testigo puro, sector para el combinado),
  coherente con el contrato de ingesta.

### Requirement: Buffer offline en flash con reintento sin duplicados

Cuando el nodo no logra publicar (sin WiFi o sin broker), el subsistema SHALL persistir la
lectura con su timestamp original en memoria no volátil (NVS) y SHALL reintentar el envío
al recuperar conectividad, sin perder el orden temporal ni generar duplicados (HU-03
CA-03, HU-13 CA-03).

#### Scenario: Sin conexión al momento de reportar
- **WHEN** la tarea de sensado produce un paquete y la red no está disponible
- **THEN** el paquete se almacena en flash con su timestamp original sin descartarse.

#### Scenario: Drenaje del buffer al reconectar
- **WHEN** el nodo recupera conectividad
- **THEN** publica primero los registros pendientes en orden cronológico, cada uno con su
  timestamp original, antes o junto con la lectura del ciclo actual.

#### Scenario: Sin duplicados tras publicación exitosa
- **WHEN** un registro bufferizado se publica con confirmación
- **THEN** el firmware lo elimina del buffer para que no se reenvíe.

#### Scenario: Límite del buffer
- **WHEN** el buffer alcanza su capacidad máxima configurada
- **THEN** el firmware descarta el registro más antiguo (FIFO) para preservar los más
  recientes, dejando traza del descarte por serial.

### Requirement: Driver de luz abstraído y configurable

El subsistema SHALL encapsular la lectura de luz detrás de un driver que devuelva el valor
que alimenta el campo `uv`, con una constante de configuración que documente si la fuente
es el fotoresistor (LDR, % de luz) o un sensor UV real, dejando un `TODO` explícito hasta
confirmar el hardware definitivo.

#### Scenario: Fuente de luz intercambiable
- **WHEN** se cambie de fotoresistor a un sensor UV real
- **THEN** SHALL bastar con ajustar el driver de luz y su constante, sin tocar el
  ensamblado del payload ni el resto del firmware.
