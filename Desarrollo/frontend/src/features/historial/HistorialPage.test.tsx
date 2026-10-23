// @vitest-environment jsdom
import '@/test/reactFlowJsdom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { buildRuleSchema } from '@/data/mock/reglasMock';
import type { ActionRecord } from '@/types/domain';

const record = (e: Partial<ActionRecord>): ActionRecord => ({
  id: 'HE-1',
  sectorId: 'MZ-1-001',
  zonaName: 'MZ-1',
  tipo: 'Riego',
  time: 'hace 5 min',
  ts: 1,
  fecha: '03/10 10:05',
  lectura: 'Humedad de sustrato 38%.',
  decision: 'El motor de reglas ordena regar 5,4 L durante 648 s.',
  accion: 'Electroválvula abierta.',
  res: 'Efectiva',
  resSoft: '',
  resInk: '',
  sev: '—',
  tint: '',
  ink: '',
  path: 'M0 0',
  evo: null,
  ...e,
});

const RECORDS = [
  record({ id: 'a', ts: 3, fecha: '03/10 14:00', regla: 'DeficitCriticoRule', volumenL: 6, duracionSeg: 720 }),
  record({
    id: 'b',
    ts: 2,
    fecha: '03/10 10:00',
    tipo: 'Alerta',
    sectorId: '—',
    zonaName: 'MZ-3',
    res: 'Informativo',
    alerta: 'CRITICAL',
    regla: 'DeficitCriticoRule',
    lectura: 'Alerta de la macro-zona.',
    decision: 'Déficit hídrico crítico',
    accion: 'Alerta CRITICAL registrada para el operador.',
  }),
];

vi.mock('@/hooks/useHistory', () => ({
  useHistory: () => ({ records: RECORDS, loading: false, error: null }),
}));
vi.mock('@/hooks/useRuleEngineSchema', () => ({
  useRuleEngineSchema: () => ({ schema: buildRuleSchema(), loading: false, error: null }),
}));
vi.mock('@/hooks/PageMeta', () => ({ usePageTitle: () => {} }));

const { HistorialPage } = await import('./HistorialPage');

beforeEach(() => render(<HistorialPage />));
afterEach(cleanup);

const detail = () => screen.getByRole('complementary', { name: 'Detalle de la acción' });

describe('HistorialPage', () => {
  it('arranca con la acción más reciente elegida y su lectura, decisión y acción al lado', () => {
    const d = within(detail());
    expect(d.getByRole('heading', { name: 'MZ-1-001' })).toBeTruthy();
    expect(d.getByText('Humedad de sustrato 38%.')).toBeTruthy();
    expect(d.getByText('6 L')).toBeTruthy();
    // La regla, con su nombre legible y sin emoji
    expect(d.getByText('Déficit hídrico crítico (R-02)')).toBeTruthy();
  });

  it('al elegir una alerta, el detalle cambia y no ofrece el razonamiento del motor', () => {
    fireEvent.click(screen.getByText('Alerta crítica'));

    const d = within(detail());
    expect(d.getByRole('heading', { name: 'MZ-3' })).toBeTruthy();
    expect(d.queryByRole('button', { name: /razonamiento/i })).toBeNull();
  });

  it('el razonamiento se abre en un popup con el grafo y el selector de reglas', () => {
    fireEvent.click(within(detail()).getByRole('button', { name: /Ver razonamiento del motor/ }));

    const dialog = screen.getByRole('dialog', { name: 'Razonamiento del motor' });
    expect(dialog.querySelector('[data-state="decided"]')?.textContent).toBe('Déficit hídrico crítico (R-02)');
    expect(dialog.querySelectorAll('[data-state="skipped"]').length).toBeGreaterThan(0);

    fireEvent.click(within(dialog).getByRole('button', { name: 'Sólo ejecutadas' }));
    expect(dialog.querySelectorAll('[data-state="skipped"]').length).toBe(0);

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: 'Razonamiento del motor' })).toBeNull();
  });
});
