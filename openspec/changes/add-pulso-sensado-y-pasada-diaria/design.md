## Context

Estado actual:

- `SensingScheduler` publica `LEER_AHORA` a todas las zonas cada `sensingPullIntervalSeconds` (60 s por defecto,
  máximo 3600), contado desde el fin del ciclo anterior. `SensingCadence` resuelve ese intervalo y el umbral de
  lectura vieja `max(piso, 3 × X)`, que también decide el estado "fuera de servicio" del panel.
- `NurseryService.updateTelemetry` corre con **cada** telemetría: persiste la lectura en la zona, recalcula el estado
  de los 100 sectores, **les pisa el diagnóstico** con uno derivado del estado ("Sano 98 %" o la patología por
  defecto con 92 %) y evalúa el motor (`OrigenEvaluacion.TELEMETRIA`).
- `NurseryWatchdog` evalúa todas las reglas de los 600 sectores cada `intervaloEvaluacionMinutos` (5), sin
  métricas frescas (`OrigenEvaluacion.BARRIDO`).
- `intervaloSensadoMinutos` (240) sólo define las franjas de `CicloLectura` (ancladas a las 02:00) que usa el riego.
- `HardwareService` marca el nodo testigo como intermitente a los 60 s sin heartbeat y fuera de servicio a los 120 s.
- `PasadaRielService` sólo arranca por `POST /api/pasadas`; recorre 2 sectores fijos de la primera zona con el único
  riel. El servicio de inferencia consulta `/api/capturas/pendientes-inactivas` cada 10 s y diagnostica las capturas
  quietas hace 1 min, o sea ~1 min después de terminar la pasada.
- `DiagnosticoService.alta` copia estado, confianza y severidad al sector. Sólo `SupplyRule` usa el diagnóstico.
- `ActionExecutor` registra la inacción sólo ante un cambio, con un tope de 30 min por sector
  (`yerbanalytics.historial.inaccion-intervalo-min`, commit `d9cc7d5`).
- El panel general consulta el snapshot cada 5 s (`NurseryContext`).

Restricciones: el backend no conoce al simulador; el contrato MQTT, el firmware y el simulador no deberían cambiar;
un único ESP32 en el stand comparte riel, válvula y sensado (`GuardiaHardware`); toda ruta nueva se declara en
`MapaPermisos`. Las reglas concretas van a cambiar: la cadencia no debe depender de cuáles sean.

## Goals / Non-Goals

**Goals:**
- Un único ritmo de decisión: el pulso, cada `intervaloSensadoMinutos` en franjas desde las 02:00, también pedible a
  mano por API, con o sin motor, para demostrarlo.
- Detección de nodos caídos por pulsos sin respuesta, con escalamiento `WARNING` → `WARNING` → `CRITICAL`.
- Reglas de diagnóstico que actúan una vez por diagnóstico nuevo, con esa marca persistida.
- Pasada diaria configurable (hora, activa/inactiva) y la manual intacta.
- Historial atado al pulso.

**Non-Goals:**
- Definir o reescribir las reglas agronómicas (siguen las actuales; sólo `SupplyRule` se marca como de diagnóstico).
- Ejecutar el plan de rustificación por horario (es de `add-rustificacion-por-macrozona`).
- Un riel por macro-zona o un recorrido de más de 2 sectores.
- Cambios al contrato MQTT, al firmware, al simulador o al servicio de inferencia. Las secuencias de Demo Expo sólo
  suman `ejecutarMotor` a la `LECTURA` (D11).
- Una vista del avance del pulso: el resultado se ve en el panel general y en el historial.
- Recuperar pulsos o pasadas perdidos mientras el backend estaba caído.

## Decisions

### D1. El pulso reemplaza al pedido periódico; un solo intervalo de sensado

`SensingScheduler` pasa a ser el planificador del pulso (`PulsoSensadoService` + scheduler en el carril
`sensingScheduler`). El próximo disparo se calcula con `CicloLectura.inicio(ahora, intervalo)` + un paso, la misma
función que ya usa el riego: el pulso coincide con el ciclo de lectura por construcción, y "un riego común por ciclo"
pasa a significar "por pulso". Se retira `sensingPullIntervalSeconds`.

*Alternativa descartada:* conservar los dos intervalos (pedido en segundos y franjas en minutos). Es exactamente lo
que generó la avalancha: el ritmo real lo marcaba un valor que nadie asociaba con "evaluar".

### D2. Correlación por ventana de tiempo, no por `commandId`

La telemetría del contrato no trae `commandId` (`contrato.h`: "SIN commandId y SIN ack"). Una zona del pulso queda
pendiente desde el pedido; la primera telemetría de esa zona con hora de recepción ≥ pedido la responde. Es el mismo
criterio que ya usa la secuencia `LECTURA`. Reintentos: cada `yerbanalytics.pulso.reintento-seg` (60) hasta
`yerbanalytics.pulso.intentos` (3).

