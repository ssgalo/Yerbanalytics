// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import * as data from '@/data';
import type { DataRepository } from '@/data';
import type { Detection } from '@/types/domain';
import { CapturaModal } from './CapturaModal';
import { countByLabel, visibleDetections } from './detectionView';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const DETECCIONES: Detection[] = [
  { label: 'Clorosis', score: 0.62, box: [0.1, 0.1, 0.2, 0.2] },
  { label: 'Clorosis', score: 0.7, box: [0.3, 0.3, 0.4, 0.45] },
  { label: 'Sano', score: 0.85, box: [0.5, 0.5, 0.7, 0.7] },
  { label: 'Sano', score: 0.8, box: [0.6, 0.1, 0.8, 0.3] },
  { label: 'Sano', score: 0.75, box: [0.05, 0.6, 0.25, 0.9] },
  { label: 'Plaga foliar', score: 0.4, box: [0.75, 0.75, 0.9, 0.95] },
];

function repoCon(getDiagnosisDetections: DataRepository['getDiagnosisDetections']) {
  const fn = vi.fn(getDiagnosisDetections);
  vi.spyOn(data, 'getRepository').mockReturnValue({ getDiagnosisDetections: fn } as unknown as DataRepository);
  return fn;
}

const montar = (props: Partial<Parameters<typeof CapturaModal>[0]> = {}) =>
  render(
    <CapturaModal
      thumb="linear-gradient(#000,#fff)"
      imagenUrl="/demo/capturas/clorosis/01.jpg"
      estado="Clorosis"
      ago="hace 5 min"
      sectorId="MZ-1-001"
      diagnosisId="DX-00001"
      detectionCount={DETECCIONES.length}
      onClose={() => undefined}
      {...props}
    />,
  );

const selector = () => screen.queryByRole('group', { name: 'Cajas sobre la foto' });
const opcion = (nombre: string) => within(selector()!).getByRole('button', { name: nombre });
const cajas = () => document.querySelectorAll('[data-testid="detection-overlay"] g');
const etiquetas = () => [...cajas()].map((g) => g.getAttribute('data-label'));

describe('CapturaModal · cajas de detección', () => {
  it('sin detecciones no ofrece el selector', () => {
    const pedir = repoCon(async () => DETECCIONES);
    montar({ detectionCount: 0 });

    expect(selector()).toBeNull();
    expect(screen.getByRole('img', { name: /Captura cenital/ })).toBeTruthy();
    expect(pedir).not.toHaveBeenCalled();
  });

  it('sin diagnóstico DX- (o sin foto) tampoco', () => {
    repoCon(async () => DETECCIONES);
    montar({ diagnosisId: null });
    expect(selector()).toBeNull();
    cleanup();
    montar({ imagenUrl: undefined });
    expect(selector()).toBeNull();
  });

  it('arranca en Original, sin cajas y sin pedirlas', () => {
    const pedir = repoCon(async () => DETECCIONES);
    montar();

    expect(opcion('Original').getAttribute('aria-pressed')).toBe('true');
    expect(cajas()).toHaveLength(0);
    expect(pedir).not.toHaveBeenCalled();
  });

  it('Diagnóstico pide las cajas una vez y muestra sólo las del estado diagnosticado', async () => {
    const pedir = repoCon(async () => DETECCIONES);
    montar();

    fireEvent.click(opcion('Diagnóstico'));
    await waitFor(() => expect(cajas()).toHaveLength(2));
    expect(pedir).toHaveBeenCalledWith('DX-00001');
    expect(new Set(etiquetas())).toEqual(new Set(['Clorosis']));
    expect(screen.getByText('diagnosticado')).toBeTruthy();

    // Todas: no vuelve a pedirlas; la leyenda nombra cada clase, sin cantidades
    fireEvent.click(opcion('Todas'));
    expect(cajas()).toHaveLength(6);
    expect(pedir).toHaveBeenCalledTimes(1);
    const leyenda = screen.getByRole('list', { name: 'Cajas sobre la foto' });
    expect(within(leyenda).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      'Sano',
      'Clorosisdiagnosticado',
      'Plaga foliar',
    ]);
    // Las del estado diagnosticado van resaltadas (trazo continuo grueso) y arriba de las demás
    const principales = [...cajas()].filter((g) => g.hasAttribute('data-main'));
    expect(principales.map((g) => g.getAttribute('data-label'))).toEqual(['Clorosis', 'Clorosis']);
    expect(etiquetas().slice(-2)).toEqual(['Clorosis', 'Clorosis']);

    // Original: la foto sin cajas
    fireEvent.click(opcion('Original'));
    expect(cajas()).toHaveLength(0);
    expect(screen.queryByRole('list', { name: 'Cajas sobre la foto' })).toBeNull();
  });

  it('si las cajas no cargan, sigue la foto con un aviso y se puede reintentar', async () => {
    const pedir = repoCon(async () => {
      throw new Error('Error 500');
    });
    montar();

    fireEvent.click(opcion('Todas'));
    expect((await screen.findByRole('alert')).textContent).toMatch(/No se pudieron cargar las cajas/);
    expect(screen.getByRole('img', { name: /Captura cenital/ })).toBeTruthy();
    expect(cajas()).toHaveLength(0);

    pedir.mockImplementation(async () => DETECCIONES);
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(cajas()).toHaveLength(6));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('un clic en los controles o la foto no cierra el modal', () => {
    repoCon(async () => DETECCIONES);
    const onClose = vi.fn();
    montar({ onClose });

    fireEvent.click(opcion('Diagnóstico'));
    fireEvent.click(screen.getByRole('img', { name: /Captura cenital/ }));
    expect(onClose).not.toHaveBeenCalled();
  });
});

describe('DetectionOverlay · funciones puras', () => {
  it('cada vista filtra lo que corresponde', () => {
    expect(visibleDetections(DETECCIONES, 'Clorosis', 'original')).toEqual([]);
    expect(visibleDetections(DETECCIONES, 'Clorosis', 'diagnosis').every((d) => d.label === 'Clorosis')).toBe(true);
    expect(visibleDetections(DETECCIONES, 'Clorosis', 'all')).toHaveLength(DETECCIONES.length);
  });

  it('cuenta por clase en el orden del modelo', () => {
    expect(countByLabel(DETECCIONES)).toEqual([
      { label: 'Sano', count: 3 },
      { label: 'Clorosis', count: 2 },
      { label: 'Plaga foliar', count: 1 },
    ]);
  });
});
