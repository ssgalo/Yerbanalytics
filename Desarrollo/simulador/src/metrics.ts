/* ============================================================
   The witness node's ten metrics, as the form presents them.

   `base` is the value each field is prefilled with: a healthy nursery, so sending a
   reasonable reading does not require typing ten numbers.

   Two keys carry a story:
     - `ce` — this side works in dS/m, the platform's unit. The server converts to µS/cm on
       publish, because that is what the probe emits.
     - `uv` — LDR light percentage, not UV radiation. The key is kept for firmware
       compatibility; the metric is luminosity.

   Labels and diagnosis states are shown verbatim to the operator, so they stay in Spanish.
   ============================================================ */

export interface Metric {
  /** Contract key — firmware vocabulary, not translatable. */
  key: string;
  label: string;
  unit: string;
  /** Input decimals: 0 → step 1, >0 → step 0.1. */
  dec: number;
  base: number;
  group: 'ambiente' | 'nutricion';
}

export const METRICS: Metric[] = [
  { key: 'humSus', label: 'Humedad de sustrato', unit: '%', dec: 0, base: 55, group: 'ambiente' },
  { key: 'humAmb', label: 'Humedad ambiental', unit: '%', dec: 0, base: 72, group: 'ambiente' },
  { key: 'temp', label: 'Temperatura del aire', unit: '°C', dec: 1, base: 23, group: 'ambiente' },
  { key: 'uv', label: 'Luminosidad', unit: '%', dec: 0, base: 50, group: 'ambiente' },
  { key: 'tempSuelo', label: 'Temperatura del sustrato', unit: '°C', dec: 1, base: 20, group: 'ambiente' },
  { key: 'ce', label: 'Nutrientes (CE)', unit: 'dS/m', dec: 1, base: 1.4, group: 'nutricion' },
  { key: 'phSuelo', label: 'pH del sustrato', unit: 'pH', dec: 1, base: 5.5, group: 'nutricion' },
  { key: 'n', label: 'Nitrógeno', unit: 'mg/kg', dec: 0, base: 150, group: 'nutricion' },
  { key: 'p', label: 'Fósforo', unit: 'mg/kg', dec: 0, base: 45, group: 'nutricion' },
  { key: 'k', label: 'Potasio', unit: 'mg/kg', dec: 0, base: 180, group: 'nutricion' },
];

/**
 * The platform's diagnosis taxonomy. Mirrors `DiagnosticoService.ESTADOS`. These are domain
 * values displayed literally in the UI, so they stay in Spanish.
 */
export const DIAGNOSIS_STATES = [
  'Sano',
  'Clorosis',
  'Estrés solar',
  'Daño biótico',
  'Ácaro',
  'Plaga foliar',
  'Daño fúngico',
  'No concluyente',
];

export const SEVERITIES = ['—', 'Baja', 'Media', 'Alta'];
