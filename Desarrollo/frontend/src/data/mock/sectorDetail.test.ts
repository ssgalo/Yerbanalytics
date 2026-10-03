import { describe, expect, it } from 'vitest';
import { buildNursery } from './generators';
import { buildSectorDetail } from './sectorDetail';

const SEED = 20260613;

const filaValvula = (valve: string) => {
  const { zonas } = buildNursery(SEED);
  const zona = zonas[0];
  const sector = { ...zona.sectors[0], actuadores: { ...zona.sectors[0].actuadores, valve } };
  return buildSectorDetail(sector, zona.lectura).actsRows[0];
};

describe('buildSectorDetail · estado de la válvula (13.5)', () => {
  it('"En cola" se muestra tal cual, sin pasar por abierta ni por cerrada', () => {
    const fila = filaValvula('En cola');

    expect(fila.state).toBe('En cola');
    expect(fila.active).toBe(false);
    // Un estilo propio (ámbar): ni el verde de "Regando" ni el gris de "Cerrada".
    expect(fila.dot).not.toBe(filaValvula('Regando').dot);
    expect(fila.dot).not.toBe(filaValvula('Cerrada').dot);
  });

  it('"Regando" es la única que queda activa', () => {
    expect(filaValvula('Regando')).toMatchObject({ state: 'Regando', active: true });
    expect(filaValvula('Cerrada')).toMatchObject({ state: 'Cerrada', active: false });
  });
});
