import { describe, expect, it } from 'vitest';
import {
  DEFAULT_MACRO_ZONAS_POR_FILA,
  DEFAULT_SECTORES_POR_FILA,
  disposicionError,
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
  it('resume una grilla N × M con la disposición por defecto', () => {
    expect(topologiaSummary(10, 100)).toEqual({
      macroZonas: 10,
      sectoresPorMacroZona: 100,
      totalSectores: 1000,
      generada: true,
      macroZonasPorFila: DEFAULT_MACRO_ZONAS_POR_FILA,
      sectoresPorFila: DEFAULT_SECTORES_POR_FILA,
    });
  });

  it('incluye la disposición indicada, acotada a las cantidades', () => {
    const t = topologiaSummary(4, 8, { macroZonasPorFila: 2, sectoresPorFila: 4 });
    expect(t.macroZonasPorFila).toBe(2);
    expect(t.sectoresPorFila).toBe(4);
    // se acota si excede las cantidades
    const clamped = topologiaSummary(4, 8, { macroZonasPorFila: 9, sectoresPorFila: 99 });
    expect(clamped.macroZonasPorFila).toBe(4);
    expect(clamped.sectoresPorFila).toBe(8);
  });

  it('valida la disposición contra la grilla actual', () => {
    expect(disposicionError({ macroZonasPorFila: 3, sectoresPorFila: 10 }, 6, 100)).toBeNull();
    expect(disposicionError({ macroZonasPorFila: 0, sectoresPorFila: 10 }, 6, 100)).not.toBeNull();
    expect(disposicionError({ macroZonasPorFila: 7, sectoresPorFila: 10 }, 6, 100)).not.toBeNull();
    expect(disposicionError({ macroZonasPorFila: 3, sectoresPorFila: 101 }, 6, 100)).not.toBeNull();
    expect(disposicionError({ macroZonasPorFila: 1.5, sectoresPorFila: 10 }, 6, 100)).not.toBeNull();
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

  it('expone la disposición en el snapshot del vivero', async () => {
    const repo = new MockRepository(SEED);
    const nursery = await repo.getNursery();
    expect(nursery.layout).toEqual({
      macroZonasPorFila: DEFAULT_MACRO_ZONAS_POR_FILA,
      sectoresPorFila: DEFAULT_SECTORES_POR_FILA,
    });
  });

  it('guarda la disposición sin regenerar la grilla ni descartar la flota', async () => {
    const repo = new MockRepository(SEED);
    const hwAntes = await repo.getHardware();

    const t = await repo.guardarDisposicion({ macroZonasPorFila: 2, sectoresPorFila: 5 });
    expect(t.macroZonasPorFila).toBe(2);
    expect(t.sectoresPorFila).toBe(5);
    // la grilla no cambió
    expect(t.macroZonas).toBe(6);
    expect(t.totalSectores).toBe(600);

    const nursery = await repo.getNursery();
    expect(nursery.layout).toEqual({ macroZonasPorFila: 2, sectoresPorFila: 5 });
    expect(nursery.sectors).toHaveLength(600);
    // la flota queda intacta
    const hwDespues = await repo.getHardware();
    expect(hwDespues.total).toBe(hwAntes.total);
  });

  it('rechaza una disposición fuera del rango de la grilla', async () => {
    const repo = new MockRepository(SEED);
    await expect(
      repo.guardarDisposicion({ macroZonasPorFila: 99, sectoresPorFila: 5 }),
    ).rejects.toThrow();
  });
});
