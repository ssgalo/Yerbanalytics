/* ============================================================
   Equipos con problemas, para el resumen del Panel general.

   Sale del mismo estado técnico que muestra la vista Hardware, así las dos
   pantallas dicen el mismo número. Se refresca cada tanto como la actividad.
   ============================================================ */
import { useEffect, useState } from 'react';
import { getRepository } from '@/data';
import { summarizeEquipment, type EquipmentSummary } from './dashboardSummary';

const REFRESH_MS = 30_000;

/**
 * `enabled` = el rol puede ver los equipos; si no, no se consulta y devuelve `undefined`.
 * Mientras carga (o si falla la consulta) devuelve `null`.
 */
export function useEquipmentSummary(enabled: boolean): EquipmentSummary | null | undefined {
  const [summary, setSummary] = useState<EquipmentSummary | null>(null);

  useEffect(() => {
    if (!enabled) return;
    let active = true;

    function load() {
      getRepository()
        .getHardware()
        .then((data) => {
          if (active) setSummary(summarizeEquipment(data.dispositivos));
        })
        .catch(() => {
          /* Sin dato no se inventa uno: la tarjeta sigue diciendo que está consultando. */
        });
    }

    load();
    const interval = setInterval(load, REFRESH_MS);
    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [enabled]);

  return enabled ? summary : undefined;
}
