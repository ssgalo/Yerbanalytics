# Change: add-catalogo-umbrales-reglas

## Why

El equipo no puede contestar dos preguntas básicas sobre el motor de reglas: **qué umbral usa cada
regla y cuánto vale hoy**, y **por qué una regla disparó o no** en una evaluación concreta.

1. **Los umbrales están en cinco lugares distintos**, y casi ninguno se ve desde la UI:
   - la banda `idealMin` de `humSus` en `umbral_metrica`, que hace de umbral de riego
     (`service/ConfiguracionService.java:300-307`), con un fallback propio de 40 % en la regla
     (`engine/rules/IrrigationRule.java:41`);
   - columnas de `configuracion_operativa` (`riegoTiempoMaxSeg`, `mediasombraAperturaMaxPct`);
   - properties con `@Value` (`rain-threshold-pct`, `uv-threshold`, `stale-threshold-ms`,
     `application.properties:69,97,100`);
   - constantes en el código: confianza 85 (`rules/SupplyRule.java:34`), 2 riegos/24 h
     (`rules/DailyVolumeLimitRule.java:61`), 1 riego/24 h (`rules/IrrigationRule.java:98`),
     1 dosis/24 h (`rules/DailyDoseLimitRule.java:80`), apertura protectora 30 %
     (`rules/ShadingRule.java:90`);
   - y un **duplicado real**: la confianza mínima del diagnóstico existe dos veces, una
     hardcodeada en `SupplyRule` y otra en `capturas.confianza-minima`
     (`config/CapturaProperties.java:53`), que usa `DiagnosticoService.esConcluyente`.
2. **El DAG no sabe qué pasó.** `RuleGraph` *infiere* el recorrido parseando con regex el texto de
   `historial_evento.lectura` (`components/DAGViewer/RuleGraph.tsx:125-135`). No hay ningún
   registro de qué valor recibió cada regla ni contra qué umbral lo comparó.
3. **El próximo cambio implementa las reglas de riego R-01…R-06** (`reglas_v2.md` §5) y suma una
   docena de parámetros de §11 con tipos que hoy no existen en ningún lado: litros por punto,
   ventanas horarias `HH:mm–HH:mm`, horas, milímetros, cantidad de sectores. Después vienen ~30
   reglas más. Seguir agregando columnas a `configuracion_operativa` y `@Value` no escala.

## What Changes

- **Catálogo único de parámetros de reglas (backend).** Cada parámetro tiene una clave estable,
  etiqueta, tipo, unidad, valor de fábrica, rango permitido y referencia a la spec. Las
  definiciones viven en código, agrupadas por familia; la base guarda **sólo los valores
  modificados** (tabla `parametro_regla`). Valor vigente = override o, si no hay, el de fábrica.
- **Cada regla declara qué parámetros usa** (`Rule.parametros()`), y sólo puede leer esos: leer
  uno no declarado es un error. Así la vista "por regla" no puede mentir. Un parámetro que usan
  varias reglas existe **una sola vez**.
- **Traza de evaluación.** Al evaluar, cada regla registra sus comparaciones (qué recibió, operador,
  umbral, si se cumplió) y el orquestador registra qué reglas se evaluaron, cuáles se omitieron por
  rama bloqueada y cuáles no se alcanzaron. Se guarda **en memoria** la última evaluación por
  sector y por origen (telemetría / barrido). No se escribe en `historial_evento`.
- **API:** `GET/PUT /api/rules/parametros` (catálogo normalizado y edición en lote validada),
  `GET /api/rules/evaluaciones/{sectorId}` (última traza) y `GET /api/rules/schema` suma la
  lista de parámetros de cada nodo.
- **Migración de las 9 reglas existentes** al catálogo, sin cambiar su comportamiento (mismos
  valores de fábrica que hoy). Salen del sistema los `@Value` y constantes de umbral, y los dos
  campos de `configuracion_operativa` que eran umbrales de regla. Script manual para mudar sus
  valores y bajar las columnas.
