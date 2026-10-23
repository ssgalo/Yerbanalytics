// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import { renderHook } from '@testing-library/react';
import { useColumnQuery } from '@/hooks/useColumnQuery';
import type { ActionRecord } from '@/types/domain';
import { HISTORY_COLUMNS } from '../actionPresentation';
import { HistoryTable } from './HistoryTable';

afterEach(cleanup);

const record = (e: Partial<ActionRecord>): ActionRecord => ({
  id: 'HE-1',
  sectorId: 'MZ-1-001',
  zonaName: 'MZ-1',
  tipo: 'Riego',
  time: 'hace 5 min',
  ts: 1,
  fecha: '03/10 10:05',
  lectura: '',
  decision: '',
  accion: '',
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

const evo = (bloqueando: boolean): ActionRecord['evo'] => ({
  show: true,
  metric: 'Humedad de sustrato',
  antes: '44',
  ahora: '48',
  unit: '%',
  delta: '+4',
  latencia: '30 min',
  verdict: 'Sin efectividad',
  vSoft: '',
  vInk: '',
  bloqueando,
});

function renderTable(rows: ActionRecord[]) {
  const { result } = renderHook(() => useColumnQuery(rows, HISTORY_COLUMNS));
  return render(
    <HistoryTable query={result.current} selectedId={null} onSelect={() => undefined} />,
  );
}

describe('HistoryTable · acciones que bloquean (S-06)', () => {
  it('marca con "Bloquea" sólo las filas cuyo seguimiento sigue bloqueando', () => {
    renderTable([
      record({ id: 'a', sectorId: 'MZ-1-001', evo: evo(true) }),
      record({ id: 'b', sectorId: 'MZ-1-002', evo: evo(false) }),
      record({ id: 'c', sectorId: 'MZ-1-003' }),
    ]);

    expect(screen.getAllByText('Bloquea')).toHaveLength(1);
    const row = screen.getByText('MZ-1-001').closest('tr') as HTMLElement;
    expect(within(row).getByText('Bloquea')).toBeTruthy();
  });
});
