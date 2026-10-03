import { describe, expect, it } from 'vitest';
import fixture from './catalogoReglas.fixture.json';
import { evaluarMotor, type EntradaMotor } from './trazaReglas';
import type { CatalogoReglas, TrazaEvaluacion, TrazaRegla } from '@/types/domain';

const catalogo = fixture as CatalogoReglas;

/** 2026-06-13 12:00 UTC = 09:00 hora del vivero (UTC-3): dentro de la ventana de riego. */
const TS_DIA = '2026-06-13T12:00:00.000Z';
/** 2026-06-13 03:00 UTC = 00:00 hora del vivero: fuera de la ventana. */
const TS_NOCHE = '2026-06-13T03:00:00.000Z';
const ms = (iso: string) => Date.parse(iso);
const HORA = 3_600_000;

/** Un sector sano, con lectura fresca, sin lluvia ni UV, sin riegos previos. */
const base: EntradaMotor = {
  sectorId: 'MZ-1-001',
  zonaId: 'MZ-1',
  origen: 'TELEMETRIA',
  ts: TS_DIA,
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

const regla = (t: TrazaEvaluacion, id: string): TrazaRegla => {
  const r = t.reglas.find((x) => x.ruleId === id);
  if (!r) throw new Error(`falta ${id} en la traza`);
  return r;
};

describe('evaluarMotor · forma de la traza', () => {
  it('trae las 13 reglas, en orden de prioridad, con el origen y el sector', () => {
    const t = evaluarMotor(catalogo, base);

    expect(t.reglas.map((r) => r.ruleId)).toEqual(catalogo.reglas.map((r) => r.id));
    expect(t.sectorId).toBe('MZ-1-001');
    expect(t.zonaId).toBe('MZ-1');
    expect(t.origen).toBe('TELEMETRIA');
    expect(t.ts).toBe(base.ts);
    expect(t.reglas).toHaveLength(13);
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

const tipos = (r: TrazaRegla) => r.acciones.map((a) => a.tipo);

describe('evaluarMotor · R-01 riego por déficit', () => {
  it('humedad entre el crítico y el umbral: aplica y ordena regar el volumen del déficit', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, humSus: 40 }), 'RiegoPorDeficitRule');

    expect(r.estado).toBe('EVALUADA');
    expect(r.comparaciones.map((c) => [c.clave, c.operador, c.umbral, c.resultado])).toEqual([
      ['riego.umbral-critico', 'GE', 35, 'CUMPLE'],
      ['riego.umbral-humedad', 'LT', 45, 'CUMPLE'],
    ]);
    expect(tipos(r)).toEqual(['ACTIVAR_VALVULA']);
    // (65 − 40) × 0,2 = 5 L; a 30 L/h son 600 s.
    expect(r.acciones[0].motivo).toContain('Regar 5 L (600 s)');
  });

  it('humedad sobre el umbral: no hay déficit y no se riega', () => {
    const t = evaluarMotor(catalogo, base);
    const r = regla(t, 'RiegoPorDeficitRule');

    expect(r.comparaciones[1]).toMatchObject({ recibido: 55, umbral: 45, resultado: 'NO_CUMPLE' });
    expect(tipos(r)).toEqual(['NOOP_INFO']);
    expect(r.acciones[0].motivo).toMatch(/no hay déficit/);
  });

  it('el volumen se topa en el máximo por evento y el tiempo sale del caudal', () => {
    // (65 − 36) × 0,2 = 5,8 L < 6 L; con caudal 120 L/h son 174 s.
    const editado: CatalogoReglas = {
      ...catalogo,
      parametros: catalogo.parametros.map((p) => (p.clave === 'riego.caudal-emisor' ? { ...p, valor: '120' } : p)),
    };
    const r = regla(evaluarMotor(editado, { ...base, humSus: 36 }), 'RiegoPorDeficitRule');

    expect(r.acciones[0].motivo).toContain('Regar 5,8 L (174 s)');
  });

  it('usa el valor vigente del catálogo, no el de fábrica', () => {
    const editado: CatalogoReglas = {
      ...catalogo,
      parametros: catalogo.parametros.map((p) => (p.clave === 'riego.umbral-humedad' ? { ...p, valor: '40' } : p)),
    };
    const r = regla(evaluarMotor(editado, { ...base, humSus: 42 }), 'RiegoPorDeficitRule');

    expect(r.comparaciones[1]).toMatchObject({ umbral: 40, resultado: 'NO_CUMPLE' });
    expect(tipos(r)).toEqual(['NOOP_INFO']);
  });

  it('sin dato de humedad (barrido sin métricas): SIN_DATO con recibido null y no riega', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, humSus: null }), 'RiegoPorDeficitRule');

    expect(r.comparaciones[0]).toMatchObject({ recibido: null, resultado: 'SIN_DATO' });
    expect(tipos(r)).toEqual(['NOOP_INFO']);
  });
});

