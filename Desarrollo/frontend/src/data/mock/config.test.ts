import { describe, expect, it } from 'vitest';
import { buildConfig } from './config';
import { specs } from './specs';
import {
  bandMidpoint,
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

  it('ya no trae los dos límites que se mudaron al catálogo del motor', () => {
    const op = buildConfig().operativa as unknown as Record<string, unknown>;
    expect(op).not.toHaveProperty('riegoTiempoMaxSeg');
    expect(op).not.toHaveProperty('mediasombraAperturaMaxPct');
  });
});

describe('validación de configuración (HU-15 CA-03..06)', () => {
  it('detecta bandas incoherentes en una métrica', () => {
    const cfg = buildConfig();
    cfg.umbrales[0] = { ...cfg.umbrales[0], idealMin: 90, idealMax: 10 };
    expect(umbralError(cfg.umbrales[0])).not.toBeNull();
    expect(validateConfig(cfg).length).toBeGreaterThan(0);
  });

  it('el punto de alerta de fábrica es el medio de cada franja amarilla', () => {
    const hum = buildConfig().umbrales.find((u) => u.key === 'humAmb')!;
    expect(hum.alertMin).toBe(bandMidpoint(hum.warnMin, hum.idealMin, hum.dec));
    expect(hum.alertMax).toBe(bandMidpoint(hum.idealMax, hum.warnMax, hum.dec));
    expect(umbralError(hum)).toBeNull();
  });

  it('detecta un punto de alerta fuera de su franja', () => {
    const u = buildConfig().umbrales[0];
    expect(umbralError({ ...u, alertMin: u.warnMin - 1 })).toMatch(/punto de alerta/);
    expect(umbralError({ ...u, alertMax: u.idealMax - 1 })).toMatch(/punto de alerta/);
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
    cfg.operativa = { ...cfg.operativa, insumoDosisMax24hMl: 0 };
    expect(operativaErrors(cfg.operativa).length).toBeGreaterThan(0);
  });

  it('el horario por defecto de la mediasombra es válido y exige cierre posterior a la apertura', () => {
    const cfg = buildConfig();
    expect(cfg.operativa.mediasombraAbiertaDesde).toBe('08:00');
    expect(cfg.operativa.mediasombraAbiertaHasta).toBe('17:00');
    cfg.operativa = { ...cfg.operativa, mediasombraAbiertaDesde: '18:00', mediasombraAbiertaHasta: '08:00' };
    expect(operativaErrors(cfg.operativa)).toEqual([
      'Horario de la mediasombra: la hora de cierre debe ser posterior a la de apertura.',
    ]);
  });

  it('la configuración ya no trae el plan de rustificación (es por macro-zona)', () => {
    expect(buildConfig()).not.toHaveProperty('rustificacion');
  });

  it('exige las 5 métricas: una config incompleta es inválida', () => {
    const cfg: Configuracion = buildConfig();
    cfg.umbrales = cfg.umbrales.slice(0, 3);
    expect(validateConfig(cfg).length).toBeGreaterThan(0);
  });
});

describe('pulso de sensado y pasada diaria', () => {
  const con = (patch: Partial<Configuracion['operativa']>) => {
    const cfg = buildConfig();
    cfg.operativa = { ...cfg.operativa, ...patch };
    return validateConfig(cfg);
  };

  it('de fábrica: pulso de 240 min, pasada apagada a las 11:00 y umbral del backend', () => {
    const op = buildConfig().operativa;
    expect(op).toMatchObject({ intervaloSensadoMinutos: 240, pasadaDiariaActiva: false, pasadaDiariaHora: '11:00' });
    expect(op.effectiveStaleThresholdSeconds).toBe(14580);
  });

  it('ya no trae el intervalo de evaluación ni el pedido de lectura en segundos', () => {
    const op = buildConfig().operativa as unknown as Record<string, unknown>;
    expect(op).not.toHaveProperty('intervaloEvaluacionMinutos');
    expect(op).not.toHaveProperty('sensingPullIntervalSeconds');
  });

  it.each([30, 59, 361, 400, 90.5])('rechaza un intervalo de %s min', (v) => {
    expect(con({ intervaloSensadoMinutos: v })).toEqual([
      'El intervalo de sensado y evaluación debe ser un entero entre 60 y 360 minutos.',
    ]);
  });

  it.each([60, 240, 360])('acepta un intervalo de %s min', (v) => {
    expect(con({ intervaloSensadoMinutos: v })).toEqual([]);
  });

  it.each(['', '24:00', '12:60', '9:30', 'mediodía'])('rechaza la hora «%s»', (v) => {
    expect(con({ pasadaDiariaHora: v })).toEqual([
      'La hora de la pasada diaria debe tener el formato HH:MM (00:00 a 23:59).',
    ]);
  });

  it.each(['00:00', '10:30', '23:59'])('acepta la hora %s, con la pasada apagada o encendida', (v) => {
    expect(con({ pasadaDiariaHora: v, pasadaDiariaActiva: false })).toEqual([]);
    expect(con({ pasadaDiariaHora: v, pasadaDiariaActiva: true })).toEqual([]);
  });
});
