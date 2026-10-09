// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { DemoExpoPage } from './DemoExpoPage';
import { PageMetaProvider } from '@/hooks/PageMeta';
import type { EstadoPasada, EstadoPaso, Pasada, PasoPasada } from '@/types/domain';

const estado = vi.hoisted(() => ({
  visible: true,
  cargandoSwitch: false,
  hook: {} as Record<string, unknown>,
}));

vi.mock('@/hooks/DemoExpoContext', () => ({
  useDemoExpo: () => ({ visible: estado.visible, cargando: estado.cargandoSwitch, cambiar: vi.fn() }),
}));
vi.mock('@/hooks/usePasada', () => ({
  usePasada: () => estado.hook,
}));
// Un solo objeto: el panel depende de la identidad de `descartarError` en un efecto.
const secuenciaMock = vi.hoisted(() => ({
  secuencia: null,
  cargando: false,
  error: null,
  iniciando: false,
  iniciar: () => Promise.resolve(),
  cancelar: () => Promise.resolve(),
  descartarError: () => undefined,
}));
vi.mock('@/hooks/useSecuencia', () => ({ useSecuencia: () => secuenciaMock }));

afterEach(cleanup);

const base = (n: number, tipo: PasoPasada['tipo'], over: Partial<PasoPasada> = {}): PasoPasada => ({
  n,
  tipo,
  posicion: tipo === 'HOME' ? 0 : n <= 2 ? 1 : 2,
  sectorId: tipo === 'CAPTURAR' ? (n === 2 ? 'MZ-1-001' : 'MZ-1-002') : null,
  estado: 'PENDIENTE',
  codigoError: null,
  detalle: null,
  commandId: null,
  ordenId: null,
  estadoOrden: null,
  capturaId: null,
  imagenUrl: null,
  diagnostico: null,
  iniciadoEn: null,
  terminadoEn: null,
  ...over,
});

const pasos = (estados: EstadoPaso[], overs: Partial<PasoPasada>[] = []): PasoPasada[] =>
  (['MOVER', 'CAPTURAR', 'MOVER', 'CAPTURAR', 'HOME'] as const).map((t, i) =>
    base(i + 1, t, { estado: estados[i], ...overs[i] }),
  );

const pasada = (
  estadoPasada: EstadoPasada,
  ps: PasoPasada[],
  over: Partial<Pasada> = {},
): Pasada => ({
  id: 'p',
  estado: estadoPasada,
  iniciadaEn: 1,
  finalizadaEn: estadoPasada === 'EN_CURSO' ? null : 2,
  cancelacionSolicitada: false,
  error: null,
  pasos: ps,
  ...over,
});

const hook = (p: Pasada | null, over: Record<string, unknown> = {}) => {
  estado.hook = {
    pasada: p,
    cargando: false,
    error: null,
    iniciando: false,
    iniciar: vi.fn(),
    cancelar: vi.fn(),
    ...over,
  };
  return estado.hook as { iniciar: ReturnType<typeof vi.fn>; cancelar: ReturnType<typeof vi.fn> };
};

const montar = () =>
  render(
    <MemoryRouter>
      <PageMetaProvider>
        <DemoExpoPage />
      </PageMetaProvider>
    </MemoryRouter>,
  );

const btn = (nombre: RegExp) => screen.queryByRole('button', { name: nombre }) as HTMLButtonElement | null;

describe('DemoExpoPage · interruptor apagado', () => {
  it('avisa que está desactivada y enlaza a Configuración', () => {
    estado.visible = false;
    hook(null);

    montar();

    expect(screen.getByText(/La sección Demo Expo está desactivada/)).toBeTruthy();
    expect(screen.getByRole('link', { name: /Configuración/ }).getAttribute('href')).toBe('/configuracion');
    expect(btn(/Iniciar pasada/)).toBeNull();
  });
});

