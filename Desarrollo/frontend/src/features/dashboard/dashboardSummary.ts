/* ============================================================
   Resúmenes del Panel general.

   Lógica pura, sin JSX: cifras y frases que el panel muestra sobre el vivero,
   los equipos y la actividad del día, pensadas para quien no conoce el dominio.
   ============================================================ */
import type { ChipTone } from '@/components/ui/Chip';
import type { ActionRecord, Dispositivo, Stats, Status, Zona } from '@/types/domain';

/** Ancla de la sección de actividad: "Acciones del sistema hoy" scrollea hasta acá. */
export const ACTIVITY_ANCHOR = 'actividad-del-sistema';

/** Umbral de señal débil del nodo testigo, en dBm (igual que el prototipo del rediseño). */
export const WEAK_SIGNAL_DBM = -85;

/** "1 sector" / "2 sectores". */
export function pluralize(n: number, singular: string, plural: string): string {
  return `${n} ${n === 1 ? singular : plural}`;
}

/** Une partes de una enumeración: "a", "a y b", "a, b y c". */
export function joinWords(parts: string[]): string {
  if (parts.length <= 1) return parts[0] ?? '';
  return `${parts.slice(0, -1).join(', ')} y ${parts[parts.length - 1]}`;
}

/** Columnas de la grilla de sectores: la disposición configurada, sin pasarse de la cantidad. */
export function gridColumns(totalSectors: number, sectorsPerRow: number): number {
  return Math.max(1, Math.min(sectorsPerRow, totalSectors));
}

/** Sectores de la zona en un estado. */
export function countByStatus(zona: Zona, status: Status): number {
  return zona.sectors.filter((s) => s.status === status).length;
}

/** Instante de la lectura más reciente de todo el vivero; `null` si ningún nodo reportó. */
export function latestReading(zonas: Zona[]): number | null {
  const ts = zonas.map((z) => z.lectura.ts).filter((t): t is number => t != null);
  return ts.length > 0 ? Math.max(...ts) : null;
}

/** "14:32" en hora local. */
export function clockTime(ts: number): string {
  return new Date(ts).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false });
}

/** Desglose de las acciones del día: "28 riegos, 7 dosificaciones y 6 movimientos de mediasombra." */
export function actionsBreakdown(stats: Stats): string {
  if (stats.actToday === 0) return 'El motor todavía no actuó hoy.';
  const parts = [
    stats.actRiego > 0 && pluralize(stats.actRiego, 'riego', 'riegos'),
    stats.actInsumo > 0 && pluralize(stats.actInsumo, 'dosificación', 'dosificaciones'),
    stats.actSombra > 0 && pluralize(stats.actSombra, 'movimiento de mediasombra', 'movimientos de mediasombra'),
  ].filter((p): p is string => Boolean(p));
  return parts.length > 0 ? `${joinWords(parts)}.` : '';
}

export interface EquipmentSummary {
  /** Equipos con algún problema (cada uno cuenta una sola vez, por su peor problema). */
  total: number;
  outOfService: number;
  faulty: number;
  /** Señal intermitente o batería baja: todavía funcionan, pero conviene mirarlos. */
  warning: number;
  /** Nodos testigo que reportan al día, sobre el total de nodos. */
  nodesReporting: number;
  nodes: number;
}

/** Equipos con problemas, contando cada uno por su peor problema. */
export function summarizeEquipment(devices: Dispositivo[]): EquipmentSummary {
  let outOfService = 0;
  let faulty = 0;
  let warning = 0;
  for (const d of devices) {
    if (d.estado === 'fuera_de_servicio') outOfService++;
    else if (d.falla != null) faulty++;
    else if (d.estado === 'intermitente' || d.bateriaBaja) warning++;
  }
  const nodes = devices.filter((d) => d.tipo === 'nodo_testigo');
  return {
    total: outOfService + faulty + warning,
    outOfService,
    faulty,
    warning,
    nodesReporting: nodes.filter((d) => d.estado !== 'fuera_de_servicio').length,
    nodes: nodes.length,
  };
}

/** "2 fuera de servicio y 2 con aviso. 5 de 6 nodos reportan al día." */
export function equipmentText(s: EquipmentSummary): string {
  const nodes = s.nodes > 0 ? ` ${s.nodesReporting} de ${s.nodes} nodos reportan al día.` : '';
  if (s.total === 0) return `Todos los equipos funcionan.${nodes}`;
  const parts = [
    s.outOfService > 0 && `${s.outOfService} fuera de servicio`,
    s.faulty > 0 && `${s.faulty} con falla`,
    s.warning > 0 && `${s.warning} con aviso`,
  ].filter((p): p is string => Boolean(p));
  return `${joinWords(parts)}.${nodes}`;
}

/* ── Actividad del día ── */

/** Sustantivo de cada tipo de evento del historial. */
const ACTION_NOUN: Record<string, string> = {
  Riego: 'Riego',
  Insumo: 'Dosificación de insumo',
  Mediasombra: 'Mediasombra',
  Alerta: 'Alerta',
};

/** Título en lenguaje llano: "Riego en MZ-4-005", "Alerta en Macro-zona 3". */
export function activityTitle(r: ActionRecord): string {
  const noun = ACTION_NOUN[r.tipo] ?? r.tipo;
  /* Las alertas son de la macro-zona: llegan sin sector ("—"). */
  const place = r.sectorId && r.sectorId !== '—' ? r.sectorId : r.zonaName;
  return place ? `${noun} en ${place}` : noun;
}

/** Tono del chip de resultado; lo desconocido va en neutro. */
const RESULT_TONE: Record<string, ChipTone> = {
  Efectiva: 'ok',
  'En seguimiento': 'info',
  Pospuesta: 'warning',
  Abortada: 'critical',
  Informativo: 'line',
};

export function resultTone(res: string): ChipTone {
  return RESULT_TONE[res] ?? 'line';
}

/** Hora del registro ("HH:mm"); `fecha` llega como "dd/MM HH:mm". */
export function activityTime(r: ActionRecord): string {
  return r.fecha.split(' ')[1] ?? r.time;
}
