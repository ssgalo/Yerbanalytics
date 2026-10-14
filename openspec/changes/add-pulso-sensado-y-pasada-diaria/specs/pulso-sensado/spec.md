## ADDED Requirements

### Requirement: Pulso programado en franjas ancladas a las 02:00

El backend SHALL emitir un pulso de sensado al comienzo de cada franja de `intervaloSensadoMinutos` (acotado a
60–360 min, 240 por defecto), con las franjas ancladas a las 02:00 en la zona horaria del vivero (con 240 min: 02,
06, 10, 14, 18 y 22 h). Un pulso SHALL publicar un `LEER_AHORA` (contrato vigente, sin cambios) a cada macro-zona de
la topología. Los pulsos programados SHALL ejecutar el motor de reglas. El intervalo SHALL releerse de la
configuración persistida al calcular cada próximo pulso, así un cambio guardado rige sin reiniciar. Si el backend no
estaba corriendo al comienzo de una franja, ese pulso NO SHALL recuperarse.

#### Scenario: Pulsos del día con el intervalo por defecto
- **GIVEN** `intervaloSensadoMinutos` = 240
- **WHEN** transcurre un día completo
- **THEN** se emiten pulsos a las 02:00, 06:00, 10:00, 14:00, 18:00 y 22:00, y ninguno entre ellos

#### Scenario: Un pulso pide lectura a todas las zonas
- **GIVEN** una topología con `MZ-1` y `MZ-2`
- **WHEN** se emite un pulso
- **THEN** se publica exactamente un `LEER_AHORA` a `nursery/zone/MZ-1/command` y uno a `nursery/zone/MZ-2/command`

#### Scenario: Cambio de intervalo en caliente
- **GIVEN** `intervaloSensadoMinutos` = 240 y el último pulso a las 10:00
- **WHEN** se guarda 120 a las 10:30
- **THEN** el próximo pulso es a las 12:00 (franja de 120 min anclada a las 02:00)

#### Scenario: Valor guardado fuera de rango
- **GIVEN** un `intervaloSensadoMinutos` guardado de 30 o de 600
- **WHEN** se calcula el próximo pulso
- **THEN** se usa el valor acotado (60 o 360)

### Requirement: Pulso manual por API, con o sin motor

El backend SHALL exponer `POST /api/pulsos` con el cuerpo `{"ejecutarMotor": boolean}` (si se omite, `true`), que
emite un pulso en el momento y responde `202` con su identificador, su origen (`MANUAL`), si ejecuta el motor y la
hora del pedido. Con `ejecutarMotor: false` el pulso sólo actualiza los valores sensados; con `true` además ejecuta el
motor, igual que un pulso programado. En todo lo demás (reintentos, fallas, escalamiento) un pulso manual SHALL
comportarse como uno programado. Un pulso manual NO SHALL correr las franjas. Si hay un pulso en curso, el pedido
SHALL rechazarse con `409` y un motivo legible; si al comienzo de una franja hay un pulso en curso, el programado
SHALL omitirse y quedar en el log. La ruta SHALL declararse en `MapaPermisos` con `pasadas.operar`.

#### Scenario: Leer ahora
- **GIVEN** que no hay un pulso en curso
- **WHEN** se invoca `POST /api/pulsos` con `{"ejecutarMotor": false}`
- **THEN** se publica `LEER_AHORA` a todas las zonas, sus lecturas se persisten y el motor no se ejecuta

#### Scenario: Leer y ejecutar el motor ahora
- **GIVEN** que no hay un pulso en curso
- **WHEN** se invoca `POST /api/pulsos` con `{"ejecutarMotor": true}`
- **THEN** se publica `LEER_AHORA` a todas las zonas y cada zona que responde ejecuta el motor

#### Scenario: Pulso en curso
- **GIVEN** un pulso en curso
- **WHEN** se invoca `POST /api/pulsos`
- **THEN** el backend responde `409` y no publica nada

