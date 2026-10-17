## ADDED Requirements

### Requirement: E-01 Efectividad del riego
Treinta minutos después de un riego del sector del nodo testigo, el sistema SHALL pedir una lectura de verificación
a la MZ y evaluar el riego como "Efectiva" si la humedad de sustrato subió 8 puntos o más respecto de la previa. La
lectura de verificación NO SHALL disparar reglas.

#### Scenario: Riego efectivo
- **WHEN** la humedad previa fue 44 % y la verificación da 63 %
- **THEN** el riego es "Efectiva" (delta +19)

#### Scenario: Riego sin efecto
- **WHEN** la verificación da 48 % (delta +4)
- **THEN** el riego es "Sin efectividad"

#### Scenario: La humedad baja
- **WHEN** la verificación da 38 % tras una previa de 44 %
- **THEN** el riego es "Sin efectividad" (el delta negativo no cuenta)

#### Scenario: La verificación no decide
- **WHEN** la lectura de verificación trae humedad 30 %
- **THEN** R-02 no se dispara con esa lectura

### Requirement: E-02 Efectividad del fertilizante correctivo
Catorce días después de la primera aplicación correctiva (F-C2), el sistema SHALL evaluarla como "Efectiva" si el
diagnóstico del sector es Sano o su severidad bajó al menos un nivel.

#### Scenario: Mejoró
- **WHEN** la severidad pasó de Alta a Media a los 14 días
- **THEN** es "Efectiva"

#### Scenario: Igual
- **WHEN** la severidad sigue Alta
- **THEN** es "Sin efectividad"

### Requirement: E-03 Efectividad del fitosanitario
Siete días después del fitosanitario, el sistema SHALL evaluarlo como "Efectiva" si la severidad del sector no aumentó
y no apareció ningún vecino nuevo con el mismo diagnóstico.

#### Scenario: Estable
- **WHEN** la severidad es la misma y los vecinos siguen sanos
- **THEN** es "Efectiva" aunque el daño siga visible

#### Scenario: Se propaga
- **WHEN** aparece un vecino con el mismo diagnóstico
- **THEN** es "Sin efectividad"

### Requirement: S-06 Acción sin efectividad
Cuando la última acción de un tipo quedó "Sin efectividad", el motor SHALL bloquear su repetición autónoma hasta que
se registre la revisión: el riego en toda la MZ (con la excepción de R-02, a lo sumo un riego cada 12 h por sector), el
fertilizante correctivo en el sector (el plan de nutrición sigue), el fitosanitario en el sector. La mediasombra
NO SHALL bloquearse. Se emite `CRITICAL` pidiendo revisión física.

#### Scenario: Riego bloqueado en la MZ
- **WHEN** el último riego de MZ-1 quedó "Sin efectividad" y la humedad es 43 %
- **THEN** R-01 no riega ningún sector de MZ-1 y queda un `CRITICAL`

#### Scenario: R-02 sigue
- **WHEN** el riego de MZ-1 está bloqueado por S-06 y la humedad cae a 30 %
- **THEN** R-02 riega, a lo sumo un riego cada 12 h por sector

#### Scenario: Plan de nutrición
- **WHEN** el fertilizante correctivo de S-045 quedó "Sin efectividad"
- **THEN** F-C2 no vuelve a aplicar en S-045 pero N-01 sí puede aplicar el plan

#### Scenario: Mediasombra
- **WHEN** cualquier acción quedó "Sin efectividad"
- **THEN** la mediasombra sigue pudiendo cerrarse

### Requirement: Revisión humana
Un usuario con permiso SHALL poder registrar la revisión de una acción sin efectividad, lo que SHALL levantar el
bloqueo y dejar el evento en el historial con usuario y detalle.

#### Scenario: Revisión registrada
- **WHEN** un Ingeniero Agrónomo registra la revisión del riego de MZ-1
- **THEN** R-01 vuelve a regar en el siguiente pulso

#### Scenario: Sin permiso
- **WHEN** un usuario sin `efectividad.revisar` lo intenta
- **THEN** la respuesta es 403

### Requirement: F-05 Sano después de un tratamiento
Cuando un sector tratado vuelve a tener un diagnóstico Sano vigente, el sistema SHALL cerrar su seguimiento como
"Efectiva" y emitir `INFO`.

#### Scenario: Se cura antes de la latencia
- **WHEN** a los 4 días de un fitosanitario el sector da Sano
- **THEN** el seguimiento se cierra "Efectiva" sin esperar los 7 días

### Requirement: Retiro del seguimiento genérico
El sistema NO SHALL conservar `FollowUpRule` ni una evaluación genérica por humedad para todo tipo de evento.

#### Scenario: Eventos no evaluables
- **WHEN** se registra un evento de mediasombra
- **THEN** no se crea seguimiento de efectividad
