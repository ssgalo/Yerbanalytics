// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RecentDiagnostics } from './RecentDiagnostics';
import type { DiagnosisCard } from '@/types/domain';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const diag = (d: Partial<DiagnosisCard>): DiagnosisCard => ({
  id: 'DG-001',
  sectorId: 'MZ-2-014',
  zonaName: 'Macro-zona 2',
  estado: 'Clorosis',
  conf: 91,
  sev: 'Media',
  sevSoft: '#FFF3DC',
  sevInk: '#8A5A00',
  thumb: 'linear-gradient(#3a7, #164)',
  time: 'hace 5 min',
  concluyente: true,
  imagenUrl: null,
  ...d,
});

describe('RecentDiagnostics', () => {
  it('un sector con varios diagnósticos recientes no repite la clave de React (cada diagnóstico tiene la suya)', () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    render(
      <MemoryRouter>
        <RecentDiagnostics
          recentDiag={[
            diag({ id: 'DG-001', estado: 'Clorosis' }),
            diag({ id: 'DG-002', estado: 'Estrés solar' }),
            diag({ id: 'DG-003', sectorId: 'MZ-3-001', estado: 'Sano' }),
          ]}
        />
      </MemoryRouter>,
    );

    expect(screen.getAllByText(/MZ-2-014/)).toHaveLength(2);
    const avisosDeClave = error.mock.calls.filter((c) => String(c[0]).includes('same key'));
    expect(avisosDeClave).toHaveLength(0);
  });
});