#### Scenario: El manual no corre las franjas
- **GIVEN** franjas de 240 min
- **WHEN** se pide un pulso manual a las 11:15
- **THEN** el próximo pulso programado sigue siendo a las 14:00

#### Scenario: Sin permiso
- **WHEN** un usuario sin `pasadas.operar` invoca `POST /api/pulsos`
- **THEN** el backend responde `403`

### Requirement: Reintentos dentro del pulso

Cada zona de un pulso SHALL quedar pendiente hasta recibir su primera telemetría con hora de recepción igual o
posterior al pedido. A las zonas pendientes el backend SHALL reenviarles `LEER_AHORA` cada
`yerbanalytics.pulso.reintento-seg` (60 s), hasta `yerbanalytics.pulso.intentos` (3) pedidos en total. Vencido el
último plazo, la zona SHALL quedar sin respuesta en ese pulso y NO SHALL evaluarse. El pulso SHALL terminar cuando
ninguna zona siga pendiente. Una falla al publicar a una zona NO SHALL impedir publicar a las demás.

#### Scenario: Respuesta en el primer intento
- **GIVEN** un pulso pedido a T
- **WHEN** llega telemetría de `MZ-1` a T + 2 s
- **THEN** `MZ-1` responde al pulso y no recibe reintentos

#### Scenario: Respuesta tras un reintento
- **GIVEN** un pulso pedido a T sin telemetría de `MZ-2`
- **WHEN** a T + 60 s se reenvía `LEER_AHORA` y la telemetría llega a T + 63 s
- **THEN** `MZ-2` responde al pulso y no cuenta como falla

#### Scenario: Zona sin respuesta
- **GIVEN** un pulso pedido a T y ninguna telemetría de `MZ-3`
- **WHEN** vence el plazo del tercer pedido (T + 180 s)
- **THEN** `MZ-3` queda sin respuesta en ese pulso y sus sectores no se evalúan

#### Scenario: Telemetría anterior al pedido
- **GIVEN** un pulso pedido a T
- **WHEN** la última telemetría de `MZ-1` se recibió a T − 5 s
- **THEN** no cuenta como respuesta

### Requirement: Escalamiento por pulsos consecutivos sin respuesta

El backend SHALL llevar, persistido por macro-zona, la cantidad de pulsos consecutivos en que su nodo testigo no
respondió. Al cerrar un pulso sin respuesta de una zona SHALL incrementarla y registrar en el historial una alerta de
esa macro-zona: `WARNING` en el primero y en el segundo pulso consecutivo, y `CRITICAL` en el tercero, que además
pone el nodo **fuera de servicio** (la zona y sus sectores se muestran "Fuera de servicio"). Los pulsos siguientes
sin respuesta NO SHALL repetir alertas. Cualquier telemetría recibida de la zona SHALL volver la cuenta a cero; si el
nodo estaba fuera de servicio, SHALL registrar una alerta `INFO` de que volvió a responder y la zona recupera su
estado según la lectura.

#### Scenario: Primer pulso sin respuesta
- **GIVEN** `MZ-3` con 0 pulsos sin respuesta
- **WHEN** un pulso cierra sin respuesta de `MZ-3`
- **THEN** la cuenta queda en 1, el historial registra una alerta `WARNING` de `MZ-3` y la zona sigue mostrando su última lectura

#### Scenario: Segundo pulso consecutivo
- **GIVEN** `MZ-3` con 1 pulso sin respuesta
- **WHEN** el pulso siguiente cierra sin respuesta de `MZ-3`
- **THEN** la cuenta queda en 2 y el historial registra otra alerta `WARNING`

#### Scenario: Tercer pulso consecutivo
- **GIVEN** `MZ-3` con 2 pulsos sin respuesta
- **WHEN** el pulso siguiente cierra sin respuesta de `MZ-3`
- **THEN** la cuenta queda en 3, el historial registra una alerta `CRITICAL` y `MZ-3` y sus sectores pasan a "Fuera de servicio"

