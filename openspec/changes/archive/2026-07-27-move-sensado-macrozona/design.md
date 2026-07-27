# Diseño técnico — sensado a nivel macro-zona

## Context

### Estado actual

La telemetría ya llega **a nivel zona**: el nodo publica en
`nursery/zone/{zonaId}/telemetry` y `MqttTelemetryReceiver` extrae el `zonaId` del topic.
Pero `NurseryService.updateTelemetry` recorre los 100 sectores de esa zona y **copia el
mismo valor en cada uno** (`SectorEntity.humSusRaw`, `humAmbRaw`, `tempRaw`, `ceRaw`,
`uvRaw`, `lastReadingTime`). El modelo de datos guarda 100 réplicas de un dato único, y la
UI las presenta como si fueran lecturas independientes por sector.

La lectura se usa hoy en tres lugares:

1. `buildMetricsList(sector)` → estado de salud del sector (ok/warning/critical) y color en
   el mapa.
2. El motor de reglas (`engine/rules/`, p. ej. `RiegoRule`) → decisiones de actuación.
3. `SectorDetail` en el front → 5 tiles con sparkline + gráfico principal (sólo `humSus`).

### Restricciones

- **El contrato MQTT del PR #16 es fijo.** Las claves (`humSus`, `humAmb`, `temp`, `ce`,
  `uv`, `tempSuelo`, `phSuelo`, `n`, `p`, `k`, `salinidad`, `tds`) ya están escritas en
  `Desarrollo/embebido/comun/contrato.h`. Este cambio se adapta al contrato, no al revés.
- **El backend hoy falla ante campos desconocidos** en el payload — por eso el firmware
  tiene `ENVIAR_METRICAS_EXTENDIDAS` en `false`. Hay que levantar esa restricción.
- **La actuación sigue siendo por sector.** Cada sector tiene su electroválvula, su bomba y
  su mediasombra. Sólo el *sensado* es por zona.
- `ddl-auto=update` agrega columnas nuevas pero no migra datos ni borra columnas.
- El repo mantiene el patrón Repository en el front: la UI no sabe si detrás hay mock o
  HTTP. Cualquier dato nuevo debe existir en **ambas** implementaciones.

## Goals / Non-Goals

**Goals:**

- Que el modelo de datos refleje la realidad física: una lectura por macro-zona, no 100.
- Que la UI ubique el sensado donde corresponde y no sugiera instrumentación por sector.
- Dejar el front y el backend preparados para las 10 métricas del nodo, sin trabajo
  adicional cuando llegue el hardware.
- Histórico navegable para **cualquiera** de las 10 métricas, no sólo humedad de sustrato.
- No cambiar el comportamiento observable del mapa ni del motor de reglas.

**Non-Goals:**

- Tocar el firmware. El contrato MQTT queda tal cual está en el PR #16.
- Persistir series históricas reales en base. El histórico se sigue **derivando** de la
  lectura actual con el generador determinístico, igual que hoy.
- Agregar un sensor UV real ni resolver la medición de radiación ultravioleta.
- Sensado por sector, aunque el nodo combinado lo permita a futuro.
- Migración automática de datos en producción (el vivero aún no está desplegado).

## Decisions

### D1 — La lectura vive en `ZonaEntity`, no en una tabla de lecturas

`ZonaEntity` gana las columnas de las 10 métricas (`hum_sus_raw`, `hum_amb_raw`,
`temp_raw`, `ce_raw`, `uv_raw`, `temp_suelo_raw`, `ph_suelo_raw`, `n_raw`, `p_raw`,
`k_raw`) más el estado del nodo (`nodo_mac`, `nodo_battery`, `nodo_signal`,
`last_reading_time`). `SectorEntity` pierde las suyas.

**Alternativa descartada — tabla `lectura_zona` con histórico:** sería lo correcto para
guardar series reales, pero el histórico hoy se deriva, no se persiste. Crear la tabla sin
un escritor de series es infraestructura muerta. Cuando se implemente el histórico real,
esa tabla es el paso natural y este diseño no lo bloquea.

