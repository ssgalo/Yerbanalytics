import { describe, expect, it } from 'vitest';
import {
  BANDEJAS,
  COLUMNAS,
  FILAS,
  TUBETES_POR_SECTOR,
  calcularGeometria,
} from './geometriaSector';

describe('geometría del sector', () => {
  const g = calcularGeometria();

  it('4 bandejas de 5×5 = 100 tubetes', () => {
    expect(BANDEJAS).toBe(4);
    expect(FILAS * COLUMNAS).toBe(25);
    expect(TUBETES_POR_SECTOR).toBe(100);
    expect(g.bandejas).toHaveLength(4);
    expect(g.tubetes).toHaveLength(100);
  });

  it('las bandejas están dentro de la caja del sector y no se pisan', () => {
    const { caja } = g;
    for (const b of g.bandejas) {
      expect(b.x).toBeGreaterThanOrEqual(caja.x);
      expect(b.y).toBeGreaterThanOrEqual(caja.y);
      expect(b.x + b.w).toBeLessThanOrEqual(caja.x + caja.w);
      expect(b.y + b.h).toBeLessThanOrEqual(caja.y + caja.h);
    }
    for (let i = 0; i < g.bandejas.length; i++) {
      for (let j = i + 1; j < g.bandejas.length; j++) {
        const a = g.bandejas[i];
        const b = g.bandejas[j];
        const separadas =
          a.x + a.w <= b.x || b.x + b.w <= a.x || a.y + a.h <= b.y || b.y + b.h <= a.y;
        expect(separadas).toBe(true);
      }
    }
  });

  it('cada tubete cae dentro de su bandeja y en posición única', () => {
    const posiciones = new Set<string>();
    for (const t of g.tubetes) {
      const b = g.bandejas[t.bandeja];
      expect(t.cx - g.radio).toBeGreaterThanOrEqual(b.x);
      expect(t.cx + g.radio).toBeLessThanOrEqual(b.x + b.w);
      expect(t.cy - g.radio).toBeGreaterThanOrEqual(b.y);
      expect(t.cy + g.radio).toBeLessThanOrEqual(b.y + b.h);
      posiciones.add(`${t.cx},${t.cy}`);
    }
    expect(posiciones.size).toBe(100);
  });

  it('el nodo testigo queda fuera de la caja del sector, dentro del viewBox', () => {
    const { nodo, caja, viewBox } = g;
    expect(nodo.x).toBeGreaterThanOrEqual(caja.x + caja.w);
    expect(nodo.x + nodo.w).toBeLessThanOrEqual(viewBox.w);
  });

  it('el microaspersor está en el centro de la caja', () => {
    expect(g.aspersor.x).toBe(g.caja.x + g.caja.w / 2);
    expect(g.aspersor.y).toBe(g.caja.y + g.caja.h / 2);
  });
});
