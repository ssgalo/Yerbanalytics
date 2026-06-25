# Change: add-mqtt-telemetry

## Why
El sistema necesita recibir datos en tiempo real de los dispositivos físicos (ESP32) instalados en el vivero. Dado que el hardware no está listo aún, requerimos:
1. Establecer la arquitectura y el contrato de comunicación IoT mediante MQTT.
2. Implementar un cliente MQTT en el backend de Spring Boot para suscribirse a los datos de telemetría.
3. Crear un simulador de ESP32 en el backend que publique datos de prueba al broker MQTT para validar el flujo completo.

## What Changes
- **Backend Dependencies**: Se añaden `spring-integration-mqtt` y `spring-boot-starter-integration` a `pom.xml`.
- **Configuración**: Propiedades de conexión al broker MQTT en `application.properties`.
- **Ingestión**: Creación de un canal de integración y manejador de mensajes MQTT para recibir telemetría de sustrato y clima.
- **Simulación**: Un componente `MqttTelemetrySimulator` que emula el envío periódico de telemetría de los 10 sensores testigo.
- **Servicios**: Actualización de `NurseryService` para integrar las lecturas en tiempo real con el estado actual del vivero.

## Impact
- **Affected specs:** `backend-core`, `telemetry-contract`.
- **Affected code:** `pom.xml`, `application.properties`, nuevo paquete `com.yerbanalytics.backend.mqtt`.
- **Frontend:** Ninguno en esta fase (sigue consultando `GET /api/nursery`).

## Non-Goals
- No se implementa persistencia en base de datos en esta fase (se mantiene en caché en memoria por ahora).
- No se implementa la comunicación de actuadores por MQTT hacia el exterior (se centrará en telemetría de entrada/uplink).
