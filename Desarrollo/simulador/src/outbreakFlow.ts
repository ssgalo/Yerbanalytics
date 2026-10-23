/* ============================================================
   Qué sectores de la zona reciben el diagnóstico de un foco (F-04). El foco son varios sectores de la misma zona con el
   mismo diagnóstico, y el backend trata a sus lindantes en la grilla de la zona: conviene que los afectados estén en el
   medio, donde todos tienen sectores alrededor.

   Módulo sin React ni fetch, como `bypassFlow.ts`: se prueba sin backend. El simulador no edita la topología: elige entre
   los sectores que ya conoce.
   ============================================================ */
import type { SectorRef } from './types';

/**
 * Los `count` sectores contiguos del medio de la zona, por número de sector. Una zona con menos sectores de los pedidos no
 * da ninguno: no hay foco que mostrar.
 */
export function pickOutbreakSectors(zoneSectors: SectorRef[], count: number): SectorRef[] {
  if (count <= 0 || zoneSectors.length < count) return [];
  const ordered = [...zoneSectors].sort((a, b) => a.id.localeCompare(b.id));
  const start = Math.min(Math.floor(ordered.length / 2), ordered.length - count);
  return ordered.slice(start, start + count);
}
