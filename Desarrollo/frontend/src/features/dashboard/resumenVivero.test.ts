import { describe, expect, it } from 'vitest';
import type { Sector, Status, Zona } from '@/types/domain';
import {
  columnasGrilla,
  contadoresZona,
  estadoZona,
  pluralizar,
  resumenVivero,
  resumenZona,
  textoPlantines,
} from './resumenVivero';

/** Zona mínima: sólo lo que miran las funciones bajo prueba. */
function zona(estados: Status[], stale = false, id = 'MZ-1'): Zona {
  const sectors = estados.map((status, i) => ({ id: `${id}-${i}`, status, n: 1 }) as Sector);
  return {
    id,
    name: id,
    sectors,
    total: sectors.length,
    lectura: { stale },
  } as Zona;
}

describe('pluralizar', () => {
  it('usa singular sólo con 1', () => {
    expect(pluralizar(1, 'sector', 'sectores')).toBe('1 sector');
    expect(pluralizar(0, 'sector', 'sectores')).toBe('0 sectores');
    expect(pluralizar(2, 'sector', 'sectores')).toBe('2 sectores');
  });
});

describe('estadoZona (peor estado)', () => {
  it('manda lo crítico por sobre lo demás', () => {
    expect(estadoZona(zona(['ok', 'warning', 'critical']))).toBe('critical');
  });
  it('observación si no hay críticos', () => {
    expect(estadoZona(zona(['ok', 'warning', 'offline']))).toBe('warning');
  });
  it('ok si todo está sano', () => {
    expect(estadoZona(zona(['ok', 'ok']))).toBe('ok');
  });
  it('offline si el nodo no tiene datos vigentes, aunque haya críticos', () => {
    expect(estadoZona(zona(['critical'], true))).toBe('offline');
  });
  it('offline si todos los sectores están sin señal', () => {
    expect(estadoZona(zona(['offline', 'offline']))).toBe('offline');
  });
  it('una zona vacía no se da por mala', () => {
    expect(estadoZona(zona([]))).toBe('ok');
  });
});

describe('resumenZona', () => {
  it('Todo bien', () => {
    expect(resumenZona(zona(['ok', 'offline']))).toBe('Todo bien');
  });
  it('concuerda número con 1 sector', () => {
    expect(resumenZona(zona(['warning', 'ok']))).toBe('1 sector necesita atención');
  });
  it('concuerda número con varios sectores', () => {
    expect(resumenZona(zona(['warning', 'critical', 'critical']))).toBe(
      '3 sectores necesitan atención',
    );
  });
  it('sin datos del sensor tiene prioridad', () => {
    expect(resumenZona(zona(['critical'], true))).toBe('Sin datos del sensor');
  });
});

describe('resumenVivero', () => {
  it('vivero saludable', () => {
    const r = resumenVivero([zona(['ok', 'ok'], false, 'MZ-1'), zona(['ok'], false, 'MZ-2')]);
    expect(r).toEqual({ text: 'El vivero está saludable: los 3 sectores están bien.', status: 'ok' });
  });
  it('cuenta sectores con atención y zonas sin datos', () => {
    const r = resumenVivero([
      zona(['warning', 'ok']),
      zona(['ok', 'ok'], true, 'MZ-2'),
      zona(['critical'], false, 'MZ-3'),
    ]);
    expect(r.text).toBe('2 de 5 sectores necesitan atención · 1 zona sin datos del sensor.');
    expect(r.status).toBe('critical');
  });
  it('sólo zonas sin datos', () => {
    const r = resumenVivero([zona(['ok'], true), zona(['ok'], true, 'MZ-2')]);
    expect(r.text).toBe('2 zonas sin datos del sensor.');
    expect(r.status).toBe('offline');
  });
  it('un solo sector en atención', () => {
    expect(resumenVivero([zona(['warning'])]).text).toBe('1 de 1 sector necesita atención.');
  });
  it('vivero vacío', () => {
    expect(resumenVivero([]).status).toBe('ok');
  });
});

describe('contadoresZona', () => {
  it('cuenta en palabras y omite críticos en cero', () => {
    expect(contadoresZona(zona(['ok', 'ok', 'warning', 'offline']))).toBe(
      '2 saludables · 1 en observación · 1 sin señal',
    );
  });
  it('incluye críticos cuando hay', () => {
    expect(contadoresZona(zona(['critical', 'critical', 'ok']))).toBe(
      '1 saludable · 0 en observación · 2 críticos · 0 sin señal',
    );
  });
});

describe('textoPlantines', () => {
  it('100 plantines por sector, sin depender de Sector.n', () => {
    expect(textoPlantines(zona(['ok', 'ok']))).toBe('2 sectores · 200 plantines');
    expect(textoPlantines(zona(['ok']))).toBe('1 sector · 100 plantines');
  });
});

describe('columnasGrilla', () => {
  it('no supera la cantidad de sectores', () => {
    expect(columnasGrilla(1, 10)).toBe(1);
    expect(columnasGrilla(4, 10)).toBe(4);
  });
  it('respeta la disposición configurada', () => {
    expect(columnasGrilla(100, 10)).toBe(10);
  });
  it('mínimo 1', () => {
    expect(columnasGrilla(0, 0)).toBe(1);
    expect(columnasGrilla(5, -3)).toBe(1);
  });
});
