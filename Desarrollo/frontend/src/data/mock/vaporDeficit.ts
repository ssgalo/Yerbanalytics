/* ============================================================
   Déficit de presión de vapor (DPV) del modo mock.

   Espejo de `readings/VaporPressureDeficit` y `VaporPressureBand` del backend (reglas_v2 §1.4): la demo
   estática no tiene backend, así que deriva el valor de la temperatura y la humedad de la lectura mock.
   Si cambia la fórmula o un tramo allá, cambia acá.
   ============================================================ */
import type { Metric, VaporBand, VaporDeficit } from '@/types/domain';

const LABELS: Record<VaporBand, string> = {
  SATURATED: 'aire saturado',
  TRANSITION: 'transición',
  COMFORTABLE: 'confortable',
  HIGH_DEMAND: 'demanda alta',
  DRY_HEAT: 'calor seco',
};

/** El tramo de un DPV en kPa; el límite inferior de cada tramo le pertenece. */
function bandOf(kpa: number): VaporBand {
  if (kpa < 0.3) return 'SATURATED';
  if (kpa < 0.5) return 'TRANSITION';
  if (kpa < 1.2) return 'COMFORTABLE';
  if (kpa < 2.0) return 'HIGH_DEMAND';
  return 'DRY_HEAT';
}

/** DPV en kPa a 0,01, o `null` si falta una entrada o la humedad no está entre 0 y 100. */
export function vaporDeficit(tempC: number | null, humPct: number | null): VaporDeficit | null {
  if (tempC == null || humPct == null || humPct < 0 || humPct > 100) return null;
  const es = 0.6108 * Math.exp((17.27 * tempC) / (tempC + 237.3));
  const value = Math.round(es * (1 - humPct / 100) * 100) / 100;
  const band = bandOf(value);
  return { value, band, label: LABELS[band] };
}

/** El DPV de una lectura mock, a partir de su temperatura del aire y su humedad ambiental. */
export function vaporDeficitDeMetricas(metrics: Metric[]): VaporDeficit | null {
  const raw = (key: string) => metrics.find((m) => m.key === key)?.raw ?? null;
  return vaporDeficit(raw('temp'), raw('humAmb'));
}
