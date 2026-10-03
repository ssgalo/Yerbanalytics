import { describe, expect, it } from 'vitest';
import {
  agruparPorConsumidor,
  agruparPorRama,
  filtrarParametros,
  filtrarReglas,
  indicePorClave,
  textoResumen,
  resumenRegla,
} from './catalogoView';
import { catalogoDeFabrica, conValor } from './__fixtures__/catalogos';

describe('agruparPorRama', () => {
  it('agrupa por rama en el orden del motor y cada rama por prioridad', () => {
    const grupos = agruparPorRama(catalogoDeFabrica().reglas);

    expect(grupos.map((g) => g.rama)).toEqual(['GLOBAL', 'RIEGO', 'INSUMO', 'MEDIASOMBRA', 'SEGUIMIENTO']);
    // El orden en que corre el motor: ciclo → R-04 → R-02 → R-05 → R-06 → R-03 → R-01.
    expect(grupos[1].reglas.map((r) => r.id)).toEqual([
      'CicloLecturaRiegoRule',
      'SustratoSaturadoRule',
      'DeficitCriticoRule',
      'FueraDeVentanaRiegoRule',
      'PausaTrasAplicacionRule',
      'PosponerPorLluviaRule',
      'RiegoPorDeficitRule',
    ]);
  });

  it('omite las ramas sin reglas y escala a decenas de reglas', () => {
    const reglas = Array.from({ length: 40 }, (_, i) => ({
      id: `R${i}`,
      label: `Regla ${i}`,
      rama: 'RIEGO' as const,
      prioridad: 40 - i,
      parametros: [],
    }));

    const grupos = agruparPorRama(reglas);

    expect(grupos).toHaveLength(1);
    expect(grupos[0].reglas[0].id).toBe('R39');
    expect(grupos[0].reglas).toHaveLength(40);
  });
});

describe('resumen por regla', () => {
  it('cuenta parámetros y modificados', () => {
    const c = conValor(catalogoDeFabrica(), 'riego.umbral-humedad', '40');
    const riego = c.reglas.find((r) => r.id === 'RiegoPorDeficitRule')!;

    expect(resumenRegla(riego, indicePorClave(c))).toEqual({ total: 6, modificados: 1 });
  });

  it('el texto sigue el formato "N parámetros · M modificados"', () => {
    expect(textoResumen({ total: 3, modificados: 1 })).toBe('3 parámetros · 1 modificado');
    expect(textoResumen({ total: 3, modificados: 0 })).toBe('3 parámetros · 0 modificados');
    expect(textoResumen({ total: 1, modificados: 0 })).toBe('1 parámetro · 0 modificados');
    expect(textoResumen({ total: 2, modificados: 2 })).toBe('2 parámetros · 2 modificados');
  });

  it('una regla sin parámetros lo dice', () => {
    expect(textoResumen({ total: 0, modificados: 0 })).toBe('Sin parámetros configurables');
  });
});

describe('filtrarReglas', () => {
  const sinFiltros = { busqueda: '', rama: 'TODAS' as const, soloModificados: false };

  it('sin filtros devuelve todas', () => {
    const c = catalogoDeFabrica();
    expect(filtrarReglas(c, sinFiltros)).toHaveLength(13);
  });

  it('busca por nombre de regla o de alguno de sus parámetros, sin importar tildes ni mayúsculas', () => {
    const c = catalogoDeFabrica();

    // "lluvia": R-03 por su nombre y sus parámetros; R-02 también la nombra en el umbral crítico que comparte.
    expect(filtrarReglas(c, { ...sinFiltros, busqueda: 'lluvia' }).map((r) => r.id)).toContain('PosponerPorLluviaRule');
    // Sin tildes ni mayúsculas: "SATURACION" encuentra "Saturación".
    expect(filtrarReglas(c, { ...sinFiltros, busqueda: 'SATURACION' }).map((r) => r.id)).toEqual(['SustratoSaturadoRule']);
    // Una regla se encuentra por el nombre de uno de sus parámetros ("Ventana horaria…" es de R-05).
    expect(filtrarReglas(c, { ...sinFiltros, busqueda: 'ventana horaria' }).map((r) => r.id)).toContain('FueraDeVentanaRiegoRule');
  });

  it('busca también por la clave del parámetro', () => {
    const c = catalogoDeFabrica();
    expect(filtrarReglas(c, { ...sinFiltros, busqueda: 'uv-umbral' }).map((r) => r.id)).toEqual(['ShadingRule']);
  });

  it('filtra por rama', () => {
    const c = catalogoDeFabrica();
    expect(filtrarReglas(c, { ...sinFiltros, rama: 'INSUMO' }).map((r) => r.id)).toEqual([
      'DailyDoseLimitRule',
      'SupplyRule',
    ]);
  });

  it('"sólo modificados" deja las reglas con algún parámetro editado', () => {
    const c = conValor(catalogoDeFabrica(), 'insumo.max-dosis-24h', '2');

    expect(filtrarReglas(c, { ...sinFiltros, soloModificados: true }).map((r) => r.id)).toEqual([
      'DailyDoseLimitRule',
    ]);
  });

  it('una regla sin parámetros no aparece al buscar un parámetro, pero sí por su nombre', () => {
    const c = catalogoDeFabrica();
    expect(filtrarReglas(c, { ...sinFiltros, busqueda: 'seguimiento' }).map((r) => r.id)).toEqual(['FollowUpRule']);
  });
});