#### Scenario: Sigue sin responder
- **GIVEN** `MZ-3` fuera de servicio
- **WHEN** otro pulso cierra sin respuesta de `MZ-3`
- **THEN** no se registra ninguna alerta nueva

#### Scenario: Vuelve a responder
- **GIVEN** `MZ-3` fuera de servicio
- **WHEN** llega una telemetría de `MZ-3`
- **THEN** la cuenta vuelve a 0, el historial registra una alerta `INFO` y la zona muestra su estado según la lectura

#### Scenario: Una respuesta corta la racha
- **GIVEN** `MZ-3` con 2 pulsos sin respuesta
- **WHEN** `MZ-3` responde al pulso siguiente
- **THEN** la cuenta vuelve a 0 y una falla posterior vuelve a empezar por `WARNING`

#### Scenario: La cuenta sobrevive a un reinicio
- **GIVEN** `MZ-3` con 2 pulsos sin respuesta
- **WHEN** el backend se reinicia y el pulso siguiente cierra sin respuesta de `MZ-3`
- **THEN** se registra la alerta `CRITICAL` y la zona pasa a "Fuera de servicio"

### Requirement: El motor se evalúa una vez por zona y por pulso

En un pulso que ejecuta el motor, la primera telemetría que responde SHALL persistir la lectura y disparar la
evaluación del motor de los sectores de esa zona (origen `TELEMETRIA`). Las telemetrías siguientes de esa zona dentro
del mismo pulso, las de un pulso sin motor y toda telemetría recibida fuera de un pulso (arranque del nodo, secuencia
`LECTURA`, envío manual) SHALL actualizar la lectura de la zona y el estado de sus sectores, pero NO SHALL disparar el
motor.

#### Scenario: Lectura de un pulso con motor
- **WHEN** `MZ-1` responde a un pulso con motor
- **THEN** sus 100 sectores se evalúan una vez y la traza de cada uno queda con origen `TELEMETRIA`

#### Scenario: Lectura de un pulso sin motor
- **WHEN** `MZ-1` responde a un pulso pedido con `ejecutarMotor: false`
- **THEN** la lectura de `MZ-1` y el estado de sus sectores se actualizan, y el motor no se evalúa

#### Scenario: Lectura fuera de pulso
- **GIVEN** que no hay pulso en curso
- **WHEN** llega una telemetría de `MZ-1` (por ejemplo, el arranque del nodo)
- **THEN** la lectura de `MZ-1` y el estado de sus sectores se actualizan, y el motor no se evalúa

#### Scenario: Segunda respuesta en el mismo pulso
- **GIVEN** que `MZ-1` ya respondió al pulso en curso
- **WHEN** llega otra telemetría de `MZ-1`
- **THEN** se actualiza la lectura y el motor no vuelve a evaluarse

### Requirement: Lectura con motor en Demo Expo

La vista Demo Expo NO SHALL ofrecer botones de pulso de todas las zonas. En la tarjeta "Lectura" de la sección
Secuencias, junto a "Leer sensores ahora", SHALL ofrecer "Leer sensores y ejecutar motor ahora", habilitado en las
mismas condiciones que el resto de las secuencias (`pasadas.operar`, sin secuencia ni pasada en curso). Ese botón
inicia la secuencia `LECTURA` con `ejecutarMotor: true` (ver `secuencias-guionadas`): pide la lectura sólo a la zona
de la secuencia, como un pulso con motor de esa zona, y muestra los valores igual que la lectura simple. El
`POST /api/pulsos` sigue disponible por API para pedir un pulso de todas las zonas.

#### Scenario: Lectura con motor
- **WHEN** el operador pulsa "Leer sensores y ejecutar motor ahora"
- **THEN** se inicia la secuencia `LECTURA` con `ejecutarMotor: true`, la vista muestra los valores que llegaron y el historial, las decisiones del motor para los sectores de esa zona

#### Scenario: Sin pulsos de todas las zonas en la vista
- **WHEN** el operador abre Demo Expo
- **THEN** no hay botones "Leer ahora" ni "Leer y ejecutar motor"
