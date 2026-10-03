# Diferencias: motor de reglas implementado vs. `reglas_v2.md`

> Actualizado el 03/10/2026, sobre la rama `feat/implementar-nuevas-reglas` (HEAD `824a830`).
> Fuente de verdad del lado del código: `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/` (se abrevia `…/backend/`). Fuente del lado del documento: `docs-motor-reglas-e-integracion/reglas_v2.md` (v2, 28/09/2026).
> Las citas `archivo:línea` fueron verificadas leyendo el archivo en HEAD. Sólo se describen diferencias; no hay recomendaciones.
> Los docs de openspec no se usaron como fuente: sólo se contrastaron contra el código los riesgos operativos que declaran.

Abreviaturas de ruta: `engine/` = `…/backend/engine/`; `rules/` = `…/backend/engine/rules/`; `riego/` = `…/backend/engine/riego/`; `param/` = `…/backend/engine/parametros/`; `traza/` = `…/backend/engine/traza/`; `service/` = `…/backend/service/`; `res/` = `Desarrollo/backend/src/main/resources/`.

---

## 1. Resumen ejecutivo

- **El motor sigue siendo un orquestador genérico, pero la rama de riego ya implementa R-01…R-06.** Hay 13 reglas Java: `ManualLock`, `StaleSensor`, `CicloLecturaRiego`, `SustratoSaturado` (R-04), `DeficitCritico` (R-02), `DailyDoseLimit`, `FueraDeVentanaRiego` (R-05), `PausaTrasAplicacion` (R-06), `PosponerPorLluvia` (R-03), `RiegoPorDeficit` (R-01), `Supply`, `Shading` y `FollowUp`. De las 40 reglas con ID del documento, 4 coinciden, 10 coinciden con diferencias declaradas, 7 son una versión distinta y 19 no existen (conteo abajo).
- **Riego: R-04 y R-05 coinciden; R-01, R-02, R-03 y R-06 tienen diferencias.** R-01 no emite la alerta INFO ni registra al terminar (registra al abrir) y está sujeta a una guarda propia de "un riego por sector y ciclo de lectura". R-02 aplica siempre un tope de 1 riego cada 12 h por sector, que el doc reserva para cuando S-06 bloquea el riego. R-03 sí retira lo encolado, pero en el despacho y con un WARNING de descarte (no el INFO del doc). R-06 depende de los eventos "Insumo", y la bomba conserva el enganche "Dosificando" (ver 4.2).
- **El riego ya no se abre al decidir.** `ACTIVAR_VALVULA` encola una solicitud con volumen y duración calculados; un despacho (cada 10 s) abre de a 10 válvulas por macro-zona, en orden de numeración, y **revalida con los datos de ese momento** antes de abrir cada una (`engine/ActionExecutor.java:298-336`, `riego/DespachoRiego.java:254-445`). Con la lectura no vigente **pausa** la ronda (no la descarta) y la retoma cuando vuelve el nodo; cada solicitud vence al empezar el tercer ciclo de lectura. El ESP32 corta solo al cumplir `durationSec`; el backend no manda orden de cierre ni escucha el ACK.
- **Los umbrales viven en un catálogo único de 21 parámetros** (`param/CatalogoParametros.java:34-45`), editable por `GET/PUT /api/rules/parametros`, con restricciones cruzadas. Quedaron fuera del catálogo la fecha de siembra, los intervalos de sensado y evaluación, las latencias de seguimiento y los parámetros del cliente de pronóstico.
- **Cada evaluación deja una traza** (qué recibió cada regla, contra qué umbral, qué decidió), en memoria y por origen (telemetría o barrido). El dashboard la muestra en la sección "Motor de reglas" (pestañas Parámetros e Inspector). El Registro de Inacción en el historial se escribe **sólo cuando cambia la decisión** del sector (`engine/ActionExecutor.java:187-216`).
- **Mediasombra: modelo distinto y sin cambios de fondo.** Sigue siendo un % de apertura por sector, con plan por día de ciclo (sólo si hay `sowing-date-iso`, vacío por defecto) y una protección por UV pronosticado ≥ 7 que fija la apertura en 30 % (`rules/ShadingRule.java:90-148`). Con la etapa 1 del plan (20 %), un pico UV **abre** a 30 % (ver 4.3).
- **Nutrición y reglas por diagnóstico (N, F) siguen sin existir como tales.** Una única `SupplyRule` activa la bomba si el sector está en estado `critical` y la confianza es ≥ 85 % (`diagnostico.confianza-minima`). El comando de la bomba sigue saliendo con `parametros = {}` (`engine/ActionExecutor.java:130`). El diagnóstico sigue pudiendo ser sintético (`service/NurseryService.java:548-566`).
- **Seguridad: S-01 parcial, S-02 distinta, S-06 parcial; S-03, S-04 y S-05 no existen.** `StaleSensorRule` ahora también bloquea con la humedad de sustrato congelada y su umbral es 90 s. Tampoco existe ningún camino que cree un bloqueo manual.
- **Telemetría y evaluación siguen siendo push + barrido.** El backend nunca pide la lectura (`mqtt/MqttConfig.java:51-60`: la suscripción de telemetría; la otra, desde 70, es la de los eventos del riel y no es del motor). El motor corre con cada mensaje (para los 100 sectores de la zona) y cada 5 min por el barrido, que no riega ni toca la cola. El doc supone pedido cada 4 h.
- **El código tiene lógica que el doc no define:** guarda de ciclo de lectura, cola y revalidación al despachar, tope de R-02 sin S-06, bloqueo por humedad congelada, `DailyDoseLimitRule` (1 dosis cada 24 h), diagnóstico sintético, orquestación en ramas y los endpoints `/api/rules/*`. Ver punto 5.

**Conteo de cobertura (40 reglas con ID en el documento):**

| Estado | Cantidad | Reglas |
|---|---|---|
| Implementada | 4 | R-04, R-05, O-01, O-04 |
| Parcial | 10 | S-01, S-06, R-01, R-02, R-03, R-06, P-D1, P-D2, O-02, O-05 |
| Distinta | 7 | S-02, M-01, M-02, F-C2, F-F1, F-P1, E-01 |
| Ausente | 19 | S-03, S-04, S-05, N-01, N-02, F-C1, F-F2, F-S1, F-S2, F-S3, F-04, F-05, F-06, P-D3, P-D4, P-D5, E-02, E-03, O-03 |

Reglas del código sin equivalente en el doc: `CicloLecturaRiegoRule` y `DailyDoseLimitRule` (reglas completas); además, `FollowUpRule` y los comportamientos de ejecución (cola, despacho, revalidación). Ver punto 5.

**Riesgos operativos conocidos** (declarados en el diseño del cambio de riego y verificados en el código):

1. **Sensor de humedad trabado en un valor seco plausible.** Sin E-01 ni S-06 nada lo detecta: con la humedad fija en 40 %, R-01 vuelve a regar ~5 L en cada ciclo de lectura de la ventana (06, 10, 14 y 18 h con el ciclo de fábrica). El único freno es la guarda de ciclo (`rules/CicloLecturaRiegoRule.java:98-103`); `bloqueoRepeticion` se marca pero nadie lo lee (4.1, S-06). Con un valor fijo bajo 35 %, R-02 repite como mucho cada 12 h por sector (`rules/DeficitCriticoRule.java:84-97`). La humedad *congelada* (la sonda deja de reportar) sí se detecta (S-02).
2. **"Comando publicado" no confirma entrega.** El despacho da la válvula por abierta cuando el cliente MQTT aceptó el mensaje (`riego/DespachoRiego.java:424-442`, `engine/ComandoActuadorPublisher.java:66-70`; salida asíncrona, `mqtt/MqttConfig.java:132`). El backend no se suscribe al ACK.
3. **R-06 depende de eventos "Insumo" y la bomba conserva el enganche "Dosificando"** (4.2, R-06).
4. **R-03 sin pronóstico no pospone** (O-01, a propósito). En el primer mensaje tras arrancar no hay pronóstico cacheado y R-01 puede encolar una ronda; el pronóstico se pide al arrancar (`WeatherService.precalentar`, `engine/weather/WeatherService.java:126-156`) y el despacho vuelve a mirar la lluvia antes de abrir cada válvula (4.2, R-03). Si la API de Open-Meteo no responde (con timeouts de 3 s de conexión y 5 s de lectura) sigue sin pronóstico y se riega.

---

## 2. Cómo funciona hoy el motor

- **Disparadores.**
  - Reactivo: cada mensaje MQTT de telemetría llama a `NurseryService.updateTelemetry` (`service/NurseryService.java:456-596`, `mqtt/MqttTelemetryReceiver.java:36`). Normaliza el `timestamp` a ms (463-475), ignora una lectura anterior a la guardada (478-482), guarda la lectura en la zona, arma **una vez por zona** el contexto de riego (532-536) y el pronóstico sin esperar (539), y evalúa **los 100 sectores** con origen `TELEMETRIA` (541-590).
  - Proactivo: `NurseryWatchdog.evaluarTodos` barre los 600 sectores cada `intervaloEvaluacionMinutos` (default 5 min, `service/NurseryWatchdog.java:83-104`, `service/ConfiguracionService.java:345`). Evalúa con origen `BARRIDO`, `List.of()` como métricas y `ContextoRiego` vacío (`service/NurseryWatchdog.java:145-157`), con el pronóstico bloqueante (117). Por eso R-01 y R-02 ven humedad `null` y no riegan desde el barrido, y el barrido no toca la cola (`engine/ActionExecutor.java:112-114`).
  - Despacho: `DespachoRiego.tick` corre cada `yerbanalytics.riego.despacho-intervalo-ms` = 10 000 ms, en su propio scheduler (`riego/DespachoRiego.java:254-255`, `res/application.properties:103`).
  - No hay disparo por captura/diagnóstico: `DiagnosticoService.alta` sólo guarda el diagnóstico y actualiza campos del sector. Tampoco por cambio de franja horaria.
  - La cadencia de telemetría no la fija el backend: el firmware publica cada 30 s (`Desarrollo/embebido/comun/config.example.h:49`); el simulador toma `intervaloSensadoMinutos` de la base (`Desarrollo/simulador/server/emission.ts:39-45`).
