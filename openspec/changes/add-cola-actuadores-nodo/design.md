# Design: add-cola-actuadores-nodo

Contexto: `proposal.md`. Contrato vigente: sección "Ack" de `Desarrollo/embebido/comun/contrato.h` (un ACK por
comando, publicado al TERMINAR). Este cambio agrega ACK intermedios y una cola de cargas en el nodo.

## D-1 — La serialización vive en el firmware
El problema es eléctrico y local: lo que no tiene que pasar es que el nodo prenda varias cargas a la vez. El único que
sabe qué está prendido *de verdad* es el nodo.

Alternativas descartadas:
- **Escalonar en el backend** (publicar un comando, esperar el ACK, publicar el siguiente): obliga a que el motor, el
  despacho de riego, `ActionExecutor`, las secuencias y el estado seguro compartan un planificador por nodo, y el
  backend no sabe qué nodo atiende qué sectores (topología ≠ cableado). Además un ACK perdido frenaría todo.
- **Escalonar en el happy path del simulador** (lo que hay hoy): sólo protege ese botón; un pulso normal sigue
  tirando abajo la fuente.

Con la cola en el nodo, todo lo que publica comandos sigue igual.

## D-2 — Qué es una "carga" y el límite
Carga = una acción que mueve potencia y dura: un tramo de **riego** de un sector (válvula + bomba de riego), un tramo
de **insumo** de un sector (válvula + bomba de insumo) y un movimiento de la **mediasombra**. El nodo corre a lo
sumo `MAX_CARGAS_SIMULTANEAS` a la vez, en orden de llegada (FIFO).

- `config.example.h`: `#define MAX_CARGAS_SIMULTANEAS 1`. El módulo de la cola trae un `#ifndef` con el mismo
  default, así un `config.h` anterior (no se versiona) compila igual; `static_assert(MAX_CARGAS_SIMULTANEAS >= 1)`.
- Capacidad de la cola: `COLA_CARGAS_MAX` = 8 (dos sectores × riego/insumo + mediasombra, con holgura). Cola llena →
  ACK final inmediato `ERROR cola_llena`.
- El riel no cuenta: tiene su propio slot y ciclo (`act_riel`), y ya se probó solo. Si algún día coincide con una
  carga, se revisa (riesgo anotado).

## D-3 — Qué NO hace cola (preempción)
Se decide al **llegar** el comando, en `task_actuacion`, antes de encolar:

| Comando | Qué hace |
|---|---|
| inválido (JSON, acción, sector, parámetros, duración) | ACK final de error inmediato, como hoy. No ocupa lugar. |
| `valve OFF` / `pump OFF` | Inmediato. Corta el tramo en curso de ese sector y actuador (`ERROR cancelado`, como hoy) **y** descarta lo encolado del mismo sector y actuador (`ERROR cancelado` a cada uno). Su propio ACK: `SUCCESS ok` inmediato. |
| `shade SET` ya en la posición pedida (sin movimiento en curso) | `SUCCESS sin_cambio` inmediato, como hoy. No ocupa lugar. |
| `shade SET` con `prioridad: "INMEDIATA"` | Arranca ya, aunque el límite esté lleno, y descarta el `shade SET` encolado (`ERROR reemplazado`). |
| `valve ON` / `pump ON` / `shade SET` normales | Si hay lugar, arrancan (`iniciado`). Si no, a la cola (`en_cola`). |

Reemplazos (mismo sector y actuador, o la mediasombra, que es una sola):
- Si el anterior **está en curso**, el nuevo lo reemplaza y arranca ya (`ERROR reemplazado` al viejo, como hoy). Usa
  el mismo lugar de carga, no suma consumo.
- Si el anterior **está encolado**, el nuevo toma su lugar en la fila (el viejo: `ERROR reemplazado`). Mantener el
  turno evita que una orden repetida se vaya al fondo.

### D-3.1 — Cómo sabe el nodo que un `shade SET` es de seguridad: `parametros.prioridad`
El estado seguro (S-02) cierra la malla con `shade SET {targetPct: 0}`. Esa orden no se distingue por su contenido:
las secuencias de Demo Expo (`DESPLEGAR`, `APERTURA_DESPLEGADA = 0`) y la regla de mediasombra también mandan `SET 0`.
Si "SET 0 = urgente", el motor volvería a arrancar la malla junto con el riego, que es justo la falla de este cambio.
El patrón OFF/SET no alcanza para la malla (no tiene OFF), así que se agrega un parámetro explícito:

- `parametros.prioridad`: `"INMEDIATA"` | ausente. Cualquier otro valor se trata como ausente (con log): un valor raro
  nunca debe hacer fallar un cierre de seguridad.
