# Diferencias: motor de reglas implementado vs. `reglas_v2.md`

> Fuente de verdad del lado del código: `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/` (se abrevia `…/backend/`). Fuente del lado del documento: `/home/santiago/Descargas/reglas_v2.md` (v2, 28/09/2026).
> Las citas `archivo:línea` fueron verificadas leyendo el archivo. Sólo se describen diferencias; no hay recomendaciones.
> Los docs de openspec no se usaron como fuente.

Abreviaturas de ruta: `engine/` = `…/backend/engine/`; `rules/` = `…/backend/engine/rules/`; `service/` = `…/backend/service/`; `res/` = `Desarrollo/backend/src/main/resources/`.

---

## 1. Resumen ejecutivo

- **El motor es un esqueleto genérico, no las reglas v2.** Hay 9 reglas Java (`ManualLock`, `StaleSensor`, `WeatherOverride`, `DailyVolumeLimit`, `DailyDoseLimit`, `Irrigation`, `Supply`, `Shading`, `FollowUp`). Del catálogo de 40 reglas del documento, sólo 2 se comportan igual, 5 están parciales, 9 son una versión distinta y 24 no existen (conteo en la tabla del punto 3).
- **Riego (R-01…R-06) es la familia más alejada.** `IrrigationRule` riega con humedad < 42 % (`idealMin` de `humSus`, `service/ConfiguracionService.java:300-307`, `res/data.sql:646`) y no < 45 %. Abre la válvula un tiempo fijo (`riegoTiempoMaxSeg` = 120 s, `res/data.sql:666`), no por la fórmula `(65 − humedad) × 0,2 L`. No hay ventana horaria, ni umbral crítico (R-02), ni bloqueo por saturación (R-04), ni pausa post-aplicación (R-06), ni riego de a 10 sectores.
- **La lluvia pospone incluso el déficit crítico.** `WeatherOverrideRule` (prioridad 2) emite `POSTPONE_RIEGO` si la probabilidad de la hora actual es ≥ 60 % (`rules/WeatherOverrideRule.java:41,75`). Corre antes que `IrrigationRule` y no mira la humedad. Contradice el principio 3 y R-02 del doc ("con déficit crítico se riega aunque se anuncie lluvia"). El pronóstico no trae milímetros ni ventana de 4 h (`engine/weather/OpenMeteoWeatherClient.java:80,113`).
- **Mediasombra: modelo distinto.** El código maneja un **% de apertura** (0–100) por sector. Sigue un plan por **día de ciclo** (4 etapas de 1-7/8-14/15-21/22-30 días con 20/40/70/100 %, `res/data.sql:672-675`) que **sólo corre si se configura `yerbanalytics.nursery.sowing-date-iso`**, y por defecto está vacío (`res/application.properties:109`). El doc define estados **abierta/cerrada** por macro-zona, con horarios (§6, §7) y M-02 por calor (≥ 35 °C, o luz ≥ 85 % con ≥ 32 °C). En el código la única protección es UV pronosticado ≥ 7 que baja la apertura a 30 % (`rules/ShadingRule.java:89-96`). No mira temperatura, luz del sensor ni hora.
- **Nutrición y reglas por diagnóstico (N, F) no existen como tales.** No hay plan de nutrición, N-02, F-C1/F-C2/F-F1/F-F2/F-P1/F-S1/F-S2/F-S3, foco ni tope de aplicaciones. Una única `SupplyRule` activa la bomba si el sector está en estado `critical` y la confianza del diagnóstico es ≥ 85 % (hardcodeado, `rules/SupplyRule.java:34,62,72`). No distingue la clase de diagnóstico.
- **El diagnóstico que alimenta a `SupplyRule` puede ser sintético.** En cada telemetría `NurseryService` pisa el diagnóstico del sector: "Sano" 98 % si el estado es `ok`, y si pasa a `warning`/`critical` asigna el primer ítem de `PATHOS` con confianza 92 % (`service/NurseryService.java:472-490`). Para `critical` ese ítem es "Daño fúngico", severidad Alta (`constant/NurseryConstants.java:85-86`). La dosificación puede dispararse sin que haya ninguna captura.
- **Comando de bomba incompleto.** `ActionExecutor` publica `pump ON` con `parametros = {}` (`engine/ActionExecutor.java:94`). El firmware lee `ml` (`Desarrollo/embebido/comun/util_json.cpp:77`) y responde error `dosis_invalida` si `ml ≤ 0` (`Desarrollo/embebido/actuacion/act_bomba.cpp:24-27`). El doc exige que la orden lleve la dosis y su duración (principio 6, §8.2).
- **Seguridad: sólo S-01 (parcial) y una versión reducida de S-02.** No hay lectura inválida (S-03), hardware incompleto (S-04), tópico de errores del ESP32 (S-05) ni bloqueo por falta de efectividad (S-06). Existe la bandera `bloqueoRepeticion` pero ninguna regla la lee. Tampoco hay forma de crear un bloqueo manual (no hay endpoint ni servicio que escriba `ManualLockEntity`).
- **Telemetría y evaluación son push + barrido de 5 min.** El backend nunca le pide la lectura al nodo (no hay publicador de pedidos; `MqttConfig` sólo suscribe `telemetryTopic`, `mqtt/MqttConfig.java:28,54`). El motor corre en cada mensaje de telemetría (para los 100 sectores de la zona) y además cada 5 min por `NurseryWatchdog`. El doc: pedido cada 4 h, reintentos, evaluación por lectura/captura/cambio de franja.
- **Hay lógica en el código que el doc eliminó.** `DailyVolumeLimitRule` (≥ 2 riegos/24 h) y el tope de 1 riego/24 h dentro de `IrrigationRule` son límites diarios, que el Anexo A marca como eliminados ("Límites diarios, histéresis, batería: Eliminados"). `DailyDoseLimitRule` bloquea la dosificación si hubo una en las últimas 24 h.

**Conteo de cobertura (40 reglas con ID en el documento):**

| Estado | Cantidad |
|---|---|
| Implementada | 2 (O-01, O-04) |
| Parcial | 5 (S-01, S-06, P-D1, P-D2, O-02) |
| Distinta | 9 (S-02, R-01, R-03, M-01, M-02, F-C2, F-F1, F-P1, E-01) |
| Ausente | 24 |

Reglas del código sin equivalente en el doc: `DailyVolumeLimitRule` (1 regla completa); además hay comportamientos extra dentro de reglas que sí mapean (guarda de 1 riego/24 h en `IrrigationRule`, `DailyDoseLimitRule`, `FollowUpRule`). Ver punto 5.

---

## 2. Cómo funciona hoy el motor

