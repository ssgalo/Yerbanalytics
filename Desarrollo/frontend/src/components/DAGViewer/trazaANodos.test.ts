import { describe, expect, it } from 'vitest';
import { clasificarRegla, colorArista, trazaANodos } from './trazaANodos';
import { evaluarMotor, type EntradaMotor } from '@/data/mock/trazaReglas';
import { buildRuleSchema } from '@/data/mock/reglasMock';
import fixture from '@/data/mock/catalogoReglas.fixture.json';
import type { CatalogoReglas, TrazaRegla } from '@/types/domain';

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
const estados = (e: Partial<EntradaMotor>) =>
  trazaANodos(schema, evaluarMotor(catalogo, { ...base, ...e }));

const regla = (acciones: string[], estado: TrazaRegla['estado'] = 'EVALUADA'): TrazaRegla => ({
  ruleId: 'X',
  rama: 'RIEGO',
  prioridad: 1,
  estado,
  comparaciones: [],
  acciones: acciones.map((tipo) => ({ tipo, motivo: '' })),
  bloqueadaPor: null,
  error: null,
});

describe('clasificarRegla', () => {
  it('mapea cada acción a su estado visual', () => {
    expect(clasificarRegla(regla(['NOOP_INFO']))).toBe('paso');
    expect(clasificarRegla(regla(['ACTIVAR_VALVULA']))).toBe('accion');
    expect(clasificarRegla(regla(['ACTIVAR_BOMBA']))).toBe('accion');
    expect(clasificarRegla(regla(['MOVER_MEDIASOMBRA']))).toBe('accion');
    expect(clasificarRegla(regla(['POSTPONE_RIEGO']))).toBe('pospuso');
    expect(clasificarRegla(regla(['ABORT_RIEGO']))).toBe('bloqueo');
    expect(clasificarRegla(regla(['ABORT_INSUMO']))).toBe('bloqueo');
    expect(clasificarRegla(regla(['ABORT_ALL']))).toBe('bloqueo');
  });

  it('mapea los estados de la traza que no corrieron', () => {
    expect(clasificarRegla(regla([], 'OMITIDA_RAMA_BLOQUEADA'))).toBe('omitida');
    expect(clasificarRegla(regla([], 'NO_ALCANZADA'))).toBe('noAlcanzada');
    expect(clasificarRegla(regla([], 'ERROR'))).toBe('error');
  });

  it('si una regla emite varias acciones, manda la más fuerte', () => {
    expect(clasificarRegla(regla(['ACTIVAR_VALVULA', 'ABORT_RIEGO']))).toBe('bloqueo');
    expect(clasificarRegla(regla(['NOOP_INFO', 'ACTIVAR_VALVULA']))).toBe('accion');
  });

  it('la alerta no es una acción sobre un actuador: acompaña a la que decide el estado', () => {
    expect(clasificarRegla(regla(['ALERTA']))).toBe('paso');
    expect(clasificarRegla(regla(['ABORT_RIEGO', 'ALERTA']))).toBe('bloqueo');
    expect(clasificarRegla(regla(['POSTPONE_RIEGO', 'ALERTA']))).toBe('pospuso');
    expect(clasificarRegla(regla(['ACTIVAR_VALVULA', 'ALERTA']))).toBe('accion');
  });
});

