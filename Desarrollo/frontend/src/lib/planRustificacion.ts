/* ============================================================
   Plan de rustificación por macro-zona: validación del alta y progreso. El backend es la
   autoridad (valida igual y decide el estado); acá se calcula sólo para mostrar y para no
   mandar un plan que sabemos inválido.
   ============================================================ */
import type { EtapaRustificacion, NuevoPlanRustificacion, PlanRustificacion } from '@/types/domain';
import { addDays, daysBetween, horarioError, isIsoDate, todayIso, windowMinutes } from './horario';

/** Horario por defecto de la mediasombra (sin plan), de fábrica. */
export const HORARIO_MEDIASOMBRA_DEFAULT = { desde: '08:00', hasta: '18:00' } as const;

/** Tope de días de una etapa: un plan de rustificación dura semanas, no meses. */
export const MAX_DIAS_ETAPA = 60;

/** Etapas de partida al abrir el formulario: cuatro semanas, cada una con más horas de sol. */
export const ETAPAS_SUGERIDAS: Omit<EtapaRustificacion, 'orden'>[] = [
  { dias: 7, abiertaDesde: '10:00', abiertaHasta: '13:00' },
  { dias: 7, abiertaDesde: '09:00', abiertaHasta: '15:00' },
  { dias: 7, abiertaDesde: '08:00', abiertaHasta: '17:00' },
  { dias: 7, abiertaDesde: '07:00', abiertaHasta: '19:00' },
];

/** Total de días del plan. */
export const totalDias = (etapas: Pick<EtapaRustificacion, 'dias'>[]) => etapas.reduce((a, e) => a + e.dias, 0);

/** Último día del plan ("YYYY-MM-DD"). */
export const fechaFin = (fechaInicio: string, etapas: Pick<EtapaRustificacion, 'dias'>[]) =>
  addDays(fechaInicio, Math.max(0, totalDias(etapas) - 1));

/** Errores que impiden iniciar el plan. Vacío = se puede mandar. */
export function planErrors(plan: NuevoPlanRustificacion, hoy: string = todayIso()): string[] {
  const out: string[] = [];
  if (!isIsoDate(plan.fechaInicio)) out.push('Elegí la fecha de inicio.');
  else if (plan.fechaInicio < hoy) out.push('La fecha de inicio no puede ser anterior a hoy.');
  if (plan.etapas.length === 0) out.push('El plan necesita al menos una etapa.');
  plan.etapas.forEach((e, i) => {
    if (!Number.isInteger(e.dias) || e.dias < 1 || e.dias > MAX_DIAS_ETAPA) {
      out.push(`Etapa ${i + 1}: la duración debe ser un entero entre 1 y ${MAX_DIAS_ETAPA} días.`);
    }
    const h = horarioError(e.abiertaDesde, e.abiertaHasta);
    if (h) out.push(`Etapa ${i + 1}: ${h}`);
  });
  return out;
}

/**
 * Etapas que exponen MENOS horas que la anterior. No es un error (el agrónomo puede querer una
 * pausa), pero va contra la idea del plan, así que se avisa.
 */
export function etapasQueRetroceden(etapas: Omit<EtapaRustificacion, 'orden'>[]): number[] {
  const out: number[] = [];
  for (let i = 1; i < etapas.length; i++) {
    const prev = windowMinutes(etapas[i - 1].abiertaDesde, etapas[i - 1].abiertaHasta);
    const cur = windowMinutes(etapas[i].abiertaDesde, etapas[i].abiertaHasta);
    if (prev > 0 && cur > 0 && cur < prev) out.push(i + 1);
  }
  return out;
}

export interface ProgresoPlan {
  /** Día del plan (1-based); 0 si todavía no empezó. */
  dia: number;
  totalDias: number;
  /** Etapa de hoy, o null si no empezó o ya terminó. */
  etapa: EtapaRustificacion | null;
  /** Días que faltan para empezar (> 0 sólo si está programado). */
  faltan: number;
  terminado: boolean;
}

/** Dónde está el plan hoy, según sus fechas. */
export function progreso(plan: Pick<PlanRustificacion, 'fechaInicio' | 'etapas'>, hoy: string = todayIso()): ProgresoPlan {
  const total = totalDias(plan.etapas);
  const transcurridos = daysBetween(plan.fechaInicio, hoy);
  if (transcurridos < 0) return { dia: 0, totalDias: total, etapa: null, faltan: -transcurridos, terminado: false };
  if (transcurridos >= total) return { dia: total, totalDias: total, etapa: null, faltan: 0, terminado: true };
  let acumulado = 0;
  for (const e of plan.etapas) {
    acumulado += e.dias;
    if (transcurridos < acumulado) {
      return { dia: transcurridos + 1, totalDias: total, etapa: e, faltan: 0, terminado: false };
    }
  }
  return { dia: total, totalDias: total, etapa: null, faltan: 0, terminado: true };
}

/** Un plan rige (programado o en curso): la zona no admite otro. */
export const planVigente = (p: PlanRustificacion | null): p is PlanRustificacion =>
  p !== null && (p.estado === 'programado' || p.estado === 'en_curso');
