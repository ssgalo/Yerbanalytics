## MODIFIED Requirements

### Requirement: Estado técnico derivado
El backend SHALL derivar el estado operativo de cada dispositivo (Operativo / Señal
intermitente / Fuera de servicio) y SHALL marcar como "Batería Baja" a los nodos cuya batería cae bajo el umbral
configurado, sin sacarlos de operación (HU-21 CA-01/CA-02/CA-03). Para el nodo testigo de una macro-zona el estado
SHALL seguir los pulsos de sensado consecutivos sin respuesta (ver `pulso-sensado`): 0 → Operativo, 1 o 2 → Señal
intermitente, 3 o más → Fuera de servicio; no SHALL depender del tiempo transcurrido desde su último reporte, porque
entre pulsos el nodo no reporta. Los demás dispositivos conservan el criterio por último reporte y fallas.
(Previously: el nodo testigo pasaba a intermitente a los 60 s y a fuera de servicio a los 120 s sin heartbeat.)

#### Scenario: Nodo entre pulsos
- **GIVEN** un nodo testigo que respondió al último pulso hace 3 h
- **WHEN** se invoca `GET /api/hardware`
- **THEN** el nodo figura "Operativo"

#### Scenario: Nodo que no respondió un pulso
- **GIVEN** un nodo testigo con 1 pulso consecutivo sin respuesta
- **WHEN** se invoca `GET /api/hardware`
- **THEN** el nodo figura "Señal intermitente"

#### Scenario: Nodo sin responder tres pulsos
- **GIVEN** un nodo testigo con 3 pulsos consecutivos sin respuesta
- **WHEN** se invoca `GET /api/hardware`
- **THEN** el backend lo marca como "Fuera de servicio"

#### Scenario: Batería bajo el umbral
- **WHEN** la batería de un nodo cae bajo el umbral mínimo configurado
- **THEN** el backend lo marca como "Batería Baja" manteniéndolo operativo

#### Scenario: Estado por dispositivo en el panel
- **WHEN** se invoca `GET /api/hardware`
- **THEN** cada dispositivo informa su tipo, sector/macro-zona, batería, señal, último
  update y estado operativo
