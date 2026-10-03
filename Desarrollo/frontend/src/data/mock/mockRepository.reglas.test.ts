import { afterEach, describe, expect, it, vi } from 'vitest';
import { MockRepository } from './mockRepository';
import fixture from './catalogoReglas.fixture.json';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import type { CatalogoReglas } from '@/types/domain';

const nuevo = () => new MockRepository(20260613);
const UMBRAL = 'riego.umbral-humedad';
const param = (c: CatalogoReglas, clave: string) => c.parametros.find((p) => p.clave === clave)!;

describe('MockRepository · catálogo de reglas', () => {
  it('arma el catálogo desde el fixture', async () => {
    expect(await nuevo().getCatalogoReglas()).toEqual(fixture);
  });

  it('entrega copias: mutar el resultado no altera el repositorio', async () => {
    const repo = nuevo();
    const c = await repo.getCatalogoReglas();
    param(c, UMBRAL).valor = '1';
    expect(param(await repo.getCatalogoReglas(), UMBRAL).valor).toBe('45');
  });

  it('saveParametros persiste en memoria y marca el parámetro como modificado', async () => {
    const repo = nuevo();

    const guardado = await repo.saveParametros([{ clave: UMBRAL, valor: '40' }]);

    expect(param(guardado, UMBRAL)).toMatchObject({ valor: '40', fabrica: '45', modificado: true });
    expect(param(guardado, UMBRAL).updatedBy).toBeTruthy();
    expect(param(guardado, UMBRAL).updatedTs).toEqual(expect.any(Number));
    expect(param(await repo.getCatalogoReglas(), UMBRAL).valor).toBe('40');
  });

  it('valor null restablece la fábrica', async () => {
    const repo = nuevo();
    await repo.saveParametros([{ clave: UMBRAL, valor: '40' }]);

    const c = await repo.saveParametros([{ clave: UMBRAL, valor: null }]);

    expect(param(c, UMBRAL)).toMatchObject({ valor: '45', modificado: false, updatedBy: null });
  });

  it('valida con la misma función que la UI y rechaza con ParametrosInvalidosError', async () => {
    const repo = nuevo();

    const promesa = repo.saveParametros([{ clave: UMBRAL, valor: '99' }]);

    await expect(promesa).rejects.toBeInstanceOf(ParametrosInvalidosError);
    await expect(promesa).rejects.toMatchObject({
      errores: [{ clave: UMBRAL, mensaje: 'Debe estar entre 35 y 60 %.' }],
    });
  });

  it('es todo o nada: un cambio inválido en el lote no deja pasar a los válidos', async () => {
    const repo = nuevo();

    await expect(
      repo.saveParametros([
        { clave: UMBRAL, valor: '40' },
        { clave: 'riego.lluvia-probabilidad', valor: '5' },
      ]),
    ).rejects.toBeInstanceOf(ParametrosInvalidosError);

    expect(param(await repo.getCatalogoReglas(), UMBRAL).valor).toBe('45');
  });

  it('rechaza una clave que no existe en el catálogo', async () => {
    await expect(nuevo().saveParametros([{ clave: 'no.existe', valor: '1' }])).rejects.toMatchObject({
      errores: [{ clave: 'no.existe' }],
    });
  });
});