- **Disparadores.**
  - Reactivo: cada mensaje MQTT de telemetría de una zona llama a `NurseryService.updateTelemetry`, que guarda la lectura en la zona y evalúa **los 100 sectores** con `ruleOrchestrator.evaluate` + `actionExecutor.execute` (`service/NurseryService.java:416-512`, `mqtt/MqttTelemetryReceiver.java:36`).
  - Proactivo: `NurseryWatchdog.evaluarTodos` barre los 600 sectores cada `intervaloEvaluacionMinutos` (default 5 min, `service/NurseryWatchdog.java:83-94`, `service/ConfiguracionService.java:359`). Pasa `List.of()` como métricas (`service/NurseryWatchdog.java:146`) y `sector.getStatus()` como estado. Por eso `IrrigationRule` ve `humSus == null` y no riega desde el watchdog.
  - No hay disparo por captura/diagnóstico: `DiagnosticoService.alta` sólo guarda el diagnóstico y actualiza campos del sector (`service/DiagnosticoService.java:95-112`). No hay disparo por cambio de franja horaria.
  - La frecuencia de telemetría no la fija el backend. Depende de quien publique (firmware o simulador). No se revisó la cadencia del firmware.
- **Orden y modelo de evaluación.** `RuleOrchestrator` ordena las reglas por `priority()` ascendente: ManualLock 0, StaleSensor 1, WeatherOverride 2, DailyVolumeLimit 4, DailyDoseLimit 5, Irrigation 10, Supply 11, Shading 12, FollowUp 20 (`engine/RuleOrchestrator.java:36-39`). Cada regla pertenece a una rama (`RuleBranch`: GLOBAL, RIEGO, INSUMO, MEDIASOMBRA, SEGUIMIENTO). `ABORT_ALL` corta todo; `ABORT_RIEGO`/`POSTPONE_RIEGO` saltean el resto de la rama RIEGO; `ABORT_INSUMO` saltea el resto de INSUMO (`engine/RuleOrchestrator.java:62-98`). **MEDIASOMBRA y SEGUIMIENTO sólo se detienen con `ABORT_ALL`.** No existe la jerarquía S → supervivencia → sanidad → rutina del doc (principio 1).
- **Modelo de acciones.** Las reglas devuelven `RuleAction(type, ruleName, motivo)` (`engine/RuleAction.java:14`). Tipos: `ACTIVAR_VALVULA`, `ACTIVAR_BOMBA`, `MOVER_MEDIASOMBRA`, `ABORT_*`, `POSTPONE_RIEGO`, `NOOP_INFO` (`engine/ActionType.java:13-53`). Los parámetros viajan embebidos en el texto del motivo (`[tiempo-max-seg=N]`, `[apertura=N]`), parseados por regex (`engine/ActionExecutor.java:43-46,179-198`). `ActionExecutor` actualiza el campo del sector (`"Regando"`, `"Dosificando"`, % de mediasombra) y publica el comando MQTT `nursery/zone/{zona}/sector/{sector}/command` (`engine/ActionExecutor.java:49,148-169`).
- **Alertas y trazabilidad.** No hay alertas `INFO/WARNING/CRITICAL` generadas por reglas. Cada `NOOP_INFO` y cada acción bloqueante se persiste como evento "Info" en el historial (`engine/ActionExecutor.java:107-118`, `service/HistorialService.java:147-163`), **en cada evaluación y para cada sector**. Las alertas de la campana del dashboard se derivan del estado de los sectores (`service/NurseryService.java:402-413`).
- **Estado de actuadores.** `ActionExecutor` setea `"Regando"`/`"Dosificando"`. Ningún código del repo los vuelve a poner en `"Cerrada"`/`"En espera"` (búsqueda de `Regando|Dosificando|setActuadorValve|setActuadorPump` en `src/`: sólo `engine/ActionExecutor.java:78,90` y los valores de offline de `TopologiaService`). El comando sólo se publica en la transición (`engine/ActionExecutor.java:80,91`).
- **Origen de parámetros.**
  - Umbral de riego = `idealMin` de `humSus`. Tabla `umbral_metrica`, defaults en `constant/NurseryConstants.java:56-69` y `res/data.sql:646-663`.
  - Límites operativos en BD (`configuracion_operativa`, una fila): `riegoTiempoMaxSeg` 120, `riegoVolMaxDiarioMl` 2000, `insumoDosisMax24hMl` 15, `mediasombraAperturaMaxPct` 100, `seguimientoLatenciaMin` 2, `seguimientoDeltaMin` 5, `intervaloSensadoMinutos` 240, `intervaloEvaluacionMinutos` 5 (`res/data.sql:666`, `service/ConfiguracionService.java:349-361`).
  - Properties: `engine.rain-threshold-pct` 60, `engine.uv-threshold` 7, `nursery.stale-threshold-ms` 30000, `nursery.sowing-date-iso` vacío (`res/application.properties:69,97,100,109`).
  - Hardcodeados en reglas: confianza 85 (`rules/SupplyRule.java:34`), 2 riegos/24 h (`rules/DailyVolumeLimitRule.java:61`), 1 riego/24 h (`rules/IrrigationRule.java:98`), 1 dosis/24 h (`rules/DailyDoseLimitRule.java:80`), apertura protectora 30 % (`rules/ShadingRule.java:90`).
  - Declarados pero sin uso en Java: `yerbanalytics.engine.action-cooldown-minutes=30` (`res/application.properties:94`; no hay ninguna referencia en `src/main/java`), `intervaloSensadoMinutos` (sólo se guarda y se devuelve en el DTO).
- **Zona horaria.** Se usa `ZoneId.systemDefault()` (`rules/ShadingRule.java:110`, `engine/weather/OpenMeteoWeatherClient.java:150`), no `America/Argentina/Buenos_Aires` fija.

---

## 3. Tabla de cobertura regla por regla

Estados: **Implementada** (mismo comportamiento), **Parcial** (cubre una parte), **Distinta** (existe algo equivalente con otra lógica o valores), **Ausente**.

