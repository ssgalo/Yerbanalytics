## ADDED Requirements

### Requirement: Botón "Leer ahora" e interfaz en el simulador
En el simulador (`Desarrollo/simulador/`), la interfaz web SHALL incluir una sección de **Modo Demo** con:
1. Controles para ingresar métricas ficticias de sensores para cualquier zona.
2. Controles para configurar clima ficticio y sincronizarlo con el backend.
3. Lista de reglas con interruptores (*toggles*) para habilitar, deshabilitar y bypasear cada regla.
4. Botón interactivo **"Leer ahora"**.

#### Scenario: Visualización del panel demo en el simulador
- **GIVEN** el simulador conectado al broker MQTT y al backend en modo demo
- **WHEN** el operador accede a la UI del simulador
- **THEN** se visualizan los controles de clima ficticio, el catálogo de reglas con sus interruptores y el botón "Leer ahora"

### Requirement: Ciclo ciberfísico interactivo con afectación en historial y actuación ESP32
Al pulsar el botón **"Leer ahora"** en el simulador, el sistema SHALL ejecutar la secuencia coordinada:
1. Si hay clima ficticio modificado, sincronizarlo vía `POST /backend/api/demo/weather`.
2. Solicitar un pulso de lectura manual con ejecución de motor (`POST /backend/api/pulsos { "ejecutarMotor": true }`).
3. El backend emite `LEER_AHORA` por MQTT (`nursery/zone/{zonaId}/command`).
4. El simulador publica la telemetría correspondiente en `nursery/zone/{zonaId}/telemetry`.
5. `NurseryService` evalúa las reglas activas a través del `RuleOrchestrator`.
6. Las acciones decididas por el motor SHALL persistirse en el historial agronómico (`historialService`), impactando las tablas y vistas del historial del vivero.
7. Las acciones resultantes de actuación física SHALL publicarse al ESP32 por MQTT (`nursery/zone/{zonaId}/sector/{sectorId}/command`).

#### Scenario: Ejecución de "Leer ahora" con déficit hídrico
- **GIVEN** un sector configurado con humedad de sustrato ficticia crítica (ej. 20%)
- **AND** clima ficticio sin lluvia
- **AND** reglas de riego habilitadas
- **WHEN** el usuario hace clic en "Leer ahora"
- **THEN** el motor evalúa el sector, `ActionExecutor` encola la electroválvula y publica el comando `valve ON` al ESP32 vía MQTT
- **AND** se crea una entrada en el historial del sector registrando la decisión y actuación agronómica
