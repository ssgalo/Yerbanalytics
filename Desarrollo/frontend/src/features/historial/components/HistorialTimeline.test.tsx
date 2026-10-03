// @vitest-environment jsdom
import '@/test/reactFlowJsdom';
import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { HistorialTimeline } from './HistorialTimeline';
import { HistorialFilters } from './HistorialFilters';
import { buildRuleSchema } from '@/data/mock/reglasMock';
import type { ActionRecord } from '@/types/domain';

afterEach(cleanup);

const schema = buildRuleSchema();

const evento = (e: Partial<ActionRecord>): ActionRecord => ({
  id: 'HE-1',
  sectorId: 'MZ-2-014',
  zonaName: 'Macro-zona 2',
  tipo: 'Riego',
  time: 'hace 5 min',
  ts: 1,
  fecha: '03/10 10:05',
  lectura: 'Humedad de sustrato 30% (regla DeficitCriticoRule).',
  decision: 'El motor de reglas ordena regar 6 L durante 720 s.',
  accion: 'Electroválvula abierta · riego autónomo en curso.',
  res: 'Efectiva',
  resSoft: '#E7F1EA',
  resInk: '#2E7A4F',
  sev: '—',
  tint: '#E2EEF3',
  ink: '#2A6E8C',
  path: 'M12 2.7s6 6.6 6 11a6 6 0 0 1-12 0c0-4.4 6-11 6-11Z',
  evo: null,
  ...e,
});

/** Abre el ciclo, la macro-zona y el sector: el detalle de cada acción está tres clics adentro. */
function abrirTodo(zona: string, sector: string) {
  fireEvent.click(screen.getByRole('button', { name: /Ciclo/ }));
  fireEvent.click(screen.getByRole('button', { name: new RegExp(zona) }));
  fireEvent.click(screen.getByRole('button', { name: new RegExp(sector) }));
}

describe('HistorialTimeline · riegos y alertas (13.5)', () => {
  it('un riego muestra su volumen, su duración y la regla que lo ordenó', () => {
    const riego = evento({ regla: 'DeficitCriticoRule', volumenL: 6, duracionSeg: 720 });
    render(<HistorialTimeline records={[riego]} schema={schema} schemaLoading={false} />);

    abrirTodo('Macro-zona 2', 'MZ-2-014');

    const meta = screen.getByTestId('evento-meta');
    expect(meta.textContent).toContain('6 L');
    expect(meta.textContent).toContain('720 s');
    // El nombre legible sale del esquema del motor, no el id de la clase.
    expect(meta.textContent).toContain('Déficit hídrico crítico (R-02)');
  });

  it('una alerta muestra su nivel, va rotulada como de la macro-zona y no ofrece el razonamiento de un sector', () => {
    const alerta = evento({
      id: 'HE-2',
      tipo: 'Alerta',
      sectorId: '—',
      regla: 'SustratoSaturadoRule',
      alerta: 'WARNING',
      res: 'Informativo',
      lectura: 'Alerta de la macro-zona MZ-2 (regla SustratoSaturadoRule).',
      decision: 'Sustrato saturado, riesgo de asfixia radicular y hongos',
      accion: 'Alerta WARNING registrada para el operador.',
    });
    render(<HistorialTimeline records={[alerta]} schema={schema} schemaLoading={false} />);

    fireEvent.click(screen.getByRole('button', { name: /Ciclo/ }));
    fireEvent.click(screen.getByRole('button', { name: /Macro-zona 2/ }));
    expect(screen.getByText('Alerta advertencia: Informativo')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Alertas de la macro-zona/ }));

    expect(screen.getByTestId('evento-meta').textContent).toContain('Advertencia');
    expect(screen.getByText('Sustrato saturado, riesgo de asfixia radicular y hongos')).toBeTruthy();
    expect(screen.queryByText(/Ver razonamiento del motor/)).toBeNull();
  });

  it('un evento de Insumo (sin los datos nuevos) se muestra igual que antes', () => {
    render(
      <HistorialTimeline
        records={[evento({ tipo: 'Insumo', regla: undefined, volumenL: undefined, duracionSeg: undefined })]}
        schema={schema}
        schemaLoading={false}
      />,
    );
    abrirTodo('Macro-zona 2', 'MZ-2-014');

    expect(screen.queryByTestId('evento-meta')).toBeNull();
    expect(screen.getByText(/Ver razonamiento del motor/)).toBeTruthy();
  });
});

describe('HistorialFilters · tipo de acción (13.5)', () => {
  it('el filtro de tipo suma "Alerta" y el de resultado "Informativo"', () => {
    render(
      <HistorialFilters
        value={{ tipo: 'Todas', zona: 'Todas', sector: '', resultado: 'Todas', desde: '', hasta: '' }}
        zonaOpts={[]}
        count={0}
        onChange={() => undefined}
      />,
    );

    const opciones = screen.getAllByRole('option').map((o) => o.textContent);
    expect(opciones).toContain('Alerta');
    expect(opciones).toContain('Informativo');
  });
});