describe('evaluarMotor · R-02 déficit crítico (gana a las compuertas)', () => {
  it('bajo el umbral crítico: riega el volumen máximo y emite la alerta CRITICAL', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 30 });
    const r = regla(t, 'DeficitCriticoRule');

    expect(r.comparaciones[0]).toMatchObject({ clave: 'riego.umbral-critico', operador: 'LT', umbral: 35, resultado: 'CUMPLE' });
    expect(tipos(r)).toEqual(['ACTIVAR_VALVULA', 'ALERTA']);
    expect(r.acciones[0].motivo).toContain('Regar 6 L (720 s)');
  });

  it('las compuertas R-05, R-06 y R-03 no actúan: dicen que lo cubre R-02, y R-01 también', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 30 });

    for (const id of ['FueraDeVentanaRiegoRule', 'PausaTrasAplicacionRule', 'PosponerPorLluviaRule', 'RiegoPorDeficitRule']) {
      expect(regla(t, id).estado).toBe('EVALUADA');
      expect(tipos(regla(t, id))).toEqual(['NOOP_INFO']);
      expect(regla(t, id).acciones[0].motivo).toMatch(/R-02/);
    }
  });

  it('de noche, con lluvia prevista y recién fertilizado: R-02 riega igual', () => {
    const t = evaluarMotor(catalogo, {
      ...base,
      ts: TS_NOCHE,
      humSus: 30,
      lluviaPct: 90,
      lluviaMm: 12,
      ultimaAplicacionMs: ms(TS_NOCHE) - HORA,
    });

    expect(tipos(regla(t, 'DeficitCriticoRule'))).toEqual(['ACTIVAR_VALVULA', 'ALERTA']);
    expect(tipos(regla(t, 'FueraDeVentanaRiegoRule'))).toEqual(['NOOP_INFO']);
    expect(tipos(regla(t, 'PosponerPorLluviaRule'))).toEqual(['NOOP_INFO']);
  });

  it('con un riego crítico reciente respeta el tope y aborta (sin S-06 un sensor roto regaría sin fin)', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 30, ultimoRiegoCriticoMs: ms(TS_DIA) - 3 * HORA });
    const r = regla(t, 'DeficitCriticoRule');

    expect(r.comparaciones[1]).toMatchObject({ clave: 'riego.exceptuado-bloqueo', recibido: 3, umbral: 12, resultado: 'CUMPLE' });
    expect(tipos(r)).toEqual(['ABORT_RIEGO']);
  });

  it('pasado el tope vuelve a regar', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 30, ultimoRiegoCriticoMs: ms(TS_DIA) - 13 * HORA });

    expect(tipos(regla(t, 'DeficitCriticoRule'))).toEqual(['ACTIVAR_VALVULA', 'ALERTA']);
  });
});

