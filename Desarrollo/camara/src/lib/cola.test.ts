import { describe, expect, it } from 'vitest';
import { ColaOrdenes, esperaBackoff } from './cola';
import { dimensiones } from './captura';
import type { Orden } from './contrato';

const orden = (id: string, intento = 1): Orden => ({
  ordenId: id,
  sectorId: 'MZ-1-001',
  zonaId: 'MZ-1',
  posicionRiel: 100,
  emitidaEn: 0,
  venceEn: 0,
  intento,
});

describe('ColaOrdenes', () => {
  it('respeta el orden de llegada', () => {
    const cola = new ColaOrdenes(5);
    cola.encolar(orden('a'));
    cola.encolar(orden('b'));

    expect(cola.desencolar()?.ordenId).toBe('a');
    expect(cola.desencolar()?.ordenId).toBe('b');
    expect(cola.desencolar()).toBeUndefined();
  });

  it('encola en vez de descartar mientras haya lugar', () => {
    const cola = new ColaOrdenes(3);
    cola.encolar(orden('a'));
    const r = cola.encolar(orden('b'));

    expect(r.descartada).toBeUndefined();
    expect(cola.largo).toBe(2);
  });

  it('al llenarse descarta la más antigua y la devuelve para acusarla', () => {
    // El contrato prohíbe descartar en silencio: si el backend no se entera, la orden queda
    // esperando su vencimiento en lugar de reintentarse enseguida.
    const cola = new ColaOrdenes(2);
    cola.encolar(orden('vieja'));
    cola.encolar(orden('media'));

    const r = cola.encolar(orden('nueva'));

    expect(r.descartada?.ordenId).toBe('vieja');
    expect(cola.largo).toBe(2);
  });

  it('una reentrega del mismo ordenId reemplaza en lugar de duplicar', () => {
    const cola = new ColaOrdenes(5);
    cola.encolar(orden('a', 1));

    const r = cola.encolar(orden('a', 2));

    expect(r.descartada).toBeUndefined();
    expect(cola.largo).toBe(1);
    expect(cola.desencolar()?.intento).toBe(2);
  });

  it('el tope se puede ajustar cuando baja la config del backend', () => {
    const cola = new ColaOrdenes(10);
    cola.setTope(1);
    cola.encolar(orden('a'));

    expect(cola.encolar(orden('b')).descartada?.ordenId).toBe('a');
  });
});

describe('esperaBackoff', () => {
  it('crece con cada intento', () => {
    const primero = esperaBackoff(1, 1000, 30_000);
    const cuarto = esperaBackoff(4, 1000, 30_000);

    expect(cuarto).toBeGreaterThan(primero);
  });

  it('no supera el tope', () => {
    for (let i = 1; i <= 20; i++) {
      expect(esperaBackoff(i, 1000, 30_000)).toBeLessThanOrEqual(30_000 * 1.2);
    }
  });

  it('aplica jitter, para que varios dispositivos no reintenten en sincronía', () => {
    const muestras = new Set(Array.from({ length: 30 }, () => esperaBackoff(3)));

    expect(muestras.size).toBeGreaterThan(1);
  });
});

describe('dimensiones', () => {
  it('achica preservando la relación de aspecto', () => {
    expect(dimensiones(4032, 3024, 1920, 1080)).toEqual({ ancho: 1440, alto: 1080 });
  });

  it('no agranda una imagen más chica que el máximo', () => {
    expect(dimensiones(640, 480, 1920, 1080)).toEqual({ ancho: 640, alto: 480 });
  });

  it('tolera un video sin dimensiones todavía', () => {
    expect(dimensiones(0, 0, 1920, 1080)).toEqual({ ancho: 1920, alto: 1080 });
  });

  it('no tira resolución cuando el video viene en vertical', () => {
    // El caso normal de un teléfono montado en el riel. Comparando ancho con ancho y alto
    // con alto, un 1080x1920 contra un máximo de 1920x1080 se reduciría a 810x1080: se
    // perdería más de la mitad de los píxeles por una diferencia de orientación.
    expect(dimensiones(1080, 1920, 1920, 1080)).toEqual({ ancho: 1080, alto: 1920 });
  });

  it('aplica el límite igual en vertical que en apaisado', () => {
    const apaisado = dimensiones(4032, 3024, 1920, 1080);
    const vertical = dimensiones(3024, 4032, 1920, 1080);

    expect(vertical).toEqual({ ancho: apaisado.alto, alto: apaisado.ancho });
  });
});