describe('MockRepository · esquema del DAG', () => {
  it('trae start, las 13 reglas con sus parámetros y los terminales por rama', async () => {
    const schema = await nuevo().getRuleSchema();
    const ids = schema.nodes.map((n) => n.id);

    expect(ids).toContain('start');
    for (const r of (fixture as CatalogoReglas).reglas) {
      const nodo = schema.nodes.find((n) => n.id === r.id)!;
      expect(nodo).toMatchObject({ label: r.label, branch: r.rama, priority: r.prioridad });
      expect(nodo.parametros).toEqual(r.parametros);
    }
    for (const rama of ['RIEGO', 'INSUMO', 'MEDIASOMBRA', 'SEGUIMIENTO']) {
      expect(ids).toContain(`success-${rama}`);
      expect(ids).toContain(`abort-${rama}`);
    }
    expect(schema.nodes.find((n) => n.id === 'start')!.parametros).toEqual([]);
  });

  it('encadena cada regla con la siguiente de su rama y cierra con el terminal', async () => {
    const { edges } = await nuevo().getRuleSchema();
    const par = (s: string, t: string) => edges.some((e) => e.source === s && e.target === t);

    expect(par('start', 'ManualLockRule')).toBe(true);
    expect(par('ManualLockRule', 'StaleSensorRule')).toBe(true);
    // La rama de riego, en el orden en que corre el motor.
    expect(par('StaleSensorRule', 'SustratoSaturadoRule')).toBe(true);
    expect(par('SustratoSaturadoRule', 'CicloLecturaRiegoRule')).toBe(true);
    expect(par('CicloLecturaRiegoRule', 'DeficitCriticoRule')).toBe(true);
    expect(par('DeficitCriticoRule', 'FueraDeVentanaRiegoRule')).toBe(true);
    expect(par('FueraDeVentanaRiegoRule', 'PausaTrasAplicacionRule')).toBe(true);
    expect(par('PausaTrasAplicacionRule', 'PosponerPorLluviaRule')).toBe(true);
    expect(par('PosponerPorLluviaRule', 'RiegoPorDeficitRule')).toBe(true);
    expect(par('RiegoPorDeficitRule', 'success-RIEGO')).toBe(true);
    expect(par('FollowUpRule', 'success-SEGUIMIENTO')).toBe(true);
  });
});

describe('MockRepository · traza de evaluación', () => {
  it('es determinística', async () => {
    const repo = nuevo();
    const a = await repo.getTrazaEvaluacion('MZ-1-001', 'TELEMETRIA');
    const b = await repo.getTrazaEvaluacion('MZ-1-001', 'TELEMETRIA');
    expect(a).toEqual(b);
  });

  it('es coherente con la lectura de la zona: humedad < umbral ⇒ CUMPLE', async () => {
    const repo = nuevo();
    const { zonas } = await repo.getNursery();
    let verificadas = 0;

    for (const z of zonas) {
      const raw = z.lectura.metrics.find((m) => m.key === 'humSus')?.raw ?? null;
      const traza = await repo.getTrazaEvaluacion(z.sectors[0].id, 'TELEMETRIA');
      const riego = traza!.reglas.find((r) => r.ruleId === 'RiegoPorDeficitRule')!;
      if (riego.estado !== 'EVALUADA') continue; // la rama puede estar cortada (saturado, ciclo, ventana, lluvia…)

      const c = riego.comparaciones[1];
      expect(c.recibido).toBe(raw);
      expect(c.resultado).toBe(raw !== null && raw < 45 ? 'CUMPLE' : 'NO_CUMPLE');
      verificadas++;
    }
    expect(verificadas).toBeGreaterThan(0);
  });

  it('el barrido no tiene lectura de métricas: la humedad queda SIN_DATO', async () => {
    const traza = await nuevo().getTrazaEvaluacion('MZ-1-001', 'BARRIDO');

    expect(traza!.origen).toBe('BARRIDO');
    const riego = traza!.reglas.find((r) => r.ruleId === 'RiegoPorDeficitRule')!;
    // Con la lectura de 5 min de antigüedad el barrido corta riego antes de llegar a evaluarla.
    expect(riego.estado).toBe('OMITIDA_RAMA_BLOQUEADA');
    expect(traza!.reglas.find((r) => r.ruleId === 'StaleSensorRule')!.acciones[0].tipo).toBe('ABORT_RIEGO');
  });

  it('refleja un umbral editado', async () => {
    const repo = nuevo();
    await repo.saveParametros([{ clave: UMBRAL, valor: '60' }]);
    const { zonas } = await repo.getNursery();

    for (const z of zonas) {
      const riego = (await repo.getTrazaEvaluacion(z.sectors[0].id, 'TELEMETRIA'))!.reglas.find(
        (r) => r.ruleId === 'RiegoPorDeficitRule',
      )!;
      if (riego.estado === 'EVALUADA') expect(riego.comparaciones[1].umbral).toBe(60);
    }
  });

  it('sin origen devuelve la de telemetría', async () => {
    const t = await nuevo().getTrazaEvaluacion('MZ-1-001');
    expect(t!.origen).toBe('TELEMETRIA');
  });

  it('falla con un sector que no existe', async () => {
    await expect(nuevo().getTrazaEvaluacion('NOPE-1')).rejects.toThrow(/NOPE-1/);
  });
});

