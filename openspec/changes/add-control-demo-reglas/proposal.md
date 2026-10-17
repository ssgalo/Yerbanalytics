# Change: add-control-demo-reglas

> Rama: `feat/reglas-v2-control-demo`. **Prerrequisito transversal**: define cómo se apaga y se acciona **cada regla nueva
> desde el simulador**, y arregla lo que hoy lo impide. Todos los demás cambios del índice dependen de éste.
> Índice: `reglas-v2-overview/README.md`.

## Why

Requisito del equipo: *toda regla nueva debe poder apagarse y accionarse desde el simulador, como las demás*
(CLAUDE.md §6: flag de activación, cooldowns y bypass sólo en `yerbanalytics.mode=SIMULATED`; todo control de demo vive en
`Desarrollo/simulador/`, nunca en el dashboard productivo).

Para cumplirlo hay que saber qué es "como las demás". Verificado en el código, hoy una regla se controla así:

1. **Apagar:** `RuleToggles` (simulador) lista `GET /api/demo/rules` y hace `PUT /api/demo/rules/{regla}` con `enabled`.
   El `RuleOrchestrator` consulta `overrides.isEnabled(rule.name())` y la omite con `OMITIDA_DESHABILITADA`.
2. **Cooldowns y guardas:** cada regla publica `cooldownKeys()` y `bypassKeys()`; la lógica los lee con
   `ctx.cooldownOverrideMinutes(NAME, key)` / `ctx.isBypassed(NAME, key)`, y los componentes que revalidan por la regla
   (`DespachoRiego`) usan `overrides.skipsGuard(...)`.
3. **Accionar:** `ruleConditions.ts` del simulador **no es genérico**: tiene un `RULE_CATALOG` fijo (`R01`, `R02`, `R04`,
   `R03`, `SHADING`, `SUPPLY`) y una función `calculateConditions` que, por regla, arma la telemetría, el clima simulado y
   el diagnóstico previo que la hacen disparar en el siguiente pulso. Una regla nueva que no figure ahí **no se puede
   accionar** desde la UI.

Y verificado, hay cuatro problemas que lo impiden:

- **(a) Reglas fuera de la cadena por sector no aparecen.** `DemoRulesController.list()` recorre
  `orchestrator.getRules()`, o sea sólo las `Rule` que corren por sector. Las reglas por macro-zona (mediasombra, plan de
  nutrición, F-S2…), las precondiciones de la aplicación y la efectividad no son `Rule` de esa cadena: **no tendrían botón
  de apagado ni de cooldown**, y `PUT /api/demo/rules/{nombre}` respondería 404.
- **(b) R-03 no se puede accionar hoy.** `DemoWeatherController.set` recibe `precipMm` pero arma el pronóstico sin
  `horas` (`new WeatherForecast(..., List.of())`); `PosponerPorLluviaRule` usa `forecast.lluviaProxima(...)`, que sin horas
  devuelve probabilidad y milímetros `null`, y `lluviaPospone` queda en falso. *Hallazgo por lectura de código, no ejecutado.*
- **(c) El clima simulado no sirve para las reglas nuevas.** Sólo tiene temperatura, humedad, probabilidad, mm y UV de
  *la hora actual*. M-02 necesita temperatura por hora y UV máximo del día; P-D3/P-D4 la lluvia de 6 h; O-01…O-03 un modo
  "sin pronóstico".
- **(d) El simulador tiene números de la v1 cableados:** `DEFAULT_THRESHOLDS['diagnostico.confianza-minima'] = 85` y un
  estado crítico forzado (`CRITICAL_AIR_TEMP`) pensado para `SupplyRule`, que se retira en `add-dosificacion-base`.

## What Changes

- **Contrato `DemoControllable`** (backend): lo que el simulador necesita saber de *cualquier* pieza de decisión
  (`name`, `label`, `branch`, `priority`, `cooldownKeys`, `bypassKeys`). `Rule` lo extiende; los componentes por zona y
  servicios lo implementan. `DemoRulesController` lista y actualiza **todos** los `DemoControllable`, no sólo la cadena.
- **Regla de oro, verificable con un test:** todo `DemoControllable` aparece en `GET /api/demo/rules`, se puede apagar y,
  apagado, no actúa y deja `OMITIDA_DESHABILITADA` (o su equivalente) en la traza.
- **Clima simulado completo:** `POST /api/demo/weather` genera marcas horarias (probabilidad y mm por hora, temperatura por
  hora, UV máximo del día) y se agrega `PUT /api/demo/weather/offline` para simular "sin pronóstico". Arregla (b) y (c).
- **Simulador por escenarios:** `ruleConditions.ts` pasa de un `switch` fijo a un registro de **escenarios por regla**
  (cada cambio agrega el suyo, con su test). Se actualizan los valores cableados de la v1 (d).
- **Lista de verificación común** ("Control desde el simulador") que cada cambio del índice cumple en su `tasks.md` y su
  spec, con los nombres de sus reglas y las claves de cooldown/bypass.

## Out of scope

- Cualquier control de demo en el dashboard productivo (prohibido por CLAUDE.md §4/§6.2).
- Reloj simulado global: se descarta (ver design D-6); el tiempo se controla por cooldown/bypass de cada regla.
