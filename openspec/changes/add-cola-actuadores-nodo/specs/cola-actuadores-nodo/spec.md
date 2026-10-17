## ADDED Requirements

### Requirement: El nodo corre una carga a la vez
El firmware del nodo (`actuacion/`, usado por `nodo_combinado` y `nodo_actuador`) SHALL ejecutar a lo sumo
`MAX_CARGAS_SIMULTANEAS` (config.h, por defecto 1) acciones de carga a la vez —tramo de riego de un sector, tramo de
insumo de un sector, movimiento de la mediasombra— y SHALL encolar el resto en orden de llegada. La cola SHALL vivir
en RAM, con capacidad `COLA_CARGAS_MAX`; un comando que no entra SHALL recibir `ERROR cola_llena` al instante.

#### Scenario: Tres comandos en el mismo pulso
- **GIVEN** el nodo sin cargas en curso y el límite en 1
- **WHEN** llegan `valve ON S1 {durationSec:20}`, `pump ON S2 {ml:10}` y `shade SET {targetPct:0}`
- **THEN** arranca sólo el riego de S1; el insumo de S2 arranca al terminar el riego, y la malla al terminar el insumo

#### Scenario: Config vieja
- **GIVEN** un `config.h` sin `MAX_CARGAS_SIMULTANEAS`
- **WHEN** se compila `nodo_combinado`
- **THEN** compila con el límite en 1

#### Scenario: Cola llena
- **GIVEN** la cola con `COLA_CARGAS_MAX` comandos esperando
- **WHEN** llega otro `valve ON`
- **THEN** el nodo responde `ERROR` con `detalle.tipo = "cola_llena"` y no lo ejecuta

### Requirement: OFF y seguridad no esperan turno
`valve OFF` y `pump OFF` SHALL actuar al llegar, cortar el tramo en curso de ese sector y actuador, y descartar lo
encolado del mismo sector y actuador; cada comando cortado o descartado SHALL recibir su ACK final `ERROR cancelado`.
Un `shade SET` con `parametros.prioridad = "INMEDIATA"` SHALL arrancar al llegar aunque el límite esté lleno y SHALL
descartar el `shade SET` encolado (`ERROR reemplazado`). `prioridad` SHALL ignorarse en `valve ON` y `pump ON`, y
cualquier valor distinto de `"INMEDIATA"` SHALL tratarse como ausente.

#### Scenario: OFF de un riego encolado
- **GIVEN** S1 regando y `valve ON S2` en la cola
- **WHEN** llega `valve OFF S2`
- **THEN** el OFF recibe `SUCCESS ok`, el ON de S2 recibe `ERROR cancelado` sin haber arrancado, y S1 sigue regando

#### Scenario: Estado seguro con la malla encolada
- **GIVEN** S1 regando y un `shade SET {targetPct:100}` en la cola
- **WHEN** llegan `valve OFF S1` y `shade SET {targetPct:0, prioridad:"INMEDIATA"}`
- **THEN** el riego se corta (`cancelado`), el SET encolado recibe `ERROR reemplazado` y la malla empieza a cerrarse ya

#### Scenario: SET 0 del motor
- **GIVEN** S1 regando
- **WHEN** llega `shade SET {targetPct:0}` sin `prioridad`
- **THEN** queda en la cola con `en_cola`

#### Scenario: Prioridad en un ON
- **WHEN** llega `valve ON S2 {durationSec:20, prioridad:"INMEDIATA"}` con S1 regando
- **THEN** se encola como cualquier ON

### Requirement: Reemplazo dentro de la cola
Un comando de carga para el mismo sector y actuador (o para la mediasombra) que uno anterior SHALL reemplazarlo: si el
anterior está en curso, arranca en su lugar (`ERROR reemplazado` al anterior); si está encolado, ocupa su lugar en la
fila (`ERROR reemplazado` al anterior).

#### Scenario: Riego repetido mientras espera
- **GIVEN** `valve ON S2` (A) en la posición 1 de la cola
- **WHEN** llega `valve ON S2` (B)
- **THEN** A recibe `ERROR reemplazado` y B queda en la posición 1