| ID | Nombre corto | Estado | Clase Java | Diferencia en una línea |
|---|---|---|---|---|
| S-01 | Bloqueo manual | Parcial | `ManualLockRule` | `ABORT_ALL` si hay bloqueo activo (`rules/ManualLockRule.java:58-63`), pero no hay forma de crear/reanudar bloqueos y no se detienen actuadores ni se emite INFO |
| S-02 | Nodo sin respuesta | Distinta | `StaleSensorRule` | Sólo `ABORT_RIEGO` si no hubo telemetría en 30 s; sin pedido/reintentos, sin escalado 1/3-2/3-3/3, sin estado seguro (malla cerrada, bombas) |
| S-03 | Lectura inválida | Ausente | — | No hay validación de rango físico ni reintento de lectura |
| S-04 | Hardware incompleto | Ausente | — | Ninguna regla consulta el mapeo de actuadores (`RuleContext` no lo trae) |
| S-05 | Falla reportada por ESP32 | Ausente | — | El backend no suscribe tópico de errores; no existe estado "Falla hidráulica"/"Falla mecánica en mediasombra" |
| S-06 | Acción sin efectividad | Parcial | `HistorialService` (no es regla) | Se marca `bloqueoRepeticion` al evaluar (`service/HistorialService.java:217`) pero ninguna regla lo consulta; no hay revisión humana ni excepción R-02 |
| R-01 | Riego por déficit | Distinta | `IrrigationRule` | Umbral 42 % (no 45), sin ventana 06-18, tiempo fijo 120 s (no volumen), todos los sectores a la vez, sin R-06, máx. 1 riego/24 h por sector |
| R-02 | Déficit crítico | Ausente | — | No hay umbral crítico de riego (35 %), ni 6 L, ni "aunque llueva" |
| R-03 | Posponer por lluvia | Distinta | `WeatherOverrideRule` | Prob. ≥ 60 % (no 70 %), sin mm ni ventana 4 h, sin condicionar a R-01; corta también el déficit crítico |
| R-04 | Sustrato saturado | Ausente | — | No hay bloqueo de riego por humedad alta ni alerta en 80 % |
| R-05 | Riego fuera de ventana | Ausente | — | `IrrigationRule` no mira la hora |
| R-06 | Pausa post-aplicación | Ausente | — | No hay pausa de 6 h tras `ACTIVAR_BOMBA` |
| M-01 | Horario base | Distinta | `ShadingRule` | Plan por día de ciclo en % de apertura (no horario abierta/cerrada); sin perfil de crecimiento; sin cierre nocturno; requiere fecha de siembra |
| M-02 | Protección por calor | Distinta | `ShadingRule` | Sólo UV pronosticado ≥ 7 → apertura 30 %; sin temperatura/luz, sin ventana 11-16, sin mantener hasta 16:00, sin alertas por severidad |
| N-01 | Plan de nutrición | Ausente | — | No hay aplicación periódica de fertilizante |
| N-02 | CE baja | Ausente | — | Ninguna regla usa la CE (ni media de 24 h) |
| F-C1 | Clorosis no nutricional | Ausente | — | No se evalúan causas (humedad, CE, pH) ni se bloquea fertilizante |
| F-C2 | Clorosis nutricional | Distinta | `SupplyRule` | Sin distinción por clase: cualquier sector `critical` con confianza ≥ 85 % dosifica |
| F-F1 | Daño fúngico | Distinta | `SupplyRule` | Idem F-C2; sin condición de severidad/ambiente/3 capturas |
| F-F2 | Condición predisponente | Ausente | — | No hay DPV ni alerta sin diagnóstico |
| F-P1 | Plaga foliar | Distinta | `SupplyRule` | Idem F-C2; sin clima de ácaros ni 3 capturas |
| F-S1 | Estrés solar (sector) | Ausente | — | No hay riego de reposición ligado al diagnóstico |
| F-S2 | Estrés solar extendido (MZ) | Ausente | — | No hay cierre de malla por 3 días por % de sectores |
| F-S3 | Estrés solar sin radiación | Ausente | — | Sin alerta de "síntoma no explicado por radiación" |
| F-04 | Foco | Ausente | — | No hay tratamiento de sectores lindantes (el modelo de datos no tiene vecindad usada por el motor) |
| F-05 | Sano tras tratamiento | Ausente | — | El seguimiento se cierra por delta de humedad, no por diagnóstico Sano |
| F-06 | Tope de aplicaciones | Ausente | — | El único tope es 1 dosis/24 h; no hay 3 en 30 días |
| P-D1 | Diagnóstico vigente | Parcial | `SupplyRule` | Sólo confianza ≥ 85 % (doc: 70 %, ≤ 48 h, última captura); sin antigüedad |
| P-D2 | Intervalo mínimo | Parcial | `DailyDoseLimitRule` | Bloquea con ≥ 1 "Insumo" en 24 h; no hay intervalo 5/7 días por insumo ni prioridad fitosanitario |
| P-D3 | Ventana y temperatura | Ausente | — | Sin ventanas 07-10 / 17-19 ni tope 30 °C |
| P-D4 | Sin lluvia ≥ 5 mm/6 h | Ausente | — | `SupplyRule` no consulta el pronóstico |
| P-D5 | Humedad ≥ 45 % | Ausente | — | `SupplyRule` no mira la humedad de sustrato |
| E-01 | Efectividad del riego | Distinta | `FollowUpRule` + `HistorialService.evaluarSeguimiento` | `|Δ humedad| ≥ 5` a los 2 min (doc: ≥ +8 a los 30 min con lectura de verificación); alcance sector |
| E-02 | Efectividad fertilizante | Ausente | — | Los eventos "Insumo" se evalúan con humedad de sustrato, no con diagnóstico |
| E-03 | Efectividad fitosanitario | Ausente | — | Ídem; sin vecinos ni severidad |
| O-01 | Riego sin pronóstico | Implementada | `WeatherOverrideRule` | Con `forecast == null` emite `NOOP_INFO` y el riego sigue (`rules/WeatherOverrideRule.java:69-73`) |
| O-02 | Mediasombra sin pronóstico | Parcial | `ShadingRule` | Sin pronóstico se saltea la protección UV (`rules/ShadingRule.java:89`); M-02 por lectura no existe |
| O-03 | Aplicaciones sin pronóstico | Ausente | — | Al no existir P-D3/P-D4 no hay nada que degradar |
| O-04 | Plan sin internet | Implementada | `ShadingRule` | El plan depende sólo del reloj local y la fecha de siembra (`rules/ShadingRule.java:110`); es el plan del código, no el de §7 |
| O-05 | Sincronización | Ausente | — | No hay mecanismo de sincronización/subida de eventos con timestamp original en el motor (no se revisó el firmware) |
| §7 (no tiene ID) | Plan de rustificación | Distinta | `ShadingRule` + `rustificacion_etapa` | Ver punto 4.3 |

---

## 4. Diferencias detalladas

### 4.1 Seguridad (S)

**S-01 Bloqueo manual**
- Doc: bloqueo por Operario sobre sector o MZ; "riego cerrado y bombas detenidas"; con bloqueo de MZ la malla queda quieta; alerta INFO al activar y reanudar; reanudar sin acciones retroactivas (§4). El doc reconoce que el front no lo tiene (§13).
- Código: `ManualLockRule` es la de prioridad 0 y emite `ABORT_ALL` si `ctx.bloqueoManualActivo()` (`rules/ManualLockRule.java:31,58-63`). El flag se calcula por sector o zona (`service/NurseryService.java:537-538`, `service/NurseryWatchdog.java:119-138`). Coincide el alcance sector/MZ y que la malla también queda quieta (`ABORT_ALL` corta todas las ramas).
- Diferencias:
  - Ningún código crea ni desactiva `ManualLockEntity`: `ManualLockRepository` sólo se usa para leer (`service/NurseryService.java:537-538`, `service/NurseryWatchdog.java:118`). No hay controller ni service de bloqueos.
  - El motor no envía órdenes de cierre de riego ni de parada de bombas al bloquear: sólo deja de emitir acciones nuevas.
  - No hay alerta INFO al activar/reanudar. Cada ciclo se registra un evento "Info" (`engine/ActionExecutor.java:113-118`).
  - Con bloqueo de zona el watchdog usa el sector tal cual está; no hay lógica de "reanudar sin retroactividad".