- **Orden y ramas.** `RuleOrchestrator` ordena las reglas por `priority()` ascendente (`engine/RuleOrchestrator.java:50-52`):

  | Prioridad | Regla | Rama | Qué es |
  |---|---|---|---|
  | 0 | `ManualLockRule` | GLOBAL | S-01 |
  | 1 | `StaleSensorRule` | GLOBAL | S-02 (lectura y humedad frescas) |
  | 2 | `SustratoSaturadoRule` | RIEGO | R-04 |
  | 3 | `CicloLecturaRiegoRule` | RIEGO | Un riego por sector y ciclo; riego en curso |
  | 4 | `DeficitCriticoRule` | RIEGO | R-02 |
  | 5 | `DailyDoseLimitRule` | INSUMO | Límite de dosis en 24 h |
  | 6 | `FueraDeVentanaRiegoRule` | RIEGO | R-05 |
  | 7 | `PausaTrasAplicacionRule` | RIEGO | R-06 |
  | 8 | `PosponerPorLluviaRule` | RIEGO | R-03 |
  | 10 | `RiegoPorDeficitRule` | RIEGO | R-01 |
  | 11 | `SupplyRule` | INSUMO | Dosificación por diagnóstico |
  | 12 | `ShadingRule` | MEDIASOMBRA | M-01/M-02 (a su manera) |
  | 20 | `FollowUpRule` | SEGUIMIENTO | Seguimiento post-acción (E-01 a su manera) |

  (Prioridades en el `PRIORITY` de cada clase; rama en `branch()`, `GLOBAL` por defecto: `engine/Rule.java:45-47`.) `ABORT_ALL` corta todo; `ABORT_RIEGO`/`POSTPONE_RIEGO` saltean el resto de la rama RIEGO; `ABORT_INSUMO` el resto de INSUMO (`engine/RuleOrchestrator.java:87-136`). **MEDIASOMBRA y SEGUIMIENTO sólo se detienen con `ABORT_ALL`.** `ACTIVAR_VALVULA` no es bloqueante: R-02 no corta la rama, y las reglas siguientes (R-05, R-06, R-03, R-01) se declaran "no aplica" porque la humedad ya está bajo el umbral crítico (`rules/RiegoRuleSupport.java:44-48`). Las reglas no alcanzadas quedan registradas en la traza (`NO_ALCANZADA`, `OMITIDA_RAMA_BLOQUEADA`, `engine/RuleOrchestrator.java:91-103`). No existe la jerarquía S → supervivencia → sanidad → rutina como familias; en riego, R-04 (2) → ciclo (3) → R-02 (4) → R-05 (6) → R-06 (7) → R-03 (8) → R-01 (10). R-04 va antes que la guarda de ciclo para que un sector recién regado con humedad alta igual alerte y cancele la cola.
- **Acciones tipadas.** Las reglas devuelven `RuleAction(type, ruleName, motivo, detalle)` (`engine/RuleAction.java:16-21`). El `detalle` es `DetalleRiego(volumenL, duracionSeg, humedad, recortado)`, `DetalleAlerta(nivel, texto)` (INFO | WARNING | CRITICAL) o `CancelaRiego` (marca de cancelación de seguridad) (`engine/DetalleRiego.java:11`, `engine/DetalleAlerta.java:7`, `engine/CancelaRiego.java:17`). Tipos: `ACTIVAR_VALVULA`, `ACTIVAR_BOMBA`, `MOVER_MEDIASOMBRA`, `ABORT_*`, `POSTPONE_RIEGO`, `NOOP_INFO` y `ALERTA` (`engine/ActionType.java:13-59`). Sólo la mediasombra sigue llevando su parámetro en el texto del motivo (`[apertura=N]`, `engine/ActionExecutor.java:74,406-410`).
- **Cola y despacho en tandas.** En una evaluación de telemetría, un `ACTIVAR_VALVULA` encola o actualiza la solicitud del sector (nunca la duplica) y una de R-01 no degrada una de R-02 ya encolada (`engine/ActionExecutor.java:309-322`). Una solicitud encolada **se completa** aunque la humedad se recupere: sólo la retira una cancelación explícita de seguridad (`CancelaRiego` en un `ABORT_RIEGO`/`ABORT_ALL`: bloqueo manual y saturación; ventana cerrada y pausa por aplicación sólo para las de R-01) (`engine/ActionExecutor.java:325-335`). El sensor sin datos ya **no** la retira: `StaleSensorRule` no lleva marca de cancelación y el despacho pausa. El despacho abre hasta `riego.sectores-simultaneos` (10) válvulas por zona en orden de `numero` de sector (`riego/ColaRiego.java:140`, `riego/DespachoRiego.java:266,375-376`) y publica `valve ON` con `durationSec` (424-425). La duración sale de `t = ceil(V / caudal × 3600)`, con tope de 1200 s (`riego/CalculoRiego.java:85-96`, `mqtt/ContratoNodo.java:50`). Ejemplo con valores de fábrica: humedad 40 % → 5 L → 600 s; R-02 → 6 L → 720 s.
- **Revalidación al despachar.** Antes de abrir cada válvula el despacho lee la zona de la base y decide, en este orden (`riego/DespachoRiego.java:303-445,559-577`):
  1. **Vence** lo viejo: una solicitud vale durante el ciclo de lectura en que se pidió y el siguiente, y vence al empezar el tercero (`CicloLectura.vencida`, `riego/CicloLectura.java:75-77`: `solicitadaEn < inicioDelCicloAnterior(ahora)`). Se descarta con una alerta WARNING por zona (329-340).
  2. **Pausa** (no abre ninguna válvula de la zona y **conserva** la cola, sin alerta) si la lectura o la humedad de sustrato no están vigentes (mismo parámetro y definición que `StaleSensorRule`, `riego/FrescuraLectura.java:20-40`) o falta la humedad (354-362, 559-572). Se retoma sola cuando el nodo vuelve.
  3. **Retira todo** si la humedad actual ≥ `riego.saturacion-bloqueo` (R-04, 573-575) y toda solicitud de un sector o zona con bloqueo manual (379-383, 412-416).
  4. **Por solicitud de R-01** (`motivoDeDescarteDeR01`, 474-499): fuera de `riego.ventana-normal` (R-05, cerrada al minuto), pausa tras aplicación (R-06), lluvia prevista (R-03, con el pronóstico cacheado y sin esperar; sin dato no pospone) y "ya regó en este ciclo". Si no se puede leer el historial, esperan.
  5. **Por solicitud de R-02** (`motivoDeDescarteDeR02`, 502-509): sólo el tope de `riego.exceptuado-bloqueo` horas desde el último riego crítico. No depende de ventana, pausa, lluvia ni ciclo.
  6. Un sector que ya está regando se descarta (384-388).

  Lo que se retira deja una alerta WARNING "Riego descartado al despachar", una por zona y motivo (`riego/DespachoRiego.java:580-592`). El "último riego", el último crítico y la última aplicación salen de una consulta agrupada por zona y tick (sólo si hay algo en cola) mezclada con la memoria del despacho (520-540).
- **Cierre de la válvula por tiempo.** No hay orden de cierre. El backend da el riego por terminado a `ts + duración + 5 s` (`riego/DespachoRiego.java:104,436`); "Regando" no es un estado guardado sino ese cálculo (`riego/DespachoRiego.java:214-221`, mostrado por el snapshot en `service/NurseryService.java:171-177`). Tras un reinicio se reconstruye del historial (633-652); lo pendiente en cola se pierde y la siguiente telemetría lo decide de nuevo. La columna `sector.actuador_valve` quedó como legado: sólo la escribe `TopologiaService.java:255` al generar la topología.
- **Bomba.** `ACTIVAR_BOMBA` sigue marcando `actuadorPump = "Dosificando"`, registra el evento "Insumo" y publica `pump ON` con `parametros = {}` sólo en la transición (`engine/ActionExecutor.java:124-132`). Ningún código vuelve a poner el campo en "En espera" salvo `TopologiaService.java:256` al regenerar la topología.
- **Alertas.** `ALERTA` se persiste como evento "Alerta" con alcance macro-zona (`sectorId = "—"`) una sola vez por (zona, regla, ciclo de lectura) (`engine/ActionExecutor.java:343-371`, `service/HistorialService.java:111-129`). Las emiten R-02 (CRITICAL), R-03 (INFO) y R-04 (WARNING, desde 80 %); el despacho agrega WARNING por riegos descartados. Las alertas de la campana del dashboard siguen derivándose del estado de los sectores (`service/NurseryService.java:443-453`).
- **Registro de inacción sólo ante un cambio.** Cada `NOOP_INFO` y cada acción bloqueante se persiste como evento "Info" **sólo si la decisión del sector cambió** respecto de la última registrada para ese origen; si cambió cualquier regla se escribe el conjunto completo de ese sector. Es estado en memoria: tras un reinicio se registra una vez más (`engine/ActionExecutor.java:47-54,187-216`, `service/HistorialService.java:201-217`).
- **Catálogo de parámetros.** Una enum por familia (Seguridad 1, Riego 15, Insumo 1, Mediasombra 3, Diagnóstico 1) con clave, tipo, unidad, valor de fábrica, rango y referencia a la spec (`param/ParametrosRiego.java:23-85`, `param/ParametrosSeguridad.java:13-16`, `param/ParametrosInsumo.java:13-16`, `param/ParametrosMediasombra.java:13-24`, `param/ParametrosDiagnostico.java:13-16`). El catálogo falla al construirse si hay un valor de fábrica fuera de rango o una restricción violada (`param/CatalogoParametros.java:16-26`) y valida cuatro restricciones cruzadas (crítico < umbral < objetivo; bloqueo ≤ alerta de saturación; el volumen máximo debe caber en 1200 s) (64-82). En la base (`parametro_regla`) sólo hay overrides; valor vigente = override o fábrica (`param/CatalogoParametrosService.java:135-156`). Cada regla declara sus parámetros (`Rule.parametros()`) y sólo puede leer esos (`traza/Evaluacion.java:42-58`). Edición en lote, todo o nada, auditada en el historial (`param/CatalogoParametrosService.java:179-276`, `controller/ReglasParametrosController.java:36-47`).
- **Dónde viven los umbrales ahora.**
  - Catálogo: todo lo que una regla compara, salvo lo de abajo.
  - `umbral_metrica` (bandas ideal/warn por métrica): sólo definen el estado del sector y el color; **ya no mandan sobre el riego** (`service/NurseryService.java:683-720`).
  - `configuracion_operativa` (una fila): `intervaloSensadoMinutos` (240), `intervaloEvaluacionMinutos` (5), latencia y delta de seguimiento (2 min, 5), `insumoDosisMax24hMl` (15) (`service/ConfiguracionService.java:338-347`). Salieron `riegoTiempoMaxSeg`, `mediasombraAperturaMaxPct` y `riegoVolMaxDiarioMl`. `insumoDosisMax24hMl` ya no decide nada ni lo lee el motor (el texto de `DailyDoseLimitRule` muestra `insumo.max-dosis-24h`, `rules/DailyDoseLimitRule.java:94-96`); el límite real es `insumo.max-dosis-24h` (1 dosis).
  - Properties: `yerbanalytics.nursery.sowing-date-iso` (vacío, `rules/ShadingRule.java:52-59`), `yerbanalytics.riego.despacho-intervalo-ms`, `yerbanalytics.weather.*` (`res/application.properties:103,108,116-130`).
  - Sin uso en Java: `yerbanalytics.engine.action-cooldown-minutes=30` (`res/application.properties:91`) y `yerbanalytics.engine.watchdog-interval-ms=300000` (98). No hay ninguna referencia en `src/main/java`.