describe('DemoExpoPage', () => {
  it('sin ninguna pasada: estado vacío y botón Iniciar habilitado', () => {
    estado.visible = true;
    const h = hook(null);

    montar();

    expect(screen.getByText(/Todavía no hubo ninguna pasada/)).toBeTruthy();
    expect(btn(/Cancelar/)).toBeNull();
    const iniciar = btn(/Iniciar pasada/)!;
    expect(iniciar.disabled).toBe(false);
    fireEvent.click(iniciar);
    expect(h.iniciar).toHaveBeenCalled();
  });

  it('mientras inicia, el botón queda deshabilitado', () => {
    estado.visible = true;
    hook(null, { iniciando: true });
    montar();
    expect(btn(/Iniciar pasada|Iniciando/)!.disabled).toBe(true);
  });

  it('muestra el mensaje de error del backend tal cual', () => {
    estado.visible = true;
    hook(null, { error: 'No hay ningún dispositivo de captura conectado.' });
    montar();
    expect(screen.getByRole('alert').textContent).toContain('No hay ningún dispositivo de captura conectado.');
  });

  it('en curso: Cancelar visible, Iniciar deshabilitado, headline del paso y textos por paso', () => {
    estado.visible = true;
    const h = hook(
      pasada(
        'EN_CURSO',
        pasos(['OK', 'EN_CURSO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'], [
          { iniciadoEn: 0, terminadoEn: 4000 },
          { estadoOrden: 'ENTREGADA', iniciadoEn: 4000 },
        ]),
      ),
    );

    montar();

    expect(btn(/Iniciar pasada/)!.disabled).toBe(true);
    fireEvent.click(btn(/Cancelar/)!);
    expect(h.cancelar).toHaveBeenCalled();
    expect(screen.getByText('En curso')).toBeTruthy();
    expect(screen.getByRole('status').textContent).toMatch(/Sacando la foto del sector MZ-1-001/);
    expect(screen.getByText('Riel → posición 1')).toBeTruthy();
    expect(screen.getByText('Riel → posición 2')).toBeTruthy();
    expect(screen.getByText('Foto del sector MZ-1-001')).toBeTruthy();
    expect(screen.getByText('Foto del sector MZ-1-002')).toBeTruthy();
    expect(screen.getByText('Riel → home')).toBeTruthy();
    expect(screen.getByText('El celular está sacando la foto')).toBeTruthy();
  });

  it('un movimiento en curso dice "Moviendo…" y el headline nombra el sector de destino', () => {
    estado.visible = true;
    hook(pasada('EN_CURSO', pasos(['EN_CURSO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'], [{ iniciadoEn: 0 }])));

    montar();

    expect(screen.getByText('Moviendo…')).toBeTruthy();
    expect(screen.getByRole('status').textContent).toMatch(/El riel se está moviendo al sector MZ-1-001/);
  });

  it('una orden PENDIENTE dice que espera al celular', () => {
    estado.visible = true;
    hook(
      pasada('EN_CURSO', pasos(['OK', 'EN_CURSO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'], [{}, { estadoOrden: 'PENDIENTE', iniciadoEn: 0 }])),
    );
    montar();
    expect(screen.getByText('Esperando al celular')).toBeTruthy();
  });

  it('con "cancelando…" no ofrece cancelar de nuevo', () => {
    estado.visible = true;
    hook(
      pasada('EN_CURSO', pasos(['OMITIDO', 'OMITIDO', 'OMITIDO', 'OMITIDO', 'EN_CURSO'], [{ detalle: 'Cancelada por el operador' }]), {
        cancelacionSolicitada: true,
      }),
    );

    montar();

    expect(btn(/Cancelar/)).toBeNull();
    expect(screen.getByText(/cancelando/i)).toBeTruthy();
    expect(screen.getByText('Cancelada por el operador')).toBeTruthy();
  });

  it('con la foto recibida muestra la miniatura y espera el diagnóstico', () => {
    estado.visible = true;
    hook(
      pasada('COMPLETADA', pasos(['OK', 'OK', 'OK', 'OK', 'OK'], [
        {},
        { estadoOrden: 'RECIBIDA', capturaId: 'CAP-1', imagenUrl: 'http://x/api/capturas/CAP-1/imagen' },
        {},
        { estadoOrden: 'RECIBIDA', capturaId: 'CAP-2', imagenUrl: 'http://x/api/capturas/CAP-2/imagen' },
      ])),
    );

    montar();

    const img = screen.getByAltText('Foto del sector MZ-1-001') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe('http://x/api/capturas/CAP-1/imagen');
    expect(screen.getAllByText(/Esperando diagnóstico de IA/)).toHaveLength(2);
    expect(screen.getAllByText(/se analiza tras 1 min sin fotos nuevas/)).toHaveLength(2);
    expect(screen.getByText('Completada')).toBeTruthy();
    expect(btn(/Cancelar/)).toBeNull();
    expect(btn(/Iniciar pasada/)!.disabled).toBe(false);
  });

  it('con diagnóstico muestra estado, confianza, severidad y el enlace a Diagnósticos de IA', () => {
    estado.visible = true;
    hook(
      pasada('COMPLETADA', pasos(['OK', 'OK', 'OK', 'OK', 'OK'], [
        {},
        {
          estadoOrden: 'RECIBIDA',
          capturaId: 'CAP-1',
          imagenUrl: '/a.jpg',
          diagnostico: { estado: 'Clorosis', conf: 87, sev: 'Media', creadoEn: 1 },
        },
      ])),
    );

    montar();

    const fila = screen.getByText('Foto del sector MZ-1-001').closest('li') as HTMLElement;
    expect(within(fila).getByText('Clorosis')).toBeTruthy();
    expect(within(fila).getByText(/87\s?%/)).toBeTruthy();
    expect(within(fila).getByText('Media')).toBeTruthy();
    expect(within(fila).queryByText(/Esperando diagnóstico/)).toBeNull();
    expect(within(fila).getByRole('link', { name: /Ver en Diagnósticos de IA/ }).getAttribute('href')).toBe('/diagnosticos');
  });

  it('un paso con error muestra el detalle del backend y la pasada fallida su banner', () => {
    estado.visible = true;
    const detalle = 'El riel no respondió. ¿El ESP32 está encendido y conectado al broker?';
    hook(
      pasada('FALLIDA', pasos(['ERROR', 'OMITIDO', 'OMITIDO', 'OMITIDO', 'OK'], [{ codigoError: 'RIEL_SIN_RESPUESTA', detalle }]), {
        error: detalle,
      }),
    );

    montar();

    expect(screen.getByText('Falló')).toBeTruthy();
    expect(screen.getAllByText(detalle).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Omitido')).toHaveLength(3);
  });

  it('una foto fallida muestra el detalle de ese paso', () => {
    estado.visible = true;
    hook(
      pasada('FALLIDA', pasos(['OK', 'ERROR', 'OK', 'OK', 'OK'], [{}, { estadoOrden: 'ERROR', detalle: 'El celular no pudo sacar la foto: CAMARA' }]), {
        error: 'El celular no pudo sacar la foto: CAMARA',
      }),
    );
    montar();
    expect(screen.getAllByText('El celular no pudo sacar la foto: CAMARA').length).toBeGreaterThanOrEqual(1);
  });
});

describe('DemoExpoPage · secuencias', () => {
  it('muestra la sección Secuencias debajo de la pasada, que sigue igual', () => {
    estado.visible = true;
    hook(pasada('COMPLETADA', pasos(['OK', 'OK', 'OK', 'OK', 'OK'])));

    montar();

    const titulo = screen.getByRole('heading', { name: 'Secuencias' });
    const ultimoPaso = screen.getByText('Riel → home').closest('li') as HTMLElement;
    expect(ultimoPaso.compareDocumentPosition(titulo) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(btn(/Iniciar pasada/)).not.toBeNull();
    expect(btn(/^Regar$/)!.disabled).toBe(false);
  });

  it('con la pasada en curso, las secuencias quedan deshabilitadas', () => {
    estado.visible = true;
    hook(pasada('EN_CURSO', pasos(['EN_CURSO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE'], [{ iniciadoEn: 0 }])));

    montar();

    expect(btn(/^Regar$/)!.disabled).toBe(true);
    expect(btn(/Leer sensores ahora/)!.disabled).toBe(true);
  });

  it('con el interruptor apagado no se muestra ni la sección', () => {
    estado.visible = false;
    hook(null);

    montar();

    expect(screen.queryByRole('heading', { name: 'Secuencias' })).toBeNull();
  });
});
