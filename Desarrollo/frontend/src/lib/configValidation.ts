/* ============================================================
   Validación de la configuración agronómica (HU-15 CA-03..06).
   Espeja las reglas del backend (ConfiguracionService.validar) para
   bloquear el guardado en cliente y para que el mock falle igual que
   el backend real. El "rango fisiológico" de fábrica = bandas crit de
   las specs base.
   ============================================================ */
import { specs } from '@/data/mock/specs';
import type {
  Configuracion,
  ConfigOperativa,
  MetricThreshold,
  RustificacionEtapa,
} from '@/types/domain';

const factoryByKey = new Map(specs.map((s) => [s.key, s]));

const fmt = (v: number) => String(v);

/** Error de coherencia/envelope de una banda de métrica, o null si es válida. */
export function umbralError(u: MetricThreshold): string | null {
  const coherente =
    u.critMin <= u.warnMin &&
    u.warnMin <= u.idealMin &&
    u.idealMin < u.idealMax &&
    u.idealMax <= u.warnMax &&
    u.warnMax <= u.critMax;
  if (!coherente) {
    return `Bandas incoherentes en «${u.label}»: crit mín ≤ warn mín ≤ ideal mín < ideal máx ≤ warn máx ≤ crit máx.`;
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
  const positive = (v: number, nombre: string) => {
    if (!(v > 0)) out.push(`El valor de ${nombre} debe ser mayor a 0.`);
  };
  positive(op.riegoTiempoMaxSeg, 'el tiempo máximo de apertura de riego');
  positive(op.riegoVolMaxDiarioMl, 'el volumen máximo diario de riego');
  positive(op.insumoDosisMax24hMl, 'la dosis máxima de insumo por 24 h');
  positive(op.seguimientoLatenciaMin, 'la latencia de seguimiento');
  positive(op.seguimientoDeltaMin, 'el delta mínimo de recuperación');
  if (op.mediasombraAperturaMaxPct <= 0 || op.mediasombraAperturaMaxPct > 100) {
    out.push('La apertura máxima de mediasombra debe estar entre 0 y 100 %.');
  }
  return out;
}

/** Error del plan de rustificación (días invertidos, solapamiento, % fuera de rango). */
export function etapasError(etapas: RustificacionEtapa[], aperturaMax: number): string | null {
  if (!etapas || etapas.length === 0) return null;
  const ordenadas = [...etapas].sort((a, b) => a.diaDesde - b.diaDesde);
  let prevHasta = 0;
  for (const et of ordenadas) {
    if (et.diaDesde < 1 || et.diaDesde > et.diaHasta) {
      return 'Etapa de rustificación inválida: «día desde» debe ser ≥ 1 y ≤ «día hasta».';
    }
    if (et.diaDesde <= prevHasta) {
      return 'Las etapas de rustificación no pueden solaparse en el cronograma.';
    }
    if (et.aperturaPct < 0 || et.aperturaPct > aperturaMax) {
      return `La apertura de cada etapa debe estar entre 0 % y la apertura máxima (${fmt(aperturaMax)} %).`;
    }
    prevHasta = et.diaHasta;
  }
  return null;
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
  const eEtapas = etapasError(cfg.rustificacion, cfg.operativa.mediasombraAperturaMaxPct);
  if (eEtapas) out.push(eEtapas);
  return out;
}