- **Traza de evaluación.** Cada regla evalúa a través de una `Evaluacion` que registra cada comparación (recibido, operador, umbral, resultado `CUMPLE | NO_CUMPLE | SIN_DATO`) (`traza/Evaluacion.java:58-97`, `traza/ResultadoComparacion.java`). `TrazaEvaluacionStore` guarda la última por sector y origen, en memoria (`traza/TrazaEvaluacionStore.java:17-75`), expuesta en `GET /api/rules/evaluaciones/{sectorId}` (`controller/ReglasEvaluacionesController.java:35-45`). El dashboard la muestra en la sección "Motor de reglas" (`Desarrollo/frontend/src/components/layout/Sidebar.tsx:27`, ruta `/reglas`, `router.tsx:25`), con dos pestañas: Parámetros (edición del catálogo) e Inspector (el DAG coloreado con la última evaluación) (`Desarrollo/frontend/src/features/reglas/ReglasPage.tsx:3-4`).
- **Reloj y zona horaria.** El motor toma "la hora" de un único `Clock` con zona `America/Argentina/Buenos_Aires` (`config/RelojConfig.java:16-19`, `config/ZonaHorariaVivero.java:12`); las ventanas, el ciclo de lectura y el día del plan de rustificación se evalúan en esa zona (`rules/RiegoRuleSupport.java:63-69`, `riego/CicloLectura.java:46-55`, `rules/ShadingRule.java:120`). La consulta de pronóstico envía `timezone` (`engine/weather/OpenMeteoWeatherClient.java:129`). `DailyDoseLimitRule` ya cuenta "hace 24 h" con el reloj del contexto (`ctx.now()`, `rules/DailyDoseLimitRule.java:86`).
- **Pronóstico.** Open-Meteo, horario (probabilidad, milímetros, UV, temperatura, humedad, código WMO), 2 días, 25 marcas desde la hora actual (`engine/weather/OpenMeteoWeatherClient.java:123-133,186-193`). Caché de 15 min; un fallo se recuerda 60 s; la telemetría usa `getForecastSinEspera` (nunca bloquea; usa un pronóstico vencido hasta 4 TTL y refresca aparte), el barrido y el snapshot usan `getForecast` con 3 reintentos y espera 1/2/4 s (`engine/weather/WeatherService.java:126-156,220-234`, `res/application.properties:120-130`). Se pide también **al arrancar** (`precalentar`, en el hilo de refresco) y el cliente HTTP tiene timeouts de 3 s de conexión y 5 s de lectura (`yerbanalytics.weather.connect-timeout-ms`/`read-timeout-ms`, `res/application.properties:134-135`); un refresco colgado más de 60 s se reemplaza (`WeatherService.REFRESCO_MAX`).

---

## 3. Tabla de cobertura regla por regla

Estados: **Implementada** (mismo comportamiento), **Parcial** (coincide con diferencias declaradas), **Distinta** (existe algo equivalente con otra lógica o valores), **Ausente**.

| ID | Nombre corto | Estado | Clase Java | Diferencia en una línea |
|---|---|---|---|---|
| S-01 | Bloqueo manual | Parcial | `ManualLockRule` | `ABORT_ALL` con cancelación de la cola y descarte en el despacho (`rules/ManualLockRule.java:59-67`, `riego/DespachoRiego.java:379-383`), pero nada crea ni reanuda bloqueos, no se cierran actuadores abiertos ni se emite INFO |
| S-02 | Nodo sin respuesta | Distinta | `StaleSensorRule` | `ABORT_RIEGO` si la lectura o la humedad tienen más de 90 s (`rules/StaleSensorRule.java:64-93`) y el despacho **pausa** la ronda hasta que vuelva el nodo; sin pedido, reintentos, escalado 1/3-2/3-3/3 ni estado seguro (malla, bombas) |
| S-03 | Lectura inválida | Ausente | — | No hay validación de rango físico ni reintento (`service/NurseryService.java:488-507`) |
| S-04 | Hardware incompleto | Ausente | — | Ninguna regla consulta el mapeo de actuadores (`engine/RuleContext.java:20-49`) |
| S-05 | Falla reportada por ESP32 | Ausente | — | El backend no suscribe el tópico de errores ni el de ACK de los actuadores (`mqtt/MqttConfig.java:51-60`; la otra suscripción, desde 70, es la de los eventos del riel) |
| S-06 | Acción sin efectividad | Parcial | `HistorialService` (no es regla) | Se marca `bloqueoRepeticion` (`service/HistorialService.java:290`) pero ninguna regla lo lee; el tope de 12 h de R-02 sí se aplica, siempre |
| R-01 | Riego por déficit | Parcial | `RiegoPorDeficitRule` | Condición y fórmula coinciden; sin alerta INFO, registra al abrir (no al terminar) y suma la guarda de un riego por sector y ciclo (4.2) |
| R-02 | Déficit crítico | Parcial | `DeficitCriticoRule` | Coincide (35 %, 6 L, cualquier hora, aunque llueva, ignora R-06, CRITICAL); el tope de 12 h se aplica siempre, no sólo bajo S-06, y un riego en curso lo frena |
| R-03 | Posponer por lluvia | Parcial | `PosponerPorLluviaRule` | Coincide (70 %, 5 mm, 4 h, INFO); el despacho retira lo encolado si la lluvia aparece después, pero con un WARNING de descarte, no el INFO del doc |
| R-04 | Sustrato saturado | Implementada | `SustratoSaturadoRule` | ≥ 75 % bloquea el riego de la zona; WARNING desde 80 % con el texto del doc |
| R-05 | Riego fuera de ventana | Implementada | `FueraDeVentanaRiegoRule` | Fuera de 06:00-18:00 (cerrada al minuto) sólo riega R-02; el despacho lo revalida |
| R-06 | Pausa post-aplicación | Parcial | `PausaTrasAplicacionRule` | Coincide la regla (6 h, no afecta a R-02; el despacho la revalida); la "aplicación" es el último evento "Insumo", y el enganche "Dosificando" impide registrar más de uno por sector |
| M-01 | Horario base | Distinta | `ShadingRule` | Plan por día de ciclo en % de apertura (no horario abierta/cerrada); sin perfil de crecimiento ni cierre nocturno; requiere fecha de siembra |
| M-02 | Protección por calor | Distinta | `ShadingRule` | Sólo UV pronosticado ≥ 7 → apertura 30 %; sin temperatura/luz, sin ventana 11-16, sin retención hasta las 16:00, sin alertas |
| N-01 | Plan de nutrición | Ausente | — | No hay aplicación periódica de fertilizante |
| N-02 | CE baja | Ausente | — | Ninguna regla usa la CE (ni media de 24 h) |
| F-C1 | Clorosis no nutricional | Ausente | — | No se evalúan causas (humedad, CE, pH) ni se bloquea fertilizante |
| F-C2 | Clorosis nutricional | Distinta | `SupplyRule` | Sin distinción por clase: cualquier sector `critical` con confianza ≥ 85 % dosifica |
| F-F1 | Daño fúngico | Distinta | `SupplyRule` | Idem F-C2; sin severidad/ambiente/3 capturas |
| F-F2 | Condición predisponente | Ausente | — | No hay DPV ni alerta sin diagnóstico |
| F-P1 | Plaga foliar | Distinta | `SupplyRule` | Idem F-C2; sin clima de ácaros ni 3 capturas |
| F-S1 | Estrés solar (sector) | Ausente | — | No hay riego de reposición ligado al diagnóstico |
| F-S2 | Estrés solar extendido (MZ) | Ausente | — | No hay cierre de malla por 3 días por % de sectores |
| F-S3 | Estrés solar sin radiación | Ausente | — | Sin alerta de "síntoma no explicado por radiación" |
| F-04 | Foco | Ausente | — | No hay tratamiento de sectores lindantes |
| F-05 | Sano tras tratamiento | Ausente | — | El seguimiento se cierra por delta de humedad, no por diagnóstico Sano |
| F-06 | Tope de aplicaciones | Ausente | — | El único tope es 1 dosis/24 h; no hay 3 en 30 días |
| P-D1 | Diagnóstico vigente | Parcial | `SupplyRule` | Sólo confianza ≥ 85 % (`diagnostico.confianza-minima`; doc: 70 %); sin antigüedad ≤ 48 h ni "última captura" |
| P-D2 | Intervalo mínimo | Parcial | `DailyDoseLimitRule` | Bloquea con ≥ 1 "Insumo" en 24 h (`insumo.max-dosis-24h`); sin intervalo 5/7 días ni prioridad fitosanitario |
| P-D3 | Ventana y temperatura | Ausente | — | Sin ventanas 07-10 / 17-19 ni tope 30 °C |
| P-D4 | Sin lluvia ≥ 5 mm/6 h | Ausente | — | `SupplyRule` no consulta el pronóstico |
| P-D5 | Humedad ≥ 45 % | Ausente | — | `SupplyRule` no mira la humedad de sustrato |
| E-01 | Efectividad del riego | Distinta | `FollowUpRule` + `HistorialService.evaluarSeguimiento` | `\|Δ humedad\| ≥ 5` a los 2 min (doc: ≥ +8 a los 30 min con lectura de verificación); no alimenta ninguna regla |
| E-02 | Efectividad fertilizante | Ausente | — | Los eventos "Insumo" se evalúan con humedad de sustrato, no con diagnóstico |
| E-03 | Efectividad fitosanitario | Ausente | — | Ídem; sin vecinos ni severidad |
| O-01 | Riego sin pronóstico | Implementada | `PosponerPorLluviaRule` | Sin pronóstico o con datos incompletos, R-03 queda `SIN_DATO`, no pospone y R-01 riega (`rules/PosponerPorLluviaRule.java:91-94`) |
| O-02 | Mediasombra sin pronóstico | Parcial | `ShadingRule` | Sin pronóstico se saltea la protección UV y sigue el plan; M-02 por lectura no existe |
| O-03 | Aplicaciones sin pronóstico | Ausente | — | Al no existir P-D3/P-D4 no hay nada que degradar |
| O-04 | Plan sin internet | Implementada | `ShadingRule` | El plan depende sólo del reloj local y la fecha de siembra (`rules/ShadingRule.java:113-120`); es el plan del código, no el de §7 |
| O-05 | Sincronización | Parcial | firmware + `NurseryService` | El nodo guarda hasta 50 lecturas con su timestamp y las sube en orden (`Desarrollo/embebido/sensado/buffer_offline.h:2-5`); el backend las normaliza e ignora las anteriores a la última guardada (`service/NurseryService.java:463-482`). Sólo telemetría, sin deduplicar timestamps iguales; sin probar con hardware |
| §7 (no tiene ID) | Plan de rustificación | Distinta | `ShadingRule` + `rustificacion_etapa` | Ver punto 4.3 |

---

## 4. Diferencias detalladas

### 4.1 Seguridad (S)

**S-01 Bloqueo manual**
- Doc: bloqueo por Operario sobre sector o MZ; "riego cerrado y bombas detenidas"; con bloqueo de MZ la malla queda quieta; alerta INFO al activar y reanudar; reanudar sin acciones retroactivas (§4). El doc reconoce que el front no lo tiene (§13).
- Código: `ManualLockRule` (prioridad 0) emite `ABORT_ALL` con `CancelaRiego.TODAS` si `ctx.bloqueoManualActivo()` (`rules/ManualLockRule.java:59-67`). El flag se calcula por sector o zona (`service/NurseryService.java:611-612`, `service/NurseryWatchdog.java:124-141`). `ABORT_ALL` corta todas las ramas (la malla también queda quieta), retira la solicitud de riego encolada del sector (`engine/ActionExecutor.java:328-335`) y el despacho descarta lo pendiente de un sector o zona bloqueados, y revalida justo antes de abrir (`riego/DespachoRiego.java:268-272,379-383,412-416`).
- Diferencias:
  - Ningún código crea ni desactiva `ManualLockEntity`: `ManualLockRepository` sólo se lee (`service/NurseryService.java:611-612`, `service/NurseryWatchdog.java:124`, `riego/DespachoRiego.java:268,596-597`). No hay controller ni service de bloqueos.
  - No se envían órdenes de cierre de riego ni de parada de bombas: un riego ya abierto sigue hasta cumplir su duración.
  - No hay alerta INFO al activar/reanudar. El motivo del bloqueo se registra como evento "Info" cuando cambia la decisión del sector.

