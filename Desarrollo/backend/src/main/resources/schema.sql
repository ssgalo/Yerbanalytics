-- Yerbanalytics Productive Database Schema (PostgreSQL)

CREATE TABLE IF NOT EXISTS zona (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    sub VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS sector (
    id VARCHAR(255) PRIMARY KEY,
    zona_id VARCHAR(255) NOT NULL,
    n INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    color VARCHAR(255) NOT NULL,
    status_label VARCHAR(255) NOT NULL,
    tip VARCHAR(255) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    diagnosis_estado VARCHAR(255) NOT NULL,
    diagnosis_conf DOUBLE PRECISION,
    diagnosis_sev VARCHAR(255) NOT NULL,
    actuador_valve VARCHAR(255) NOT NULL,
    actuador_pump VARCHAR(255) NOT NULL,
    actuador_shade INTEGER NOT NULL,
    last_reading_time BIGINT,
    hum_sus_raw DOUBLE PRECISION,
    hum_amb_raw DOUBLE PRECISION,
    temp_raw DOUBLE PRECISION,
    ce_raw DOUBLE PRECISION,
    uv_raw DOUBLE PRECISION,
    CONSTRAINT fk_sector_zona FOREIGN KEY (zona_id) REFERENCES zona(id)
);

CREATE TABLE IF NOT EXISTS dispositivo (
    id VARCHAR(255) PRIMARY KEY,
    serial VARCHAR(255) NOT NULL UNIQUE,
    tipo VARCHAR(255) NOT NULL,
    zona_id VARCHAR(255),
    sector_id VARCHAR(255),
    bateria INTEGER,
    senal INTEGER,
    ultimo_update BIGINT,
    falla VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS configuracion_operativa (
    id INTEGER PRIMARY KEY,
    riego_tiempo_max_seg DOUBLE PRECISION NOT NULL,
    riego_vol_max_diario_ml DOUBLE PRECISION NOT NULL,
    insumo_dosis_max_24h_ml DOUBLE PRECISION NOT NULL,
    mediasombra_apertura_max_pct DOUBLE PRECISION NOT NULL,
    seguimiento_latencia_min INTEGER NOT NULL,
    seguimiento_delta_min DOUBLE PRECISION NOT NULL,
    updated_by VARCHAR(255),
    updated_ts BIGINT
);

CREATE TABLE IF NOT EXISTS rustificacion_etapa (
    orden INTEGER PRIMARY KEY,
    dia_desde INTEGER NOT NULL,
    dia_hasta INTEGER NOT NULL,
    apertura_pct INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS umbral_metrica (
    metric_key VARCHAR(255) PRIMARY KEY,
    ideal_min DOUBLE PRECISION NOT NULL,
    ideal_max DOUBLE PRECISION NOT NULL,
    warn_min DOUBLE PRECISION NOT NULL,
    warn_max DOUBLE PRECISION NOT NULL,
    crit_min DOUBLE PRECISION NOT NULL,
    crit_max DOUBLE PRECISION NOT NULL
);

CREATE TABLE IF NOT EXISTS historial_evento (
    id VARCHAR(255) PRIMARY KEY,
    sector_id VARCHAR(255) NOT NULL,
    zona_id VARCHAR(255) NOT NULL,
    zona_name VARCHAR(255) NOT NULL,
    tipo VARCHAR(255) NOT NULL,
    ts BIGINT NOT NULL,
    lectura TEXT NOT NULL,
    decision TEXT NOT NULL,
    accion TEXT NOT NULL,
    res VARCHAR(255) NOT NULL,
    sev VARCHAR(255) NOT NULL,
    metric_key VARCHAR(255),
    valor_antes DOUBLE PRECISION,
    umbral_recuperacion DOUBLE PRECISION,
    latency_ms BIGINT,
    evo_show BOOLEAN NOT NULL,
    evo_metric VARCHAR(255),
    evo_antes VARCHAR(255),
    evo_ahora VARCHAR(255),
    evo_unit VARCHAR(255),
    evo_delta VARCHAR(255),
    evo_latencia VARCHAR(255),
    evo_verdict VARCHAR(255),
    evo_evaluado_ts BIGINT,
    bloqueo_repeticion BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS topologia_layout (
    id INTEGER PRIMARY KEY,
    macro_zonas_por_fila INTEGER NOT NULL,
    sectores_por_fila INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS modo_operacion (
    id INTEGER PRIMARY KEY,
    modo VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS sensor_simulado (
    id BIGSERIAL PRIMARY KEY,
    serial_key VARCHAR(255) NOT NULL UNIQUE,
    serial VARCHAR(255) NOT NULL,
    zona_id VARCHAR(255) NOT NULL
);
