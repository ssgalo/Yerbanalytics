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

describe('agruparPorObjetivo · ubicación de un parámetro compartido', () => {
  const base = catalogoDeFabrica();
  const regla = (id: string, objetivo: ReglaCatalogo['objetivo']): ReglaCatalogo => ({
    ...base.reglas[0],
    id,
    codigo: '',
    label: id,
    objetivo,
    parametros: [],
  });
  const ubicar = (usadoPor: string[], reglas: ReglaCatalogo[]): string | undefined => {
    const parametro = { ...base.parametros[0], clave: 'x.compartido', usadoPor };
    const c = { ...base, reglas, parametros: [parametro] };
    return agruparPorObjetivo(c).find((g) => g.compartidos.some((p) => p.clave === 'x.compartido'))
      ?.objetivo;
  };

  it('gana el grupo con más consumidores con interruptor, aunque el primero en pantalla sea otro', () => {
    // Caso real: diagnostico.confianza-minima y vigencia-horas los usan sobre todo reglas de Sanidad, pero F-S1 es de Riego.
    const reglas = [
      regla('F-S1', 'IRRIGATION'),
      regla('S-A', 'SANITY'),
      regla('S-B', 'SANITY'),
      regla('S-C', 'SANITY'),
    ];
    expect(ubicar(['F-S1', 'S-A', 'S-B', 'S-C'], reglas)).toBe('SANITY');
  });

  it('empate: el primero en el orden de la pantalla', () => {
    const reglas = [regla('s', 'SANITY'), regla('r', 'IRRIGATION')];
    expect(ubicar(['s', 'r'], reglas)).toBe('IRRIGATION');
  });

  it('los consumidores sin interruptor no votan', () => {
    // DespachoRiego (Riego) y una regla de Nutrición: vota sólo la regla.
    expect(ubicar(['DespachoRiego', 'n'], [regla('n', 'NUTRITION')])).toBe('NUTRITION');
    // Dos servicios de Riego no pesan más que una regla de Sanidad.
    expect(ubicar(['DespachoRiego', 'ReadingPurge', 's'], [regla('s', 'SANITY')])).toBe('SANITY');
  });

  it('si ninguno tiene interruptor, el primer grupo en orden donde aparece un consumidor', () => {
    expect(ubicar(['HistorialService', 'DespachoRiego'], [])).toBe('IRRIGATION');
  });
});

describe('agruparPorObjetivo', () => {
  it('agrupa en el orden fijado y no dibuja un grupo sin piezas (Sanidad)', () => {
    const grupos = agruparPorObjetivo(catalogoDeFabrica());

    expect(grupos.map((g) => g.objetivo)).toEqual([
      'IRRIGATION',
      'NUTRITION',
      'SHADE',
      'FOLLOW_UP',
      'PROTECTION',
    ]);
    expect(grupos.map((g) => g.titulo)).toEqual([
      'Riego',
      'Nutrición',
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

  it('un grupo de Sanidad aparece cuando hay una pieza con ese objetivo', () => {
    const c = catalogoDeFabrica();
    const f: ReglaCatalogo = {
      ...c.reglas[0],
      id: 'FungalDamageRule',
      objetivo: 'SANITY',
      efecto: 'ACTS',
      proteccion: null,
    };
    const grupos = agruparPorObjetivo({ ...c, reglas: [...c.reglas, f] });

    expect(grupos.map((g) => g.objetivo)).toContain('SANITY');
    expect(grupos.map((g) => g.objetivo).indexOf('SANITY')).toBe(2);
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

  it('un parámetro compartido se edita una vez, en el grupo con más consumidores con interruptor', () => {
    const c = catalogoDeFabrica();

    // Lo usan R-01, R-03, R-05, R-06 (Riego) y la alerta de falla hidráulica (Protecciones): vive en Riego.
    expect(grupo('IRRIGATION', c)!.compartidos.map((p) => p.clave)).toContain(
      'riego.umbral-humedad',
    );
    expect(grupo('PROTECTION', c)!.compartidos.map((p) => p.clave)).not.toContain(
      'riego.umbral-humedad',
    );
    // Lo lee una protección y el despacho: el despacho no tiene interruptor y no vota, así que va con la protección.
    expect(grupo('PROTECTION', c)!.compartidos.map((p) => p.clave)).toContain(
      'seguridad.antiguedad-max-lectura',
    );
    // Nutrición: la regla de insumo y el servicio de aplicación.
    expect(grupo('NUTRITION', c)!.compartidos.map((p) => p.clave)).toContain(
      'diagnostico.confianza-minima',
    );

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
    const hist = grupo('FOLLOW_UP', c)!.consumidores.find((x) => x.id === 'HistorialService')!;
    expect(hist.parametros.map((p) => p.clave).sort()).toEqual([
      'seguimiento.delta-min',
      'seguimiento.latencia-min',
    ]);
    expect(nombreConsumidor('HistorialService')).toMatch(/^Ejecución/);
    expect(nombreConsumidor('DespachoRiego')).toMatch(/^Ejecución/);
  });

  it('CONSUMIDORES no incluye las piezas que ahora vienen como reglas y cada uno declara su objetivo', () => {
    for (const id of [
      'EscalamientoNodo',
      'ReadingValidationGuard',
      'HardwareFaultService',
      'ApplicationPreconditions',
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
    expect(cambios.flatMap((g) => g.reglas.map((r) => r.id))).toEqual(['RiegoPorDeficitRule']);
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
