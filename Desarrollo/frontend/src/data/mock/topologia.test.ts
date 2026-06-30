import { describe, expect, it } from 'vitest';
import {
  MAX_MACRO_ZONAS,
  MAX_SECTORES_POR_ZONA,
  topologiaError,
  topologiaSummary,
  zonaDefsFor,
} from './topologia';
import { MockRepository } from './mockRepository';
import type { NuevaTopologia } from '@/types/domain';

const SEED = 20260613;

describe('topología — helpers (HU-18 CA-01)', () => {
  it('resume una grilla N × M', () => {
    expect(topologiaSummary(10, 100)).toEqual({
      macroZonas: 10,
      sectoresPorMacroZona: 100,
      totalSectores: 1000,
      generada: true,
    });
  });

  it('genera N macro-zonas con ids, nombres y subs sintetizados', () => {
    const defs = zonaDefsFor(3);
    expect(defs.map((z) => z.id)).toEqual(['MZ-1', 'MZ-2', 'MZ-3']);
    expect(defs[0]).toEqual({ id: 'MZ-1', name: 'Macro-zona 1', sub: 'Sector norte' });
    expect(defs[2].sub).toBe('Sector sur');
  });

  it('rechaza rangos fuera de los límites operativos', () => {
    expect(topologiaError(false, { macroZonas: 0, sectoresPorMacroZona: 100 })).not.toBeNull();
    expect(
      topologiaError(false, { macroZonas: MAX_MACRO_ZONAS + 1, sectoresPorMacroZona: 100 }),
    ).not.toBeNull();
    expect(topologiaError(false, { macroZonas: 6, sectoresPorMacroZona: 0 })).not.toBeNull();
    expect(
      topologiaError(false, { macroZonas: 6, sectoresPorMacroZona: MAX_SECTORES_POR_ZONA + 1 }),
    ).not.toBeNull();
    expect(topologiaError(false, { macroZonas: 1.5, sectoresPorMacroZona: 100 })).not.toBeNull();
  });

  it('exige confirmación para regenerar una topología ya cargada', () => {
    const input: NuevaTopologia = { macroZonas: 3, sectoresPorMacroZona: 4 };
    expect(topologiaError(true, input)).not.toBeNull(); // sin regenerar → conflicto
    expect(topologiaError(true, { ...input, regenerar: true })).toBeNull();
    expect(topologiaError(false, input)).toBeNull(); // vivero vacío → genera
  });
});

describe('topología — MockRepository (HU-18 CA-01)', () => {
  it('parte del seed demo 6 × 100', async () => {
    const repo = new MockRepository(SEED);
    const t = await repo.getTopologia();
    expect(t.macroZonas).toBe(6);
    expect(t.sectoresPorMacroZona).toBe(100);
    expect(t.totalSectores).toBe(600);
    expect(t.generada).toBe(true);
  });

  it('rechaza generar sobre el seed sin confirmar la regeneración', async () => {
    const repo = new MockRepository(SEED);
    await expect(repo.generarTopologia({ macroZonas: 3, sectoresPorMacroZona: 4 })).rejects.toThrow();
  });

  it('regenera la grilla y la refleja en el vivero, en estado offline', async () => {
    const repo = new MockRepository(SEED);
    const t = await repo.generarTopologia({ macroZonas: 3, sectoresPorMacroZona: 4, regenerar: true });
    expect(t.totalSectores).toBe(12);

    const nursery = await repo.getNursery();
    expect(nursery.zonas).toHaveLength(3);
    expect(nursery.sectors).toHaveLength(12);
    expect(nursery.stats.total).toBe(12);
    expect(nursery.zonas.map((z) => z.id)).toEqual(['MZ-1', 'MZ-2', 'MZ-3']);
    expect(nursery.sectors.every((s) => s.status === 'offline')).toBe(true);
    // ids únicos con la convención MZ-{z}-{NNN}.
    expect(nursery.byId['MZ-1-001']).toBeDefined();
    expect(nursery.byId['MZ-3-004']).toBeDefined();
    expect(new Set(nursery.sectors.map((s) => s.id)).size).toBe(12);
  });

  it('descarta la flota de hardware al regenerar', async () => {
    const repo = new MockRepository(SEED);
    await repo.generarTopologia({ macroZonas: 2, sectoresPorMacroZona: 5, regenerar: true });
    const hw = await repo.getHardware();
    expect(hw.total).toBe(0);
  });
});
