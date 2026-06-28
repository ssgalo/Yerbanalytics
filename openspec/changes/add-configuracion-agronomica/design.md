# Design: add-configuracion-agronomica

## Context

El backend ya persiste el estado del vivero (`zona`, `sector`) y el historial de
acciones (`historial_evento`), y procesa telemetría MQTT en
`NurseryService.updateTelemetry()`. Pero los parámetros agronómicos que gobiernan ese
procesamiento son constantes de código: las bandas de las 5 métricas
(`NurseryConstants.SPECS`), el umbral de riego (`humSusVal < 42` literal) y la
latencia/delta del seguimiento (`@Value` de `HistorialService`). HU-15 pide que el
Ingeniero Agrónomo pueda **calibrar** esos valores desde la plataforma.

El frontend ya tiene el patrón de migración mock → http por variable de entorno
(`DataRepository` con `MockRepository`/`HttpRepository`) y vistas con formularios y
filtros (p. ej. `HistorialFilters`). La sección Configuración reutiliza ese patrón:
una vista de formularios que lee/escribe vía repositorio.

## Goals / Non-Goals

**Goals**
- Persistir la configuración agronómica (umbrales de métricas, límites de actuadores,
  plan de rustificación, parámetros de seguimiento) con valores de fábrica seguros.
- Validar al guardar contra el rango fisiológico y la coherencia de las bandas, y
  auditar quién/cuándo cambió, registrando el cambio en el historial.
- Que los umbrales y los parámetros de seguimiento **gobiernen el motor** en runtime
  (estado de métricas y evaluación post-acción), no sólo se muestren.
- Endpoint `GET`/`PUT` y una vista de formularios que lo consume, con migración
  mock → http por entorno. Contrato JSON idéntico entre DTO backend y tipo frontend.

**Non-Goals**
- Gating por rol (HU-01/HU-20) y sincronización física con el hardware.
- Aplicación de los límites de actuadores (volumen/dosis/tiempo) — eso es de
  HU-06/07/08; aquí sólo se definen y validan.
- Versionado de configuraciones (solo último `updatedBy`/`updatedTs`).

## Decisions

### 1. Modelo de datos en tres entidades
Se separa por cardinalidad natural, con `ddl-auto=update` (mismo enfoque que
`SectorEntity`/`HistorialEventoEntity`):
- `UmbralMetricaEntity` — PK `metricKey` (`humSus|humAmb|temp|ce|uv`); columnas
  `idealMin, idealMax, warnMin, warnMax, critMin, critMax`. 5 filas. Sólo se guardan
  las **bandas**; `label`, `unit`, `dec`, `base` siguen viniendo de
  `NurseryConstants.SPECS` (no se duplican en la base).
- `ConfiguracionOperativaEntity` — fila única (`id = 1`): `riegoTiempoMaxSeg`,
  `riegoVolMaxDiarioMl`, `insumoDosisMax24hMl`, `mediasombraAperturaMaxPct`,
  `seguimientoLatenciaMin`, `seguimientoDeltaMin`, + auditoría `updatedBy`,
  `updatedTs`.
- `RustificacionEtapaEntity` — `id`, `orden`, `diaDesde`, `diaHasta`, `aperturaPct`.
  Lista ordenada que describe el plan de exposición (HU-15 CA-06).

### 2. DTO agregado `Configuracion`
El endpoint expone/recibe un único record `Configuracion` que agrupa
`List<UmbralMetrica>` + `ConfiguracionOperativa` + `List<RustificacionEtapa>`. Es el
espejo exacto del tipo `Configuracion` del frontend, evitando múltiples round-trips.

### 3. Validación fisiológica al guardar (HU-15 CA-03..CA-06)
`ConfiguracionService.updateConfiguracion()` valida antes de persistir:
- **Bandas anidadas y coherentes** por métrica: `critMin ≤ warnMin ≤ idealMin <
  idealMax ≤ warnMax ≤ critMax`, y todas dentro del rango fisiológico de fábrica
  (límites duros tomados de las bandas `crit` de `SPECS`).
- **Límites de actuadores** positivos: tiempo, volumen, dosis y % > 0; apertura
  máxima en `[0, 100]`.
