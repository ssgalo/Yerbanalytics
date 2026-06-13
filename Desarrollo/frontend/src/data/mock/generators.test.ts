import { describe, expect, it } from 'vitest';
import { buildNursery } from './generators';

const SEED = 20260613;

describe('buildNursery (fidelidad del RNG)', () => {
  it('es determinístico: misma semilla → mismas stats', () => {
    const a = buildNursery(SEED);
    const b = buildNursery(SEED);
    expect(a.stats).toEqual(b.stats);
  });

  it('genera 600 sectores en 6 macro-zonas de 100', () => {
    const { zonas, sectors } = buildNursery(SEED);
    expect(zonas).toHaveLength(6);
    zonas.forEach((z) => expect(z.sectors).toHaveLength(100));
    expect(sectors).toHaveLength(600);
  });

  it('las stats agregadas son coherentes', () => {
    const { stats } = buildNursery(SEED);
    expect(stats.total).toBe(600);
    expect(stats.sano + stats.warning + stats.critical + stats.offline).toBe(600);
    expect(stats.alerta).toBe(stats.warning + stats.critical);
    expect(stats.sanoPct).toBe(Math.round((stats.sano / 600) * 100));
    expect(stats.diagCount).toBeGreaterThan(0);
  });

  it('indexa los sectores por id', () => {
    const { byId, sectors } = buildNursery(SEED);
    expect(Object.keys(byId)).toHaveLength(600);
    expect(byId['MZ-1-001']).toBe(sectors[0]);
  });

  it('los diagnósticos no incluyen Sano ni Sin diagnóstico salvo No concluyente', () => {
    const { diagnoses } = buildNursery(SEED);
    diagnoses.forEach((d) => {
      expect(d.estado).not.toBe('Sano');
      expect(d.estado).not.toBe('Sin diagnóstico');
    });
  });
});
