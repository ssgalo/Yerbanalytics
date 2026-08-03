/* ============================================================
   Configuración agronómica de fábrica (HU-15). Determinística: deriva
   las bandas de `specs.ts` y usa los mismos defaults sembrados en el
   backend (data.sql). El contrato (Configuracion) es idéntico al DTO.
   ============================================================ */
import type { Configuracion } from '@/types/domain';
import { specs } from './specs';

/** Plan de rustificación por defecto (idéntico al seed del backend). */
const DEFAULT_RUSTIFICACION = [
  { orden: 1, diaDesde: 1, diaHasta: 7, aperturaPct: 20 },
  { orden: 2, diaDesde: 8, diaHasta: 14, aperturaPct: 40 },
  { orden: 3, diaDesde: 15, diaHasta: 21, aperturaPct: 70 },
  { orden: 4, diaDesde: 22, diaHasta: 30, aperturaPct: 100 },
];

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
      // Las métricas informativas (sonda de suelo) tienen rangos aún sin validar.
      provisional: !s.afectaEstado,
    })),
    operativa: {
      riegoTiempoMaxSeg: 120,
      riegoVolMaxDiarioMl: 2000,
      insumoDosisMax24hMl: 15,
      mediasombraAperturaMaxPct: 100,
      seguimientoLatenciaMin: 2,
      seguimientoDeltaMin: 5,
      updatedBy: 'Valores de fábrica',
      updatedTs: null,
    },
    rustificacion: DEFAULT_RUSTIFICACION.map((e) => ({ ...e })),
  };
}