- **Etapas de rustificación** sin solapamiento y con `diaDesde ≤ diaHasta` y
  `aperturaPct` en `[0, aperturaMaxPct]`.
Si algo falla lanza una excepción de validación → el controller responde **400** con
un mensaje claro (HU-15 CA-03). La validación del frontend espeja estas reglas para
bloquear el guardado antes del round-trip.

### 4. Auditoría y registro en el historial (HU-15 CA-02)
Al guardar se setean `updatedBy`/`updatedTs` y se delega en `HistorialService` para
registrar un evento de tipo `Configuración` (intervención manual: quién, cuándo, qué
bloque cambió), reutilizando la infraestructura inmutable del historial.

### 5. Integración con el motor — valores activos
- `ConfiguracionService.getEffectiveSpecs()` arma `List<MetricSpec>` combinando los
  metadatos base de `SPECS` con las bandas persistidas. `NurseryService` lo usa en
  `buildMetricsList()`/`getSnapshot()` en lugar de la constante `SPECS`, y deriva el
  disparo de la electroválvula del `idealMin` de `humSus` configurado (en vez del
  literal `42`).
- `HistorialService.withSeguimiento()` toma `latencyMs`/`label`/`umbral` desde
  `ConfiguracionService` (los `@Value` actuales quedan como fallback por si no hay
  fila sembrada).
- **Dependencias entre beans:** `NurseryService → ConfiguracionService` y
  `ConfiguracionService → HistorialService`; `HistorialService` no depende de
  `ConfiguracionService` salvo en `withSeguimiento`. Donde aparezca un ciclo se
  rompe con `@Lazy` en el parámetro del constructor (estilo ya usado en el proyecto).

### 6. Capa de datos del frontend: `getConfig()` / `saveConfig()`
Se agregan a `DataRepository`. `HttpRepository` hace `GET`/`PUT
{baseUrl}/configuracion`; `MockRepository` cachea una config por defecto (derivada de
`specs.ts` y de constantes locales) y `saveConfig` actualiza el cache en memoria,
validando con la misma función que la UI. El generador es determinístico (no hay RNG;
son los defaults de fábrica).

### 7. Vista: formularios por bloque
`features/configuracion/` con `ConfiguracionPage` (estado local del formulario +
`useConfig`) y subcomponentes `UmbralesForm`, `LimitesActuadoresForm`,
`RustificacionPlanForm`, `SeguimientoForm`. Validación en cliente que marca el campo
inválido y deshabilita Guardar; botón "Restablecer valores de fábrica". Feedback de
éxito/error al guardar. Reutiliza átomos UI (`Card`, `Badge`) y tokens existentes.

## Risks / Trade-offs

- **Riesgo:** introducir dependencias entre `NurseryService`, `ConfiguracionService` e
  `HistorialService` puede crear un ciclo de beans.
  **Mitigación:** `@Lazy` en el punto del ciclo; mantener `ConfiguracionService` sin
  estado mutable compartido.
- **Trade-off:** la config se lee de la base en el camino caliente de telemetría (cada
  ciclo). **Mitigación:** `getEffectiveSpecs()` es barato (5 filas) y puede cachearse
  en memoria invalidando el cache al guardar; aceptable para el volumen de la demo.
- **Trade-off:** los metadatos de métricas (`label/unit/dec/base`) no se persisten, se
  derivan de `SPECS`. Mantiene la base limpia y una sola fuente de esos metadatos.

## Migration Plan

1. Backend — entidades + repositorios.
2. Backend — DTOs (`Configuracion` y anidados) y `ConfiguracionService` (get +
   update con validación + `getEffectiveSpecs` + getters de seguimiento).
3. Backend — `ConfiguracionController` (`GET`/`PUT`); integración en `NurseryService`
   e `HistorialService`; seed en `data.sql`; auditoría en historial.
4. Frontend — tipos `Configuracion`; `getConfig`/`saveConfig` en repository/http/mock.
5. Frontend — hook `useConfig`; feature `configuracion/` (page + subforms); router
   reemplaza el placeholder.
6. Tests — mock determinístico y validación de bandas; build del backend.
