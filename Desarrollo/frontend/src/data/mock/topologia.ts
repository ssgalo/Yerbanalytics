/* ============================================================
   Generación dinámica de la topología del vivero (HU-18 CA-01). Determinístico y
   espejo de `TopologiaService` del backend: valida rangos y conflicto igual que el
   servidor, y deriva el resumen e ids de la grilla (`MZ-{z}` / `MZ-{z}-{NNN}`).
   ============================================================ */
import type { NuevaTopologia, TopologiaVivero } from '@/types/domain';

/** Límites operativos de la grilla (espejo de `TopologiaService`). */
export const MAX_MACRO_ZONAS = 50;
export const MAX_SECTORES_POR_ZONA = 500;

/** Macro-zonas por defecto del vivero, alineadas con el seed (6 × 100). */
export const DEFAULT_MACRO_ZONAS = 6;
export const DEFAULT_SECTORES_POR_MACRO_ZONA = 100;

/** Sub-zona ciclada por macro-zona, replicando el estilo del seed. */
const SUBS = ['Sector norte', 'Sector centro', 'Sector sur'];

/** Definición lógica de una macro-zona generada. */
export interface ZonaDef {
  id: string;
  name: string;
  sub: string;
}

/** Resumen de una topología de N × M. */
export function topologiaSummary(macroZonas: number, sectoresPorMacroZona: number): TopologiaVivero {
  return {
    macroZonas,
    sectoresPorMacroZona,
    totalSectores: macroZonas * sectoresPorMacroZona,
    generada: macroZonas > 0,
  };
}

/** Definiciones de las N macro-zonas (`MZ-1`..`MZ-N`) con nombre y sub sintetizados. */
export function zonaDefsFor(macroZonas: number): ZonaDef[] {
  return Array.from({ length: Math.max(0, macroZonas) }, (_, i) => {
    const z = i + 1;
    return { id: `MZ-${z}`, name: `Macro-zona ${z}`, sub: SUBS[(z - 1) % SUBS.length] };
  });
}

/**
 * Valida una generación igual que el backend; devuelve el mensaje de error o null.
 * `existeTopologia` indica si ya hay una grilla cargada (sin `regenerar`, se rechaza).
 */
export function topologiaError(existeTopologia: boolean, input: NuevaTopologia): string | null {
  const { macroZonas, sectoresPorMacroZona } = input;
  if (!Number.isInteger(macroZonas) || macroZonas <= 0 || macroZonas > MAX_MACRO_ZONAS) {
    return `La cantidad de macro-zonas debe estar entre 1 y ${MAX_MACRO_ZONAS}.`;
  }
  if (
    !Number.isInteger(sectoresPorMacroZona) ||
    sectoresPorMacroZona <= 0 ||
    sectoresPorMacroZona > MAX_SECTORES_POR_ZONA
  ) {
    return `La cantidad de sectores por macro-zona debe estar entre 1 y ${MAX_SECTORES_POR_ZONA}.`;
  }
  if (existeTopologia && !input.regenerar) {
    return 'El vivero ya tiene una topología cargada. Confirmá la regeneración para reemplazarla.';
  }
  return null;
}