**S-02 Nodo sin respuesta**
- Doc: el backend pide la lectura; espera 60 s; reintenta 2 veces con 1 min; alertas WARNING 1/3 y 2/3 y CRITICAL 3/3; estado seguro (riego cerrado, bombas detenidas, **malla cerrada**); reintento cada 15 min; INFO al volver (§4, §11).
- Código: `StaleSensorRule` marca `sensorStale` si `lastReadingTime` es `null` o tiene más de `stale-threshold-ms` = 30 000 ms (`service/NurseryService.java:531-533`, `service/NurseryWatchdog.java:133-134`, `res/application.properties:69`; el comentario dice "30 segundos para la demo"). Emite `ABORT_RIEGO` (`rules/StaleSensorRule.java:50-56`).
- Diferencias:
  - No hay pedido de lectura ni reintentos: el backend sólo recibe (ver 6).
  - Sólo se corta la rama RIEGO. La rama INSUMO y la MEDIASOMBRA siguen evaluándose (`engine/RuleOrchestrator.java:88-91`). No se ordena cerrar la malla ni detener bombas.
  - En el camino de telemetría `sensorStale` nunca es true (la zona acaba de actualizar `lastReadingTime`, `service/NurseryService.java:444`). Sólo el watchdog lo detecta.
  - En la vista, un sector stale se muestra como `offline` con actuadores "Cerrada"/"En espera" (`service/NurseryService.java:95-121`). Es presentación, no una orden al hardware.
  - No hay alertas `WARNING`/`CRITICAL`/`INFO` ni escalado. El umbral (30 s) no corresponde al ciclo de 4 h.
  - Existe un watchdog de hardware aparte con umbrales de 60 s / 120 s para demo (`res/application.properties:84,86`). No se leyó su código; queda fuera del motor de reglas.

**S-03 Lectura inválida**
- Doc: rangos físicos (humedad 0-100, temperatura −10…60, pH 3-9, CE 0-10, luz 0-100), reintento inmediato hasta 2 veces, descarte de la métrica y WARNING/CRITICAL (§4).
- Código: `updateTelemetry` asigna los valores del payload sin validar rango (`service/NurseryService.java:426-440`). La única conversión es CE µS/cm → dS/m (línea 435). `ConfiguracionService.validar` sólo valida la configuración de umbrales (`service/ConfiguracionService.java:179-209`).
- Ausente.

**S-04 Hardware incompleto**
- Doc: no ejecutar si falta mapear un actuador (HU-18 CA-04) (§4).
- Código: `RuleContext` no incluye información de hardware (`engine/RuleContext.java:20-50`) y ninguna regla inyecta `HardwareService`. Ausente en el motor.

**S-05 Falla reportada por el ESP32**
- Doc: tópico propio de errores (`SIN_CAUDAL`, `CAUDAL_EXCESIVO`, `MEDIASOMBRA_FIN_DE_CARRERA`); marca sector/malla como falla y los saca de la automatización hasta registrar la reparación (§4, §13).
- Código: `MqttConfig` crea un único adaptador de entrada sobre `yerbanalytics.mqtt.telemetry-topic` (`mqtt/MqttConfig.java:28,54`). No hay consumo de errores ni de ack del tópico `…/ack` (definido en `Desarrollo/embebido/comun/contrato.h`, `contratoTopicAck`). No hay estados "Falla hidráulica"/"Falla mecánica". Ausente.

**S-06 Acción sin efectividad**
- Doc: la última acción "Sin efectividad" bloquea la repetición autónoma: riego → toda la MZ con excepción de R-02 (1 cada 12 h); fertilizante correctivo → sector; fitosanitario → sector; nunca mediasombra; CRITICAL; se libera con revisión humana (§4).
- Código: `HistorialService.evaluarSeguimiento` marca `bloqueoRepeticion = !efectiva` (`service/HistorialService.java:216-217`). Ninguna regla ni consulta lee ese campo (búsqueda de `bloqueoRepeticion` en `src/main`: sólo entidad y `HistorialService`). No hay revisión humana ni alerta CRITICAL. Parcial: sólo se detecta y se marca.

### 4.2 Riego (R)

**Fórmula de volumen / medición** (§5, §1.2.1)
- Doc: `V = (65 − humedad) × 0,2` L, máx. 6 L; tiempo = V / caudal calibrado (30 L/h); sectores de a 10; orden de numeración.
- Código: no hay cálculo de volumen. El tiempo máximo es `riegoTiempoMaxSeg` (120 s por defecto) pegado al motivo como `[tiempo-max-seg=N]` (`rules/IrrigationRule.java:108-111`). Si la configuración no está disponible, no se adjunta tiempo y el ejecutor usa 600 s (`engine/ActionExecutor.java:83`, `rules/IrrigationRule.java:116-118`). No hay caudal calibrado ni caudalímetro. Los 100 sectores de la zona se evalúan en el mismo ciclo y todos pueden abrir válvula a la vez (`service/NurseryService.java:465-511`).

**R-01**
- Doc: humedad < 45 % **y** hora 06:00-18:00 **y** no R-03/R-04; riega con la fórmula; salta sectores en pausa R-06; INFO (§5).
- Código: `IrrigationRule` riega si `humSus < umbral`, con `umbral = idealMin` de `humSus` = 42 % (`rules/IrrigationRule.java:84`, `service/ConfiguracionService.java:300-307`, `res/data.sql:646`). El fallback de la regla es 40 % (`rules/IrrigationRule.java:41`) y el del servicio 42 % (línea 306). No se mira la hora. Si el sector ya regó en las últimas 24 h, emite `ABORT_RIEGO` (`rules/IrrigationRule.java:94-104`), o sea **un riego por día por sector**.
- No se emite alerta INFO; sí se registra el riego en el historial en la transición (`engine/ActionExecutor.java:80-82`).
- El umbral es configurable por la fila `umbral_metrica` (con validación de bandas, `service/ConfiguracionService.java:179-209`); el doc pide rango 35-60 %.

**R-02**
- Doc: humedad < 35 % → riego a cualquier hora, 6 L, aunque llueva y aunque haya pausa; CRITICAL (§5).
- Código: no existe. El estado del sector pasa a `critical` si `humSus < 32` o `> 80` (bandas `warn`, `service/NurseryService.java:573-574`, `constant/NurseryConstants.java:58`), pero ninguna regla de riego reacciona distinto. Ausente.

**R-03**
- Doc: condiciones de R-01 **y** prob. ≥ 70 % con ≥ 5 mm en las próximas 4 h → no regar; INFO (§5).
- Código: `WeatherOverrideRule` emite `POSTPONE_RIEGO` si `probLluviaPct >= 60` (`rules/WeatherOverrideRule.java:41,75`). El valor es la probabilidad de **la hora actual** (`engine/weather/OpenMeteoWeatherClient.java:111-113`). No se pide ni evalúa precipitación en mm (`…:80`). Los slots de las próximas 4 horas existen en `forecastSlots` pero ninguna regla los usa.
- No depende de que se cumpla R-01 ni de la humedad: corre siempre (prioridad 2) y, como `POSTPONE_RIEGO` corta la rama RIEGO (`engine/RuleOrchestrator.java:88-91`), `IrrigationRule` no se evalúa. El riego queda pospuesto incluso con humedad crítica.
- El umbral es configurable por property (`yerbanalytics.engine.rain-threshold-pct`, `res/application.properties:97`), no por la configuración operativa de la BD.

