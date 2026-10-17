## ADDED Requirements

### Requirement: Tópico de errores del nodo
El contrato SHALL definir el tópico `nursery/zone/{zona}/errores` con el payload
`{ nodo, sector, tipo, valor, timestamp }` y los tipos `SIN_CAUDAL`, `CAUDAL_EXCESIVO` y
`MEDIASOMBRA_FIN_DE_CARRERA`, y el backend SHALL escucharlo.

#### Scenario: Error de caudal
- **WHEN** el nodo de MZ-1 publica `{tipo:"SIN_CAUDAL", sector:"S-012", valor:0}`
- **THEN** el backend registra la falla del sector S-012

#### Scenario: Tipo desconocido
- **WHEN** llega un `tipo` que el contrato no define
- **THEN** el mensaje se descarta con un log y no cambia ningún estado

### Requirement: Falla hidráulica
Ante `SIN_CAUDAL` o `CAUDAL_EXCESIVO`, el sistema SHALL marcar el sector "Falla hidráulica", sacarlo del riego y
de la dosificación automáticos hasta que se registre la reparación, y SHALL emitir una alerta `CRITICAL`.

#### Scenario: Sin caudal con sustrato seco
- **WHEN** hay `SIN_CAUDAL` en S-012 y la humedad de su MZ es 40 %
- **THEN** el sector queda en "Falla hidráulica", R-01 y R-02 no lo riegan, y el texto de la alerta pide riego
  manual del sector

#### Scenario: Sin caudal con sustrato húmedo
- **WHEN** hay `CAUDAL_EXCESIVO` en S-012 y la humedad de la MZ es 60 %
- **THEN** la alerta es `CRITICAL` sin el pedido de riego manual

#### Scenario: Otros sectores
- **WHEN** S-012 tiene falla hidráulica
- **THEN** los demás sectores de la MZ siguen regándose normalmente

### Requirement: Falla de mediasombra
Ante `MEDIASOMBRA_FIN_DE_CARRERA`, el sistema SHALL marcar "Falla mecánica en mediasombra" en la MZ, NO SHALL
volver a mover la malla hasta registrar la reparación, y SHALL emitir `CRITICAL`.

#### Scenario: La malla quedó abierta
- **WHEN** falla el final de carrera de "cerrada"
- **THEN** la alerta pide cubrir a mano y la malla no recibe comandos nuevos

#### Scenario: Mismo hecho por ack y por tópico
- **WHEN** llegan el ack `falla_mecanica` y el error del tópico para la misma MZ
- **THEN** queda una sola alerta

### Requirement: Registro de reparación
Un usuario con permiso SHALL poder registrar la reparación de un dispositivo averiado, lo que SHALL levantar
la marca de falla, dejar el evento en el historial con usuario y detalle, y devolver el actuador a lo autónomo.

#### Scenario: Reparación registrada
- **WHEN** un Operario registra la reparación de la válvula de S-012
- **THEN** la falla se limpia y en el siguiente pulso R-01 puede volver a regar S-012

#### Scenario: Sin permiso
- **WHEN** un usuario sin `hardware.reparar` intenta registrar una reparación
- **THEN** la respuesta es 403

### Requirement: Inyección de errores en el simulador
El simulador SHALL poder publicar los tres tipos de error por el broker, sin que el backend tenga ninguna
referencia al simulador.

#### Scenario: Prueba de punta a punta
- **WHEN** desde el simulador se publica `CAUDAL_EXCESIVO` para un sector
- **THEN** el backend reacciona igual que ante un error de un nodo real
