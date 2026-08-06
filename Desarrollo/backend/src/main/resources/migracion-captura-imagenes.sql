-- ============================================================================
--  DDL de referencia · captura de imágenes cenitales
--  (openspec/changes/add-captura-imagenes)
-- ----------------------------------------------------------------------------
--  ESTE SCRIPT NORMALMENTE NO HACE FALTA. Las cuatro tablas son NUEVAS y no
--  tocan ninguna existente, así que `ddl-auto=update` las crea sola al arrancar.
--
--  Está acá por dos motivos:
--    1. Dejar el esquema documentado y legible sin tener que deducirlo de las
--       entidades JPA (misma práctica que `migracion-manual.sql`).
--    2. Poder crear las tablas a mano en un entorno con `ddl-auto=none` o donde
--       el usuario de la app no tenga permisos de DDL.
--
--  A diferencia de `migracion-manual.sql`, este script NO migra datos ni borra
--  nada: es puramente aditivo y se puede correr sobre una base poblada.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. Orden de captura.
--    Entidad de primera clase del flujo: es lo que permite saber en qué
--    posición del riel se tomó una foto. La imagen se sube citando este `id`.
--    El estado se persiste (no se deriva) porque el watchdog necesita saber qué
--    órdenes están en vuelo aunque el backend se reinicie.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orden_captura (
    id             VARCHAR(64)  PRIMARY KEY,
    sector_id      VARCHAR(32)  NOT NULL,
    zona_id        VARCHAR(32)  NOT NULL,
    posicion_riel  INTEGER      NOT NULL,
    dispositivo_id VARCHAR(64),                 -- null mientras está PENDIENTE
    estado         VARCHAR(16)  NOT NULL,       -- PENDIENTE|ENTREGADA|RECIBIDA|FALLIDA|VENCIDA|ERROR
    intentos       INTEGER      NOT NULL DEFAULT 1,
    motivo_fallo   VARCHAR(255),                -- conjunto cerrado del contrato
    detalle_fallo  VARCHAR(500),
    creada_en      BIGINT       NOT NULL,
    entregada_en   BIGINT,
    vence_en       BIGINT       NOT NULL
);

-- El watchdog barre por (estado, vence_en) cada pocos segundos.
CREATE INDEX IF NOT EXISTS idx_orden_captura_estado_vence
    ON orden_captura (estado, vence_en);

-- ----------------------------------------------------------------------------
-- 2. Captura.
--    Metadata de la imagen. Los BYTES NO VIVEN ACÁ: el JPEG se guarda en
--    `yerbanalytics.capturas.dir`, particionado por fecha, y esta fila conserva
--    la ruta. Guardarlo como bytea metería varios GB por semana en el pg_dump.
--    El unique sobre orden_id sostiene la idempotencia del 409.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS captura (
    id               VARCHAR(64)  PRIMARY KEY,
    orden_id         VARCHAR(64)  NOT NULL UNIQUE,
    sector_id        VARCHAR(32)  NOT NULL,
    zona_id          VARCHAR(32)  NOT NULL,
    posicion_riel    INTEGER      NOT NULL,
    dispositivo_id   VARCHAR(64)  NOT NULL,
    ancho            INTEGER      NOT NULL,
    alto             INTEGER      NOT NULL,
    bytes            BIGINT       NOT NULL,
    sha256           VARCHAR(64)  NOT NULL,     -- verificado contra lo recibido; sirve de ETag
    capturada_en     BIGINT,                    -- reloj del dispositivo (informativo)
    recibida_en      BIGINT       NOT NULL,     -- reloj del backend (autoridad)
    ruta_archivo     VARCHAR(255) NOT NULL,
    constraints_json VARCHAR(2000)              -- ajustes de cámara realmente aplicados
);

CREATE INDEX IF NOT EXISTS idx_captura_sector
    ON captura (sector_id, recibida_en DESC);

-- ----------------------------------------------------------------------------
-- 3. Dispositivo de cámara.
--    Deliberadamente separado de `dispositivo`: aquel registra el hardware del
--    vivero (nodos y actuadores) mapeado a sectores, con batería y señal. Un
--    dispositivo de captura no tiene nada de eso.
--    El estado operativo NO se almacena: se deriva del silencio desde el último
--    heartbeat, igual que hace HardwareService con los nodos.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dispositivo_camara (
    id                 VARCHAR(64)  PRIMARY KEY,
    nombre             VARCHAR(128) NOT NULL,
    plataforma         VARCHAR(128),            -- informativo; el backend no lo usa para decidir
    refresh_token_hash VARCHAR(64)  NOT NULL,   -- huella SHA-256, nunca el valor en claro
    ultimo_heartbeat   BIGINT,
    capturas_ok        INTEGER      NOT NULL DEFAULT 0,
    capturas_error     INTEGER      NOT NULL DEFAULT 0,
    captura_listo      BOOLEAN      NOT NULL DEFAULT FALSE,
    creado_en          BIGINT       NOT NULL,
    revocado           BOOLEAN      NOT NULL DEFAULT FALSE
);

-- ----------------------------------------------------------------------------
-- 4. Diagnóstico.
--    SIN COLUMNA DE ORIGEN, a propósito. Un diagnóstico cargado a mano desde el
--    simulador y uno emitido por el modelo de IA son la misma fila, porque
--    entran por la misma operación (POST /api/diagnosticos). Una marca que
--    distinguiera el ensayo de la operación real volvería infiel el ensayo.
--
--    Consecuencia asumida: no se pueden purgar selectivamente los diagnósticos
--    de prueba. La purga posible es por fecha o por sector, que alcanza porque
--    todo diagnóstico está anclado a una captura fechada.
--
--    captura_id es NOT NULL: todo diagnóstico nace del análisis de una imagen.
--    Esa restricción es lo que impide falsear el camino, incluso por accidente.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS diagnostico (
    id         VARCHAR(64) PRIMARY KEY,
    sector_id  VARCHAR(32) NOT NULL,
    zona_id    VARCHAR(32) NOT NULL,
    captura_id VARCHAR(64) NOT NULL,
    estado     VARCHAR(64) NOT NULL,            -- taxonomía de la plataforma
    conf       DOUBLE PRECISION NOT NULL,       -- 0-100
    sev        VARCHAR(16) NOT NULL,
    creado_en  BIGINT      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_diagnostico_creado
    ON diagnostico (creado_en DESC);

COMMIT;

-- ----------------------------------------------------------------------------
-- Verificación posterior sugerida:
--   SELECT estado, count(*) FROM orden_captura GROUP BY estado;
--   SELECT c.id, c.sector_id, c.bytes, c.ruta_archivo,
--          to_timestamp(c.recibida_en / 1000) AS recibida
--     FROM captura c ORDER BY c.recibida_en DESC LIMIT 10;
--
-- Purga de capturas y diagnósticos por antigüedad (el particionado por fecha
-- del filesystem hace que borrar los archivos sea un rm del directorio del día):
--   DELETE FROM diagnostico WHERE creado_en < <epoch_ms>;
--   DELETE FROM captura     WHERE recibida_en < <epoch_ms>;
-- ----------------------------------------------------------------------------