describe('MockRepository · casos de riego de la demo (13.2)', () => {
  afterEach(() => vi.useRealTimers());

  /** Mediodía del vivero (15:00 UTC = 12:00 UTC-3): dentro de la ventana de riego. */
  const alMediodia = () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-03T15:00:00.000Z'));
    return nuevo();
  };
  const tipos = async (repo: MockRepository, sectorId: string, ruleId: string) =>
    (await repo.getTrazaEvaluacion(sectorId, 'TELEMETRIA'))!.reglas.find((r) => r.ruleId === ruleId)!.acciones.map((a) => a.tipo);

  it('MZ-2 (déficit, sin lluvia): los sectores en cola se evalúan y R-01 ordena regar', async () => {
    const repo = alMediodia();

    expect(await tipos(repo, 'MZ-2-020', 'RiegoPorDeficitRule')).toEqual(['ACTIVAR_VALVULA']);
  });

  it('MZ-2: un sector "Regando" ya regó en este ciclo y la regla de ciclo corta la rama', async () => {
    const repo = alMediodia();
    const traza = (await repo.getTrazaEvaluacion('MZ-2-003', 'TELEMETRIA'))!;

    expect(traza.reglas.find((r) => r.ruleId === 'CicloLecturaRiegoRule')!.acciones.map((a) => a.tipo)).toEqual(['ABORT_RIEGO']);
    expect(traza.reglas.find((r) => r.ruleId === 'RiegoPorDeficitRule')!.estado).toBe('OMITIDA_RAMA_BLOQUEADA');
  });

  it('MZ-3 (déficit con lluvia prevista): R-03 pospone y emite la alerta', async () => {
    const repo = alMediodia();

    expect(await tipos(repo, 'MZ-3-020', 'PosponerPorLluviaRule')).toEqual(['POSTPONE_RIEGO', 'ALERTA']);
  });

  it('MZ-4 (déficit crítico): R-02 riega con el volumen máximo y emite la alerta crítica', async () => {
    const repo = alMediodia();

    expect(await tipos(repo, 'MZ-4-020', 'DeficitCriticoRule')).toEqual(['ACTIVAR_VALVULA', 'ALERTA']);
  });

  it('MZ-4 de noche: R-02 riega igual, la ventana no lo frena', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-03T03:00:00.000Z')); // 00:00 en el vivero
    const repo = nuevo();

    expect(await tipos(repo, 'MZ-4-020', 'DeficitCriticoRule')).toEqual(['ACTIVAR_VALVULA', 'ALERTA']);
  });

  it('MZ-2 de noche: el déficit común lo corta R-05 (ventana horaria, operador EN)', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-03T03:00:00.000Z'));
    const repo = nuevo();
    const traza = (await repo.getTrazaEvaluacion('MZ-2-020', 'TELEMETRIA'))!;
    const r05 = traza.reglas.find((r) => r.ruleId === 'FueraDeVentanaRiegoRule')!;

    expect(r05.acciones.map((a) => a.tipo)).toEqual(['ABORT_RIEGO']);
    const ventana = r05.comparaciones.find((c) => c.operador === 'EN')!;
    // La lectura es de unos minutos antes: pasadas las 23 h.
    expect(ventana.recibido).toMatch(/^23:\d\d$/);
    expect(ventana).toMatchObject({ umbral: '06:00-18:00', resultado: 'NO_CUMPLE' });
  });

  it('MZ-5 (saturado): R-04 bloquea y emite la alerta', async () => {
    const repo = alMediodia();

    expect(await tipos(repo, 'MZ-5-020', 'SustratoSaturadoRule')).toEqual(['ABORT_RIEGO', 'ALERTA']);
  });
});
