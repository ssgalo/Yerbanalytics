# Design: add-catalogo-umbrales-reglas

Abreviaturas: `be/` = `Desarrollo/backend/src/main/java/com/yerbanalytics/backend/`;
`res/` = `Desarrollo/backend/src/main/resources/`; `fe/` = `Desarrollo/frontend/src/`.

## Context

**Cómo se evalúa hoy.** `RuleOrchestrator.evaluate(ctx)` recorre las reglas por prioridad y corta
por rama según las acciones bloqueantes (`be/engine/RuleOrchestrator.java:54-101`). Devuelve sólo
`List<RuleAction>`: no queda registro de qué reglas se saltearon ni de qué comparó cada una. Corre
para los 100 sectores de una zona con cada telemetría (`be/service/NurseryService.java:465-511`) y
para los 600 cada 5 min desde el watchdog (`be/service/NurseryWatchdog.java:106-167`).

**De dónde sale cada umbral hoy** (inventario completo de lo que las reglas comparan):

| Regla | Qué compara | Origen actual |
|---|---|---|
| `StaleSensorRule` | antigüedad de la lectura > 30 s | `@Value stale-threshold-ms` (`application.properties:69`), calculado **fuera** de la regla (`NurseryService.java:531-533`, `NurseryWatchdog.java:133-134`) |
| `WeatherOverrideRule` | prob. lluvia ≥ 60 % | `@Value` (`rules/WeatherOverrideRule.java:40-42`) |
| `DailyVolumeLimitRule` | riegos en 24 h ≥ 2 | literal (`rules/DailyVolumeLimitRule.java:61`) |
| `DailyDoseLimitRule` | dosis en 24 h > 0 | literal (`rules/DailyDoseLimitRule.java:80`) |
| `IrrigationRule` | `humSus` < 42 %; riegos en 24 h > 0; tiempo máx. 120 s | `idealMin` de `umbral_metrica` (`ConfiguracionService.java:300-307`); literal (`IrrigationRule.java:98`); `configuracion_operativa` (`IrrigationRule.java:108-111`) |
| `SupplyRule` | estado == critical; confianza ≥ 85 % | literales (`rules/SupplyRule.java:34,62`) |
| `ShadingRule` | UV ≥ 7; apertura protectora 30 %; apertura máx. 100 % | `@Value` (`rules/ShadingRule.java:54`); literal (`:90`); `configuracion_operativa` (`:84`) |
| `ManualLockRule`, `FollowUpRule` | sin umbrales | — |

**Restricciones que condicionan el diseño:**

- `spring.sql.init.mode=never` (`application.properties:55`): `data.sql` **no** se aplica solo. Los
  valores de fábrica no pueden depender de un seed; tienen que vivir en código, como ya pasa con
  `ConfiguracionService.defaultOperativa()` (`ConfiguracionService.java:349-361`).
- `ddl-auto=update` (`application.properties:42`) no baja columnas. Las de
  `configuracion_operativa` son `nullable=false` (`model/ConfiguracionOperativaEntity.java:28-40`):
  si se sacan del mapeo y no se bajan de la tabla, el primer `INSERT` de esa fila falla.
- `visualize-rule-dag` fijó como non-goal *"no se serializarán grafos en PostgreSQL"*
  (`openspec/changes/visualize-rule-dag/proposal.md`). Este diseño lo respeta.
- Invariantes del repo: backend sin modos; la UI sólo habla con `DataRepository` y funciona en
  `mock` y `http`; CSS Modules + tokens.

## Goals / Non-Goals

**Goals**
- Una sola definición y un solo valor por parámetro, aunque lo usen varias reglas.
- Que la relación regla → parámetros sea **verificable**, no documentación.
- Ver, por evaluación, qué recibió cada regla contra qué umbral, sin costo de base de datos.
- Tipos de valor suficientes para todos los parámetros de riego de §11.
- Sumar una regla nueva = una clase de regla + (si hace falta) una entrada en el catálogo. Cero
  migraciones, cero cambios en el frontend.

**Non-Goals** — ver `proposal.md` → *Out of scope*.

## Decisions

### D1 — Definiciones en código, overrides en la base

`DefinicionParametro` es una interface; cada familia la implementa con un enum
(`ParametrosSeguridad`, `ParametrosRiego`, `ParametrosInsumo`, `ParametrosMediasombra`,
`ParametrosDiagnostico`) en `be/engine/parametros/`. Cada entrada declara:

