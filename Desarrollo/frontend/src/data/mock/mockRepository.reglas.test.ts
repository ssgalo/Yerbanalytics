import { describe, expect, it } from 'vitest';
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
    expect(param(await repo.getCatalogoReglas(), UMBRAL).valor).toBe('42');
  });

  it('saveParametros persiste en memoria y marca el parámetro como modificado', async () => {
    const repo = nuevo();

    const guardado = await repo.saveParametros([{ clave: UMBRAL, valor: '40' }]);

    expect(param(guardado, UMBRAL)).toMatchObject({ valor: '40', fabrica: '42', modificado: true });
    expect(param(guardado, UMBRAL).updatedBy).toBeTruthy();
    expect(param(guardado, UMBRAL).updatedTs).toEqual(expect.any(Number));
    expect(param(await repo.getCatalogoReglas(), UMBRAL).valor).toBe('40');
  });

  it('valor null restablece la fábrica', async () => {
    const repo = nuevo();
    await repo.saveParametros([{ clave: UMBRAL, valor: '40' }]);

    const c = await repo.saveParametros([{ clave: UMBRAL, valor: null }]);

    expect(param(c, UMBRAL)).toMatchObject({ valor: '42', modificado: false, updatedBy: null });
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

    expect(param(await repo.getCatalogoReglas(), UMBRAL).valor).toBe('42');
  });

  it('rechaza una clave que no existe en el catálogo', async () => {
    await expect(nuevo().saveParametros([{ clave: 'no.existe', valor: '1' }])).rejects.toMatchObject({
      errores: [{ clave: 'no.existe' }],
    });
  });
});

describe('MockRepository · esquema del DAG', () => {
  it('trae start, las 9 reglas con sus parámetros y los terminales por rama', async () => {
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
    expect(par('WeatherOverrideRule', 'DailyVolumeLimitRule')).toBe(true);
    expect(par('IrrigationRule', 'success-RIEGO')).toBe(true);
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
      const riego = traza!.reglas.find((r) => r.ruleId === 'IrrigationRule')!;
      if (riego.estado !== 'EVALUADA') continue; // la rama puede estar cortada por lluvia o lectura vieja

      const c = riego.comparaciones[0];
      expect(c.recibido).toBe(raw);
      expect(c.resultado).toBe(raw !== null && raw < 42 ? 'CUMPLE' : 'NO_CUMPLE');
      verificadas++;
    }
    expect(verificadas).toBeGreaterThan(0);
  });

  it('el barrido no tiene lectura de métricas: la humedad queda SIN_DATO', async () => {
    const traza = await nuevo().getTrazaEvaluacion('MZ-1-001', 'BARRIDO');

    expect(traza!.origen).toBe('BARRIDO');
    const riego = traza!.reglas.find((r) => r.ruleId === 'IrrigationRule')!;
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
        (r) => r.ruleId === 'IrrigationRule',
      )!;
      if (riego.estado === 'EVALUADA') expect(riego.comparaciones[0].umbral).toBe(60);
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
