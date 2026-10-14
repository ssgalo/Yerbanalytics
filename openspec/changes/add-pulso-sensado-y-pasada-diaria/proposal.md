## Why

Hoy el motor de reglas corre con cada lectura que llega, y el backend pide lectura cada 60 s: el motor evalúa los
600 sectores por minuto y el historial se llenó con cientos de miles de filas por hora. Ninguno de los tres
intervalos configurables controla de verdad cuándo se decide: el pedido de lectura (segundos) marca el ritmo, el
"Intervalo de Sensado" sólo define franjas para el riego y el "Intervalo de Evaluación" es un barrido sin lectura
nueva. Además, cada lectura pisa el diagnóstico de IA de los 100 sectores de la zona con uno inventado a partir de
los sensores, así que las reglas que deberían usar la imagen nunca la ven.

El modelo operativo del vivero es otro: una vez al día el riel fotografía los sectores y el modelo de IA los
diagnostica; cada 4 h el backend lee los sensores de cada macro-zona y en ese momento evalúa el motor, combinando
lectura y diagnóstico. Las reglas definitivas todavía no están cerradas, pero esta cadencia sí, y es independiente de
qué reglas haya. Tiene que poder demostrarse en vivo: la lectura, el motor y la pasada se piden a mano por API.

## What Changes

- **Pulso de sensado.** Cada `intervaloSensadoMinutos` (60–360 min, 240 por defecto), en franjas ancladas a las
  02:00, el backend pide lectura (`LEER_AHORA`) a todas las macro-zonas, con hasta 3 pedidos por zona. Los pulsos
  programados ejecutan el motor con la primera lectura de cada zona: es el único momento en que el motor decide.
- **Pulso manual por API**: `POST /api/pulsos` con `ejecutarMotor` — `false` sólo actualiza los valores sensados,
  `true` además ejecuta el motor.
- **Escalamiento por nodo testigo.** Un pulso sin respuesta (tras los 3 pedidos) deja una alerta `WARNING` de la
  zona; el segundo consecutivo, otra `WARNING`; el tercero, una alerta `CRITICAL` que pone el nodo fuera de servicio.
  La cuenta se persiste por zona y vuelve a cero con cualquier lectura. La vista Hardware sigue esa cuenta.
- **BREAKING — Sin barrido.** Se elimina `NurseryWatchdog` y el `intervaloEvaluacionMinutos`. La mediasombra la
  mueven las reglas en el pulso; el plan de rustificación por horario queda fuera del motor (cambio
  `add-rustificacion-por-macrozona`).
- **BREAKING — Se retira `sensingPullIntervalSeconds`**: el intervalo de sensado pasa a ser el del pulso. Las lecturas
  que llegan fuera de un pulso con motor (arranque del nodo, secuencia `LECTURA` simple, pulso sin motor) sólo actualizan los
  valores.
- **Umbral de lectura vieja** recalculado sobre el pulso (`max(piso, intervalo + ventana de reintentos)`); sólo
  protege al riego, ya no decide si un nodo está fuera de servicio.
- **Diagnóstico nuevo.** Las reglas que usan el diagnóstico de IA sólo corren si el sector tiene un diagnóstico que
  todavía no se usó; al evaluarlas, queda marcado como usado en la base (sobrevive a un reinicio).
- **Se deja de inventar el diagnóstico.** La ingesta de telemetría no toca más los campos de diagnóstico del sector;
  sólo los cambia el alta de diagnósticos.
- **Pasada diaria del riel** a una hora local configurable, que se puede activar o desactivar. Es la misma pasada que
  la manual (`POST /api/pasadas`, sin cambios): un único riel, el mismo recorrido. Si no puede arrancar, deja una
  alerta y no se reintenta.
- **Historial.** En cada evaluación de pulso se registra el conjunto completo de inacción de cada sector, haya
  cambiado o no. Se retiran la deduplicación y el tope `yerbanalytics.historial.inaccion-intervalo-min`.
- **Frontend.** Configuración: un solo intervalo ("sensado y evaluación"), sin el de segundos ni el de evaluación;
  bloque "Pasada diaria del riel" (activa + hora). Demo Expo: en la tarjeta "Lectura" de Secuencias, "Leer sensores y ejecutar motor ahora" junto a "Leer sensores
  ahora" (sólo la zona de la secuencia); el
  resultado se ve en el panel general.

## Capabilities

### New Capabilities
- `pulso-sensado`: el pulso de sensado — franjas, pulso manual con o sin motor, reintentos, escalamiento por pulsos
  sin respuesta, evaluación del motor por zona y la lectura con motor de Demo Expo.
- `pasada-programada`: la pasada diaria del riel — hora configurable, activar/desactivar, coexistencia con la pasada
  manual y alerta cuando no puede arrancar.

### Modified Capabilities
- `sensing-cadence`: el pedido periódico pasa a ser el pulso; cambia la fórmula y el alcance del umbral de lectura
  vieja.
- `motor-reglas`: el motor sólo se dispara en el pulso (sin barrido), las reglas de diagnóstico consumen diagnósticos
  nuevos y el registro de inacción se escribe completo en cada pulso.
- `rustificacion-mediasombra`: se retira el disparo de la mediasombra por el barrido.
- `configuracion-persistencia`: se retiran `sensingPullIntervalSeconds` e `intervaloEvaluacionMinutos`; se agregan los
  campos de la pasada diaria; el umbral expuesto sigue la fórmula nueva.
- `configuracion-agronomica`: un solo editor de intervalo (el del pulso) y el editor de la pasada diaria.
- `diagnosticos-registro`: el diagnóstico del sector sólo lo cambia el alta y registra cuándo llegó.
- `dosificacion-insumo`: la dosificación por diagnóstico sólo se evalúa con un diagnóstico nuevo.
- `hardware-persistencia`: el estado técnico del nodo testigo sigue los pulsos sin respuesta, no el tiempo sin
  reportar.

## Impact

- **Backend:** `SensingScheduler` y `SensingCadence` (pasan a ser el planificador del pulso), `NurseryService`
  (`updateTelemetry` sólo evalúa en un pulso con motor y deja de inventar diagnóstico; estado "fuera de servicio"
  por la cuenta de pulsos), `NurseryWatchdog` (se elimina), `RuleOrchestrator` / `Rule` / traza (regla omitida por
  falta de diagnóstico nuevo), `ActionExecutor` (sin deduplicación ni tope), `DiagnosticoService`, `HardwareService`,
  `SectorEntity` y `ZonaEntity` (columnas nuevas), `ConfiguracionOperativaEntity` y su DTO, `PasadaRielService`
  (disparo programado), controlador nuevo (`/api/pulsos`), `MapaPermisos`, `application.properties`. Script de
  migración manual para las columnas retiradas.
- **Frontend:** tipos de configuración, `IntervalosForm`, validación, capa de datos (mock y http) del pulso, Demo
  Expo.
- **Sin cambios:** contrato MQTT y firmware (el nodo ya lee al arrancar y ante `LEER_AHORA`), simulador (ya
  responde `LEER_AHORA`), servicio de inferencia (ya consulta cada 10 s y diagnostica al terminar la pasada),
  contrato de cámara, recorrido y contrato del riel. La secuencia `LECTURA` suma el parámetro `ejecutarMotor`.
- **Riesgo operativo:** el riego reacciona como mucho una vez por pulso (hasta 4 h de demora para el déficit
  crítico), coherente con `reglas_v2` §1.2. Historial: ~7.800 filas de inacción por pulso con motor (~47.000 por día).