**S-02 Nodo sin respuesta**
- Doc: el backend pide la lectura; espera 60 s; reintenta 2 veces con 1 min; alertas WARNING 1/3 y 2/3 y CRITICAL 3/3; estado seguro (riego cerrado, bombas detenidas, **malla cerrada**); reintento cada 15 min; INFO al volver (§4, §11).
- Código: `StaleSensorRule` hace dos comparaciones contra `seguridad.antiguedad-max-lectura` (fábrica 90 s = 3 intervalos de publicación del nodo; rango 10-600 s, `param/ParametrosSeguridad.java:13-16`): la antigüedad de la última lectura de la zona y la antigüedad de la **humedad de sustrato** (`zona.humSusTs`, que sólo se actualiza si el payload trae `humSus`, `service/NurseryService.java:490-495`). Cualquiera de las dos vencida, o sin dato, emite `ABORT_RIEGO` **sin** marca de cancelación (`rules/StaleSensorRule.java:64-93`): corta la evaluación (el motor no pide riego sin lectura) pero no vacía la cola. La humedad se mide aparte porque, si la sonda falla, el nodo sigue publicando el resto y el backend conservaría la humedad vieja (`riego/FrescuraLectura.java:10-12`). El despacho repite la misma comprobación antes de abrir cada válvula (`riego/DespachoRiego.java:559-572`) y **pausa**: con el nodo muerto y 90 sectores en cola no se abre ninguno, la cola se conserva y se retoma cuando vuelve una lectura vigente (354-362); si el hueco dura más de dos ciclos de lectura, las solicitudes vencen (`riego/CicloLectura.java:75-77`).
- Diferencias:
  - No hay pedido de lectura ni reintentos: el backend sólo recibe (ver 6).
  - Sólo se corta la rama RIEGO. INSUMO y MEDIASOMBRA siguen evaluándose (`engine/RuleOrchestrator.java:127-130`). No se ordena cerrar la malla ni detener bombas.
  - Sin alertas `WARNING`/`CRITICAL`/`INFO` por la caída ni escalado. El despacho, al pausar, no emite alerta; sólo deja un WARNING cuando una solicitud **vence** (`riego/DespachoRiego.java:329-340,580-592`).
  - En el camino de telemetría la antigüedad se mide contra el `timestamp` normalizado del payload (`service/NurseryService.java:511`): una lectura reenviada desde el buffer del nodo, vieja, también corta el riego.
  - En la vista, una zona stale se muestra como `offline` con actuadores "Cerrada"/"En espera" (`service/NurseryService.java:134-136,149-160`). Es presentación, no una orden al hardware.
  - Existe un watchdog de hardware aparte (umbrales de 60 s / 120 s, `res/application.properties:81-83`). No se leyó su código; queda fuera del motor.

**S-03 Lectura inválida**
- Doc: rangos físicos (humedad 0-100, temperatura −10…60, pH 3-9, CE 0-10, luz 0-100), reintento inmediato hasta 2 veces, descarte de la métrica y WARNING/CRITICAL (§4).
- Código: `updateTelemetry` asigna los valores del payload sin validar rango (`service/NurseryService.java:488-507`). La única conversión es CE µS/cm → dS/m (502). `ConfiguracionService.validar` sólo valida la configuración de umbrales. Ausente.

**S-04 Hardware incompleto**
- Doc: no ejecutar si falta mapear un actuador (HU-18 CA-04) (§4).
- Código: `RuleContext` no incluye información de hardware (`engine/RuleContext.java:20-49`) y ninguna regla ni el despacho consultan `HardwareService`. Ausente.

**S-05 Falla reportada por el ESP32**
- Doc: tópico propio de errores (`SIN_CAUDAL`, `CAUDAL_EXCESIVO`, `MEDIASOMBRA_FIN_DE_CARRERA`); marca sector/malla como falla y los saca de la automatización hasta registrar la reparación (§4, §13).
- Código: `MqttConfig` crea un adaptador de entrada sobre `yerbanalytics.mqtt.telemetry-topic` (`mqtt/MqttConfig.java:51-60`) y, desde la pasada del riel, otro sobre `nursery/rail/event` (desde :70), que no es del motor. No hay consumo de errores ni del tópico `…/ack` de los actuadores (`contratoTopicAck` en `Desarrollo/embebido/comun/contrato.h`). No existen los estados "Falla hidráulica"/"Falla mecánica". Con `CAUDALIMETRO_INSTALADO 0` (de fábrica) el firmware ni siquiera verifica flujo (`Desarrollo/embebido/actuacion/act_valvula.cpp:54`). Ausente.

**S-06 Acción sin efectividad**
- Doc: la última acción "Sin efectividad" bloquea la repetición autónoma: riego → toda la MZ con excepción de R-02 (1 cada 12 h); fertilizante correctivo → sector; fitosanitario → sector; nunca mediasombra; CRITICAL; se libera con revisión humana (§4).
- Código: `HistorialService.evaluarSeguimiento` marca `bloqueoRepeticion = !efectiva` (`service/HistorialService.java:285-290`). Ninguna regla lee ese campo (búsqueda de `bloqueoRepeticion` en `src/main`: sólo la entidad y `HistorialService`). No hay revisión humana ni alerta CRITICAL. Lo que sí existe es la excepción de R-02, aplicada **siempre**: un sector no recibe un segundo riego crítico antes de `riego.exceptuado-bloqueo` (12 h; rango 6-24 h) (`rules/DeficitCriticoRule.java:84-97`). Parcial: se detecta y se marca, pero no bloquea nada (ni el riego común, ni la dosificación).

### 4.2 Riego (R)

**Fórmula de volumen / medición** (§5, §1.2.1)
- Doc: `V = (65 − humedad) × 0,2` L, máx. 6 L; tiempo = V / caudal calibrado (30 L/h); sectores de a 10; orden de numeración.
- Código: `CalculoRiego.porDeficit`: `V = min((objetivo − humedad) × litrosPorPunto, volumenMax)`, redondeado a 0,01 L; `t = ceil(V / caudal × 3600)` en `BigDecimal`; si supera 1200 s se recorta y se marca `recortado` (`riego/CalculoRiego.java:48-55,85-96`). Los cuatro números son parámetros del catálogo: `riego.humedad-objetivo` 65 (55-75), `riego.litros-por-punto` 0,2 (0,1-0,5), `riego.volumen-max-evento` 6 L (3-10), `riego.caudal-emisor` 30 L/h (5-120) (`param/ParametrosRiego.java:38-53`). El caudal es un valor configurado, no medido ni calibrado por aforo; no hay caudalímetro (`CAUDALIMETRO_INSTALADO 0`). Un volumen que redondea a 0 L no genera riego (`rules/RiegoPorDeficitRule.java:75-79`).
- Orden y cupo: la cola se despacha por número de sector, 10 válvulas a la vez por zona (`riego/ColaRiego.java:140`, `riego/DespachoRiego.java:266,375-376`). Con valores de fábrica una ronda de 100 sectores de 600 s son 10 tandas de ~605 s. El cupo se llena por número de sector, no por urgencia ni por humedad.

**R-01**
- Doc: humedad < 45 % **y** hora 06:00-18:00 **y** no R-03/R-04; riega con la fórmula; salta sectores en pausa R-06; INFO; al terminar se registra sector, duración, volumen y condición (§5).
- Código: `RiegoPorDeficitRule` aplica si `riego.umbral-critico` ≤ humedad < `riego.umbral-humedad` (35 ≤ h < 45 de fábrica; rango del umbral 35-60 %) (`rules/RiegoRuleSupport.java:44-48`). Con humedad < 35 % dice "lo cubre R-02" y no actúa (`rules/RiegoPorDeficitRule.java:65-70`). La ventana, R-03, R-04 y R-06 las aplican reglas anteriores de la misma rama (prioridades 2, 6, 7 y 8), y R-01 sólo se evalúa si ninguna cortó. Emite `ACTIVAR_VALVULA` con `DetalleRiego` (80-85); se encola y se despacha (ver 2). La humedad es la de la lectura de la zona, igual para sus 100 sectores.
- Diferencias:
  - No emite alerta INFO. El riego se registra en el historial **al abrir la válvula**, con regla, volumen, duración y humedad (`riego/DespachoRiego.java:604-615`, `service/HistorialService.java:91-103`), no al terminar.
  - Guarda propia, sin equivalente en el doc: un sector recibe a lo sumo un riego de R-01 por ciclo de lectura (ver "Guarda de ciclo").
  - Los sectores en cola esperan turno: pueden pasar ~1 h 40 min entre la decisión y la apertura de la última tanda (cálculo con valores de fábrica); el despacho revalida al abrir (ventana, pausa R-06, lluvia R-03 y "ya regó en el ciclo"; con lectura no vigente espera, y la solicitud vence a los dos ciclos).
  - La fórmula coincide; el umbral y el rango son los del doc (45 %, 35-60 %).

**R-02**
- Doc: humedad < 35 % → riego a cualquier hora, 6 L, aunque llueva y aunque haya pausa; CRITICAL "Déficit hídrico crítico" (§5). En §4 (S-06), un máximo de 1 riego cada 12 h por sector sólo como excepción cuando el riego está bloqueado.
- Código: `DeficitCriticoRule` aplica con humedad < `riego.umbral-critico` (35 %; rango 25-40) (`rules/DeficitCriticoRule.java:73-81`). Volumen = `riego.volumen-max-evento` (6 L, 720 s con 30 L/h) (99-112). Emite además `ALERTA` CRITICAL con el texto "Déficit hídrico crítico" (113-115), una vez por zona y ciclo. Corre a cualquier hora (el despacho no le exige ventana: esa condición sólo está en `motivoDeDescarteDeR01`, `riego/DespachoRiego.java:476-478`), aunque llueva (R-03 sólo aplica con 35 ≤ h < 45) y aunque haya pausa por R-06 (ídem). Una solicitud de R-02 en cola no se degrada a R-01 (`engine/ActionExecutor.java:315-318`).
- Diferencias:
  - Tope de 1 riego crítico cada `riego.exceptuado-bloqueo` (12 h) por sector, **aplicado siempre** (`rules/DeficitCriticoRule.java:84-97`); el doc lo reserva para cuando S-06 bloquea el riego. El tope corta la evaluación pero no retira lo encolado; el despacho lo revalida para las solicitudes de R-02 (`riego/DespachoRiego.java:502-509`).
  - La guarda de ciclo no frena a R-02 (`rules/CicloLecturaRiegoRule.java:95-112`), pero un riego en curso del sector sí (104-108).
  - Puede bloquearlo R-04 (saturación) y las reglas S, como en el doc; en la práctica R-04 no coincide con humedad < 35 %.

