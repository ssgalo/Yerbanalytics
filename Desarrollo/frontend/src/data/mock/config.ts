/* ============================================================
   Configuración agronómica de fábrica (HU-15). Determinística: deriva
   las bandas de `specs.ts` y usa los mismos defaults sembrados en el
   backend (data.sql). El contrato (Configuracion) es idéntico al DTO.
   ============================================================ */
import type { Configuracion } from '@/types/domain';
import { bandMidpoint } from '@/lib/configValidation';
import { HORARIO_MEDIASOMBRA_DEFAULT } from '@/lib/planRustificacion';
import { specs } from './specs';

/** Same default as the backend. */
const DEFAULT_SENSING_PULL_SECONDS = 60;
/** Static value the real backend would report for the default interval (mock stands in for it). */
const DEFAULT_STALE_THRESHOLD_SECONDS = 180;

/** Construye la configuración de fábrica (también usada por "restablecer"). */
export function buildConfig(): Configuracion {
  return {
    umbrales: specs.map((s) => ({
      key: s.key,
      label: s.label,
      unit: s.unit,
      dec: s.dec,
      idealMin: s.ideal[0],
      idealMax: s.ideal[1],
      warnMin: s.warn[0],
      warnMax: s.warn[1],
      critMin: s.crit[0],
      critMax: s.crit[1],
      alertMin: bandMidpoint(s.warn[0], s.ideal[0], s.dec),
      alertMax: bandMidpoint(s.ideal[1], s.warn[1], s.dec),
      // Las métricas informativas (sonda de suelo) tienen rangos aún sin validar.
      provisional: !s.afectaEstado,
    })),
    operativa: {
      insumoDosisMax24hMl: 15,
      seguimientoLatenciaMin: 2,
      seguimientoDeltaMin: 5,
      intervaloSensadoMinutos: 240,
      intervaloEvaluacionMinutos: 5,
      sensingPullIntervalSeconds: DEFAULT_SENSING_PULL_SECONDS,
      effectiveStaleThresholdSeconds: DEFAULT_STALE_THRESHOLD_SECONDS,
      mediasombraAbiertaDesde: HORARIO_MEDIASOMBRA_DEFAULT.desde,
      mediasombraAbiertaHasta: HORARIO_MEDIASOMBRA_DEFAULT.hasta,
      updatedBy: 'Mock System',
      updatedTs: null,
    },
  };
}