`updateTelemetry` le pregunta al servicio del pulso "¿esta telemetría responde a un pulso en curso, y ese pulso
ejecuta el motor?" con un método que además marca la zona como respondida, atómicamente, para que dos telemetrías
simultáneas no evalúen dos veces. Si corresponde motor: flujo actual. Si no: persiste lectura y estado de sectores.

*Alternativa descartada:* agregar `commandId` a la telemetría. Cambia el contrato, el firmware y el simulador para
ganar una precisión que la ventana ya da.

### D3. Un pulso con o sin motor, mismo camino

`POST /api/pulsos {ejecutarMotor}` responde `202` con el pulso (id, origen, `ejecutaMotor`, pedido). Los programados
siempre ejecutan el motor. Un pulso con `ejecutarMotor: false` tiene los mismos reintentos y el mismo escalamiento,
así que por API también sirve para mostrar un nodo caído (tres pulsos con el nodo apagado lo ponen fuera de
servicio). En la vista, la lectura con motor es una secuencia de una zona (D11). El estado del pulso vive en memoria y no se expone: la vista no lo necesita (el panel
general refresca cada 5 s) y el `409` alcanza para avisar que hay uno en curso. Permiso: `pasadas.operar`, el mismo
que ya habilita operar la pasada y las secuencias en Demo Expo.

El pulso **no** toma `GuardiaHardware`: pedir una lectura no mueve hardware. Si el ESP32 está ocupado con una pasada
y no contesta, lo cubren los reintentos (ver Riesgos).

### D4. Escalamiento persistido por zona

`ZonaEntity` suma `pulsosSinRespuesta` (entero, nulo = 0). Al cerrar un pulso, cada zona sin respuesta incrementa la
cuenta y registra alerta (`HistorialService.registrarAlerta`, regla `PulsoSensado`): 1 y 2 → `WARNING`, 3 →
`CRITICAL`; más de 3 → sin alerta. Cualquier telemetría de la zona la vuelve a 0 (si venía de ≥ 3, alerta `INFO`
"el nodo volvió a responder"). El estado "fuera de servicio" del snapshot y de `HardwareService` sale de
`pulsosSinRespuesta >= 3` (o de una zona que nunca leyó), no del umbral de lectura vieja ni del tiempo sin heartbeat.

*Alternativa descartada:* cuenta en memoria. Un reinicio en medio de una racha la perdería, y el nodo podría quedar
"operativo" con dos fallas encima.

### D5. Sin barrido

Se elimina `NurseryWatchdog` y `intervaloEvaluacionMinutos`. Lo que hacía queda cubierto así: los nodos caídos, por
D4; la mediasombra por reglas, en el pulso como cualquier regla; la mediasombra por plan de rustificación, por su
propio horario fijo fuera del motor (otro cambio). `OrigenEvaluacion.BARRIDO` y el estado de inacción por barrido del
`ActionExecutor` se eliminan; la traza del Inspector queda con un solo origen.

### D6. "Diagnóstico nuevo" con dos marcas en el sector

`SectorEntity` suma `diagnosisTs` (hora de llegada del diagnóstico vigente; lo escribe `DiagnosticoService.alta` con
el `creadoEn` del diagnóstico) y `diagnosisUsadoTs` (hora de llegada del último usado). Nuevo ⇔ `diagnosisTs != null`
y (`diagnosisUsadoTs == null` o `diagnosisTs > diagnosisUsadoTs`). `Rule` suma `default boolean usaDiagnostico()`;
`SupplyRule` devuelve `true`. El orquestador saltea esas reglas si no hay diagnóstico nuevo (estado de traza
`OMITIDA_SIN_DIAGNOSTICO_NUEVO`); si alguna se evaluó, `ResultadoEvaluacion` lo informa y `NurseryService` copia
`diagnosisTs` a `diagnosisUsadoTs` dentro de la misma transacción (un rollback lo deja nuevo).

*Alternativa descartada:* "las reglas de diagnóstico sólo corren en el primer pulso del día". Falla si el riel se
atrasa, si la inferencia tarda, si la pasada es manual (la demo) o si la hora de la pasada cae después del primer
pulso. *Otra descartada:* una columna en `diagnostico`: el registro de diagnósticos es inmutable, y lo que importa es
el estado del sector.

### D7. La ingesta deja de inventar el diagnóstico

Se borra el bloque "Diagnosis" de `updateTelemetry` (asignaba "Sano"/patología según el estado). `reason` sigue
cayendo al diagnóstico vigente cuando ninguna métrica decisiva explica el estado. El mapa de patologías por estado
(`PATHOS`) se elimina si queda sin uso.