**Alternativa descartada — dejar los campos en el sector:** es el problema que el cambio
viene a resolver.

### D2 — El estado del sector se sigue derivando de la lectura de su zona

`buildMetricsList` pasa a recibir la `ZonaEntity` en vez del `SectorEntity`. El resto de
`updateTelemetry` (recalcular status, color, label, diagnóstico) sigue igual, recorriendo
los sectores de la zona.

**Por qué:** el mapa de producción y el motor de reglas dependen del `status` por sector.
Mantener esa derivación intacta hace que este cambio sea invisible para ellos. Lo único que
cambia es de dónde sale el número.

**Consecuencia esperada:** los 100 sectores de una zona van a compartir status salvo por el
diagnóstico de IA —que sí es por sector—. Eso ya pasa hoy de hecho; el cambio sólo lo hace
explícito en el modelo.

### D3 — Set de 10 métricas; `salinidad` y `tds` se toleran pero no se modelan

La sonda RS-485 lee 9 registros Modbus: humedad, temperatura, EC, pH, N, P, K, salinidad y
TDS. De esos, salinidad y TDS son **derivados por factor de la misma medición de EC** — se
mueven en proporción exacta con `ce`, así que graficarlos es repetir el mismo dato tres
veces con distinta unidad. Las decisiones agronómicas (fertirriego, lavado de sales) se
toman sobre CE.

`MetricsPayload` los declara como campos que se **descartan al deserializar**, y el
`ObjectMapper` se configura con `FAIL_ON_UNKNOWN_PROPERTIES = false` para que cualquier
clave futura del firmware no rompa la ingesta.

**Set final:**

| clave | etiqueta | unidad | dec | ideal | warn | crit | base |
|---|---|---|---|---|---|---|---|
| `humSus` | Humedad de sustrato | % | 0 | 42–68 | 32–80 | 22–90 | 55 |
| `humAmb` | Humedad ambiental | % | 0 | 62–84 | 52–91 | 42–96 | 72 |
| `temp` | Temperatura del aire | °C | 1 | 18–27 | 15–31 | 11–35 | 23 |
| `tempSuelo` | Temperatura del sustrato | °C | 1 | 16–24 | 13–28 | 10–32 | 20 |
| `ce` | Nutrientes (CE) | dS/m | 1 | 1,0–1,9 | 0,8–2,5 | 0,5–3,1 | 1,4 |
| `phSuelo` | pH del sustrato | pH | 1 | 5,0–6,0 | 4,5–6,5 | 4,0–7,0 | 5,5 |
| `n` | Nitrógeno | mg/kg | 0 | 100–200 | 70–260 | 40–320 | 150 |
| `p` | Fósforo | mg/kg | 0 | 30–60 | 20–80 | 10–100 | 45 |
| `k` | Potasio | mg/kg | 0 | 120–240 | 90–300 | 60–380 | 180 |
| `uv` | Luminosidad | % | 0 | 35–70 | 20–85 | 10–95 | 50 |

Los rangos de las 5 métricas nuevas son **provisionales**: se eligieron como punto de
partida razonable (la yerba mate prefiere sustrato ácido, de ahí el pH 5,0–6,0) y quedan
editables desde configuración agronómica (HU-15). Requieren validación con el vivero.

### D4 — La normalización de unidades ocurre en la ingesta, no en la UI

`ce` llega en µS/cm y se divide por 1000 al persistir. Una sola conversión, en un solo
lugar: `updateTelemetry`. La base guarda siempre dS/m y todo lo que lee de la base
—reglas, UI, configuración— trabaja en la unidad canónica.

**Alternativa descartada — convertir en el front:** dejaría el motor de reglas comparando
µS/cm contra umbrales en dS/m. Error silencioso de factor 1000 en decisiones de riego.

### D5 — `uv` conserva la clave pero cambia de significado

