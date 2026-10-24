import { describe, expect, it } from 'vitest';
import {
  agruparPorObjetivo,
  codigoVisible,
  CONSUMIDORES,
  estadoGrupo,
  NOMBRE_OBJETIVO,
  nombreInterno,
  ORDEN_OBJETIVOS,
  parametrosDe,
} from './catalogoView';
import { catalogoDeFabrica } from './__fixtures__/catalogos';
import type { ReglaCatalogo } from '@/types/domain';

const grupo = (objetivo: string, c = catalogoDeFabrica()) =>
  agruparPorObjetivo(c).find((g) => g.objetivo === objetivo);

describe('agruparPorObjetivo', () => {
  it('agrupa en el orden fijado, con Sanidad entre Nutrición y Mediasombra', () => {
    const grupos = agruparPorObjetivo(catalogoDeFabrica());

    expect(grupos.map((g) => g.objetivo)).toEqual(ORDEN_OBJETIVOS);
    expect(grupos.map((g) => g.titulo)).toEqual([
      'Riego',
      'Nutrición',
      'Sanidad',
      'Mediasombra',
      'Seguimiento',
      'Protecciones',
    ]);
    expect(NOMBRE_OBJETIVO.SANITY).toBe('Sanidad');
  });

  it('un grupo sin piezas ni ejecución no se dibuja (Sanidad sin sus reglas)', () => {
    const c = catalogoDeFabrica();
    const grupos = agruparPorObjetivo({ ...c, reglas: c.reglas.filter((r) => r.objetivo !== 'SANITY') });
    expect(grupos.map((g) => g.objetivo)).not.toContain('SANITY');
  });

  it('Sanidad trae las reglas bióticas y el estrés solar sin radiación; Seguimiento, la evaluación de efectividad', () => {
    expect(grupo('SANITY')!.reglas.map((r) => r.id).sort()).toEqual([
      'FoliarPestRule',
      'FungalConditionRule',
      'FungalDamageRule',
      'OutbreakRule',
      'SolarStressUnexplainedRule',
    ]);
    expect(grupo('FOLLOW_UP')!.reglas.map((r) => r.id)).toEqual(['EffectivenessService']);
    expect(grupo('PROTECTION')!.reglas.map((r) => r.id)).toContain('IneffectiveActionRule');
  });

  it('ordena por código natural (primer código si es compuesto) y deja sin código al final por nombre', () => {
    const c = catalogoDeFabrica();
    const mk = (id: string, codigo: string, label: string, prioridad: number): ReglaCatalogo => ({
      ...c.reglas[0],
      id,
      codigo,
      label,
      prioridad,
      objetivo: 'IRRIGATION',
    });
    const reglas = [
      mk('a', '', 'Zeta', 1),
      mk('b', 'R-10', 'B', 2),
      mk('c', 'R-02', 'C', 3),
      mk('d', 'R-01, R-04', 'D', 4),
      mk('e', '', 'Álamo', 5),
    ];
    expect(grupo('IRRIGATION', { ...c, reglas })!.reglas.map((r) => r.id)).toEqual(['d', 'c', 'b', 'e', 'a']);
  });

  it('deja afuera las eliminadas', () => {
    const c = catalogoDeFabrica();
    c.reglas.find((r) => r.id === 'PosponerPorLluviaRule')!.eliminada = true;
    expect(grupo('IRRIGATION', c)!.reglas.map((r) => r.id)).not.toContain('PosponerPorLluviaRule');
  });

  it('las filas de ejecución traen sólo las copias propias del servicio, en el grupo que le toca', () => {
    const despacho = grupo('IRRIGATION')!.ejecucion.find((x) => x.id === 'DespachoRiego')!;
    expect(despacho.titulo).toBe(CONSUMIDORES.DespachoRiego.titulo);
    // Lo que revalida (lluvia, saturación, ventana…) lo lee de la copia de la regla que decidió: no es suyo.
    expect(despacho.parametros.map((p) => p.clave)).toEqual(['riego.sectores-simultaneos']);
    expect(grupo('PROTECTION')!.ejecucion.map((x) => x.id).sort()).toEqual(['PulsoSensado', 'ReadingPurge']);
    expect(grupo('NUTRITION')!.ejecucion.map((x) => x.id)).toEqual(
      expect.arrayContaining(['ApplicationService', 'TankService']),
    );
  });

  it('CONSUMIDORES no incluye piezas con interruptor y cada uno declara su objetivo', () => {
    const ids = new Set(catalogoDeFabrica().reglas.map((r) => r.id));
    for (const id of Object.keys(CONSUMIDORES)) expect(ids.has(id)).toBe(false);
    for (const c of Object.values(CONSUMIDORES)) expect(ORDEN_OBJETIVOS).toContain(c.objetivo);
  });
});

describe('parametrosDe', () => {
  it('cada pieza tiene su copia de una clave que leen varias', () => {
    const c = catalogoDeFabrica();
    const r01 = parametrosDe(c, 'RiegoPorDeficitRule').find((p) => p.clave === 'riego.umbral-humedad')!;
    const r03 = parametrosDe(c, 'PosponerPorLluviaRule').find((p) => p.clave === 'riego.umbral-humedad')!;
    expect(r01).not.toBe(r03);
    expect([r01.regla, r03.regla]).toEqual(['RiegoPorDeficitRule', 'PosponerPorLluviaRule']);
  });

  it('coincide con las claves que la pieza declara', () => {
    const c = catalogoDeFabrica();
    for (const r of c.reglas) expect(parametrosDe(c, r.id).map((p) => p.clave)).toEqual(r.parametros);
  });
});

describe('nombres y estado del grupo', () => {
  it('entre paréntesis va el código o, si no tiene, el nombre interno sin "Rule"', () => {
    expect(codigoVisible({ id: 'RiegoPorDeficitRule', codigo: 'R-01' })).toBe('R-01');
    expect(codigoVisible({ id: 'ShadingRule', codigo: '' })).toBe('Shading');
    expect(nombreInterno('HorarioMediasombraService')).toBe('HorarioMediasombraService');
  });

  it('el interruptor del grupo está prendido, apagado o a medio camino', () => {
    const [a, b] = catalogoDeFabrica().reglas;
    expect(estadoGrupo([{ ...a, activa: true }, { ...b, activa: true }])).toBe(true);
    expect(estadoGrupo([{ ...a, activa: false }, { ...b, activa: false }])).toBe(false);
    expect(estadoGrupo([{ ...a, activa: true }, { ...b, activa: false }])).toBe('mixed');
    expect(estadoGrupo([])).toBe(false);
  });
});