**R-04**
- Doc: humedad ≥ 75 % bloquea el riego autónomo de la MZ; WARNING en ≥ 80 % (§5).
- Código: no hay regla. El valor entra sólo en el estado del sector: `humSus` es `warning` si sale de 42-68 y `critical` por encima de 80 (`service/NurseryService.java:573-577`). Como `IrrigationRule` sólo riega por debajo de 42, el efecto práctico es que no se riega con humedad alta, pero no hay bloqueo explícito (ni de las aplicaciones, ni alerta). Ausente como regla.

**R-05**
- Doc: fuera de 06-18 sólo puede regar R-02 (§5). Código: sin horario. Ausente.

**R-06**
- Doc: tras aplicar fitosanitario o fertilizante hace < 6 h, R-01 no riega ese sector (§5). Código: sin relación entre las ramas INSUMO y RIEGO más allá de que el orquestador las trata separadas. Ausente.

### 4.3 Mediasombra (M) y plan de rustificación (§7)

**Modelo**
- Doc: **dos estados** (abierta/cerrada), **por macro-zona**, regulada por horario; de noche siempre cerrada; gana cerrar (§1.1, §6, principio 4).
- Código: **% de apertura por sector** (`SectorEntity.actuadorShade`, entero; seed `0`, `res/data.sql:13`). `MOVER_MEDIASOMBRA` publica `shade SET {targetPct}` por sector (`engine/ActionExecutor.java:98-105`). El tope es `mediasombraAperturaMaxPct` = 100 (`rules/ShadingRule.java:84`, `res/data.sql:666`). No hay "gana cerrar": UV pisa al plan (`rules/ShadingRule.java:87-100`) pero se limita la apertura a `min(30, max)`, no se cierra.
- No hay orden de cierre ante bloqueo de zona, nodo caído o falla (ver S-02, S-05).

**M-01**
- Doc: perfil de crecimiento 08-10 abierta, resto cerrado; con plan activo, horario de la etapa del día; sin alertas (§6, §7).
- Código: sólo existe el camino "plan": `cycleDay = hoy − sowingDate + 1`; busca la etapa que contiene ese día y mueve la apertura a `min(aperturaPct, max)` (`rules/ShadingRule.java:103-133`). Sin `sowing-date-iso` (default vacío) devuelve `NOOP_INFO` y no mueve la malla (`rules/ShadingRule.java:103-106`). Fuera de los días de las etapas (> 30) también `NOOP_INFO` (línea 118-121), es decir **no hay estado final** que siga con la etapa 4 al terminar. No hay perfil de crecimiento, ni evaluación por franja horaria, ni cierre nocturno.

**M-02**
- Doc: entre 11:00 y 16:00, si T ≥ 35 °C, o luz ≥ 85 % con T ≥ 32 °C, o UV pronosticado ≥ 11 con T ≥ 32 °C → malla cerrada hasta las 16:00; T = máx(lectura, pronóstico); alertas INFO/WARNING (≥ 35)/CRITICAL (≥ 38) (§6).
- Código: si `forecast.uvIndex() >= 7.0` (property `engine.uv-threshold`, `res/application.properties:100`) pone apertura = `min(30, max)` (`rules/ShadingRule.java:89-96`). Sin ventana horaria, sin temperatura ni luminosidad, sin retención hasta las 16:00 (apenas el UV baje, la próxima evaluación vuelve al plan), sin alertas. El UV es el de la hora actual del pronóstico (`engine/weather/OpenMeteoWeatherClient.java:114`), no el máximo del día.
- La temperatura pronosticada (`WeatherForecast.tempC`) se calcula pero ninguna regla la consume.

**Plan de rustificación (§7)**
- Doc: por MZ; lo inicia/cancela/resetea el Ingeniero Agrónomo; 4 etapas con horarios (45 días de fábrica; rango 20-60); sólo regula mediasombra; tras el día 45 sigue con la etapa 4; INFO al terminar/cancelar.
- Código: **un plan global** (tabla `rustificacion_etapa`, sin zona), con etapas por día→% de apertura: 1-7 → 20 %, 8-14 → 40 %, 15-21 → 70 %, 22-30 → 100 % (`res/data.sql:672-675`, 30 días). El inicio lo define una property global `nursery.sowing-date-iso` (`rules/ShadingRule.java:54-55`). No hay iniciar/cancelar/resetear, ni rol, ni por MZ, ni alertas de fin. La edición del plan se hace por la configuración agronómica (`service/ConfiguracionService.java:151-157`, validación de solapamiento y rango de apertura en 228-251). Una etapa define un % de apertura, no franjas horarias.

### 4.4 Nutrición (N)

- **N-01** (plan cada 7 días por MZ, 100 mL, próxima ventana apta): no existe. No hay registro del "último plan", ni intervalo por MZ, ni dosis en mL (ver 4.6 sobre la dosis).
- **N-02** (CE media 24 h < 0,5 dS/m y sin fertilización en 5 días → adelantar N-01): no existe. La CE (`ce`) es una métrica que afecta estado (`afectaEstado = true`, `constant/NurseryConstants.java:64`) pero no hay regla que la use. No hay media de 24 h ni historial de lecturas.

### 4.5 Reglas por diagnóstico (F) — 🔶 Sin revisar en el doc (§8.5)

La sección F del documento está marcada **Sin revisar**; todas las diferencias de esta sección caen ahí.