describe('evaluarMotor · R-05 ventana horaria', () => {
  it('de noche el déficit común no riega: la ventana (operador EN) corta la rama', () => {
    const t = evaluarMotor(catalogo, { ...base, ts: TS_NOCHE, humSus: 40 });
    const r = regla(t, 'FueraDeVentanaRiegoRule');

    expect(r.comparaciones[2]).toEqual({
      etiqueta: 'Hora local',
      clave: 'riego.ventana-normal',
      recibido: '00:00',
      operador: 'EN',
      umbral: '06:00-18:00',
      unidad: '',
      configurable: true,
      resultado: 'NO_CUMPLE',
    });
    expect(tipos(r)).toEqual(['ABORT_RIEGO']);
    for (const id of ['PausaTrasAplicacionRule', 'PosponerPorLluviaRule', 'RiegoPorDeficitRule']) {
      expect(regla(t, id)).toMatchObject({ estado: 'OMITIDA_RAMA_BLOQUEADA', bloqueadaPor: 'FueraDeVentanaRiegoRule' });
    }
  });

  it('de día pasa: la hora local (UTC-3) está dentro de la ventana', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, humSus: 40 }), 'FueraDeVentanaRiegoRule');

    expect(r.comparaciones[2]).toMatchObject({ recibido: '09:00', operador: 'EN', resultado: 'CUMPLE' });
    expect(tipos(r)).toEqual(['NOOP_INFO']);
  });

  it('la lectura de las 18:00 todavía entra y la de las 18:01 no', () => {
    const a1800 = regla(evaluarMotor(catalogo, { ...base, ts: '2026-06-13T21:00:59.000Z', humSus: 40 }), 'FueraDeVentanaRiegoRule');
    const a1801 = regla(evaluarMotor(catalogo, { ...base, ts: '2026-06-13T21:01:00.000Z', humSus: 40 }), 'FueraDeVentanaRiegoRule');

    expect(a1800.comparaciones[2]).toMatchObject({ recibido: '18:00', resultado: 'CUMPLE' });
    expect(a1801.comparaciones[2]).toMatchObject({ recibido: '18:01', resultado: 'NO_CUMPLE' });
  });
});

describe('evaluarMotor · R-03 lluvia', () => {
  it('lluvia probable y abundante pospone el riego, emite la alerta y omite R-01', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 40, lluviaPct: 80, lluviaMm: 8 });
    const lluvia = regla(t, 'PosponerPorLluviaRule');

    expect(lluvia.comparaciones[2]).toMatchObject({ clave: 'riego.lluvia-probabilidad', recibido: 80, umbral: 70, resultado: 'CUMPLE' });
    expect(lluvia.comparaciones[3]).toMatchObject({ clave: 'riego.lluvia-mm', recibido: 8, umbral: 5, resultado: 'CUMPLE' });
    expect(tipos(lluvia)).toEqual(['POSTPONE_RIEGO', 'ALERTA']);
    expect(regla(t, 'RiegoPorDeficitRule')).toMatchObject({ estado: 'OMITIDA_RAMA_BLOQUEADA', bloqueadaPor: 'PosponerPorLluviaRule' });
    // Las otras ramas siguen evaluándose.
    expect(regla(t, 'DailyDoseLimitRule').estado).toBe('EVALUADA');
    expect(regla(t, 'ShadingRule').estado).toBe('EVALUADA');
  });

  it('probable pero con pocos milímetros: no pospone', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 40, lluviaPct: 80, lluviaMm: 2 });

    expect(tipos(regla(t, 'PosponerPorLluviaRule'))).toEqual(['NOOP_INFO']);
    expect(tipos(regla(t, 'RiegoPorDeficitRule'))).toEqual(['ACTIVAR_VALVULA']);
  });

  it('sin pronóstico queda SIN_DATO y no pospone (se asume que no llueve)', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, humSus: 40, lluviaPct: null, lluviaMm: null }), 'PosponerPorLluviaRule');

    expect(r.comparaciones[2]).toMatchObject({ recibido: null, resultado: 'SIN_DATO' });
    expect(tipos(r)).toEqual(['NOOP_INFO']);
  });
});

describe('evaluarMotor · R-04 sustrato saturado', () => {
  it('saturado bloquea el riego y omite el resto de la rama', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 77 });
    const r = regla(t, 'SustratoSaturadoRule');

    expect(r.comparaciones[0]).toMatchObject({ clave: 'riego.saturacion-bloqueo', operador: 'GE', umbral: 75, resultado: 'CUMPLE' });
    expect(tipos(r)).toEqual(['ABORT_RIEGO']);
    expect(regla(t, 'DeficitCriticoRule')).toMatchObject({ estado: 'OMITIDA_RAMA_BLOQUEADA', bloqueadaPor: 'SustratoSaturadoRule' });
  });

  it('con la humedad en el umbral de alerta además emite ALERTA', () => {
    expect(tipos(regla(evaluarMotor(catalogo, { ...base, humSus: 82 }), 'SustratoSaturadoRule'))).toEqual(['ABORT_RIEGO', 'ALERTA']);
  });

  it('humedad normal: no bloquea', () => {
    expect(tipos(regla(evaluarMotor(catalogo, base), 'SustratoSaturadoRule'))).toEqual(['NOOP_INFO']);
  });
});

