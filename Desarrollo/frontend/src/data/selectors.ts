/* ============================================================
   Selectores: derivaciones puras sobre el dataset del vivero.
   La UI consume estas funciones sin conocer su implementación.
   ============================================================ */
import type { NurseryData, Range, SectorDetail } from '@/types/domain';
import { buildSectorDetail } from './mock/sectorDetail';

/** Detalle derivado de un sector (gráficos, tiles, historial) para un rango. */
export function selectSectorDetail(
  data: NurseryData,
  sectorId: string,
  range: Range,
): SectorDetail | null {
  const sector = data.byId[sectorId];
  if (!sector) return null;
  return buildSectorDetail(sector, range);
}