**R-03**
- Doc: condiciones de R-01 **y** probabilidad ≥ 70 % con ≥ 5 mm en las próximas 4 h → no regar en este ciclo; INFO "Riego pospuesto por pronóstico de lluvia" (§5).
- Código: `PosponerPorLluviaRule` aplica con 35 ≤ h < 45 y, por orden de prioridad, ya dentro de la ventana (R-05 corta antes). Toma la **probabilidad máxima horaria** y la **suma de milímetros** de las marcas T con `trunc(ahora) < T ≤ trunc(ahora) + horas` (`engine/weather/WeatherForecast.java:68-87`) y compara ambas con `≥` contra `riego.lluvia-probabilidad` (70 %; 50-95), `riego.lluvia-mm` (5 mm; 2-20) y `riego.lluvia-ventana` (4 h; 2-12); la condición vive en `PrecondicionesRiego.lluviaPospone` (`rules/PosponerPorLluviaRule.java:77-103`). Si se cumplen, emite `POSTPONE_RIEGO` y una `ALERTA` INFO con el texto del doc (98-102). Si falta la probabilidad o los milímetros, ambas comparaciones quedan `SIN_DATO` y no pospone (91-94).
- Diferencias:
  - **Retira lo encolado en el despacho**, no en la regla: `POSTPONE_RIEGO` no lleva marca de cancelación (`engine/ActionExecutor.java:295`), pero el despacho vuelve a evaluar la lluvia con el pronóstico cacheado antes de abrir cada solicitud de R-01 (`riego/DespachoRiego.java:487-493`). Una ronda encolada antes de que el pronóstico anunciara lluvia ya no se despacha. Lo que queda en el log es un WARNING "Riego descartado al despachar", no el INFO del doc.
  - La probabilidad "combinada" del doc se interpreta como máxima horaria y los milímetros como suma (el doc no lo precisa).
  - Sin pronóstico cacheado la regla queda sin dato y no pospone (O-01, a propósito; `engine/weather/WeatherService.java:146-156`). El pronóstico se pide al arrancar, así que la ventana en frío se reduce pero no desaparece, y por eso el despacho revalida.

**R-04**
- Doc: humedad ≥ 75 % bloquea el riego autónomo de la MZ; WARNING "Sustrato saturado, riesgo de asfixia radicular y hongos" en ≥ 80 % (§5).
- Código: `SustratoSaturadoRule` (prioridad 2, antes que la guarda de ciclo) emite `ABORT_RIEGO` con `CancelaRiego.TODAS` si humedad ≥ `riego.saturacion-bloqueo` (75 %; 65-85), y además `ALERTA` WARNING con ese texto si ≥ `riego.saturacion-alerta` (80 %) (`rules/SustratoSaturadoRule.java:40,68-95`). Corta toda la rama RIEGO (también R-02), retira la ronda encolada y el despacho revalida contra el mismo umbral (`riego/DespachoRiego.java:573-575`). Coincide.

**R-05**
- Doc: fuera de 06-18 sólo puede regar R-02; la lectura de las 18:00 todavía entra (§5).
- Código: `FueraDeVentanaRiegoRule` emite `ABORT_RIEGO` con `CancelaRiego.SOLO_DEFICIT_COMUN` si se cumplen las condiciones de R-01 y la hora local está fuera de `riego.ventana-normal` (06:00-18:00 de fábrica), con la ventana cerrada al minuto (18:00:59 todavía entra) (`rules/FueraDeVentanaRiegoRule.java:57-72`, `param/VentanaHoraria.java:50-52`, `traza/Evaluacion.java:86-89`). Retira las solicitudes de R-01 encoladas y el despacho lo revalida con la hora de ese momento (`riego/DespachoRiego.java:367-368,476-478`). Coincide.

**R-06**
- Doc: tras aplicar fitosanitario o fertilizante hace < 6 h, R-01 no riega ese sector; R-02 sí (§5).
- Código: `PausaTrasAplicacionRule` emite `ABORT_RIEGO` (`SOLO_DEFICIT_COMUN`) si se cumplen las condiciones de R-01 y pasaron menos de `riego.pausa-tras-aplicacion` (6 h; 2-24) desde la última aplicación del sector (`rules/PausaTrasAplicacionRule.java:60-79`); R-02 no se ve afectado. Una dosificación que llega con R-01 ya encolada también la retira: el despacho revalida la pausa (`riego/DespachoRiego.java:483-486`).
- Diferencias:
  - "Aplicación" = el último evento de tipo "Insumo" del sector en las últimas 24 h (`service/NurseryService.java:643-656`), sin distinguir fertilizante de fitosanitario.
  - Ese evento lo escribe sólo `ActionExecutor` al activar la bomba, dentro de `if (!"Dosificando".equals(oldPump))` (`engine/ActionExecutor.java:114-116`). El campo `actuadorPump` queda en "Dosificando" para siempre (ver 2), así que tras la primera dosificación de un sector no se registra otra y la pausa deja de protegerlo.

**Guarda de ciclo de lectura (no está en el doc)**
- `CicloLecturaRiegoRule` (prioridad 3, después de R-04): `ABORT_RIEGO` si el sector ya regó desde el inicio del ciclo en curso (sólo R-01: con humedad < `riego.umbral-critico` no corta) o si tiene un riego en curso (corta también a R-02); sin lectura de humedad, corta (`rules/CicloLecturaRiegoRule.java:73-114`). Estos cortes no retiran lo encolado.
- El ciclo son franjas de `intervaloSensadoMinutos` (240 de fábrica, acotado a 60-360 min) ancladas a las 02:00 locales: 02, 06, 10, 14, 18 y 22 h (`riego/CicloLectura.java:35-55`). Con la regla se evalúa la humedad del sector y, al despachar, el despacho repite "ya regó en este ciclo" para R-01. El nodo real publica cada 30 s, así que "lectura" y "ciclo" no coinciden como en el doc: la guarda es lo que evita regar en cada mensaje.

**Despacho y cancelaciones** (ver 2). Qué retira de la cola una evaluación de telemetría (`engine/ActionExecutor.java:325-335`) y qué revalida el despacho (`riego/DespachoRiego.java:303-445`):

| Motivo | ¿Retira una evaluación de telemetría? | Qué hace el despacho |
|---|---|---|
| Humedad se recuperó (R-01/R-02 "no aplica") | No | Nada |
| Saturación (R-04) | Sí, toda solicitud | Retira toda solicitud (humedad ≥ bloqueo) |
| Bloqueo manual (`ManualLockRule`) | Sí, toda solicitud | Retira |
| Sensor sin datos o humedad congelada | **No** (sólo corta la evaluación) | **Pausa**: no abre, conserva la cola, sin alerta |
| Solicitud vieja | — | Retira al empezar el tercer ciclo de lectura (WARNING) |
| Ventana cerrada (R-05) | Sí, sólo las de R-01 | Retira las de R-01 (hora actual, al minuto) |
| Pausa por aplicación (R-06) | Sí, sólo las de R-01 | Retira las de R-01 |
| Lluvia (R-03) | No | Retira las de R-01 (pronóstico cacheado; sin dato no pospone) |
| Guarda de ciclo / riego en curso | No | Retira las de R-01 si ya regó en el ciclo; descarta lo repetido de un sector que ya está regando |
| Tope de 12 h de R-02 | No | Retira las de R-02 |

**Publicación del comando.** `ComandoActuadorPublisher.publicar` devuelve `publicado = true` cuando el gateway aceptó el mensaje (`engine/ComandoActuadorPublisher.java:55-70`); el handler es asíncrono, QoS 1 (`mqtt/MqttConfig.java:128-135`). Si no se pudo publicar, no se registra el riego y la solicitud sigue en cola (`riego/DespachoRiego.java:426-433`). El backend no se suscribe al ACK. Mientras la válvula está abierta, el firmware bloquea la tarea de actuación (`Desarrollo/embebido/actuacion/act_valvula.cpp:70-75`): un comando de bomba o mediasombra al mismo nodo espera hasta 1200 s.

### 4.3 Mediasombra (M) y plan de rustificación (§7)

**Modelo**
- Doc: **dos estados** (abierta/cerrada), **por macro-zona**, regulada por horario; de noche siempre cerrada; gana cerrar (§1.1, §6, principio 4).
- Código: **% de apertura por sector** (`SectorEntity.actuadorShade`, entero). `MOVER_MEDIASOMBRA` publica `shade SET {targetPct}` por sector (`engine/ActionExecutor.java:134-141`). El tope es `mediasombra.apertura-maxima` (100 %; rango 10-100, `param/ParametrosMediasombra.java:21-24`). No hay "gana cerrar".
- No hay orden de cierre ante bloqueo de zona, nodo caído o falla (ver S-02, S-05).

**M-01**
- Doc: perfil de crecimiento 08-10 abierta, resto cerrado; con plan activo, horario de la etapa del día; sin alertas (§6, §7).
- Código: sólo existe el camino "plan": `cycleDay = hoy − sowingDate + 1` (con la fecha del reloj del vivero), busca la etapa que contiene ese día y mueve la apertura a `min(aperturaPct, max)` (`rules/ShadingRule.java:113-143`). Sin `sowing-date-iso` (default vacío, `res/application.properties:108`) devuelve `NOOP_INFO` y no mueve la malla (113-116). Fuera de los días de las etapas (> 30) también `NOOP_INFO` (128-131): **no hay estado final** que siga con la etapa 4. No hay perfil de crecimiento, ni evaluación por franja horaria, ni cierre nocturno.

**M-02**
- Doc: entre 11:00 y 16:00, si T ≥ 35 °C, o luz ≥ 85 % con T ≥ 32 °C, o UV pronosticado ≥ 11 con T ≥ 32 °C → malla cerrada hasta las 16:00; T = máx(lectura, pronóstico); alertas INFO/WARNING (≥ 35)/CRITICAL (≥ 38) (§6).
- Código: si el UV pronosticado ≥ `mediasombra.uv-umbral` (7; rango 1-11) fija la apertura en `min(mediasombra.apertura-proteccion-uv, max)` = 30 % (`rules/ShadingRule.java:94-110`). Sin ventana horaria, sin temperatura ni luminosidad, sin retención hasta las 16:00 (apenas el UV baje, la próxima evaluación vuelve al plan), sin alertas. El UV es el de la hora actual del pronóstico (`engine/weather/OpenMeteoWeatherClient.java:132-133`), no el máximo del día.
- **Con la etapa 1 del plan (20 % de apertura), un pico UV lleva la apertura a 30 %**: la abre. La regla compara `currentOpening != protectiveOpening` y mueve sin mirar si el plan pide menos apertura (`rules/ShadingRule.java:98-107`, etapas en `res/data.sql:672-675`).
- La temperatura pronosticada (`WeatherForecast.tempC`) se calcula pero ninguna regla la consume.

