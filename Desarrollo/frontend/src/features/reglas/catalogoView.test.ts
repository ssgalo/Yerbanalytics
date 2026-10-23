import { describe, expect, it } from 'vitest';
import {
  agruparPorObjetivo,
  CONSUMIDORES,
  nombreConsumidor,
  NOMBRE_OBJETIVO,
  ORDEN_OBJETIVOS,
  SIN_FILTROS,
  indicePorClave,
} from './catalogoView';
import { catalogoDeFabrica, conValor } from './__fixtures__/catalogos';
import type { ReglaCatalogo } from '@/types/domain';

const grupo = (
  objetivo: string,
  c = catalogoDeFabrica(),
  f = SIN_FILTROS,
  editadas = new Set<string>(),
) => agruparPorObjetivo(c, f, editadas).find((g) => g.objetivo === objetivo);

describe('agruparPorObjetivo · orden de las piezas', () => {
  it('ordena por código natural (primer código si es compuesto) y deja sin código al final por label', () => {
    const c = catalogoDeFabrica();
    const base = c.reglas[0];
    const mk = (id: string, codigo: string, label: string, prioridad: number): ReglaCatalogo => ({
      ...base,
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
    const g = agruparPorObjetivo({ ...c, reglas }).find((x) => x.objetivo === 'IRRIGATION');
    expect(g?.reglas.map((r) => r.id)).toEqual(['d', 'c', 'b', 'e', 'a']);
    const p = agruparPorObjetivo({ ...c, reglas }, SIN_FILTROS, new Set(), 'prioridad').find(
      (x) => x.objetivo === 'IRRIGATION',
    );
    expect(p?.reglas.map((r) => r.id)).toEqual(['a', 'b', 'c', 'd', 'e']);
  });
});

describe('agruparPorObjetivo', () => {
  it('agrupa en el orden fijado, con Sanidad entre Nutrición y Mediasombra', () => {
    const grupos = agruparPorObjetivo(catalogoDeFabrica());

    expect(grupos.map((g) => g.objetivo)).toEqual([
      'IRRIGATION',
      'NUTRITION',
      'SANITY',
      'SHADE',
      'FOLLOW_UP',
      'PROTECTION',
    ]);
    expect(grupos.map((g) => g.titulo)).toEqual([
      'Riego',
      'Nutrición',
      'Sanidad',
      'Mediasombra',
      'Seguimiento',
      'Protecciones',
    ]);
    expect(ORDEN_OBJETIVOS).toEqual([
      'IRRIGATION',
      'NUTRITION',
      'SANITY',
      'SHADE',
      'FOLLOW_UP',
      'PROTECTION',
    ]);
    expect(NOMBRE_OBJETIVO.SANITY).toBe('Sanidad');
  });

  it('un grupo sin piezas no se dibuja (Sanidad sin sus reglas)', () => {
    const c = catalogoDeFabrica();
    const grupos = agruparPorObjetivo({
      ...c,
      reglas: c.reglas.filter((r) => r.objetivo !== 'SANITY'),
    });

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
    // S-06 es una protección: vive en Protecciones aunque sea de la rama de riego.
    expect(grupo('PROTECTION')!.reglas.map((r) => r.id)).toContain('IneffectiveActionRule');
  });

  it('dentro de cada grupo, por prioridad; R-04 (protege) está en Riego y no en Protecciones', () => {
    const riego = agruparPorObjetivo(catalogoDeFabrica(), SIN_FILTROS, new Set(), 'prioridad').find(
      (g) => g.objetivo === 'IRRIGATION',
    )!;

    expect(riego.reglas.map((r) => r.id)).toEqual([
      'SustratoSaturadoRule',
      'CicloLecturaRiegoRule',
      'DeficitCriticoRule',
      'FueraDeVentanaRiegoRule',
      'PausaTrasAplicacionRule',
      'PosponerPorLluviaRule',
      'RiegoPorDeficitRule',
      'SolarStressRule',
    ]);
    expect(riego.reglas[0].efecto).toBe('PROTECTS');
    expect(grupo('PROTECTION')!.reglas.map((r) => r.id)).not.toContain('SustratoSaturadoRule');
    expect(grupo('PROTECTION')!.reglas.map((r) => r.id)).toEqual(
      expect.arrayContaining([
        'ManualLockRule',
        'EscalamientoNodo',
        'ReadingValidationGuard',
        'HardwareFaultService',
      ]),
    );
  });

  it('cuenta las apagadas de cada grupo', () => {
    const c = catalogoDeFabrica();
    c.reglas.find((r) => r.id === 'RiegoPorDeficitRule')!.activa = false;

    expect(grupo('IRRIGATION', c)!.apagadas).toBe(1);
    // Las retiradas de fábrica también cuentan como apagadas.
    expect(grupo('NUTRITION', c)!.apagadas).toBe(2);
  });

  it('un parámetro compartido se edita una vez: en el primer grupo, en el orden, donde tiene un consumidor', () => {
    const c = catalogoDeFabrica();

    // Lo usan R-01, R-03, R-05, R-06 (Riego) y la alerta de falla hidráulica (Protecciones): vive en Riego.
    expect(grupo('IRRIGATION', c)!.compartidos.map((p) => p.clave)).toContain(
      'riego.umbral-humedad',
    );
    expect(grupo('PROTECTION', c)!.compartidos.map((p) => p.clave)).not.toContain(
      'riego.umbral-humedad',
    );
    // Lo lee una protección y el despacho: gana Riego, que va antes.
    expect(grupo('IRRIGATION', c)!.compartidos.map((p) => p.clave)).toContain(
      'seguridad.antiguedad-max-lectura',
    );
    // El diagnóstico de IA lo leen la dosificación y las reglas F-*: gana Riego (F-S1), que va antes.
    for (const clave of ['diagnostico.confianza-minima', 'diagnostico.vigencia-horas']) {
      expect(grupo('IRRIGATION', c)!.compartidos.map((p) => p.clave)).toContain(clave);
      expect(grupo('NUTRITION', c)!.compartidos.map((p) => p.clave)).not.toContain(clave);
    }

    const todos = agruparPorObjetivo(c).flatMap((g) => g.compartidos.map((p) => p.clave));
    expect(new Set(todos).size).toBe(todos.length);
    expect(todos.sort()).toEqual(
      c.parametros
        .filter((p) => p.usadoPor.length > 1)
        .map((p) => p.clave)
        .sort(),
    );
  });

  it('los consumidores sin interruptor se rotulan "Ejecución" y sólo traen lo que ninguna pieza usa', () => {
    const c = catalogoDeFabrica();

    const despacho = grupo('IRRIGATION', c)!.consumidores.find((x) => x.id === 'DespachoRiego')!;
    expect(despacho.parametros.map((p) => p.clave)).toEqual(['riego.sectores-simultaneos']);
    const purga = grupo('PROTECTION', c)!.consumidores.find((x) => x.id === 'ReadingPurge')!;
    expect(purga.parametros.map((p) => p.clave)).toEqual(['readings.retention-days']);
    expect(nombreConsumidor('ReadingPurge')).toMatch(/^Ejecución/);
    expect(nombreConsumidor('DespachoRiego')).toMatch(/^Ejecución/);
  });

  it('CONSUMIDORES no incluye las piezas que ahora vienen como reglas y cada uno declara su objetivo', () => {
    for (const id of [
      'EscalamientoNodo',
      'ReadingValidationGuard',
      'HardwareFaultService',
      'ApplicationPreconditions',
      'EffectivenessService',
      'HistorialService',
    ]) {
      expect(CONSUMIDORES[id]).toBeUndefined();
    }
    for (const c of Object.values(CONSUMIDORES)) expect(ORDEN_OBJETIVOS).toContain(c.objetivo);
  });

  it('filtra por Apagadas, Protecciones y Con cambios', () => {
    const c = catalogoDeFabrica();
    c.reglas.find((r) => r.id === 'SustratoSaturadoRule')!.activa = false;

    const apagadas = agruparPorObjetivo(c, { ...SIN_FILTROS, soloApagadas: true });
    expect(apagadas.flatMap((g) => g.reglas.map((r) => r.id)).sort()).toEqual([
      'DailyDoseLimitRule',
      'SupplyRule',
      'SustratoSaturadoRule',
    ]);
    expect(apagadas.flatMap((g) => g.compartidos)).toEqual([]);

    const protecciones = agruparPorObjetivo(c, { ...SIN_FILTROS, soloProtecciones: true });
    expect(protecciones.flatMap((g) => g.reglas).every((r) => r.efecto === 'PROTECTS')).toBe(true);
    expect(protecciones.flatMap((g) => g.reglas.map((r) => r.id))).toContain(
      'SustratoSaturadoRule',
    );

    const cambios = agruparPorObjetivo(
      c,
      { ...SIN_FILTROS, soloConCambios: true },
      new Set(['riego.humedad-objetivo']),
    );
    // La humedad objetivo la usan R-01 y F-S1 (estrés solar): las dos aparecen, ambas en Riego.
    expect(cambios.flatMap((g) => g.reglas.map((r) => r.id)).sort()).toEqual([
      'RiegoPorDeficitRule',
      'SolarStressRule',
    ]);
    expect(cambios).toHaveLength(1);
  });

  it('busca por nombre, código o parámetro, sin tildes ni mayúsculas', () => {
    const c = catalogoDeFabrica();
    const ids = (busqueda: string) =>
      agruparPorObjetivo(c, { ...SIN_FILTROS, busqueda }).flatMap((g) => g.reglas.map((r) => r.id));

    expect(ids('SATURACION')).toContain('SustratoSaturadoRule');
    expect(ids('r-04')).toEqual(['SustratoSaturadoRule']);
    expect(ids('uv-umbral')).toEqual(['ShadingRule']);
  });

  it('el índice por clave sigue sirviendo para resolver los parámetros de una pieza', () => {
    const c = conValor(catalogoDeFabrica(), 'riego.umbral-humedad', '40');
    expect(indicePorClave(c).get('riego.umbral-humedad')!.modificado).toBe(true);
  });
});