- Sólo se honra en `shade SET`. En `valve ON`/`pump ON` se ignora (con log): saltear la cola con una carga larga
  rompe el invariante eléctrico, y nadie lo necesita (cerrar es OFF, que ya es inmediato).
- Lo manda **sólo** `ActuatorShutdown` (estado seguro S-02 y bloqueo manual S-01; el S-01 hoy no toca la malla, y en
  los OFF el parámetro es inocuo). Además `ActuatorShutdown` publica primero los OFF y al final la malla, así el
  instante en que se suman cargas es de milisegundos.
- Un firmware viejo ignora la clave (ArduinoJson sólo lee las que pide).

## D-4 — ACK intermedio `EN_CURSO`
Mismo tópico (`nursery/zone/{zona}/sector/{sector}/ack`), mismo QoS (el nodo publica con `QOS_ACK`; PubSubClient
sale en QoS 0; el backend suscribe QoS 1), sin retain, mismo `commandId`.

```json
{"commandId":"<uuid>","status":"EN_CURSO","detalle":{"tipo":"en_cola","posicion":2,"esperaMaxSec":45}}
{"commandId":"<uuid>","status":"EN_CURSO","detalle":{"tipo":"iniciado","durationSec":20}}
{"commandId":"<uuid>","status":"EN_CURSO","detalle":{"tipo":"iniciado","durationSec":10,"ml":10}}
{"commandId":"<uuid>","status":"EN_CURSO","detalle":{"tipo":"iniciado","targetPct":0}}
```

- `en_cola`: sólo si el comando no pudo arrancar al llegar. `posicion` (1 = el próximo) y `esperaMaxSec`: cota
  superior de la espera = lo que le queda a lo que está en curso + la duración de lo que tiene adelante (una
  mediasombra cuenta `TIMEOUT_MEDIASOMBRA_MS`). Con el límite en 1 es exacta salvo la malla.
- `iniciado`: cuando la carga arranca de verdad (al llegar, o al salir de la cola). Lleva la duración efectiva, ya
  recortada a los límites locales.
- Secuencia por comando de carga: `[en_cola] → iniciado → final`. Un encolado que se descarta no tiene `iniciado`:
  pasa directo a su final (`cancelado`/`reemplazado`). OFF, `sin_cambio` e inválidos: sólo el final, como hoy.
- El ACK final no cambia (ver contrato), y sigue siendo el único que entra al dedup como "terminado".

`detalle.tipo` nuevos: `en_cola`, `iniciado` (con `EN_CURSO`) y `cola_llena` (con `ERROR`).

## D-5 — Dedup por `commandId` en el nodo
`dedup_comandos` ya distingue "en curso" (la reentrega se ignora) y "terminado" (se reenvía el ACK final). Un
comando **encolado** cuenta como "en curso": se registra al llegar, así una reentrega QoS 1 no lo encola dos veces.
No se reenvían intermedios ante una reentrega (el backend no los necesita para cerrar: su plazo tiene respaldo).

## D-6 — Dónde vive la lógica en el firmware, y cómo se prueba
- Nuevo `actuacion/cola_cargas.{h,cpp}`: **lógica pura**, sin `Arduino.h` ni FreeRTOS (sólo `<stdint.h>`,
  `<string.h>`): arribo (arranca / encola / reemplaza), cancelación por sector y actuador, fin de una carga y "quién
  sigue", con el reloj pasado como argumento. Devuelve decisiones; no toca relés ni publica.
- `task_actuacion` la usa: arma el `Comando` como hoy, consulta a la cola, llama a `act_riego::comenzar` /
  `act_mediasombra::comenzar` cuando le toca a cada uno, y por cada ACK final que sale de `atender()` avisa a la cola
  que esa carga terminó y arranca la siguiente. Los ACK intermedios los encola en `sys->ack` como cualquier otro.
- Los drivers (`act_riego`, `act_mediasombra`) no cambian de contrato: siguen sabiendo reemplazar y cortar. Lo
  encolado todavía no los tocó.
- Prueba: no hay precedente de tests de firmware en el repo (sólo compilación con `arduino-cli`). Se agrega un
  arnés **host** mínimo: `Desarrollo/embebido/test/host/cola_cargas_test.cpp` (asserts simples), compilado con
  `g++ -std=c++17` junto a `actuacion/cola_cargas.cpp`. Va fuera de `actuacion/` para no romper la copia plana del
  README (`cp actuacion/* …`). El resto del firmware se verifica compilando `nodo_combinado` y `nodo_actuador`.

## D-7 — ACK que no se pierden con la red caída (barato)
Hoy `task_red` saca los ACK de `sys->ack` y los publica una vez, haya o no MQTT: con la red caída se pierden. Con
hasta tres ACK por comando conviene aplicar el mismo patrón que los eventos del riel (`xQueuePeek` → publicar →
recién ahí `xQueueReceive`) y subir `sys->ack` de 10 a 16. Son pocas líneas y no cambia el contrato.

