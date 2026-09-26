/* ============================================================
   Capturas de demostración del vivero — SOLO modo mock.

   Fotografías reales del dataset de yerba mate, recortadas en 3:2 con el síntoma
   centrado: la galería las muestra en una caja de 388x120 y el detalle de sector en
   una de 150x150, y `object-fit: cover` recorta hacia el centro en ambos casos.

   Archivo generado desde `public/demo/capturas/`. No editar a mano.
   ============================================================ */

/** Rutas de captura disponibles por estado de diagnóstico. */
export const capturasDemo: Record<string, string[]> = {
  'Clorosis': [
    '/demo/capturas/clorosis/01.jpg',
    '/demo/capturas/clorosis/02.jpg',
    '/demo/capturas/clorosis/03.jpg',
    '/demo/capturas/clorosis/04.jpg',
    '/demo/capturas/clorosis/05.jpg',
  ],
  'Estrés solar': [
    '/demo/capturas/estres-solar/01.jpg',
    '/demo/capturas/estres-solar/02.jpg',
    '/demo/capturas/estres-solar/03.jpg',
    '/demo/capturas/estres-solar/04.jpg',
    '/demo/capturas/estres-solar/05.jpg',
  ],
  'Daño fúngico': [
    '/demo/capturas/dano-fungico/01.jpg',
    '/demo/capturas/dano-fungico/02.jpg',
    '/demo/capturas/dano-fungico/03.jpg',
    '/demo/capturas/dano-fungico/04.jpg',
    '/demo/capturas/dano-fungico/05.jpg',
  ],
  'Plaga foliar': [
    '/demo/capturas/plaga-foliar/01.jpg',
    '/demo/capturas/plaga-foliar/02.jpg',
    '/demo/capturas/plaga-foliar/03.jpg',
    '/demo/capturas/plaga-foliar/04.jpg',
    '/demo/capturas/plaga-foliar/05.jpg',
  ],
  'No concluyente': [
    '/demo/capturas/no-concluyente/01.jpg',
    '/demo/capturas/no-concluyente/02.jpg',
    '/demo/capturas/no-concluyente/03.jpg',
    '/demo/capturas/no-concluyente/04.jpg',
    '/demo/capturas/no-concluyente/05.jpg',
  ],
};

/**
 * Captura correspondiente a un diagnóstico, elegida por índice y no al azar.
 *
 * Usar el RNG del generador acá correría toda la secuencia siguiente y cambiaría los
 * valores del vivero sembrado; el índice del diagnóstico mantiene la selección
 * determinística sin tocar ese stream. Al ser el contador global, además, dos
 * diagnósticos consecutivos del mismo estado nunca reciben la misma fotografía.
 *
 * Los pools tienen cinco fotos a propósito: la galería es de cuatro columnas, así que un
 * pool de cuatro repetiría la misma imagen en la tarjeta de justo arriba. Cinco es coprimo
 * con cuatro y ninguna se repite en la misma columna.
 */
export function capturaDemo(estado: string, indice: number): string | undefined {
  const pool = capturasDemo[estado];
  if (!pool || pool.length === 0) return undefined;
  return pool[indice % pool.length];
}
