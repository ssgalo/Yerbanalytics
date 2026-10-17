# Tasks: add-operacion-sin-internet

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-sin-internet`. Va al final: requiere `add-mediasombra-v2`
(O-02) y `add-dosificacion-base` (O-03).

## 0. Previo
- [ ] 0.1 Resolver D-4 (O-05) con el equipo.
- [x] 0.2 Leído `WeatherService`: vigencia utilizable = 4 × TTL (15 min) = 60 min; pasada esa, `getForecastSinEspera()` devuelve `null`.

## 1. Estado del pronóstico
- [ ] 1.1 Test: disponible / vencido / ausente con reloj inyectado.
- [ ] 1.2 `WeatherService.estado()`; `RuleContext.forecast()` ⇒ `null` si no está disponible.
- [ ] 1.3 Test: el estado `VENCIDO` aparece cuando el pronóstico supera `cache-ttl-ms × FACTOR_USABLE_VENCIDO` (60 min); sin parámetro nuevo.

## 2. Supuestos declarados
- [ ] 2.1 Test (contrato): toda regla con `supuestoSinPronostico()` no vacío registra el supuesto en la traza sin pronóstico.
- [ ] 2.2 `Rule.supuestoSinPronostico()` y registro en `Evaluacion`; Inspector lo muestra.
- [ ] 2.3 Tests por regla: R-03, `DespachoRiego`, M-02, P-D3, P-D4.

## 3. Alertas y O-04
- [ ] 3.1 Test: dos `INFO` al caer y volver; ninguna repetida.
- [ ] 3.2 Test de integración O-04 con reloj inyectado.

## 4. O-05
- [ ] 4.1 Según D-4: actualizar `reglas_v2.md` y el índice con "no aplicable" (a), o abrir un cambio nuevo (b).

## 5. Cierre
- [ ] 5.1 README; índice; suites verdes.

## 6. Control desde el simulador (requiere `add-control-demo-reglas`)
- [ ] 6.1 Escenario modificador `NO_FORECAST` en el simulador (usa `PUT /api/demo/weather/offline`); se combina con R-03, M-02, P-D3 y P-D4. Tests de cada combinación.
- [ ] 6.2 Las alertas de entrada/salida del modo se disparan también con el pronóstico simulado offline.