## D-8 — Backend: el comando sigue pendiente y el plazo sale del arranque
`SeguimientoComandosService`:
- Hoy hace `resueltos.add(commandId)` **antes** de mirar el status: un `EN_CURSO` (status desconocido para el código
  actual) daría el comando por resuelto y el ACK final se ignoraría como repetido. Se corrige: sólo un final
  (`SUCCESS`/`ERROR`) resuelve; un status desconocido se loguea y no resuelve.
- `Pendiente` guarda la duración esperada (`duracionMs`, la que hoy ya calcula de `durationSec`/`ml`).
- `EN_CURSO en_cola`: `venceMs = ahora + esperaMaxSec·1000 + duracionMs + margen`. Sin `esperaMaxSec`, el plazo no se
  toca.
- `EN_CURSO iniciado`: `venceMs = ahora + duracionMs + margen` (hora de **recepción** en el backend: el ACK no lleva
  hora y el reloj del nodo puede no estar sincronizado; en la LAN la latencia es despreciable).
- Un intermedio de un `commandId` resuelto o desconocido se ignora (debug). Si no hay pendiente no se sabe de qué
  actuador es, igual que con los `cancelado` (fix-cierre-actuadores-y-dosis D-2).
- Publica `ActuatorProgress(zonaId, sectorId, actuator, commandId, phase, atMs, maxWaitMs)` (`phase`: `QUEUED` |
  `STARTED`), un evento de Spring como `ActuatorStopped`, sin dependencia directa (evita el ciclo
  despacho → publisher → seguimiento).

**Orden de despliegue:** backend antes que firmware. El backend nuevo funciona con el firmware viejo (sin
intermedios todo cae en el plazo de hoy); el backend viejo con el firmware nuevo pierde los ACK finales (por el
`resueltos.add` de arriba) y las secuencias de Demo Expo tratan el intermedio como error (`aplicarAck` toma un status
desconocido como `ERROR`).

## D-9 — "Regando" y "En cola" en el despacho
`DespachoRiego.Curso` suma la fase del nodo (`SENT` | `QUEUED` | `STARTED`).
- Al despachar: `SENT`, `finMs = ahora + duración + 5 s` (lo de hoy: es el respaldo).
- `QUEUED` (del mismo `commandId`): `finMs = at + maxWait + duración + 5 s`. `estadoValvula` devuelve **"En cola"**:
  el mismo texto que la cola del despacho, porque para quien mira es lo mismo (espera turno, la válvula no está
  abierta) y el frontend ya lo muestra. El sector sigue contando en el cupo de la zona: el comando ya salió.
- `STARTED`: `finMs = at + duración + 5 s`, "Regando".
- Un progreso con otro `commandId` (viejo) no toca el riego vigente, como el `cancelado` tardío (D-6 del cambio
  anterior). La reconstrucción tras un reinicio del backend sigue saliendo del historial (`ts` del despacho): un riego
  que estaba en la cola del nodo puede darse por cerrado antes; su ACK final llega como desconocido. Aceptado.

## D-10 — "Dosificando" en `SupplyDoseService`
El estado persistido sigue siendo `Dosificando` (es lo que impide que `ActionExecutor` ordene otra dosis; un valor
nuevo en la base abriría esa puerta). La fase del nodo va en memoria, por sector:
- `QUEUED` → `estadoBomba` (lo pide el snapshot) devuelve **"En cola"** mientras la base diga `Dosificando`.
- `STARTED` → el respaldo por tiempo usa `at + duración + margen` en lugar de `ts` del historial.
- Sin fase en memoria (reinicio, firmware viejo) → lo de hoy (`ts + duración + margen` del historial). Para un
  encolado el plazo es `at + maxWait + duración + margen`.
- El ACK final sigue cerrando por `ActuatorStopped` y limpia la entrada en memoria.

## D-11 — Secuencias de Demo Expo
`SecuenciaService.aplicarAck`: un `EN_CURSO` del paso en curso **no cierra** el paso; recalcula su plazo:
`en_cola` → `ahora + esperaMax + plazo del paso`; `iniciado` → `ahora + plazo del paso`. Aplica a `ESPERAR` (riego:
`venceAckEn`) y a `DESPLEGAR`/`ENROLLAR` (hoy cuentan desde `publicadoEn`; pasan a un `venceAckEn` igual). Como
`ABRIR` cierra al publicar y `ESPERAR` entra en el tick siguiente, el intermedio del ON puede llegar entre los dos: la
secuencia recuerda el último progreso de `comandoOnId` y `ESPERAR` lo aplica al entrar. Un status desconocido que no
sea `EN_CURSO` sigue tratándose como error.

