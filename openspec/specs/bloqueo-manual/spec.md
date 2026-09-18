# bloqueo-manual

> **Estado de implementación:**
> - ✅ **Motor (backend):** `BloqueoManualEntity`, `BloqueoManualRepository`, `BloqueoManualRule` están implementados. `NurseryService` y `NurseryWatchdog` consultan el repositorio en cada ciclo.
> - ❌ **REST API:** No existe `BloqueoController`. Los endpoints `GET/POST/DELETE /api/bloqueos` y el registro de bloqueos en historial son **pendientes de implementación**.

## Purpose

Gestión del bloqueo manual de actuación autónoma por el operario (HU-19).
Permite al operario activar y desactivar bloqueos sobre un sector o una zona
completa, suspendiendo toda acción autónoma del motor mientras el bloqueo está
activo. Cubre la persistencia en `bloqueo_manual` y el endpoint REST de gestión.

## Requirements

### Requirement: Activación de bloqueo por sector o zona

El sistema SHALL permitir al operario activar un bloqueo manual sobre un sector
individual o sobre una zona completa. Al activarse, SHALL quedar persistido en la
tabla `bloqueo_manual` con el identificador del sector (o `null` para bloqueo
zonal), el identificador de la zona, el usuario que lo activó, el timestamp y el
motivo. El bloqueo SHALL comenzar activo (`activo = true`) inmediatamente.

#### Scenario: Activar bloqueo de sector

- **WHEN** el operario activa un bloqueo sobre el sector "S-1"
- **THEN** se persiste un registro en `bloqueo_manual` con `sectorId = "S-1"`,
  `zonaId` de su zona, `activo = true` y `activadoTs = ahora`

#### Scenario: Activar bloqueo zonal

- **WHEN** el operario activa un bloqueo sobre la zona "Z-1" sin especificar sector
- **THEN** se persiste un registro con `sectorId = null`, `zonaId = "Z-1"` y
  `activo = true`, que afecta a todos los sectores de esa zona

### Requirement: Desactivación de bloqueo

El sistema SHALL permitir desactivar un bloqueo activo por su identificador,
marcando `activo = false`. La desactivación SHALL ser inmediata: en el ciclo
de telemetría siguiente, el motor evaluará el sector nuevamente sin el bloqueo.

#### Scenario: Desactivar bloqueo existente

- **WHEN** el operario desactiva el bloqueo con id `42`
- **THEN** ese registro queda con `activo = false` y el motor ya no lo considera
  en la próxima evaluación del sector

#### Scenario: Desactivar bloqueo inexistente

- **WHEN** se solicita desactivar un bloqueo con id que no existe
- **THEN** el backend responde con `404`

### Requirement: Efecto bloqueante sobre el motor de reglas

Mientras exista al menos un bloqueo activo para un sector (o para su zona),
`BloqueoManualRule` SHALL emitir `ABORT_ALL` y el motor SHALL detener la evaluación
de ese sector en ese ciclo. NO SHALL activarse ningún actuador ni publicarse ningún
comando MQTT mientras el bloqueo esté vigente.

#### Scenario: Motor paralizado por bloqueo activo

- **WHEN** hay un bloqueo manual activo para el sector "S-1" y llega telemetría
- **THEN** el motor emite `ABORT_ALL` para ese sector, lo registra en el historial
  como inacción y no publica ningún comando MQTT

#### Scenario: Motor retoma al desactivar el bloqueo

- **WHEN** el operario desactiva el bloqueo y llega el siguiente ciclo de telemetría
- **THEN** el motor evalúa todas las reglas normalmente para ese sector

### Requirement: Consulta de bloqueos activos

El backend SHALL exponer un endpoint de consulta que devuelva todos los bloqueos
activos, con soporte de filtro por zona o sector.

#### Scenario: Listar bloqueos activos

- **WHEN** se consulta `GET /api/bloqueos` sin filtros
- **THEN** se devuelven todos los registros con `activo = true`, ordenados por
  `activadoTs` descendente

#### Scenario: Filtrar por zona

- **WHEN** se consulta `GET /api/bloqueos?zona=Z-1`
- **THEN** se devuelven solo los bloqueos activos de esa zona

### Requirement: Endpoints REST de gestión

El backend SHALL exponer:
- `GET /api/bloqueos` — listar bloqueos activos (con filtros opcionales `zona`, `sector`)
- `POST /api/bloqueos` — activar un nuevo bloqueo (body: `zonaId`, `sectorId?`, `activadoPor`, `motivo`)
- `DELETE /api/bloqueos/{id}` — desactivar un bloqueo por id

No SHALL existir endpoint de edición: solo se crean y se desactivan.

#### Scenario: Alta de bloqueo via POST

- **WHEN** se invoca `POST /api/bloqueos` con payload válido
- **THEN** el backend persiste el bloqueo y devuelve `201` con el id asignado

#### Scenario: Alta sin zona

- **WHEN** se invoca `POST /api/bloqueos` sin `zonaId`
- **THEN** el backend responde `400`

### Requirement: Registro de bloqueo en el historial

Cada activación y desactivación de un bloqueo SHALL quedar registrada en
`historial_evento` con tipo `"BloqueoManual"`, el usuario responsable y el motivo,
para mantener la trazabilidad de las intervenciones manuales.

#### Scenario: Historial al activar

- **WHEN** el operario activa un bloqueo
- **THEN** se persiste un evento en `historial_evento` de tipo `"BloqueoManual"`,
  con la decisión del operario y el sector/zona afectado

#### Scenario: Historial al desactivar

- **WHEN** el operario desactiva un bloqueo
- **THEN** se persiste un evento de tipo `"BloqueoManual"` indicando la reactivación
  de la actuación autónoma
