## ADDED Requirements

### Requirement: Recepción de comandos en tarea/cola dedicada

El subsistema de actuación SHALL suscribirse al topic de comando de su sector y encolar
los comandos recibidos (desde el callback MQTT) hacia una tarea ejecutora, de modo que la
recepción no bloquee ni la red ni la ejecución. Solo se compila e inicializa en los nodos
con actuación (`nodo_actuador`, `nodo_combinado`).

#### Scenario: Recepción de comando
- **WHEN** el backend publica un comando en el topic del sector del nodo
- **THEN** el callback lo valida, lo entrega a la cola de comandos y la tarea ejecutora lo
  procesa disparando el actuador correspondiente.

#### Scenario: Comando malformado o no reconocido
- **WHEN** llega un mensaje que no cumple el contrato (JSON inválido, actuador desconocido
  o parámetros fuera de rango)
- **THEN** el firmware NO acciona ningún actuador y publica un ACK con
  `status: "ERROR"` indicando el motivo.

### Requirement: Ejecución de riego (electroválvula)

El subsistema SHALL comandar la electroválvula de riego a partir de un comando, respetando
la duración recibida, y SHALL leer el feedback del caudalímetro/presión para detectar
falla hidráulica (HU-06 CA-04/CA-05).

#### Scenario: Apertura y cierre por tiempo
- **WHEN** se recibe un comando de riego con duración
- **THEN** el nodo abre el relé de la electroválvula y lo cierra al cumplirse la duración
  (o al llegar un comando de corte), y publica un ACK `SUCCESS` con los datos de cierre.

#### Scenario: Falla hidráulica
- **WHEN** tras abrir la electroválvula el caudalímetro no detecta flujo (o la presión cae
  a cero) dentro del umbral configurado
- **THEN** el nodo cierra inmediatamente el relé y publica un ACK `ERROR` indicando
  "falla_hidraulica".

### Requirement: Ejecución de dosificación (bomba peristáltica)

El subsistema SHALL accionar la bomba peristáltica para inyectar el volumen en mililitros
recibido en el comando, deteniéndola al alcanzar el objetivo, y SHALL aplicar la ejecución
de forma idempotente respecto del `commandId` para evitar dobles dosis (HU-07 CA-01/CA-04).

#### Scenario: Inyección de volumen exacto
- **WHEN** se recibe un comando de insumo con `ml`
- **THEN** el nodo activa la bomba el tiempo/pasos equivalentes al volumen, la detiene al
  completarlo y publica un ACK `SUCCESS` con el volumen aplicado.

#### Scenario: Comando duplicado (defensa en profundidad)
- **WHEN** llega un comando con un `commandId` ya ejecutado
- **THEN** el nodo NO repite la inyección y reenvía el ACK del resultado previo,
  complementando la idempotencia que el backend aplica vía in-flight lock y cooldown.

### Requirement: Ejecución de mediasombra (motor)

El subsistema SHALL mover el motor de mediasombra hacia la posición objetivo (porcentaje
de apertura) recibida, deteniéndolo al alcanzar el fin de carrera o la posición calculada,
y SHALL cortar la energía ante sobrecorriente sostenida o ausencia de fin de carrera en el
tiempo estipulado (HU-08 CA-03/CA-04).

#### Scenario: Movimiento a posición objetivo
- **WHEN** se recibe un comando de mediasombra con posición objetivo
- **THEN** el nodo mueve el motor hasta la posición, lo detiene y publica un ACK `SUCCESS`
  con el nuevo estado de cobertura.

#### Scenario: Atasco mecánico
- **WHEN** el driver detecta sobrecorriente sostenida o no se activa el fin de carrera en
  el tiempo esperado
- **THEN** el nodo corta la energía del motor de inmediato y publica un ACK `ERROR`
  indicando "falla_mecanica".

### Requirement: Límites de seguridad locales

El subsistema SHALL aplicar límites físicos de seguridad locales (tiempo máximo de
apertura, volumen/dosis máximos por comando) como última barrera, incluso si el comando
del backend los excede, dejando el actuador en estado seguro ante cualquier duda.

#### Scenario: Comando excede el límite físico local
- **WHEN** un comando solicita una duración o dosis por encima del límite local configurado
- **THEN** el nodo recorta la acción al límite seguro (o la rechaza) y lo informa en el
  ACK, priorizando la integridad del cultivo y del hardware.

### Requirement: Publicación de ACK con estado obligatorio

El subsistema SHALL publicar un ACK tras cada comando en el topic de ack del sector, con un
campo `status` obligatorio de valor `SUCCESS` o `ERROR` (alineado con lo que evalúa el
motor de reglas del backend), más detalle opcional (duración, volumen, posición o tipo de
falla). El ACK es la señal con la que el backend libera el in-flight lock y decide el
cooldown.

#### Scenario: ACK de ejecución exitosa
- **WHEN** un comando se ejecuta completo
- **THEN** el nodo publica `{ "status": "SUCCESS", ... }` con los datos de cierre.

#### Scenario: ACK de falla
- **WHEN** un comando falla o es rechazado
- **THEN** el nodo publica `{ "status": "ERROR", ... }` con el motivo, para que el backend
  no aplique cooldown y pueda reintentar.
