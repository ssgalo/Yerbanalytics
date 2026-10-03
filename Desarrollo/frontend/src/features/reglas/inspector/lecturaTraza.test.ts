import { describe, expect, it } from 'vitest';
import { etiquetaOrigen, formatoComparacion, resumenTraza, simboloOperador } from './lecturaTraza';
import { evaluarMotor, type EntradaMotor } from '@/data/mock/trazaReglas';
import { buildRuleSchema } from '@/data/mock/reglasMock';
import fixture from '@/data/mock/catalogoReglas.fixture.json';
import type { CatalogoReglas, Comparacion } from '@/types/domain';

const catalogo = fixture as CatalogoReglas;
const schema = buildRuleSchema();
const base: EntradaMotor = {
  sectorId: 'MZ-1-001',
  zonaId: 'MZ-1',
  origen: 'TELEMETRIA',
  ts: '2026-06-13T12:00:00.000Z',
  antiguedadSeg: 1,
  bloqueoManual: false,
  humSus: 55,
  lluviaPct: 10,
  lluviaMm: 1,
  uvIndex: 3,
  dosis24h: 0,
  estadoSector: 'ok',
  confianza: 92,
};

const cmp = (p: Partial<Comparacion>): Comparacion => ({
  etiqueta: 'Humedad de sustrato',
  clave: 'riego.umbral-humedad',
  recibido: 38,
  operador: 'LT',
  umbral: 45,
  unidad: '%',
  configurable: true,
  resultado: 'CUMPLE',
  ...p,
});

describe('simboloOperador', () => {
  it('muestra los operadores como símbolos', () => {
    expect(['LT', 'LE', 'GT', 'GE', 'EQ'].map((o) => simboloOperador(o as never))).toEqual(['<', '≤', '>', '≥', '=']);
  });
});

describe('formatoComparacion', () => {
  it('arma "recibido op umbral" con la unidad y marca si se cumplió', () => {
    expect(formatoComparacion(cmp({}))).toEqual({
      recibido: '38 %',
      operador: '<',
      umbral: '45 %',
      marca: '✓',
      resultado: 'CUMPLE',
    });
    expect(formatoComparacion(cmp({ recibido: 55, resultado: 'NO_CUMPLE' })).marca).toBe('✗');
  });

  it('con valor 1 la unidad va en singular: "1 riego" y "3 riegos"', () => {
    const f = formatoComparacion(cmp({ recibido: 1, umbral: 3, unidad: 'riegos' }));
    expect(f.recibido).toBe('1 riego');
    expect(f.umbral).toBe('3 riegos');
  });

  it('SIN_DATO se lee "sin dato" y no inventa valor', () => {
    const f = formatoComparacion(cmp({ recibido: null, resultado: 'SIN_DATO' }));
    expect(f.recibido).toBe('sin dato');
    expect(f.marca).toBe('?');
  });

  it('redondea decimales largos y deja la unidad pegada con un espacio', () => {
    expect(formatoComparacion(cmp({ recibido: 0.30000000000000004, umbral: 0.5, unidad: 'L' })).recibido).toBe('0.3 L');
    expect(formatoComparacion(cmp({ recibido: 300, umbral: 90, unidad: 's' })).umbral).toBe('90 s');
  });

  it('las condiciones fijas aceptan texto y booleanos, sin unidad', () => {
    const texto = formatoComparacion(
      cmp({ recibido: 'warning', umbral: 'critical', unidad: '', operador: 'EQ', clave: null, configurable: false }),
    );
    expect([texto.recibido, texto.operador, texto.umbral]).toEqual(['warning', '=', 'critical']);

    const bool = formatoComparacion(cmp({ recibido: false, umbral: true, unidad: '', operador: 'EQ', clave: null }));
    expect([bool.recibido, bool.umbral]).toEqual(['no', 'sí']);
  });
});