| Campo | Ejemplo |
|---|---|
| `clave()` | `riego.umbral-humedad` (estable, kebab-case, prefijo = familia) |
| `etiqueta()` / `descripcion()` | "Umbral de riego (humedad de sustrato)" |
| `tipo()` | `NUMERO`, `ENTERO`, `HORA`, `VENTANA_HORARIA` |
| `unidad()` | `%`, `°C`, `L`, `L/punto`, `mL`, `mm`, `h`, `min`, `s`, `sectores`, `riegos`, `índice` |
| `fabrica()` | `42` / `06:00-18:00` |
| `min()` / `max()` / `decimales()` | `35` / `60` / `0` (vacío para ventanas) |
| `refSpec()` | `reglas_v2 §11 Riego` |

`CatalogoParametros` (bean) junta todos los enums y **falla al arrancar** si hay claves
duplicadas, un fábrica fuera de su propio rango, o una regla que declara una clave que no existe.

La tabla `parametro_regla(clave PK, valor TEXT NOT NULL, updated_by, updated_ts)` guarda sólo
overrides, en formato canónico por tipo (`"42"`, `"0.2"`, `"06:00"`, `"06:00-18:00"`). Restablecer
a fábrica = borrar la fila.

**Alternativas descartadas:**
- *Metadatos en la base, sembrados por `data.sql`*: el seed no corre solo (`sql.init.mode=never`),
  y cada parámetro nuevo exigiría una migración. Además rompe el criterio ya tomado en
  `add-configuracion-agronomica` D1 (*los metadatos no se duplican en la base*).
- *Más columnas en `configuracion_operativa`*: una columna por parámetro, `ALTER` por regla nueva,
  y sin lugar para tipo, rango ni unidad.
- *Un único enum gigante*: con ~80 parámetros a futuro es un archivo de mil líneas que todos tocan.
  Un enum por familia escala igual y reparte los conflictos.

### D2 — Tipos de valor y restricciones cruzadas

`ValorParametro` es una interface sellada: `Numero(double)`, `Hora(LocalTime)`,
`VentanaHoraria(LocalTime desde, LocalTime hasta)`. `ENTERO` es `Numero` con `decimales = 0`
validado. Horas, minutos y milímetros son `NUMERO` con unidad: no merecen tipo propio porque se
comparan igual. Una ventana con `desde > hasta` cruza la medianoche (`contiene()` lo resuelve), lo
que permite expresar "fuera de 06–18" sin tipo extra.

Las restricciones que involucran a varios parámetros (`crítico < umbral < objetivo`,
`bloqueo saturación ≤ alerta saturación`) se declaran como `RestriccionCruzada(claves, predicado,
mensaje)` en el enum de la familia y se validan **sobre el conjunto resultante** de cada guardado.

**Cobertura de riego (lo que el cambio siguiente necesita)** — se prueba con una familia de test,
no se publica:

| Parámetro §11 / §6.3 | Tipo | Unidad | Fábrica | Rango |
|---|---|---|---|---|
| Umbral de riego | NUMERO | % | 45 | 35–60 |
| Humedad objetivo | NUMERO | % | 65 | 55–75 |
| Umbral crítico | NUMERO | % | 35 | 25–40 |
| Bloqueo por saturación / alerta | NUMERO ×2 | % | 75 / 80 | 65–85 |
| Litros por punto de déficit | NUMERO (2 dec.) | L/punto | 0,2 | 0,1–0,5 |
| Volumen máximo por evento | NUMERO | L | 6 | 3–10 |
| Caudal calibrado del emisor (§1.2) | NUMERO | L/h | 30 | — (a definir con el aforo) |
| Sectores a la vez por MZ | ENTERO | sectores | 10 | 1–100 |
| Ventana de riego normal | VENTANA_HORARIA | — | 06:00–18:00 | — |
| Lluvia: probabilidad / mm / ventana | NUMERO ×3 | % / mm / h | 70 / 5 / 4 | 50–95 / 2–20 / 2–12 |
| Pausa tras aplicación | NUMERO | h | 6 | 2–24 |
| Riego exceptuado del bloqueo S-06 | NUMERO | h | 12 | 6–24 |

Restricciones cruzadas del set: `crítico < umbral < objetivo` y `bloqueo ≤ alerta`.

