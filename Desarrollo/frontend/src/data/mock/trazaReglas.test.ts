import { describe, expect, it } from 'vitest';
import fixture from './catalogoReglas.fixture.json';
import { evaluarMotor, type EntradaMotor } from './trazaReglas';
import type { CatalogoReglas, TrazaEvaluacion, TrazaRegla } from '@/types/domain';

const catalogo = fixture as CatalogoReglas;

/** Un sector sano, con lectura fresca, sin lluvia ni UV, sin riegos previos. */
const base: EntradaMotor = {
  sectorId: 'MZ-1-001',
  zonaId: 'MZ-1',
  origen: 'TELEMETRIA',
  ts: '2026-06-13T12:00:00.000Z',
  antiguedadSeg: 1,
  bloqueoManual: false,
  humSus: 55,
  lluviaPct: 10,
  uvIndex: 3,
  riegos24h: 0,
  dosis24h: 0,
  estadoSector: 'ok',
  confianza: 92,
};

const regla = (t: TrazaEvaluacion, id: string): TrazaRegla => {
  const r = t.reglas.find((x) => x.ruleId === id);
  if (!r) throw new Error(`falta ${id} en la traza`);
  return r;
};

describe('evaluarMotor · forma de la traza', () => {
  it('trae las 9 reglas, en orden de prioridad, con el origen y el sector', () => {
    const t = evaluarMotor(catalogo, base);

    expect(t.reglas.map((r) => r.ruleId)).toEqual(catalogo.reglas.map((r) => r.id));
    expect(t.sectorId).toBe('MZ-1-001');
    expect(t.zonaId).toBe('MZ-1');
    expect(t.origen).toBe('TELEMETRIA');
    expect(t.ts).toBe(base.ts);
  });

  it('es determinística', () => {
    expect(evaluarMotor(catalogo, base)).toEqual(evaluarMotor(catalogo, base));
  });

  it('la huella cambia cuando cambia un valor vigente', () => {
    const editado: CatalogoReglas = {
      ...catalogo,
      parametros: catalogo.parametros.map((p) =>
        p.clave === 'riego.umbral-humedad' ? { ...p, valor: '40' } : p,
      ),
    };
    expect(evaluarMotor(editado, base).parametrosHash).not.toBe(
      evaluarMotor(catalogo, base).parametrosHash,
    );
  });
});

describe('evaluarMotor · riego', () => {
  it('humedad bajo el umbral: cumple, y la regla acciona la electroválvula', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, humSus: 38 }), 'IrrigationRule');

    expect(r.estado).toBe('EVALUADA');
    expect(r.comparaciones[0]).toEqual({
      etiqueta: 'Humedad de sustrato',
      clave: 'riego.umbral-humedad',
      recibido: 38,
      operador: 'LT',
      umbral: 42,
      unidad: '%',
      configurable: true,
      resultado: 'CUMPLE',
    });
    expect(r.acciones.map((a) => a.tipo)).toEqual(['ACTIVAR_VALVULA']);
  });

  it('humedad sobre el umbral: no cumple y no se riega', () => {
    const r = regla(evaluarMotor(catalogo, base), 'IrrigationRule');

    expect(r.comparaciones).toHaveLength(1);
    expect(r.comparaciones[0]).toMatchObject({ recibido: 55, umbral: 42, resultado: 'NO_CUMPLE' });
    expect(r.acciones.map((a) => a.tipo)).toEqual(['NOOP_INFO']);
  });

  it('usa el valor vigente del catálogo, no el de fábrica', () => {
    const editado: CatalogoReglas = {
      ...catalogo,
      parametros: catalogo.parametros.map((p) =>
        p.clave === 'riego.umbral-humedad' ? { ...p, valor: '35' } : p,
      ),
    };
    const r = regla(evaluarMotor(editado, { ...base, humSus: 38 }), 'IrrigationRule');

    expect(r.comparaciones[0]).toMatchObject({ umbral: 35, resultado: 'NO_CUMPLE' });
  });

  it('sin dato de humedad (barrido): SIN_DATO con recibido null', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, humSus: null }), 'IrrigationRule');

    expect(r.comparaciones[0]).toMatchObject({ recibido: null, resultado: 'SIN_DATO' });
  });

  it('bajo el umbral pero con el tope de riegos alcanzado: bloquea la rama', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 38, riegos24h: 1 });
    const r = regla(t, 'IrrigationRule');

    expect(r.comparaciones.map((c) => c.clave)).toEqual([
      'riego.umbral-humedad',
      'riego.max-riegos-24h-sector',
    ]);
    expect(r.comparaciones[1]).toMatchObject({ recibido: 1, operador: 'GE', umbral: 1, resultado: 'CUMPLE' });
    expect(r.acciones.map((a) => a.tipo)).toEqual(['ABORT_RIEGO']);
  });
});

