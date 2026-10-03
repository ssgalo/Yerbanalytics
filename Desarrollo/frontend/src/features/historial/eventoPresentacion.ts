/* ============================================================
   Cómo se muestra un evento del historial: volumen, duración, regla y nivel de alerta.
   Funciones puras; el timeline sólo las pinta.
   ============================================================ */
import type { ActionRecord, NivelAlerta } from '@/types/domain';

/** Nombre y colores (tokens) de cada nivel de alerta. */
export const NIVEL_ALERTA: Record<NivelAlerta, { nombre: string; soft: string; ink: string }> = {
  INFO: { nombre: 'Informativa', soft: 'var(--info-soft)', ink: 'var(--info-ink)' },
  WARNING: { nombre: 'Advertencia', soft: 'var(--warn-soft)', ink: 'var(--warn-ink)' },
  CRITICAL: { nombre: 'Crítica', soft: 'var(--crit-soft)', ink: 'var(--crit-ink)' },
};

/** Ícono propio de las alertas: el backend no les define uno y heredarían la gota del riego. */
export const PATH_ALERTA = 'M12 3 2.5 20h19L12 3ZM12 10v4M12 17h.01';

/** "6 L", "5,4 L": coma decimal, sin ceros de más. */
export const formatoVolumen = (litros: number): string => `${String(Number(litros.toFixed(2))).replace('.', ',')} L`;

export const formatoDuracion = (segundos: number): string => `${segundos} s`;

/** Las alertas son de la macro-zona (el backend las guarda con `sectorId` "—"). */
export const esAlertaDeZona = (r: ActionRecord): boolean => r.tipo === 'Alerta';

/** Rótulo del tipo: "Riego", "Insumo"… y "Alerta crítica" para las alertas con nivel. */
export function etiquetaTipo(r: ActionRecord): string {
  if (r.tipo === 'Alerta' && r.alerta) return `Alerta ${NIVEL_ALERTA[r.alerta].nombre.toLowerCase()}`;
  return r.tipo;
}

/** Colores e ícono del evento: los del backend, salvo las alertas (por nivel). */
export function presentacionIcono(r: ActionRecord): { tint: string; ink: string; path: string } {
  if (r.tipo !== 'Alerta') return { tint: r.tint, ink: r.ink, path: r.path };
  const n = NIVEL_ALERTA[r.alerta ?? 'INFO'];
  return { tint: n.soft, ink: n.ink, path: PATH_ALERTA };
}