El firmware manda en `uv` el **porcentaje de luz de un LDR** (`sensor_luz`), no un índice
UV. La clave se mantiene por compatibilidad con `contrato.h`, pero la etiqueta pasa a
"Luminosidad", la unidad a `%` y los umbrales a rangos de porcentaje.

> **Deuda documentada:** si más adelante se incorpora un sensor UV real (p. ej. GUVA-S12SD
> o VEML6075), hay que separar los conceptos: `uv` vuelve a ser radiación UV en UVI con sus
> umbrales originales (1–6 ideal, 0–8 warn, 0–12 crit) y la luminosidad pasa a una clave
> nueva (`luz`). Eso implica tocar `contrato.h`, `sensor_luz`, `SPECS`, `umbral_metrica` y
> las reglas que usan radiación. Este cambio deja el sistema en un estado honesto respecto
> del hardware actual, no cierra la puerta al sensor real.

### D6 — Histórico por métrica: un solo componente, la métrica seleccionada es estado local

El panel de sensado mantiene una métrica seleccionada (por defecto `humSus`). El gráfico
—reutilizando el `MainChart` que hoy vive en el sector— dibuja la serie de esa métrica en
el rango elegido. La serie se sigue generando con `series(seed, valorActual, n, amplitud)`,
donde `seed` deriva de la zona y de la clave de la métrica para que sea estable entre
renders.

**Alternativa descartada — 10 gráficos simultáneos:** ilegible y desproporcionado en la
columna derecha.

**Alternativa descartada — modal:** rompe la lectura comparativa; el usuario quiere
alternar entre métricas rápido, no abrir y cerrar diálogos.

### D7 — Layout: la columna derecha crece a 400px

`MapPage.module.css` pasa de `grid-template-columns: 1fr 320px` a `1fr 400px`. La grilla de
sectores se achica pero mantiene su disposición configurable (`sectoresPorFila`, HU-18
CA-01), así que el usuario puede compensar bajando sectores por fila.

Orden de la columna derecha: **sensado → resumen de zona → sectores a revisar**. El sensado
va primero porque es el contexto que explica los estados que se ven en la grilla.

Las 10 métricas se muestran en grilla de 2 columnas dentro del panel; el bloque de
conductividad (`ce`) y los nutrientes (`n`, `p`, `k`) se agrupan visualmente bajo un
subtítulo "Nutrición del sustrato" para que no se lean como magnitudes sueltas.

### D8 — `NurseryData` expone la lectura dentro de `Zona`

```ts
interface LecturaZona {
  metrics: Metric[];        // las 10, ya evaluadas contra sus umbrales
  ts: number | null;        // epoch ms de la lectura
  ago: string;              // 'hace 4 min'
  stale: boolean;           // sin reporte hace más del umbral
}

interface NodoTestigo {
  mac: string | null;
  battery: number | null;   // %
  signal: number | null;    // dBm
  bateriaBaja: boolean;
}

interface Zona {
  /* ...campos actuales... */
  lectura: LecturaZona;
  nodo: NodoTestigo;
}
```

`Sector` pierde `metrics`, `ago` y `stale` (que pasan a derivarse de la zona) y
`SectorDetail` pierde `metricTiles`, `mainLine`, `mainArea`, `mainMin` y `mainMax`.

**Por qué anidado en `Zona` y no un `Record<zonaId, Lectura>` aparte:** todo consumidor que
necesita la lectura ya tiene la zona en la mano. Un mapa lateral obligaría a cruzar por id
en cada componente.

### D9 — El simulador y el dashboard de simulación cubren las 10 métricas

`EnvioMetrics` y `MqttTelemetrySimulator` se extienden con las 5 claves nuevas,
manteniendo el comportamiento actual de **lectura parcial**: sólo se actualizan las
métricas presentes en el payload, las ausentes conservan su último valor. Es lo que hace
usable el envío manual desde el dashboard de simulación.

## Risks / Trade-offs

