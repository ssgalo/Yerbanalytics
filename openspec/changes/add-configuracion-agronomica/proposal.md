# Change: add-configuracion-agronomica

## Why

La sección **Configuración** es hoy un placeholder (`/configuracion` →
`PlaceholderPage` titulado "Umbrales y rustificación"). El Product Backlog la define
en una historia de prioridad alta:

- **HU-15** — el **Ingeniero Agrónomo** quiere **configurar umbrales, límites
  operativos máximos y planes de rustificación** para calibrar las decisiones del
  sistema a la realidad del vivero y evitar sobredosis. Sus CA exigen: valores
  seguros de fábrica para *Ilex paraguariensis* (CA-01), guardado que actualiza la
  base, audita usuario/timestamp y encola la regla (CA-02), bloqueo de valores fuera
  del rango fisiológico (CA-03), límites de riego —tiempo de apertura y volumen
  diario— (CA-04), dosis máxima de insumo por 24 h (CA-05), plan de exposición de la
  mediasombra —cronograma de días y % de apertura por etapa— (CA-06) y parámetros de
  seguimiento post-acción —latencia y delta mínimo de mejora— (CA-07).

Hoy estos parámetros viven **hardcodeados** y no se pueden calibrar sin recompilar:
las bandas de las 5 métricas están en `NurseryConstants.SPECS` (backend) y
`data/mock/specs.ts` (frontend); el disparo de riego es el literal `humSusVal < 42`
en `NurseryService`; y la latencia/delta del seguimiento son `@Value` fijos en
`HistorialService`. El cambio `add-historial-trazabilidad` dejó explícitamente como
*non-goal* "latencia configurable por el agrónomo (HU-15)"; este cambio cierra esa
brecha.

## What Changes

- **Persistencia de configuración** en el backend: nuevas entidades
  `umbral_metrica` (bandas por métrica), `configuracion_operativa` (fila única con
  los límites de actuadores y los parámetros de seguimiento, más auditoría) y
  `rustificacion_etapa` (plan de exposición por etapas).
- **Validación fisiológica** al guardar: se rechazan bandas incoherentes o valores
  fuera del rango seguro de fábrica, y límites no positivos o etapas solapadas
  (HU-15 CA-03..CA-06).
- **Endpoints** `GET /api/configuracion` y `PUT /api/configuracion`. El PUT audita
  (`updatedBy`/`updatedTs`) y registra el cambio en el historial (HU-15 CA-02).
- **Integración con el motor** (los valores pasan a estar activos):
  - `NurseryService` calcula el estado de las métricas con los umbrales persistidos
    (vía `getEffectiveSpecs()`) y deriva el disparo de la electroválvula del `idealMin`
    de `humSus` configurado, en vez de la constante y el literal `42`.
  - `HistorialService` toma la latencia y el delta de seguimiento desde la
    configuración persistida (los `@Value` quedan como fallback).
- **Capa de datos del frontend**: `DataRepository` gana `getConfig()` y
  `saveConfig()`; mock determinístico (defaults de `specs.ts`) y cliente HTTP.
- **Vista Configuración** real: formularios por bloque (umbrales de métricas, riego,
  insumos, mediasombra/rustificación, seguimiento) con validación en cliente,
  guardar con feedback y restablecer valores de fábrica. Reemplaza el placeholder de
  `/configuracion`.

## Impact

- Affected specs: `configuracion-agronomica` (frontend, nueva),
  `configuracion-persistencia` (backend, nueva).
- Affected code:
  - Backend `Desarrollo/backend/`: nuevos `model/UmbralMetricaEntity`,
    `model/ConfiguracionOperativaEntity`, `model/RustificacionEtapaEntity`;
    `repository/UmbralMetricaRepository`, `repository/ConfiguracionOperativaRepository`,
    `repository/RustificacionEtapaRepository`; `dto/Configuracion`, `dto/UmbralMetrica`,
    `dto/ConfiguracionOperativa`, `dto/RustificacionEtapa`; `service/ConfiguracionService`;
    `controller/ConfiguracionController`; edición de `service/NurseryService` y
    `service/HistorialService` (integración) y `resources/data.sql` (seed).
  - Frontend `Desarrollo/frontend/`: nuevos `features/configuracion/`,
    `data/mock/config.ts`, `hooks/useConfig.ts`; edición de `types/domain.ts`,
    `data/repository.ts`, `data/http/httpRepository.ts`, `data/mock/mockRepository.ts`,
    `router.tsx`.
- No toca Modelo_IA. El placeholder de `/hardware` se conserva.

## Non-Goals

- **Gating por rol** (solo Ingeniero Agrónomo): depende de HU-01/HU-20
  (autenticación inexistente hoy); la vista se expone sin auth, consistente con el
  resto de la app.
- **Aplicación** real de los límites de actuadores (volumen/dosis/tiempo/% máximos):
  vive en HU-06/07/08 (riego, insumo y mediasombra autónomos), aún no implementadas.
  Este cambio sólo los define, valida, persiste y muestra.
- **Sincronización con el hardware** (CA-02 menciona "encolar la regla para el
  hardware"): se persiste y audita el cambio; la sync física es de las HU de
  actuación/offline.
- **Versionado** del historial de configuraciones más allá de `updatedBy`/`updatedTs`.
