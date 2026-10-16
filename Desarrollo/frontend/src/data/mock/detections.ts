/* ============================================================
   Detecciones de demostración — SOLO modo mock.

   El backend guarda con cada diagnóstico las cajas que el servicio de inferencia usó para
   elegirlo. El mock no tiene modelo, así que las inventa de forma determinística (misma tarjeta,
   mismas cajas) y COHERENTE con lo que muestra la tarjeta:

   - la cantidad de cajas del estado diagnosticado cae en la banda de su severidad, según el
     criterio por defecto;
   - su score promedio es exactamente el `conf` de la tarjeta, y el nivel de confianza sale de
     las bandas provisorias del equipo (design D4);
   - aparecen hojas sanas y, a veces, otro estado anómalo que "perdió" (menos hojas y menos
     confianza), que es lo que la vista "Todas" deja ver.

   Usa su propio RNG sembrado por el id de la tarjeta: no corre la secuencia del generador del
   vivero, que define todo lo demás.
   ============================================================ */
import { createRng } from '@/lib/rng';
import { hasConfidence, isAnomalous } from '@/lib/diagnosis';
import { DEFAULT_DIAGNOSIS_CRITERIA, MODEL_ANOMALOUS_STATES } from '@/lib/diagnosisCriteria';
import type { ConfidenceLevel, Detection, Severity } from '@/types/domain';

/**
 * Bandas de confianza por clase (válida, media desde, alta desde) sobre el score crudo: los
 * defaults provisorios del servicio de inferencia. En el sistema real las calibra el equipo por
 * variables de entorno y el dashboard no las conoce: acá sólo hacen falta para que la demo sea
 * coherente.
 */
export const MOCK_CONFIDENCE_BANDS: Record<string, [number, number, number]> = {
  Sano: [0.6, 0.7, 0.8],
  Clorosis: [0.4, 0.55, 0.7],
  'Estrés solar': [0.4, 0.55, 0.7],
  'Plaga foliar': [0.35, 0.5, 0.65],
  'Daño fúngico': [0.35, 0.5, 0.65],
};

const DEFAULT_BANDS: [number, number, number] = [0.4, 0.55, 0.7];
const bandsOf = (estado: string) => MOCK_CONFIDENCE_BANDS[estado] ?? DEFAULT_BANDS;

/** Nivel de confianza de un score promedio (× 100) según las bandas de la clase. */
export function mockConfidenceLevel(estado: string, conf: number | null): ConfidenceLevel | null {
  if (conf == null || !hasConfidence(estado)) return null;
  const [, medium, high] = bandsOf(estado);
  const s = conf / 100;
  return s >= high ? 'Alta' : s >= medium ? 'Media' : 'Baja';
}

