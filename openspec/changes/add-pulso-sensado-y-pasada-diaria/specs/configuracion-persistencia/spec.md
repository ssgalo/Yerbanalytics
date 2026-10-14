## ADDED Requirements

### Requirement: Programación persistida de la pasada diaria

El backend SHALL persistir en la configuración operativa `pasadaDiariaActiva` (booleano, de fábrica `false`) y
`pasadaDiariaHora` (texto `HH:mm` entre `00:00` y `23:59`, de fábrica `"11:00"`). `GET /api/configuracion` SHALL
devolverlos y `PUT /api/configuracion` SHALL actualizarlos, con la misma validación y auditoría que el resto de la
configuración.

#### Scenario: Valores de fábrica
- **GIVEN** una base recién sembrada o una existente tras arrancar con este cambio
- **WHEN** se invoca `GET /api/configuracion`
- **THEN** `pasadaDiariaActiva` es `false` y `pasadaDiariaHora` es `"11:00"`

#### Scenario: Activar y cambiar la hora
- **WHEN** se invoca `PUT /api/configuracion` con `pasadaDiariaActiva: true` y `pasadaDiariaHora: "10:30"`
- **THEN** el siguiente `GET /api/configuracion` devuelve esos valores

#### Scenario: Hora inválida
- **WHEN** se invoca `PUT /api/configuracion` con `pasadaDiariaHora` "25:00", "9:5" o vacía
- **THEN** el backend responde `400` nombrando el parámetro y no persiste nada

## MODIFIED Requirements

### Requirement: Effective stale threshold exposed

The backend SHALL expose the effective stale threshold (`max(floor, intervalo del pulso + ventana de respuesta)`,
see `sensing-cadence`) to the frontend, so the UI does not hardcode it.
(Previously: `max(floor, 3 × sensingPullIntervalSeconds)`.)

#### Scenario: Threshold follows the saved interval
- **GIVEN** floor = 90 s and a 180 s response window
- **WHEN** `intervaloSensadoMinutos` is saved as 120
- **THEN** the backend reports an effective stale threshold of 7 380 s

## REMOVED Requirements

### Requirement: Persisted sensing pull interval
**Reason**: El pedido de lectura cada X segundos se reemplaza por el pulso de sensado, cuyo intervalo es
`intervaloSensadoMinutos`. Tener dos intervalos de sensado independientes era la causa de que el motor corriera
cada minuto.
**Migration**: `sensingPullIntervalSeconds` deja de existir en el DTO, en `GET/PUT /api/configuracion` y en la
entidad, y lo mismo `intervaloEvaluacionMinutos` (el barrido que gobernaba se elimina, ver `motor-reglas`). Un `PUT`
que todavía los mande los ignora (Jackson descarta propiedades desconocidas). Las columnas
`sensing_pull_interval_seconds` e `intervalo_evaluacion_minutos` se borran con
`resources/migracion-pulso-sensado.sql`, a correr a mano.
