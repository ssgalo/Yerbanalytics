/* ============================================================
   Geometría del dibujo del sector físico (coordenadas del viewBox).

   Un sector son 4 bandejas de 5×5 tubetes, regadas por un microaspersor
   compartido. Vive aparte del JSX para poder verificar que todo entra en
   la caja y que el nodo testigo —que es de la macro-zona— queda afuera.
   ============================================================ */

export const BANDEJAS = 4;
export const FILAS = 5;
export const COLUMNAS = 5;
export const TUBETES_POR_SECTOR = BANDEJAS * FILAS * COLUMNAS;

interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface Tubete {
  bandeja: number;
  cx: number;
  cy: number;
}

export interface GeometriaSector {
  viewBox: { w: number; h: number };
  caja: Rect;
  bandejas: Rect[];
  tubetes: Tubete[];
  radio: number;
  aspersor: { x: number; y: number; radioRiego: number };
  nodo: Rect;
}

const VIEWBOX = { w: 640, h: 520 };
const CAJA: Rect = { x: 20, y: 78, w: 420, h: 400 };
const MARGEN = 12;
const SEPARACION = 12;

export function calcularGeometria(): GeometriaSector {
  const bw = (CAJA.w - 2 * MARGEN - SEPARACION) / 2;
  const bh = (CAJA.h - 2 * MARGEN - SEPARACION) / 2;
  const bandejas: Rect[] = [0, 1, 2, 3].map((i) => ({
    x: CAJA.x + MARGEN + (i % 2) * (bw + SEPARACION),
    y: CAJA.y + MARGEN + Math.floor(i / 2) * (bh + SEPARACION),
    w: bw,
    h: bh,
  }));

  const cw = bw / COLUMNAS;
  const ch = bh / FILAS;
  const radio = Math.min(cw, ch) * 0.34;

  const tubetes: Tubete[] = bandejas.flatMap((b, bandeja) =>
    Array.from({ length: FILAS * COLUMNAS }, (_, k) => ({
      bandeja,
      cx: b.x + cw * (k % COLUMNAS) + cw / 2,
      cy: b.y + ch * Math.floor(k / COLUMNAS) + ch / 2,
    })),
  );

  return {
    viewBox: VIEWBOX,
    caja: CAJA,
    bandejas,
    tubetes,
    radio,
    aspersor: {
      x: CAJA.x + CAJA.w / 2,
      y: CAJA.y + CAJA.h / 2,
      radioRiego: Math.min(CAJA.w, CAJA.h) / 2 + 6,
    },
    nodo: { x: 470, y: 90, w: 150, h: 54 },
  };
}
