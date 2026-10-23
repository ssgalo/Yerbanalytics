# Tasks: add-plan-nutricion

**TDD estricto**, un commit por bloque. Rama: `feat/reglas-v2-plan-nutricion`. Requiere `add-dosificacion-base`.

> Los nombres de clases y métodos que esta spec escribía en español se implementaron en inglés (CLAUDE.md §7): `PlanNutricionRule` →
> `NutritionPlanRule`, `CeBajaRule` → `LowEcRule`, `ultima_aplicacion_plan` → `last_plan_application_ts`, etc. Ver «Decisiones tomadas» en `design.md`.

## 0. Previo
- [x] 0.1 Confirmar D-1: usar `ZoneRuleEngine` (se **crea** acá: `add-mediasombra-v2` se canceló) y D-5 (primera ejecución: una zona sin aplicaciones del plan se considera vencida).
- [x] 0.2 Verificado: no hay historial de lecturas; requiere `add-historial-lecturas`.

## 1. Modelo
- [x] 1.1 Columnas en la zona: `nutrition_plan_interval_days`, `last_plan_application_ts`, `nutrition_plan_due_ts` (el adelantamiento) y `nutrition_batch_start_ts` (la tanda en curso; la necesita D-2).
- [x] 1.2 Parámetros del catálogo (D-6, familia `NUTRICION`) con test de rangos (`ParametrosRealesTest`).

## 2. N-01
- [x] 2.1 Test (tabla): vencido / no vencido / sin historial / idempotencia por tanda (`NutritionScheduleTest`, `NutritionPlanRuleTest`).
- [x] 2.2 `NutritionPlanRule` (patrón CLAUDE.md §6: `ZoneRule`, `@Component`, flag ON/OFF, cooldown/bypass sólo vía simulador).
- [x] 2.3 Genera un pedido por sector vía `ApplicationService`; test con 100 sectores (`NutritionPlanServiceTest`).
- [x] 2.4 Cierre de tanda ⇒ `last_plan_application_ts`; test con un sector diferido.

## 3. N-02
- [x] 3.1 Test: media de 6 lecturas, sin dato con < 6, «sin fertilizar en 5 días» cuenta plan y correctivas (`LowEcRuleTest`).
- [x] 3.2 `LowEcRule`; adelanta el plan; alerta única.

## 4. Frontend
- [x] 4.1 Detalle de macro-zona: intervalo del plan y próxima aplicación (`features/nutrition/NutritionPlanCard`); tests y lint.

## 5. Cierre
- [x] 5.1 Inspector (`/reglas`) muestra N-01 y N-02 (esquema + traza de zona sumada a la del sector); README; índice; suites verdes.

## 6. Control desde el simulador (requiere `add-control-demo-reglas`)
- [x] 6.1 `NutritionPlanRule` y `LowEcRule` como `DemoControllable` (apagable; N-01 cooldown `interval`; N-02 cooldown `no-fertilized` y bypass `samples`); tests.
- [x] 6.2 Escenarios `NUTRITION_PLAN` y `LOW_EC` en el simulador con sus tests.
