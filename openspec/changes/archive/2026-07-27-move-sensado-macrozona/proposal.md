# Mover los valores sensados del sector a la macro-zona

## Why

El alcance del proyecto define **un solo nodo sensor testigo por macro-zona**: los 100
sectores de una MZ comparten físicamente la misma lectura. Hoy la UI muestra las métricas
dentro del detalle de cada sector, lo que sugiere —falsamente— que cada sector tiene
instrumentación propia. Cuando se conecte el sensado real, los 100 sectores de una zona
van a mostrar exactamente el mismo número, y la vista de sector va a mentir sobre el
origen del dato.

Además, el firmware del nodo (PR #16, `add-firmware-esp32`) ya publica **más métricas de
las que la plataforma modela**: la sonda de suelo RS-485 entrega temperatura de sustrato,
pH, N, P y K, que hoy no existen ni en el backend ni en el front. Conviene resolver las
dos cosas juntas: el panel de sensado se muda al lugar correcto y nace ya preparado para
todas las métricas del nodo.

## What Changes

### Modelo de datos: la lectura pertenece a la macro-zona

- **BREAKING**: las lecturas dejan de vivir en `SectorEntity` (`hum_sus_raw`, `hum_amb_raw`,
  `temp_raw`, `ce_raw`, `uv_raw`, `last_reading_time`) y pasan a `ZonaEntity`, junto con el
  estado del nodo testigo (`battery`, `signal`, `mac`).
- La ingesta MQTT (`MqttTelemetryReceiver` → `NurseryService.updateTelemetry`) deja de
  abanicar la lectura a los 100 sectores: escribe una vez en la zona. El estado de salud de
  cada sector se sigue recalculando a partir de la lectura de su zona, así que el mapa y el
  motor de reglas no cambian de comportamiento.
- `NurseryData.zonas[]` expone `lectura` (métricas evaluadas contra sus umbrales) y `nodo`
  (batería, señal, última lectura, si está caído).

### Cinco métricas nuevas, dos descartadas

Se suman las métricas independientes que publica la sonda: `tempSuelo`, `phSuelo`, `n`,
`p`, `k`. El set operativo pasa de 5 a **10 métricas**.

Se descartan **`salinidad` y `tds`**: la sonda las calcula multiplicando la misma medición
de conductividad por un factor fijo, así que se mueven en proporción exacta con `ce` y no
aportan información nueva. El backend igualmente las **tolera** en el payload sin fallar,
para que el firmware pueda publicar con `ENVIAR_METRICAS_EXTENDIDAS` activo sin romper la
ingesta.

### Dos correcciones de unidad contra el contrato del firmware

- `ce`: la sonda emite **µS/cm** y la plataforma asume **dS/m** (factor 1000). La ingesta
  normaliza a dS/m; los umbrales agronómicos actuales quedan válidos.
- `uv`: hoy se rotula "Radiación UV" en UVI, pero el nodo mide **% de luz con un LDR** —
  no mide UV. Se re-rotula a **"Luminosidad"** en `%` con umbrales en esa unidad,
  conservando la clave `uv` del contrato MQTT para no romper el firmware ya escrito.

### UI

- **Vista de macro-zona** (`/mapa`): panel nuevo **"Valores sensados · nodo testigo"** en
  la columna derecha, que se ensancha de 320px a 400px (la grilla de sectores se achica en
  consecuencia). Muestra las 10 métricas con su color de estado, más batería, señal y
  antigüedad de la última lectura del nodo.
- **Histórico por métrica**: al hacer clic en cualquier métrica del panel se abre su serie
  histórica con selector de rango 24h/7d/30d. Hoy el gráfico existe sólo para humedad de
  sustrato y está clavado en el sector; pasa a estar disponible para las 10 métricas a
  nivel zona.
- **BREAKING** — **Vista de sector** (`/sector/:id`): se quitan la fila de tiles de
  métricas y el gráfico principal. El sector queda con lo que sí es suyo: diagnóstico de
  IA, actuadores, seguimiento post-acción e historial de acciones.

### Configuración agronómica (HU-15)

Los umbrales de las 5 métricas nuevas se agregan a `umbral_metrica` y quedan editables
desde la pantalla de configuración, igual que los actuales. Los rangos iniciales son
**provisionales** y deben validarse con el vivero.

## Capabilities

### New Capabilities

- `sensado-macrozona`: panel de valores sensados a nivel macro-zona — set de 10 métricas,
  evaluación contra umbrales, estado del nodo testigo e histórico navegable por métrica.
- `sensado-persistencia`: modelo de datos e ingesta de la lectura a nivel zona —
  entidad, normalización de unidades, tolerancia a métricas no modeladas y recálculo del
  estado de los sectores.

### Modified Capabilities

- `production-map`: la vista de macro-zona incorpora el panel de sensado y el histórico;
  cambia la disposición de columnas y se retira el selector de zonas de adentro de la vista.
- `app-shell`: el detalle de macro-zona deja de ser un destino del sidebar — se entra
  eligiendo una macro-zona en el panel general.
- `sector-detail`: se eliminan los requisitos de tiles de métricas y gráfico principal del
  detalle de sector.
- `data-layer`: los contratos de dominio y el repositorio exponen la lectura y el nodo a
  nivel zona, y la serie histórica por métrica.

## Impact

**Backend** (`Desarrollo/backend/`)
- `model/ZonaEntity.java` (+ campos de lectura y nodo), `model/SectorEntity.java` (− campos raw)
- `mqtt/MqttTelemetryPayload.java`, `mqtt/MqttTelemetryReceiver.java`, `mqtt/MqttTelemetrySimulator.java`
- `service/NurseryService.java` (`updateTelemetry`, `buildMetricsList`), `service/ConfiguracionService.java`
- `constant/NurseryConstants.java` (`SPECS`), `dto/Zona.java`, `dto/Sector.java`, `dto/EnvioTelemetria.java`
- `engine/rules/` — las reglas leen la métrica desde la zona
- `resources/data.sql` — umbrales nuevos + re-rango de `uv`

**Frontend** (`Desarrollo/frontend/`)
- `src/types/domain.ts`, `src/data/mock/specs.ts`, `src/data/mock/generators.ts`,
  `src/data/mock/sectorDetail.ts`, `src/data/http/httpRepository.ts`, `src/data/selectors.ts`
- `src/features/map/` — panel nuevo, histórico, `MapPage.module.css`
- `src/features/sector/` — se borran `MetricTile` y `MainChart` del árbol de la vista
- `src/features/simulacion/` — el envío manual de telemetría cubre las 10 métricas

**Migración de base de datos**: requiere pasos manuales (mover los valores de `sector` a
`zona`, borrar columnas viejas, re-seed de umbrales). `ddl-auto=update` agrega columnas
pero no migra datos ni elimina las obsoletas. Se documentan como tarea explícita.

**No incluye**: cambios en el firmware (el contrato MQTT se respeta tal como está en el
PR #16), ni el sensor UV real, ni persistencia de series históricas —el histórico se sigue
derivando como hoy.
