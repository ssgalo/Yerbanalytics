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

  it('la lectura sensada vive en la macro-zona, no en el sector', () => {
    const { zonas, sectors } = buildNursery(SEED);
    zonas.forEach((z) => {
      expect(z.lectura.metrics).toHaveLength(10);
      expect(z.nodo.mac).toMatch(/^A4:CF:12:9A:00:\d{2}$/);
    });
    // El sector no expone métricas propias: las comparte con su zona.
    sectors.forEach((s) => expect(s).not.toHaveProperty('metrics'));
  });

  it('los sectores de una zona no pueden estar mejor que su lectura ambiental', () => {
    const { zonas } = buildNursery(SEED);
    const orden = { ok: 0, warning: 1, critical: 2, offline: 3 };
    zonas.forEach((z) => {
      const decisivas = z.lectura.metrics.filter((m) => m.spec.afectaEstado);
      const piso = decisivas.some((m) => m.status === 'critical')
        ? 'critical'
        : decisivas.some((m) => m.status === 'warning')
          ? 'warning'
          : 'ok';
      z.sectors.forEach((s) => expect(orden[s.status]).toBeGreaterThanOrEqual(orden[piso]));
    });
  });

  it('los diagnósticos no incluyen Sano ni Sin diagnóstico salvo No concluyente', () => {
    const { diagnoses } = buildNursery(SEED);
    diagnoses.forEach((d) => {
      expect(d.estado).not.toBe('Sano');
      expect(d.estado).not.toBe('Sin diagnóstico');
    });
  });
});

describe('buildNursery · válvulas del riego por tandas (13.5)', () => {
  const valvulas = (zonaId: string) => {
    const z = buildNursery(SEED).zonas.find((x) => x.id === zonaId)!;
    return z.sectors.filter((s) => s.status !== 'offline').map((s) => s.actuadores.valve);
  };
  const humSus = (zonaId: string) =>
    buildNursery(SEED).zonas.find((x) => x.id === zonaId)!.lectura.metrics.find((m) => m.key === 'humSus')!.raw;

  it('con déficit el despacho riega de a 10 sectores y el resto queda "En cola"', () => {
    // MZ-2: déficit común (40 % bajo el umbral de 45 %) y pronóstico sin lluvia que alcance.
    expect(humSus('MZ-2')).toBe(40);
    const z = buildNursery(SEED).zonas.find((x) => x.id === 'MZ-2')!;
    const regando = z.sectors.filter((s) => s.actuadores.valve === 'Regando');
    const enCola = z.sectors.filter((s) => s.actuadores.valve === 'En cola');

    expect(regando).toHaveLength(10);
    expect(regando.every((s) => s.n <= 10)).toBe(true);
    expect(enCola.length).toBeGreaterThan(0);
    expect(enCola.every((s) => s.n > 10)).toBe(true);
  });

  it('una zona con la humedad dentro del rango ideal (sobre el umbral de 45 %) tiene todas las válvulas cerradas', () => {
    expect(new Set(valvulas('MZ-1'))).toEqual(new Set(['Cerrada']));
  });

  it('con lluvia prevista suficiente el déficit común espera: no hay válvulas abiertas ni en cola', () => {
    expect(humSus('MZ-3')).toBe(41);
    expect(new Set(valvulas('MZ-3'))).toEqual(new Set(['Cerrada']));
  });

  it('el déficit crítico riega aunque se prevea lluvia (R-02 no la espera)', () => {
    expect(humSus('MZ-4')).toBe(30);
    expect(valvulas('MZ-4')).toContain('Regando');
  });

  it('un sustrato saturado no riega', () => {
    expect(humSus('MZ-5')).toBe(82);
    expect(new Set(valvulas('MZ-5'))).toEqual(new Set(['Cerrada']));
  });
});
