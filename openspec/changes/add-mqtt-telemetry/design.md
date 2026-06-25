# Design: add-mqtt-telemetry

## Context
El backend requiere recibir lecturas en tiempo real para las 5 variables críticas (`humSus`, `humAmb`, `temp`, `ce`, `uv`) y el estado de la batería de los nodos testigo.

## Decisions

### 1. Topología de Topics MQTT
Usaremos un esquema jerárquico y extensible para la comunicación:
- **Uplink (Telemetría de entrada):** `nursery/zone/{zoneId}/telemetry` (ej. `nursery/zone/MZ-1/telemetry`)
  - *¿Por qué a nivel de macro-zona y no sector?* Porque la arquitectura física define 10 sensores testigo (uno por macro-zona para inferir el estado de 100 sectores circundantes).
  - *Payload esperado (JSON):*
    ```json
    {
      "mac": "24:0a:c4:12:34:56",
      "battery": 85,
      "timestamp": 1782414800,
      "metrics": {
        "humSus": 52.4,
        "humAmb": 72.1,
        "temp": 23.5,
        "ce": 1.2,
        "uv": 4.0
      }
    }
    ```

### 2. Uso de Spring Integration MQTT
Se utilizará `spring-integration-mqtt` por su estabilidad y su abstracción nativa con Spring Framework. Se define un adaptador de entrada `MqttPahoMessageDrivenChannelAdapter` para gestionar la conexión y suscripción a los topics.

### 3. Simulador de Dispositivos (Background Worker)
Un componente `@Component` con `@Scheduled` que se conecte como un cliente publicador MQTT separado y envíe datos periódicamente (ej. cada 10 segundos) a los topics de las macro-zonas `MZ-1` a `MZ-6` usando un generador de telemetría para obtener valores lógicos y dinámicos. Esto se activa solo si un flag `yerbanalytics.mqtt.simulator.enabled=true` está encendido en la configuración.

### 4. Actualización del Estado del Vivero
El `NurseryService` mantendrá en memoria un mapa del último reporte de telemetría recibido para cada macro-zona. Al realizar `getSnapshot()`, en lugar de generar datos completamente aleatorios en cada sector, se sobreescribirán las métricas de los sectores correspondientes con las lecturas reales del sensor testigo de dicha zona, de modo que el frontend vea reflejados los cambios en tiempo real en la interfaz.
