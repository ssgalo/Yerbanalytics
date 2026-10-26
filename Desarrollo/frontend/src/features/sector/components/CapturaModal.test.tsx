// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import * as data from '@/data';
import type { DataRepository } from '@/data';
import type { Detection, DiagnosisDetections } from '@/types/domain';
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

/** Respuesta del backend sin recorte (carga manual o diagnóstico anterior al recorte). */
const SIN_RECORTE: DiagnosisDetections = { detections: DETECCIONES, crop: null };
/** Respuesta con el recorte de fábrica del servicio de inferencia (1080×1440 → 802×1074). */
const CON_RECORTE: DiagnosisDetections = { detections: DETECCIONES, crop: [0.2574, 0.2542, 1, 1] };

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

const foto = () => screen.getByRole('img', { name: /Captura cenital/ }) as HTMLImageElement;
const marco = () => screen.getByTestId('captura-frame');

/** jsdom no carga imágenes: simula la carga de una captura de 1080×1440. */
function cargarFoto(width = 1080, height = 1440) {
  Object.defineProperty(foto(), 'naturalWidth', { value: width, configurable: true });
  Object.defineProperty(foto(), 'naturalHeight', { value: height, configurable: true });
  fireEvent.load(foto());
}

const selector = () => screen.queryByRole('group', { name: 'Cajas sobre la foto' });
const opcion = (nombre: string) => within(selector()!).getByRole('button', { name: nombre });
const cajas = () => document.querySelectorAll('[data-testid="detection-overlay"] g');
const etiquetas = () => [...cajas()].map((g) => g.getAttribute('data-label'));

describe('CapturaModal · cajas de detección', () => {
  it('sin detecciones no ofrece el selector, pero igual pide el recorte', async () => {
    const pedir = repoCon(async () => ({ detections: [], crop: null }));
    montar({ detectionCount: 0 });

    expect(selector()).toBeNull();
    expect(foto()).toBeTruthy();
    await waitFor(() => expect(pedir).toHaveBeenCalledWith('DX-00001'));
  });

  it('sin diagnóstico DX- (o sin foto) tampoco, y no pide nada', () => {
    const pedir = repoCon(async () => SIN_RECORTE);
    montar({ diagnosisId: null });
    expect(selector()).toBeNull();
    cleanup();
    montar({ imagenUrl: undefined });
    expect(selector()).toBeNull();
    expect(pedir).not.toHaveBeenCalled();
  });

  it('arranca en Original, sin cajas; las pide una vez al abrir', async () => {
    const pedir = repoCon(async () => SIN_RECORTE);
    montar();

    expect(opcion('Original').getAttribute('aria-pressed')).toBe('true');
    await waitFor(() => expect(pedir).toHaveBeenCalledTimes(1));
    expect(cajas()).toHaveLength(0);
  });

  it('Diagnóstico pide las cajas una vez y muestra sólo las del estado diagnosticado', async () => {
    const pedir = repoCon(async () => SIN_RECORTE);
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

  it('si las cajas no cargan, sigue la foto entera con un aviso y se puede reintentar', async () => {
    const pedir = repoCon(async () => {
      throw new Error('Error 500');
    });
    montar();
    await waitFor(() => expect(pedir).toHaveBeenCalledTimes(1));
    cargarFoto();
    // La foto entera, visible y sin recortar
    await waitFor(() => expect(marco().className).not.toMatch(/pending/));
    expect(marco().className).not.toMatch(/region/);

    fireEvent.click(opcion('Todas'));
    expect((await screen.findByRole('alert')).textContent).toMatch(/No se pudieron cargar las cajas/);
    expect(foto()).toBeTruthy();
    expect(cajas()).toHaveLength(0);

    pedir.mockImplementation(async () => SIN_RECORTE);
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(cajas()).toHaveLength(6));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('un clic en los controles o la foto no cierra el modal', () => {
    repoCon(async () => SIN_RECORTE);
    const onClose = vi.fn();
    montar({ onClose });

    fireEvent.click(opcion('Diagnóstico'));
    fireEvent.click(screen.getByRole('img', { name: /Captura cenital/ }));
    expect(onClose).not.toHaveBeenCalled();
  });
});

describe('CapturaModal · región recortada', () => {
  it('con recorte, las tres vistas muestran sólo la región, con las cajas encima', async () => {
    repoCon(async () => CON_RECORTE);
    montar();

    // Mientras no se sabe qué región mostrar, la foto no se ve
    expect(marco().className).toMatch(/pending/);
    await waitFor(() => expect(screen.getByRole('group', { name: 'Cajas sobre la foto' })).toBeTruthy());
    cargarFoto();

    await waitFor(() => expect(marco().className).toMatch(/region/));
    expect(marco().className).not.toMatch(/pending/);
    // Región de 802×1074 (0,7426 × 0,7458 de 1080×1440): el marco toma su relación de aspecto
    expect(Number(marco().style.aspectRatio)).toBeCloseTo((0.7426 * 1080) / (0.7458 * 1440), 4);
    // La imagen se escala y se desplaza para que asome sólo la región
    expect(parseFloat(foto().style.width)).toBeCloseTo(100 / 0.7426, 2);
    expect(parseFloat(foto().style.height)).toBeCloseTo(100 / 0.7458, 2);
    expect(parseFloat(foto().style.left)).toBeCloseTo((-0.2574 / 0.7426) * 100, 2);
    expect(parseFloat(foto().style.top)).toBeCloseTo((-0.2542 / 0.7458) * 100, 2);

    // Las vistas con cajas siguen sobre la región: el overlay vive dentro del mismo marco
    fireEvent.click(opcion('Todas'));
    await waitFor(() => expect(cajas()).toHaveLength(6));
    expect(marco().querySelector('[data-testid="detection-overlay"]')).toBeTruthy();
    expect(marco().className).toMatch(/region/);
    fireEvent.click(opcion('Diagnóstico'));
    expect(marco().className).toMatch(/region/);
  });

  it('sin recorte, la imagen entera como siempre', async () => {
    repoCon(async () => SIN_RECORTE);
    montar();
    await waitFor(() => expect(marco().className).not.toMatch(/pending/));
    cargarFoto();

    expect(marco().className).not.toMatch(/region/);
    expect(foto().style.width).toBe('');
    fireEvent.click(opcion('Todas'));
    await waitFor(() => expect(cajas()).toHaveLength(6));
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