**Plan de rustificación (§7)**
- Doc: por MZ; lo inicia/cancela/resetea el Ingeniero Agrónomo; 4 etapas con horarios (45 días de fábrica; rango 20-60); sólo regula mediasombra; tras el día 45 sigue con la etapa 4; INFO al terminar/cancelar.
- Código: **un plan global** (tabla `rustificacion_etapa`, sin zona), con etapas por día→% de apertura: 1-7 → 20 %, 8-14 → 40 %, 15-21 → 70 %, 22-30 → 100 % (`res/data.sql:672-675`, 30 días). El inicio lo define una property global (`rules/ShadingRule.java:52-59`), la única excepción que conserva su `@Value` fuera del catálogo. No hay iniciar/cancelar/resetear, ni rol, ni por MZ, ni alertas de fin. La edición del plan se hace por la configuración agronómica (`service/ConfiguracionService.java`, validación de solapamiento y rango). Una etapa define un % de apertura, no franjas horarias.

### 4.4 Nutrición (N)

- **N-01** (plan cada 7 días por MZ, 100 mL, próxima ventana apta): no existe. No hay registro del "último plan", ni intervalo por MZ, ni dosis en mL.
- **N-02** (CE media 24 h < 0,5 dS/m y sin fertilización en 5 días → adelantar N-01): no existe. La CE es una métrica que afecta estado (`afectaEstado = true`, `constant/NurseryConstants.java:64`) pero no hay regla que la use. No hay media de 24 h ni historial de lecturas.

### 4.5 Reglas por diagnóstico (F) — 🔶 Sin revisar en el doc (§8.5)

La sección F del documento está marcada **Sin revisar**; todas las diferencias de esta sección caen ahí.

- **Qué hace el código.** `SupplyRule` (prioridad 11, rama INSUMO): si el estado del sector no es `critical` → `NOOP_INFO`; si la confianza es nula → `NOOP_INFO`; si `conf ≥ diagnostico.confianza-minima` (85 %; rango 50-100) → `ACTIVAR_BOMBA`; si no → `NOOP_INFO` (`rules/SupplyRule.java:65-95`). No lee la clase del diagnóstico (sólo la cita en el texto), ni la severidad, ni la antigüedad.
- **El "estado crítico" viene de sensores, no de la IA.** `finalStatus` es `critical` si cualquier métrica con `afectaEstado` está `critical` (`service/NurseryService.java:517-530`). Por las bandas actuales esto incluye humedad de sustrato, humedad ambiental, temperatura, **luminosidad** (`uv` crítica si < 20 % o > 85 %) y CE (`constant/NurseryConstants.java:58-64`). Una lectura de luminosidad baja pasa el sector a `critical` y, con el diagnóstico sintético, puede activar la bomba. Es una consecuencia de las bandas y la lógica leídas; no se ejecutó.
- **Diagnóstico sintético** (`service/NurseryService.java:548-566`): `ok` → "Sano" 98 %; `warning` → "Clorosis"/Media; `critical` → "Daño fúngico"/Alta con confianza 92 % (`constant/NurseryConstants.java:80-90`). Si el sector ya estaba en `warning`/`critical` con un diagnóstico real (cargado por `POST /api/diagnosticos`), se conserva (555-556). Si el estado vuelve a `ok`, se pisa con "Sano".
- **Falta por regla:**
  - F-C1: sin chequeo de asfixia/salinidad/pH ni WARNING con causa.
  - F-C2: la severidad "moderada o alta" en rustificación no se evalúa.
  - F-F1: sin condiciones de severidad, ambiente húmedo (humedad > 80 %, DPV) ni 3 capturas seguidas.
  - F-F2: no hay DPV. No se calcula ni se guarda (búsqueda de `dpv|vapor` en `src/main/java`: sin resultados).
  - F-P1: sin clima de ácaros ni conteo de capturas.
  - F-S1/F-S2/F-S3: no hay riego de reposición, ni cierre de MZ por 3 días, ni alerta de síntoma no explicado. El motor no consulta historial de lecturas (ver punto 7).
  - F-04: no hay vecindad ni anillo de 8 sectores.
  - F-05: ver E.
  - F-06: no hay conteo de fitosanitarios por 30 días; sólo `DailyDoseLimitRule` (1 dosis/24 h).
- **Alertas y trazabilidad.** El Registro de Inacción dejó de escribir una fila por regla, sector y evaluación: sólo escribe cuando cambia la decisión del sector (ver 2). Las alertas de riego sí son una por zona y ciclo. Para las reglas de insumo no hay alertas.

### 4.6 Precondiciones de dosificación (P-D) y ejecución (§8.2)

- **Dosis y bomba.** Doc: un insumo por tanque; dosis 100 mL por sector; `t = dosis / caudal`; la orden lleva duración y el ESP32 corta solo; stock descontado y alertas al 20 % / tanque vacío (§8.1-8.2, §11).
- Código: una sola acción `ACTIVAR_BOMBA` sin tipo de insumo ni dosis; el comando es `pump ON` con `parametros` vacíos (`engine/ActionExecutor.java:130`). El firmware espera `ml` y rechaza la orden sin él (`Desarrollo/embebido/actuacion/act_bomba.cpp:24-27`, `Desarrollo/embebido/comun/util_json.cpp:77`). No hay stock/tanques, ni reingreso restringido de 24 h, ni marca de "aplicado" más allá del evento de historial "Insumo" (y su enganche, ver R-06).
- La dosis máxima por 24 h (`insumoDosisMax24hMl`, default 15) **ya no la lee el motor**: la regla cuenta eventos contra `insumo.max-dosis-24h` (1) y su texto muestra ese valor (`rules/DailyDoseLimitRule.java:90-96`).
- **P-D1** vigente: parcial. Sólo confianza ≥ `diagnostico.confianza-minima` (85 %; el doc: 70 % configurable 50-95, última captura, ≤ 48 h) (`rules/SupplyRule.java:76-77`). El mismo parámetro lo usa `DiagnosticoService.esConcluyente` (`service/DiagnosticoService.java:134`), que sólo alimenta la presentación (`service/NurseryService.java:772`), no el motor.
- **P-D2** intervalo mínimo: `DailyDoseLimitRule` bloquea con `ABORT_INSUMO` si hay ≥ `insumo.max-dosis-24h` (1) eventos "Insumo" en 24 h para el sector (`rules/DailyDoseLimitRule.java:86-98`). Cubre "no más de una aplicación por día" pero no el intervalo de 5 días (fertilizante) / 7 días (fitosanitario) ni el orden fitosanitario → fertilizante.
- **P-D3** ventana y temperatura ≤ 30 °C: no existe.
- **P-D4** lluvia ≥ 5 mm en 6 h: no existe (`SupplyRule` no usa el pronóstico).
- **P-D5** humedad ≥ 45 % o regado después de la última lectura: no existe.

### 4.7 Efectividad (E)

- Mecanismo del código: cada riego o dosificación registra un evento con `valorAntes` = humedad de sustrato (la que motivó el riego, para el riego; la actual de la zona, para el insumo), latencia y umbral de la configuración operativa (`service/HistorialService.java:91-103,159-186`). `evaluarSeguimiento` corre por `@Scheduled` cada 30 s (265-267) y además `FollowUpRule` lo invoca en cada evaluación de cada sector (`rules/FollowUpRule.java:70-76`). Pasada la latencia compara `ahora − antes`; el veredicto es "Efectiva" si `abs(delta) >= umbral` (`service/HistorialService.java:272-290`). Un riego se registra al abrir la válvula, así que la latencia corre desde la apertura.
- **E-01.** Doc: lectura de verificación a los 30 min (rango 15-60), humedad de la MZ, delta ≥ +8; alcance MZ. Código: latencia 2 min (`seguimientoLatenciaMin`), delta mínimo 5, valor **absoluto** (una baja de 5 puntos también cuenta como efectiva), sin pedido de lectura de verificación (usa la última lectura que haya). Como la humedad es la de la zona (un nodo testigo para 100 sectores), el veredicto de cada sector de la ronda compara contra esa misma lectura.
- **E-02, E-03.** Doc: evalúan diagnóstico (14 días / 7 días). Código: los eventos "Insumo" se evalúan con `metricKey = "humSus"`, es decir, contra la humedad de sustrato. Ausentes como regla.
- **F-05** (cierre "Efectiva" cuando el sector vuelve a Sano): no existe; el veredicto no mira el diagnóstico.
- La salida de la evaluación no alimenta ninguna regla (ver S-06).

### 4.8 Operación sin internet (O)

- **O-01.** Doc: sin pronóstico, asumir que no llueve y R-01 riega. Código: sin pronóstico (o con probabilidad o milímetros sin dato) R-03 deja las comparaciones `SIN_DATO`, emite `NOOP_INFO` "se asume que no llueve y el riego sigue su curso" y R-01 decide (`rules/PosponerPorLluviaRule.java:79-94`). La telemetría pide el pronóstico sin esperar y no se bloquea sin internet (`engine/weather/WeatherService.java:146-156`); el cliente tiene timeouts, así que una API colgada no traba el hilo. Implementada.
- **O-02.** Doc: M-02 usa sólo la lectura. Código: sin pronóstico la comparación de UV queda `SIN_DATO` y se sigue con el plan (`rules/ShadingRule.java:96-97,113-116`); no existe M-02 por lectura. Parcial.
- **O-03.** Sin P-D3/P-D4 no hay qué degradar. Ausente.
- **O-04.** El plan se basa en reloj local y fecha configurada (`rules/ShadingRule.java:113-120`). Implementada sobre el plan del código.
- **O-05.** Doc: al volver la conexión se suben todos los eventos con su timestamp original y sin duplicados. Código: el nodo guarda hasta 50 lecturas con su timestamp original y las drena en orden al reconectar (`Desarrollo/embebido/sensado/buffer_offline.h:2-5`, `BUFFER_OFFLINE_CAPACIDAD 50` en `comun/config.example.h:116`; al llenarse se descarta la más antigua, `buffer_offline.cpp:43-45`). El backend normaliza el `timestamp` (segundos o ms) y descarta lo anterior a la última lectura guardada (`mqtt/ContratoNodo.java:69-78`, `service/NurseryService.java:463-482`). Sólo cubre telemetría; un timestamp igual al guardado se reprocesa. Parcial; el firmware modular no se compiló ni se probó.

### 4.9 Principios del motor (§2) — cómo se reflejan