**Descartado:** un tipo `LISTA_VENTANAS` (lo pide P-D3, "07–10 y 17–19"). Es de dosificación, no de
riego; se suma cuando llegue esa familia. La interface sellada lo admite sin tocar lo existente.

### D3 — La regla declara sus parámetros y sólo puede leer esos

```java
public interface Rule {
    // ...lo de hoy (priority, name, label, branch)...
    default List<DefinicionParametro> parametros() { return List.of(); }
    List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev);
}
```

`Evaluacion` es el objeto por-regla-por-ciclo que arma el orquestador. Expone:

- `double numero(DefinicionParametro p)`, `LocalTime hora(p)`, `VentanaHoraria ventana(p)`:
  lectura del valor vigente. **Lanza `ParametroNoDeclaradoException`** si `p` no está en
  `parametros()` de la regla.
- `boolean comparar(String etiqueta, Double recibido, Operador op, DefinicionParametro umbral)`:
  compara contra el parámetro **y registra** la comparación.
- `boolean compararFijo(String etiqueta, Object recibido, Operador op, Object umbral)`: para
  condiciones que no son configurables (p. ej. `estado == critical` de `SupplyRule:62`). Queda en la
  traza marcada como `configurable = false`, así se ve que existe y que no se edita.

La regla nunca construye su propia lógica de comparación fuera de `Evaluacion`: el test de cada
regla verifica que la traza tiene las comparaciones esperadas.

Por qué así:
- **Garantía, no convención.** Si la regla lee algo que no declaró, el test revienta. La vista
  "por regla" se arma de `parametros()`, así que es exacta por construcción.
- **Compartido sin duplicar.** Dos reglas que declaran `ParametrosRiego.UMBRAL_HUMEDAD` apuntan a la
  misma definición y al mismo valor.

**Alternativas descartadas:**
- *Meter la `Evaluacion` en `RuleContext`*: `RuleContext` es un snapshot inmutable y compartido por
  todas las reglas del ciclo (`be/engine/RuleContext.java:13-19`). La traza es por regla y mutable;
  mezclarlas obliga a copiar el record por regla.
- *Que `evaluate` devuelva `RuleResult(acciones, comparaciones)`*: la regla tendría que armar a mano
  la lista de comparaciones además de decidir, y nada impide que se olvide de una. Con `comparar()`
  decidir y registrar son el mismo gesto.
- *Anotaciones (`@UsaParametro("...")`) y reflexión*: claves como strings sin chequeo de tipo, y
  nada impide leer un `@Value` por afuera.

**Migración incremental:** durante la transición la interface conserva un
`default evaluate(ctx, ev) { return evaluate(ctx); }` y el `evaluate(ctx)` de hoy; cada regla se
mueve en su propia tanda con su test. La última tanda borra la firma vieja (ver `tasks.md`).

### D4 — Valores vigentes: snapshot por ciclo, cache invalidado al guardar

`CatalogoParametrosService.vigentes()` devuelve un `ParametrosVigentes` inmutable (mapa
clave → valor) cacheado en un `volatile`, igual que `effectiveSpecsCache` y `operativaCache`
(`ConfiguracionService.java:47-50`), e invalidado al guardar. El orquestador lo toma **una vez por
evaluación de sector** y se lo pasa a cada `Evaluacion`. Consecuencias:

- Costo en el camino caliente: un acceso a un campo `volatile`. Cero consultas.
- Todas las reglas de un ciclo ven los mismos valores aunque alguien guarde a mitad de barrido.
- La traza guarda **el valor de umbral usado**, no una referencia: si después se cambia el
  parámetro, la traza vieja sigue mostrando contra qué se comparó.

### D5 — Traza: modelo y ciclo de vida

```
TrazaEvaluacion(sectorId, zonaId, origen, ts, parametrosHash, List<TrazaRegla>)
TrazaRegla(ruleId, rama, prioridad, estado, List<Comparacion>, List<AccionTrazada>, bloqueadaPor?)
  estado ∈ { EVALUADA, OMITIDA_RAMA_BLOQUEADA, NO_ALCANZADA }
Comparacion(etiqueta, clave?, recibido, operador, umbral, unidad, configurable, resultado)
  resultado ∈ { CUMPLE, NO_CUMPLE, SIN_DATO }
AccionTrazada(tipo, motivo)
OrigenEvaluacion ∈ { TELEMETRIA, BARRIDO }
```