describe('evaluarMotor · R-06 pausa tras una aplicación', () => {
  it('con un insumo aplicado hace 2 h el riego por déficit se pausa', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 40, ultimaAplicacionMs: ms(TS_DIA) - 2 * HORA });
    const r = regla(t, 'PausaTrasAplicacionRule');

    expect(r.comparaciones[2]).toMatchObject({ clave: 'riego.pausa-tras-aplicacion', recibido: 2, umbral: 6, resultado: 'CUMPLE' });
    expect(tipos(r)).toEqual(['ABORT_RIEGO']);
  });

  it('con la pausa cumplida o sin aplicaciones deja pasar', () => {
    const cumplida = regla(evaluarMotor(catalogo, { ...base, humSus: 40, ultimaAplicacionMs: ms(TS_DIA) - 7 * HORA }), 'PausaTrasAplicacionRule');
    const sin = regla(evaluarMotor(catalogo, { ...base, humSus: 40 }), 'PausaTrasAplicacionRule');

    expect(tipos(cumplida)).toEqual(['NOOP_INFO']);
    expect(tipos(sin)).toEqual(['NOOP_INFO']);
  });
});

describe('evaluarMotor · un riego por ciclo de lectura', () => {
  it('un sector regado en este ciclo no vuelve a regar por déficit común (R-01)', () => {
    // 09:00 local: el ciclo de 4 h anclado a las 02:00 empezó a las 06:00; el riego fue a las 08:30.
    const t = evaluarMotor(catalogo, { ...base, humSus: 40, ultimoRiegoMs: ms(TS_DIA) - 30 * 60_000 });
    const r = regla(t, 'CicloLecturaRiegoRule');

    expect(r.comparaciones[0]).toMatchObject({ recibido: 30, operador: 'LE', umbral: 180, configurable: false, resultado: 'CUMPLE' });
    expect(tipos(r)).toEqual(['ABORT_RIEGO']);
    expect(r.acciones[0].motivo).toMatch(/ya se regó en este ciclo/);
    expect(regla(t, 'DeficitCriticoRule')).toMatchObject({ estado: 'OMITIDA_RAMA_BLOQUEADA', bloqueadaPor: 'CicloLecturaRiegoRule' });
  });

  it('con déficit crítico la guarda de ciclo NO corta: R-02 sólo tiene su tope de horas', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 30, ultimoRiegoMs: ms(TS_DIA) - 30 * 60_000 });
    const r = regla(t, 'CicloLecturaRiegoRule');

    expect(r.comparaciones.at(-1)).toMatchObject({ etiqueta: 'Humedad de sustrato', recibido: 30, operador: 'LT', clave: 'riego.umbral-critico', resultado: 'CUMPLE' });
    expect(tipos(r)).toEqual(['NOOP_INFO']);
    expect(r.acciones[0].motivo).toMatch(/no aplica a R-02/);
    expect(tipos(regla(t, 'DeficitCriticoRule'))).toEqual(['ACTIVAR_VALVULA', 'ALERTA']);
  });

  it('con déficit crítico y el riego previo también dentro del tope de R-02, el tope corta', () => {
    const t = evaluarMotor(catalogo, {
      ...base, humSus: 30, ultimoRiegoMs: ms(TS_DIA) - 30 * 60_000, ultimoRiegoCriticoMs: ms(TS_DIA) - 3 * HORA,
    });

    expect(tipos(regla(t, 'DeficitCriticoRule'))).toEqual(['ABORT_RIEGO']);
  });

  it('ya regó en el ciclo y no hay lectura de humedad: corta (sin dato no se arriesga)', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: null, ultimoRiegoMs: ms(TS_DIA) - 30 * 60_000 });

    expect(tipos(regla(t, 'CicloLecturaRiegoRule'))).toEqual(['ABORT_RIEGO']);
  });

  it('un riego abierto en este momento corta también a R-02 (nadie abre una válvula abierta)', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 30, riegoEnCursoHastaMs: ms(TS_DIA) + 120_000 });

    expect(tipos(regla(t, 'CicloLecturaRiegoRule'))).toEqual(['ABORT_RIEGO']);
    expect(regla(t, 'DeficitCriticoRule').estado).toBe('OMITIDA_RAMA_BLOQUEADA');
  });

  it('un riego de un ciclo anterior no cuenta', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 40, ultimoRiegoMs: ms(TS_DIA) - 4 * HORA });

    expect(tipos(regla(t, 'CicloLecturaRiegoRule'))).toEqual(['NOOP_INFO']);
    expect(tipos(regla(t, 'RiegoPorDeficitRule'))).toEqual(['ACTIVAR_VALVULA']);
  });

  it('un riego abierto en este momento también corta', () => {
    const t = evaluarMotor(catalogo, { ...base, humSus: 40, riegoEnCursoHastaMs: ms(TS_DIA) + 120_000 });
    const r = regla(t, 'CicloLecturaRiegoRule');

    expect(r.comparaciones[1]).toMatchObject({ recibido: 120, operador: 'GT', umbral: 0, resultado: 'CUMPLE' });
    expect(tipos(r)).toEqual(['ABORT_RIEGO']);
  });

  it('un sector que nunca regó queda SIN_DATO en la primera comparación y no corta', () => {
    const r = regla(evaluarMotor(catalogo, base), 'CicloLecturaRiegoRule');

    expect(r.comparaciones[0]).toMatchObject({ recibido: null, resultado: 'SIN_DATO' });
    expect(tipos(r)).toEqual(['NOOP_INFO']);
  });
});

