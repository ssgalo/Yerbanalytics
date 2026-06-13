/* ============================================================
   RNG determinístico — portado EXACTO desde Yerbanalytics.dc.html
   (mulberry32). NO reordenar llamadas: el orden define la salida.
   ============================================================ */

/** Crea un generador pseudoaleatorio sembrado y reproducible. */
export function createRng(seed: number): () => number {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Elige un elemento al azar del array usando el generador `r`. */
export function pick<T>(r: () => number, arr: T[]): T {
  return arr[Math.floor(r() * arr.length)];
}

/** Número aleatorio en el rango [a, b) usando el generador `r`. */
export function rr(r: () => number, a: number, b: number): number {
  return a + (b - a) * r();
}
