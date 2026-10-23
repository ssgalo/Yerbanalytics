/* ============================================================
   Cómo se muestra una acción del historial: resultado, ícono, volumen, duración y lectura.
   Funciones puras; la tabla y el detalle sólo las pintan.
   ============================================================ */
import type { ChipTone } from '@/components/ui/Chip';
import type { Column } from '@/lib/columnQuery';
import type { ActionRecord, NivelAlerta } from '@/types/domain';

/** `sectorId` de las acciones de alcance macro-zona (mediasombra, alertas): no son de un sector. */
export const ZONE_WIDE = '—';

export const isZoneWide = (r: ActionRecord): boolean => r.sectorId === ZONE_WIDE;

/** Nombre y tono de cada nivel de alerta. */
export const ALERT_LEVEL: Record<NivelAlerta, { name: string; tone: ChipTone }> = {
  INFO: { name: 'Informativa', tone: 'info' },
  WARNING: { name: 'Advertencia', tone: 'warning' },
  CRITICAL: { name: 'Crítica', tone: 'critical' },
};

/** Tono de cada resultado: el estado lleva además su glifo. */
const RESULT_TONE: Record<string, ChipTone> = {
  Efectiva: 'ok',
  'En seguimiento': 'info',
  Pospuesta: 'warning',
  Abortada: 'critical',
};

/** Ícono propio de las alertas: el backend no les define uno. */
const ALERT_PATH = 'M12 3 2.5 20h19L12 3ZM12 10v4M12 17h.01';

/** "Efectiva", "Pospuesta"… y "Alerta crítica" para las alertas con nivel. */
export function resultLabel(r: ActionRecord): string {
  if (r.tipo === 'Alerta' && r.alerta) return `Alerta ${ALERT_LEVEL[r.alerta].name.toLowerCase()}`;
  return r.res;
}

export function resultTone(r: ActionRecord): ChipTone {
  if (r.tipo === 'Alerta' && r.alerta) return ALERT_LEVEL[r.alerta].tone;
  return RESULT_TONE[r.res] ?? 'line';
}

/** Path del ícono del tipo de acción (el del backend, salvo las alertas). */
export const typeIconPath = (r: ActionRecord): string => (r.tipo === 'Alerta' ? ALERT_PATH : r.path);

/** "6 L", "5,4 L": coma decimal, sin ceros de más. */
export const formatVolume = (liters: number): string => `${String(Number(liters.toFixed(2))).replace('.', ',')} L`;

export const formatDuration = (seconds: number): string => `${seconds} s`;

/** La lectura de algunos eventos nombra la regla por su clase: se cambia por su nombre legible. */
export function readingText(r: ActionRecord, ruleLabel: (id: string) => string): string {
  return r.lectura.replace(/Ciclo de evaluaci[oó]n:\s*(\w+)\.?/i, (_m, id: string) => `Evaluando: ${ruleLabel(id)}`);
}

/* Columnas de la tabla: cada una se filtra y se ordena desde su encabezado. */
const AZ = { asc: 'De A a Z', desc: 'De Z a A' };

export const HISTORY_COLUMNS: Column<ActionRecord>[] = [
  {
    key: 'fecha',
    label: 'Fecha',
    kind: 'date',
    date: (r) => r.ts,
    sortLabels: { asc: 'Más antiguas primero', desc: 'Más recientes primero' },
  },
  { key: 'tipo', label: 'Tipo', kind: 'values', value: (r) => r.tipo, sortLabels: AZ },
  { key: 'zona', label: 'Macro-zona', kind: 'values', value: (r) => r.zonaName, sortLabels: AZ },
  {
    key: 'sector',
    label: 'Sector',
    kind: 'values',
    value: (r) => r.sectorId,
    optionLabel: (v) => (v === ZONE_WIDE ? 'Toda la macro-zona' : v),
    searchable: true,
    sortLabels: AZ,
  },
  { key: 'resultado', label: 'Resultado', kind: 'values', value: resultLabel, sortLabels: AZ },
];
