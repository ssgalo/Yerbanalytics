/* ============================================================
   Configuración agronómica de fábrica (HU-15). Determinística: deriva
   las bandas de `specs.ts` y usa los mismos defaults sembrados en el
   backend (data.sql). El contrato (Configuracion) es idéntico al DTO.
   ============================================================ */
import type { Configuracion } from '@/types/domain';
import { bandMidpoint } from '@/lib/configValidation';
import { HORARIO_MEDIASOMBRA_DEFAULT } from '@/lib/planRustificacion';
import { specs } from './specs';

/** Los mismos valores que siembra el backend: un pulso cada 4 h y la pasada diaria apagada a las 11:00. */
const DEFAULT_PULSE_MINUTES = 240;
/**
 * Umbral de lectura vieja que informaría el backend: max(piso de 90 s, pulso + 180 s de reintentos).
 * El mock hace de backend; la vista nunca lo calcula.
 */
export const mockStaleThresholdSeconds = (intervaloMin: number) => Math.max(90, intervaloMin * 60 + 180);

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
      intervaloSensadoMinutos: DEFAULT_PULSE_MINUTES,
      effectiveStaleThresholdSeconds: mockStaleThresholdSeconds(DEFAULT_PULSE_MINUTES),
      pasadaDiariaActiva: false,
      pasadaDiariaHora: '11:00',
      mediasombraAbiertaDesde: HORARIO_MEDIASOMBRA_DEFAULT.desde,
      mediasombraAbiertaHasta: HORARIO_MEDIASOMBRA_DEFAULT.hasta,
      updatedBy: 'Mock System',
      updatedTs: null,
    },
  };
}
