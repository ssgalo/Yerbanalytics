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

  it('usadoPor es el índice inverso de reglas[].parametros, más los consumidores que no son reglas', () => {
    const ids = new Set(catalogo.reglas.map((r) => r.id));
    for (const p of catalogo.parametros) {
      const esperado = catalogo.reglas.filter((r) => r.parametros.includes(p.clave)).map((r) => r.id);
      expect(p.usadoPor.filter((u) => ids.has(u)).sort()).toEqual([...esperado].sort());
    }
  });

  it('riego.sectores-simultaneos lo usa el despacho (que no es una regla) y ninguna regla', () => {
    const p = catalogo.parametros.find((x) => x.clave === 'riego.sectores-simultaneos');
    expect(p?.usadoPor).toEqual(['DespachoRiego']);
  });

  it('el umbral de riego es compartido por R-01, R-03, R-05 y R-06, y no hay claves de las reglas viejas', () => {
    const umbral = catalogo.parametros.find((x) => x.clave === 'riego.umbral-humedad');
    expect(umbral?.usadoPor).toEqual([
      'FueraDeVentanaRiegoRule',
      'PausaTrasAplicacionRule',
      'PosponerPorLluviaRule',
      'RiegoPorDeficitRule',
    ]);
    expect(umbral?.fabrica).toBe('45');
    expect(catalogo.parametros.find((x) => x.clave === 'riego.lluvia-probabilidad')?.fabrica).toBe('70');
    const claves = catalogo.parametros.map((x) => x.clave);
    for (const vieja of ['riego.tiempo-max-apertura', 'riego.max-riegos-24h', 'riego.max-riegos-24h-sector']) {
      expect(claves).not.toContain(vieja);
    }
    const reglas = catalogo.reglas.map((r) => r.id);
    for (const vieja of ['IrrigationRule', 'WeatherOverrideRule', 'DailyVolumeLimitRule']) {
      expect(reglas).not.toContain(vieja);
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
 * Igualdad TOTAL: el fixture tiene exactamente los parámetros del backend (ni uno de más ni uno de
 * menos), con la misma definición y fábrica. Si el backend suma, saca o cambia uno, este test obliga a
 * regenerar el fixture.
 */
describe.skipIf(!existsSync(DIR_PARAMETROS))('catalogoReglas.fixture.json · contra los enums del backend', () => {
  const backend = leerEnumsDelBackend();
  const sinConstante = ({ constante: _c, ...resto }: EntradaEnum) => resto;
  const porClave = (a: { clave: string }, b: { clave: string }) => a.clave.localeCompare(b.clave);

  it('lee los enums del backend', () => {
    expect(backend.length).toBeGreaterThan(0);
  });

  it('el fixture tiene exactamente los parámetros del backend, con la misma definición y fábrica', () => {
    const delFixture = catalogo.parametros.map((p) => ({
      clave: p.clave,
      tipo: p.tipo,
      unidad: p.unidad,
      fabrica: p.fabrica,
      min: p.min,
      max: p.max,
      decimales: p.decimales,
    }));
    expect(delFixture.sort(porClave)).toEqual(backend.map(sinConstante).sort(porClave));
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

/** Consumidores de parámetros que no son reglas (hoy sólo el despacho de riego): también entran en `usadoPor`. */
const ARCHIVO_DESPACHO = resolve(DIR_ENGINE, 'riego', 'DespachoRiego.java');

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
    const fuentes = leerReglasDelBackend();
    const delBackend = parametrosPorRegla(fuentes, constantes);
    const despacho = existsSync(ARCHIVO_DESPACHO)
      ? parametrosPorRegla({ DespachoRiego: readFileSync(ARCHIVO_DESPACHO, 'utf8') }, constantes).DespachoRiego
      : [];

    it('el fixture tiene exactamente las reglas del backend, con su prioridad', () => {
      const prioridades = Object.fromEntries(
        Object.entries(fuentes).map(([id, src]) => [id, Number(/PRIORITY\s*=\s*(\d+)/.exec(src)?.[1])]),
      );
      expect(Object.fromEntries(catalogo.reglas.map((r) => [r.id, r.prioridad]))).toEqual(prioridades);
    });

    it('cada regla declara los mismos parámetros que el fixture (reglas[].parametros)', () => {
      for (const r of catalogo.reglas) {
        expect({ regla: r.id, parametros: [...r.parametros].sort() }).toEqual({
          regla: r.id,
          parametros: [...delBackend[r.id]].sort(),
        });
      }
    });

    it('usadoPor de cada parámetro es el índice inverso de lo que declaran las reglas (y el despacho) del backend', () => {
      for (const p of catalogo.parametros) {
        const esperado = catalogo.reglas.filter((r) => delBackend[r.id].includes(p.clave)).map((r) => r.id);
        if (despacho.includes(p.clave)) esperado.push('DespachoRiego');
        expect({ clave: p.clave, usadoPor: [...p.usadoPor].sort() }).toEqual({
          clave: p.clave,
          usadoPor: esperado.sort(),
        });
      }
    });
  },
);