- **Qué hace el código.** `SupplyRule` (prioridad 11, rama INSUMO): si `finalStatus != "critical"` → `NOOP_INFO`; si la confianza es nula → `NOOP_INFO`; si `conf >= 85` → `ACTIVAR_BOMBA`; si no → `NOOP_INFO` (`rules/SupplyRule.java:62-82`). No lee la clase del diagnóstico (sólo la cita en el texto), ni la severidad, ni la antigüedad.
- **El "estado crítico" viene de sensores, no de la IA.** `finalStatus` es `critical` si cualquier métrica con `afectaEstado` está `critical` (`service/NurseryService.java:453-463`). Por las bandas actuales, esto incluye humedad de sustrato, humedad ambiental, temperatura, **luminosidad** (`uv` crítica si < 20 % o > 85 %, `constant/NurseryConstants.java:61`) y CE (`…:64`). Una lectura de luminosidad baja pasa el sector a `critical`, y con el diagnóstico sintético (ver abajo) puede activar la bomba. Es una consecuencia de las bandas y la lógica leídas; no se ejecutó.
- **Diagnóstico sintético** (`service/NurseryService.java:472-490`): `ok` → "Sano" 98 %; `warning` → "Clorosis"/Media; `critical` → "Daño fúngico"/Alta con confianza 92 %. Si el sector ya estaba en `warning`/`critical` con un diagnóstico real (cargado por `POST /api/diagnosticos`), se conserva (línea 479-480). Si el estado vuelve a `ok`, se pisa con "Sano".
- **Falta por regla:**
  - F-C1: sin chequeo de asfixia/salinidad/pH ni WARNING con causa.
  - F-C2: el severidad "moderada o alta" en rustificación no se evalúa.
  - F-F1: sin condiciones de severidad, ambiente húmedo (humedad > 80 %, DPV) ni 3 capturas seguidas.
  - F-F2: no hay DPV. El DPV no se calcula ni se guarda (búsqueda de `dpv|vapor` en `src/main/java`: sin resultados); tampoco existe ninguna alerta de condiciones predisponentes.
  - F-P1: sin clima de ácaros ni conteo de capturas.
  - F-S1/F-S2/F-S3: no hay riego de reposición, ni cierre de MZ por 3 días, ni alerta de síntoma no explicado. No hay lectura de las últimas 48 h (el motor no consulta historial de lecturas; ver punto 7).
  - F-04: no hay vecindad ni anillo de 8 sectores.
  - F-05: ver E.
  - F-06: no hay conteo de fitosanitarios por 30 días; sólo `DailyDoseLimitRule` (1 dosis/24 h).
- **No hay "una alerta por evento".** Cada evaluación registra un evento "Info" por regla y por sector (`engine/ActionExecutor.java:107-118`). Principio 8 del doc: no se cumple.

### 4.6 Precondiciones de dosificación (P-D) y ejecución (§8.2)

- **Dosis y bomba.** Doc: un insumo por tanque; dosis 100 mL por sector; `t = dosis / caudal`; la orden lleva duración y el ESP32 corta solo; stock descontado y alertas al 20 % / tanque vacío (§8.1-8.2, §11).
- Código: una sola acción `ACTIVAR_BOMBA` sin tipo de insumo ni dosis; el comando es `pump ON` con `parametros` vacíos (`engine/ActionExecutor.java:94`). El firmware espera `ml` y rechaza la orden sin él (`Desarrollo/embebido/actuacion/act_bomba.cpp:24-27`, `Desarrollo/embebido/comun/util_json.cpp:77`). No hay stock/tanques, ni reingreso restringido de 24 h, ni marca de "aplicado" para P-D2/R-06 más allá del evento de historial "Insumo".
- La dosis máxima por 24 h (`insumoDosisMax24hMl`, default 15, `res/data.sql:666`) **sólo aparece en el texto del motivo** (`rules/DailyDoseLimitRule.java:81-85`); la regla cuenta eventos, no mL.
- **P-D1** vigente: parcial. Sólo confianza ≥ 85 % hardcodeado (`rules/SupplyRule.java:34,72`). El doc: umbral 70 % configurable (50-95), última captura, ≤ 48 h. Existe `capturas.confianza-minima = 85` (`res/application.properties:143`, `config/CapturaProperties.java:53`), que sólo usa `DiagnosticoService.esConcluyente` (`service/DiagnosticoService.java:133-135`); no se encontró uso de `esConcluyente` en el motor.
- **P-D2** intervalo mínimo y un solo producto por día: `DailyDoseLimitRule` bloquea con `ABORT_INSUMO` si hay ≥ 1 evento "Insumo" en 24 h para el sector (`rules/DailyDoseLimitRule.java:77-87`). Cubre "no más de una aplicación por día" pero no el intervalo de 5 días (fertilizante) / 7 días (fitosanitario) ni el orden fitosanitario → fertilizante.
- **P-D3** ventana y temperatura ≤ 30 °C: no existe.
- **P-D4** lluvia ≥ 5 mm en 6 h: no existe (`SupplyRule` no usa el pronóstico).
- **P-D5** humedad ≥ 45 % o regado después de la última lectura: no existe.

### 4.7 Efectividad (E)

- Mecanismo del código: cada riego o dosificación registra un evento con `valorAntes` = humedad de sustrato de la zona, latencia y umbral de la configuración operativa (`service/HistorialService.java:66-132`). `evaluarSeguimiento` corre por `@Scheduled` cada 30 s (`service/HistorialService.java:192`, `res/application.properties:78`) y además `FollowUpRule` lo invoca en cada evaluación de cada sector (`rules/FollowUpRule.java:71`). Pasada la latencia compara `ahora − antes`; el veredicto es "Efectiva" si `abs(delta) >= umbral` (`service/HistorialService.java:209-217`).
- **E-01.** Doc: lectura de verificación a los 30 min (rango 15-60), humedad de la MZ, delta ≥ +8; alcance MZ. Código: latencia 2 min (`seguimientoLatenciaMin`, `res/data.sql:666`), delta mínimo 5, valor **absoluto** (una baja de 5 puntos también cuenta como efectiva), sin pedido de lectura de verificación (usa la última lectura que haya).
- **E-02, E-03.** Doc: evalúan diagnóstico (14 días / 7 días). Código: los eventos "Insumo" se evalúan con `metricKey = "humSus"` (`service/HistorialService.java:119`), es decir, contra la humedad de sustrato. Ausentes como regla.
- **F-05** (cierre "Efectiva" cuando el sector vuelve a Sano): no existe; el veredicto no mira el diagnóstico.
- La salida de la evaluación no alimenta ninguna regla (ver S-06).

### 4.8 Operación sin internet (O)

- **O-01.** Doc: sin pronóstico, asumir que no llueve y R-01 riega. Código: `forecast == null` → `NOOP_INFO` "modo degradado" y se sigue (`rules/WeatherOverrideRule.java:69-73`). `WeatherService` devuelve `null` tras 3 reintentos con espera 1/2/4 s (`engine/weather/WeatherService.java:84-98`, `res/application.properties:124-127`) y cachea 15 min (`…:121`). Comportamiento equivalente. Los reintentos bloquean el hilo del motor (`Thread.sleep`, `engine/weather/WeatherService.java:100-106`; pool de un hilo, `config/SchedulersConfig.java:42-50`). Implementada de hecho.
- **O-02.** Doc: M-02 usa sólo la lectura. Código: sin pronóstico no se evalúa UV y se sigue con el plan; no existe M-02 por lectura. Parcial.
- **O-03.** Sin P-D3/P-D4 no hay qué degradar. Ausente.
- **O-04.** El plan se basa en reloj local y fecha configurada (`rules/ShadingRule.java:110`). Implementada sobre el plan del código.
- **O-05.** No se encontró en el motor ningún mecanismo de subida de eventos con timestamp original ni de deduplicación. La parte del nodo (firmware) no se revisó. Sólo hay una referencia a idempotencia del ESP32 ante comandos repetidos (`mqtt/MqttConfig.java:93`). Ausente en el backend.

### 4.9 Principios del motor (§2) — cómo se reflejan

