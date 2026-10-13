# Tasks: add-umbral-alerta-metricas

## 1. Backend
- [x] 1.1 `UmbralMetricaEntity`: `alert_min`, `alert_max` (`Double`, nulables)
- [x] 1.2 `UmbralMetrica` DTO con `alertMin`/`alertMax`; valor por defecto = medio de la franja
- [x] 1.3 Validación de coherencia con los puntos; persistencia al guardar
- [x] 1.4 `NurseryService`: alertas por métrica (CRITICAL / WARNING), orden por severidad, tope 8
- [x] 1.5 Tests: defecto, validación, generación de alertas

## 2. Frontend
- [x] 2.1 `MetricThreshold` con `alertMin`/`alertMax`; mock de fábrica con la misma fórmula
- [x] 2.2 `umbralError` y conteo de cambios con los puntos
- [x] 2.3 Editor de bandas: barra con zonas, manijas arrastrables/teclado, seis campos
- [x] 2.4 Tests del editor y de la validación

## 3. Cierre
- [x] 3.1 `npm test`, `lint`, `build`; `./mvnw test`
- [x] 3.2 Verificación visual
