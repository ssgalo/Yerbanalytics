# Change: add-rules-engine

## Why

Hoy la lógica de decisión del sistema vive **dispersa** dentro de
`NurseryService.updateTelemetry()`: evaluación de umbrales, apertura de
electroválvula, dosificación de insumo y registro en historial se resuelven en
un solo método transaccional de ~90 líneas. Esto funciona para el MVP simulado,
pero **no escala** al momento de incorporar:

- La **API meteorológica** (HU-09) con reintentos y fallback.
- El **modelo de IA** (HU-04) con su confidence score como gate de actuación.
- La **mediasombra/rustificación** (HU-08) con su plan por días.
- Los **bloqueos manuales** (HU-19) del operario.
- El **modo offline** (HU-13) donde el motor corre localmente en el nodo.

El motor de reglas debe vivir en el **backend** (Spring Boot) porque es el
único componente con acceso centralizado a: telemetría de sensores (MQTT),
API climática externa, diagnóstico del modelo de IA, configuración agronómica,
estado de actuadores/bloqueos, e historial inmutable.

El frontend **no toma decisiones**; solo visualiza el resultado.

## What Changes

- **Abstracción `Rule`**: interfaz común con prioridad, nombre y método
  `evaluate(RuleContext)` que devuelve acciones.
- **`RuleContext`**: value object inmutable que consolida el snapshot del
  sector, métricas evaluadas, forecast climático, diagnóstico IA, bloqueos
  y configuración operativa.
- **Reglas concretas** (una clase por decisión de negocio): `BloqueoManualRule`,
  `StaleSensorRule`, `ClimaOverrideRule`, `RiegoRule`, `InsumoRule`,
  `MediasombraRule`, `DosisLimiteRule`, `SeguimientoRule`.
- **`RuleOrchestrator`**: coordinador que itera las reglas ordenadas por
  prioridad y corta la cadena ante acciones bloqueantes.
- **`ActionExecutor`**: materializa acciones — publica comandos MQTT a
  actuadores, persiste en `historial_evento` y emite alertas.
- **`WeatherService`**: cliente asíncrono de la API climática con retry
  exponential backoff y fallback (HU-09).
- **Tabla `bloqueo_manual`**: persistencia de bloqueos del operario (HU-19).
- **Extracción**: la lógica inline actual de `updateTelemetry()` migra a las
  `Rule` correspondientes sin cambiar el comportamiento observable.

## Impact

- Affected specs: `rules-engine` (nueva).
- Affected code: `Desarrollo/backend/` — paquete nuevo `engine/` y refactor
  de `NurseryService`, `HistorialService`.
- No toca frontend ni Modelo_IA (el motor los consume como inputs, no los
  modifica).
- Historias de usuario cubiertas: HU-04 (gate IA), HU-06 (riego), HU-07
  (insumo), HU-08 (mediasombra), HU-09 (clima), HU-10 (alertas), HU-11
  (trazabilidad), HU-12 (seguimiento), HU-15 (configuración), HU-19
  (bloqueo manual).
