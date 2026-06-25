# Design: database-persistence

## Context
El sistema requiere persistencia para reflejar el estado actual del vivero a través de reinicios del backend. Además, la ingestión de telemetría debe actualizar de forma persistente y concurrente la base de datos para que los datos queden registrados permanentemente.

## Decisions

### 1. Motor de Base de Datos y Docker
Se utilizará **PostgreSQL 17** por su robustez, soporte nativo de tipos de datos JSON y excelente rendimiento con JPA/Hibernate.
Expondremos el contenedor en el puerto estándar **`5432`**.

### 2. Modelo de Datos Simplificado (Desnormalización de Métricas)
En lugar de mapear una relación `1:N` entre `Sector` y `Metric` (lo que requeriría 5 registros por sector, totalizando 3000 filas de métricas y haciendo que las consultas de snapshot requieran múltiples joins o N+1 queries), se propone almacenar las lecturas de los sensores directamente como columnas en la tabla `sector`:
- `hum_sus_raw` (Double)
- `hum_amb_raw` (Double)
- `temp_raw` (Double)
- `ce_raw` (Double)
- `uv_raw` (Double)

Al consultar el snapshot o detalles, el backend construirá dinámicamente los objetos `Metric` mapeados a los DTOs correspondientes utilizando estas columnas y las especificaciones fijas en `NurseryConstants.SPECS`. Esto simplifica la lógica, mejora el rendimiento de base de datos y reduce la sobrecarga de Hibernate.

### 3. Entidades JPA
Se definen dos entidades principales:
* **`ZonaEntity`**: Mapea la tabla `zona`. Tiene las columnas `id` (MZ-1 a MZ-6), `name` y `sub`.
* **`SectorEntity`**: Mapea la tabla `sector`. Tiene columnas para:
  - `id` (PK, ej. MZ-1-001)
  - `n` (Índice numérico)
  - `status` (ok, warning, critical, offline)
  - `color` (Hex)
  - `statusLabel` (Saludable, En observación, etc.)
  - `tip` (Texto hover)
  - `reason` (Razón de alerta)
  - `valve` (Regando, Cerrada)
  - `pump` (Dosificando, En espera)
  - `shade` (Porcentaje de cobertura)
  - `ago` (Último reporte)
  - `stale` (Fuera de línea)
  - `diagnosisEstado`, `diagnosisConf`, `diagnosisSev` (Datos del diagnóstico actual)
  - Columnas de métricas crudas (`humSusRaw`, `humAmbRaw`, `tempRaw`, `ceRaw`, `uvRaw`).

### 4. Inicialización Automática (Database Seeding)
Se implementa un script semilla `src/main/resources/data.sql` que es ejecutado automáticamente por Spring Boot en el arranque cuando las tablas están vacías:
1. Inserta las 6 macrozonas.
2. Inserta los 600 sectores con sus nombres y coordenadas iniciales en estado `"offline"` (valores de sensores como `NULL`).
3. Utiliza la cláusula `ON CONFLICT (id) DO NOTHING` para evitar duplicados en arranques subsecuentes.
Esto garantiza que la primera vez que se inicia el sistema, el vivero arranca con la misma maqueta base vacía/offline coherente sobre la cual se inyectarán las telemetrías MQTT.
