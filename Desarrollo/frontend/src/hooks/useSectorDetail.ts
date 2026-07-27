/* ============================================================
   Hook: detalle derivado de un sector. Memoizado para no recalcular
   en cada render. Ya no depende de un rango temporal: el gráfico de
   series vive en el panel de sensado de la macro-zona.
   ============================================================ */
import { useMemo } from 'react';
import { selectSectorDetail } from '@/data';
import type { Sector, SectorDetail, Zona } from '@/types/domain';
import { useNurseryData } from './NurseryContext';

export function useSectorDetail(sectorId: string | undefined): {
  sector: Sector | null;
  /** Macro-zona del sector: dueña de la lectura sensada que explica su estado. */
  zona: Zona | null;
  detail: SectorDetail | null;
} {
  const data = useNurseryData();
  return useMemo(() => {
    if (!sectorId) return { sector: null, zona: null, detail: null };
    const sector = data.byId[sectorId] ?? null;
    const zona = sector ? (data.zonas.find((z) => z.id === sector.zona) ?? null) : null;
    const detail = selectSectorDetail(data, sectorId);
    return { sector, zona, detail };
  }, [data, sectorId]);
}