- **Frontend: sección nueva "Motor de reglas"** (`/reglas`) con dos vistas:
  - **Parámetros por regla**: reglas agrupadas por rama, cada una con sus parámetros y su valor
    vigente; los compartidos se marcan y se editan en un solo borrador. Búsqueda, filtro por rama
    y "sólo modificados", pensado para 40 reglas.
  - **Inspector**: el DAG del motor para un sector, pintado con la traza real y con "recibido vs.
    umbral" sobre cada nodo.
- **La UI consume todo por `DataRepository`**, con mock determinístico. Se corrige de paso que el
  esquema del DAG hoy se pide con `fetch` directo (`hooks/useRuleEngineSchema.ts:28`) y por eso
  no funciona en modo `mock`.

## Out of scope

- **Las reglas de riego R-01…R-06.** Van en el cambio siguiente. Este deja lista la
  infraestructura: los tipos de valor y las restricciones cruzadas que necesitan sus parámetros
  (§11 Riego y §6.3 de `diferencias-motor-reglas-vs-reglas-v2.md`) quedan probados por test, pero
  sus **definiciones** se suman con las reglas que las usan, para no publicar parámetros editables
  que no afectan nada.
- Cambiar el comportamiento agronómico de cualquier regla actual (valores de fábrica idénticos a
  los de hoy, incluido el 42 % de riego).
- Dejar de persistir el "Registro de Inacción" por ciclo en `historial_evento` (ver design,
  Decisión abierta DA-6).
- Evaluación en seco ("¿qué haría el motor con estos valores?"): `FollowUpRule` tiene efectos
  laterales (`rules/FollowUpRule.java:71`) y lo vuelve un cambio aparte.
- Parámetros por macro-zona (v2 los pide para el plan de nutrición y la rustificación).
- Gating por rol del agrónomo (sin autenticación, igual que Configuración).

## Capabilities

### New Capabilities

- `catalogo-parametros-reglas`: definición, valor vigente, validación, persistencia, auditoría y API
  de los parámetros de reglas; declaración regla → parámetros.
- `traza-evaluacion-reglas`: registro, retención y exposición de la última evaluación del motor por
  sector.
- `motor-reglas-ui`: sección "Motor de reglas" del dashboard (parámetros por regla e inspector del
  DAG con traza).

### Modified Capabilities

- `configuracion-persistencia` (de `add-configuracion-agronomica`): deja de persistir
  `riegoTiempoMaxSeg` y `mediasombraAperturaMaxPct`, y el riego deja de derivarse de `idealMin`
  de `humSus` (se revierte la decisión 5 de ese cambio; el porqué en `design.md` D7).
- `configuracion-agronomica`: la vista deja de editar esos dos campos y enlaza a "Motor de reglas".
- `motor-reglas`: el umbral de silencio pasa al catálogo y cada evaluación declara su origen.

## Impact

- **Backend** (`Desarrollo/backend/`): nuevo paquete `engine/parametros/` (definiciones, catálogo,
  servicio, entidad y repositorio) y `engine/traza/` (evaluación, comparaciones, almacén);
  `Rule`, `RuleContext`, `RuleOrchestrator`, las 9 reglas, `RuleEngineSchemaController`,
  `RuleNodeDto`, controller nuevo de parámetros y trazas; `ConfiguracionService`,
  `ConfiguracionOperativaEntity`/DTO, `NurseryService`, `NurseryWatchdog`, `DiagnosticoService`,
  `CapturaProperties`; `application.properties` (bajas: `rain-threshold-pct`, `uv-threshold`,
  `stale-threshold-ms`, `capturas.confianza-minima`); nuevo
  `resources/migracion-catalogo-parametros.sql`.
- **Frontend** (`Desarrollo/frontend/`): `types/domain.ts`, `data/repository.ts`, HTTP y mock;
  nuevo `features/reglas/`; `RuleGraph` gana un modo con traza; `Sidebar` y `router`;
  `LimitesActuadoresForm` y `RustificacionPlanForm` pierden/consultan los campos mudados.
- **Base:** una tabla nueva (`parametro_regla`) y dos columnas menos en `configuracion_operativa`
  (script manual, igual que `migracion-quitar-simulador.sql`).
- **Sin impacto** en el contrato de cámara, el contrato MQTT, el simulador ni el Modelo_IA. El
  backend sigue sin modos.
