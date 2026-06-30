# Design: add-gestion-hardware

## Context

El backend ya persiste el vivero (`zona`, `sector`), el historial (`historial_evento`)
y la configuración (`configuracion_operativa`, `umbral_metrica`, `rustificacion_etapa`),
y procesa telemetría MQTT en `NurseryService.updateTelemetry()`. Pero los conceptos de
**hardware** están aplanados como columnas sobre el sector (`actuador_valve`,
`actuador_pump`, `actuador_shade`, lecturas crudas) y **no hay ninguna entidad de
dispositivo**: el `mac` y la `battery` que llegan por MQTT se descartan, y no existe
batería, señal, último heartbeat ni falla por equipo. HU-18 y HU-21 necesitan ese
registro de dispositivos de primera clase.

El frontend ya tiene el patrón mock → http por entorno (`DataRepository` con
`MockRepository`/`HttpRepository`) y vistas con filtros y formularios (Historial,
Configuración). La sección Hardware reutiliza ese patrón.

## Goals / Non-Goals

**Goals**
- Un registro de dispositivos persistente, mapeado a sector o macro-zona, con validación
  de unicidad (serial/MAC, un actuador por tipo y sector).
- Derivar el estado técnico (batería baja / intermitente / fuera de servicio / averiado)
  por watchdog, sin almacenar un estado que se vuelva stale.
- Que la telemetría MQTT alimente el heartbeat del nodo testigo (cerrar la brecha del
  `mac`/`battery` descartados).
- Detectar y mostrar los sectores con mapeo de hardware incompleto (HU-18 CA-04).
- Endpoints `GET`/`POST`/`PUT` y una vista que los consume, con migración mock → http
  por entorno. Contrato JSON idéntico entre DTO backend y tipo frontend.

**Non-Goals**
- Generación dinámica de la topología (HU-18 CA-01): se opera sobre la grilla sembrada.
- Gating por rol y sincronización física con el hardware.
- Disparo real de la falla física desde el motor de actuación (HU-06/07/08).

## Decisions

### 1. Entidad `dispositivo` de primera clase (no columnas sobre `sector`)
Se introduce `DispositivoEntity` (tabla `dispositivo`, `ddl-auto=update`) en vez de
agregar columnas al sector, porque un dispositivo tiene ciclo de vida propio (alta,
batería, señal, falla, recambio) y cardinalidad distinta: el **nodo testigo** se mapea a
una **macro-zona** (`zona_id`), mientras que los **actuadores** (electroválvula, bomba
peristáltica, mediasombra) se mapean a un **sector** (`sector_id`). Campos:
`id` (PK `DEV-###`), `serial` (único = MAC/serial), `tipo`, `zonaId`/`sectorId`
(uno u otro), `bateria`, `senal`, `ultimoUpdate`, `falla`.

### 2. Estado técnico derivado, no almacenado
`estado` (`operativo` | `intermitente` | `fuera_de_servicio`) se calcula en cada lectura
para no guardar un valor que quede stale (mismo criterio que el `offline` del sector):
- Si hay `falla` → `fuera_de_servicio` (averiado), y se mantiene hasta el recambio.
- Nodo testigo (con heartbeat): sin update por encima del umbral crítico →
  `fuera_de_servicio`; por encima del umbral corto → `intermitente`; si no, `operativo`.
- Actuadores (sin heartbeat propio en el MVP, alimentados por relé/PSU, no por batería):
  `operativo` salvo que tengan `falla`.
La bandera `bateriaBaja` (HU-21 CA-02) marca a los nodos con `bateria` bajo el umbral
sin sacarlos de operación. Los umbrales viven en `application.properties`
(`hardware.bateria-min-pct`, `…watchdog-intermitente-ms`, `…watchdog-critico-ms`), con
valores chicos para la demo (igual que `stale-threshold-ms`).

### 3. Heartbeat por telemetría MQTT
Se agrega `signal` al `MqttTelemetryPayload`. Tras procesar la telemetría de una
macro-zona, `NurseryService.updateTelemetry()` invoca
`HardwareService.actualizarHeartbeat(zoneId, mac, battery, signal, ts)`, que actualiza
batería/señal/último update del nodo testigo de esa zona. El simulador emite ahora una
**MAC distinta por macro-zona** (y una batería baja para una zona, para ejercitar
"Batería Baja"). Dependencia `NurseryService → HardwareService` (sin ciclo).

