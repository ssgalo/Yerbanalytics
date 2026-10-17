# Tasks: add-control-demo-reglas

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-control-demo`. Va **primero** (con `align-reglas-existentes-v2`):
todos los demás cambios la usan. Suites: `./mvnw test` y, en `Desarrollo/simulador`, `npm test && npm run typecheck`.

## 1. `DemoControllable` (backend)
- [ ] 1.1 Test: `DemoControllableRegistry` lista las 13 `Rule` actuales y un componente de prueba que no es `Rule`.
- [ ] 1.2 Interfaz `DemoControllable`; `Rule extends DemoControllable`; registro.
- [ ] 1.3 Test (MockMvc, modo demo): `GET /api/demo/rules` incluye controlables que no son `Rule`; `PUT` a uno de ellos funciona; nombre desconocido ⇒ 404.
- [ ] 1.4 `DemoRulesController` usa el registro; valida las claves de cooldown/bypass contra las declaradas (400 si no).
- [ ] 1.5 Test: `PUT /api/demo/rules/cooldowns/zero` alcanza a todos los controlables.
- [ ] 1.6 Test (modo `REAL`): las rutas dan 404.
- [ ] 1.7 Test de arquitectura: toda clase `@Component` que implemente `Rule` o `DemoControllable` declara `label()` no vacío.

## 2. Clima simulado
- [ ] 2.1 Test: con sólo los campos viejos, `POST /api/demo/weather` genera 24 marcas con prob y mm; `lluviaProxima(ahora, 4)` devuelve 80 % / ≥ 5 mm.
- [ ] 2.2 Test de regresión: `PosponerPorLluviaRule` pospone con ese clima (hoy no; ver diseño (b)).
- [ ] 2.3 `WeatherForecast` suma temperatura por hora y `uvMaxDia`; `SimulatedWeatherInputDto` con campos opcionales.
- [ ] 2.4 `PUT /api/demo/weather/offline`; test de que `getForecast*()` devuelve `null` y que se limpia con `DELETE`.
- [ ] 2.5 Avisar a `add-mediasombra-v2` que su tarea 1 (pronóstico horario) se resuelve acá.

## 3. Simulador: escenarios
- [ ] 3.1 Test: migrar `RULE_CATALOG` y `calculateConditions` a `SCENARIOS` sin cambiar los resultados de `ruleConditions.test.ts`.
- [ ] 3.2 Interfaz `RuleScenario` y registro; `RuleAutomationSection` itera el registro.
- [ ] 3.3 Panel de reglas: lo que figura en `GET /api/demo/rules` y no tiene escenario se ve en gris ("sin escenario").
- [ ] 3.4 `DEFAULT_THRESHOLDS`: confianza 70; quitar `CRITICAL_AIR_TEMP` cuando se retire `SupplyRule` (queda anotado en `add-dosificacion-base`).
- [ ] 3.5 API del simulador: `setOfflineForecast`; control en la sección de clima.

## 4. Lista de verificación (se copia en cada cambio)
- [ ] 4.1 Documentar en `reglas-v2-overview/README.md` la lista "Control desde el simulador" (ver índice) y exigirla en revisión de PR.
- [ ] 4.2 Documentar en `Desarrollo/simulador/README.md` cómo agregar un escenario.
- [ ] 4.3 CLAUDE.md §6: referenciar `DemoControllable` en el patrón de reglas nuevas.

## 5. Cierre
- [ ] 5.1 Suites verdes (backend y simulador), índice.