## D-12 — Simulador: misma cola, mismos ACK
`simulatedAck` deja de ser "un ACK y una demora": nuevo módulo puro `server/nodeQueue.ts` (reloj y timers inyectados)
con las reglas de D-3/D-4, una cola por **macro-zona** simulada (el simulador no sabe qué nodo atiende qué sectores;
la zona es el nodo testigo). Límite: `SIM_NODE_MAX_LOADS` (default 1). `mqtt.ts` le pasa cada comando y publica los
ACK que emite. Cambia un comportamiento: hoy un OFF no corta el ON simulado en curso (su `SUCCESS` sale igual); ahora
lo corta con `cancelado`, como el firmware. Con 1 por zona, una tanda de 10 válvulas se riega de a una: es lo que
haría el prototipo.

## D-13 — Happy path: un pulso otra vez
Se vuelve al diseño original de `add-demo-expo-happy-path` (un pulso con las tres reglas): se borran `STEP_ORDER`,
`STEP_RULES`, `enableOnly`/`previousEnabled`, `waitUntilFinished`, `expectedSeconds`, `STEP_WAIT_MS` y el diagnóstico
"justo antes del paso 2" (vuelve antes del pulso). La tarjeta sigue por regla (traza del motor) y suma el avance de
cada acción consultando el snapshot: válvula `En cola` → "en cola", `Regando` → "en curso", `Cerrada` → "hecho";
bomba `En cola` / `Dosificando` / `En espera`. La mediasombra no tiene estado derivado del nodo: muestra lo de la
traza ("ordenada") y el estado guardado.

## Secuencia: un pulso, tres comandos (límite 1)

```
Backend (motor/despacho)        Broker            Nodo (cola, límite 1)               Backend (seguimiento)
  valve ON S1 20s  ──────────────▶ ───────────▶  llega → arranca              ─▶ EN_CURSO iniciado (S1)    → "Regando", vence t+20+90
  valve ON S2 20s  ──────────────▶ ───────────▶  llega → cola pos 1           ─▶ EN_CURSO en_cola esp 20s  → "En cola", vence t+20+20+90
  pump ON S1 10ml  ──────────────▶ ───────────▶  llega → cola pos 2           ─▶ EN_CURSO en_cola esp 40s  → bomba "En cola"
  shade SET 0      ──────────────▶ ───────────▶  llega → cola pos 3           ─▶ EN_CURSO en_cola esp 50s
                                                 t+20 S1 termina              ─▶ SUCCESS ok (S1)
                                                       S2 arranca             ─▶ EN_CURSO iniciado (S2)    → "Regando"
                                                 t+40 S2 termina              ─▶ SUCCESS ok (S2)
                                                       insumo S1 arranca      ─▶ EN_CURSO iniciado         → "Dosificando"
                                                 t+50 insumo termina          ─▶ SUCCESS ok                → "En espera"
                                                       malla arranca          ─▶ EN_CURSO iniciado
                                                 t+5x final de carrera        ─▶ SUCCESS ok
```

## Modos de falla
- **Reinicio a mitad de la cola** (brownout, corte): la cola y el dedup están en RAM y se pierden; los relés arrancan
  apagados (`iniciar()`). Nada se reanuda solo. Cada comando pendiente vence en el seguimiento y deja "no confirmó a
  tiempo" (alerta de hoy); "Regando"/"Dosificando" caen por su plazo. El motor vuelve a decidir en el próximo pulso.
  Trabajo futuro: un evento de arranque del nodo que cierre todo al instante.
- **OFF mientras está encolado:** se descarta de la cola con `ERROR cancelado`; nunca arranca. El backend cierra el
  estado con el `cancelado`, como hoy.
- **`commandId` duplicado** (reentrega QoS 1): encolado o en curso → se ignora; terminado → se reenvía el final.
- **Red caída:** los comandos ya recibidos se cumplen igual (todo es local). Los ACK esperan en `sys->ack` (D-7) hasta
  que vuelva MQTT, en orden; si la cola se llena, se pierden y el backend cae en el plazo. Un comando que no llega
  nunca no existe para el nodo: lo cubre el plazo del seguimiento.
- **Intermedio perdido, final llega:** el final cierra igual. **Final perdido:** vence el plazo (recalculado si llegó
  el intermedio).
- **Intermedio tardío después del final:** el `commandId` ya está resuelto; se ignora.

## Riesgos
- Con 1 carga por vez, un riego de 20 min en S1 deja S2 esperando 20 min: es el costo de no tirar la fuente. Se sube
  `MAX_CARGAS_SIMULTANEAS` con una fuente mejor.
- `esperaMaxSec` es una cota: un plazo generoso tarda más en alertar si el nodo murió con la cola llena.
- El riel no está en el límite (D-2): una pasada con una carga en curso sigue sumando consumo.
