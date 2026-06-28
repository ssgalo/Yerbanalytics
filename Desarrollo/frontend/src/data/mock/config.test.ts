import { describe, expect, it } from 'vitest';
import { buildConfig } from './config';
import { specs } from './specs';
import {
  etapasError,
  operativaErrors,
  umbralError,
  validateConfig,
} from '@/lib/configValidation';
import type { Configuracion } from '@/types/domain';

describe('buildConfig (configuración de fábrica)', () => {
  it('es determinística: dos construcciones son equivalentes', () => {
    expect(buildConfig()).toEqual(buildConfig());
  });

  it('cubre las 5 métricas con las bandas de las specs base', () => {
    const cfg = buildConfig();
    expect(cfg.umbrales.map((u) => u.key)).toEqual(specs.map((s) => s.key));
    cfg.umbrales.forEach((u) => {
      const s = specs.find((x) => x.key === u.key)!;
      expect([u.idealMin, u.idealMax]).toEqual(s.ideal);
      expect([u.critMin, u.critMax]).toEqual(s.crit);
    });
  });

  it('la configuración de fábrica es válida', () => {
    expect(validateConfig(buildConfig())).toEqual([]);
  });
});

describe('validación de configuración (HU-15 CA-03..06)', () => {
  it('detecta bandas incoherentes en una métrica', () => {
    const cfg = buildConfig();
    cfg.umbrales[0] = { ...cfg.umbrales[0], idealMin: 90, idealMax: 10 };
    expect(umbralError(cfg.umbrales[0])).not.toBeNull();
    expect(validateConfig(cfg).length).toBeGreaterThan(0);
  });

  it('detecta valores fuera del rango fisiológico de fábrica', () => {
    const cfg = buildConfig();
    const u = cfg.umbrales[0];
    // crit máx por encima del envelope de fábrica
    cfg.umbrales[0] = { ...u, critMax: u.critMax + 1000 };
    expect(umbralError(cfg.umbrales[0])).not.toBeNull();
  });

  it('rechaza límites operativos no positivos', () => {
    const cfg = buildConfig();
    cfg.operativa = { ...cfg.operativa, riegoVolMaxDiarioMl: 0 };
    expect(operativaErrors(cfg.operativa).length).toBeGreaterThan(0);
  });

  it('rechaza una apertura de mediasombra fuera de [0, 100]', () => {
    const cfg = buildConfig();
    cfg.operativa = { ...cfg.operativa, mediasombraAperturaMaxPct: 150 };
    expect(operativaErrors(cfg.operativa).length).toBeGreaterThan(0);
  });

  it('detecta etapas de rustificación solapadas', () => {
    const cfg = buildConfig();
    const err = etapasError(
      [
        { orden: 1, diaDesde: 1, diaHasta: 10, aperturaPct: 20 },
        { orden: 2, diaDesde: 5, diaHasta: 15, aperturaPct: 40 },
      ],
      100,
    );
    expect(err).not.toBeNull();
    expect(cfg.rustificacion.length).toBeGreaterThan(0);
  });

  it('acepta un plan de rustificación bien formado', () => {
    expect(etapasError(buildConfig().rustificacion, 100)).toBeNull();
  });

  it('exige las 5 métricas: una config incompleta es inválida', () => {
    const cfg: Configuracion = buildConfig();
    cfg.umbrales = cfg.umbrales.slice(0, 3);
    expect(validateConfig(cfg).length).toBeGreaterThan(0);
  });
});
