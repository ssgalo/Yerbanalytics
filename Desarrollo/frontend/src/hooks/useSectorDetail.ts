/* ============================================================
   Hook: detalle derivado de un sector para un rango temporal.
   Memoizado para no recalcular gráficos en cada render.
   ============================================================ */
import { useMemo } from 'react';
import { selectSectorDetail } from '@/data';
import type { Range, Sector, SectorDetail } from '@/types/domain';
import { useNurseryData } from './NurseryContext';

export function useSectorDetail(
  sectorId: string | undefined,
  range: Range,
): { sector: Sector | null; detail: SectorDetail | null } {
  const data = useNurseryData();
  return useMemo(() => {
    if (!sectorId) return { sector: null, detail: null };
    const sector = data.byId[sectorId] ?? null;
    const detail = selectSectorDetail(data, sectorId, range);
    return { sector, detail };
  }, [data, sectorId, range]);
}
