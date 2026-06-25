# Tasks: add-mqtt-telemetry

## Checklist de Implementación

- [x] Agregar dependencias de Spring Integration MQTT en `pom.xml`.
- [x] Crear `docker-compose.yml` en la raíz del proyecto para el broker Mosquitto.
- [x] Configurar propiedades de conexión del broker MQTT en `application.properties`.
- [x] Crear el DTO `MqttTelemetryPayload` para parsear los mensajes JSON del ESP32.
- [x] Implementar la clase de configuración `MqttConfig` para instanciar el cliente y adaptador de entrada MQTT.
- [x] Implementar el manejador de mensajes MQTT que actualice las métricas en caché de `NurseryService`.
- [x] Crear el simulador `MqttTelemetrySimulator` que corra en segundo plano publicando mensajes periódicos.
- [x] Levantar un broker MQTT local (ej. Mosquitto) y verificar el flujo de extremo a extremo.