### D8. Pasada diaria: disparo por minuto, sin estado de "ya corrió hoy"

Un tick por minuto en `pasadaScheduler` lee `pasadaDiariaActiva` y `pasadaDiariaHora`; si está activa, la hora local
(truncada al minuto) es la configurada y no se disparó ya en ese minuto, llama a `PasadaRielService.iniciar()`. Un
rechazo se registra como alerta `WARNING` (zona del recorrido, regla `PasadaProgramada`), sin reintento. La
configuración vive en `configuracion_operativa` y se edita por `PUT /api/configuracion`, que ya da validación y
auditoría. Por defecto, desactivada: en desarrollo no debe mover el riel sin que nadie lo pida.

*Alternativa descartada:* `>= hora y no corrió hoy`, con la fecha en memoria: tras un reinicio posterior a la hora
volvería a correr el mismo día.

### D9. Historial: inacción completa en cada pulso

`ActionExecutor` pierde la deduplicación por firma y el tope de 30 min: en cada evaluación de pulso escribe una fila
por cada inacción del sector. Así cada pulso queda como un "ciclo" completo en la vista del historial (que ya agrupa
por fecha y hora) y el DAG de cada sector siempre tiene todas sus reglas. Volumen: ~7.800 filas por pulso con motor,
~47.000 por día con 6 pulsos; la consulta del historial ya tiene tope (commit `207a385`).

### D10. Umbral de lectura vieja sobre el pulso, sólo para el riego

`SensingCadence.staleThresholdSeconds(piso, intervaloMin, ventanaSeg) = max(piso, intervaloMin × 60 + ventanaSeg)`.
Lo siguen usando `StaleSensorRule` (humedad de sustrato que no vino en la respuesta) y la revalidación del despacho
de riego (que pausa la ronda si la lectura dejó de estar vigente). Deja de usarse para el estado "fuera de servicio"
(D4).

### D11. En Demo Expo, la lectura con motor es una secuencia de una zona

La vista no ofrece pulsos de todas las zonas: en la tarjeta "Lectura" de Secuencias, "Leer sensores y ejecutar motor
ahora" inicia la secuencia `LECTURA` con `ejecutarMotor: true`. Su paso `PEDIR` emite un pulso con motor sólo para la
zona de la secuencia (`PulsoSensadoService.iniciar(MANUAL, true, [zona])`) en vez del `LEER_AHORA` suelto. El motor
sigue teniendo un único camino (la respuesta a un pulso con motor) y la secuencia muestra los valores como siempre.
`POST /api/pulsos` queda para pedir el pulso de todas las zonas por API.

*Alternativa descartada:* que la secuencia llame al motor al recibir la lectura. Abriría un segundo camino al motor,
fuera de la ingesta y de su transacción.

## Risks / Trade-offs

- [El riego reacciona una vez por pulso: el déficit crítico puede esperar hasta 4 h] → Es la cadencia que plantea
  `reglas_v2` §1.2; el intervalo se baja hasta 60 min, y "Leer y ejecutar motor" decide en el momento.
- [El ESP32 del stand no responde `LEER_AHORA` mientras mueve el riel] → Los 3 pedidos en 3 min cubren una pasada de
  ~2 min; si no alcanza, cuenta como un pulso sin respuesta (`WARNING`). Verificar con el hardware.
- [Un pulso manual dentro de la franja permite decidir dos veces en el mismo ciclo] → Las reglas de riego ya limitan
  a un riego común por ciclo de lectura y el diagnóstico se consume una vez.
- [Sin barrido, una regla que dependa sólo de la hora no corre entre pulsos] → Hoy no hay ninguna fuera de la
  mediasombra; el plan de rustificación tendrá su propio horario.
- [El historial crece ~47.000 filas por día] → Aceptado por ahora; la consulta tiene tope y filtros. Si molesta,
  volver a "sólo ante un cambio" es local al `ActionExecutor`.
- [Retirar dos campos de la configuración rompe a un cliente que los lea] → Sólo los usa el frontend, que cambia en
  este mismo cambio.

## Migration Plan

1. Backend y frontend se despliegan juntos (los campos retirados y los nuevos viajan en `GET/PUT /api/configuracion`).
2. `ddl-auto=update` agrega `pasada_diaria_activa`, `pasada_diaria_hora`, `sector.diagnosis_ts`,
   `sector.diagnosis_usado_ts` y `zona.pulsos_sin_respuesta`. Los nulos se leen como valores de fábrica o 0.
3. `resources/migracion-pulso-sensado.sql` (a mano, como los demás): borra `sensing_pull_interval_seconds` e
   `intervalo_evaluacion_minutos`.
4. Rollback: volver a la versión anterior; las columnas nuevas sobran y las retiradas se recrean con su default.

## Open Questions

- Ninguna abierta por ahora.