describe('evaluarMotor · bloqueos por rama', () => {
  it('lluvia probable pospone el riego y omite las reglas siguientes de la rama', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 38, lluviaPct: 80 });

    const lluvia = regla(t, 'WeatherOverrideRule');
    expect(lluvia.comparaciones[0]).toMatchObject({
      recibido: 80,
      operador: 'GE',
      umbral: 60,
      resultado: 'CUMPLE',
    });
    expect(lluvia.acciones[0].tipo).toBe('POSTPONE_RIEGO');

    for (const id of ['DailyVolumeLimitRule', 'IrrigationRule']) {
      expect(regla(t, id)).toMatchObject({
        estado: 'OMITIDA_RAMA_BLOQUEADA',
        bloqueadaPor: 'WeatherOverrideRule',
        comparaciones: [],
        acciones: [],
      });
    }
    // Las otras ramas siguen evaluándose.
    expect(regla(t, 'DailyDoseLimitRule').estado).toBe('EVALUADA');
    expect(regla(t, 'ShadingRule').estado).toBe('EVALUADA');
  });

  it('la lectura vieja (barrido) bloquea sólo la rama de riego', () => {
    const t = evaluarMotor(catalogo, { ...base, origen: 'BARRIDO', antiguedadSeg: 300, humSus: null });

    expect(regla(t, 'StaleSensorRule').comparaciones[0]).toMatchObject({
      recibido: 300,
      operador: 'GT',
      umbral: 90,
      unidad: 's',
      resultado: 'CUMPLE',
    });
    expect(regla(t, 'StaleSensorRule').acciones[0].tipo).toBe('ABORT_RIEGO');
    expect(regla(t, 'IrrigationRule')).toMatchObject({
      estado: 'OMITIDA_RAMA_BLOQUEADA',
      bloqueadaPor: 'StaleSensorRule',
    });
    expect(regla(t, 'SupplyRule').estado).toBe('EVALUADA');
  });

  it('sin ninguna lectura la regla de antigüedad queda SIN_DATO y bloquea igual', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, antiguedadSeg: null }), 'StaleSensorRule');

    expect(r.comparaciones[0]).toMatchObject({ recibido: null, resultado: 'SIN_DATO' });
    expect(r.acciones[0].tipo).toBe('ABORT_RIEGO');
  });

  it('el bloqueo manual corta todo: el resto queda NO_ALCANZADA', () => {
    const t = evaluarMotor(catalogo, { ...base, bloqueoManual: true });

    expect(regla(t, 'ManualLockRule').comparaciones[0]).toMatchObject({
      clave: null,
      configurable: false,
      recibido: true,
      resultado: 'CUMPLE',
    });
    expect(regla(t, 'ManualLockRule').acciones[0].tipo).toBe('ABORT_ALL');
    for (const r of t.reglas.filter((x) => x.ruleId !== 'ManualLockRule')) {
      expect(r).toMatchObject({ estado: 'NO_ALCANZADA', bloqueadaPor: 'ManualLockRule' });
    }
  });
});

describe('evaluarMotor · insumo y mediasombra', () => {
  it('sector crítico con diagnóstico confiable: dosifica', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, estadoSector: 'critical', confianza: 90 }), 'SupplyRule');

    expect(r.comparaciones[0]).toMatchObject({ clave: null, configurable: false, recibido: 'critical' });
    expect(r.comparaciones[1]).toMatchObject({
      clave: 'diagnostico.confianza-minima',
      recibido: 90,
      umbral: 85,
      resultado: 'CUMPLE',
    });
    expect(r.acciones[0].tipo).toBe('ACTIVAR_BOMBA');
  });

  it('diagnóstico poco confiable: no dosifica', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, estadoSector: 'critical', confianza: 70 }), 'SupplyRule');

    expect(r.comparaciones[1].resultado).toBe('NO_CUMPLE');
    expect(r.acciones[0].tipo).toBe('NOOP_INFO');
  });

  it('sector que no es crítico: sólo la condición fija, sin segunda comparación', () => {
    const r = regla(evaluarMotor(catalogo, base), 'SupplyRule');

    expect(r.comparaciones).toHaveLength(1);
    expect(r.comparaciones[0]).toMatchObject({ resultado: 'NO_CUMPLE', configurable: false });
  });

  it('pico de UV: la mediasombra pasa a la apertura protectora', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, uvIndex: 9 }), 'ShadingRule');

    expect(r.comparaciones[0]).toMatchObject({ recibido: 9, operador: 'GE', umbral: 7, resultado: 'CUMPLE' });
    expect(r.acciones[0].tipo).toBe('MOVER_MEDIASOMBRA');
  });

  it('el seguimiento no tiene comparaciones', () => {
    expect(regla(evaluarMotor(catalogo, base), 'FollowUpRule').comparaciones).toEqual([]);
  });
});
