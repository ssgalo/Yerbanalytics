import { describe, expect, it } from 'vitest';
import type { ActionRecord } from '@/types/domain';
import { inicioDelDia, registrosDeHoy } from './useActividadHoy';

const reg = (id: string, ts: number) => ({ id, ts }) as ActionRecord;

describe('registrosDeHoy', () => {
  const ahora = new Date(2026, 9, 11, 15, 30);
  const hoy = inicioDelDia(ahora);

  it('deja sólo lo ocurrido desde la medianoche, más reciente primero', () => {
    const records = [
      reg('ayer', hoy - 1),
      reg('medianoche', hoy),
      reg('tarde', hoy + 15 * 3_600_000),
      reg('mañana', hoy + 9 * 3_600_000),
    ];
    expect(registrosDeHoy(records, ahora).map((r) => r.id)).toEqual(['tarde', 'mañana', 'medianoche']);
  });

  it('sin eventos de hoy devuelve vacío', () => {
    expect(registrosDeHoy([reg('ayer', hoy - 60_000)], ahora)).toEqual([]);
  });
});
