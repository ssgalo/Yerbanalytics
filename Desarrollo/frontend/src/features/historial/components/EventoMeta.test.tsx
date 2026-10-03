// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { EventoMeta } from './EventoMeta';
import { etiquetaTipo, formatoDuracion, formatoVolumen } from '../eventoPresentacion';
import type { ActionRecord } from '@/types/domain';

afterEach(cleanup);

const base: ActionRecord = {
  id: 'HE-1',
  sectorId: 'MZ-1-003',
  zonaName: 'Macro-zona 1',
  tipo: 'Riego',
  time: 'hace 5 min',
  ts: 1,
  fecha: '03/10 10:05',
  lectura: '',
  decision: '',
  accion: '',
  res: 'Efectiva',
  resSoft: '#fff',
  resInk: '#000',
  sev: '—',
  tint: '#fff',
  ink: '#000',
  path: 'M0 0',
  evo: null,
};

const nombres = { DeficitCriticoRule: '🚨 Déficit hídrico crítico (R-02)' };

describe('EventoMeta', () => {
  it('un riego muestra volumen, duración y regla (con el nombre legible de la regla)', () => {
    render(<EventoMeta registro={{ ...base, regla: 'DeficitCriticoRule', volumenL: 6, duracionSeg: 720 }} nombresReglas={nombres} />);

    expect(screen.getByText('6 L')).toBeTruthy();
    expect(screen.getByText('720 s')).toBeTruthy();
    expect(screen.getByText(/Déficit hídrico crítico \(R-02\)/)).toBeTruthy();
  });

  it('una regla desconocida se muestra por su id', () => {
    render(<EventoMeta registro={{ ...base, regla: 'OtraRule', volumenL: 5.4, duracionSeg: 648 }} nombresReglas={nombres} />);

    expect(screen.getByText('5,4 L')).toBeTruthy();
    expect(screen.getByText(/OtraRule/)).toBeTruthy();
  });

  it('una alerta muestra su nivel', () => {
    render(<EventoMeta registro={{ ...base, tipo: 'Alerta', regla: 'SustratoSaturadoRule', alerta: 'WARNING' }} nombresReglas={{}} />);

    expect(screen.getByText('Advertencia')).toBeTruthy();
    expect(screen.queryByText(/ L$/)).toBeNull();
  });

  it('un evento sin datos nuevos (insumo, mediasombra o historial viejo) no muestra nada', () => {
    const { container } = render(<EventoMeta registro={{ ...base, tipo: 'Insumo' }} nombresReglas={{}} />);

    expect(container.textContent).toBe('');
  });
});

describe('eventoPresentacion', () => {
  it('formatea volumen con coma decimal y sin ceros de más', () => {
    expect(formatoVolumen(6)).toBe('6 L');
    expect(formatoVolumen(5.4)).toBe('5,4 L');
    expect(formatoVolumen(4.25)).toBe('4,25 L');
  });

  it('formatea la duración en segundos', () => {
    expect(formatoDuracion(648)).toBe('648 s');
  });

  it('el rótulo del tipo agrega el nivel a las alertas', () => {
    expect(etiquetaTipo({ ...base, tipo: 'Alerta', alerta: 'CRITICAL' })).toBe('Alerta crítica');
    expect(etiquetaTipo({ ...base, tipo: 'Riego' })).toBe('Riego');
  });
});