| Principio | Doc | Código |
|---|---|---|
| 1 Orden S → supervivencia → sanidad → rutina | Jerarquía por familias | Orden por número de prioridad y ramas independientes; no hay familias (`engine/RuleOrchestrator.java:36-98`) |
| 2 Decide con última lectura/captura válida | Sólo con lectura válida; diagnóstico vigente | Sin validación de lectura; sin vigencia de diagnóstico; en el watchdog `metrics` está vacío |
| 3 El clima sólo agrega protección | Con déficit crítico se riega | La lluvia pospone siempre (`rules/WeatherOverrideRule.java:75`) |
| 4 En mediasombra gana cerrar | Cerrar | No existe; la regla UV *reduce* a 30 % |
| 5 Dosis nunca pasa la etiqueta | — | No hay dosis en el motor |
| 6 Corte por tiempo calculado; el ESP32 corta solo | Orden con duración | Riego: `durationSec` en el comando (`engine/ActionExecutor.java:83-84`), con tiempo fijo. Bomba: sin duración ni mL |
| 7 Cuándo evalúa | Por lectura (4 h), captura, cambio de franja | Por mensaje de telemetría y cada 5 min; nunca por captura ni franja |
| 8 Una alerta por evento | Una vez al activarse | Un evento "Info" por regla/sector/ciclo |
| 9 Horario local fijo | America/Argentina/Buenos_Aires | `ZoneId.systemDefault()` |

---

## 5. Lo que está en el código y NO está en el doc

1. **`DailyVolumeLimitRule`** (prioridad 4, rama RIEGO): `ABORT_RIEGO` si el sector tiene ≥ 2 eventos "Riego" en las últimas 24 h (`rules/DailyVolumeLimitRule.java:57-68`). El texto habla de "límite de volumen diario" pero cuenta riegos ("Fallback logic until real volume integration", línea 60). El doc eliminó los límites diarios (Anexo A, fila "Límites diarios, histéresis, batería"). En la práctica queda tapada por la guarda de `IrrigationRule` (> 0 riegos en 24 h).
2. **Guarda de 1 riego por 24 h en `IrrigationRule`** (`rules/IrrigationRule.java:94-104`). El doc de v2 dice "sin intervalo mínimo" entre riegos (Anexo A, fila Riego) y sólo limita R-02 a 1 cada 12 h bajo S-06.
3. **`DailyDoseLimitRule`**: 1 dosificación por 24 h por sector (`rules/DailyDoseLimitRule.java:77-87`); cubre parcialmente P-D2, pero con otra lógica (cualquier producto, no por intervalo de insumo).
4. **`riegoVolMaxDiarioMl` (2000) e `insumoDosisMax24hMl` (15)** como parámetros configurables que no se usan para decidir (sólo aparecen en mensajes: `rules/IrrigationRule.java:103`, `rules/DailyDoseLimitRule.java:85`). No están en el §11.
5. **`intervaloSensadoMinutos` (240) e `intervaloEvaluacionMinutos` (5)** como parámetros de configuración operativa (`service/ConfiguracionService.java:100-104,145-146`). Sólo el segundo se usa (intervalo del watchdog). El doc define intervalo de lectura 4 h (1-6 h) pero como pedido del backend, no como configuración que el motor consuma.
6. **Conducta de la mediasombra como porcentaje** y plan por día de ciclo (4 etapas de 1-7/8-14/15-21/22-30) en lugar de horarios (ver 4.3).
7. **Diagnóstico sintético por estado de sensores** (`service/NurseryService.java:472-490`) como entrada del motor.
8. **Orquestación en ramas (DAG)** con `ABORT_*` por rama, y `GET /api/rules/schema` que expone el grafo (`controller/RuleEngineSchemaController.java:56-119`, `engine/RuleBranch.java:8-23`). El doc no define esta estructura.
9. **Registro de Inacción**: persistencia de cada `NOOP_INFO`/bloqueo como evento "Info" (`service/HistorialService.java:147-163`). El doc pide trazabilidad de acciones y alertas (HU-11), no un evento por cada evaluación sin acción.
10. **`FollowUpRule`** como regla (prioridad 20) que delega en `HistorialService.evaluarSeguimiento()` (`rules/FollowUpRule.java:68-79`). Se ejecuta por sector en cada ciclo (100 veces por mensaje de telemetría de una zona) aunque el método es global.
11. **Property sin uso**: `yerbanalytics.engine.action-cooldown-minutes` (30 min) descrita como cooldown entre acciones (`res/application.properties:92-94`); no la lee ninguna clase Java.
12. **Pronóstico**: se consulta Open-Meteo con `lat/lon` configurables (`res/application.properties:117-118`), cache 15 min y reintentos con backoff. El doc no define proveedor ni caché.
13. **Pausas del estado de actuadores**: el campo `"Regando"`/`"Dosificando"` pasa a ser un enganche. Como nunca se limpia, tras la primera acción del sector no se publican nuevos comandos de válvula/bomba (la guarda de transición es `!"Regando".equals(oldValve)`, `engine/ActionExecutor.java:80`). Es comportamiento de la implementación, no del doc.

---

## 6. Diferencias de supuestos y parámetros (§1 y §11 vs. valores reales)

### 6.1 Estructura, hardware y comunicación (§1)

| Parámetro | Doc | Código |
|---|---|---|
| Macro-zonas | 10 MZ × 100 sectores = 100 000 plantines (§1.1) | 6 MZ × 100 sectores = 600 sectores (`res/data.sql:5-10`, `constant/NurseryConstants.java:71-78`) |
| Sector | 4 bandejas de 25 celdas, 100 plantines, ~0,3 m² | No se modela la geometría en el motor |
| Mediasombra | Dos estados, por MZ | % de apertura por sector (`SectorEntity.actuadorShade`) |
| Riego en campo | Electroválvula + microaspersor por sector; prototipo: bomba | Comando `valve ON` por sector (`engine/ActionExecutor.java:84`) |
| Caudal del emisor | 30 L/h; caudal calibrado por aforo | No hay caudal en el motor (búsqueda de `caudal` en `src/main`: sólo menciones en `MqttConfig` y `DailyDoseLimitRule`) |
| Caudalímetro | Versión final; S-05 | No existe |
| Dosificación | Dos tanques (fertilizante, fitosanitario) con bomba propia | Una sola acción `ACTIVAR_BOMBA` por sector, sin tipo de insumo |
| Sectores regando a la vez por MZ | 10 | Sin límite: hasta 100 por ciclo |
| Telemetría | El backend pide la lectura cada 4 h (02, 06, 10, 14, 18, 22 h) | El nodo publica; el backend sólo consume (`mqtt/MqttConfig.java:28,54`) |
| Errores del ESP32 | Tópico propio | No se suscribe |
| Batería | Sin regla de batería | No hay regla de motor; `hardware.bateria-min-pct = 20` en properties (`res/application.properties:82`) |
| Luminosidad | % LDR; ≥ 85 % = sol directo | % LDR (`constant/NurseryConstants.java:47-50`); sin uso de 85 % en reglas |
| Humedad de sustrato | 80 % cerca de saturación | Banda crítica > 80 (`constant/NurseryConstants.java:58`); sin regla |
| Captura/diagnóstico | Una pasada diaria a las 09:00 | Sin planificador de pasadas; la orden de captura se emite por endpoint (`controller/CapturaController.java:44,67`) |
| Zona horaria | America/Argentina/Buenos_Aires | `ZoneId.systemDefault()` |

