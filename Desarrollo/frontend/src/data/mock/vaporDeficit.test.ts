import { describe, expect, it } from 'vitest';
import { vaporDeficit, vaporDeficitDeMetricas } from './vaporDeficit';
import type { Metric } from '@/types/domain';

describe('vaporDeficit', () => {
  it('replica el valor de referencia del backend: 25 °C y 60 % dan 1,27 kPa', () => {
    expect(vaporDeficit(25, 60)).toEqual({ value: 1.27, band: 'HIGH_DEMAND', label: 'demanda alta' });
  });

  it('aire saturado: HR 100 % da 0 kPa', () => {
    expect(vaporDeficit(25, 100)).toEqual({ value: 0, band: 'SATURATED', label: 'aire saturado' });
  });

  it('calor seco: 38 °C y 25 % dan 4,97 kPa', () => {
    expect(vaporDeficit(38, 25)).toMatchObject({ value: 4.97, band: 'DRY_HEAT' });
  });

  it.each([
    [0.29, 'SATURATED'],
    [0.3, 'TRANSITION'],
    [0.5, 'COMFORTABLE'],
    [1.2, 'HIGH_DEMAND'],
    [2.0, 'DRY_HEAT'],
  ])('el tramo de %s kPa es %s (el límite inferior pertenece al tramo)', (kpa, band) => {
    // Se busca la humedad que da ese DPV a 25 °C: HR = 100 · (1 − dpv / es)
    const es = 0.6108 * Math.exp((17.27 * 25) / (25 + 237.3));
    const hr = 100 * (1 - kpa / es);
    expect(vaporDeficit(25, hr)?.band).toBe(band);
  });

  it('sin entradas, o con humedad fuera de 0–100, no hay valor (no un cero)', () => {
    expect(vaporDeficit(null, 60)).toBeNull();
    expect(vaporDeficit(25, null)).toBeNull();
    expect(vaporDeficit(25, 130)).toBeNull();
  });
});

describe('vaporDeficitDeMetricas', () => {
  const metrica = (key: string, raw: number | null) => ({ key, raw }) as Metric;

  it('lo deriva de la temperatura del aire y la humedad ambiental de la lectura', () => {
    const dpv = vaporDeficitDeMetricas([metrica('temp', 25), metrica('humAmb', 60), metrica('uv', 50)]);
    expect(dpv?.value).toBe(1.27);
  });

  it('sin alguna de las dos métricas no hay DPV', () => {
    expect(vaporDeficitDeMetricas([metrica('temp', 25), metrica('humAmb', null)])).toBeNull();
    expect(vaporDeficitDeMetricas([metrica('temp', 25)])).toBeNull();
  });
});