### 4. Validación del alta (HU-18 CA-02/CA-03)
`HardwareService.registrarDispositivo()` valida antes de persistir: serial obligatorio y
**único** (`HardwareConflictoException` → 409), tipo válido, nodo testigo con `zonaId`
existente (y la zona sin otro testigo), actuador con `sectorId` existente (y el sector
**sin otro actuador del mismo tipo**). Los datos faltantes/ inválidos lanzan
`HardwareInvalidoException` → 400. El frontend espeja estas reglas para bloquear el alta
antes del round-trip.

### 5. Sectores incompletos (HU-18 CA-04)
Se consideran "incompletos" los sectores que tienen **al menos un dispositivo mapeado**
pero les falta alguno de los actuadores requeridos (electroválvula, bomba peristáltica,
mediasombra). Así sólo se listan los sectores que el Administrador empezó a aprovisionar,
no los 600 sin tocar. La actuación autónoma queda señalada como deshabilitada en ellos
(presentación; la aplicación real es de las HU de actuación).

### 6. Recambio (HU-21 CA-05)
`PUT /api/hardware/{id}` reutiliza el registro existente: actualiza el serial (pieza
nueva), limpia la `falla`, revincula a su posición y deja que el heartbeat refresque el
estado. Devuelve la flota actualizada.

### 7. DTO agregado `HardwareData`
El `GET` expone un único record `HardwareData` = `List<Dispositivo>` (con estado/labels/
colores derivados) + KPIs (total, operativos, batería baja, fuera de servicio, averiados)
+ `List<SectorIncompleto>`. El `POST`/`PUT` también devuelven `HardwareData` para que la
vista se refresque en un solo round-trip. Es el espejo exacto del tipo `HardwareData` del
frontend.

### 8. Capa de datos del frontend
Se agregan a `DataRepository`: `getHardware()`, `registerDevice(d)`, `replaceDevice(id,d)`.
`HttpRepository` hace `GET`/`POST`/`PUT {baseUrl}/hardware`; `MockRepository` mantiene una
flota mutable en memoria, deriva `HardwareData` (estado/KPIs/incompletos) con la misma
lógica que el backend y valida el dedupe igual que el servidor. La flota de fábrica es
determinística e incluye un caso de cada estado.

### 9. Vista: KPIs + tabla + alta + incompletos
`features/hardware/` con `HardwarePage` y subcomponentes `HardwareKpis`,
`HardwareFilters` (tipo/estado/zona), `HardwareTable` (con acción de recambio),
`AltaHardwareForm` (alta y recambio, con validación en cliente) y `SectoresIncompletos`.
Reutiliza átomos UI (`Card`, `Badge`, `StatusDot`, `ProgressBar`, `Icon`) y el ícono
`hardware` ya existente.

## Risks / Trade-offs

- **Riesgo:** inyectar `HardwareService` en el camino caliente de telemetría agrega una
  escritura por ciclo. **Mitigación:** el heartbeat actualiza una sola fila (el testigo
  de la zona) dentro de la transacción existente; volumen despreciable para la demo.
- **Trade-off:** el estado se deriva en cada `GET` en vez de almacenarse. Evita estados
  stale a costa de un cálculo barato sobre pocas filas.
- **Trade-off:** los actuadores no tienen heartbeat propio en el MVP; su estado depende
  sólo de la `falla`. Es coherente con el BOM (relé/PSU, no batería) y se puede extender
  cuando exista downlink por actuador.

## Migration Plan

1. Backend — `DispositivoEntity` + `DispositivoRepository`.
2. Backend — DTOs (`Dispositivo`, `SectorIncompleto`, `HardwareData`) + `HardwareService`
   (get con estado derivado + alta validada + recambio + heartbeat) + excepciones.
3. Backend — `HardwareController` (`GET`/`POST`/`PUT`); integración del heartbeat en
   `NurseryService`; `signal` en el payload y MAC por zona en el simulador; seed en
   `data.sql`; umbrales en `application.properties`.
4. Frontend — tipos `Dispositivo`/`HardwareData`; `getHardware`/`registerDevice`/
   `replaceDevice` en repository/http/mock.
5. Frontend — hook `useHardware`; feature `hardware/` (page + subcomponentes); router
   reemplaza el placeholder.
6. Tests — mock determinístico y dedupe/estados; build del backend.
