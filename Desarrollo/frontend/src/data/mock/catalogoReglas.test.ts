import { describe, expect, it } from 'vitest';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import fixture from './catalogoReglas.fixture.json';
import { parametroError } from '@/lib/parametrosValidation';
import type { CatalogoReglas } from '@/types/domain';

const catalogo = fixture as CatalogoReglas;

describe('catalogoReglas.fixture.json · consistencia interna', () => {
  it('cada clave aparece una sola vez', () => {
    const claves = catalogo.parametros.map((p) => p.clave);
    expect(new Set(claves).size).toBe(claves.length);
  });

  it('toda regla referencia claves que existen', () => {
    const claves = new Set(catalogo.parametros.map((p) => p.clave));
    for (const r of catalogo.reglas) for (const c of r.parametros) expect(claves.has(c)).toBe(true);
  });

  it('usadoPor es exactamente el índice inverso de reglas[].parametros', () => {
    for (const p of catalogo.parametros) {
      const esperado = catalogo.reglas.filter((r) => r.parametros.includes(p.clave)).map((r) => r.id);
      expect([...p.usadoPor].sort()).toEqual([...esperado].sort());
    }
  });

  it('el valor de fábrica es válido para su propia definición y no está modificado', () => {
    for (const p of catalogo.parametros) {
      expect(parametroError(p, p.fabrica)).toBeNull();
      expect(p.valor).toBe(p.fabrica);
      expect(p.modificado).toBe(false);
    }
  });

  it('las reglas vienen ordenadas por prioridad', () => {
    const prioridades = catalogo.reglas.map((r) => r.prioridad);
    expect(prioridades).toEqual([...prioridades].sort((a, b) => a - b));
  });
});

/**
 * Anti-drift contra el backend (design D8 / Risks). El fixture se escribió a mano fiel a los
 * enums `engine/parametros/Parametros*.java`; este test los lee y falla si divergen. Se saltea
 * si el árbol del backend no está (p. ej. un clon que sólo trae el frontend).
 */
const DIR_PARAMETROS = resolve(
  __dirname,
  '../../../../backend/src/main/java/com/yerbanalytics/backend/engine/parametros',
);

interface EntradaEnum {
  clave: string;
  tipo: string;
  unidad: string;
  fabrica: string;
  min: number | null;
  max: number | null;
  decimales: number;
}

function leerEnumsDelBackend(): EntradaEnum[] {
  const re =
    /\w+\(\s*"([^"]+)",\s*"(?:[^"\\]|\\.)*",\s*"(?:[^"\\]|\\.)*",\s*TipoParametro\.(\w+),\s*"([^"]*)",\s*"([^"]*)",\s*(null|[\d.]+),\s*(null|[\d.]+),\s*(\d+),\s*"(?:[^"\\]|\\.)*"\)/g;
  const out: EntradaEnum[] = [];
  for (const f of readdirSync(DIR_PARAMETROS).filter((n) => /^Parametros\w+\.java$/.test(n))) {
    const src = readFileSync(resolve(DIR_PARAMETROS, f), 'utf8');
    for (const m of src.matchAll(re)) {
      out.push({
        clave: m[1],
        tipo: m[2],
        unidad: m[3],
        fabrica: m[4],
        min: m[5] === 'null' ? null : Number(m[5]),
        max: m[6] === 'null' ? null : Number(m[6]),
        decimales: Number(m[7]),
      });
    }
  }
  return out;
}

describe.skipIf(!existsSync(DIR_PARAMETROS))('catalogoReglas.fixture.json · contra los enums del backend', () => {
  it('tiene los mismos parámetros, con la misma definición y fábrica', () => {
    const backend = leerEnumsDelBackend();
    expect(backend.length).toBeGreaterThan(0);

    const delFixture = catalogo.parametros.map((p) => ({
      clave: p.clave,
      tipo: p.tipo,
      unidad: p.unidad,
      fabrica: p.fabrica,
      min: p.min,
      max: p.max,
      decimales: p.decimales,
    }));
    const porClave = (a: EntradaEnum, b: EntradaEnum) => a.clave.localeCompare(b.clave);
    expect(delFixture.sort(porClave)).toEqual(backend.sort(porClave));
  });
});
