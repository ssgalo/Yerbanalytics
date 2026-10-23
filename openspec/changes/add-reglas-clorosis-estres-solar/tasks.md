# Tasks: add-reglas-clorosis-estres-solar

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-clorosis-estres-solar`. **F-S2 (bloque 4.3) no se implementó**: depende de `add-mediasombra-v2` (cancelado), ver el design. Requiere `add-dosificacion-base`;
F-S2 requiere `add-mediasombra-v2`.

## 0. Previo
- [x] 0.1 D-5 queda sin objeto (F-S2 no se implementó). D-3: la rustificación se resuelve con el plan **global** detrás de `HardeningPhase`, no apagada (ver el design).
- [x] 0.2 Verificado: `SolicitudRiego` admite una regla de origen nueva; falta registrar las claves de F-S1 en los chequeos de guarda de `DespachoRiego` (tarea 4.1b).
- [x] 0.3 `add-historial-lecturas` y `add-dosificacion-base` están integrados. El `ZoneRuleEngine` no hace falta: las cuatro reglas implementadas son de sector.

## 1. Historial de lecturas
- [x] 1.1 `ReadingHistory.max/average` por ventana y franja (10–16 h) ya tenía sus tests (`add-historial-lecturas`); `SolarExposureTest` fija la ventana y la franja que piden F-S1/F-S3, y `ChlorosisSolarFlowIntegrationTest` las recorre contra la base.

## 2. Parámetros
- [x] 2.1 Test y catálogo (D-7): familias `CLOROSIS` y `ESTRES` y las restricciones cruzadas luz baja < alta y temperatura baja < alta; el frontend los muestra en Parámetros. `estres.pct-sectores-mz` y `estres.dias-proteccion` no se crearon (son de F-S2).

## 3. Clorosis
- [x] 3.1 Test de las tres causas y bordes; `ChlorosisNonNutritionalRule`.
- [x] 3.2 Test F-C2: con y sin causa (lo garantiza la cadena: `ChlorosisSolarFlowIntegrationTest`), intervalo (lo respeta P-D2 del `ApplicationService`) y rustificación.
- [x] 3.3 `ChlorosisNutritionalRule`; pide a `ApplicationService`.

## 4. Estrés solar
- [x] 4.1 Test F-S1 con los cuatro escenarios; `SolarStressRule` pide un riego por el despacho.
- [x] 4.1b Test: `DespachoRiego` ya trataba toda solicitud que no es de R-02 como riego común (ventana, R-06, lluvia y ciclo); se sumó que el bypass `window` de F-S1 también lo salte el despacho. R-04 no hace falta: la humedad de F-S1 es < 55 % y R-04 actúa sobre el saturado.
- [x] 4.2 Test F-S3 y la zona gris; `SolarStressUnexplainedRule`.
- [ ] 4.3 ~~Test F-S2: conteo por MZ, 10 %, 3 días, calendario intacto; `SolarStressWideRule` pide CERRADA a `add-mediasombra-v2`.~~ **Bloqueado**: `add-mediasombra-v2` se canceló y no hay quien retenga la malla cerrada de 11 a 16 h (ver el design).

## 5. Cierre
- [x] 5.1 Inspector; README; índice; suites verdes.

## 6. Control desde el simulador (requiere `add-control-demo-reglas`)
- [x] 6.1 Las **cuatro** reglas implementadas como `DemoControllable` (apagado; F-S1 bypass `window` y `samples`; F-S3 bypass `samples`); tests de cada clave. F-C2 no declara el cooldown `interval` (lo respeta P-D2, cuyo cooldown es `interval-fertilizer` de `ApplicationPreconditions`); F-S2 y sus llaves `protection` y `quorum` quedan sin hacer.
- [x] 6.2 Escenarios `CHLOROSIS_NUTRITIONAL`, `CHLOROSIS_NON_NUTRITIONAL`, `SOLAR_STRESS` y `SOLAR_STRESS_UNEXPLAINED` con sus tests. `SOLAR_STRESS_WIDE` queda sin hacer (es de F-S2).
