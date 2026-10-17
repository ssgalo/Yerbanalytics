# Tasks: add-reglas-clorosis-estres-solar

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-clorosis-estres-solar`. Requiere `add-dosificacion-base`;
F-S2 requiere `add-mediasombra-v2`.

## 0. Previo
- [ ] 0.1 Confirmar D-5 (10 % de qué) y D-3 (si el plan de rustificación aún no está, dejar la condición apagada).
- [x] 0.2 Verificado: `SolicitudRiego` admite una regla de origen nueva; falta registrar las claves de F-S1 en los chequeos de guarda de `DespachoRiego` (tarea 4.1b).
- [ ] 0.3 Confirmar que `add-historial-lecturas` y `ZoneRuleEngine` (de `add-mediasombra-v2`) están integrados.

## 1. Historial de lecturas
- [ ] 1.1 Test de integración: `ReadingHistory.max/average` por ventana y franja horaria (10–16 h) con los valores que usa F-S1/F-S3.

## 2. Parámetros
- [ ] 2.1 Test y catálogo (D-7) con restricciones cruzadas; frontend.

## 3. Clorosis
- [ ] 3.1 Test (tabla) de las tres causas y bordes; `ChlorosisNonNutritionalRule`.
- [ ] 3.2 Test F-C2: con y sin causa, intervalo, rustificación.
- [ ] 3.3 `ChlorosisNutritionalRule`; pide a `ApplicationService`.

## 4. Estrés solar
- [ ] 4.1 Test F-S1 con los cuatro escenarios; `SolarStressRule` pide un riego por el despacho.
- [ ] 4.1b Test: `DespachoRiego` revalida las guardas de F-S1 (ventana, R-04, R-06) al abrir cada válvula, igual que para R-01; hoy sólo conoce las de R-01…R-06.
- [ ] 4.2 Test F-S3 y la zona gris; `SolarStressUnexplainedRule`.
- [ ] 4.3 Test F-S2: conteo por MZ, 10 %, 3 días, calendario intacto; `SolarStressWideRule` pide CERRADA a `add-mediasombra-v2`.

## 5. Cierre
- [ ] 5.1 Inspector; README; índice; suites verdes.

## 6. Control desde el simulador (requiere `add-control-demo-reglas`)
- [ ] 6.1 Las cinco reglas como `DemoControllable` (apagado; F-C2 cooldown `interval`; F-S1 bypass `window`; F-S1/F-S3 bypass `samples`; F-S2 cooldown `protection` y bypass `quorum`); tests de cada clave.
- [ ] 6.2 Escenarios `CHLOROSIS_NUTRITIONAL`, `CHLOROSIS_NON_NUTRITIONAL`, `SOLAR_STRESS`, `SOLAR_STRESS_WIDE` y `SOLAR_STRESS_UNEXPLAINED` con sus tests.
