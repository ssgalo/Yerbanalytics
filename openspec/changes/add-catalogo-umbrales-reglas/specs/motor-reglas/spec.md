## MODIFIED Requirements

### Requirement: Trigger reactivo y Watchdog proactivo

El motor SHALL evaluarse en dos caminos complementarios:

- **Reactivo**: disparado por cada paquete de telemetría MQTT entrante.
- **Proactivo (Watchdog)**: un scheduler periódico que itera todos los sectores
  para detectar nodos caídos (sin telemetría reciente) mediante `StaleSensorRule`
  y para evaluar reglas independientes de telemetría fresca (como el plan de días
  de `MediasombraRule`).

Cada evaluación SHALL identificar su origen (`TELEMETRIA` o `BARRIDO`) para que su traza se
conserve por separado (ver `traza-evaluacion-reglas`).

#### Scenario: Watchdog detecta nodo sin reportar

- **WHEN** un sector supera el umbral de silencio (parámetro de catálogo
  `seguridad.antiguedad-max-lectura`) sin recibir telemetría MQTT
- **THEN** el Watchdog dispara la evaluación de ese sector y `StaleSensorRule` emite
  una alerta de nodo desconectado, y la traza registra la antigüedad recibida contra el umbral

#### Scenario: Cambio de etapa de rustificación sin telemetría

- **WHEN** el día del ciclo de siembra entra en una nueva etapa del plan de rustificación
  y no ha llegado telemetría reciente
- **THEN** el Watchdog dispara la evaluación y `MediasombraRule` emite `MOVER_MEDIASOMBRA`
