/* ============================================================
   Horarios del día ("HH:MM", hora local del vivero) y fechas de calendario ("YYYY-MM-DD").
   Los usan el horario por defecto de la mediasombra y los planes de rustificación.
   ============================================================ */

const HHMM = /^([01]\d|2[0-3]):([0-5]\d)$/;

/** Minutos desde la medianoche, o null si el texto no es una hora válida. */
export function minutesOf(hhmm: string | null | undefined): number | null {
  const m = HHMM.exec(hhmm ?? '');
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}

/** Duración de una ventana en minutos (0 si es inválida). */
export function windowMinutes(desde: string, hasta: string): number {
  const a = minutesOf(desde);
  const b = minutesOf(hasta);
  return a !== null && b !== null && b > a ? b - a : 0;
}

/** "6 h", "6 h 30 min". */
export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

/**
 * Error de una ventana diaria, o null si es válida. No cruza la medianoche: la mediasombra se
 * abre de día.
 */
export function horarioError(desde: string, hasta: string): string | null {
  const a = minutesOf(desde);
  const b = minutesOf(hasta);
  if (a === null || b === null) return 'cada hora debe tener el formato HH:MM.';
  if (b <= a) return 'la hora de cierre debe ser posterior a la de apertura.';
  return null;
}

/** Fecha local de hoy como "YYYY-MM-DD". */
export function todayIso(now: Date = new Date()): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${p(now.getMonth() + 1)}-${p(now.getDate())}`;
}

/** Fecha "YYYY-MM-DD" más `days` días (sin problemas de horario de verano: trabaja en UTC). */
export function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** Días de `from` a `to` (negativo si `to` es anterior). */
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000);
}

/** "12/10/2026". */
export function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

export const isIsoDate = (s: string) => /^\d{4}-\d{2}-\d{2}$/.test(s) && !Number.isNaN(Date.parse(`${s}T00:00:00Z`));