### Requirement: ACK intermedio EN_CURSO
Por cada comando de carga el nodo SHALL publicar, en el tópico de ACK del sector y con el mismo `commandId`,
`{"status":"EN_CURSO","detalle":{"tipo":"en_cola","posicion":N,"esperaMaxSec":S}}` si no pudo arrancar al llegar, y
`{"status":"EN_CURSO","detalle":{"tipo":"iniciado",…}}` cuando arranca, con la duración efectiva (`durationSec`, `ml`
o `targetPct`). El ACK final SHALL seguir igual que hoy. Un comando descartado de la cola SHALL recibir sólo su final.
OFF, `sin_cambio` e inválidos SHALL recibir sólo su final.

#### Scenario: Carga que arranca al llegar
- **WHEN** llega `valve ON S1 {durationSec:20}` con el nodo libre
- **THEN** publica `EN_CURSO iniciado {durationSec:20}` y, a los 20 s, `SUCCESS ok {durationSec:20}`

#### Scenario: Carga que espera
- **GIVEN** S1 regando con 15 s restantes
- **WHEN** llega `pump ON S2 {ml:10}`
- **THEN** publica `EN_CURSO en_cola {posicion:1, esperaMaxSec:15}`, después `EN_CURSO iniciado {durationSec:10, ml:10}`
  y por último `SUCCESS ok`

### Requirement: Dedup con la cola
Un `commandId` encolado SHALL contar como en curso para `dedup_comandos`: su reentrega SHALL ignorarse. Uno terminado
SHALL reenviar su ACK final.

#### Scenario: Reentrega QoS 1 de un encolado
- **GIVEN** `valve ON S2` (id X) en la cola
- **WHEN** el broker reentrega X
- **THEN** la cola sigue con un solo X

### Requirement: Contrato espejado
`contrato.h` SHALL documentar `STATUS_EN_CURSO`, los `detalle.tipo` `en_cola`, `iniciado` y `cola_llena`, las claves
`posicion`, `esperaMaxSec` y `prioridad` y el valor `"INMEDIATA"`. `ContratoNodo.java` y `simulador/server/contract.ts`
SHALL espejar los mismos valores.

#### Scenario: Espejos
- **WHEN** se revisan los tres archivos
- **THEN** los tres nombran los mismos status, tipos y claves

### Requirement: El seguimiento recalcula el plazo desde el arranque
`SeguimientoComandosService` SHALL resolver un comando sólo con un ACK final (`SUCCESS`/`ERROR`). Un `EN_CURSO` de un
comando pendiente SHALL mantenerlo pendiente y recalcular su plazo: `en_cola` → recepción + `esperaMaxSec` + duración
+ margen; `iniciado` → recepción + duración + margen. Un status desconocido SHALL loguearse sin resolver el comando.
SHALL publicar `ActuatorProgress` con la fase (`QUEUED`/`STARTED`).

#### Scenario: Riego que espera 20 s en el nodo
- **GIVEN** `valve ON S2 {durationSec:20}` publicado en t0 y margen de 90 s
- **WHEN** en t0 llega `en_cola {esperaMaxSec:20}` y en t0+20 s `iniciado`
- **THEN** el comando vence en t0+20+20+90 s y no antes

#### Scenario: Final después de un intermedio
- **WHEN** llega `EN_CURSO iniciado` y después `SUCCESS ok` del mismo comando
- **THEN** el comando se cierra con el `SUCCESS` (no se ignora como repetido)

#### Scenario: Firmware sin intermedios
- **WHEN** sólo llega el ACK final
- **THEN** todo funciona como antes de este cambio

### Requirement: Riego derivado del arranque
`DespachoRiego.estadoValvula` SHALL devolver "En cola" mientras el nodo informe `en_cola` para el comando que abrió el
riego, y "Regando" desde `iniciado` hasta `iniciado + duración + 5 s`. Sin intermedios SHALL seguir derivándolo de
`ts + duración + 5 s`. Un progreso de otro `commandId` SHALL NOT cambiar el riego vigente.

