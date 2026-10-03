import { describe, expect, it } from 'vitest';
import {
  cambiosDelBorrador,
  editar,
  erroresDelBorrador,
  restablecer,
  sinEnviados,
  valorMostrado,
  type Borrador,
} from './borrador';
import { catalogoDeFabrica, conValor } from './__fixtures__/catalogos';

const UMBRAL = 'riego.umbral-humedad';
const vacio: Borrador = new Map();

describe('borrador indexado por clave', () => {
  it('sin edición muestra el valor vigente', () => {
    const c = catalogoDeFabrica();
    const p = c.parametros.find((x) => x.clave === UMBRAL)!;
    expect(valorMostrado(p, vacio)).toBe('42');
  });

  it('editar guarda el texto por clave y se ve en cualquier aparición', () => {
    const c = catalogoDeFabrica();
    const p = c.parametros.find((x) => x.clave === UMBRAL)!;

    const b = editar(vacio, p, '40');

    expect(valorMostrado(p, b)).toBe('40');
    expect(b.size).toBe(1);
  });

  it('volver al valor vigente borra la edición (no queda sucio)', () => {
    const p = catalogoDeFabrica().parametros.find((x) => x.clave === UMBRAL)!;
    expect(editar(editar(vacio, p, '40'), p, '42').size).toBe(0);
    // Comparación numérica, no de texto: "42.0" es el mismo valor que "42".
    expect(editar(vacio, p, '42.0').size).toBe(0);
  });

  it('editar no muta el borrador anterior', () => {
    const p = catalogoDeFabrica().parametros.find((x) => x.clave === UMBRAL)!;
    const b1 = editar(vacio, p, '40');
    editar(b1, p, '41');
    expect(valorMostrado(p, b1)).toBe('40');
  });
});

describe('cambiosDelBorrador', () => {
  it('manda UN cambio por clave editada, sin importar cuántas reglas la muestren', () => {
    const c = catalogoDeFabrica();
    const p = c.parametros.find((x) => x.clave === UMBRAL)!;
    let b = editar(vacio, p, '40');
    b = editar(b, p, '39'); // segunda edición de la misma clave

    expect(cambiosDelBorrador(c, b)).toEqual([{ clave: UMBRAL, valor: '39' }]);
  });

  it('un parámetro modificado que vuelve a fábrica se manda como valor null', () => {
    const c = conValor(catalogoDeFabrica(), UMBRAL, '40');
    const p = c.parametros.find((x) => x.clave === UMBRAL)!;

    expect(cambiosDelBorrador(c, editar(vacio, p, '42'))).toEqual([{ clave: UMBRAL, valor: null }]);
    expect(cambiosDelBorrador(c, restablecer(vacio, p))).toEqual([{ clave: UMBRAL, valor: null }]);
  });

  it('restablecer un parámetro que ya está en fábrica no genera cambio', () => {
    const c = catalogoDeFabrica();
    const p = c.parametros.find((x) => x.clave === UMBRAL)!;
    expect(cambiosDelBorrador(c, restablecer(vacio, p))).toEqual([]);
  });

  it('restablecer se ve como el valor de fábrica', () => {
    const c = conValor(catalogoDeFabrica(), UMBRAL, '40');
    const p = c.parametros.find((x) => x.clave === UMBRAL)!;
    expect(valorMostrado(p, restablecer(vacio, p))).toBe('42');
  });

  it('un borrador vacío no manda nada', () => {
    expect(cambiosDelBorrador(catalogoDeFabrica(), vacio)).toEqual([]);
  });
});

describe('erroresDelBorrador', () => {
  it('valida cada clave editada con el rango del propio parámetro', () => {
    const c = catalogoDeFabrica();
    const p = c.parametros.find((x) => x.clave === UMBRAL)!;

    const errores = erroresDelBorrador(c, editar(vacio, p, '99'));

    expect(errores.get(UMBRAL)).toBe('Debe estar entre 35 y 60 %.');
  });

  it('sin errores si todo es válido, y restablecer nunca es inválido', () => {
    const c = catalogoDeFabrica();
    const p = c.parametros.find((x) => x.clave === UMBRAL)!;
    expect(erroresDelBorrador(c, editar(vacio, p, '40')).size).toBe(0);
    expect(erroresDelBorrador(c, restablecer(vacio, p)).size).toBe(0);
  });
});

describe('sinEnviados', () => {
  const c = catalogoDeFabrica();
  const umbral = c.parametros.find((x) => x.clave === UMBRAL)!;
  const otro = c.parametros.find((x) => x.clave === 'insumo.max-dosis-24h')!;

  it('quita las claves enviadas cuyo valor no cambió desde el envío', () => {
    const enviado = editar(vacio, umbral, '40');
    expect(sinEnviados(enviado, enviado).size).toBe(0);
  });

  it('conserva lo editado mientras el PUT estaba en vuelo (clave nueva o valor distinto)', () => {
    const enviado = editar(vacio, umbral, '40');
    const actual = editar(editar(enviado, umbral, '45'), otro, '2');

    const resto = sinEnviados(actual, enviado);

    expect([...resto.entries()]).toEqual([
      [UMBRAL, '45'],
      [otro.clave, '2'],
    ]);
  });

  it('un restablecer enviado (null) también se limpia si no cambió', () => {
    const enviado: Borrador = new Map([[UMBRAL, null]]);
    expect(sinEnviados(enviado, enviado).size).toBe(0);
  });
});
