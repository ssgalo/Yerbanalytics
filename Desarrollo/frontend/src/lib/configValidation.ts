/* ============================================================
   Validación de la configuración agronómica (HU-15 CA-03..06).
   Espeja las reglas del backend (ConfiguracionService.validar) para
   bloquear el guardado en cliente y para que el mock falle igual que
   el backend real. El "rango fisiológico" de fábrica = bandas crit de
   las specs base.
   ============================================================ */
import { specs } from '@/data/mock/specs';
import type { Configuracion, ConfigOperativa, MetricThreshold } from '@/types/domain';
import { horarioError } from './horario';

/** Mirrors the backend range for sensingPullIntervalSeconds. */
export const SENSING_PULL_MIN_SECONDS = 10;
export const SENSING_PULL_MAX_SECONDS = 3600;

const factoryByKey = new Map(specs.map((s) => [s.key, s]));

const fmt = (v: number) => String(v);

/** Medio de una franja redondeado a `dec` decimales: el punto de alerta de fábrica (igual que el backend). */
export function bandMidpoint(from: number, to: number, dec: number): number {
  const scale = 10 ** dec;
  return Math.round(((from + to) / 2) * scale) / scale;
}

/** Error de coherencia/envelope de una banda de métrica, o null si es válida. */
export function umbralError(u: MetricThreshold): string | null {
  const coherente =
    u.critMin <= u.warnMin &&
    u.warnMin <= u.idealMin &&
    u.idealMin < u.idealMax &&
    u.idealMax <= u.warnMax &&
    u.warnMax <= u.critMax;
  if (!coherente) {
    return `Bandas incoherentes en «${u.label}»: el ideal debe quedar dentro del crítico y el crítico dentro del rango fisiológico.`;
  }
  if (u.alertMin < u.warnMin || u.alertMin > u.idealMin || u.alertMax < u.idealMax || u.alertMax > u.warnMax) {
    return `Bandas incoherentes en «${u.label}»: cada punto de alerta debe quedar entre el crítico y el ideal de su lado.`;
  }
  const f = factoryByKey.get(u.key);
  if (f) {
    const [envMin, envMax] = f.crit;
    if (u.critMin < envMin || u.critMax > envMax) {
      return `«${u.label}» fuera del rango fisiológico permitido (${fmt(envMin)} a ${fmt(envMax)} ${u.unit}).`;
    }
  }
  return null;
}

/** Errores de los límites operativos. */
export function operativaErrors(op: ConfigOperativa): string[] {
  const out: string[] = [];
  const positive = (v: number | null | undefined, nombre: string) => {
    if (!(Number(v) > 0)) out.push(`El valor de ${nombre} debe ser mayor a 0.`);
  };
  positive(op.insumoDosisMax24hMl, 'la dosis máxima de insumo por 24 h');
  positive(op.seguimientoLatenciaMin, 'la latencia de seguimiento');
  positive(op.seguimientoDeltaMin, 'el delta mínimo de recuperación');
  positive(op.intervaloSensadoMinutos ?? 0, 'el intervalo de sensado');
  positive(op.intervaloEvaluacionMinutos ?? 0, 'el intervalo de evaluación');
  const pull = op.sensingPullIntervalSeconds;
  if (pull != null && !(Number.isInteger(pull) && pull >= SENSING_PULL_MIN_SECONDS && pull <= SENSING_PULL_MAX_SECONDS)) {
    out.push(
      `La frecuencia de pedido de lectura debe ser un entero entre ${SENSING_PULL_MIN_SECONDS} y ${SENSING_PULL_MAX_SECONDS} segundos.`,
    );
  }
  const horario = horarioError(op.mediasombraAbiertaDesde, op.mediasombraAbiertaHasta);
  if (horario) out.push(`Horario de la mediasombra: ${horario}`);
  return out;
}

/** Lista de mensajes de error de toda la configuración. Vacía = válida. */
export function validateConfig(cfg: Configuracion): string[] {
  const out: string[] = [];
  if (!cfg.umbrales || cfg.umbrales.length !== specs.length) {
    out.push(`Se esperan los umbrales de las ${specs.length} métricas.`);
  } else {
    for (const u of cfg.umbrales) {
      const e = umbralError(u);
      if (e) out.push(e);
    }
  }
  out.push(...operativaErrors(cfg.operativa));
  return out;
}
