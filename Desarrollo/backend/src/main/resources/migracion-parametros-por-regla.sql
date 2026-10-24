-- ============================================================
-- Migración manual: los parámetros de reglas pasan a ser de cada pieza (rediseno-motor-reglas D-7).
--
-- Antes un override era de la clave (parametro_regla: clave → valor) y lo leían todas las piezas
-- que declaraban esa clave. Ahora cada pieza tiene su copia (regla_parametro: consumidor + clave →
-- valor) y editar la de una no cambia la de otra.
--
-- Este script copia cada override vigente a TODAS las piezas que leen esa clave como propia, así
-- nadie pierde un valor editado, y después borra la tabla vieja. Las claves que un ejecutor lee de
-- otra pieza (D-2: el despacho de riego, el servicio del plan de nutrición) no tienen copia propia:
-- van sólo a la regla dueña.
--
-- La lista (clave, pieza) se generó desde el catálogo real (CatalogoParametros.declarantes) al
-- momento del cambio. Sin correr el script las copias quedan en fábrica y el backend avisa con un
-- WARN al arrancar mientras encuentre parametro_regla con filas.
--
-- Correr a mano UNA vez, con el backend nuevo ya arrancado al menos una vez (para que Hibernate
-- cree regla_parametro; igual se crea acá si falta). Idempotente: sin la tabla vieja no hace nada,
-- y una copia ya existente no se pisa.
-- ============================================================

BEGIN;

CREATE TABLE IF NOT EXISTS regla_parametro (
    consumidor  VARCHAR(255) NOT NULL,
    clave       VARCHAR(255) NOT NULL,
    valor       TEXT         NOT NULL,
    updated_by  VARCHAR(255),
    updated_ts  BIGINT,
    PRIMARY KEY (consumidor, clave)
);

