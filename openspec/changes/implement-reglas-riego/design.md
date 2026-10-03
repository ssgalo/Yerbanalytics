# Design: implement-reglas-riego

Abreviaturas: `be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`;
`bt/` = `Desarrollo/backend/src/test/java/com/yerbanalytics/backend/`;
`res/` = `Desarrollo/backend/src/main/resources/`; `fw/` = `Desarrollo/embebido/`;
`fe/` = `Desarrollo/frontend/src/`; **v2** = `docs-motor-reglas-e-integracion/reglas_v2.md`.

## Context

**Lo que hay (verificado en el código, no en el documento de diferencias):**

| Pieza | Estado actual |
|---|---|
| Rama RIEGO | `StaleSensorRule` (1, GLOBAL, emite `ABORT_RIEGO`), `WeatherOverrideRule` (2), `DailyVolumeLimitRule` (4), `IrrigationRule` (10) |
| Orquestador | `ABORT_RIEGO`/`POSTPONE_RIEGO` cortan el resto de la rama; las acciones ya acumuladas **no se borran** (`be/engine/RuleOrchestrator.java:114,127-130`) |
| Umbrales | Ya en el catálogo: `riego.umbral-humedad` 42, `riego.tiempo-max-apertura` 120 s, `riego.max-riegos-24h` 2, `riego.max-riegos-24h-sector` 1, `riego.lluvia-probabilidad` 60 (`be/engine/parametros/ParametrosRiego.java:13-32`) |
| Parámetros v2 | Modelados sólo en test: `bt/engine/parametros/ParametrosRiegoV2Fixture.java` (15 claves + 3 restricciones cruzadas) |
| Acción de riego | Duración pegada al texto `[tiempo-max-seg=N]` y parseada por regex (`be/engine/ActionExecutor.java:46,83,189-198`) |
| Estado de la válvula | Enganche `"Regando"`; nada lo cierra (`ActionExecutor.java:78-85`; único otro escritor: alta de sector, `be/service/TopologiaService.java:241`) |
| Disparo | Telemetría: 100 sectores de la zona por mensaje (`be/service/NurseryService.java:502-551`). Barrido: `metrics = List.of()` (`be/service/NurseryWatchdog.java:146`), nunca riega |
| Cadencia real | El nodo publica cada **30 s** (`fw/comun/config.example.h:48`); v2 supone que el backend pide la lectura cada 4 h |
| Hora | `Instant.now()` en el contexto (`NurseryService.java:582`); `ZoneId.systemDefault()` en Open-Meteo (`be/engine/weather/OpenMeteoWeatherClient.java:343,361`) |
| Pronóstico | Sólo probabilidad de la hora actual; el pedido no lleva `timezone`, así que Open-Meteo devuelve horas GMT y `currentHourIndex` las compara con la hora local del JVM (`OpenMeteoWeatherClient.java:268-275,341-352`) |
| Válvula en firmware | Recorta a `LIMITE_VALVULA_SEG_MAX = 120` (`fw/comun/config.example.h:96`, `fw/actuacion/act_valvula.cpp:25-29`); cierra a los 10 s si el caudalímetro no da pulsos (`act_valvula.cpp:42-54`, `TIMEOUT_CAUDAL_MS`) |
| Historial | `registrarRiego` sin volumen ni duración ni regla (`be/service/HistorialService.java:66-75`); `sev` es la severidad del **diagnóstico**, no de una alerta (`:100`) |
| Lectura parcial | Una métrica que no viene conserva el valor anterior (`NurseryService.java:463-477`); la frescura es sólo por zona (`StaleSensorRule.java:77-82`) |

**Restricciones:** backend sin modos; el simulador no se nombra en el backend; `ddl-auto=update`
agrega columnas nulas pero no baja nada (`res/application.properties:42`); `sql.init.mode=never`
(`:55`); el contrato MQTT está espejado en firmware, `ContratoNodo` y `simulador/server/contract.ts`.

## Goals / Non-Goals

**Goals:** R-01…R-06 con los valores de §5/§11; riego que vuelve a ocurrir (ciclo de válvula
cerrado); de a N sectores por MZ en orden; nada de riego en bucle; cada comparación visible en el
Inspector; sin reescribir el orquestador.

**Non-Goals:** ver `proposal.md` → *Out of scope*.

## Decisions

### D1 — Reglas, orden y cómo gana R-02 sin tocar el orquestador

Todas en la rama `RIEGO`, en `be/engine/rules/`. Se borran `IrrigationRule`, `WeatherOverrideRule`,
`DailyVolumeLimitRule` y sus tests.

| Regla v2 | Clase (`name()`) | Prio | Parámetros (catálogo) | Acción si se cumple | Si no |
|---|---|---|---|---|---|
| — (guarda D5) | `CicloLecturaRiegoRule` | 2 | — (condición fija) | `ABORT_RIEGO` "ya regado en este ciclo / riego en curso" | `NOOP_INFO` |
| R-04 | `SustratoSaturadoRule` | 3 | `saturacion-bloqueo`, `saturacion-alerta` | `ABORT_RIEGO`; con ≥ alerta, además `ALERTA` WARNING (MZ) | `NOOP_INFO` |
| R-02 | `DeficitCriticoRule` | 4 | `umbral-critico`, `volumen-max-evento`, `caudal-emisor`, `exceptuado-bloqueo` | `ACTIVAR_VALVULA` (volumen máx.) + `ALERTA` CRITICAL (MZ); con tope D9 vencido no: `ABORT_RIEGO` | `NOOP_INFO` |
| R-05 | `FueraDeVentanaRiegoRule` | 6 | `ventana-normal`, `umbral-humedad`, `umbral-critico` | `ABORT_RIEGO` | `NOOP_INFO` |
| R-06 | `PausaTrasAplicacionRule` | 7 | `pausa-tras-aplicacion`, `umbral-humedad`, `umbral-critico` | `ABORT_RIEGO` (sector) | `NOOP_INFO` |
| R-03 | `PosponerPorLluviaRule` | 8 | `lluvia-probabilidad`, `lluvia-mm`, `lluvia-ventana`, `umbral-humedad`, `umbral-critico` | `POSTPONE_RIEGO` + `ALERTA` INFO (MZ) | `NOOP_INFO` |
| R-01 | `RiegoPorDeficitRule` | 10 | `umbral-humedad`, `umbral-critico`, `humedad-objetivo`, `litros-por-punto`, `volumen-max-evento`, `caudal-emisor` | `ACTIVAR_VALVULA` (fórmula) | `NOOP_INFO` |

(Las claves llevan el prefijo `riego.`; tabla completa en D8.)

