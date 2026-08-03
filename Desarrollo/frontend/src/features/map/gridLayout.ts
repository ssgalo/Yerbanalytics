/* ============================================================
   Geometría de la grilla de sectores.

   Vive aparte del componente porque MapPage necesita el ancho
   resultante para dimensionar la columna del mapa, y SectorGrid
   necesita el lado de celda para dibujar.

   Las constantes deben coincidir con SectorGrid.module.css.
   ============================================================ */

/** Separación entre celdas, en px. */
export const GRID_GAP = 6;
/** Respiro interno de la tarjeta, en px. */
export const GRID_PADDING = 12;
/** Grosor del borde de la tarjeta, en px. */
export const GRID_BORDE = 1;
/** Alto que ocupa el resumen de zona dentro de la tarjeta (chips + separación). */
export const RESUMEN_ALTO = 54;

/**
 * Lado de celda que hace entrar la grilla completa: el menor entre lo que permite el
 * alto disponible y el ancho concedido. Garantiza celdas cuadradas.
 *
 * Devuelve 0 si todavía no hay espacio medido (primer render).
 */
export function calcularCelda(
  cols: number,
  rows: number,
  alto: number,
  anchoMaximo: number,
): number {
  const alrededor = GRID_PADDING * 2 + GRID_BORDE * 2;
  const altoUtil = alto - alrededor - RESUMEN_ALTO - (rows - 1) * GRID_GAP;
  const anchoUtil = anchoMaximo - alrededor - (cols - 1) * GRID_GAP;
  if (altoUtil <= 0 || anchoUtil <= 0) return 0;
  return Math.max(4, Math.floor(Math.min(altoUtil / rows, anchoUtil / cols)));
}

/**
 * Ancho total de la tarjeta para un lado de celda dado, borde incluido.
 *
 * El borde cuenta: la tarjeta usa `box-sizing: content-box`, así que sobresale de
 * la caja de contenido. Si la columna se dimensionara sin él, su `overflow: hidden`
 * recortaría la línea y el redondeo del lado derecho.
 */
export function anchoDeCaja(cols: number, cell: number): number {
  return cols * cell + (cols - 1) * GRID_GAP + GRID_PADDING * 2 + GRID_BORDE * 2;
}
