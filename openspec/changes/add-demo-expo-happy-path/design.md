# Design: add-demo-expo-happy-path

## D1. Reutilizar escenarios, no duplicarlos
El modo simulador es `calculateConditions(['R01','SUPPLY','SHADING'], umbrales)`. Los umbrales salen del catálogo vivo
(`buildThresholds`), así que nada está hardcodeado. Valores resultantes con los de fábrica: `humSus` = punto medio entre
crítico (35) y riego (45) = **40 %** (riega R-01, no R-02); `temp` = **40 °C** (sector crítico, condición de
`SupplyRule`); clima con `uvIndex` = umbral UV + 1 (**8**) y 5 % de lluvia; diagnóstico *Clorosis / Alta / Alta* con
`conf` = confianza mínima + 5 (**75 %**).

## D2. "Cerrar" la mediasombra
`ShadingRule`, ante un pico UV, emite `MOVER_MEDIASOMBRA` con `targetPct` = `mediasombra.apertura-proteccion-uv` (30 % de
fábrica). Es un % de **apertura**: el firmware despliega la malla (la cierra) con cualquier valor por debajo de
`MEDIASOMBRA_UMBRAL_APERTURA_PCT` (50, espejado como `SHADE_DEPLOY_BELOW_OPENING_PCT` en `server/contract.ts`). El resultado
dice «Mediasombra cerrada (30 % de apertura)», o «abierta» si el objetivo es >= 50. Si ya estaba en esa posición la regla
emite un NOOP y el resultado lo dice (no actuó).

## D3. R-05 (ventana horaria)
`FueraDeVentanaRiegoRule` aborta el riego común fuera de 06:00–18:00. En modo simulador se agrega la clave de bypass
`window` por `PUT /api/demo/rules/FueraDeVentanaRiegoRule` (conservando las otras). En ESP32 real no se toca: se
respetan las reglas de producción.

## D4. Orden de la corrida
Cooldowns → bypass → clima → diagnóstico (flujo de captura de `captureFlow.ts`) → lectura **fijada** en la zona + emisión encendida → **un** pulso con motor → liberar la lectura y devolver la emisión a su estado previo (en `finally`). El motor decide con la PRIMERA lectura posterior al LEER_AHORA: una lectura publicada antes no cuenta, por eso se fija ("Lecturas fijadas") y la emisión responde con ella. Quedan activos a propósito el clima simulado, el bypass de R-05 y los cooldowns en 0 (el despacho del riego revalida guardas después de la decisión); la tarjeta de resultado lo informa. En ESP32 real se suelta además cualquier lectura fijada de la zona. El
diagnóstico va antes del pulso porque ése es el único momento en que decide el motor y `SupplyRule` actúa una vez por
diagnóstico nuevo. Un 409 (pulso en curso) se espera con `runPulses`.

## D5. Lectura del resultado
Las lecturas de traza que fallan (p. ej. 403) cuentan como sondeos fallidos y el mensaje final las nombra (`reglas.ver`).
El pulso responde 202 y el motor decide después, cuando la zona contesta. Se hace polling de la traza hasta que su `ts`
**cambie** respecto de la previa a la corrida (no se compara con el reloj: el simulador y el backend pueden no estar
sincronizados). `summarizeTrace` resume por rama: actuó si hay `ACTIVAR_VALVULA` / `ACTIVAR_BOMBA` /
`MOVER_MEDIASOMBRA`; si no, el motivo viene de la acción bloqueante, la nota de la regla o el estado de la traza.

## D6. Límite conocido del modo ESP32 real
La API pública no expone si el diagnóstico vigente ya fue usado por el motor (`diagnosis_usado_ts` es interno). El
pre-chequeo sólo verifica que haya un diagnóstico anómalo con confianza suficiente; la traza final confirma si
`SupplyRule` lo tomó (`OMITIDA_SIN_DIAGNOSTICO_NUEVO` si no).

## D7. Permisos del rol Servicio
Leer la traza y el catálogo (`reglas.ver`) y pedir pulsos/secuencias/pasadas (`pasadas.ver`, `pasadas.operar`) no estaba en la
matriz por defecto del rol Servicio: 403. Se agregan a `Permiso.matrizPorDefecto(SERVICIO)` (única excepción de backend de
este cambio, genérica para cuentas de integración) con la migración manual idempotente
`migracion-permisos-servicio-simulador.sql`. Si el catálogo igual da 403, el panel avisa en vez de caer en silencio a los
valores de fábrica (`loadThresholds`).

## D8. Una carga por vez — REEMPLAZADO por `add-cola-actuadores-nodo`
> Esta decisión (tres pulsos con motor, una regla ejecutora encendida por vez, espera de fin de carga y pausa de 5 s) fue un
> desvío para que el ESP32 no arrancara riego, insumo y mediasombra juntos y se reiniciara por brownout. **Ya no rige.** Ahora
> el nodo serializa las cargas con una cola propia (`openspec/changes/add-cola-actuadores-nodo/`, design D-13) y el happy path
> vuelve al diseño original: **un** pulso con las tres reglas habilitadas, sin encender/apagar reglas por paso (se borraron
> `STEP_ORDER`, `STEP_RULES`, `enableOnly`, `waitUntilFinished`, `expectedSeconds` y `STEP_WAIT_MS`). El diagnóstico se registra
> antes del pulso. La tarjeta sigue siendo por regla (traza) y suma el avance de cada acción leído del snapshot: válvula
> `En cola`/`Regando`/`Cerrada` y bomba `En cola`/`Dosificando`/`En espera` → «en cola» / «en curso» / «hecho»; la mediasombra
> queda «ordenada» (no tiene estado derivado del nodo).
