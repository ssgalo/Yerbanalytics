import { describe, expect, it } from 'vitest';
import { buildHistory } from './history';

const SEED = 20260613;

describe('buildHistory (historial determinístico)', () => {
  it('es determinístico: misma semilla → mismos ids y sectores', () => {
    const a = buildHistory(SEED);
    const b = buildHistory(SEED);
    expect(a.map((r) => [r.id, r.sectorId, r.tipo])).toEqual(
      b.map((r) => [r.id, r.sectorId, r.tipo]),
    );
  });

  it('genera registros ordenados del más reciente al más antiguo', () => {
    const recs = buildHistory(SEED);
    expect(recs.length).toBeGreaterThan(0);
    for (let i = 1; i < recs.length; i++) {
      expect(recs[i - 1].ts).toBeGreaterThanOrEqual(recs[i].ts);
    }
  });

  it('cada acción tiene su cadena de justificación completa', () => {
    buildHistory(SEED).forEach((r) => {
      expect(r.lectura).toBeTruthy();
      expect(r.decision).toBeTruthy();
      expect(r.accion).toBeTruthy();
      expect(['Riego', 'Insumo', 'Mediasombra', 'Alerta']).toContain(r.tipo);
    });
  });

  it('las acciones pospuestas o abortadas no tienen seguimiento', () => {
    buildHistory(SEED).forEach((r) => {
      if (r.res === 'Pospuesta' || r.res === 'Abortada') {
        expect(r.evo).toBeNull();
      }
    });
  });

  it('el seguimiento evaluado expone veredicto y delta coherentes', () => {
    const conEvo = buildHistory(SEED).filter((r) => r.evo && r.evo.ahora !== '—');
    expect(conEvo.length).toBeGreaterThan(0);
    conEvo.forEach((r) => {
      expect(['Efectiva', 'Sin efectividad']).toContain(r.evo!.verdict);
      expect(r.evo!.delta).not.toBe('—');
    });
  });
});

describe('buildHistory · riegos y alertas (13.5)', () => {
  it('los riegos traen regla, volumen y duración coherentes con el caudal de fábrica (30 L/h)', () => {
    const riegos = buildHistory(SEED).filter((r) => r.tipo === 'Riego' && r.volumenL != null);

    expect(riegos.length).toBeGreaterThan(0);
    riegos.forEach((r) => {
      expect(['RiegoPorDeficitRule', 'DeficitCriticoRule']).toContain(r.regla);
      expect(r.duracionSeg).toBe(Math.ceil((r.volumenL! * 3600) / 30));
      expect(r.duracionSeg).toBeLessThanOrEqual(1200);
    });
  });

  it('hay riegos por déficit crítico, pospuestos por lluvia y alertas de los tres niveles', () => {
    const recs = buildHistory(SEED);

    expect(recs.some((r) => r.regla === 'DeficitCriticoRule' && r.tipo === 'Riego')).toBe(true);
    expect(recs.some((r) => r.regla === 'PosponerPorLluviaRule' && r.res === 'Pospuesta')).toBe(true);
    expect(new Set(recs.filter((r) => r.tipo === 'Alerta').map((r) => r.alerta))).toEqual(
      new Set(['INFO', 'WARNING', 'CRITICAL']),
    );
  });

  it('las alertas son de la macro-zona: sin sector, informativas y sin seguimiento', () => {
    buildHistory(SEED)
      .filter((r) => r.tipo === 'Alerta')
      .forEach((r) => {
        expect(r.sectorId).toBe('—');
        expect(r.res).toBe('Informativo');
        expect(r.evo).toBeNull();
        expect(r.volumenL).toBeNull();
      });
  });
});