| Principio | Doc | Código |
|---|---|---|
| 1 Orden S → supervivencia → sanidad → rutina | Jerarquía por familias | Orden por número de prioridad y ramas independientes; no hay familias. Dentro de riego: S (0, 1) → R-04 (2) → ciclo (3) → R-02 (4) → R-05 (6) → R-06 (7) → R-03 (8) → R-01 (10) (`engine/RuleOrchestrator.java:50-52`) |
| 2 Decide con última lectura/captura válida | Sólo con lectura válida; diagnóstico vigente | Riego: sólo con lectura y humedad vigentes (`rules/StaleSensorRule.java:64-93`; el despacho pausa si dejan de estarlo). Sin validación de rango (S-03); sin vigencia de diagnóstico; en el barrido `metrics` está vacío |
| 3 El clima sólo agrega protección | Con déficit crítico se riega | Cumple en riego: R-03 sólo aplica con 35 ≤ h < 45 y R-02 riega aunque llueva. En mediasombra, el UV puede abrir (ver 4.3) |
| 4 En mediasombra gana cerrar | Cerrar | No existe |
| 5 Dosis nunca pasa la etiqueta | — | No hay dosis en el motor |
| 6 Corte por tiempo calculado; el ESP32 corta solo | Orden con duración | Riego: `durationSec` calculado de volumen ÷ caudal en el comando; el firmware corta al cumplirla (`riego/DespachoRiego.java:424-425`, `Desarrollo/embebido/actuacion/act_valvula.cpp:70-75`). Bomba: sin duración ni mL |
| 7 Cuándo evalúa | Por lectura (4 h), captura, cambio de franja | Por mensaje de telemetría y cada 5 min; el ciclo de lectura regula el riego; nunca por captura ni franja |
| 8 Una alerta por evento | Una vez al activarse | Las alertas de riego se persisten una vez por (zona, regla, ciclo de lectura) (`engine/ActionExecutor.java:343-355`): R-02 CRITICAL vuelve a salir en cada ciclo mientras siga seco. El Registro de Inacción sólo escribe ante un cambio |
| 9 Horario local fijo | America/Argentina/Buenos_Aires | `ZonaHorariaVivero.ZONA` (`config/ZonaHorariaVivero.java:12`) |

---

## 5. Lo que está en el código y NO está en el doc

1. **`CicloLecturaRiegoRule` y la noción de ciclo de lectura** (`rules/CicloLecturaRiegoRule.java:73-114`, `riego/CicloLectura.java:46-55`). Un riego por sector y ciclo de lectura (franjas de `intervaloSensadoMinutos` ancladas a las 02:00), más el corte por riego en curso. Reemplaza a los límites diarios que el Anexo A eliminó; el doc sólo habla de lectura cada 4 h.
2. **Cola, despacho en tandas y revalidación** (`riego/ColaRiego.java`, `riego/DespachoRiego.java:254-445`). El doc define "10 a la vez, en orden de numeración", pero no revalidar al despachar, ni pausar sin lectura vigente, ni el vencimiento de las solicitudes, ni descartar con alerta WARNING "Riego descartado al despachar".
3. **Tope de 12 h de R-02 sin S-06** (`rules/DeficitCriticoRule.java:84-97`). Es la excepción de S-06 aplicada siempre.
4. **Bloqueo por humedad congelada** (`rules/StaleSensorRule.java:71-93`, `riego/FrescuraLectura.java`): `humSusTs` propio, con el mismo umbral que la lectura. El doc no distingue la antigüedad de una métrica de la de la lectura.
5. **`DailyDoseLimitRule`**: 1 dosificación por 24 h por sector, parámetro `insumo.max-dosis-24h` (`rules/DailyDoseLimitRule.java:77-103`). Cubre parcialmente P-D2 con otra lógica (cualquier producto, no por intervalo de insumo).
6. **`insumoDosisMax24hMl` (15)** como campo de la configuración operativa que ya no decide nada ni lo lee el motor (el texto de `DailyDoseLimitRule` muestra `insumo.max-dosis-24h`, `rules/DailyDoseLimitRule.java:96`). Editarlo en la pantalla de Configuración no cambia el comportamiento.
7. **`intervaloSensadoMinutos` (240) e `intervaloEvaluacionMinutos` (5)** como configuración operativa. El primero ahora define el ciclo de lectura del riego (acotado a 60-360 min, `riego/CicloLectura.java:35-43`) y, en el simulador, el período de emisión; el nodo real publica a 30 s sin importar este valor. El segundo es el intervalo del barrido.
8. **Mediasombra como porcentaje** y plan por día de ciclo (4 etapas de 1-7/8-14/15-21/22-30) en lugar de horarios (ver 4.3).
9. **Diagnóstico sintético por estado de sensores** (`service/NurseryService.java:548-566`) como entrada del motor.
10. **Orquestación en ramas (DAG)** con `ABORT_*` por rama, `GET /api/rules/schema` que expone el grafo (`controller/RuleEngineSchemaController.java:57-119`, `engine/RuleBranch.java:8-23`) y la traza de evaluación (`GET /api/rules/evaluaciones/{sectorId}`). El doc no define esta estructura.
11. **Catálogo de parámetros** editable (`GET/PUT /api/rules/parametros`) con restricciones cruzadas y auditoría; el doc (§11) define valores y rangos, no el mecanismo.
12. **Registro de Inacción** como eventos "Info" cuando cambia la decisión de un sector (`engine/ActionExecutor.java:187-216`). El doc pide trazabilidad de acciones y alertas (HU-11), no un evento por cada decisión sin acción.
13. **`FollowUpRule`** como regla (prioridad 20) que delega en `HistorialService.evaluarSeguimiento()` (`rules/FollowUpRule.java:70-80`). Se ejecuta por sector en cada ciclo (100 veces por mensaje de telemetría de una zona) aunque el método es global.
14. **Property sin uso**: `yerbanalytics.engine.action-cooldown-minutes` (`res/application.properties:89-91`) y `yerbanalytics.engine.watchdog-interval-ms` (96-98); no las lee ninguna clase Java.
15. **Pronóstico**: Open-Meteo con `lat/lon` configurables (`res/application.properties:116-117`), caché de 15 min, fallo cacheado 60 s, reintentos con backoff, timeouts HTTP, pedido al arrancar y consulta sin espera desde la telemetría. El doc no define proveedor ni caché.
16. **Estado de la válvula derivado por tiempo** ("Regando", "En cola", "Cerrada") para el dashboard (`riego/DespachoRiego.java:214-221`). El estado "En cola" no existe en el doc.
17. **Lecturas anteriores a la última guardada se ignoran** y el `timestamp` del nodo se normaliza (`service/NurseryService.java:463-482`, `mqtt/ContratoNodo.java:69-78`).
18. **Enganche "Dosificando"** del campo de la bomba: tras la primera dosificación no se publican nuevos comandos de bomba ni se registran nuevos eventos "Insumo" para el sector (`engine/ActionExecutor.java:126-131`). Es comportamiento de la implementación, no del doc.

Resueltos respecto de la versión anterior de este documento: el pronóstico ya no se consulta sin `timezone` ni con la zona del JVM (usa la del vivero, `engine/weather/OpenMeteoWeatherClient.java:129`); `DailyVolumeLimitRule`, la guarda de 1 riego por 24 h dentro de `IrrigationRule` y el enganche "Regando" ya no existen; `riegoVolMaxDiarioMl` salió de la entidad, el DTO y la pantalla de Configuración; `riegoTiempoMaxSeg` y `mediasombraAperturaMaxPct` pasaron al catálogo (esta última como `mediasombra.apertura-maxima`).

---

## 6. Diferencias de supuestos y parámetros (§1 y §11 vs. valores reales)

### 6.1 Estructura, hardware y comunicación (§1)

| Parámetro | Doc | Código |
|---|---|---|
| Macro-zonas | 10 MZ × 100 sectores = 100 000 plantines (§1.1) | 6 MZ × 100 sectores = 600 sectores (`constant/NurseryConstants.java:71-78`) |
| Sector | 4 bandejas de 25 celdas, 100 plantines, ~0,3 m² | No se modela la geometría en el motor |
| Mediasombra | Dos estados, por MZ | % de apertura por sector (`SectorEntity.actuadorShade`) |
| Riego en campo | Electroválvula + microaspersor por sector; prototipo: bomba | Comando `valve ON` con `durationSec` por sector (`riego/DespachoRiego.java:424-425`) |
| Caudal del emisor | 30 L/h; caudal calibrado por aforo | `riego.caudal-emisor` = 30 L/h, valor configurable (`param/ParametrosRiego.java:50-53`); no hay aforo ni calibración |
| Caudalímetro | Versión final; S-05 | No existe; `CAUDALIMETRO_INSTALADO 0` de fábrica en el firmware |
| Dosificación | Dos tanques (fertilizante, fitosanitario) con bomba propia | Una sola acción `ACTIVAR_BOMBA` por sector, sin tipo de insumo |
| Sectores regando a la vez por MZ | 10 | 10 (`riego.sectores-simultaneos`, rango 1-100) |
| Telemetría | El backend pide la lectura cada 4 h (02, 06, 10, 14, 18, 22 h) | El nodo publica cada 30 s; el backend sólo consume la telemetría (`mqtt/MqttConfig.java:51-60`) |
| Comandos | La orden de riego/bomba lleva su duración | Riego: sí. Bomba: `parametros` vacío. Mediasombra: `targetPct` |
| Errores del ESP32 | Tópico propio | No se suscribe |
| Batería | Sin regla de batería | No hay regla de motor; `hardware.bateria-min-pct = 20` en properties (`res/application.properties:79`) |
| Luminosidad | % LDR; ≥ 85 % = sol directo | % LDR; sin uso de 85 % en reglas |
| Humedad de sustrato | 80 % cerca de saturación | Banda crítica > 80 (`constant/NurseryConstants.java:58`); R-04 bloquea desde 75 y alerta desde 80 |
| Captura/diagnóstico | Una pasada diaria a las 09:00 | Sin planificador horario. La orden de captura se emite por endpoint (`controller/CapturaController.java:70`) o con una **pasada del riel a demanda** (`POST /api/pasadas`, `controller/PasadaRielController.java:32`: mueve el riel, fotografía dos sectores y vuelve a home); el motor no la dispara |
| Zona horaria | America/Argentina/Buenos_Aires | Igual (`config/ZonaHorariaVivero.java:12`) |

### 6.2 Telemetría y diagnóstico (§11)

| Parámetro | Doc | Código |
|---|---|---|
| Intervalo de lectura | 4 h (1-6 h) | `intervaloSensadoMinutos` = 240; define el ciclo de lectura (acotado a 60-360 min) pero no se lo pide al nodo |
| Espera de respuesta del nodo | 60 s | No aplica; antigüedad máxima de la lectura = 90 s (`seguridad.antiguedad-max-lectura`, 10-600 s) |
| Reintentos (sin respuesta/inválida) | 2, cada 1 min | No existen |
| Reintento con nodo fuera de servicio | 15 min | No existe |
| Lectura de verificación post-riego | 30 min | No existe; seguimiento a 2 min (`configuracion_operativa`) |
| Hora de la pasada de captura | 09:00 | No hay hora fija: la pasada se dispara a demanda (Demo Expo o `POST /api/pasadas`) |
| Antigüedad máxima de la captura | 48 h | No se aplica |
| Confianza mínima del diagnóstico | 70 % (50-95) | 85 % (50-100), `diagnostico.confianza-minima` (`param/ParametrosDiagnostico.java:13-16`) |

### 6.3 Riego (§11)

