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
const DIR_ENGINE = resolve(__dirname, '../../../../backend/src/main/java/com/yerbanalytics/backend/engine');
const DIR_PARAMETROS = resolve(DIR_ENGINE, 'parametros');
const DIR_REGLAS = resolve(DIR_ENGINE, 'rules');

interface EntradaEnum {
  /** `Clase.CONSTANTE` en el código Java (p. ej. `ParametrosRiego.UMBRAL_HUMEDAD`): lo que usan las reglas. */
  constante: string;
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
    /(\w+)\(\s*"([^"]+)",\s*"(?:[^"\\]|\\.)*",\s*"(?:[^"\\]|\\.)*",\s*TipoParametro\.(\w+),\s*"([^"]*)",\s*"([^"]*)",\s*(null|[\d.]+),\s*(null|[\d.]+),\s*(\d+),\s*"(?:[^"\\]|\\.)*"\)/g;
  const out: EntradaEnum[] = [];
  for (const f of readdirSync(DIR_PARAMETROS).filter((n) => /^Parametros\w+\.java$/.test(n))) {
    const src = readFileSync(resolve(DIR_PARAMETROS, f), 'utf8');
    for (const m of src.matchAll(re)) {
      out.push({
        constante: `${f.replace('.java', '')}.${m[1]}`,
        clave: m[2],
        tipo: m[3],
        unidad: m[4],
        fabrica: m[5],
        min: m[6] === 'null' ? null : Number(m[6]),
        max: m[7] === 'null' ? null : Number(m[7]),
        decimales: Number(m[8]),
      });
    }
  }
  return out;
}

/**
 * Mientras el backend esté sumando parámetros (cambio de riego v2) que el fixture todavía no
 * incorpora, la comparación se limita a los parámetros que el fixture ya tiene: éstos sí tienen que
 * coincidir exactamente. Los que existen sólo en el backend se informan sin romper la suite; el bloque
 * de frontend de ese cambio actualiza el fixture y entonces corresponde volver a exigir igualdad total.
 */
describe.skipIf(!existsSync(DIR_PARAMETROS))('catalogoReglas.fixture.json · contra los enums del backend', () => {
  const backend = leerEnumsDelBackend();
  const sinConstante = ({ constante: _c, ...resto }: EntradaEnum) => resto;
  const porClave = (a: { clave: string }, b: { clave: string }) => a.clave.localeCompare(b.clave);

  it('lee los enums del backend', () => {
    expect(backend.length).toBeGreaterThan(0);
  });

  it('cada parámetro del fixture existe en el backend con la misma definición y fábrica', () => {
    const delFixture = catalogo.parametros.map((p) => ({
      clave: p.clave,
      tipo: p.tipo,
      unidad: p.unidad,
      fabrica: p.fabrica,
      min: p.min,
      max: p.max,
      decimales: p.decimales,
    }));
    const claves = new Set(delFixture.map((p) => p.clave));
    const delBackend = backend.filter((e) => claves.has(e.clave)).map(sinConstante);
    expect(delFixture.sort(porClave)).toEqual(delBackend.sort(porClave));
  });

  it('informa (sin fallar) los parámetros que el backend declara y el fixture todavía no', () => {
    const claves = new Set(catalogo.parametros.map((p) => p.clave));
    const soloBackend = backend.map((e) => e.clave).filter((c) => !claves.has(c));
    if (soloBackend.length > 0) {
      console.warn(`[anti-drift] ${soloBackend.length} parámetros sólo en el backend: ${soloBackend.sort().join(', ')}`);
    }
    expect(Array.isArray(soloBackend)).toBe(true);
  });
});

/**
 * Qué parámetros declara cada regla (`parametros()`), leído del código Java de `engine/rules/`.
 * De ahí salen `reglas[].parametros` y, por inversión, `usadoPor` del fixture.
 */