describe('evaluarMotor · bloqueos por rama', () => {
  it('la lectura vieja (barrido) bloquea sólo la rama de riego', () => {
    const t = evaluarMotor(catalogo, { ...base, origen: 'BARRIDO', antiguedadSeg: 300, humSus: null });

    expect(regla(t, 'StaleSensorRule').comparaciones[0]).toMatchObject({
      recibido: 300,
      operador: 'GT',
      umbral: 90,
      unidad: 's',
      resultado: 'CUMPLE',
    });
    expect(tipos(regla(t, 'StaleSensorRule'))).toEqual(['ABORT_RIEGO']);
    expect(regla(t, 'CicloLecturaRiegoRule')).toMatchObject({ estado: 'OMITIDA_RAMA_BLOQUEADA', bloqueadaPor: 'StaleSensorRule' });
    expect(regla(t, 'RiegoPorDeficitRule')).toMatchObject({ estado: 'OMITIDA_RAMA_BLOQUEADA', bloqueadaPor: 'StaleSensorRule' });
    expect(regla(t, 'SupplyRule').estado).toBe('EVALUADA');
  });

  it('la regla de frescura siempre registra las dos antigüedades (lectura y humedad de sustrato)', () => {
    const r = regla(evaluarMotor(catalogo, base), 'StaleSensorRule');

    expect(r.comparaciones.map((c) => c.etiqueta)).toEqual([
      'Antigüedad de la última lectura',
      'Antigüedad de la humedad de sustrato',
    ]);
    expect(tipos(r)).toEqual(['NOOP_INFO']);
  });

  it('con la sonda de humedad congelada (lectura fresca, humedad vieja) no se riega', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, antiguedadHumSusSeg: 4000 }), 'StaleSensorRule');

    expect(r.comparaciones[1]).toMatchObject({ recibido: 4000, resultado: 'CUMPLE' });
    expect(tipos(r)).toEqual(['ABORT_RIEGO']);
    expect(r.acciones[0].motivo).toMatch(/sonda/);
  });

  it('sin ninguna lectura la regla de antigüedad queda SIN_DATO y bloquea igual', () => {
    const r = regla(evaluarMotor(catalogo, { ...base, antiguedadSeg: null }), 'StaleSensorRule');

    expect(r.comparaciones[0]).toMatchObject({ recibido: null, resultado: 'SIN_DATO' });
    expect(tipos(r)).toEqual(['ABORT_RIEGO']);
  });

  it('el bloqueo manual corta todo: el resto queda NO_ALCANZADA', () => {
    const t = evaluarMotor(catalogo, { ...base, bloqueoManual: true });

    expect(regla(t, 'ManualLockRule').comparaciones[0]).toMatchObject({
      clave: null,
      configurable: false,
      recibido: true,
      resultado: 'CUMPLE',
    });
    expect(tipos(regla(t, 'ManualLockRule'))).toEqual(['ABORT_ALL']);
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