`OMITIDA_RAMA_BLOQUEADA` lleva `bloqueadaPor` (la regla que cortó la rama) y `NO_ALCANZADA` es lo
que queda después de un `ABORT_ALL`. Eso es exactamente lo que hoy el frontend adivina por regex.

**Dónde vive:** `TrazaEvaluacionStore`, un `ConcurrentHashMap<sectorId, EnumMap<Origen, Traza>>` en
memoria. Se guarda **la última por sector y por origen**. Telemetría (hilo MQTT) y barrido (hilo
del scheduler) escriben en paralelo; los registros son inmutables, así que alcanza con el
reemplazo atómico de la entrada.

**Por qué por origen y no sólo "la última":** el barrido evalúa con `metrics = List.of()`
(`NurseryWatchdog.java:146`). Si guardáramos sólo la última, cada 5 min el barrido taparía la traza
de la telemetría con "humedad: sin dato", y el inspector nunca mostraría el caso que importa.

**Costo:**
- CPU: por regla, una `Evaluacion` y 1–3 `Comparacion`. Barrido = 600 sectores × 9 reglas ≈ 5 400
  objetos chicos cada 5 min. Despreciable al lado de lo que ya se hace: cada `NOOP_INFO` es hoy un
  `INSERT` en `historial_evento` (`be/engine/ActionExecutor.java:107-118`).
- Memoria: 600 sectores × 2 orígenes × ~2 KB ≈ 2,5 MB. Con 40 reglas, ~10 MB. Acotado por la
  cantidad de sectores, no por el tiempo.
- Base: **cero**. No se toca `historial_evento`.

**Qué se pierde:** la traza no sobrevive a un reinicio. Es diagnóstico en vivo, no auditoría; el
barrido la regenera en ≤ 5 min. La auditoría sigue siendo `historial_evento`.

**Alternativas descartadas:**
- *Persistir cada traza*: ~5 400 filas por barrido, encima de las que ya genera el Registro de
  Inacción, y contra el non-goal de `visualize-rule-dag`.
- *Ring buffer de N trazas por sector*: multiplica la memoria por N para un caso (navegar trazas
  viejas) que hoy nadie pidió. Es la Decisión abierta DA-4.
- *Columna `ciclo_id` en `historial_evento` para cruzar traza e historial*: requiere persistir la
  traza para que el cruce sirva en ciclos viejos. Mismo problema.

### D6 — API

Todo bajo `/api/rules`, el prefijo que ya usa `RuleEngineSchemaController`.

**`GET /api/rules/parametros`** — catálogo normalizado: los parámetros van **una vez** y las reglas
los referencian por clave.

```json
{
  "reglas": [
    { "id": "IrrigationRule", "label": "💦 Riego", "rama": "RIEGO", "prioridad": 10,
      "parametros": ["riego.umbral-humedad", "riego.max-riegos-24h-sector", "riego.tiempo-max-apertura"] }
  ],
  "parametros": [
    { "clave": "riego.umbral-humedad", "etiqueta": "Umbral de riego (humedad de sustrato)",
      "descripcion": "…", "familia": "RIEGO", "tipo": "NUMERO", "unidad": "%",
      "valor": "42", "fabrica": "42", "min": 35, "max": 60, "decimales": 0,
      "refSpec": "reglas_v2 §5 R-01", "modificado": false,
      "usadoPor": ["IrrigationRule"], "updatedBy": null, "updatedTs": null }
  ]
}
```

`usadoPor` se deriva en el servidor; el cliente no tiene que invertir el índice.

**`PUT /api/rules/parametros`** — edición en lote, todo o nada:
`{ "cambios": [ { "clave": "riego.umbral-humedad", "valor": "40" }, { "clave": "…", "valor": null } ] }`.
`valor: null` restablece fábrica. Valida tipo, rango y restricciones cruzadas sobre el conjunto
resultante; si algo falla, **400** con `{ "errores": [ { "clave", "mensaje" } ] }` y no persiste
nada. Si pasa: persiste, audita con `X-Usuario` (mismo patrón que `ConfiguracionController`),
invalida el cache, registra un evento "Configuración" en el historial con las claves cambiadas, y
devuelve el catálogo actualizado.

**`GET /api/rules/evaluaciones/{sectorId}?origen=TELEMETRIA|BARRIDO`** — la última traza de ese
origen; sin `origen`, la más reciente de las dos. **404** si el sector no existe, **204** si todavía
no se evaluó desde el arranque.

