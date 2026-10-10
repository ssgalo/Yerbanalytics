-- =============================================================================
-- Auditoría de seguridad append-only (HU-20 CA-03, design D7).
--
-- El backend ejecuta este script SOLO al arrancar (SeguridadInicializador), después de que
-- Hibernate creó la tabla. Es idempotente: se puede correr las veces que haga falta.
--
-- Correrlo a mano hace falta únicamente si el usuario de la base que usa la aplicación no puede
-- crear funciones (el arranque falla con un mensaje que lo dice). En ese caso:
--     psql -U <usuario-con-permisos> -d yerbanalytics -f auditoria-triggers.sql
-- =============================================================================

CREATE OR REPLACE FUNCTION auditoria_seguridad_inalterable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'auditoria_seguridad es append-only: % rechazado', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS auditoria_seguridad_sin_update_delete ON auditoria_seguridad;
CREATE TRIGGER auditoria_seguridad_sin_update_delete
    BEFORE UPDATE OR DELETE ON auditoria_seguridad
    FOR EACH ROW EXECUTE FUNCTION auditoria_seguridad_inalterable();

DROP TRIGGER IF EXISTS auditoria_seguridad_sin_truncate ON auditoria_seguridad;
CREATE TRIGGER auditoria_seguridad_sin_truncate
    BEFORE TRUNCATE ON auditoria_seguridad
    FOR EACH STATEMENT EXECUTE FUNCTION auditoria_seguridad_inalterable();
