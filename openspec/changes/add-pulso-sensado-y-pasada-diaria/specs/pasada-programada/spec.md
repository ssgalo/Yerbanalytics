## ADDED Requirements

### Requirement: Pasada diaria a hora configurable

Si `pasadaDiariaActiva` es verdadero, el backend SHALL iniciar una pasada del riel una vez por día, en el minuto de
`pasadaDiariaHora` (`HH:mm`, hora local del vivero). La pasada programada SHALL ser exactamente la misma que la
manual: mismo riel, mismo recorrido, mismas precondiciones y el mismo estado consultable en
`GET /api/pasadas/actual`. Los dos campos SHALL releerse de la configuración persistida, así un cambio rige desde el
minuto siguiente sin reiniciar. Si el backend no estaba corriendo en ese minuto, ese día no SHALL haber pasada
programada (no se recupera).

#### Scenario: Pasada a la hora configurada
- **GIVEN** `pasadaDiariaActiva` = true y `pasadaDiariaHora` = "11:00"
- **WHEN** el reloj del vivero llega a las 11:00
- **THEN** se inicia una pasada, idéntica a la que inicia `POST /api/pasadas`

#### Scenario: Desactivada
- **GIVEN** `pasadaDiariaActiva` = false
- **WHEN** el reloj llega a `pasadaDiariaHora`
- **THEN** no se inicia ninguna pasada

#### Scenario: Cambio de hora en caliente
- **GIVEN** `pasadaDiariaHora` = "11:00"
- **WHEN** a las 10:30 se guarda "10:45"
- **THEN** la pasada del día se inicia a las 10:45 y no a las 11:00

#### Scenario: Una sola por día
- **GIVEN** que la pasada programada del día ya corrió a las 11:00
- **WHEN** a las 11:20 se guarda `pasadaDiariaHora` = "11:30"
- **THEN** se inicia otra pasada a las 11:30, porque la hora configurada cambió; con la misma hora no se repite

### Requirement: Alerta cuando la pasada programada no puede arrancar

El backend SHALL registrar en el historial una alerta `WARNING` cuando la pasada programada se rechaza a su hora
(pasada o secuencia en curso, sin topología, sin dispositivo de captura conectado), con el motivo del rechazo y
asignada a la macro-zona del recorrido, y NO SHALL reintentarla ese día.

#### Scenario: Sin cámara conectada
- **GIVEN** la pasada diaria activa y ningún dispositivo de captura conectado
- **WHEN** llega la hora programada
- **THEN** el historial registra una alerta `WARNING` "La pasada programada no pudo iniciarse: No hay ningún dispositivo de captura conectado." y no se publica nada al riel

### Requirement: La pasada manual no cambia

`POST /api/pasadas`, `GET /api/pasadas/actual` y `POST /api/pasadas/actual/cancelar` SHALL seguir funcionando igual,
estén o no activa la pasada diaria. La pasada manual y la programada NO SHALL afectarse entre sí más allá de la
regla vigente de una pasada por vez.

#### Scenario: Pasada manual para la demo
- **GIVEN** la pasada diaria desactivada
- **WHEN** un operador invoca `POST /api/pasadas`
- **THEN** la pasada corre como hoy

#### Scenario: Manual en curso a la hora programada
- **GIVEN** una pasada manual `EN_CURSO` a las 10:59
- **WHEN** llega la hora programada de las 11:00
- **THEN** la programada se rechaza por "Ya hay una pasada en curso." y queda la alerta correspondiente
