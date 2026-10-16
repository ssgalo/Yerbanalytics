// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { DiagnosisCard } from '@/types/domain';
import { DiagnosisTile } from './DiagnosisTile';

afterEach(cleanup);

const card = (p: Partial<DiagnosisCard>): DiagnosisCard => ({
  id: 'DX-00001',
  sectorId: 'MZ-1-001',
  zonaName: 'Macro-zona 1',
  estado: 'Clorosis',
  conf: 62,
  confidence: 'Media',
  detectionCount: 14,
  sev: 'Media',
  sevSoft: '',
  sevInk: '',
  thumb: '',
  time: 'hace 5 min',
  creadoEn: null,
  concluyente: true,
  ...p,
});

const montar = (d: DiagnosisCard) =>
  render(
    <MemoryRouter>
      <DiagnosisTile d={d} onExpand={() => undefined} canOpenSector />
    </MemoryRouter>,
  );

describe('DiagnosisTile', () => {
  it('muestra el nivel de confianza, sin score, cantidad de detecciones ni aviso de actuación', () => {
    montar(card({}));

    expect(screen.getByText('Confianza media')).toBeTruthy();
    expect(screen.getByText('Severidad media')).toBeTruthy();
    expect(screen.queryByText(/62|Score/)).toBeNull();
    expect(screen.queryByText(/detecci/)).toBeNull();
    expect(screen.queryByText(/Bajo el umbral|Concluyente|umbral/)).toBeNull();
    expect(screen.queryByRole('img', { name: /Confianza \d/ })).toBeNull();
  });

  it('un Sano no muestra severidad', () => {
    montar(card({ estado: 'Sano', sev: '—', confidence: 'Alta', conf: 91 }));

    expect(screen.getByText('Confianza alta')).toBeTruthy();
    expect(screen.queryByText(/Severidad|Sin severidad/)).toBeNull();
  });

  it('No concluyente no muestra confianza, severidad ni score', () => {
    montar(card({ estado: 'No concluyente', sev: '—', confidence: null, conf: null, concluyente: false }));

    expect(screen.queryByText(/Confianza/)).toBeNull();
    expect(screen.queryByText(/Severidad/)).toBeNull();
    expect(screen.queryByText(/Score/)).toBeNull();
  });

  it('un diagnóstico anterior a los niveles se muestra "sin nivel"', () => {
    montar(card({ confidence: null, detectionCount: 0 }));

    expect(screen.getByText('Sin nivel de confianza')).toBeTruthy();
    expect(screen.queryByText(/detecci/)).toBeNull();
  });
});
