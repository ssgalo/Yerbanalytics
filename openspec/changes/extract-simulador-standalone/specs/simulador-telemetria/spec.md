## ADDED Requirements

### Requirement: El simulador publica al broker como un nodo más
El simulador SHALL publicar la telemetría directamente en el broker MQTT, en el topic de la
macro-zona correspondiente, con el mismo payload, las mismas claves y las mismas unidades que
publica el firmware del nodo. El backend NO SHALL intervenir en la publicación ni SHALL poder
distinguir un mensaje del simulador de uno de un nodo físico.

#### Scenario: La lectura entra por el pipeline real
- **WHEN** el simulador publica una lectura de una macro-zona
- **THEN** el backend la ingiere por el mismo camino que la telemetría de un nodo físico y
  actualiza los sectores de esa macro-zona

#### Scenario: El mensaje es indistinguible del de un nodo
- **WHEN** se compara el mensaje publicado por el simulador con el que publica el firmware
- **THEN** usan el mismo topic, las mismas claves y las mismas unidades, sin ninguna marca de
  origen

#### Scenario: Unidades del contrato al publicar
- **WHEN** el usuario carga una conductividad en la unidad que muestra la interfaz
- **THEN** el simulador la convierte a la unidad del contrato antes de publicar, y el valor
  que el backend persiste coincide con el que el usuario cargó

#### Scenario: Broker no disponible
- **WHEN** el simulador intenta publicar y el broker no está accesible
- **THEN** informa el error al usuario y no da la lectura por enviada

### Requirement: Gestión de sensores simulados propios
El simulador SHALL mantener su propia lista de sensores simulados —cada uno con serial/MAC y
macro-zona—, con alta, baja y listado, persistida en su propio almacenamiento y conservando
el orden de alta. La lista SHALL ser independiente del registro de hardware del sistema: dar
de alta un sensor simulado NO SHALL crear ni modificar ningún dispositivo registrado. El alta
SHALL rechazar un serial/MAC ya usado por otro sensor simulado, comparándolo sin distinción
de mayúsculas.

#### Scenario: Alta y listado
- **WHEN** el usuario da de alta un sensor simulado con serial/MAC y macro-zona
- **THEN** el sensor aparece en el listado del simulador
- **AND** el registro de hardware del sistema no cambia

#### Scenario: Los sensores sobreviven al reinicio del simulador
- **WHEN** se dan de alta sensores y luego se reinicia el simulador
- **THEN** el listado devuelve los mismos sensores, en el mismo orden de alta

#### Scenario: Serial/MAC duplicado
- **WHEN** el alta usa un serial/MAC que ya tiene otro sensor simulado
- **THEN** el simulador rechaza el alta y lo informa, sin agregar el sensor

#### Scenario: Baja de un sensor
- **WHEN** el usuario elimina un sensor simulado
- **THEN** deja de aparecer en el listado y no se puede enviar desde él, también tras
  reiniciar el simulador

#### Scenario: Las macro-zonas ofrecidas son las de la topología vigente
- **WHEN** el usuario abre el alta de un sensor simulado
- **THEN** el desplegable de macro-zona ofrece exactamente las macro-zonas de la topología
  actual del vivero, y refleja los cambios cuando la topología se regenera

### Requirement: Envío por métrica o completo, con fecha/hora opcional
El simulador SHALL permitir enviar, para cada sensor simulado, **cada métrica por separado**
—con su propio valor y su propia fecha/hora opcional— y también **todas las métricas juntas**
en una sola lectura. Toda lectura SHALL llevar al menos una métrica. Si no se indica
fecha/hora, la lectura SHALL enviarse con la fecha/hora actual.

#### Scenario: Envío de una sola métrica
- **WHEN** el usuario carga el valor de una sola métrica y pulsa el envío de esa métrica
- **THEN** el simulador publica únicamente esa métrica, y los sectores de la macro-zona
  conservan el último valor de las demás

#### Scenario: Envío conjunto
- **WHEN** el usuario carga valores y pulsa el envío conjunto
- **THEN** el simulador publica las diez métricas en una sola lectura

#### Scenario: Envío sin fecha/hora
- **WHEN** el usuario deja la fecha/hora vacía y envía
- **THEN** la lectura viaja sellada con la fecha/hora actual

#### Scenario: Envío con fecha/hora elegida
- **WHEN** el usuario carga una fecha/hora y envía
- **THEN** la lectura viaja con esa fecha/hora

#### Scenario: Envío sin ninguna métrica
- **WHEN** el usuario intenta enviar sin haber cargado ninguna métrica
- **THEN** el simulador rechaza el envío y no publica nada

#### Scenario: Reflejo en el sistema
- **WHEN** una lectura se publica correctamente
- **THEN** las métricas enviadas de los sectores de esa macro-zona se actualizan en el
  dashboard, conservando las no enviadas

### Requirement: Emisión automática periódica
El simulador SHALL ofrecer una emisión automática de telemetría periódica que dé actividad de
fondo al vivero, apagada por defecto para no pisar los valores cargados a mano. Mientras esté
apagada, el simulador NO SHALL publicar nada por su cuenta.

#### Scenario: Apagada por defecto
- **WHEN** el simulador arranca
- **THEN** la emisión automática está apagada y no se publica telemetría

#### Scenario: Emisión automática encendida
- **WHEN** el usuario enciende la emisión automática
- **THEN** el simulador publica telemetría periódica a las macro-zonas hasta que se la apague

#### Scenario: La emisión automática se detiene con el simulador
- **WHEN** el simulador se apaga
- **THEN** deja de publicar, y el sistema queda esperando telemetría de hardware real

### Requirement: Regeneración de la topología desde el simulador
El simulador SHALL ofrecer un control para regenerar la topología del vivero (N macro-zonas ×
M sectores) usando el endpoint público de topología, para hacerla coincidir con el hardware
que se quiere simular. SHALL pedir confirmación cuando ya existe una topología cargada.

#### Scenario: Regenerar la grilla
- **WHEN** el usuario regenera la topología desde el simulador
- **THEN** el vivero queda con la grilla nueva y sus sectores sin lecturas previas

#### Scenario: Confirmación al reemplazar
- **WHEN** el usuario pide regenerar y ya hay una topología cargada
- **THEN** el simulador pide confirmación antes de reemplazarla

#### Scenario: Sin endpoint propio
- **WHEN** se audita la petición que emite el simulador para regenerar
- **THEN** es la misma que emite el panel de topología del dashboard, sobre el mismo endpoint
  público