| Parámetro | Doc | Código (fábrica; rango) |
|---|---|---|
| Umbral de riego | 45 % (35-60) | `riego.umbral-humedad` 45 % (35-60) |
| Humedad objetivo | 65 % (55-75) | `riego.humedad-objetivo` 65 % (55-75) |
| Umbral crítico | 35 % (25-40) | `riego.umbral-critico` 35 % (25-40) |
| Bloqueo por saturación (alerta) | 75 % (80 %); 65-85 | `riego.saturacion-bloqueo` 75 % / `riego.saturacion-alerta` 80 % (ambos 65-85) |
| Litros por punto de déficit | 0,2 L (0,1-0,5) | `riego.litros-por-punto` 0,2 (0,1-0,5) |
| Volumen máximo por evento | 6 L (3-10) | `riego.volumen-max-evento` 6 L (3-10) |
| Sectores a la vez por MZ | 10 (1-100) | `riego.sectores-simultaneos` 10 (1-100) |
| Ventana de riego normal | 06:00-18:00 | `riego.ventana-normal` 06:00-18:00 (cerrada al minuto) |
| Lluvia para posponer | 70 % / 5 mm / 4 h (50-95 / 2-20 / 2-12) | `riego.lluvia-probabilidad` 70 (50-95), `riego.lluvia-mm` 5 (2-20), `riego.lluvia-ventana` 4 h (2-12) |
| Pausa de riego tras aplicación | 6 h (2-24) | `riego.pausa-tras-aplicacion` 6 h (2-24) |
| Riego exceptuado del bloqueo (R-02) | 1 cada 12 h (6-24) | `riego.exceptuado-bloqueo` 12 h (6-24), aplicado siempre |
| Caudal del emisor | 30 L/h (§1.2) | `riego.caudal-emisor` 30 L/h (5-120); sin parámetro equivalente en la tabla de §11 |

Los valores de fábrica y rangos del doc coinciden con los del catálogo (`param/ParametrosRiego.java:23-85`). Parámetros del catálogo que el doc no lista: `riego.caudal-emisor` y el tope de duración de la válvula (1200 s, `mqtt/ContratoNodo.java:50`, no editable).

### 6.4 Mediasombra (§11)

| Parámetro | Doc | Código |
|---|---|---|
| Perfil de crecimiento | Abierta 08-10 | No existe |
| Etapas del plan | Tabla de §7 (horas) | Etapas por día con % (1-7: 20, 8-14: 40, 15-21: 70, 22-30: 100) |
| Duración del plan | 45 días (20-60) | 30 días (último `diaHasta`, `res/data.sql:675`); editable con validación sólo de coherencia/solapamiento |
| Ventana de protección M-02 | 11:00-16:00 | Sin ventana |
| Temperatura de calor extremo | 35 °C | No se usa |
| Temperatura que con sol/UV cierra la malla | 32 °C | No se usa |
| Temperatura crítica (alerta) | 38 °C | No se usa |
| Luz de sol directo | 85 % | No se usa |
| Índice UV extremo | 11 (8-13) | `mediasombra.uv-umbral` 7 (1-11); la acción es apertura 30 % (`mediasombra.apertura-proteccion-uv`, 0-100) |
| Estrés solar extendido | 10 % de sectores / 3 días | No existe |

### 6.5 Dosificación y seguimiento (§11)

| Parámetro | Doc | Código |
|---|---|---|
| Dosis de fertilizante / fitosanitario | 100 mL / 100 mL | No hay dosis en el comando; `insumoDosisMax24hMl` = 15 sólo informativo |
| Intervalo mínimo | 5 / 7 días | `insumo.max-dosis-24h` = 1 dosis en 24 h (1-10) |
| Intervalo del plan de nutrición | 7 días por MZ | No existe |
| Ventanas de aplicación | 07-10 y 17-19 | Sin ventana |
| Temperatura máxima para aplicar | 30 °C | Sin chequeo |
| Humedad mínima para aplicar | 45 % | Sin chequeo |
| Lluvia que impide aplicar | 5 mm en 6 h | Sin chequeo |
| Reingreso tras fitosanitario | 24 h | No existe |
| Sectores para declarar foco | 3 en 72 h | No existe |
| Tope de fitosanitarios | 3 en 30 días | No existe |
| Aviso de stock bajo | 20 % | No hay stock |
| Caudal de rotura / tiempo sin caudal | 150 % × 30 s / 10 s | El firmware tiene `TIMEOUT_CAUDAL_MS` = 10 000 ms (`Desarrollo/embebido/comun/config.example.h:104`) y sólo lo aplica con `CAUDALIMETRO_INSTALADO 1`; el backend no consume el error |
| Latencia / delta de efectividad | 30 min y +8 / 14 d / 7 d | 2 min y \|Δ\| ≥ 5 (valores de `configuracion_operativa`) |

### 6.6 Rangos por métrica (§3) vs. bandas reales

El código evalúa cada métrica así: `critical` si el valor sale de la banda `warn`; `warning` si sale de la banda `ideal`; `ok` en otro caso (`service/NurseryService.java:700-707`). Por eso "crítico" en el código equivale a salir de `warn`, no al "Crítico bajo/alto" del doc. Valores de `constant/NurseryConstants.java:58-68`. Estas bandas definen el estado y el color del sector; **las reglas de riego ya no las usan** (usan el catálogo).

| Métrica | Doc: óptimo / crítico | Código: ideal / warn (fuera = crítico) | ¿Afecta estado en código? |
|---|---|---|---|
| Humedad de sustrato (%) | 50-70 / < 35 y > 80 | 42-68 / 32-80 | Sí |
| Humedad ambiental (%) | 60-85 / < 35 y > 90 (2 lecturas) | 62-84 / 52-91 | Sí |
| Temperatura del aire (°C) | 18-30 / ≤ 2 y ≥ 35 | 18-27 / 15-31 | Sí |
| Luminosidad (%) | según horario / ≥ 85 con ≥ 32 °C | 35-70 / 20-85 | Sí |
| Temperatura de sustrato (°C) | 18-28 / ≤ 8 y ≥ 32 | 16-24 / 13-28 | No |
| CE (dS/m) | 0,6-1,2 / < 0,3 y > 2,0 | 1,0-1,9 / 0,8-2,5 | **Sí** (el doc la usa sólo en N-02 y F-C1) |
| pH | 5,0-6,0 / < 4,5 y > 6,5 | 5,0-6,0 / 4,5-6,5 | No |
| N (mg/kg) | 40-100 / < 20 y > 150 | 100-200 / 70-260 | No |
| P (mg/kg) | 10-30 / < 5 y > 50 | 30-60 / 20-80 | No |
| K (mg/kg) | 60-150 / < 30 y > 250 | 120-240 / 90-300 | No |

El doc dice que N, P y K "se muestran y colorean pero no disparan acciones" (coincide) y que la temperatura de sustrato es "sólo alerta" (coincide en que no mueve estado).

Observación: la banda de luminosidad hace que valores bajos (por ejemplo de noche) cuenten como `critical` y muevan el estado del sector.

---

## 7. Puntos que no se pudieron determinar

1. **Firmware modular sin compilar.** El cambio de `act_valvula.cpp` (`static_assert` del límite de 1200 s, `#error` si falta `CAUDALIMETRO_INSTALADO`, verificación de flujo condicionada) se escribió y revisó a mano y no consta que se haya compilado. `arduino-cli` ya está instalado en la PC de desarrollo (con el core `esp32:esp32` 3.3.12), pero sólo se usó para el sketch del riel; compilar el firmware modular sigue pendiente. Un `config.h` anterior (con `LIMITE_VALVULA_SEG_MAX 120` o sin el flag) debe fallar al compilar a propósito (`Desarrollo/embebido/actuacion/act_valvula.cpp:5-13`).
2. **Hardware: sólo el riel.** El nodo real (sensado, buffer offline, válvula, bomba, mediasombra) no se probó contra este backend; la cadencia real de telemetría y la actuación de la válvula por duración salen de leer el código. Lo único probado con hardware es el riel de la cámara (`vivero_esp32_red`), que no es parte del motor.
3. **Scripts SQL manuales.** No se verificó si `migracion-catalogo-parametros.sql` y `migracion-reglas-riego.sql` se aplicaron en alguna base. Hay una incoherencia a la vista: el primero compara el `ideal_min` de `humSus` contra 42 (la fábrica anterior) para decidir si copia un override de `riego.umbral-humedad` (`res/migracion-catalogo-parametros.sql:99-100`), pero la fábrica ahora es 45; una base con `ideal_min = 42` no recibe override y su umbral de riego pasa de 42 a 45.
4. **Si `data.sql` se aplica automáticamente.** `spring.sql.init.mode=never` (`res/application.properties:55`); el README del backend dice que `data.sql` siembra la base. No se verificó el mecanismo exacto. Los valores por defecto de la configuración operativa se tomaron de `ConfiguracionService.defaultOperativa` (`service/ConfiguracionService.java:338-347`); en una base ya sembrada con otros valores pueden diferir.
5. **Pruebas en ejecución (03/10/2026).** Se levantaron el backend y el dashboard (modo `http`) contra la base de desarrollo y se verificó, en ejecución real:
   - **Verificado:** arranque limpio con 13 reglas; API del catálogo (valor válido, fuera de rango, restricción cruzada, lote vacío, restablecer, auditoría); R-01 con humedad 40 % → comando de 600 s y riego de 5 L; un riego por ciclo; R-02 con 30 % → 6 L con alerta crítica, frenada por un riego en curso y por su tope de 12 h; `timestamp` del nodo en segundos y `timestamp` inutilizable; "sin señal" a los 92 s y barrido que no riega; historial que no crece con mensajes idénticos; el simulador; el frontend en modo `http` con datos reales.
   - **No verificado en ejecución:** R-04 (saturación), R-05 y R-02 fuera de ventana, y el despacho en tandas de 10 (la base tenía un solo sector, que además ya había regado).
   - **La segunda ronda de correcciones no se probó en ejecución** (`d8e3fb1`, `df9a6db`, `bbb06ef`): la pausa de la ronda con lectura no vigente, el vencimiento de solicitudes, la revalidación de R-06/R-03/ciclo/tope en el despacho, el orden R-04 → ciclo y el pronóstico al arrancar con timeouts. Sólo las cubren los tests automáticos (los del despacho usan repositorios falsos). Tampoco se contrastó el despacho (`@Scheduled(scheduler = "despachoScheduler")`) contra la base real con una ronda en cola.
6. **Plan de ejecución de los índices de `historial_evento`.** Se declaran en la entidad (`model/HistorialEventoEntity.java:15-20`); no se verificó contra el plan de ejecución real de PostgreSQL con un historial grande.
7. **Hora del pronóstico.** El cliente busca la marca de la hora actual en el reloj del vivero y envía `timezone` (`engine/weather/OpenMeteoWeatherClient.java:129,215-224`). Open-Meteo documenta que algunas variables horarias describen la hora anterior a su marca; no se verificó contra la API cómo corresponden la probabilidad, los milímetros y el UV del índice usado.
8. **Código del watchdog de hardware** (`HardwareService`, umbrales de `res/application.properties:79-83`) no leído; podría cubrir parte de S-02/S-04 fuera del motor de reglas.
9. **Valores de `LIMITE_BOMBA_ML_MAX` y el manejo de caudal del firmware** no revisados más allá de `config.example.h` (parámetros S-05 "los aplica el ESP32").
10. **Historial de lecturas.** No se encontró ninguna estructura que guarde lecturas pasadas de las zonas (la zona guarda sólo el último valor, `service/NurseryService.java:490-511`). Reglas del doc que necesitan ventanas (N-02 24 h, F-C1 48 h, F-F1/F-F2 DPV, F-P1 72 h, F-S1 48 h) no tendrían de dónde leer; no se verificó la existencia de otra tabla de lecturas fuera de lo recorrido.