**`GET /api/rules/schema`** — `RuleNodeDto` (`be/dto/RuleNodeDto.java:13-19`) suma
`List<String> parametros` (vacía en los nodos especiales). Cambio aditivo.

### D7 — Convivencia con lo existente (sin dos lugares editando lo mismo)

Criterio: **va al catálogo todo lo que una regla compara; se queda donde está lo que no.**

| Hoy | Destino | Notas |
|---|---|---|
| `umbral_metrica` (bandas ideal/warn/crit) | **Se queda** en Configuración | Son bandas de *estado* de la métrica, no umbrales de regla. v2 también las separa: el óptimo de humedad es 50–70 (§3) y el umbral de riego es 45 (§5). |
| `idealMin` de `humSus` como umbral de riego | **Catálogo** `riego.umbral-humedad`, fábrica 42 | Se revierte `add-configuracion-agronomica` D5. Ver DA-1. |
| `configuracion_operativa.riegoTiempoMaxSeg` | **Catálogo** `riego.tiempo-max-apertura` (s) | Sale del DTO, de la entidad y de `LimitesActuadoresForm`. |
| `configuracion_operativa.mediasombraAperturaMaxPct` | **Catálogo** `mediasombra.apertura-maxima` (%) | `ConfiguracionService.validarRustificacion` (`:228-251`) y `RustificacionPlanForm` pasan a leerlo del catálogo. |
| `riegoVolMaxDiarioMl`, `insumoDosisMax24hMl` | **Se quedan**, sin cambios | Ninguna regla los compara: sólo aparecen en textos (`IrrigationRule.java:103`, `DailyDoseLimitRule.java:85`). v2 elimina los límites diarios; los barren los cambios de riego y dosificación. Se documenta en el form que no intervienen en decisiones. |
| `seguimientoLatenciaMin/DeltaMin`, intervalos | **Se quedan** | Los consume `HistorialService`/watchdog, no una regla. |
| `stale-threshold-ms` | **Catálogo** `seguridad.antiguedad-max-lectura` (s, fábrica 30) | `StaleSensorRule` calcula la antigüedad ella misma con `ctx.zona().getLastReadingTime()` y `ctx.now()`; sale `RuleContext.sensorStale`. La vista de `NurseryService` lee el mismo parámetro. |
| `rain-threshold-pct`, `uv-threshold` | **Catálogo** (fábrica 60 y 7) | Se borran de `application.properties`. |
| `capturas.confianza-minima` + literal 85 de `SupplyRule` | **Catálogo** `diagnostico.confianza-minima` (fábrica 85) | Un solo valor para `SupplyRule` y `DiagnosticoService.esConcluyente` (`DiagnosticoService.java:133-135`). Ver DA-5. |
| Literales 2 / 1 / 1 / 30 | **Catálogo** (`riego.max-riegos-24h`, `riego.max-riegos-24h-sector`, `insumo.max-dosis-24h`, `mediasombra.apertura-proteccion-uv`) | Son dos parámetros de riegos/24 h y no uno: hoy tienen valores y efectos distintos (`DailyVolumeLimitRule:61` vs `IrrigationRule:98`); unificarlos cambiaría qué regla corta la rama. Ambos desaparecen con R-01. |
| `sowing-date-iso` | **Se queda** como property | No es un umbral sino un dato del lote, y v2 lo reemplaza por el inicio del plan por MZ (§7). Excepción explícita: `ShadingRule` conserva ese único `@Value`. |
| `action-cooldown-minutes` | **No se toca** | No lo lee ninguna clase (`diferencias…md` §5.11), pero la spec viva `motor-reglas` → *Idempotencia mediante cooldowns* lo exige. Es una deuda de implementación, no un umbral de regla vigente; cuando se implemente, entra al catálogo. |

**Migración de datos** — `res/migracion-catalogo-parametros.sql`, manual, mismo formato que
`migracion-quitar-simulador.sql`: copia `riego_tiempo_max_seg` y `mediasombra_apertura_max_pct` a
`parametro_regla` **sólo si difieren de fábrica**, y después baja las dos columnas. Con la base
nueva o sin correr el script, el backend arranca igual (los valores de fábrica son los mismos que
el seed); sólo falla el `INSERT` de una fila operativa nueva, y eso queda dicho en el encabezado
del script y en el README del backend.

