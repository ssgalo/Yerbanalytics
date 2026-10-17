import { describe, expect, it } from 'vitest';
import { describirFalla, desdeCuando } from './fallas';

describe('describirFalla (S-05)', () => {
  it('los errores de caudal son una falla hidráulica del sector', () => {
    expect(describirFalla('SIN_CAUDAL')).toEqual({ titulo: 'Falla hidráulica', detalle: 'sin caudal' });
    expect(describirFalla('CAUDAL_EXCESIVO')).toEqual({ titulo: 'Falla hidráulica', detalle: 'caudal excesivo' });
  });

  it('el error de la malla es una falla mecánica en la mediasombra', () => {
    expect(describirFalla('MEDIASOMBRA_FIN_DE_CARRERA')).toEqual({
      titulo: 'Falla mecánica en mediasombra',
      detalle: 'fin de carrera sin activar',
    });
  });

  it('una falla que el contrato no define (cargada por otro camino) se muestra tal cual', () => {
    expect(describirFalla('Falla Hidráulica')).toEqual({ titulo: 'Falla Hidráulica', detalle: null });
  });
});

describe('desdeCuando', () => {
  const AHORA = Date.parse('2026-10-17T13:00:00Z');

  it('dice desde hace cuánto está marcada la falla', () => {
    expect(desdeCuando(AHORA - 30_000, AHORA)).toBe('hace 30 s');
    expect(desdeCuando(AHORA - 5 * 60_000, AHORA)).toBe('hace 5 min');
    expect(desdeCuando(AHORA - 3 * 3_600_000, AHORA)).toBe('hace 3 h');
    expect(desdeCuando(AHORA - 50 * 3_600_000, AHORA)).toBe('hace 2 d');
  });

  it('sin fecha (falla anterior a que se registrara desde cuándo) no inventa una', () => {
    expect(desdeCuando(null, AHORA)).toBeNull();
  });

  it('una fecha futura (reloj del servidor adelantado) se lee como recién', () => {
    expect(desdeCuando(AHORA + 60_000, AHORA)).toBe('hace 0 s');
  });
});