describe('filtrarParametros (vista por parámetro)', () => {
  const sinFiltros = { busqueda: '', rama: 'TODAS' as const, soloModificados: false };

  it('lista cada parámetro una sola vez, aunque lo usen varias reglas', () => {
    const c = catalogoDeFabrica();

    const lista = filtrarParametros(c, sinFiltros);

    const claves = lista.map((p) => p.clave);
    expect(new Set(claves).size).toBe(claves.length);
    // El umbral de riego lo usan cuatro reglas y aparece una sola vez, en el orden en que corre el motor.
    expect(lista.find((p) => p.clave === 'riego.umbral-humedad')!.usadoPor).toEqual([
      'FueraDeVentanaRiegoRule',
      'PausaTrasAplicacionRule',
      'PosponerPorLluviaRule',
      'RiegoPorDeficitRule',
    ]);
  });

  it('el consumidor que no es una regla (el despacho) cuenta como de la rama de riego', () => {
    const c = catalogoDeFabrica();

    const enRiego = filtrarParametros(c, { ...sinFiltros, rama: 'RIEGO' }).map((p) => p.clave);
    const enInsumo = filtrarParametros(c, { ...sinFiltros, rama: 'INSUMO' }).map((p) => p.clave);

    expect(enRiego).toContain('riego.sectores-simultaneos');
    expect(enInsumo).not.toContain('riego.sectores-simultaneos');
    expect(filtrarParametros(c, { ...sinFiltros, busqueda: 'ejecución del riego' }).map((p) => p.clave)).toEqual([
      'riego.sectores-simultaneos',
    ]);
  });

  it('el filtro por rama deja los parámetros que usa alguna regla de esa rama', () => {
    const c = catalogoDeFabrica();
    const claves = filtrarParametros(c, { ...sinFiltros, rama: 'MEDIASOMBRA' }).map((p) => p.clave);
    expect(claves).toHaveLength(3);
    expect(claves.every((k) => k.startsWith('mediasombra.'))).toBe(true);
  });

  it('busca por nombre del parámetro o de una regla que lo usa', () => {
    const c = catalogoDeFabrica();
    expect(filtrarParametros(c, { ...sinFiltros, busqueda: 'confianza' }).map((p) => p.clave)).toEqual([
      'diagnostico.confianza-minima',
    ]);
    expect(filtrarParametros(c, { ...sinFiltros, busqueda: 'SupplyRule' }).map((p) => p.clave)).toEqual([
      'diagnostico.confianza-minima',
    ]);
  });
});

describe('agruparPorConsumidor', () => {
  it('agrupa los parámetros que lee algo que no es una regla bajo "Ejecución del riego"', () => {
    const grupos = agruparPorConsumidor(catalogoDeFabrica());

    expect(grupos).toHaveLength(1);
    expect(grupos[0]).toMatchObject({ id: 'DespachoRiego', titulo: 'Ejecución del riego' });
    expect(grupos[0].parametros.map((p) => p.clave)).toEqual(['riego.sectores-simultaneos']);
  });

  it('un parámetro que usan sólo reglas no genera grupo', () => {
    const c = catalogoDeFabrica();
    c.parametros = c.parametros.filter((p) => p.clave !== 'riego.sectores-simultaneos');

    expect(agruparPorConsumidor(c)).toEqual([]);
  });
});