**La página Configuración** conserva bandas, límites que no son de reglas, rustificación,
seguimiento e intervalos. Suma un enlace: *"Los umbrales que usan las reglas del motor se editan en
Motor de reglas"*.

### D8 — UI: sección "Motor de reglas"

Ruta `/reglas` en el `Sidebar` (debajo de Configuración), feature `fe/features/reglas/`, dos
pestañas: **Parámetros** e **Inspector**.

**Datos:** `DataRepository` suma `getCatalogoReglas()`, `saveParametros(cambios)`,
`getRuleSchema()` y `getTrazaEvaluacion(sectorId, origen?)`. `useRuleEngineSchema` deja de hacer
`fetch` directo y pasa por el repositorio. El mock arma un catálogo determinístico (las 9 reglas y
sus parámetros) y una traza derivada de las lecturas del vivero mock con la misma lógica de
comparación, para que la demo `mock` muestre algo coherente.

**Pestaña Parámetros:**
- Reglas agrupadas por rama (GLOBAL, RIEGO, INSUMO, MEDIASOMBRA, SEGUIMIENTO), en orden de
  prioridad, colapsadas por defecto con un resumen ("3 parámetros · 1 modificado").
- Cada regla lista sus parámetros con etiqueta, valor vigente, unidad, rango y referencia a la
  spec. Una regla sin parámetros dice "Sin parámetros configurables".
- **Parámetro compartido:** chip "Compartido con N reglas" con los nombres al pasar el mouse. El
  borrador se indexa **por clave**, no por regla: editarlo en una tarjeta actualiza al instante
  todas las tarjetas donde aparece. Al editarlo se resaltan sus otras apariciones.
- Barra: búsqueda (regla o parámetro), filtro por rama, "sólo modificados", y un conmutador
  **"Ver por parámetro"** que lista cada parámetro una vez con su `usadoPor` — útil para auditar
  que no haya duplicados.
- Campo por tipo: `NUMERO`/`ENTERO` reutilizan `NumberField` de Configuración; `HORA` y
  `VENTANA_HORARIA` usan `<input type="time">` (uno o dos). Validación en cliente **genérica**, a
  partir de `min`/`max`/`decimales` del DTO: no se duplican rangos en el frontend. Las
  restricciones cruzadas se validan sólo en el servidor y el 400 se muestra junto al parámetro.
- Guardar / descartar / restablecer fábrica por parámetro. Auditoría visible (`updatedBy`, fecha).

Escala para 40 reglas porque los parámetros están normalizados (render O(reglas + parámetros)),
todo arranca colapsado y la búsqueda filtra en memoria.

**Pestaña Inspector:**
- Selector zona → sector (y llega con `?sector=MZ-2-006` desde el detalle de sector), selector de
  origen (Telemetría / Barrido) y botón "Actualizar" con refresco automático opcional cada 5 s,
  igual que el dashboard.
- `RuleGraph` gana una prop opcional `traza`. Con traza, **el color de cada nodo sale del estado de
  la traza**, no del regex sobre el historial. Los nodos de regla pasan a un nodo personalizado de
  React Flow que muestra hasta dos comparaciones compactas:
  `Humedad sustrato 38 % < 42 % ✓` · `Riegos 24 h 0 > 0 ✗`. Las no configurables llevan un ícono de
  candado; `SIN_DATO` se muestra como "sin dato".
- Clic en un nodo → panel lateral con todas las comparaciones, la acción y el motivo, y un enlace
  "Editar parámetro" que lleva a la pestaña Parámetros con la regla abierta.
- Los estilos nuevos usan tokens (`--ok`, `--warn`, `--crit`, `--faint`…). Los colores hex del modo
  historial (`RuleGraph.tsx:30-118`) **no** se tocan en este cambio.

El DAG del **Historial** (`HistorialTimeline.tsx:208`) sigue como está: muestra ciclos viejos, y la
traza sólo existe para el último. Ver DA-3.

## Decisiones abiertas

Cada una tiene un default aplicado en este diseño; sólo el usuario puede confirmarlo.

- **DA-1 · Desacoplar ya el umbral de riego de la banda `idealMin` de humedad.**
  *Default: sí, con fábrica 42* (comportamiento idéntico). A favor: es lo que pide v2, y sin esto
  el umbral de riego se edita en "Umbrales de métricas" y no aparece en el catálogo. En contra: si
  alguien ya cambió `idealMin` en una base existente, su riego vuelve a 42 hasta que lo cargue en
  el catálogo (el script de migración puede copiarlo; se agrega si el usuario lo pide).