- **[Migración de base]** `ddl-auto=update` agrega las columnas nuevas en `zona` pero deja
  las viejas en `sector` con datos y no copia nada → los datos de lectura se pierden en el
  primer arranque post-cambio y las columnas obsoletas quedan huérfanas. **Mitigación:** el
  entorno es de desarrollo y `data.sql` re-siembra todo; se documenta un script SQL manual
  (`migracion-manual.sql`) con el `UPDATE zona ... FROM sector` y los `ALTER TABLE ... DROP
  COLUMN`, como tarea explícita a ejecutar antes del primer arranque si se quiere conservar
  el estado actual.

- **[Umbrales inventados]** Los rangos de `tempSuelo`, `phSuelo`, `n`, `p` y `k` no salen
  de una fuente agronómica validada para vivero de yerba mate. Si quedan mal calibrados, el
  motor de reglas puede marcar sectores sanos como críticos y disparar actuaciones
  innecesarias. **Mitigación:** son editables desde configuración agronómica (HU-15) y se
  documentan como provisionales en el mismo panel; además se agrega la validación con el
  vivero como tarea abierta.

- **[Las nuevas métricas afectan el status]** Con 10 métricas evaluadas en vez de 5, la
  probabilidad de que un sector caiga en warning/critical sube: alcanza con que **una**
  esté fuera de banda. Con umbrales provisionales, el mapa podría ponerse rojo de golpe.
  **Mitigación:** las 5 métricas nuevas se marcan como **informativas** en `MetricSpec`
  (`afectaEstado: false`) en esta entrega. El status se sigue calculando con las 5
  originales. Se habilitan para el cálculo una vez validados los umbrales con el vivero.

- **[Pérdida de contexto en la vista de sector]** El usuario que entra a un sector
  problemático ya no ve las métricas que lo explican. **Mitigación:** el sector muestra una
  línea con el estado ambiental resumido de su macro-zona y un link directo al panel de
  sensado de esa zona (`/mapa?zona=MZ-3`).

- **[Divergencia con el firmware]** Si el PR #16 cambia claves o unidades antes de
  mergearse, la ingesta queda desalineada. **Mitigación:** las claves se centralizan en una
  constante única del backend, espejada de `contrato.h`, con el archivo de origen
  referenciado en un comentario. `FAIL_ON_UNKNOWN_PROPERTIES = false` evita que una clave
  extra rompa la ingesta.

- **[Trade-off del layout]** 400px de columna derecha achican la grilla ~25%. En zonas con
  muchos sectores por fila, los cuadraditos quedan chicos. **Aceptado:** la disposición es
  configurable por el usuario (HU-18 CA-01).

## Migration Plan

1. Backend primero: entidad, ingesta, DTOs, seed. Verificar que `/api/nursery` devuelve las
   zonas con `lectura` y `nodo`.
2. Front: tipos → mock → http repository → componentes. El mock y el HTTP se actualizan
   juntos para no romper `VITE_DATA_SOURCE=mock`.
3. Vista de sector: quitar tiles y gráfico al final, cuando el panel de zona ya funciona.
4. Script SQL manual documentado, a correr sólo si se quiere conservar datos existentes.

**Rollback:** revertir el commit. No hay migración destructiva automática (las columnas
viejas de `sector` sólo se borran corriendo el script manual).

## Open Questions

- **Rangos agronómicos de las 5 métricas nuevas** — ABIERTA. Pendiente de validar con el
  vivero San Ignacio. Hasta entonces son informativas (`afectaEstado: false`) y no tienen
  efecto sobre el estado de los sectores. Cuando se validen, alcanza con poner el flag en
  `true` en `NurseryConstants.SPECS` y en `mock/specs.ts`.
- **Sensor UV real** — ABIERTA. Si se incorpora, aplicar la separación descrita en D5.
- ~~**¿El sector debería mostrar un resumen mínimo del ambiente de su zona, o nada?**~~ —
  RESUELTA al implementar: una franja de contexto bajo el header del sector, con el nombre
  de la macro-zona y link a `/mapa?zona={id}`. Sin repetir valores, para no reintroducir la
  confusión que el cambio viene a eliminar.
