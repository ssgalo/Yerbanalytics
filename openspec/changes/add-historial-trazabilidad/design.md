# Design: add-historial-trazabilidad

## Context

El backend ya persiste el estado **actual** del vivero (entidades `zona` y `sector`
con la última lectura) y procesa telemetría MQTT en `NurseryService.updateTelemetry()`.
Lo que falta es la dimensión **temporal**: un registro de cada acción que el sistema
ejecutó, por qué la ejecutó, y qué pasó después. Ese registro es la materia prima de
HU-11 (trazabilidad/auditoría) y HU-12 (efectividad post-acción).

El frontend ya tiene los building blocks visuales por sector: el timeline de
`SectorHistory` y el comparativo antes/ahora de `PostActionCard`, con los tipos
`HistoryEntry` y `Evolution`. La vista Historial los eleva a una escala global,
filtrable, sobre datos reales.

## Goals / Non-Goals

**Goals**
- Registro **inalterable** (solo insert) de acciones ejecutadas con su cadena de
  justificación completa (`lectura/diagnóstico → decisión → acción`).
- Evaluación automática de efectividad post-acción con veredicto y bloqueo de
  repetición ante `Sin efectividad`.
- Endpoint de consulta filtrable y una vista de timeline + filtros que lo consume,
  con migración mock → http por variable de entorno (igual que el resto del frontend).
- Contrato JSON idéntico entre el DTO del backend y el tipo del frontend.

**Non-Goals**
- Exportación de reportes (HU-16).
- Latencia configurable por tipo de acción por el agrónomo (HU-15).
- Paginación server-side: para la demo el endpoint devuelve la lista filtrada
  completa y el frontend filtra en memoria (igual que `DiagnosticsPage`).

## Decisions

### 1. Entidad inmutable de auditoría (`historial_evento`)
Nueva `@Entity HistorialEventoEntity` con `ddl-auto=update` (mismo enfoque que
`SectorEntity`). Guarda los campos crudos: identidad (`id`, `sectorId`, `zonaId`,
`zonaName`), `tipo` (`Riego|Insumo|Mediasombra`), `ts` (epoch ms), la cadena
(`lectura`, `decision`, `accion`), el `resultado`
(`Efectiva|En seguimiento|Pospuesta|Abortada`), `sev`, y el bloque de seguimiento
(`evoShow`, `evoMetric`, `evoAntes`, `evoAhora`, `evoUnit`, `evoDelta`,
`evoLatencia`, `evoVerdict`, `evoEvaluadoTs`, `bloqueoRepeticion`). Solo se inserta;
no hay endpoints de edición/borrado → inalterabilidad por construcción (HU-11 CA-03).
Los colores (soft/ink por resultado y severidad, tint/ink/path del ícono) **no** se
persisten: se derivan en el mapeo a DTO desde `NurseryConstants`, evitando duplicar
la paleta en la base.

### 2. Hook de registro en la actuación
En `updateTelemetry()`, antes de sobrescribir los actuadores, se compara el estado
previo con el nuevo. Si la electroválvula transiciona a `Regando` o la bomba a
`Dosificando`, se delega en `HistorialService.registrar(...)` con la condición
desencadenante (la métrica/diagnóstico que disparó la decisión) y los parámetros.
Registrar **solo en la transición** (no en cada ciclo de 10 s) evita inundar la
tabla y refleja una acción real iniciada.

### 3. Motor de seguimiento post-acción (HU-12)
`HistorialService.evaluarSeguimiento()` anotado con `@Scheduled` (el proyecto ya usa
scheduling para el simulador MQTT). Recorre los eventos con `evoEvaluadoTs == null`
cuya antigüedad superó la latencia configurada, toma la lectura actual de la métrica
afectada del sector, calcula el `delta` contra el valor `antes`, y fija el veredicto:
`Efectiva` si el delta superó el umbral de recuperación, `Sin efectividad` en caso
contrario — y en ese caso marca `bloqueoRepeticion = true` (HU-12 CA-03). La latencia
demo es corta y configurable por properties para que el veredicto aparezca en la demo.

### 4. Endpoint `GET /api/historial`
`HistorialController` con query params opcionales `sector`, `zona`, `tipo`, `desde`,
`hasta` → `List<HistorialEvento>` ordenado por `ts` descendente. CORS ya cubierto por
`CorsConfig`. Sin verbos de escritura. El contrato `HistorialEvento` (record) es el
espejo exacto del tipo `ActionRecord` del frontend.

### 5. Capa de datos del frontend: `getHistory()`
Se agrega `getHistory(): Promise<ActionRecord[]>` a `DataRepository`. `HttpRepository`
hace `GET {baseUrl}/historial`; `MockRepository` cachea una lista generada con RNG
sembrado (mismo `lib/rng.ts` y plantillas `actTpl`/`ACT`/`resMap` ya existentes),
produciendo eventos distribuidos en sectores y tiempo, con cadena y evolución. El
filtrado vive en la vista, en memoria, replicando el patrón de `DiagnosticsPage`.

### 6. Vista: timeline global + filtros
`features/historial/` con `HistorialPage` (estado local de filtros + `useHistory`),
`HistorialFilters` (selects de tipo/zona/sector/resultado + rango de fechas + contador,
patrón `DiagFilters`) y `HistorialTimeline` (reutiliza los estilos de timeline de
`SectorHistory`; cada entrada expande la cadena `lectura → decisión → acción` y, si
existe, el comparativo de evolución reutilizando el layout de `PostActionCard`).
La vista es **read-only**: no expone controles de edición ni borrado (HU-11 CA-03).

## Risks / Trade-offs

- **Riesgo:** la actuación corre cada 10 s sobre 600 sectores; registrar sin cuidado
  inundaría la tabla.
  **Mitigación:** registrar solo en la transición a estado activo del actuador.
- **Trade-off:** filtrado en memoria en el cliente (no server-side). Aceptable para
  el volumen de la demo y consistente con `DiagnosticsPage`; el endpoint igualmente
  acepta filtros para escalar más adelante.
- **Trade-off:** los colores se derivan en el mapeo a DTO, no se persisten. Mantiene
  la base limpia y una sola fuente de la paleta, a costa de recomputarlos por request.

## Migration Plan

1. Backend — entidad `HistorialEventoEntity` + `HistorialRepository`.
2. Backend — DTOs `HistorialEvento` + `Evolution` y `HistorialService` (registrar +
   getHistorial + evaluarSeguimiento).
3. Backend — `HistorialController` `GET /api/historial`; hook en `updateTelemetry`;
   seed en `data.sql`.
4. Frontend — tipo `ActionRecord`; `getHistory()` en repository/http/mock + generador.
5. Frontend — hook `useHistory`; feature `historial/` (page, filtros, timeline);
   router reemplaza el placeholder.
6. Tests — generador y filtrado en el frontend; build del backend.