### 6.2 Telemetría y diagnóstico (§11)

| Parámetro | Doc | Código |
|---|---|---|
| Intervalo de lectura | 4 h (1-6 h) | `intervaloSensadoMinutos` = 240 (guardado, sin uso) |
| Espera de respuesta del nodo | 60 s | No aplica; umbral de "stale" 30 000 ms (`res/application.properties:69`) |
| Reintentos (sin respuesta/inválida) | 2, cada 1 min | No existen |
| Reintento con nodo fuera de servicio | 15 min | No existe |
| Lectura de verificación post-riego | 30 min | No existe; seguimiento a 2 min (`res/data.sql:666`) |
| Hora de la pasada de captura | 09:00 | No hay |
| Antigüedad máxima de la captura | 48 h | No se aplica |
| Confianza mínima del diagnóstico | 70 % (50-95) | 85 % hardcodeado en `SupplyRule` (`rules/SupplyRule.java:34`) y 85 en `capturas.confianza-minima` |

### 6.3 Riego (§11)

| Parámetro | Doc | Código |
|---|---|---|
| Umbral de riego | 45 % (35-60) | 42 % = `idealMin` de `humSus` (`res/data.sql:646`) |
| Humedad objetivo | 65 % | No existe |
| Umbral crítico | 35 % (25-40) | No existe para riego; el estado del sector se vuelve `critical` con `humSus < 32` o `> 80` |
| Bloqueo por saturación (alerta) | 75 % (80 %) | No existe |
| Litros por punto de déficit | 0,2 L | No existe |
| Volumen máximo por evento | 6 L | `riegoTiempoMaxSeg` = 120 s (no volumen) |
| Sectores a la vez por MZ | 10 | Sin límite |
| Ventana de riego normal | 06:00-18:00 | Sin ventana |
| Lluvia para posponer | 70 % / 5 mm / 4 h | 60 % (property) / sin mm / hora actual |
| Pausa de riego tras aplicación | 6 h | No existe |

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
| Índice UV extremo | 11 | 7,0 (property `engine.uv-threshold`), y la acción es apertura 30 % |
| Estrés solar extendido | 10 % de sectores / 3 días | No existe |

### 6.5 Dosificación y seguimiento (§11)

| Parámetro | Doc | Código |
|---|---|---|
| Dosis de fertilizante / fitosanitario | 100 mL / 100 mL | No hay dosis en el comando; tope `insumoDosisMax24hMl` = 15 sólo informativo |
| Intervalo mínimo | 5 / 7 días | 1 dosis por 24 h (hardcodeado) |
| Intervalo del plan de nutrición | 7 días por MZ | No existe |
| Ventanas de aplicación | 07-10 y 17-19 | Sin ventana |
| Temperatura máxima para aplicar | 30 °C | Sin chequeo |
| Humedad mínima para aplicar | 45 % | Sin chequeo |
| Lluvia que impide aplicar | 5 mm en 6 h | Sin chequeo |
| Reingreso tras fitosanitario | 24 h | No existe |
| Sectores para declarar foco | 3 en 72 h | No existe |
| Tope de fitosanitarios | 3 en 30 días | No existe |
| Aviso de stock bajo | 20 % | No hay stock |
| Caudal de rotura / tiempo sin caudal | 150 % × 30 s / 10 s | El firmware tiene `TIMEOUT_CAUDAL_MS` en `Desarrollo/embebido/actuacion/act_valvula.cpp:44`; no se revisó su valor; el backend no consume el error |
| Latencia / delta de efectividad | 30 min y +8 / 14 d / 7 d | 2 min y |Δ| ≥ 5 (valores de `configuracion_operativa`) |
| Riego exceptuado del bloqueo (R-02) | 1 cada 12 h | No existe |

### 6.6 Rangos por métrica (§3) vs. bandas reales

El código evalúa cada métrica así: `critical` si el valor sale de la banda `warn`; `warning` si sale de la banda `ideal`; `ok` en otro caso (`service/NurseryService.java:572-579`). Por eso "crítico" en el código equivale a salir de `warn`, no al "Crítico bajo/alto" del doc. Valores de `constant/NurseryConstants.java:58-68`.

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

1. **Cadencia real de la telemetría.** El backend es receptor pasivo; la frecuencia depende del firmware o del simulador. No se revisó el `.ino` del nodo ni el simulador.
2. **Si `data.sql` se aplica automáticamente.** `spring.sql.init.mode=never` (`res/application.properties:55`); el README del backend dice que `data.sql` siembra la base (`Desarrollo/backend/README.md:30,37,39`). No se verificó el mecanismo exacto. Los valores por defecto se tomaron de `data.sql` y de `ConfiguracionService.defaultOperativa` (coinciden en riego 120 s, 2000 mL, dosis 15, delta 5, etc.); en una base ya sembrada con otros valores pueden diferir.
3. **Zona horaria del pronóstico.** La consulta a Open-Meteo no envía el parámetro `timezone` (`engine/weather/OpenMeteoWeatherClient.java:76-82`) y el índice de la hora actual se busca con la hora local del sistema (línea 150-157). Si el servidor corre en una zona distinta de la que devuelve el servicio, el índice (y por tanto la probabilidad de lluvia y el UV usados) podría corresponder a otra hora. No se ejecutó para comprobarlo.
4. **Comportamiento en ejecución.** Todo lo descripto sale de leer el código; no se levantó el backend ni se ejecutaron los tests de `src/test/java/.../engine/` (cubren `ManualLock`, `StaleSensor`, `WeatherOverride`, `DailyDoseLimit`, `Shading`/`FollowUp`; no hay tests de `IrrigationRule`, `SupplyRule` ni `DailyVolumeLimitRule`; el orquestador tampoco tiene test propio entre los archivos listados).
5. **Código del watchdog de hardware** (`HardwareService`, umbrales de `res/application.properties:82-86`) no leído; podría cubrir parte de S-02/S-04 fuera del motor de reglas.
6. **Valores de `TIMEOUT_CAUDAL_MS`, `LIMITE_BOMBA_ML_MAX` y el manejo de caudal del firmware** no revisados (parámetros S-05 "los aplica el ESP32").
7. **Historial de lecturas.** No se encontró ninguna estructura que guarde lecturas pasadas de las zonas (la zona guarda sólo el último valor, `service/NurseryService.java:428-445`). Reglas del doc que necesitan ventanas (N-02 24 h, F-C1 48 h, F-F1/F-F2 DPV, F-P1 72 h, F-S1 48 h) no tendrían de dónde leer; no se verificó la existencia de otra tabla de lecturas fuera de lo recorrido.
