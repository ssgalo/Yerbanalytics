# Contexto: Persistencia Física y DER

## Tablas de Base de Datos
Las entidades agregadas previamente carecían de scripts físicos de base de datos. En este cambio, se creó un archivo `schema.sql` que materializa el modelo productivo y se removió la persistencia de las variables de simulación que ensuciaban la base de datos.

## Diagrama Entidad-Relación (DER)

A continuación se muestra el esquema físico de la base de datos en PostgreSQL, denotando las relaciones entre zonas, sectores, dispositivos de hardware, y entidades misceláneas de configuración y registro:

```mermaid
erDiagram
    zona {
        VARCHAR(255) id PK
        VARCHAR(255) name
        VARCHAR(255) sub
    }
    
    sector {
        VARCHAR(255) id PK
        VARCHAR(255) zona_id FK
        INTEGER n
        VARCHAR(255) status
        VARCHAR(255) color
        VARCHAR(255) status_label
        VARCHAR(255) tip
        VARCHAR(255) reason
        VARCHAR(255) diagnosis_estado
        DOUBLE_PRECISION diagnosis_conf
        VARCHAR(255) diagnosis_sev
        VARCHAR(255) actuador_valve
        VARCHAR(255) actuador_pump
        INTEGER actuador_shade
        BIGINT last_reading_time
        DOUBLE_PRECISION hum_sus_raw
        DOUBLE_PRECISION hum_amb_raw
        DOUBLE_PRECISION temp_raw
        DOUBLE_PRECISION ce_raw
        DOUBLE_PRECISION uv_raw
    }

    dispositivo {
        VARCHAR(255) id PK
        VARCHAR(255) serial UK
        VARCHAR(255) tipo
        VARCHAR(255) zona_id
        VARCHAR(255) sector_id
        INTEGER bateria
        INTEGER senal
        BIGINT ultimo_update
        VARCHAR(255) falla
    }

    historial_evento {
        VARCHAR(255) id PK
        VARCHAR(255) sector_id
        VARCHAR(255) zona_id
        VARCHAR(255) zona_name
        VARCHAR(255) tipo
        BIGINT ts
        TEXT lectura
        TEXT decision
        TEXT accion
        VARCHAR(255) res
        VARCHAR(255) sev
        VARCHAR(255) metric_key
        DOUBLE_PRECISION valor_antes
        DOUBLE_PRECISION umbral_recuperacion
        BIGINT latency_ms
        BOOLEAN evo_show
        VARCHAR(255) evo_metric
        VARCHAR(255) evo_antes
        VARCHAR(255) evo_ahora
        VARCHAR(255) evo_unit
        VARCHAR(255) evo_delta
        VARCHAR(255) evo_latencia
        VARCHAR(255) evo_verdict
        BIGINT evo_evaluado_ts
        BOOLEAN bloqueo_repeticion
    }

    configuracion_operativa {
        INTEGER id PK
        DOUBLE_PRECISION riego_tiempo_max_seg
        DOUBLE_PRECISION riego_vol_max_diario_ml
        DOUBLE_PRECISION insumo_dosis_max_24h_ml
        DOUBLE_PRECISION mediasombra_apertura_max_pct
        INTEGER seguimiento_latencia_min
        DOUBLE_PRECISION seguimiento_delta_min
        VARCHAR(255) updated_by
        BIGINT updated_ts
    }

    rustificacion_etapa {
        INTEGER orden PK
        INTEGER dia_desde
        INTEGER dia_hasta
        INTEGER apertura_pct
    }

    umbral_metrica {
        VARCHAR(255) metric_key PK
        DOUBLE_PRECISION ideal_min
        DOUBLE_PRECISION ideal_max
        DOUBLE_PRECISION warn_min
        DOUBLE_PRECISION warn_max
        DOUBLE_PRECISION crit_min
        DOUBLE_PRECISION crit_max
    }

    topologia_layout {
        INTEGER id PK
        INTEGER macro_zonas_por_fila
        INTEGER sectores_por_fila
    }

    modo_operacion {
        INTEGER id PK
        VARCHAR(255) modo
    }

    sensor_simulado {
        BIGSERIAL id PK
        VARCHAR(255) serial_key UK
        VARCHAR(255) serial
        VARCHAR(255) zona_id
    }

    zona ||--o{ sector : "contiene"
    zona ||--o{ dispositivo : "alberga nodos testigo"
    sector ||--o{ dispositivo : "alberga actuadores"
    sector ||--o{ historial_evento : "genera eventos"
```