describe('trazaANodos', () => {
  it('sector sano: las reglas pasan y el terminal de cada rama dice que no accionó', () => {
    const e = estados({});

    expect(e['start']).toBe('inicio');
    expect(e['ManualLockRule']).toBe('paso');
    expect(e['StaleSensorRule']).toBe('paso');
    expect(e['CicloLecturaRiegoRule']).toBe('paso');
    expect(e['RiegoPorDeficitRule']).toBe('paso');
    expect(e['success-RIEGO']).toBe('noAccion');
    expect(e['success-INSUMO']).toBe('noAccion');
    expect(e['success-MEDIASOMBRA']).toBe('noAccion');
  });

  it('humedad bajo el umbral: el riego acciona y el terminal de RIEGO también', () => {
    const e = estados({ humSus: 38 });

    expect(e['RiegoPorDeficitRule']).toBe('accion');
    expect(e['success-RIEGO']).toBe('accion');
    expect(e['success-INSUMO']).toBe('noAccion');
  });

  it('déficit crítico: R-02 acciona (con su alerta) y las compuertas siguientes pasan sin cortar', () => {
    const e = estados({ humSus: 30 });

    expect(e['DeficitCriticoRule']).toBe('accion');
    for (const id of ['FueraDeVentanaRiegoRule', 'PausaTrasAplicacionRule', 'PosponerPorLluviaRule', 'RiegoPorDeficitRule']) {
      expect(e[id]).toBe('paso');
    }
    expect(e['success-RIEGO']).toBe('accion');
  });

  it('sustrato saturado: R-04 bloquea y omite el resto de la rama', () => {
    const e = estados({ humSus: 82 });

    expect(e['SustratoSaturadoRule']).toBe('bloqueo');
    expect(e['DeficitCriticoRule']).toBe('omitida');
    expect(e['RiegoPorDeficitRule']).toBe('omitida');
  });

  it('lluvia: pospone, y las siguientes de la rama quedan omitidas', () => {
    const e = estados({ humSus: 40, lluviaPct: 80, lluviaMm: 8 });

    expect(e['PosponerPorLluviaRule']).toBe('pospuso');
    expect(e['RiegoPorDeficitRule']).toBe('omitida');
    expect(e['success-RIEGO']).toBe('noAccion');
    // Las otras ramas no se enteran
    expect(e['ShadingRule']).toBe('paso');
  });

  it('lectura vieja: bloquea la regla que cortó y omite la rama de riego', () => {
    const e = estados({ antiguedadSeg: 300 });

    expect(e['StaleSensorRule']).toBe('bloqueo');
    expect(e['CicloLecturaRiegoRule']).toBe('omitida');
    expect(e['RiegoPorDeficitRule']).toBe('omitida');
  });

  it('bloqueo manual: todo lo demás queda no alcanzado', () => {
    const e = estados({ bloqueoManual: true });

    expect(e['ManualLockRule']).toBe('bloqueo');
    expect(e['StaleSensorRule']).toBe('noAlcanzada');
    expect(e['RiegoPorDeficitRule']).toBe('noAlcanzada');
    // Ninguna regla de la rama llegó a evaluarse: el terminal no dice "no se regó", dice que no se alcanzó.
    expect(e['success-RIEGO']).toBe('noAlcanzada');
    expect(e['success-INSUMO']).toBe('noAlcanzada');
  });

  it('lectura vieja: el terminal de la rama de riego queda omitido, no "no accionó"', () => {
    const e = estados({ antiguedadSeg: 300 });

    expect(e['success-RIEGO']).toBe('omitida');
  });

  it('una rama evaluada que no accionó sigue siendo "no accionó"', () => {
    expect(estados({})['success-RIEGO']).toBe('noAccion');
    // lluvia: la primera regla de la rama corrió y pospuso; el resto se omitió, pero la rama se evaluó
    expect(estados({ humSus: 40, lluviaPct: 80, lluviaMm: 8 })['success-RIEGO']).toBe('noAccion');
  });

  it('un nodo que no figura en la traza queda sin evaluar (no se inventa estado)', () => {
    const traza = evaluarMotor(catalogo, base);
    const e = trazaANodos(schema, {
      ...traza,
      reglas: traza.reglas.filter((r) => r.ruleId !== 'RiegoPorDeficitRule'),
    });

    expect(e['RiegoPorDeficitRule']).toBe('sinTraza');
  });

  it('cubre todos los nodos del esquema', () => {
    const e = estados({});
    for (const n of schema.nodes) expect(e).toHaveProperty([n.id]);
  });
});

describe('colorArista', () => {
  const APAGADO = { color: 'var(--off)', activa: false };

  it('se apaga si el ORIGEN no se evaluó, aunque el destino sea un terminal', () => {
    expect(colorArista('noAlcanzada', 'noAccion')).toEqual(APAGADO);
    expect(colorArista('omitida', 'noAccion')).toEqual(APAGADO);
    expect(colorArista('sinTraza', 'noAccion')).toEqual(APAGADO);
    expect(colorArista('noAlcanzada', 'noAlcanzada')).toEqual(APAGADO);
  });

  it('se apaga si el destino no se evaluó', () => {
    expect(colorArista('paso', 'omitida')).toEqual(APAGADO);
    expect(colorArista('paso', 'noAlcanzada')).toEqual(APAGADO);
  });

  it('entre nodos evaluados queda activa: verde, azul si la acción viene de una regla que accionó, ámbar si pospuso', () => {
    expect(colorArista('paso', 'paso')).toEqual({ color: 'var(--ok)', activa: true });
    expect(colorArista('paso', 'noAccion')).toEqual({ color: 'var(--ok)', activa: true });
    expect(colorArista('paso', 'accion')).toEqual({ color: 'var(--ok)', activa: true });
    expect(colorArista('accion', 'accion')).toEqual({ color: 'var(--info)', activa: true });
    expect(colorArista('pospuso', 'noAccion')).toEqual({ color: 'var(--warn)', activa: true });
  });
});
