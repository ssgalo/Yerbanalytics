/* ============================================================
   Presentación del diagnóstico de IA: qué datos se muestran según el estado.

   - Severidad: sólo en estados anómalos.
   - Nivel de confianza: en todos salvo No concluyente (y sin diagnóstico).
   - El score promedio (`conf`) y la cantidad de detecciones NO se muestran en el dashboard.
   - Un diagnóstico anterior a los niveles llega con `confidence: null`: se muestra "sin nivel".
   La vista nunca dice si el motor puede actuar: eso es del motor, no del diagnóstico.
   ============================================================ */
import type { ConfidenceLevel } from '@/types/domain';

/** Estados que no son una anomalía del plantín. */
const NOT_ANOMALOUS = new Set(['Sano', 'No concluyente', 'Sin diagnóstico']);

/** Clorosis, Estrés solar, Plaga foliar, Daño fúngico (y las etiquetas viejas: Daño biótico, Ácaro). */
export function isAnomalous(estado: string): boolean {
  return !NOT_ANOMALOUS.has(estado);
}

/** ¿El diagnóstico informa nivel de confianza? No en No concluyente ni sin diagnóstico. */
export function hasConfidence(estado: string): boolean {
  return estado !== 'No concluyente' && estado !== 'Sin diagnóstico';
}

/** "Confianza media"; "Sin nivel de confianza" para un diagnóstico anterior a los niveles. */
export function confidenceLabel(level: ConfidenceLevel | null | undefined): string {
  return level ? `Confianza ${level.toLowerCase()}` : 'Sin nivel de confianza';
}
