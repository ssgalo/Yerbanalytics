import { describe, expect, it } from 'vitest';
import { etapasQueRetroceden, fechaFin, planErrors, progreso } from './planRustificacion';

const etapas = [
  { orden: 1, dias: 7, abiertaDesde: '10:00', abiertaHasta: '13:00' },
  { orden: 2, dias: 7, abiertaDesde: '09:00', abiertaHasta: '15:00' },
];

describe('plan de rustificación', () => {
  it('termina el último día de la última etapa', () => {
    expect(fechaFin('2026-10-13', etapas)).toBe('2026-10-26');
  });

  it('ubica el día y la etapa de hoy', () => {
    const plan = { fechaInicio: '2026-10-01', etapas };
    expect(progreso(plan, '2026-09-29')).toMatchObject({ dia: 0, faltan: 2, etapa: null, terminado: false });
    expect(progreso(plan, '2026-10-01')).toMatchObject({ dia: 1, etapa: { orden: 1 } });
    expect(progreso(plan, '2026-10-08')).toMatchObject({ dia: 8, etapa: { orden: 2 } });
    // Pasada la última etapa, la última se sostiene hasta que se elimine el plan.
    expect(progreso(plan, '2026-10-15')).toMatchObject({ dia: 14, etapa: { orden: 2 }, terminado: true });
    expect(progreso(plan, '2026-12-31')).toMatchObject({ etapa: { orden: 2 }, terminado: true });
  });

  it('valida fecha, duración y horario de cada etapa', () => {
    const ok = { fechaInicio: '2026-10-13', etapas };
    expect(planErrors(ok, '2026-10-13')).toEqual([]);
    expect(planErrors({ ...ok, fechaInicio: '2026-10-12' }, '2026-10-13')).toEqual([
      'La fecha de inicio no puede ser anterior a hoy.',
    ]);
    expect(planErrors({ ...ok, etapas: [] }, '2026-10-13')).toEqual(['El plan necesita al menos una etapa.']);
    expect(
      planErrors({ ...ok, etapas: [{ dias: 0, abiertaDesde: '15:00', abiertaHasta: '09:00' }] }, '2026-10-13'),
    ).toEqual([
      'Etapa 1: la duración debe ser un entero entre 1 y 60 días.',
      'Etapa 1: la hora de cierre debe ser posterior a la de apertura.',
    ]);
  });

  it('avisa las etapas que exponen menos horas que la anterior', () => {
    expect(etapasQueRetroceden(etapas)).toEqual([]);
    expect(etapasQueRetroceden([...etapas, { dias: 3, abiertaDesde: '11:00', abiertaHasta: '12:00' }])).toEqual([3]);
  });
});
