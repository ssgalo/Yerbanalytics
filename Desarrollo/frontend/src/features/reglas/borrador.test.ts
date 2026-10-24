import { describe, expect, it } from 'vitest';
import {
  cambiosDelBorrador,
  editar,
  erroresDelBorrador,
  idCopia,
  sinEnviados,
  sinRegla,
  valorMostrado,
  type Borrador,
} from './borrador';
import { catalogoDeFabrica, conValor, copia } from './__fixtures__/catalogos';

const UMBRAL = 'riego.umbral-humedad';
const R01 = 'RiegoPorDeficitRule';
const R03 = 'PosponerPorLluviaRule';
const vacio: Borrador = new Map();

describe('borrador indexado por copia (regla, clave)', () => {
  it('sin edición muestra el valor vigente', () => {
    expect(valorMostrado(copia(catalogoDeFabrica(), R01, UMBRAL), vacio)).toBe('45');
  });

  it('editar la copia de una regla no cambia la de otra con la misma clave', () => {
    const c = catalogoDeFabrica();
    const b = editar(vacio, copia(c, R01, UMBRAL), '40');

    expect(valorMostrado(copia(c, R01, UMBRAL), b)).toBe('40');
    expect(valorMostrado(copia(c, R03, UMBRAL), b)).toBe('45');
    expect([...b.keys()]).toEqual([`${R01}|${UMBRAL}`]);
  });

  it('volver al valor vigente borra la edición (no queda sucio)', () => {
    const p = copia(catalogoDeFabrica(), R01, UMBRAL);
    expect(editar(editar(vacio, p, '40'), p, '45').size).toBe(0);
    // Comparación numérica, no de texto: "45.0" es el mismo valor que "45".
    expect(editar(vacio, p, '45.0').size).toBe(0);
  });

  it('editar no muta el borrador anterior', () => {
    const p = copia(catalogoDeFabrica(), R01, UMBRAL);
    const b1 = editar(vacio, p, '40');
    editar(b1, p, '41');
    expect(valorMostrado(p, b1)).toBe('40');
  });

  it('sinRegla descarta sólo las ediciones de esa pieza', () => {
    const c = catalogoDeFabrica();
    const b = editar(editar(vacio, copia(c, R01, UMBRAL), '40'), copia(c, R03, UMBRAL), '50');
    expect([...sinRegla(b, R01).keys()]).toEqual([idCopia(copia(c, R03, UMBRAL))]);
    expect(sinRegla(b, 'OtraRule')).toBe(b);
  });
});

describe('cambiosDelBorrador', () => {
  it('manda un cambio por copia editada, con su regla', () => {
    const c = catalogoDeFabrica();
    let b = editar(vacio, copia(c, R01, UMBRAL), '40');
    b = editar(b, copia(c, R01, UMBRAL), '39');
    b = editar(b, copia(c, R03, UMBRAL), '50');

    expect(cambiosDelBorrador(c, b)).toEqual([
      { regla: R03, clave: UMBRAL, valor: '50' },
      { regla: R01, clave: UMBRAL, valor: '39' },
    ]);
  });

  it('una copia modificada que vuelve a fábrica se manda como valor null', () => {
    const c = conValor(catalogoDeFabrica(), R01, UMBRAL, '40');
    expect(cambiosDelBorrador(c, editar(vacio, copia(c, R01, UMBRAL), '45'))).toEqual([
      { regla: R01, clave: UMBRAL, valor: null },
    ]);
  });

  it('un borrador vacío no manda nada', () => {
    expect(cambiosDelBorrador(catalogoDeFabrica(), vacio)).toEqual([]);
  });
});

describe('erroresDelBorrador', () => {
  it('valida cada copia editada con el rango del parámetro', () => {
    const c = catalogoDeFabrica();
    const p = copia(c, R01, UMBRAL);

    const errores = erroresDelBorrador(c, editar(vacio, p, '99'));

    expect(errores.get(idCopia(p))).toBe('Debe estar entre 35 y 60 %.');
    expect(errores.size).toBe(1);
  });

  it('sin errores si todo es válido', () => {
    const c = catalogoDeFabrica();
    expect(erroresDelBorrador(c, editar(vacio, copia(c, R01, UMBRAL), '40')).size).toBe(0);
  });
});

describe('sinEnviados', () => {
  const c = catalogoDeFabrica();
  const umbral = copia(c, R01, UMBRAL);
  const otro = copia(c, R03, UMBRAL);

  it('quita las copias enviadas cuyo valor no cambió desde el envío', () => {
    const enviado = editar(vacio, umbral, '40');
    expect(sinEnviados(enviado, enviado).size).toBe(0);
  });

  it('conserva lo editado mientras el PUT estaba en vuelo (copia nueva o valor distinto)', () => {
    const enviado = editar(vacio, umbral, '40');
    const actual = editar(editar(enviado, umbral, '38'), otro, '50');

    expect([...sinEnviados(actual, enviado).entries()]).toEqual([
      [idCopia(umbral), '38'],
      [idCopia(otro), '50'],
    ]);
  });
});
