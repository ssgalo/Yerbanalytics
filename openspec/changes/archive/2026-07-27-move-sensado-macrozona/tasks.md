# Tareas — sensado a nivel macro-zona

## 1. Backend · modelo de datos

- [x] 1.1 Agregar a `ZonaEntity` las columnas de las 10 métricas (`hum_sus_raw`, `hum_amb_raw`, `temp_raw`, `temp_suelo_raw`, `ce_raw`, `ph_suelo_raw`, `n_raw`, `p_raw`, `k_raw`, `uv_raw`) más `nodo_mac`, `nodo_battery`, `nodo_signal` y `last_reading_time`
- [x] 1.2 Quitar de `SectorEntity` las columnas `hum_sus_raw`, `hum_amb_raw`, `temp_raw`, `ce_raw`, `uv_raw` y `last_reading_time`
- [x] 1.3 Extender `NurseryConstants.SPECS` a las 10 métricas con las bandas de D3, marcando `tempSuelo`/`phSuelo`/`n`/`p`/`k` como informativas (`afectaEstado = false`) y re-rotulando `uv` a "Luminosidad" en `%`
- [x] 1.4 Sembrar en `data.sql` los umbrales de las 5 métricas nuevas y actualizar el rango de `uv` a la escala de porcentaje

## 2. Backend · ingesta MQTT

- [x] 2.1 Extender `MqttTelemetryPayload.MetricsPayload` con `tempSuelo`, `phSuelo`, `n`, `p`, `k`
- [x] 2.2 Configurar el `ObjectMapper` del receiver con `FAIL_ON_UNKNOWN_PROPERTIES = false` para tolerar `salinidad`, `tds` y claves futuras del firmware
- [x] 2.3 Centralizar las claves del contrato en una constante única del backend, espejada de `Desarrollo/embebido/comun/contrato.h`, con referencia al archivo de origen en un comentario
- [x] 2.4 Reescribir `NurseryService.updateTelemetry` para persistir una sola lectura en la zona (semántica de lectura parcial: las métricas ausentes conservan su valor previo)
- [x] 2.5 Convertir `ce` de µS/cm a dS/m en la ingesta antes de persistir
- [x] 2.6 Adaptar `buildMetricsList` para leer de `ZonaEntity` y calcular el status del sector sólo con las métricas no informativas
- [x] 2.7 Extender `MqttTelemetrySimulator` para generar las 10 métricas
- [x] 2.8 Verificar que el motor de reglas (`engine/rules/`) lee las métricas desde la zona sin cambio de comportamiento

## 3. Backend · API y configuración

- [x] 3.1 Extender el DTO `Zona` con `lectura` (métricas evaluadas, ts, ago, stale) y `nodo` (mac, batería, señal, bateriaBaja)
- [x] 3.2 Quitar `metrics`, `ago` y `stale` del DTO `Sector`
- [x] 3.3 Extender `EnvioTelemetria`/`SimulacionController` para aceptar las 10 métricas en el envío manual
- [x] 3.4 Adaptar `ConfiguracionService` para exponer y persistir los umbrales de las 10 métricas, marcando como provisionales los de las 5 nuevas
- [x] 3.5 Verificar `GET /api/nursery`: las zonas traen `lectura` y `nodo`, los sectores ya no traen métricas

## 4. Backend · migración de base

- [x] 4.1 Escribir `Desarrollo/backend/src/main/resources/migracion-manual.sql` con el `UPDATE zona ... FROM sector` (copia de valores), los `ALTER TABLE sector DROP COLUMN` y el re-seed de umbrales
- [x] 4.2 Documentar en el README del backend que ese script es manual, no lo cubre `ddl-auto=update`, y en qué orden ejecutarlo respecto del primer arranque

## 5. Frontend · contratos y capa de datos