describe('formatoComparacion · ventana horaria (operador EN)', () => {
  it('"17:42 ∈ 06:00-18:00 ✓": la hora local contra la ventana, sin unidad', () => {
    const f = formatoComparacion(
      cmp({
        etiqueta: 'Hora local',
        clave: 'riego.ventana-normal',
        recibido: '17:42',
        operador: 'EN',
        umbral: '06:00-18:00',
        unidad: '',
        resultado: 'CUMPLE',
      }),
    );

    expect([f.recibido, f.operador, f.umbral, f.marca]).toEqual(['17:42', '∈', '06:00-18:00', '✓']);
  });

  it('simboloOperador conoce a todos los operadores', () => {
    expect(simboloOperador('EN')).toBe('∈');
    expect(simboloOperador('GE')).toBe('≥');
  });
});

describe('etiquetaOrigen', () => {
  it('traduce el origen', () => {
    expect(etiquetaOrigen('TELEMETRIA')).toBe('Telemetría');
    expect(etiquetaOrigen('BARRIDO')).toBe('Barrido');
  });
});

describe('resumenTraza', () => {
  const resumen = (e: Partial<EntradaMotor>) => resumenTraza(schema, evaluarMotor(catalogo, { ...base, ...e }));

  it('sin acciones lo dice', () => {
    const r = resumen({});
    expect(r).toEqual([{ tipo: 'nada', texto: 'Ninguna regla actuó ni bloqueó: no hubo nada que hacer.' }]);
  });

  it('cuenta qué accionó', () => {
    const r = resumen({ humSus: 38 });
    expect(r).toContainEqual(
      expect.objectContaining({ tipo: 'accion', ruleId: 'RiegoPorDeficitRule', texto: expect.stringMatching(/electroválvula/) }),
    );
  });

  it('dice qué se pospuso y qué se omitió por culpa de quién', () => {
    const r = resumen({ humSus: 40, lluviaPct: 80, lluviaMm: 8 });
    expect(r).toContainEqual(
      expect.objectContaining({ tipo: 'pospuso', ruleId: 'PosponerPorLluviaRule', texto: expect.stringMatching(/riego/i) }),
    );
    expect(r).toContainEqual(
      expect.objectContaining({
        tipo: 'omitida',
        texto: expect.stringMatching(/Riego por déficit hídrico \(R-01\) quedó omitida por .*Posponer por lluvia/),
      }),
    );
  });

  it('una alerta sale en el resumen, junto a lo que accionó o pospuso', () => {
    const critico = resumen({ humSus: 30 });
    expect(critico).toContainEqual(
      expect.objectContaining({ tipo: 'accion', ruleId: 'DeficitCriticoRule', texto: expect.stringMatching(/electroválvula/) }),
    );
    expect(critico).toContainEqual(
      expect.objectContaining({ tipo: 'alerta', ruleId: 'DeficitCriticoRule', texto: expect.stringMatching(/emitió una alerta/) }),
    );

    const lluvia = resumen({ humSus: 40, lluviaPct: 80, lluviaMm: 8 });
    expect(lluvia.filter((l) => l.tipo === 'alerta')).toHaveLength(1);
    expect(lluvia.filter((l) => l.tipo === 'pospuso')).toHaveLength(1);
  });

  it('un bloqueo dice qué rama cortó y quién', () => {
    const r = resumen({ antiguedadSeg: 300 });
    expect(r).toContainEqual(
      expect.objectContaining({
        tipo: 'bloqueo',
        ruleId: 'StaleSensorRule',
        texto: expect.stringMatching(/Sensor sin datos recientes.*riego/i),
      }),
    );
  });

  it('el bloqueo total aclara que no se evaluó el resto', () => {
    const r = resumen({ bloqueoManual: true });
    expect(r).toContainEqual(expect.objectContaining({ tipo: 'bloqueo', ruleId: 'ManualLockRule' }));
    expect(r).toContainEqual(expect.objectContaining({ tipo: 'noAlcanzada', texto: expect.stringMatching(/12 reglas/) }));
  });
});
