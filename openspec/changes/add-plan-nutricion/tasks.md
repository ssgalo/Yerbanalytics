# Tasks: add-plan-nutricion

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-plan-nutricion`. Requiere `add-dosificacion-base`.

## 0. Previo
- [ ] 0.1 Confirmar D-1: usar `ZoneRuleEngine` (de `add-mediasombra-v2`; crearlo si todavía no está) y D-5 (primera ejecución).
- [x] 0.2 Verificado: no hay historial de lecturas; requiere `add-historial-lecturas`.

## 1. Modelo
- [ ] 1.1 Columnas en la zona: `intervalo_plan_nutricion`, `ultima_aplicacion_plan`, `proximo_plan_nutricion`.
- [ ] 1.2 Parámetros del catálogo (D-6) con test de rangos.

## 2. N-01
- [ ] 2.1 Test (tabla): vencido / no vencido / sin historial / idempotencia por ciclo.
- [ ] 2.2 `PlanNutricionRule` (patrón CLAUDE.md §6: `Rule`, `@Component`, flag ON/OFF, cooldown/bypass sólo vía simulador).
- [ ] 2.3 Genera `aplicacion_pendiente` por sector vía `ApplicationService`; test con 100 sectores.
- [ ] 2.4 Cierre de tanda ⇒ `ultima_aplicacion_plan`; test con un sector diferido.

## 3. N-02
- [ ] 3.1 Test: media de 6 lecturas, sin dato con < 6, "sin fertilizar en 5 días" cuenta plan y correctivas.
- [ ] 3.2 `CeBajaRule`; adelanta el plan; alerta única.

## 4. Frontend
- [ ] 4.1 Detalle de macro-zona: intervalo del plan y próxima aplicación; tests y lint.

## 5. Cierre
- [ ] 5.1 Inspector (`/reglas`) muestra N-01 y N-02; README; índice; suites verdes.

## 6. Control desde el simulador (requiere `add-control-demo-reglas`)
- [ ] 6.1 `PlanNutricionRule` y `CeBajaRule` como `DemoControllable` (apagado; N-01 cooldown `interval`; N-02 cooldown `no-fertilized` y bypass `samples`); tests.
- [ ] 6.2 Escenarios `NUTRITION_PLAN` y `LOW_EC` en el simulador con sus tests.