- **DA-2 · Migrar al catálogo las tres reglas de riego actuales** (`IrrigationRule`,
  `WeatherOverrideRule`, `DailyVolumeLimitRule`) **aunque el cambio siguiente las reemplace.**
  *Default: sí.* A favor: la sección muestra el motor completo desde el día uno y la
  infraestructura se prueba contra todos los patrones (lectura de métrica, conteo de historial,
  pronóstico). En contra: ~60 líneas de producción y sus tests que el próximo cambio reescribe.
- **DA-3 · Dónde vive el DAG con traza.** *Default: en el Inspector de `/reglas`*, y el DAG del
  Historial queda como está. Alternativa: reemplazar el del Historial por el de traza cuando el
  ciclo elegido es el último del sector. Más integrado, pero mezcla dos fuentes de verdad en una
  misma vista.
- **DA-4 · Retención de la traza.** *Default: la última por sector y origen, en memoria.*
  Alternativa: ring buffer de N por sector (memoria × N) para comparar evaluaciones consecutivas.
- **DA-5 · Unificar la confianza mínima del diagnóstico.** *Default: un solo parámetro*
  `diagnostico.confianza-minima` para `SupplyRule` y `esConcluyente`. Es eliminar un duplicado
  real, pero cambia la propiedad de configuración de la captura (`capturas.confianza-minima`
  desaparece).
- **DA-6 · Registro de Inacción por ciclo.** Hoy cada `NOOP_INFO` escribe una fila por sector y
  regla en cada evaluación (`ActionExecutor.java:107-118`, `HistorialService.java:147-163`): miles
  de filas por barrido. Con la traza, esas filas sobran. *Default: no se toca en este cambio*
  (el DAG del Historial depende de ellas). Recomiendo abrir un cambio aparte para persistir sólo
  acciones y bloqueos en transición.

## Risks / Trade-offs

- **Drift entre el catálogo mock y el real.** Mismo riesgo que hoy tiene `data/mock/specs.ts`.
  Mitigación: un test del backend serializa el catálogo de fábrica a
  `fe/data/mock/catalogoReglas.fixture.json`, y el mock del frontend se arma desde ese fixture; un
  test del backend falla si el fixture quedó viejo.
- **Cambio de firma de `Rule`.** Toca las 9 reglas y sus tests. Mitigación: firma puente durante la
  migración (D3) y una regla por tanda.
- **La traza se pierde al reiniciar.** Aceptado (D5). El inspector lo dice ("sin evaluaciones desde
  el último arranque").
- **Parámetros "zombi" de `configuracion_operativa`** (`riegoVolMaxDiarioMl`, `insumoDosisMax24hMl`)
  siguen editables y no hacen nada. Se rotulan en el form; los borran los cambios que reescriben
  riego y dosificación.
- **Script manual no corrido.** El arranque no falla, pero sí el alta de una fila operativa nueva.
  Mitigación: documentarlo y que el test de integración use una base sin esas columnas.
- **`StaleSensorRule` y el timestamp en segundos del firmware** (`circuito-sensado-a-motor.md` §5.1):
  la regla no lo arregla, pero la traza ahora lo hace visible (antigüedad recibida absurda).

## Migration Plan

1. Backend: catálogo, persistencia, validación y `GET/PUT /api/rules/parametros` (sin tocar reglas).
2. Backend: `Evaluacion`, traza, almacén y `GET /api/rules/evaluaciones/{id}`; orquestador con
   firma puente.
3. Backend: migrar las reglas de a una; sacar `@Value`, literales y columnas; script SQL; borrar la
   firma vieja de `Rule`.
4. Frontend: capa de datos (tipos, repositorio, mock con fixture, HTTP) y esquema por repositorio.
5. Frontend: pestaña Parámetros y ajustes de Configuración.
6. Frontend: Inspector con `RuleGraph` en modo traza.

Rollback: revertir el código. La tabla `parametro_regla` puede quedar (no la lee nadie). Las dos
columnas bajadas **no** vuelven solas: `ddl-auto=update` intentaría agregarlas `NOT NULL` sin default
sobre una tabla con filas y PostgreSQL lo rechaza. El script trae, comentado, el bloque inverso
(`ADD COLUMN … DEFAULT …`) para ese caso.