**Cómo gana R-02.** El orquestador conserva las acciones ya emitidas aunque después se corte la
rama. R-02 corre antes que R-05/R-06/R-03, así que su `ACTIVAR_VALVULA` sobrevive a cualquier corte
posterior. Para que la traza y el historial no digan "pospuesto por lluvia" mientras R-02 riega, las
tres compuertas **sólo actúan cuando aplica R-01**: primero registran
`umbral-critico ≤ humedad < umbral-humedad`; si no se cumple, `NOOP_INFO` "No aplica (lo cubre
R-02 / no hay déficit)". R-01 hace la misma comprobación, así nunca emite una segunda válvula.
Esto es exactamente lo que pide v2 (R-03 "cuando se cumplen las condiciones de R-01", R-05 "sólo
puede regar R-02", R-06 "R-02 sí puede regarlo") y usa los umbrales **compartidos**.

R-04 y R-02 son excluyentes (una sola humedad por MZ y `bloqueo > crítico` por rango), así que R-04
puede ir antes sin bloquear nunca a R-02. `StaleSensorRule` (GLOBAL, 1) y `ManualLockRule` (0) no
cambian de lugar: sin lectura válida o con bloqueo manual no riega nadie, tampoco R-02 (S-01, S-02).

**Descartadas:**
- *Una rama nueva `RIEGO_CRITICO` para R-02*: obliga a que `ABORT_RIEGO` corte dos ramas
  (cambio en `RuleOrchestrator.java:127-130`), suma un valor a `RamaRegla` en el frontend y duplica
  nodos de éxito/aborto en el DAG.
- *Un `ActionType` "fin de rama con éxito"*: cambio del orquestador y del DAG para algo que las
  compuertas resuelven declarando dos umbrales que ya existen.
- *R-03/R-05/R-06 como condiciones dentro de R-01*: una sola regla gorda, sin nodos propios en el
  Inspector y sin parámetros por regla.

### D2 — Acciones tipadas y `ActionType.ALERTA`

`RuleAction` suma un componente opcional `DetalleAccion detalle` (interface sellada) y conserva los
factories de tres argumentos (`be/engine/RuleAction.java:21-28`):

```java
sealed interface DetalleAccion permits DetalleRiego, DetalleAlerta {}
record DetalleRiego(double volumenL, int duracionSeg, double humedad, boolean recortado) {}
record DetalleAlerta(NivelAlerta nivel, String texto) {}   // INFO | WARNING | CRITICAL; alcance MZ
```

`ActionType` suma `ALERTA` (no bloqueante: `isBlocking()` no cambia). `ActionExecutor` deja de
parsear `[tiempo-max-seg]` (se borran `TIEMPO_MAX_PATTERN` y `parseTiempoMax`); `[apertura=N]` de la
mediasombra queda como está (fuera de alcance). El motivo sigue siendo texto legible con volumen y
duración ("4,0 L · 480 s").

### D3 — Volumen, tiempo y el límite de la válvula

`CalculoRiego` (puro, `be/engine/riego/`):

```
V = min((objetivo − h) × litrosPorPunto, volumenMax)        R-01
V = volumenMax                                              R-02
V se redondea a 0,01 L (HALF_UP, BigDecimal) — 21 × 0,2 en double es 4,2000000000000002 y daría 505 s
t = ceil(V / caudal × 3600) s;  si t > DURACION_MAX → t = DURACION_MAX, recortado = true
```

`DURACION_MAX` es del **contrato**: `ContratoNodo.DURACION_VALVULA_MAX_SEG = 1200`, espejo de
`CONTRATO_VALVULA_DURACION_MAX_SEG` en `fw/comun/contrato.h` (D13). Una restricción cruzada del
catálogo impide configurar un caudal y un volumen máximo que no entren:
`volumen-max-evento / caudal-emisor × 3600 ≤ 1200` ("Con ese caudal, el volumen máximo no se
alcanza a regar dentro del límite de la válvula"). El recorte queda como defensa y se ve en la traza.

**Por qué 1200 s:** cubre todo el rango de volumen máximo de §11 (3–10 L) al caudal nominal
(10 L / 30 L/h = 1200 s). El límite local del ESP32 sigue existiendo y sigue siendo la última
barrera (corta aunque el backend mande más), sólo que deja de estar por debajo de lo que v2 pide.

**Descartadas:**
- *Fraccionar en pulsos de 120 s*: el backend tendría que encadenar N comandos por sector, con
  estado intermedio, reintentos y dedupe; si se cae el broker a mitad, queda un riego parcial que
  nadie sabe completar. Además la cola de tandas (D4) se vuelve de dos niveles.
- *900 s*: deja afuera volúmenes de §11 (≥ 7,5 L a 30 L/h) sin una razón agronómica.

### D4 — Cola por MZ, despacho en tandas y cierre de la válvula

Las reglas **deciden**; el `DespachoRiego` (`be/engine/riego/`) **ejecuta**.

```
Telemetría (hilo MQTT)                         DespachoRiego.tick() cada 10 s
  regla → ACTIVAR_VALVULA(DetalleRiego)          1. enCurso = eventos "Riego" con ts + duracionSeg + 5 s > ahora
  ActionExecutor → cola.solicitar(sector, d)     2. por MZ: libres = sectores-simultaneos − enCurso(MZ)
  cancelación explícita de seguridad             3. pendientes de la MZ ordenados por sector.n
     (sólo TELEMETRIA) → cola.retirar(sector)     4. por cada uno (hasta libres): REVALIDA con datos
                                                    actuales (bloqueo manual, lectura vigente, saturación,
                                                    ventana para R-01) y si pasa publica valve ON
                                                    {durationSec}, registra "Riego" (volumen, duración,
                                                    regla) y lo saca de la cola
```

- **La cola** es un mapa en memoria `zonaId → (sectorId → Solicitud)`, sincronizado por zona. La
  evaluación con `ACTIVAR_VALVULA` reemplaza la solicitud (nuevo volumen); una evaluación sin él **no** la
  retira: la ronda decidida se completa, y sólo la retira una cancelación explícita de seguridad (ver
  *Correcciones de la revisión de la conmutación*, C1). El barrido no la toca (evalúa sin métricas).
- **Cierre del ciclo de la válvula.** "Regando" deja de ser un estado guardado: es
  `ts + duracionSeg·1000 + 5 s > ahora` sobre el evento "Riego" que se escribe **al despachar**.
  Coincide con el principio 6 (el ESP32 corta solo) y no necesita el ACK. El margen de 5 s cubre la
  latencia del comando para no abrir el sector N+1 antes de que cierre el N.
- **Reinicio.** Lo en curso se reconstruye del historial en el primer tick; lo pendiente se pierde
  y la siguiente telemetría (30 s con el nodo real) lo vuelve a decidir: esos sectores no tienen
  riego en el ciclo (D5).
- **Lo que ve el dashboard.** `NurseryService.getSnapshot` toma la etiqueta de la válvula de
  `DespachoRiego.estadoValvula(sectorId)` → `Regando` | `En cola` | `Cerrada`
  (`NurseryService.java:148-149`). La columna `sector.actuador_valve` queda como legado: nadie la
  escribe ni la lee para decidir. Así desaparece también el *lost update* que tendría guardarla
  (la telemetría y el barrido hacen `saveAll` de la entidad completa: `NurseryService.java:552`,
  `NurseryWatchdog.java:162`).
- **Falla de publicación.** Si el gateway MQTT lanza, no se registra el riego y la solicitud queda
  en la cola para el próximo tick (hoy se registra igual: `ActionExecutor.java:81-84,165-168`). La
  publicación se mueve a un `ComandoActuadorPublisher` que usan el despacho (válvula) y el
  `ActionExecutor` (bomba y mediasombra, sin cambios de comportamiento).
- **En la traza:** la acción de R-01/R-02 dice "Regar 4,0 L (480 s) — a la cola de MZ-2". El
  despacho no es una regla y no aparece en el DAG; se ve en el historial (evento "Riego") y en el
  mapa ("En cola" / "Regando").
- **Intervalo del tick:** `yerbanalytics.riego.despacho-intervalo-ms=10000` (infraestructura, no
  agronomía, por eso property y no catálogo).

**Descartadas:**
- *Que lo avance el watchdog*: corre cada 5 min (`NurseryWatchdog.java:85-90`); un sector que cierra
  dejaría el lugar vacío hasta 5 min y una MZ de 10 tandas tardaría ~1 h de más.
- *Que el barrido evalúe con la última lectura*: no agrega nada al riego (la telemetría ya evalúa
  cada 30 s, y con lecturas de 4 h `StaleSensorRule` corta a los 90 s) y cambia el comportamiento de
  mediasombra e insumo, fuera de alcance.
- *Cola persistida en una tabla*: más estado y migración para algo que la próxima lectura reconstruye.
- *Escuchar el ACK*: necesitaría suscripción nueva, correlación por `commandId` y qué hacer si no
  llega; el tiempo ya es la fuente de verdad del firmware.
- *Volver a "Cerrada" la columna desde el tick*: pelea con los `saveAll` de telemetría y barrido.

### D5 — Contra el riego en bucle: ciclo de lectura (y fuera los límites diarios)

v2 se protege con la cadencia: "lectura cada 4 h" (§1.2) y una decisión por lectura. Con el nodo
publicando cada 30 s, sin protección R-01 volvería a regar el sector apenas cierra la válvula,
porque la humedad sale de **un** nodo testigo que puede estar en un sector que todavía no se regó.

**Ciclo de lectura:** franjas de `intervalo` minutos ancladas a las 02:00 locales (con 240: 02, 06,
10, 14, 18, 22 h, las de §1.2; la última del día se corta a las 02:00). `intervalo` =
`configuracion_operativa.intervaloSensadoMinutos` acotado a **60–360** (§11 "Intervalo de lectura
1–6 h"): es el mismo concepto, ya existe, el simulador ya lo usa para su cadencia
(`simulador/server/emission.ts:39-40`) y así no hay dos lugares que editen lo mismo. Un valor
guardado fuera de rango se acota y se loguea; el guardado nuevo valida 60–360
(`be/service/ConfiguracionService.java:223` hoy sólo exige positivo).

`CicloLecturaRiegoRule` corta la rama si el sector **ya tuvo un riego despachado desde el inicio del
ciclo** o **tiene uno en curso**. Es una condición fija (`compararFijo`, no configurable): su único
"umbral" es el inicio del ciclo, que viaja en el contexto. **No** vale para R-02 (corrección C3): la guarda de "ya regó en este ciclo" corta
sólo a R-01; "hay un riego en curso" corta a las dos.

**Se elimina** (Anexo A, "Límites diarios… eliminados"; "sin intervalo mínimo"):
`DailyVolumeLimitRule`, la guarda de 24 h de `IrrigationRule` y las claves `riego.max-riegos-24h`,
`riego.max-riegos-24h-sector` y `riego.tiempo-max-apertura` (reemplazada por volumen ÷ caudal).

**Protección que queda:** ciclo de lectura (≤ 1 riego por sector y ciclo), R-04 (≥ 75 % bloquea),
el tope de R-02 (D9), la frescura de la humedad (D9) y el límite local del ESP32. Lo que **no**
cubre: un sensor trabado en un valor seco plausible. Ver **DA-5**.

**Descartadas:**
- *Ventana móvil "no regar si se regó hace < intervalo"*: es el "intervalo mínimo" que v2 sacó y,
  con el backend pidiendo la lectura justo a la hora (S-02 futuro), un riego de las 10:00:30
  saltearía la lectura de las 14:00:00.
- *Parámetro nuevo `riego.ciclo-lectura`*: duplica `intervaloSensadoMinutos`.

### D6 — Pronóstico: milímetros y ventana

Open-Meteo: `hourly=precipitation_probability,precipitation,uv_index,temperature_2m,relative_humidity_2m,weathercode`,
`timezone=America/Argentina/Buenos_Aires`, `forecast_days=2`. Ambos valores horarios de Open-Meteo
refieren a **la hora anterior** a su marca (`15:00` = 14:00–15:00).

`WeatherForecast` suma `List<PronosticoHora> horas` (`LocalDateTime` local, `probLluviaPct`,
`precipitacionMm`) desde la hora actual hasta +24 h, y un método puro:

```java
LluviaPrevista lluviaProxima(LocalDateTime ahora, int horas)
// toma las marcas T con truncHora(ahora) < T ≤ truncHora(ahora) + horas
// → (probMaxPct, mmTotal, horasCubiertas)
```

Los campos actuales (`probLluviaPct`, `uvIndex`, `forecastSlots`…) siguen para el widget y la
mediasombra; se suma un constructor con la firma vieja para no tocar a sus usuarios.
`currentHourIndex` busca la hora con el `Clock` del vivero (D7), no con el del JVM.

R-03 compara `probMaxPct ≥ riego.lluvia-probabilidad` **y** `mmTotal ≥ riego.lluvia-mm` con
`horas = riego.lluvia-ventana`. Sin pronóstico: `SIN_DATO`, no pospone (O-01, igual que hoy).

### D7 — Zona horaria, reloj y ventana cerrada a minuto

- `be/config/RelojConfig`: `ZonaHorariaVivero.ZONA = America/Argentina/Buenos_Aires` y un bean
  `Clock relojVivero = Clock.system(ZONA)`. `NurseryService`, `NurseryWatchdog`, `DespachoRiego`,
  `OpenMeteoWeatherClient` y el registro de riegos toman la hora de ese `Clock`. Los tests pasan un
  `Clock.fixed`.
- Las reglas no ven el reloj: usan `ctx.now().atZone(ZONA)`. Test de regla = contexto con `now` fijo.
- **18:00 entra.** v2 R-05: "La lectura de las 18:00 todavía entra en la ventana". `VentanaHoraria`
  es `[desde, hasta)` (`be/engine/parametros/VentanaHoraria.java:188-224`), así que R-05 usa
  `contieneHastaElMinuto(t)` = `contiene(t) || t.truncatedTo(MINUTES).equals(hasta)`: 18:00:59 está
  adentro, 18:01:00 afuera; 06:00:00 adentro, 05:59:59 afuera.
- `Evaluacion.compararVentana(etiqueta, LocalTime hora, DefinicionParametro ventana)` registra la
  comparación con el operador nuevo `Operador.EN` ("∈"): recibido `"17:42"`, umbral
  `"06:00-18:00"`, con clave, así el Inspector muestra "Hora local 17:42 ∈ 06:00–18:00 ✓".
- `ShadingRule` y el formato de fechas del historial siguen con `systemDefault()` (fuera de alcance;
  se anota en el documento de diferencias).

### D8 — Parámetros

Se mudan a `ParametrosRiego` (producción) los de `ParametrosRiegoV2Fixture` salvo
`riego.exceptuado-bloqueo`, que entra porque lo usa D9. Las restricciones del fixture pasan a
`CatalogoParametros.restriccionesReales()` (hoy vacía, `CatalogoParametros.java:59-62`) más la de D3.
El fixture se borra (los tests del catálogo usan las definiciones reales).

| Clave | Tipo | Unidad | Fábrica | Rango | Dec. | Usada por |
|---|---|---|---|---|---|---|
| `riego.umbral-humedad` | NUMERO | % | **45** (hoy 42) | 35–60 | 0 | R-01, R-03, R-05, R-06 |
| `riego.umbral-critico` | NUMERO | % | 35 | 25–40 | 0 | R-02, R-01, R-03, R-05, R-06 |
| `riego.humedad-objetivo` | NUMERO | % | 65 | 55–75 | 0 | R-01 |
| `riego.litros-por-punto` | NUMERO | L/punto | 0.2 | 0.1–0.5 | 2 | R-01 |
| `riego.volumen-max-evento` | NUMERO | L | 6 | 3–10 | 1 | R-01, R-02 |
| `riego.caudal-emisor` | NUMERO | L/h | 30 | 5–120 | 1 | R-01, R-02 |
| `riego.saturacion-bloqueo` | NUMERO | % | 75 | 65–85 | 0 | R-04 |
| `riego.saturacion-alerta` | NUMERO | % | 80 | 65–85 | 0 | R-04 |
| `riego.ventana-normal` | VENTANA_HORARIA | — | 06:00-18:00 | — | — | R-05 |
| `riego.lluvia-probabilidad` | NUMERO | % | **70** (hoy 60) | 50–95 | 0 | R-03 |
| `riego.lluvia-mm` | NUMERO | mm | 5 | 2–20 | 1 | R-03 |
| `riego.lluvia-ventana` | ENTERO | h | 4 | 2–12 | 0 | R-03 |
| `riego.pausa-tras-aplicacion` | NUMERO | h | 6 | 2–24 | 0 | R-06 |
| `riego.exceptuado-bloqueo` | NUMERO | h | 12 | 6–24 | 0 | R-02 (D9) |
| `riego.sectores-simultaneos` | ENTERO | sectores | 10 | 1–100 | 0 | `DespachoRiego` |

Restricciones cruzadas: `crítico < umbral < objetivo`; `saturacion-bloqueo ≤ saturacion-alerta`;
`volumen-max-evento / caudal-emisor × 3600 ≤ 1200`.

**Salen:** `riego.tiempo-max-apertura`, `riego.max-riegos-24h`, `riego.max-riegos-24h-sector`.

**Valores existentes.** El catálogo guarda sólo overrides:
- Base sin override de `riego.umbral-humedad` → pasa sola de 42 a **45** al desplegar. Con override
  (alguien guardó 42 o 40) → se respeta. Lo mismo `riego.lluvia-probabilidad` 60 → 70.
- Overrides de las tres claves que salen: el servicio ya los ignora con un warn
  (`CatalogoParametrosService.java:139-143`). `res/migracion-reglas-riego.sql` los borra para que
  no queden filas huérfanas.
- Un override viejo que viole una restricción nueva no tira el arranque (las restricciones se
  validan al guardar); se documenta en el script cómo detectarlo.

**Caudal global, no por zona.** Hoy hay una sola línea y ningún aforo (v2 §12.3). El catálogo no tiene
alcance por MZ; sumarlo es un cambio de modelo (clave + zona, UI por zona) que conviene hacer cuando
haya aforos distintos de verdad.

**`riego.sectores-simultaneos` no lo usa ninguna regla** sino el despacho. Para que `usadoPor` no
mienta, `CatalogoParametros` acepta además de las reglas una lista de `ConsumidorParametros`
(`name()`, `parametros()`), interface que `Rule` pasa a extender y que implementa `DespachoRiego`.
`usadoPor` queda `["DespachoRiego"]`. El despacho lee el valor con un helper que exige que la clave
esté declarada (misma garantía que `Evaluacion`).

### D9 — Las dos salvaguardas mínimas fuera de riego (justificadas)

1. **Frescura de la humedad de sustrato.** Si la sonda falla, el nodo no manda `humSus` y el backend
   conserva el valor viejo (`NurseryService.java:465`) mientras las otras métricas refrescan la zona:
   `StaleSensorRule` no lo detecta y R-02 regaría para siempre con una humedad congelada. Se agrega
   `zona.hum_sus_ts` (se escribe sólo cuando llega `humSus`) y `StaleSensorRule` suma una segunda
   comparación "Antigüedad de la humedad de sustrato" contra el mismo
   `seguridad.antiguedad-max-lectura`. Es un pedazo de S-03, el mínimo para no regar a ciegas.
2. **Tope de R-02 sin S-06.** v2 limita R-02 a "1 riego cada 12 h por sector" sólo cuando S-06
   bloqueó la MZ (§4, excepción). Sin S-06 un sensor roto que marca "seco" dispararía R-02 en cada
   ciclo: 6 × 6 L = 36 L/día en 10 L de sustrato. Se aplica el tope **siempre** con el mismo
   parámetro (`riego.exceptuado-bloqueo`, 12 h), que S-06 después reutiliza. Costo: si hubiera dos
   déficits críticos reales en 12 h, el segundo espera. v2 §1.2 argumenta que pasar de 45 a 35 en
   4 h no ocurre, y después de 6 L el sector queda cerca de 65 %. **DA-4**.

### D10 — Alertas mínimas

Sin subsistema nuevo. `historial_evento` suma `alerta` (nulo | `INFO` | `WARNING` | `CRITICAL`) y
`regla`. Una acción `ALERTA` de una regla se persiste como evento `tipo = "Alerta"` **una vez por MZ,
regla y ciclo de lectura** (registro en memoria `zona|regla|inicioCiclo`; tras un reinicio puede
repetirse una vez). Así se cumple el principio 8 sin un evento por sector y por evaluación.

| Regla | Nivel | Qué queda |
|---|---|---|
| R-01 | INFO | El propio evento "Riego" (sector, volumen, duración, humedad, regla) — HU-06 CA-05 |
| R-02 | CRITICAL | "Déficit hídrico crítico" (MZ) + los eventos "Riego" con `regla = DeficitCriticoRule` |
| R-03 | INFO | "Riego pospuesto por pronóstico de lluvia" (MZ, con prob. y mm) |
| R-04 | WARNING (≥ 80 %) | "Sustrato saturado, riesgo de asfixia radicular y hongos" (MZ) |
| R-05, R-06 | — | Sin alerta en v2 (sólo el registro de inacción de siempre) |

Se usan los niveles de v2 y HU-10; la spec viva `alertas-inteligentes` (CRÍTICA/ALTA/MEDIA/INFO,
endpoint propio) no está implementada y queda para el cambio de alertas (**DA-8**).

### D11 — Contexto de riego: una consulta por zona

`RuleContext` suma `ContextoRiego riego` con un constructor secundario que mantiene la firma actual
(13 `new RuleContext(...)` en main y tests no cambian):

```java
record ContextoRiego(Instant inicioCiclo, Long ultimoRiegoMs, Long ultimoRiegoCriticoMs,
                     Long ultimaAplicacionMs, Long riegoEnCursoHastaMs, Long humSusTs) {
    static ContextoRiego vacio() { … }   // barrido y tests viejos
}
```

`NurseryService.updateTelemetry` lo arma **una vez por zona** con una consulta agrupada
(`HistorialRepository.ultimosPorSector(zonaId, desde)`: `MAX(ts)` por sector, tipo y regla para
`Riego` e `Insumo`) en vez de las dos `countByTipoAndSectorAndPeriod` por sector que hacen hoy
`IrrigationRule` y `DailyVolumeLimitRule` (200 consultas por mensaje). R-06 toma la última
aplicación de los eventos `Insumo` (hoy una sola bomba: fertilizante o fitosanitario, ambos cuentan).

### D12 — DAG, Inspector y frontend

Verificado: `/api/rules/schema` se arma de `ruleOrchestrator.getRules()` agrupando por rama
(`be/controller/RuleEngineSchemaController.java:59-118`) y `/api/rules/parametros` de las reglas del
catálogo (`CatalogoParametrosService.java:90-97`). Las reglas nuevas aparecen solas en modo `http`,
con sus parámetros y su traza. Lo que **no** es automático:

- `fe/types/domain.ts:582` `OperadorComparacion` suma `'EN'` (y el Inspector lo muestra como "∈").
- Modo `mock`: `fe/data/mock/catalogoReglas.fixture.json` y `fe/data/mock/trazaReglas.ts` replican
  las reglas por nombre (`trazaReglas.ts:121-147`): hay que regenerarlos.
- `fe/components/DAGViewer/RuleGraph.tsx:129` mapea `'Riego' → 'IrrigationRule'` a mano: pasa a usar
  `record.regla` y, sin él, `RiegoPorDeficitRule`.
- Pestaña Parámetros: un parámetro cuyo `usadoPor` no es una regla (`DespachoRiego`) se muestra en un
  grupo "Ejecución del riego".
- Historial: `HistorialEvento` suma `regla`, `alerta`, `volumenL`, `duracionSeg` (aditivo); el filtro
  de tipo suma "Alerta".

Estas tareas van **después** de que cierre el frontend de `add-catalogo-umbrales-reglas` (§7–8 de su
`tasks.md`), que hoy está en curso sobre los mismos archivos.

### D13 — Contrato MQTT, firmware y simulador

| Lugar | Cambio |
|---|---|
| `fw/comun/contrato.h` | `CONTRATO_VALVULA_DURACION_MAX_SEG = 1200`: lo máximo que el backend puede pedir en `durationSec` |
| `fw/comun/config.example.h:96` | `LIMITE_VALVULA_SEG_MAX` 120 → 1200, con el porqué |
| `fw/actuacion/act_valvula.cpp` | `static_assert(LIMITE_VALVULA_SEG_MAX >= CONTRATO_VALVULA_DURACION_MAX_SEG)`: un `config.h` local con 120 deja de compilar en vez de recortar en silencio |
| `be/mqtt/ContratoNodo.java` | `DURACION_VALVULA_MAX_SEG = 1200` |
| `simulador/server/contract.ts` | `VALVE_MAX_DURATION_SEC = 1200`; `mqtt.ts` avisa en el log si un comando la supera |

El formato del comando no cambia (`{"actuador":"valve","accion":"ON","parametros":{"durationSec":N}}`).

**Caudalímetro.** `act_valvula.cpp:42-54` cierra a los 10 s si no hay pulsos. v2 §1.2: el caudalímetro
"se incorpora en la versión final" y en el prototipo riega una bomba. Sin él, **todo riego terminaría
en `falla_hidraulica` a los 10 s**. Se agrega `CAUDALIMETRO_INSTALADO` (fábrica 0) en
`config.example.h`; con 0 se saltea la verificación de flujo. **DA-3**.

**Simulador.** Sigue probando todo sin que el backend lo conozca: publica la humedad en el tópico de
siempre y loguea los comandos que recibe (`simulador/server/mqtt.ts:73-92`). Para acelerar, desde
`/reglas` se sube el caudal (120 L/h → riegos de 1–3 min) o se corre la ventana para ver R-05. R-03
no se puede forzar a mano (pronóstico real): lo cubren los tests con un `WeatherClient` falso.

## Decisiones abiertas

Cada una tiene el default aplicado en este diseño.

- **DA-1 · Ciclo de lectura = `intervaloSensadoMinutos` acotado a 60–360, anclado a las 02:00.**
  *Default: sí.* Alternativas: parámetro propio de riego (duplica), ventana móvil (D5).
- **DA-2 · Límite de la válvula 1200 s** (firmware, contrato, simulador) con restricción cruzada.
  *Default: sí.* Alternativa: 900 s y rango de volumen máx. recortado a 7,5 L.
- **DA-3 · `CAUDALIMETRO_INSTALADO 0` de fábrica.** *Default: sí* (v2 §1.2). Si el prototipo ya
  tiene caudalímetro, poner 1 en su `config.h`.
- **DA-4 · Tope de R-02 (1 cada 12 h) aplicado siempre mientras no exista S-06.** *Default: sí.*
- **DA-5 · Riesgo residual: sensor trabado en un valor seco plausible.** Sin E-01/S-06 nada lo
  detecta: con 40 % fijo, R-01 riega ~5 L en cada ciclo de la ventana (06, 10, 14, 18 h) ≈ 20 L/día
  por sector. Hoy el sistema riega como mucho 120 s por día y por sector. *Default: implementar así.*
  **BLOQUEANTE para operar con plantines reales** (no para implementar ni probar con el simulador):
  antes de campo, implementar E-01 + S-06, o bajar `riego.volumen-max-evento` y vigilar el historial.
- **DA-6 · Ventana cerrada a minuto (18:00:59 adentro).** *Default: sí* (v2 R-05).
- **DA-7 · Lluvia en la ventana = probabilidad máxima horaria y suma de mm.** *Default: sí.*
  Alternativa: probabilidad combinada `1 − ∏(1 − pᵢ)` (más alta, pospone más).
- **DA-8 · Alertas como eventos "Alerta" del historial con niveles v2, una por MZ y ciclo.**
  *Default: sí*; la spec `alertas-inteligentes` queda desalineada hasta el cambio de alertas.
- **DA-9 · `riego.sectores-simultaneos` consumido por el despacho, no por una regla.** *Default: sí*,
  con `ConsumidorParametros` y grupo "Ejecución del riego" en la UI.
- **DA-10 · Dar de baja `riegoVolMaxDiarioMl`** (`configuracion_operativa`, zombi desde el cambio
  anterior: su único uso era el texto de `IrrigationRule.java:111`). *Default: sí, en el bloque 9*,
  que toca `LimitesActuadoresForm` y por eso va al final.
- **DA-11 · Umbral de riego 42 → 45 por fábrica, respetando overrides.** *Default: sí.*
- **DA-12 · El firmware bloquea la tarea de actuación mientras riega** (`vTaskDelay`,
  `act_valvula.cpp:57-60`): con 1200 s, un comando de bomba o mediasombra al mismo nodo espera hasta
  20 min. *Default: aceptarlo en el prototipo*; el cierre por timer queda para el cambio de firmware.
- **DA-13 · Las reglas nuevas emiten `NOOP_INFO` como las existentes** (el DAG del Historial los
  usa). *Default: sí.* Con el nodo real cada 30 s eso es ~13 filas × 100 sectores por mensaje
  (~3,7 M filas/día por MZ). **Resuelta en la revisión de la conmutación (C7)**: se registra sólo cuando cambia
  la decisión del sector.

## Risks / Trade-offs

- **Doble riego al cruzar el ciclo.** Una ronda que empieza 10 min antes de un borde de ciclo puede
  regar dos veces la primera tanda (v2 tiene el mismo efecto si una ronda cruzara una lectura). Con
  fábrica una ronda dura ≤ 10 tandas × 720 s = 2 h; acotado a 2 riegos.
- **Cola en memoria.** Un reinicio pierde lo pendiente; lo vuelve a decidir la siguiente telemetría.
- **El nodo testigo es uno por MZ.** Todas las decisiones de la MZ salen de un sector. Es de v2 (§12.10).
- **Firmware desplegado con 120 s** recorta los riegos largos en silencio hasta reflashear; el
  `static_assert` lo evita sólo al recompilar.
- **Pronóstico con horas corridas** hasta este cambio: comparar trazas viejas y nuevas de lluvia puede
  confundir.

## Migration Plan

1. Infraestructura sin cambio de comportamiento: reloj, `Operador.EN`, `compararVentana`, acción
   tipada, `ContextoRiego`, columnas nuevas del historial y de la zona.
2. Pronóstico con mm y zona horaria.
3. Parámetros nuevos en el catálogo (aún sin uso salvo los que ya existen).
4. Reglas nuevas + despacho; baja de las tres viejas y sus parámetros. Este es el paso que cambia el
   comportamiento.
5. Contrato/firmware/simulador.
6. Frontend y documentación.

`res/migracion-reglas-riego.sql` (manual, mismo formato que `migracion-catalogo-parametros.sql`):
borra overrides de las claves eliminadas; (con DA-10) baja `riego_vol_max_diario_ml`; índice
opcional `historial_evento(zona_id, tipo, ts)`. Las columnas nuevas son nulas y las agrega
`ddl-auto=update`.

**Rollback:** revertir el código. Las columnas nuevas quedan sin uso. `actuador_valve` vuelve a
usarse con los valores que tenía (posiblemente "Regando" viejos: el script de rollback comentado las
pone en "Cerrada").

## Desvíos de implementación

Anotados al implementar los bloques 0–8 (los bloques siguientes parten de esto, no del texto original):

- **Fábricas 45 / 70 (D8, DA-11).** `riego.umbral-humedad` y `riego.lluvia-probabilidad` quedan en
  **42 y 60** hasta la conmutación: `IrrigationRule` y `WeatherOverrideRule` las leen y el
  comportamiento no puede cambiar antes del bloque 10. Se pasan a 45 / 70 en **10.5**, junto con las
  reglas nuevas (hay que ajustar `ParametrosRealesTest` y `ReglasParametrosCatalogoRealTest`, que hoy
  esperan 42).
- **Frescura de la humedad (D9.1).** `StaleSensorRule` lee `zona.humSusTs` de la entidad, no
  `ContextoRiego.humSusTs`: así funciona igual en el barrido (que usa `ContextoRiego.vacio()`). El campo
  `ContextoRiego.humSusTs` existe pero nadie lo completa ni lo lee; el bloque 10 puede completarlo o
  borrarlo. Efecto visible al desplegar: una zona sin `hum_sus_ts` (todas, hasta la primera telemetría con
  `humSus`) bloquea el riego con `SIN_DATO`; con el nodo a 30 s se normaliza en el primer mensaje. La
  traza de `StaleSensorRule` ahora tiene siempre **dos** comparaciones.
- **`ultimosPorSector` (D11).** Devuelve filas `(sectorId, tipo, regla, MAX(ts))` (proyección
  `HistorialRepository.UltimoEvento`); el último riego de un sector es el mayor `ts` de sus filas
  "Riego" y el de R-02 el de la fila con `regla = DeficitCriticoRule`. Armar `ContextoRiego` con eso es
  trabajo del bloque 10.2.
- **Cola y despacho (D4).** `ColaRiego.retirar(zonaId, sectorId)` (hace falta la zona) y
  `SolicitudRiego` con datos planos (`zonaId, sectorId, numero, detalle, regla, solicitadaEn`), no la
  entidad: la crea el hilo MQTT y la consume el scheduler. El despacho vuelve a cargar el `SectorEntity`
  con `findById` dentro del tick (transaccional) para `registrarRiego(sector, detalle, regla, ts)`.
  Orden de las escrituras: publicar → marcar "en curso" y sacar de la cola → registrar en el historial
  (si la base falla después de abrir la válvula, el sector no puede volver a pedirse). El despacho además
  **descarta** una solicitud de un sector que ya está regando (defensa contra la ventana entre el
  despacho y la lectura siguiente), y descarta por bloqueo manual aunque no haya cupo.
- **`CatalogoParametros` (D8).** Su constructor de Spring recibe `List<ConsumidorParametros>`;
  `DespachoRiego` inyecta el `CatalogoParametrosService` con `@Lazy` para cortar el ciclo.
- **Pronóstico (D6).** La hora actual se busca por marca completa (`yyyy-MM-ddTHH:00`), no sólo por la
  hora del día; `precipitation` ausente en la respuesta es modo degradado (`null`). Las marcas `horas`
  incluyen la hora actual como primera (desde ahí hasta +24 h) y `lluviaProxima` la excluye.
- **Intervalo de sensado (D5).** El rango 60–360 reemplaza al chequeo "positivo" (`requirePositive`) de
  ese campo en `ConfiguracionService`.
- **`tasks.md` 7.1.** El ejemplo "caudal 10 y volumen 6 → 1200 s" era inconsistente (6 L a 10 L/h son
  2160 s y se recortan): se testea 10 L a 30 L/h y 6 L a 18 L/h.

### Correcciones de la revisión de §0–§8 (commit `fix(backend)`)

- **DA-1 (desvío): el guardado del intervalo de sensado NO valida 60–360.** Vuelve a exigir sólo "positivo"
  (`ConfiguracionService`); el acotamiento a 60–360 vive únicamente en `CicloLectura.acotarMinutos`. Validarlo
  al guardar rompía las bases con un valor viejo fuera de rango (cualquier guardado de Configuración fallaba),
  contradecía al validador del frontend y le impedía al simulador emitir más rápido que cada 60 min (deriva su
  período de ese valor, que es como se prueba el sistema). Con un valor fuera de rango el ciclo usa 60 / 360 y
  se loguea (una vez por valor).
- **Despacho (D4).** `tick()` ya no es `@Transactional`: cada riego se registra en su propia transacción
  (`TransactionTemplate`), confirmada apenas después de publicar, sin transacción ni conexión abierta durante
  el MQTT. Si el registro falla, el sector queda en curso en memoria (no se republica) y el tick sigue. Cada
  solicitud se **reclama** con `ColaRiego.retirarSiCoincide` (misma instancia, bajo el candado) antes de
  publicar; si falla el broker se repone con `reponerSiAusente` (nunca pisa una decisión más nueva); y el
  bloqueo manual del sector y de la zona se revalida justo antes de abrir. Tiene su propio scheduler
  (`despachoScheduler`).
- **Orden despachable.** `ColaRiego.solicitar` devuelve `boolean` y rechaza (cancelando la solicitud anterior
  del sector) volumen no finito o ≤ 0 y duración fuera de 1…1200 s. `CalculoRiego` devuelve el plan vacío
  `(0 L, 0 s)` para un volumen que redondea a 0 y lanza `IllegalArgumentException` ante valores no finitos;
  R-01 y R-02 no emiten `ACTIVAR_VALVULA` con plan vacío.
- **Pronóstico (D6).** `PronosticoHora` y `LluviaPrevista` usan `Double` anulables: una hora sin probabilidad o
  sin milímetros ya no se lee como 0 ni cuenta en `horasCubiertas`. R-03 deja `SIN_DATO` la comparación a la
  que le falta el dato y no pospone. Sin `precipitation` en la respuesta se degrada sólo la lluvia acumulada
  (el UV de `ShadingRule` y el widget siguen).
- **Relojes.** `HistorialService` (sellos, vencimiento del seguimiento, "hace N", KPI del día en
  `America/Argentina/Buenos_Aires`), `WeatherService`/`WeatherForecast.isFresh(maxAge, ahora)`, la frescura del
  snapshot de `NurseryService` y el día de ciclo de `ShadingRule` usan el `Clock` del vivero y su zona.
- **Topología regenerada.** Después del commit se vacían también la cola de riego y lo que estaba regando.

### Desvíos de los bloques 9 y 10 (reglas y conmutación)

- **Compuertas R-03/R-05/R-06 y "aplica R-01".** Registran SIEMPRE las dos comparaciones `humedad ≥ crítico` y
  `humedad < umbral` (no sólo la que falla), así el Inspector muestra por qué aplicaron o no. El motivo de
  "no aplica" distingue "lo cubre R-02" de "no hay déficit" y de "sin lectura".
- **Regla de ciclo (D5).** Sus comparaciones son en minutos ("desde el último riego" ≤ "desde el inicio del
  ciclo") y en segundos restantes del riego en curso (0 si no hay): el Inspector no muestra epoch ms. Un sector
  que nunca regó queda `SIN_DATO` en la primera comparación (no corta). El "riego en curso" lo da
  `DespachoRiego.finRiegoEnCurso` (memoria, incluye el margen de 5 s), que además cubre la ventana entre el
  despacho y la escritura del historial.
- **R-02 sin riego crítico previo** no registra la comparación del tope (no hay con qué comparar); con uno
  previo la registra (`horas desde el último riego crítico < exceptuado-bloqueo`).
- **R-03 y "sin dato".** Probabilidad y milímetros se comparan por separado; si falta cualquiera de los dos
  (sin pronóstico, sin marcas horarias o la API devolvió `null`) esa comparación queda `SIN_DATO` y no pospone.
- **Alertas (D10).** `registrarAlerta` guarda `sectorId = "—"` (alcance macro-zona, como los eventos de
  Configuración) y `sev = "—"`. La deduplicación `zona|regla → inicio de ciclo` es en memoria; sin inicio de
  ciclo en el contexto (barrido) lo calcula del reloj y del intervalo; si la persistencia falla se reintenta
  en la próxima evaluación del ciclo.
- **`ContextoRiego.humSusTs`** se completa con `zona.humSusTs` (no lo lee ninguna regla: `StaleSensorRule` sigue
  leyendo la entidad).
- **10.4 sin `@SpringBootTest`.** Levantar el contexto apunta a la base de desarrollo y al broker reales y el
  despacho publicaría comandos de verdad; el test de punta a punta cablea las piezas reales a mano (reglas,
  orquestador, executor, cola, despacho, `NurseryService` y `NurseryWatchdog`) con repositorios en memoria,
  publicador falso y `RelojDePrueba`. El cableado de Spring lo cubren `ReglasParametrosCatalogoRealTest` (arranca
  el contexto con las siete reglas y el `DespachoRiego`) y `SchedulersConfigTest`.
- **10.6 con contexto real.** El orden de la rama RIEGO del DAG, las etiquetas y el `usadoPor` se verifican en
  `ReglasParametrosCatalogoRealTest` (contexto real); `RuleEngineSchemaControllerTest` (`@WebMvcTest`) sólo
  conserva la forma del DTO con reglas falsas.
- **Barrido (D4).** Sin cambios de diseño: evalúa sin métricas y con `ContextoRiego.vacio()`, `BARRIDO` no toca
  la cola, y una lectura vieja la corta `StaleSensorRule`. No hay riegos duplicados ni reencolado por pasada
  (cubierto en `RiegoIntegracionTest`).
- **Pruebas de §9 con fábricas 42/60.** Mientras las reglas viejas estuvieron registradas, los tests fijaban a
  mano 45 / 70 (`evV2`); al pasar las fábricas en 10.5 se volvió a `ev`.

### Desvíos de los bloques 11 a 13 (contrato, baja de `riegoVolMaxDiarioMl`, frontend)

- **Firmware sin compilar (11.2).** No hay toolchain en la máquina de desarrollo (`pio`, `platformio` ni `arduino-cli`):
  el cambio de `act_valvula.cpp` (`static_assert`, `#error` si falta `CAUDALIMETRO_INSTALADO`, y verificación de flujo
  bajo `#if CAUDALIMETRO_INSTALADO`) se revisó a mano y queda **sin compilar**. Un `config.h` local sin el flag o con
  `LIMITE_VALVULA_SEG_MAX 120` falla al compilar a propósito.
- **Test del simulador (11.3).** El simulador no tenía runner: se sumó `npm test` (`tsx --test`, nativo de Node) y la lógica
  del aviso vive en `valveDurationWarning` (`contract.ts`), que `mqtt.ts` sólo llama. El test de 11.1 del backend además
  compara el valor contra `contrato.h` (se saltea si el firmware no está en el checkout).
- **`riegoVolMaxDiarioMl` (DA-10).** `DROP COLUMN` en `migracion-reglas-riego.sql` (con su rollback comentado). La columna
  era `NOT NULL`: hasta correr el script, el primer guardado de Configuración sobre una base **sin fila** falla; con la
  fila existente no. Un cliente viejo que todavía mande el campo no rompe (Jackson ignora lo desconocido).
- **Fixture del mock regenerado desde el backend (13.2).** Se generó leyendo los enums y las reglas del backend (no a mano) y
  el test anti-drift vuelve a exigir igualdad total: mismas claves y definiciones, mismas reglas con su prioridad y sus
  parámetros, y `usadoPor` incluyendo a los consumidores que no son reglas (`DespachoRiego`).
- **Demo con casos de riego (13.2).** La lectura sorteada del vivero demo casi nunca está seca, así que MZ-2/3/4/5 fuerzan su
  humedad de sustrato (40, 41, 30 y 82 %) para mostrar déficit común, pospuesto por lluvia, déficit crítico y saturado;
  esas zonas cambian de estado (la humedad baja es "en observación" o "crítico"). Con la lluvia prevista (MZ-3) el déficit
  común no deja válvulas abiertas ni en cola; el crítico riega igual. Los "Regando" (los 10 primeros de cada zona con
  déficit) tienen un riego despachado en el ciclo: ahí corta la regla de ciclo.
- **Grupo "Ejecución del riego" (13.3).** Va justo después de la rama a la que pertenece el consumidor (Riego) y no se
  colapsa (un solo parámetro); el consumidor se rotula por un nombre legible en lugar del id de la clase.
- **Layout del DAG del Inspector.** Con la rama de riego en siete nodos y etiquetas largas (`Minutos desde el último riego
  (contra los del ciclo en curso)`), los nodos se solapaban: `ALTO_FILA` 142 → 172.
- **Historial (13.4/13.5).** Un riego "Pospuesta" o "Abortada" ya no se pinta como acción en el DAG del Historial. Las
  alertas (`sectorId` "—") no se atribuyen a ningún nodo ni ofrecen "razonamiento del motor"; se agrupan como "Alertas de
  la macro-zona" y llevan un ícono propio (el backend les cae en el de riego por no definir uno para "Alerta").

### Correcciones de la revisión de la conmutación (commits `fix(backend)` sobre `c5de65c`)

Una revisión independiente del commit `c5de65c` encontró problemas de seguridad operativa en cómo se EJECUTA el
riego (válvulas reales). Criterio de desempate: ante dos implementaciones, la que nunca riega con datos inválidos
y nunca deja sin regar un déficit crítico.

**C1 · La ronda se completa; sólo la cancela una cancelación explícita (cambia D4).** El nodo testigo mide UN
sector. Cuando el despacho lo riega la humedad sube, R-01 y R-02 dejan de aplicar y, con el nodo publicando cada
30 s, la regla "sin `ACTIVAR_VALVULA` se retira de la cola" vaciaba la ronda: los otros 90 sectores quedaban sin
agua (y en el ciclo siguiente la humedad seguía alta). La spec decide con la lectura y riega "cada sector de la
MZ". Ahora una solicitud en cola **pertenece a la ronda decidida y se completa**. Una nueva decisión de riego
para un sector ya encolado la actualiza (R-01 → R-02 si empeoró), nunca la duplica, y **no se degrada** (una
solicitud de R-02 no pasa a R-01 aunque la humedad mejore un poco: perdería el volumen máximo y la
independencia de la ventana).

*Cómo se distingue "cancelación de seguridad" de "no aplica":* una marca explícita en la acción, no el texto ni el
nombre de la regla en el executor. `ABORT_RIEGO`/`ABORT_ALL` pueden llevar `DetalleAccion = CancelaRiego`
(`TODAS` | `SOLO_DEFICIT_COMUN`) y el `ActionExecutor` retira la solicitud sólo si le alcanza. Cada regla declara
si cancela:

| Motivo (regla) | ¿Retira de la cola? | Alcance | ¿Quién lo decide? |
|---|---|---|---|
| Humedad se recuperó (R-01/R-02 "no aplica") | **No** | — | telemetría (no hace nada) |
| Sustrato saturado, R-04 | Sí | toda solicitud | telemetría (explícita) **y** despacho (revalida `humedad < saturacion-bloqueo`) |
| Bloqueo manual (`ManualLockRule`, `ABORT_ALL`) | Sí | toda solicitud | telemetría (explícita) **y** despacho (revalida) |
| Sensor sin datos / humedad congelada (`StaleSensorRule`, S-02) | Sí | toda solicitud | telemetría (explícita) **y** despacho (revalida la antigüedad) |
| Ventana cerrada, R-05 | Sí | sólo las de R-01 | telemetría (explícita) **y** despacho (revalida la hora, al minuto) |
| Pausa por aplicación, R-06 | Sí | sólo las de R-01 (de ESE sector) | telemetría (explícita) |
| Regla de ciclo (ya regó / riego en curso) | No | — | despacho (ya regando → descarta la repetida) |
| Tope de 12 h de R-02 | No | — | — (corta la evaluación, no la ronda) |
| R-03 (lluvia) | No | — | — (**riesgo residual**, ver abajo) |

**C2 · El despacho revalida con datos actuales antes de abrir CADA válvula (cierra S-02 y R-05 en la ejecución).**
Una ronda tarda ~80 min en despacharse y el despacho sólo miraba el bloqueo manual: (A) con el nodo muerto y 90
sectores en cola seguía abriendo válvulas; (B) una solicitud de R-01 de las 17:58 se despachaba de noche si no
llegaba telemetría. Ahora, por zona y por tick, antes del cupo (lo vencido no espera lugar): zona leída de la base
(sin poder leerla no se abre nada y la solicitud sigue); lectura y humedad vigentes con la misma definición que
`StaleSensorRule` (`FrescuraLectura`, compartida, mismo `seguridad.antiguedad-max-lectura`); humedad actual `<
riego.saturacion-bloqueo`; y, para las de R-01, hora dentro de `riego.ventana-normal` (cerrada al minuto). R-02 no
depende de la ventana. Lo que falla se retira de la cola, se loguea y deja UNA alerta `WARNING` ("Riego descartado
al despachar") por zona y motivo (regla `DespachoRiego`; se descarta de una vez, así que no se repite). El despacho
declara en `parametros()` los cuatro que consume (`sectores-simultaneos`, `antiguedad-max-lectura`,
`saturacion-bloqueo`, `ventana-normal`) y el catálogo los muestra en `usadoPor`.

**C3 · La guarda de ciclo ya no frena a R-02 (cambia D5).** `CicloLecturaRiegoRule` mantiene su prioridad y su
nombre, pero sus dos guardas valen distinto: *riego en curso* corta a todos; *ya regó en este ciclo* corta sólo a
R-01. Con humedad `< riego.umbral-critico` (parámetro que ahora declara) no corta por el ciclo, así R-02 queda
sólo con su tope de 12 h; sin lectura de humedad corta (sin dato no se arriesga). Se descartó una regla nueva o
mover el chequeo a R-01: R-03/R-05/R-06 se evaluarían antes y la traza diría "pospuesto por lluvia" para un sector
que ya regó. Costo conocido: tras un riego de R-01, una caída posterior bajo el umbral crítico dentro del mismo
ciclo puede volver a regar el sector con R-02 (es lo que dice la spec: sólo su tope).

**C4 · Último riego en memoria.** Si el registro en historial falla después de abrir la válvula, pasada la duración
+ 5 s el ciclo ya no veía ese riego. El despacho guarda en memoria el último riego (y el último de R-02) por
sector; `NurseryService` toma el más reciente entre eso y el historial para la guarda de ciclo y el tope de R-02.

**C5 · El pronóstico no traba el hilo de la telemetría.** `updateTelemetry` pedía el pronóstico una vez POR SECTOR
(100 por mensaje) y, con la API caída, `WeatherService` hacía 3 reintentos con `Thread.sleep` (1 + 2 s) sin cachear
el fallo: ~100 × varios segundos por mensaje sobre el hilo del broker MQTT, justo sin internet (O-01) y bloqueando a
R-02. Ahora: (a) una consulta por evaluación de zona (y una por barrido); (b) el fallo se cachea
`yerbanalytics.weather.failure-cache-ttl-ms` (60 s); (c) `getForecastSinEspera()` — la que usa la telemetría — nunca
espera: devuelve el cacheado (vencido hasta 4 TTL, mejor uno de hace 20 min que ninguno) y refresca en un hilo
aparte, una consulta a la vez; sin nada usable evalúa sin pronóstico (la traza lo muestra `SIN_DATO`). `getForecast()`
(watchdog y snapshot) conserva los reintentos pero respeta el fallo cacheado. Se descartó un `@Scheduled` de
refresco (otro reloj que configurar) y un intento síncrono único en el hilo MQTT (el cliente HTTP no tiene timeout).
Riesgo residual: el primer mensaje tras arrancar no tiene pronóstico y R-03 queda sin dato; como R-03 no cancela lo
ya encolado (C1), ese mensaje puede encolar una ronda que la lluvia habría pospuesto.

**C6 · Índices de `historial_evento`.** `ultimosPorSector` filtra por `zona_id`, `tipo IN ('Riego','Insumo')` y `ts`
(ya restringida a los tipos del riego; excluye las filas "Info") y corre en cada mensaje del nodo, pero el índice sólo
estaba en el script manual. Se declaran en la entidad (`@Table(indexes)`; `ddl-auto=update` los crea) `(zona_id, tipo,
ts)` —igualdad, lista y rango, el orden que el planificador necesita— y `(tipo, ts)` para `riegosDesde` (reconstrucción
tras un reinicio) y los KPI. El script manual conserva el mismo nombre. Sin verificar contra el plan de ejecución real
de la base (sólo contra el orden de columnas).

**C7 · El Registro de Inacción escribe sólo cuando cambia la decisión (resuelve DA-13).** Cada `NOOP_INFO`/`ABORT_*`/
`POSTPONE_RIEGO` escribía una fila "Info" por sector, por regla y por evaluación: ~13 filas × 100 sectores = ~1.300 por
mensaje de una zona, ~3,7 millones por día por zona con el nodo real a 30 s. Ahora el `ActionExecutor` recuerda, por
sector y por origen (telemetría / barrido), `regla → tipo de acción + clave estable del motivo` (el texto con los
números reemplazados por `#`: la humedad exacta o los segundos restantes no son otra decisión) y escribe sólo si la
evaluación difiere de la última registrada. **Si cambia CUALQUIER regla del sector se escribe el conjunto completo de
sus reglas en esa evaluación**: es lo que necesita el DAG del Historial. Impacto en el frontend (verificado leyendo
`HistorialTimeline.tsx` y `RuleGraph.tsx`): el Historial agrupa las filas por minuto, zona y sector y `RuleGraph`
pinta el DAG con las filas de ese sector en ese minuto (la regla sale de "Ciclo de evaluación: X", los nodos de
prioridad menor a la del evento quedan "pasó"). Con el conjunto completo en cada cambio el DAG de ese evento queda
igual; un evento "Riego" (que ahora puede caer en un minuto sin filas Info, porque la decisión se tomó antes) se
pinta con su sola fila: `regla` + prioridad dejan "pasadas" las reglas anteriores. Lo que cambia es sólo cosmético:
los contadores "N decisiones" del ciclo bajan y el historial muestra los cambios, no cada evaluación. Estado en memoria:
tras un reinicio se registra una vez más; se limpia al regenerar la topología (`ActionExecutor.reiniciarEstado()`). El
barrido (sin métricas, decisión distinta) lleva su propio estado y no repite lo que ya dejó la telemetría. Régimen
estable: **0 filas por mensaje**. Si la escritura falla no se da por registrada y se reintenta. Se descartó dedupe
por ventana de tiempo (esconde decisiones que sí cambian) y persistir el estado (otra tabla para algo que se
reconstruye con una evaluación).

**Frontend de la corrección.** Sólo lo que cambió de forma visible: el fixture del catálogo (la regla de ciclo declara
`riego.umbral-critico`; el despacho figura en `usadoPor` de los tres parámetros que revalida) y su test anti-drift, la
traza mock de `CicloLecturaRiegoRule` (R-02 pasa; el riego en curso corta a todos) y la agrupación "Ejecución del
riego" (sólo lista los parámetros que ninguna regla usa; los compartidos se editan bajo su regla). El descarte por
revalidación no tiene traza (el despacho no es una regla): no hay nada que espejar.

**Sin corregir (documentado):**
- R-06 depende de eventos "Insumo" y la bomba conserva el enganche "Dosificando" (rama de insumos, fuera de alcance).
- "Publicado" = entregado al cliente MQTT, sin ACK: el despacho da la válvula por abierta cuando el cliente la
  aceptó, no cuando el nodo confirmó.
- El cupo se llena por número de sector, no por urgencia ni por humedad.
- R-03 no cancela lo ya encolado (la lluvia pronosticada después de decidir no retira la ronda): la lista de
  cancelaciones de seguridad es la confirmada y R-03 no está. Además, en el primer mensaje tras arrancar (sin
  pronóstico cacheado) R-03 no tiene dato; ver C5.

### Correcciones de la revisión del estado `221f5d9` (C8–C11)

Una revisión independiente de `221f5d9` y una verificación en ejecución real encontraron cuatro problemas más; el
criterio de desempate es el mismo (nunca regar con datos inválidos, nunca dejar sin regar un déficit crítico).

**C8 · Con lectura no vigente el despacho PAUSA, no descarta (cambia C2).** C2 descartaba TODA la cola de la zona si la
lectura o la humedad tenían más de `seguridad.antiguedad-max-lectura` (90 s; el nodo publica cada 30 s). Si pasaba
después de regar al sector testigo, la humedad ya había subido, R-01 y R-02 no volvían a aplicar al volver el nodo y
los sectores pendientes quedaban sin regar (dos mensajes perdidos por WiFi alcanzaban). Ahora la lectura o la humedad
no vigentes (y la falta de humedad) **pausan**: no se abre ninguna válvula de la zona, la cola se conserva, sin alerta, y
se retoma sola cuando hay lectura vigente. Siguen **retirando** el bloqueo manual y la saturación actual (R-04): no son
"datos inciertos" sino una orden o una condición presente. `StaleSensorRule` deja de emitir `CancelaRiego.TODAS`: el
sensor sin datos sólo corta la evaluación (el motor no pide riego sin lectura) pero no vacía la cola.

| Motivo | ¿Qué hace? | ¿Quién decide? | R-01 | R-02 |
|---|---|---|---|---|
| Sensor sin datos / humedad congelada (S-02) | PAUSA (no abre, conserva) | despacho (telemetría: ninguna marca) | sí | sí |
| Bloqueo manual | RETIRA | telemetría (marca) y despacho | sí | sí |
| Saturación R-04 | RETIRA | telemetría (marca) y despacho | sí | sí |
| Ventana R-05 | RETIRA | telemetría (marca) y despacho | sí | no |
| Pausa tras aplicación R-06 | RETIRA | telemetría (marca) y despacho (C9) | sí | no |
| Lluvia R-03 | RETIRA | despacho (C9) | sí | no |
| Ya regó en el ciclo | RETIRA | despacho (C9) | sí | no |
| Tope de R-02 | RETIRA | despacho (C9) | no | sí |
| Vencimiento (C8) | RETIRA con alerta | despacho | sí | sí |
| La humedad se recuperó | NADA | — | — | — |

**Vencimiento.** Para que la pausa no deje solicitudes eternas ni se rieguen rondas de horas atrás, cada solicitud es
válida durante el ciclo de lectura en que se pidió **y el siguiente**, y vence al empezar el tercero:
`CicloLectura.vencida(solicitadaEn, ahora, minutos) = solicitadaEn < inicioDelCicloAnterior(ahora)`, donde el inicio
del ciclo anterior es el del ciclo que contiene el último milisegundo antes del inicio del actual (respeta el último
ciclo del día cortado a las 02:00). Con 240 min, una solicitud de las 10:05 sigue vigente hasta las 17:59:59 y vence a
las 18:00. El despacho lo mira primero, aunque la zona esté en pausa, y deja una alerta `WARNING` por zona al vencer
(sólo una vez: lo vencido se descarta de una vez). El intervalo sale de `configuracion_operativa` como el ciclo de las
reglas (acotado a 60–360). Costo conocido: con la ronda más larga de fábrica (~2 h) cabe sobrada en dos ciclos de 4 h;
con un intervalo de 60 min una ronda de 10 tandas (~2 h) no cabría en el ciclo + gracia y se cortaría: ajustar
`riego.sectores-simultaneos` o el caudal.

**C9 · El despacho revalida todas las precondiciones de R-01 que no son la humedad.** Faltaban tres: (a) R-06: una
dosificación recibida mientras R-01 esperaba su tanda no la retiraba (`PausaTrasAplicacionRule` sólo evalúa cuando aplica
R-01 y la humedad ya había subido) y el agua lavaba el producto; (b) R-03: el primer mensaje tras arrancar no tiene
pronóstico (`getForecastSinEspera` devuelve `null` en frío), R-01 encola la ronda y, cuando llega el pronóstico,
`POSTPONE_RIEGO` no retira nada; (c) la guarda de ciclo y el tope de R-02: con `duración + 5 s` menor que el tick (10 s)
un sector reencolado podía abrirse dos veces. Ahora, por solicitud de R-01, antes del cupo: ventana (ya estaba), pausa
R-06 (última aplicación `< riego.pausa-tras-aplicacion`), lluvia R-03 (pronóstico cacheado, sin esperar, con los dos
umbrales; sin dato no pospone, O-01) y "ya regó en este ciclo"; por solicitud de R-02, el tope
`riego.exceptuado-bloqueo`. Las condiciones viven en `PrecondicionesRiego` y las usan las reglas y el despacho (nada de
umbrales ni lógica duplicados); el despacho declara en `parametros()` todos los que lee (`pausa-tras-aplicacion`,
`lluvia-probabilidad`, `lluvia-mm`, `lluvia-ventana`, `exceptuado-bloqueo`, además de los de C2), así que el catálogo los
muestra en `usadoPor`. El último riego, riego crítico y aplicación salen de `ultimosPorSector` (una consulta por zona y
tick, sólo si hay algo en cola) mezclado con la memoria del despacho (C4). Sin poder leer el historial, las de R-01
esperan (no se sabe si hay pausa ni si ya regó) y las de R-02 siguen con la memoria: un déficit crítico no se deja sin
regar por una falla de lectura. Además `WeatherService.precalentar()` (`ApplicationReadyEvent`) pide el pronóstico en el
hilo de refresco al arrancar: reduce la ventana en frío, sin eliminarla (por eso la revalidación).

**C10 · R-04 antes de la guarda de ciclo (prioridades 2 y 3).** Verificado en ejecución: recién regado, cualquier
lectura con humedad alta (82 %) caía en `CicloLecturaRiegoRule` (prioridad 2), que cortaba la rama, y
`SustratoSaturadoRule` (3) quedaba omitida: sin alerta WARNING de saturación justo después de regar y sin cancelar la cola
por esa vía. Ahora `SustratoSaturadoRule` tiene prioridad 2 y la guarda de ciclo 3. Orden final de la rama RIEGO:
R-04 (2) → ciclo (3) → R-02 (4) → R-05 (6) → R-06 (7) → R-03 (8) → R-01 (10). R-04 y R-02 siguen siendo excluyentes y R-04
no mira el ciclo, así que el cambio no puede dejar a R-02 sin regar.

**C11 · Las marcas de cancelación tienen tests propios.** Antes ningún test comprobaba que `ManualLockRule`,
`FueraDeVentanaRiegoRule`, `PausaTrasAplicacionRule` y `SustratoSaturadoRule` emitieran su `CancelaRiego` (borrar la
marca no rompía nada); ahora cada regla lo prueba y `RiegoIntegracionTest` lo prueba de punta a punta, incluido que
`StaleSensorRule` NO la emite.

**Riesgos que quedan sólo documentados (no se corrigieron):**
- El firmware no cierra la válvula antes de tiempo y abre hasta 20 min seguidos (necesita rediseño del firmware, sin
  toolchain acá): una orden de 1200 s no se puede cancelar una vez publicada.
- R-02 encolada no baja a R-01 (si la humedad mejora, sigue con el volumen máximo; es C1 a propósito).
- Sin ACK: "publicado" es entregado al cliente MQTT.
- La bomba conserva el enganche "Dosificando" (R-06 depende de eventos "Insumo", rama de insumos fuera de alcance).
- La tarjeta "Clima y riesgo" del panel usa otro criterio que el motor para la lluvia (probabilidad de la hora actual).

### Endurecimiento de pronóstico, estado en memoria y migración (C12–C14)

**C12 · Timeouts del pronóstico.** `OpenMeteoWeatherClient` no configuraba timeouts HTTP: con la API sin responder el hilo
de refresco quedaba colgado para siempre, `refrescando` no se liberaba y el pronóstico pasaba a `null` en silencio. Ahora
la fábrica de pedidos lleva `yerbanalytics.weather.connect-timeout-ms` (3 s) y `read-timeout-ms` (5 s). Además un refresco
fallido siempre libera el flag (`finally`) y uno colgado se reemplaza pasados `WeatherService.REFRESCO_MAX` (60 s): el
refresco usa un hilo por consulta (no uno solo) y el flag es una referencia por identidad para que el hilo viejo, si
vuelve, no pise al nuevo.

**C13 · Las marcas en memoria del executor se aplican tras el commit.** `ActionExecutor` marcaba "inacción registrada" y
"alerta del ciclo enviada" antes del commit de la transacción de `updateTelemetry` / del barrido: con un rollback la
memoria quedaba diciendo "registrado" y esas filas no se volvían a escribir. Con una transacción activa las marcas
quedan en un estado pendiente por transacción (que también deduplica las 100 evaluaciones de la zona dentro de ella) y se
aplican en `afterCommit`; en un rollback se descartan; sin transacción se aplican de inmediato. **Sin corregir:** las
solicitudes ya encoladas en la `ColaRiego` dentro de una transacción que revierte siguen en la cola (la cola no participa
de la transacción): el despacho las revalida con los datos guardados, vencen, y la telemetría siguiente las vuelve a decidir.

**C14 · Migración del umbral de riego.** `migracion-catalogo-parametros.sql` copiaba el `ideal_min` de `humSus` como override
sólo "si difiere de 42"; con la fábrica nueva en 45, una base con 42 (el default del seed) no recibía override y pasaba de
42 a 45 sin aviso. Ahora compara contra la fábrica actual (45). Menores: `DailyDoseLimitRule` usa `ctx.now()` (el reloj
inyectado) y cita el umbral real (`insumo.max-dosis-24h`, en dosis) en lugar del campo viejo en ml; `DespachoRiego.tick()`
aísla cada zona con un `try/catch` y las recorre en orden de id.
