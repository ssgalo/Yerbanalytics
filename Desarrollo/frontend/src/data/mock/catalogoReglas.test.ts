import { describe, expect, it } from 'vitest';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import fixture from './catalogoReglas.fixture.json';
import { parametroError } from '@/lib/parametrosValidation';
import type { CatalogoReglas, ParametroRegla } from '@/types/domain';

const catalogo = fixture as CatalogoReglas;

describe('catalogoReglas.fixture.json · consistencia interna', () => {
  it('cada copia (regla, clave) aparece una sola vez', () => {
    const copias = catalogo.parametros.map((p) => `${p.regla}|${p.clave}`);
    expect(new Set(copias).size).toBe(copias.length);
  });

  it('las copias de una clave comparten la definición (tipo, rango, unidad, fábrica)', () => {
    const def = ({ regla: _r, valor: _v, modificado: _m, updatedBy: _b, updatedTs: _t, ...resto }: ParametroRegla) =>
      resto;
    const primera = new Map<string, ParametroRegla>();
    for (const p of catalogo.parametros) {
      const otra = primera.get(p.clave);
      if (otra) expect(def(p)).toEqual(def(otra));
      else primera.set(p.clave, p);
    }
  });

  it('las copias de cada pieza son exactamente las claves que declara, en su orden', () => {
    for (const r of catalogo.reglas) {
      expect(catalogo.parametros.filter((p) => p.regla === r.id).map((p) => p.clave)).toEqual(r.parametros);
    }
  });

  it('las demás copias son de servicios de ejecución (sin interruptor); el despacho sólo tiene la suya', () => {
    const ids = new Set(catalogo.reglas.map((r) => r.id));
    const ejecucion = [...new Set(catalogo.parametros.map((p) => p.regla).filter((id) => !ids.has(id)))];
    expect(ejecucion.sort()).toEqual(['ApplicationService', 'DespachoRiego', 'PulsoSensado', 'ReadingPurge', 'TankService']);
    expect(catalogo.parametros.filter((p) => p.regla === 'DespachoRiego').map((p) => p.clave)).toEqual([
      'riego.sectores-simultaneos',
    ]);
  });

  it('el umbral de riego tiene una copia en R-01, R-03, R-05, R-06 y la alerta de falla hidráulica, y no hay claves de las reglas viejas', () => {
    const copias = catalogo.parametros.filter((x) => x.clave === 'riego.umbral-humedad');
    expect(copias.map((p) => p.regla).sort()).toEqual([
      'FueraDeVentanaRiegoRule',
      'HardwareFaultService',
      'PausaTrasAplicacionRule',
      'PosponerPorLluviaRule',
      'RiegoPorDeficitRule',
    ]);
    const umbral = copias[0];
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

  it('la confianza mínima del diagnóstico es 70 % de fábrica y se edita entre 50 y 95 (reglas_v2 §11)', () => {
    const p = catalogo.parametros.find((x) => x.clave === 'diagnostico.confianza-minima');
    expect(p).toMatchObject({ fabrica: '70', min: 50, max: 95 });
    expect(parametroError(p!, '97')).not.toBeNull();
    expect(parametroError(p!, '95')).toBeNull();
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
/** Las reglas de macro-zona (nutrición, bióticas, clorosis, estrés solar, mediasombra) viven con su feature, no con las de sector. */
const DIRS_REGLAS_DE_FEATURE = ['nutrition', 'biotic', 'chlorosis', 'solarstress', 'mediasombra'].map((dir) =>
  resolve(DIR_ENGINE, '..', dir),
);

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
    /(\w+)\(\s*"([^"]+)",\s*"(?:[^"\\]|\\.)*",\s*"(?:[^"\\]|\\.)*",\s*TipoParametro\.(\w+),\s*"([^"]*)",\s*"([^"]*)",\s*(null|-?[\d.]+),\s*(null|-?[\d.]+),\s*(\d+),\s*"(?:[^"\\]|\\.)*"\)/g;
  const out: EntradaEnum[] = [];
  for (const f of readdirSync(DIR_PARAMETROS).filter((n) => n.endsWith('.java'))) {
    const src = readFileSync(resolve(DIR_PARAMETROS, f), 'utf8');
    if (!/enum \w+ implements DefinicionParametro/.test(src)) continue;
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
    const unaPorClave = [...new Map(catalogo.parametros.map((p) => [p.clave, p])).values()];
    const delFixture = unaPorClave.map((p) => ({
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
 * De ahí salen `reglas[].parametros` y las copias `(regla, clave)` del fixture.
 */
export function parametrosPorRegla(
  fuentes: Record<string, string>,
  constantes: ReadonlyMap<string, string>,
): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const [ruleId, src] of Object.entries(fuentes)) {
    const m = /List<DefinicionParametro>\s+parametros\(\)\s*\{\s*return\s+List\.of\(([^;]*)\);/.exec(src);
    const refs = m ? [...m[1].matchAll(/([A-Z]\w*\.[A-Z][A-Z0-9_]*)/g)].map((r) => r[1]) : [];
    out[ruleId] = refs.map((r) => constantes.get(r) ?? `<constante desconocida ${r}>`);
  }
  return out;
}

function leerReglasDelBackend(): Record<string, string> {
  const fuentes: Record<string, string> = {};
  for (const dir of [DIR_REGLAS, ...DIRS_REGLAS_DE_FEATURE]) {
    for (const f of readdirSync(dir).filter((n) => /^\w+Rule\.java$/.test(n))) {
      fuentes[f.replace('.java', '')] = readFileSync(resolve(dir, f), 'utf8');
    }
  }
  return fuentes;
}

/**
 * Consumidores de parámetros que no son reglas (el despacho de riego, la purga del historial, el pulso de sensado, el
 * escalamiento del nodo, el guard de lectura inválida, el servicio de fallas del hardware, los de aplicación de insumos y el plan de nutrición): también
 * tienen sus copias. Cada uno declara sus parámetros en un `parametros()` igual al de una regla.
 */
const CONSUMIDORES_QUE_NO_SON_REGLA: Record<string, string> = {
  DespachoRiego: resolve(DIR_ENGINE, 'riego', 'DespachoRiego.java'),
  ReadingPurge: resolve(DIR_ENGINE, '..', 'readings', 'ReadingPurge.java'),
  PulsoSensado: resolve(DIR_ENGINE, '..', 'sensing', 'PulsoSensadoService.java'),
  EscalamientoNodo: resolve(DIR_ENGINE, '..', 'service', 'EscalamientoNodoService.java'),
  ReadingValidationGuard: resolve(DIR_ENGINE, '..', 'readings', 'ReadingValidationGuard.java'),
  HardwareFaultService: resolve(DIR_ENGINE, '..', 'service', 'HardwareFaultService.java'),
  ApplicationPreconditions: resolve(DIR_ENGINE, '..', 'supply', 'ApplicationPreconditions.java'),
  ApplicationService: resolve(DIR_ENGINE, '..', 'supply', 'ApplicationService.java'),
  TankService: resolve(DIR_ENGINE, '..', 'supply', 'TankService.java'),
  NutritionPlanService: resolve(DIR_ENGINE, '..', 'nutrition', 'NutritionPlanService.java'),
  EffectivenessService: resolve(DIR_ENGINE, '..', 'effectiveness', 'EffectivenessService.java'),
  HorarioMediasombraService: resolve(DIR_ENGINE, '..', 'mediasombra', 'HorarioMediasombraService.java'),
};

/**
 * Claves que un servicio lee de la copia de la regla que decidió (design D-2, `parametrosDe()`): no son copias suyas.
 */
const LEIDAS_DE_LA_DUENA: Record<string, string[]> = {
  DespachoRiego: [
    'riego.lluvia-probabilidad',
    'riego.lluvia-mm',
    'riego.lluvia-ventana',
    'riego.saturacion-bloqueo',
    'riego.ventana-normal',
    'riego.pausa-tras-aplicacion',
    'riego.exceptuado-bloqueo',
    'seguridad.antiguedad-max-lectura',
  ],
  NutritionPlanService: ['nutricion.intervalo-plan'],
};

/**
 * Servicios que deciden y el catálogo lista junto a las reglas (son `DemoControllable`, con interruptor), pero que no
 * son una `Rule` ni una `ZoneRule`: no están en la cadena del DAG. Su prioridad sólo ordena el listado.
 */
const SERVICIOS_CON_INTERRUPTOR = [
  'EscalamientoNodo',
  'ReadingValidationGuard',
  'HardwareFaultService',
  'HorarioMediasombraService',
  'ApplicationPreconditions',
  'EffectivenessService',
];

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

describe.skipIf(!existsSync(DIR_PARAMETROS) || !existsSync(DIR_REGLAS) || !DIRS_REGLAS_DE_FEATURE.every(existsSync))(
  'catalogoReglas.fixture.json · reglas y copias contra el código del backend',
  () => {
    const constantes = new Map(leerEnumsDelBackend().map((e) => [e.constante, e.clave]));
    const fuentes = leerReglasDelBackend();
    const delBackend = parametrosPorRegla(fuentes, constantes);
    const fuentesNoRegla = Object.fromEntries(
      Object.entries(CONSUMIDORES_QUE_NO_SON_REGLA)
        .filter(([, archivo]) => existsSync(archivo))
        .map(([id, archivo]) => [id, readFileSync(archivo, 'utf8')]),
    );
    const noRegla = parametrosPorRegla(fuentesNoRegla, constantes);

    it('el fixture tiene exactamente las reglas del backend, con su prioridad, más los servicios con interruptor', () => {
      const prioridades = Object.fromEntries(
        Object.entries(fuentes).map(([id, src]) => [
          id,
          Number(/PRIORITY\s*=\s*(\d+)/.exec(src)?.[1] ?? /int priority\(\)\s*\{\s*return\s*(\d+)/.exec(src)?.[1]),
        ]),
      );
      const delFixture = Object.fromEntries(
        catalogo.reglas.filter((r) => !SERVICIOS_CON_INTERRUPTOR.includes(r.id)).map((r) => [r.id, r.prioridad]),
      );
      expect(delFixture).toEqual(prioridades);
      expect(catalogo.reglas.map((r) => r.id)).toEqual(expect.arrayContaining(SERVICIOS_CON_INTERRUPTOR));
    });

    it('cada pieza declara los mismos parámetros que el fixture (reglas[].parametros)', () => {
      for (const r of catalogo.reglas) {
        expect({ regla: r.id, parametros: [...r.parametros].sort() }).toEqual({
          regla: r.id,
          parametros: [...(delBackend[r.id] ?? noRegla[r.id] ?? [])].sort(),
        });
      }
    });

    it('hay una copia por (pieza, clave) que declara el backend, menos las que un servicio lee de la regla dueña', () => {
      const esperado: string[] = [];
      for (const r of catalogo.reglas) {
        for (const c of delBackend[r.id] ?? noRegla[r.id] ?? []) esperado.push(`${r.id}|${c}`);
      }
      for (const [id, claves] of Object.entries(noRegla)) {
        if (catalogo.reglas.some((r) => r.id === id)) continue;
        for (const c of claves) if (!(LEIDAS_DE_LA_DUENA[id] ?? []).includes(c)) esperado.push(`${id}|${c}`);
      }
      expect(catalogo.parametros.map((p) => `${p.regla}|${p.clave}`).sort()).toEqual(esperado.sort());
    });
  },
);
