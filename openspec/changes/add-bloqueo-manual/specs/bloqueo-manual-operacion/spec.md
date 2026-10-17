## ADDED Requirements

Complementa la spec viva `bloqueo-manual`: ésta ya define los endpoints; este cambio los implementa y suma
el cierre inmediato, las alertas y la interfaz.

### Requirement: Cierre inmediato al activar un bloqueo
Al activar un bloqueo, el sistema SHALL ordenar el cierre de las válvulas y el apagado de las bombas del
alcance (sector, o todos los sectores de la macro-zona) y SHALL cancelar el riego encolado de ese alcance.

#### Scenario: Bloqueo de sector con riego en curso
- **WHEN** un Operario bloquea S-045 mientras su válvula está abierta
- **THEN** sale el comando de cierre de la válvula de S-045 y el riego pendiente de S-045 se cancela

#### Scenario: Bloqueo de macro-zona
- **WHEN** un Operario bloquea MZ-2
- **THEN** se cierran las válvulas y bombas de todos los sectores de MZ-2 y la cola de riego de MZ-2 queda vacía

### Requirement: Alertas del bloqueo
Activar y reanudar un bloqueo SHALL dejar una alerta `INFO` en el historial con el usuario, el alcance y,
al activar, el motivo.

#### Scenario: Activación
- **WHEN** se activa un bloqueo con motivo "mantenimiento de la línea"
- **THEN** el historial muestra una alerta INFO con ese motivo y el usuario

### Requirement: Reanudar no actúa retroactivamente
Al reanudar, el sistema SHALL evaluar sólo con la telemetría vigente en la próxima lectura y NO SHALL
reponer riegos ni aplicaciones que se cancelaron durante el bloqueo.

#### Scenario: Reanudar con el sustrato seco
- **WHEN** se reanuda el bloqueo de MZ-2 y la última humedad es 40 %
- **THEN** no se ordena ningún riego en ese instante; R-01 decide en el siguiente pulso

### Requirement: Permisos del bloqueo
Las rutas de bloqueo SHALL exigir `bloqueos.ver` para leer y `bloqueos.gestionar` para activar o reanudar.

#### Scenario: Sin permiso de gestión
- **WHEN** un usuario con sólo `bloqueos.ver` invoca `POST /api/bloqueos`
- **THEN** la respuesta es 403

### Requirement: Interfaz de bloqueo
El dashboard SHALL ofrecer bloquear y reanudar desde el detalle de sector y de macro-zona, exigir un
motivo, y mostrar el estado "Bloqueado" con quién y desde cuándo. Sólo se muestra a quien tiene el permiso.

#### Scenario: Motivo vacío
- **WHEN** el operario confirma el bloqueo sin escribir motivo
- **THEN** el formulario no se envía y marca el campo

#### Scenario: Sin permiso
- **WHEN** un usuario sin `bloqueos.gestionar` abre el detalle de un sector
- **THEN** no ve el botón de bloquear
