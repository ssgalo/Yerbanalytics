/* ============================================================
   Presentación de las fallas de hardware (S-05). El backend guarda en `Dispositivo.falla` el `tipo` del error que
   informó el nodo (contrato MQTT: `SIN_CAUDAL`, `CAUDAL_EXCESIVO`, `MEDIASOMBRA_FIN_DE_CARRERA`); acá se traduce al
   texto que ve el operador. Un valor que el contrato no define se muestra tal cual.
   ============================================================ */

export interface FallaPresentada {
  titulo: string;
  /** Qué falló, en minúscula; `null` si el valor no es del contrato. */
  detalle: string | null;
}

const FALLAS: Record<string, FallaPresentada> = {
  SIN_CAUDAL: { titulo: 'Falla hidráulica', detalle: 'sin caudal' },
  CAUDAL_EXCESIVO: { titulo: 'Falla hidráulica', detalle: 'caudal excesivo' },
  MEDIASOMBRA_FIN_DE_CARRERA: { titulo: 'Falla mecánica en mediasombra', detalle: 'fin de carrera sin activar' },
};

export function describirFalla(falla: string): FallaPresentada {
  return FALLAS[falla] ?? { titulo: falla, detalle: null };
}

/** "hace 3 h": desde cuándo está marcada la falla, o `null` si no se sabe. */
export function desdeCuando(fallaDesde: number | null, ahora: number = Date.now()): string | null {
  if (fallaDesde === null) return null;
  const seg = Math.max(0, Math.floor((ahora - fallaDesde) / 1000));
  if (seg < 60) return `hace ${seg} s`;
  const min = Math.floor(seg / 60);
  if (min < 60) return `hace ${min} min`;
  const horas = Math.floor(min / 60);
  if (horas < 24) return `hace ${horas} h`;
  return `hace ${Math.floor(horas / 24)} d`;
}