/** Semilla estable a partir de un texto (FNV-1a). */
function seedOf(key: string): number {
  let h = 2166136261;
  for (const ch of key) {
    h ^= ch.charCodeAt(0);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

const round4 = (v: number) => Math.round(v * 10_000) / 10_000;

/** Entero en [lo, hi]. */
const intIn = (r: () => number, lo: number, hi: number) => lo + Math.floor(r() * (hi - lo + 1));

/** Cantidad de hojas del estado diagnosticado, dentro de la banda de su severidad. */
function countFor(r: () => number, estado: string, sev: Severity): number {
  const band = DEFAULT_DIAGNOSIS_CRITERIA.severityBands.find((b) => b.state === estado);
  if (!band) return intIn(r, 4, 10);
  if (sev === 'Alta') return intIn(r, band.highFrom, band.highFrom + 6);
  if (sev === 'Media') return intIn(r, band.mediumFrom, band.highFrom - 1);
  return intIn(r, band.minimum, band.mediumFrom - 1);
}

/** `n` scores cuyo promedio es exactamente `mean` (pares simétricos), sin salir de la banda válida. */
function scoresAround(r: () => number, n: number, mean: number, valid: number): number[] {
  const room = Math.max(0, Math.min(mean - valid, 0.99 - mean, 0.12));
  const out: number[] = [];
  for (let i = 0; i + 1 < n; i += 2) {
    const d = r() * room;
    out.push(mean + d, mean - d);
  }
  if (n % 2 === 1) out.push(mean);
  return out;
}

/** Scores sueltos en [lo, hi). */
const scoresIn = (r: () => number, n: number, lo: number, hi: number) =>
  Array.from({ length: n }, () => lo + r() * (hi - lo));

/**
 * Ubica `n` cajas en una grilla con desplazamiento: hojas repartidas por la foto, con algún
 * roce entre vecinas pero sin pilas de cajas iguales. Coordenadas normalizadas `xyxyn`.
 */
function layout(r: () => number, n: number): Detection['box'][] {
  if (n === 0) return [];
  const cols = Math.max(1, Math.ceil(Math.sqrt(n * 1.5)));
  const rows = Math.ceil(n / cols);
  const cells = Array.from({ length: cols * rows }, (_, i) => i);
  // Fisher–Yates: qué celdas quedan ocupadas cuando sobran
  for (let i = cells.length - 1; i > 0; i--) {
    const j = Math.floor(r() * (i + 1));
    [cells[i], cells[j]] = [cells[j], cells[i]];
  }
  const cw = 1 / cols;
  const ch = 1 / rows;
  return cells.slice(0, n).map((cell) => {
    const cx = ((cell % cols) + 0.5) * cw + (r() - 0.5) * cw * 0.35;
    const cy = (Math.floor(cell / cols) + 0.5) * ch + (r() - 0.5) * ch * 0.35;
    const w = cw * (0.6 + r() * 0.35);
    const h = ch * (0.6 + r() * 0.35);
    const x1 = Math.max(0, cx - w / 2);
    const y1 = Math.max(0, cy - h / 2);
    const x2 = Math.min(1, cx + w / 2);
    const y2 = Math.min(1, cy + h / 2);
    return [round4(x1), round4(y1), round4(x2), round4(y2)];
  });
}

/**
 * Detecciones de una tarjeta de la demo. `key` es su id: misma tarjeta, mismas cajas.
 * `conf` es el score promedio × 100 del estado diagnosticado (null en No concluyente).
 */
export function mockDetections(key: string, estado: string, sev: Severity, conf: number | null): Detection[] {
  const r = createRng(seedOf(key));
  const labels: string[] = [];
  const scores: number[] = [];
  const add = (label: string, list: number[]) => {
    for (const s of list) {
      labels.push(label);
      scores.push(round4(s));
    }
  };

  if (isAnomalous(estado) && conf != null) {
    const [valid] = bandsOf(estado);
    add(estado, scoresAround(r, countFor(r, estado, sev), conf / 100, valid));
    const [sanoValid] = bandsOf('Sano');
    add('Sano', scoresIn(r, intIn(r, 4, 10), sanoValid, 0.95));
    // Un estado anómalo que perdió: con severidad media o alta del diagnosticado, otro con pocas
    // hojas y confianza baja nunca le gana en relevancia.
    if (sev !== 'Baja' && r() < 0.6) {
      const others = MODEL_ANOMALOUS_STATES.filter((s) => s !== estado);
      const other = others[Math.floor(r() * others.length)];
      const band = DEFAULT_DIAGNOSIS_CRITERIA.severityBands.find((b) => b.state === other)!;
      const [v, medium] = bandsOf(other);
      add(other, scoresIn(r, intIn(r, 1, band.mediumFrom - 1), v, medium));
    }
  } else if (estado === 'Sano' && conf != null) {
    const [valid] = bandsOf('Sano');
    add('Sano', scoresAround(r, intIn(r, 12, 24), conf / 100, valid));
  } else {
    // No concluyente: ninguna clase anómala llega a su mínimo y no hay hojas sanas válidas. Quedan,
    // a veces, unas pocas cajas de un estado anómalo que no alcanzaron para diagnosticarlo.
    if (r() < 0.7) {
      const other = MODEL_ANOMALOUS_STATES[Math.floor(r() * MODEL_ANOMALOUS_STATES.length)];
      const band = DEFAULT_DIAGNOSIS_CRITERIA.severityBands.find((b) => b.state === other)!;
      const [v, medium] = bandsOf(other);
      add(other, scoresIn(r, intIn(r, 1, band.minimum - 1), v, medium));
    }
  }

  const boxes = layout(r, labels.length);
  return labels.map((label, i) => ({ label, score: scores[i], box: boxes[i] }));
}
