/* ============================================================
   Generación dinámica de la topología del vivero (HU-18 CA-01). Determinístico y
   espejo de `TopologiaService` del backend: valida rangos y conflicto igual que el
   servidor, y deriva el resumen e ids de la grilla (`MZ-{z}` / `MZ-{z}-{NNN}`).
   ============================================================ */
import type { DisposicionTopologia, NuevaTopologia, TopologiaVivero } from '@/types/domain';

/** Límites operativos de la grilla (espejo de `TopologiaService`). */
export const MAX_MACRO_ZONAS = 50;
export const MAX_SECTORES_POR_ZONA = 500;

/** Macro-zonas por defecto del vivero, alineadas con el seed (6 × 100). */
export const DEFAULT_MACRO_ZONAS = 6;
export const DEFAULT_SECTORES_POR_MACRO_ZONA = 100;

/** Disposición visual por defecto (reproduce la presentación previa al cambio). */
export const DEFAULT_MACRO_ZONAS_POR_FILA = 3;
export const DEFAULT_SECTORES_POR_FILA = 10;

/** Acota un valor de disposición al rango válido [1, max]; con grilla vacía deja el valor. */
export function clampDisposicion(value: number, max: number): number {
  if (value < 1) return 1;
  return max > 0 ? Math.min(value, max) : value;
}

/** Sub-zona ciclada por macro-zona, replicando el estilo del seed. */
const SUBS = ['Sector norte', 'Sector centro', 'Sector sur'];

/** Definición lógica de una macro-zona generada. */
export interface ZonaDef {
  id: string;
  name: string;
  sub: string;
}

/** Resumen de una topología de N × M, con su disposición visual acotada a las cantidades. */
export function topologiaSummary(
  macroZonas: number,
  sectoresPorMacroZona: number,
  disposicion?: DisposicionTopologia,
): TopologiaVivero {
  const mzPorFila = disposicion?.macroZonasPorFila ?? DEFAULT_MACRO_ZONAS_POR_FILA;
  const secPorFila = disposicion?.sectoresPorFila ?? DEFAULT_SECTORES_POR_FILA;
  return {
    macroZonas,
    sectoresPorMacroZona,
    totalSectores: macroZonas * sectoresPorMacroZona,
    generada: macroZonas > 0,
    macroZonasPorFila: clampDisposicion(mzPorFila, macroZonas),
    sectoresPorFila: clampDisposicion(secPorFila, sectoresPorMacroZona),
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

/**
 * Valida una disposición visual igual que el backend; devuelve el mensaje de error o null.
 * Acota a la grilla actual: macro-zonas por fila ≤ macro-zonas; sectores por fila ≤ sectores
 * por macro-zona.
 */
export function disposicionError(
  disposicion: DisposicionTopologia,
  macroZonas: number,
  sectoresPorMacroZona: number,
): string | null {
  const { macroZonasPorFila, sectoresPorFila } = disposicion;
  if (
    !Number.isInteger(macroZonasPorFila) ||
    macroZonasPorFila <= 0 ||
    (macroZonas > 0 && macroZonasPorFila > macroZonas)
  ) {
    return `Las macro-zonas por fila deben estar entre 1 y ${Math.max(1, macroZonas)}.`;
  }
  if (
    !Number.isInteger(sectoresPorFila) ||
    sectoresPorFila <= 0 ||
    (sectoresPorMacroZona > 0 && sectoresPorFila > sectoresPorMacroZona)
  ) {
    return `Los sectores por fila deben estar entre 1 y ${Math.max(1, sectoresPorMacroZona)}.`;
  }
  return null;
}
