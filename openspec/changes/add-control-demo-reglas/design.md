# Design: add-control-demo-reglas

`be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`; `sim/` = `Desarrollo/simulador/`.

## Estado verificado

| Pieza | Hoy |
|---|---|
| Modo | `yerbanalytics.demo.enabled` (`DemoProperties`); los controladores `/api/demo/**` llevan `@ConditionalOnProperty` ⇒ en `REAL` dan 404 |
| Overrides | `be/engine/overrides/`: `RuleOverride(enabled, cooldownMinutes, bypassed)`, `RuleOverrides` (interfaz de lectura, con `NONE`), `RuleOverridesStore` (en memoria, `@Component` sin condición; sólo el controlador lo escribe) |
| `Rule` | `cooldownKeys()` y `bypassKeys()` por defecto vacíos; `RuleContext.isBypassed/cooldownOverrideMinutes` |
| Listado | `DemoRulesController`: `orchestrator.getRules()` ⇒ sólo la cadena por sector; valida el nombre contra esa lista |
| Clima simulado | `DemoWeatherController` + `SimulatedWeatherInputDto(tempC, humedadRelPct, probLluviaPct, precipMm, uvIndex)`; `precipMm` **no se usa**; `horas = List.of()` |
| Simulador | `sim/src/components/RuleToggles.tsx` (genérico, sobre `/api/demo/rules`); `sim/src/ruleConditions.ts` (catálogo fijo y `calculateConditions`); `RuleAutomationSection.tsx` itera `RULE_CATALOG` |
| Cooldowns en cero | `PUT /api/demo/rules/cooldowns/zero` recorre `orchestrator.getRules()` |
| Invariantes | El backend no conoce al simulador; éste llama a `/api/demo/**` por su proxy `/backend/**` (CLAUDE.md §6.2) |

## Decisiones

### D-1 — `DemoControllable`
Interfaz mínima en `be/engine/overrides/`:
```java
public interface DemoControllable {
    String name(); String label(); RuleBranch branch(); int priority();
    default Set<String> cooldownKeys() { return Set.of(); }
    default Set<String> bypassKeys()   { return Set.of(); }
}
```
`Rule extends DemoControllable` (sin cambios para las reglas existentes). Los componentes que deciden fuera de la cadena la
implementan: reglas por zona (mediasombra, plan de nutrición, F-S2), `ApplicationPreconditions` (P-D1…P-D5),
`EfectividadService`, `ReadingValidationGuard` (S-03). Un `DemoControllableRegistry` (inyecta `List<DemoControllable>`)
es la única fuente que usa el controlador. Código en inglés, texto en español (CLAUDE.md §7).

### D-2 — Quien apaga consulta al store, por nombre
`RuleOverrides.isEnabled(name)` ya funciona para cualquier nombre. Cada componente fuera de la cadena lo consulta **antes
de actuar** y deja constancia en su traza/log. Para el Inspector, si el componente no es una `Rule` por sector, la
indicación de "apagada" se muestra en la vista de su propio cambio (no se fuerza a la traza por sector).

### D-3 — Cooldown y bypass: qué clave le toca a cada cosa
Regla de diseño para todos los cambios:
| Condición de la regla | Mecanismo | Clave |
|---|---|---|
| Espera/intervalo/latencia/ventana de tiempo (minutos u horas) | `cooldownKeys` (0 la apaga) | p. ej. `interval`, `latency`, `hold` |
| Guarda de estado o de franja horaria (sí/no) | `bypassKeys` | p. ej. `window`, `hardware`, `block` |
| Hora del día que define la acción (M-01) | `bypassKeys` `force-open` / `force-closed` | ver `add-mediasombra-v2` |
Así ninguna regla depende de cambiar la hora del sistema; se "accionan" en el siguiente pulso.

### D-4 — Clima simulado completo
`SimulatedWeatherInputDto` se amplía (todos opcionales con default, para no romper al simulador actual):
`horas` (24 marcas con `probLluviaPct`, `precipMm`, `tempC`), `uvMaxDia`. Si sólo llegan los campos viejos, el controlador
**genera** 24 marcas planas con esos valores (prob y mm de la hora para las próximas horas), lo que arregla R-03 sin tocar el
simulador. `WeatherForecast` suma `tempC` por hora y `uvMaxDia` (la misma extensión que pide `add-mediasombra-v2` D-4:
se implementa **una vez**, acá, y mediasombra la reutiliza).
`PUT /api/demo/weather/offline` {on} hace que `getForecast*()` devuelvan `null` (modo sin pronóstico) hasta que se apague o se
borre el clima simulado.

### D-5 — Simulador por escenarios
`ruleConditions.ts` se reorganiza en `RuleScenario` por regla:
```ts
interface RuleScenario { key: string; label: string; branch: string;
  needsCooldownReset: boolean; needsDiagnosis: boolean;
  apply(ctx: ScenarioContext): ScenarioPatch;   // telemetría, clima, diagnóstico, cooldowns/bypass a fijar
  conflictsWith?: string[] }
```
Un registro `SCENARIOS` reemplaza a `RULE_CATALOG` + las ramas de `calculateConditions`; los escenarios actuales (R-01…R-04,
sombreado, dosificación) se migran sin cambiar su comportamiento (los tests de `ruleConditions.test.ts` siguen verdes).
Cada cambio nuevo agrega su archivo `scenarios/<regla>.ts` + test. `DEFAULT_THRESHOLDS` deja de duplicar fábricas: si el
catálogo del backend no responde, se usan los mismos valores de la v2.

### D-6 — Sin reloj simulado (DECISIÓN)
Se evaluó un reloj global de demo (desplazar `Clock`). Se descarta: el reloj inyectado del vivero (`relojVivero`) lo usan
scheduler, historial, pulsos y TTL del pronóstico; moverlo en vivo rompe invariantes de todos. El tiempo se simula
con las claves de D-3.

### D-7 — Qué NO se simula desde el simulador
Acciones humanas (iniciar plan de rustificación, registrar una reparación o una revisión, cargar un tanque) se hacen con la
API real y su cuenta Servicio **sólo si** su permiso se le otorga en la matriz de desarrollo; no hay endpoint de demo para
saltarse un permiso. Cada cambio decide si el simulador necesita esa acción y, si sí, lo declara.

## Preguntas abiertas
- ¿`Rule extends DemoControllable` obliga a tocar las 13 reglas existentes? No: sólo cambia la declaración de la interfaz.
- ¿`cooldowns/zero` debe incluir a los `DemoControllable` que no son `Rule`? Sí (D-1).
