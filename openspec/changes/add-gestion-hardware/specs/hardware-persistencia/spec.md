# Spec: hardware-persistencia

## ADDED Requirements

### Requirement: Registro de dispositivos
El backend SHALL persistir un registro de dispositivos físicos, cada uno con un serial/MAC
único, su tipo (nodo testigo, electroválvula, bomba peristáltica o mediasombra) y su
vínculo espacial: el nodo testigo a una macro-zona y los actuadores a un sector (HU-18
CA-02).

#### Scenario: Alta de un dispositivo asociado a su posición
- **WHEN** se invoca `POST /api/hardware` con un serial/MAC, un tipo y su sector o
  macro-zona
- **THEN** el backend persiste el dispositivo vinculado a esa posición
- **AND** queda disponible en `GET /api/hardware` atribuido a su sector o macro-zona

### Requirement: Validación de unicidad del alta
El backend SHALL rechazar el alta de un serial/MAC ya existente o de un segundo actuador
del mismo tipo en un mismo sector, evitando ambigüedades de trazabilidad y dobles
asignaciones (HU-18 CA-03).

#### Scenario: Serial/MAC duplicado
- **WHEN** se da de alta un dispositivo con un serial/MAC ya registrado
- **THEN** el backend rechaza el alta con un conflicto y un mensaje que indica la causa,
  sin persistir nada

#### Scenario: Actuador duplicado en un sector
- **WHEN** se da de alta un actuador de un tipo que el sector ya tiene asignado
- **THEN** el backend rechaza el alta con un conflicto, sin persistir nada

### Requirement: Mapeo incompleto de sectores
El backend SHALL identificar los sectores que tienen al menos un dispositivo mapeado pero
les falta alguno de los actuadores requeridos (electroválvula, bomba peristáltica,
mediasombra), señalando que la actuación autónoma queda deshabilitada en ellos (HU-18
CA-04).

#### Scenario: Sector con actuador faltante
- **WHEN** un sector tiene mapeado parte del equipamiento pero falta un actuador
  requerido
- **THEN** `GET /api/hardware` lo incluye en la lista de sectores incompletos con los
  tipos de actuador faltantes

### Requirement: Estado técnico derivado
El backend SHALL derivar el estado operativo de cada dispositivo (Operativo / Señal
intermitente / Fuera de servicio) a partir de su último reporte y sus fallas, y SHALL
marcar como "Batería Baja" a los nodos cuya batería cae bajo el umbral configurado, sin
sacarlos de operación (HU-21 CA-01/CA-02/CA-03).

#### Scenario: Nodo sin reportar por encima del umbral crítico
- **WHEN** un nodo testigo no reporta su heartbeat por encima del umbral crítico
- **THEN** el backend lo marca como "Fuera de servicio"

#### Scenario: Batería bajo el umbral
- **WHEN** la batería de un nodo cae bajo el umbral mínimo configurado
- **THEN** el backend lo marca como "Batería Baja" manteniéndolo operativo

#### Scenario: Estado por dispositivo en el panel
- **WHEN** se invoca `GET /api/hardware`
- **THEN** cada dispositivo informa su tipo, sector/macro-zona, batería, señal, último
  update y estado operativo

### Requirement: Heartbeat por telemetría
El backend SHALL actualizar la batería, la señal y el último update del nodo testigo de
una macro-zona a partir de la telemetría MQTT recibida para esa zona (HU-21 CA-01).

#### Scenario: Telemetría actualiza el nodo testigo
- **WHEN** llega telemetría de una macro-zona cuyo nodo testigo está registrado
- **THEN** el backend actualiza la batería, la señal y el último update de ese nodo

### Requirement: Falla física y recambio
El backend SHALL reflejar la falla física de un dispositivo asociada a su sector y
mantenerlo señalado como averiado hasta su recambio, y SHALL ofrecer un recambio que
reutiliza el registro, limpia la avería y reanuda el monitoreo (HU-21 CA-04/CA-05).

#### Scenario: Dispositivo averiado
- **WHEN** un dispositivo tiene una falla física registrada
- **THEN** `GET /api/hardware` lo refleja como averiado, asociado a su sector, hasta su
  recambio

#### Scenario: Recambio de una pieza
- **WHEN** se invoca `PUT /api/hardware/{id}` con el serial de la pieza nueva
- **THEN** el backend limpia la avería, revincula el dispositivo a su posición y reanuda
  su monitoreo

### Requirement: Endpoints de hardware
El backend SHALL exponer `GET /api/hardware` (flota con estado técnico, KPIs y sectores
incompletos), `POST /api/hardware` (alta validada) y `PUT /api/hardware/{id}` (recambio).
Un conflicto de unicidad SHALL responder con un error de conflicto y un dato inválido con
un error de solicitud, sin persistir.

#### Scenario: Consulta de la flota
- **WHEN** se invoca `GET /api/hardware`
- **THEN** devuelve la lista de dispositivos con su estado, los KPIs de la flota y los
  sectores incompletos