DO $$
BEGIN
    IF to_regclass('parametro_regla') IS NULL THEN
        RAISE NOTICE 'parametro_regla no existe: nada que migrar.';
        RETURN;
    END IF;

    INSERT INTO regla_parametro (consumidor, clave, valor, updated_by, updated_ts)
    SELECT c.consumidor, p.clave, p.valor, p.updated_by, p.updated_ts
    FROM parametro_regla p
    JOIN (VALUES
            ('seguridad.antiguedad-max-lectura', 'StaleSensorRule'),
            ('seguridad.rango-hum-min', 'ReadingValidationGuard'),
            ('seguridad.rango-hum-max', 'ReadingValidationGuard'),
            ('seguridad.rango-temp-min', 'ReadingValidationGuard'),
            ('seguridad.rango-temp-max', 'ReadingValidationGuard'),
            ('seguridad.rango-ph-min', 'ReadingValidationGuard'),
            ('seguridad.rango-ph-max', 'ReadingValidationGuard'),
            ('seguridad.rango-ce-min', 'ReadingValidationGuard'),
            ('seguridad.rango-ce-max', 'ReadingValidationGuard'),
            ('seguridad.rango-luz-min', 'ReadingValidationGuard'),
            ('seguridad.rango-luz-max', 'ReadingValidationGuard'),
            ('seguridad.relecturas-invalidas', 'ReadingValidationGuard'),
            ('riego.umbral-humedad', 'FueraDeVentanaRiegoRule'),
            ('riego.umbral-humedad', 'PausaTrasAplicacionRule'),
            ('riego.umbral-humedad', 'PosponerPorLluviaRule'),
            ('riego.umbral-humedad', 'RiegoPorDeficitRule'),
            ('riego.umbral-humedad', 'HardwareFaultService'),
            ('riego.lluvia-probabilidad', 'PosponerPorLluviaRule'),
            ('riego.umbral-critico', 'CicloLecturaRiegoRule'),
            ('riego.umbral-critico', 'DeficitCriticoRule'),
            ('riego.umbral-critico', 'FueraDeVentanaRiegoRule'),
            ('riego.umbral-critico', 'PausaTrasAplicacionRule'),
            ('riego.umbral-critico', 'PosponerPorLluviaRule'),
            ('riego.umbral-critico', 'RiegoPorDeficitRule'),
            ('riego.humedad-objetivo', 'RiegoPorDeficitRule'),
            ('riego.humedad-objetivo', 'SolarStressRule'),
            ('riego.litros-por-punto', 'RiegoPorDeficitRule'),
            ('riego.litros-por-punto', 'SolarStressRule'),
            ('riego.volumen-max-evento', 'DeficitCriticoRule'),
            ('riego.volumen-max-evento', 'RiegoPorDeficitRule'),
            ('riego.volumen-max-evento', 'SolarStressRule'),
            ('riego.caudal-emisor', 'DeficitCriticoRule'),
            ('riego.caudal-emisor', 'RiegoPorDeficitRule'),
            ('riego.caudal-emisor', 'SolarStressRule'),
            ('riego.saturacion-bloqueo', 'SustratoSaturadoRule'),
            ('riego.saturacion-alerta', 'SustratoSaturadoRule'),
            ('riego.ventana-normal', 'FueraDeVentanaRiegoRule'),
            ('riego.ventana-normal', 'SolarStressRule'),
            ('riego.lluvia-mm', 'PosponerPorLluviaRule'),
            ('riego.lluvia-ventana', 'PosponerPorLluviaRule'),
            ('riego.pausa-tras-aplicacion', 'PausaTrasAplicacionRule'),
            ('riego.pausa-tras-aplicacion', 'SolarStressRule'),
            ('riego.exceptuado-bloqueo', 'DeficitCriticoRule'),
            ('riego.sectores-simultaneos', 'DespachoRiego'),
            ('insumo.max-dosis-24h', 'DailyDoseLimitRule'),
            ('insumo.dosis-fertilizante', 'ApplicationService'),
            ('insumo.dosis-fitosanitario', 'ApplicationService'),
            ('insumo.intervalo-fertilizante', 'ApplicationPreconditions'),
            ('insumo.intervalo-fitosanitario', 'ApplicationPreconditions'),
            ('insumo.ventana-manana', 'ApplicationPreconditions'),
            ('insumo.ventana-tarde', 'ApplicationPreconditions'),
            ('insumo.temp-max-aplicar', 'ApplicationPreconditions'),
            ('insumo.humedad-min-aplicar', 'ApplicationPreconditions'),
            ('insumo.lluvia-impide-mm', 'ApplicationPreconditions'),
            ('insumo.lluvia-impide-horas', 'ApplicationService'),
            ('insumo.reingreso-horas', 'ApplicationService'),
            ('insumo.stock-aviso', 'TankService'),
            ('insumo.tope-fitosanitario', 'ApplicationPreconditions'),
            ('insumo.tope-fitosanitario-dias', 'ApplicationService'),
            ('insumo.pendiente-vence-dias', 'ApplicationService'),
            ('nutricion.intervalo-plan', 'NutritionPlanRule'),
            ('nutricion.ce-baja', 'LowEcRule'),
            ('nutricion.ce-lecturas', 'LowEcRule'),
            ('nutricion.sin-fertilizar-dias', 'LowEcRule'),
            ('biotico.dpv-saturado', 'FungalDamageRule'),
            ('biotico.dpv-saturado', 'FungalConditionRule'),
            ('biotico.dpv-calor-seco', 'FoliarPestRule'),
            ('biotico.humedad-hongo', 'FungalDamageRule'),
            ('biotico.temp-predisponente-min', 'FungalConditionRule'),
            ('biotico.temp-predisponente-max', 'FungalConditionRule'),
            ('biotico.humedad-predisponente', 'FungalConditionRule'),
            ('biotico.capturas-seguidas', 'FungalDamageRule'),
            ('biotico.capturas-seguidas', 'FoliarPestRule'),
            ('biotico.horas-dpv-acaros', 'FoliarPestRule'),
            ('biotico.sectores-foco', 'OutbreakRule'),
            ('biotico.horas-foco', 'OutbreakRule'),
            ('clorosis.humedad-asfixia', 'ChlorosisNonNutritionalRule'),
            ('clorosis.humedad-horas', 'ChlorosisNonNutritionalRule'),
            ('clorosis.ce-salinidad', 'ChlorosisNonNutritionalRule'),
            ('clorosis.ph-ferrica', 'ChlorosisNonNutritionalRule'),
            ('estres.luz-alta', 'SolarStressRule'),
            ('estres.temp-alta', 'SolarStressRule'),
            ('estres.humedad-reposicion', 'SolarStressRule'),
            ('estres.luz-baja', 'SolarStressUnexplainedRule'),
            ('estres.temp-baja', 'SolarStressUnexplainedRule'),
            ('efectividad.delta-riego', 'EffectivenessService'),
            ('efectividad.verificacion-min', 'EffectivenessService'),
            ('efectividad.latencia-fertilizante-dias', 'EffectivenessService'),
            ('efectividad.latencia-fitosanitario-dias', 'EffectivenessService'),
            ('efectividad.espera-sin-diagnostico-horas', 'EffectivenessService'),
            ('mediasombra.uv-umbral', 'ShadingRule'),
            ('diagnostico.confianza-minima', 'SupplyRule'),
            ('diagnostico.confianza-minima', 'FungalDamageRule'),
            ('diagnostico.confianza-minima', 'FoliarPestRule'),
            ('diagnostico.confianza-minima', 'ChlorosisNonNutritionalRule'),
            ('diagnostico.confianza-minima', 'ChlorosisNutritionalRule'),
            ('diagnostico.confianza-minima', 'SolarStressRule'),
            ('diagnostico.confianza-minima', 'SolarStressUnexplainedRule'),
            ('diagnostico.confianza-minima', 'OutbreakRule'),
            ('diagnostico.confianza-minima', 'EffectivenessService'),
            ('diagnostico.confianza-minima', 'ApplicationService'),
            ('diagnostico.vigencia-horas', 'FungalDamageRule'),
            ('diagnostico.vigencia-horas', 'FoliarPestRule'),
            ('diagnostico.vigencia-horas', 'ChlorosisNonNutritionalRule'),
            ('diagnostico.vigencia-horas', 'ChlorosisNutritionalRule'),
            ('diagnostico.vigencia-horas', 'SolarStressRule'),
            ('diagnostico.vigencia-horas', 'SolarStressUnexplainedRule'),
            ('diagnostico.vigencia-horas', 'EffectivenessService'),
            ('diagnostico.vigencia-horas', 'ApplicationService'),
            ('readings.retention-days', 'ReadingPurge'),
            ('sensado.espera-respuesta', 'PulsoSensado'),
            ('sensado.reintentos', 'PulsoSensado'),
            ('sensado.espera-reintento', 'PulsoSensado'),
            ('sensado.reintento-fuera-servicio', 'EscalamientoNodo')
    ) AS c(clave, consumidor) ON c.clave = p.clave
    ON CONFLICT (consumidor, clave) DO NOTHING;

    DROP TABLE parametro_regla;
END
$$;

COMMIT;