#### Scenario: Segundo sector en la cola del nodo
- **GIVEN** S1 y S2 despachados en el mismo tick y el nodo con límite 1
- **WHEN** llega `en_cola` de S2
- **THEN** S1 figura "Regando" y S2 "En cola"; S2 pasa a "Regando" al llegar su `iniciado` y no cierra hasta
  `iniciado + duración + 5 s`

### Requirement: Dosis derivada del arranque
`SupplyDoseService` SHALL mantener `Dosificando` en la base, informar "En cola" al snapshot mientras el nodo tenga la
dosis encolada y, con `iniciado`, cerrar por tiempo en `iniciado + duración + margen`. Sin intermedios SHALL seguir
usando la última fila "Insumo" del historial.

#### Scenario: Dosis detrás de un riego
- **GIVEN** la dosis de S1 en la cola del nodo
- **WHEN** se arma el snapshot
- **THEN** la bomba de S1 figura "En cola" y `ActionExecutor` no ordena otra dosis

### Requirement: Las secuencias no confunden un intermedio con el fin
`SecuenciaService` SHALL ignorar un `EN_CURSO` como cierre de paso y SHALL recalcular el plazo del paso en curso
(`ESPERAR`, `DESPLEGAR`, `ENROLLAR`) desde la recepción del intermedio, incluido el `iniciado` del ON que llegue antes
de que `ESPERAR` arranque.

#### Scenario: Secuencia de riego con intermedio
- **WHEN** en una secuencia RIEGO llega `EN_CURSO iniciado` del `valve ON`
- **THEN** el paso `ESPERAR` sigue en curso y termina OK con el `SUCCESS` final

#### Scenario: Mediasombra encolada
- **GIVEN** un riego del motor en curso en la zona de la secuencia
- **WHEN** el `shade SET` del paso `DESPLEGAR` recibe `en_cola {esperaMaxSec:30}`
- **THEN** el paso no vence por `ACTUADOR_SIN_RESPUESTA` antes de 30 s + su timeout

### Requirement: El estado seguro marca la prioridad y cierra primero
`ActuatorShutdown` SHALL publicar los `OFF` antes que la malla y SHALL agregar `prioridad: "INMEDIATA"` a sus órdenes.

#### Scenario: Estado seguro S-02
- **WHEN** `ZoneSafeState` ordena el estado seguro de una zona con válvula, bomba y mediasombra registradas
- **THEN** salen `valve OFF`, `pump OFF` y, al final, `shade SET {targetPct:0, prioridad:"INMEDIATA"}`

### Requirement: El simulador se comporta como el nodo
El simulador SHALL serializar los comandos de cada macro-zona simulada con el límite `SIM_NODE_MAX_LOADS` (default 1)
y SHALL emitir los mismos intermedios y finales que el firmware, incluidos el corte por OFF (`cancelado`) y la
prioridad.

#### Scenario: Pulso sin hardware
- **WHEN** el motor manda riego, insumo y mediasombra a una zona simulada
- **THEN** el simulador responde `iniciado` al primero, `en_cola` a los otros dos y los arranca de a uno

#### Scenario: OFF de un ON simulado en curso
- **WHEN** llega `valve OFF` con el `valve ON` simulado regando
- **THEN** el ON recibe `ERROR cancelado` y no un `SUCCESS` posterior

### Requirement: Happy path en un pulso
El happy path del simulador SHALL disparar un solo pulso con motor con las tres reglas habilitadas, sin encender ni
apagar reglas por paso ni esperar que una acción termine, y SHALL mostrar el resultado por regla con el avance de cada
acción (en cola / en curso / hecho) leído del snapshot.

#### Scenario: Botón de la Demo Expo
- **WHEN** se pulsa el happy path en modo simulador
- **THEN** se registra el diagnóstico, se dispara UN pulso y la tarjeta muestra riego "en curso", insumo "en cola" y
  mediasombra "ordenada", avanzando hasta "hecho"