export function parametrosPorRegla(
  fuentes: Record<string, string>,
  constantes: ReadonlyMap<string, string>,
): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const [ruleId, src] of Object.entries(fuentes)) {
    const m = /List<DefinicionParametro>\s+parametros\(\)\s*\{\s*return\s+List\.of\(([^;]*)\);/.exec(src);
    const refs = m ? [...m[1].matchAll(/(Parametros\w+\.\w+)/g)].map((r) => r[1]) : [];
    out[ruleId] = refs.map((r) => constantes.get(r) ?? `<constante desconocida ${r}>`);
  }
  return out;
}

function leerReglasDelBackend(): Record<string, string> {
  const fuentes: Record<string, string> = {};
  for (const f of readdirSync(DIR_REGLAS).filter((n) => /^\w+Rule\.java$/.test(n))) {
    fuentes[f.replace('.java', '')] = readFileSync(resolve(DIR_REGLAS, f), 'utf8');
  }
  return fuentes;
}

describe('parametrosPorRegla (el lector del anti-drift)', () => {
  const constantes = new Map([
    ['ParametrosRiego.A', 'riego.a'],
    ['ParametrosRiego.B', 'riego.b'],
  ]);

  it('lee los parámetros declarados, en orden, incluso repartidos en varias líneas', () => {
    const src = `
      @Override
      public List<DefinicionParametro> parametros() {
          return List.of(ParametrosRiego.A,
                  ParametrosRiego.B);
      }`;
    expect(parametrosPorRegla({ XRule: src }, constantes)).toEqual({ XRule: ['riego.a', 'riego.b'] });
  });

  it('una regla que no declara parametros() (o devuelve List.of()) no tiene parámetros', () => {
    expect(parametrosPorRegla({ ARule: 'class ARule {}' }, constantes)).toEqual({ ARule: [] });
    const vacia = 'List<DefinicionParametro> parametros() { return List.of(); }';
    expect(parametrosPorRegla({ BRule: vacia }, constantes)).toEqual({ BRule: [] });
  });

  it('una constante que no está en los enums queda marcada, para que el test falle', () => {
    const src = 'List<DefinicionParametro> parametros() { return List.of(ParametrosRiego.Z); }';
    expect(parametrosPorRegla({ XRule: src }, constantes).XRule[0]).toMatch(/desconocida/);
  });
});

describe.skipIf(!existsSync(DIR_PARAMETROS) || !existsSync(DIR_REGLAS))(
  'catalogoReglas.fixture.json · reglas y usadoPor contra el código del backend',
  () => {
    const constantes = new Map(leerEnumsDelBackend().map((e) => [e.constante, e.clave]));
    const delBackend = parametrosPorRegla(leerReglasDelBackend(), constantes);
    // Sólo las reglas que el fixture conoce: una regla nueva del backend la agrega el cambio que la trae.
    const reglasDelFixture = catalogo.reglas.filter((r) => r.id in delBackend);

    it('el backend declara todas las reglas del fixture', () => {
      expect(reglasDelFixture.map((r) => r.id)).toEqual(catalogo.reglas.map((r) => r.id));
    });

    it('cada regla declara los mismos parámetros que el fixture (reglas[].parametros)', () => {
      for (const r of reglasDelFixture) {
        expect({ regla: r.id, parametros: [...r.parametros].sort() }).toEqual({
          regla: r.id,
          parametros: [...delBackend[r.id]].sort(),
        });
      }
    });

    it('usadoPor de cada parámetro es el índice inverso de lo que declaran las reglas del backend', () => {
      for (const p of catalogo.parametros) {
        const esperado = reglasDelFixture.filter((r) => delBackend[r.id].includes(p.clave)).map((r) => r.id);
        expect({ clave: p.clave, usadoPor: [...p.usadoPor].sort() }).toEqual({
          clave: p.clave,
          usadoPor: esperado.sort(),
        });
      }
    });
  },
);