- [x] 5.1 Definir `LecturaZona` y `NodoTestigo` en `src/types/domain.ts` y anidarlos en `Zona`; agregar `afectaEstado` a `MetricSpec`
- [x] 5.2 Quitar `metrics`/`ago`/`stale` de `Sector` y `metricTiles`/`mainLine`/`mainArea`/`mainMin`/`mainMax` de `SectorDetail`
- [x] 5.3 Extender `src/data/mock/specs.ts` a las 10 métricas con las bandas de D3 (incluye re-rótulo de `uv`)
- [x] 5.4 Adaptar `src/data/mock/generators.ts` para generar la lectura y el estado del nodo a nivel zona, de forma determinística
- [x] 5.5 Limpiar `src/data/mock/sectorDetail.ts`: eliminar la construcción de tiles y del gráfico principal
- [x] 5.6 Adaptar `src/data/http/httpRepository.ts` al nuevo contrato de `/api/nursery`
- [x] 5.7 Agregar al repositorio/selectores la serie histórica por métrica de zona (`zonaId`, `metricKey`, `range`)
- [x] 5.8 Actualizar los tests del mock (`generators.test.ts` y los que dependan de métricas por sector)

## 6. Frontend · panel de sensado

- [x] 6.1 Crear `SensadoCard` en `src/features/map/components/`: 10 métricas en grilla de 2 columnas, agrupadas en "Ambiente" y "Nutrición del sustrato", con valor, unidad, rango óptimo y color de estado
- [x] 6.2 Mostrar "—" sin color para las métricas sin dato
- [x] 6.3 Agregar la fila de estado del nodo testigo: batería, señal y antigüedad de la última lectura, con señalamiento visible de batería baja y de lectura desactualizada
- [x] 6.4 Marcar visualmente las métricas informativas y sus rangos como provisionales
- [x] 6.5 Mover `MainChart` de `features/sector/components/` a un lugar compartido y adaptarlo para graficar cualquier métrica
- [x] 6.6 Implementar la selección de métrica (default `humSus`), destacada visualmente, que se mantiene al cambiar de macro-zona
- [x] 6.7 Integrar el gráfico con selector de rango 24h/7d/30d en la vista de macro-zona

## 7. Frontend · layout y vista de sector

- [x] 7.1 Cambiar `MapPage.module.css` a `grid-template-columns: 1fr 400px`
- [x] 7.2 Ordenar la columna derecha: `SensadoCard` → `ZoneSummary` → `ProblemsList`
- [x] 7.3 Quitar de `SectorPage.tsx` la fila de tiles y el gráfico principal; borrar `MetricTile.tsx`/`.module.css`
- [x] 7.4 Agregar en el detalle de sector la referencia a su macro-zona con link a `/mapa?zona={zonaId}`
- [x] 7.5 Extender el formulario de envío manual (`features/simulacion/`) a las 10 métricas
- [x] 7.6 Verificar que la grilla achicada sigue legible en las disposiciones configurables de HU-18 CA-01

## 8. Verificación

- [x] 8.1 `npm run lint` y `npm run test` en el frontend sin warnings
- [x] 8.2 Correr con `VITE_DATA_SOURCE=mock`: panel con 10 métricas, histórico por métrica, sector sin tiles
- [x] 8.3 Correr con `VITE_DATA_SOURCE=http` contra el backend: paridad de comportamiento con el mock
- [x] 8.4 Publicar telemetría MQTT con el set extendido completo (incluyendo `salinidad` y `tds`) y confirmar que la ingesta no falla y descarta las no modeladas
- [x] 8.5 Publicar una lectura parcial y confirmar que las métricas ausentes conservan su valor
- [x] 8.6 Confirmar que una métrica informativa fuera de banda se colorea pero no cambia el estado de los sectores
- [x] 8.7 Confirmar que `ce` en µS/cm llega a la UI en dS/m con el valor correcto

## 9. Pendientes documentados

- [x] 9.1 Registrar como abierta la validación agronómica de los rangos de `tempSuelo`, `phSuelo`, `n`, `p` y `k` con el vivero San Ignacio, y la habilitación posterior de esas métricas para el cálculo de estado
- [x] 9.2 Registrar en `design.md` (D5) la deuda del sensor UV real: si se incorpora, separar `uv` (radiación, UVI) de `luz` (luminosidad, %) en `contrato.h`, `SPECS`, `umbral_metrica` y las reglas
- [x] 9.3 Actualizar `CLAUDE.md` (§2 glosario y §6 backend) con el modelo de sensado por macro-zona
