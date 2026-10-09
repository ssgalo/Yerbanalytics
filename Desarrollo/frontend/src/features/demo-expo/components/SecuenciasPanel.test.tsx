// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SecuenciasPanel } from './SecuenciasPanel';
import type {
  EstadoPaso,
  PasoSecuencia,
  Secuencia,
  TipoPasoSecuencia,
  TipoSecuencia,
} from '@/types/domain';

const estado = vi.hoisted(() => ({ hook: {} as Record<string, unknown> }));
vi.mock('@/hooks/useSecuencia', () => ({ useSecuencia: () => estado.hook }));

afterEach(cleanup);

const paso = (
  n: number,
  tipo: TipoPasoSecuencia,
  e: EstadoPaso,
  over: Partial<PasoSecuencia> = {},
): PasoSecuencia => ({
  n,
  tipo,
  estado: e,
  codigoError: null,
  detalle: null,
  commandId: null,
  esperaHasta: null,
  iniciadoEn: null,
  terminadoEn: null,
  ...over,
});

const TIPOS: Record<TipoSecuencia, TipoPasoSecuencia[]> = {
  RIEGO: ['ABRIR', 'ESPERAR', 'CERRAR'],
  MEDIASOMBRA: ['DESPLEGAR', 'ESPERAR', 'ENROLLAR'],
  LECTURA: ['PEDIR', 'ESPERAR_TELEMETRIA', 'MOSTRAR'],
};

const secuencia = (
  tipo: TipoSecuencia,
  e: Secuencia['estado'],
  estados: EstadoPaso[],
  over: Partial<Secuencia> = {},
  overPasos: Partial<PasoSecuencia>[] = [],
): Secuencia => ({
  id: 's',
  tipo,
  estado: e,
  zonaId: 'MZ-1',
  sectorId: tipo === 'LECTURA' ? null : 'MZ-1-001',
  parametros: { duracionSeg: 15, esperaSeg: 8 },
  iniciadaEn: 1,
  finalizadaEn: e === 'EN_CURSO' ? null : 2,
  cancelacionSolicitada: false,
  error: null,
  lectura: null,
  pasos: TIPOS[tipo].map((t, i) => paso(i + 1, t, estados[i], overPasos[i])),
  ...over,
});

const hook = (s: Secuencia | null, over: Record<string, unknown> = {}) => {
  estado.hook = {
    secuencia: s,
    cargando: false,
    error: null,
    iniciando: false,
    iniciar: vi.fn(),
    cancelar: vi.fn(),
    descartarError: vi.fn(),
    ...over,
  };
  return estado.hook as {
    iniciar: ReturnType<typeof vi.fn>;
    cancelar: ReturnType<typeof vi.fn>;
    descartarError: ReturnType<typeof vi.fn>;
  };
};

const tree = (pasadaEnCurso: boolean) => (
  <MemoryRouter>
    <SecuenciasPanel pasadaEnCurso={pasadaEnCurso} />
  </MemoryRouter>
);
const montar = (pasadaEnCurso = false) => render(tree(pasadaEnCurso));

const btn = (nombre: RegExp) =>
  screen.queryByRole('button', { name: nombre }) as HTMLButtonElement | null;

describe('SecuenciasPanel', () => {
  it('muestra una tarjeta por tipo con su botón habilitado', () => {
    hook(null);
    montar();

    expect(screen.getByRole('heading', { name: 'Secuencias' })).toBeTruthy();
    expect(screen.getByText('Riego')).toBeTruthy();
    expect(screen.getByText('Mediasombra')).toBeTruthy();
    expect(screen.getByText('Lectura')).toBeTruthy();
    expect(btn(/^Regar$/)!.disabled).toBe(false);
    expect(btn(/Desplegar y enrollar/)!.disabled).toBe(false);
    expect(btn(/Leer sensores ahora/)!.disabled).toBe(false);
    expect(btn(/Cancelar/)).toBeNull();
  });

  it('riego: envía duracionSeg (default 10 y el valor tipeado)', () => {
    const h = hook(null);
    montar();

    fireEvent.click(btn(/^Regar$/)!);
    expect(h.iniciar).toHaveBeenLastCalledWith('RIEGO', { duracionSeg: 10 });

    fireEvent.change(screen.getByLabelText('Segundos'), { target: { value: '25' } });
    fireEvent.click(btn(/^Regar$/)!);
    expect(h.iniciar).toHaveBeenLastCalledWith('RIEGO', { duracionSeg: 25 });
  });

  it('mediasombra: envía esperaSeg (default 10 y el valor tipeado)', () => {
    const h = hook(null);
    montar();

    fireEvent.click(btn(/Desplegar y enrollar/)!);
    expect(h.iniciar).toHaveBeenLastCalledWith('MEDIASOMBRA', { esperaSeg: 10 });

    fireEvent.change(screen.getByLabelText('Espera desplegada (s)'), { target: { value: '0' } });
    fireEvent.click(btn(/Desplegar y enrollar/)!);
    expect(h.iniciar).toHaveBeenLastCalledWith('MEDIASOMBRA', { esperaSeg: 0 });
  });

  it('lectura: no manda parámetros', () => {
    const h = hook(null);
    montar();
    fireEvent.click(btn(/Leer sensores ahora/)!);
    expect(h.iniciar).toHaveBeenCalledWith('LECTURA');
  });

  it('un valor fuera de rango deshabilita el botón de esa tarjeta', () => {
    hook(null);
    montar();

    fireEvent.change(screen.getByLabelText('Segundos'), { target: { value: '500' } });
    expect(btn(/^Regar$/)!.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Segundos'), { target: { value: '' } });
    expect(btn(/^Regar$/)!.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText('Espera desplegada (s)'), { target: { value: '601' } });
    expect(btn(/Desplegar y enrollar/)!.disabled).toBe(true);
    expect(btn(/Leer sensores ahora/)!.disabled).toBe(false);
  });

  it('con una secuencia en curso los tres botones quedan deshabilitados y aparece Cancelar', () => {
    const h = hook(
      secuencia('RIEGO', 'EN_CURSO', ['OK', 'EN_CURSO', 'PENDIENTE'], {}, [
        {},
        { esperaHasta: Date.now() + 9000, iniciadoEn: 0 },
      ]),
    );
    montar();

    expect(btn(/^Regar$/)!.disabled).toBe(true);
    expect(btn(/Desplegar y enrollar/)!.disabled).toBe(true);
    expect(btn(/Leer sensores ahora/)!.disabled).toBe(true);
    fireEvent.click(btn(/Cancelar/)!);
    expect(h.cancelar).toHaveBeenCalled();
    expect(screen.getByText('En curso')).toBeTruthy();
    expect(screen.getByText('Abrir la válvula de MZ-1-001')).toBeTruthy();
    expect(screen.getByText('Regar durante 15 s')).toBeTruthy();
    expect(screen.getByText('Cerrar la válvula')).toBeTruthy();
  });

  it('con una pasada del riel en curso también quedan deshabilitados', () => {
    hook(null);
    montar(true);
    expect(btn(/^Regar$/)!.disabled).toBe(true);
    expect(btn(/Desplegar y enrollar/)!.disabled).toBe(true);
    expect(btn(/Leer sensores ahora/)!.disabled).toBe(true);
  });

  it('mientras inicia, los botones quedan deshabilitados', () => {
    hook(null, { iniciando: true });
    montar();
    expect(btn(/Leer sensores ahora/)!.disabled).toBe(true);
  });

  it('cancelando…: sin segundo Cancelar y con la leyenda', () => {
    hook(
      secuencia(
        'MEDIASOMBRA',
        'EN_CURSO',
        ['OMITIDO', 'OMITIDO', 'EN_CURSO'],
        { cancelacionSolicitada: true },
        [{ detalle: 'Cancelada por el operador' }],
      ),
    );
    montar();

    expect(btn(/Cancelar/)).toBeNull();
    expect(screen.getByText(/cancelando/i)).toBeTruthy();
    expect(screen.getAllByText('Cancelada por el operador').length).toBeGreaterThanOrEqual(1);
  });

  it('terminada: sin Cancelar y con los botones habilitados otra vez', () => {
    hook(secuencia('RIEGO', 'COMPLETADA', ['OK', 'OK', 'OK']));
    montar();

    expect(screen.getByText('Completada')).toBeTruthy();
    expect(btn(/Cancelar/)).toBeNull();
    expect(btn(/^Regar$/)!.disabled).toBe(false);
  });

  it('un paso con error muestra el detalle del backend y la secuencia fallida su banner', () => {
    const detalle =
      'La válvula no respondió en 10 s. ¿El ESP32 está encendido y conectado al broker?';
    hook(
      secuencia('RIEGO', 'FALLIDA', ['ERROR', 'OMITIDO', 'OK'], { error: detalle }, [
        { codigoError: 'ACTUADOR_SIN_RESPUESTA', detalle },
      ]),
    );
    montar();

    expect(screen.getByText('Falló')).toBeTruthy();
    expect(screen.getAllByText(detalle).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('Omitido')).toBeTruthy();
  });

  it('muestra el error de un intento (409 del backend) tal cual', () => {
    hook(null, { error: 'Hay una pasada del riel en curso.' });
    montar();
    expect(screen.getByRole('alert').textContent).toContain('Hay una pasada del riel en curso.');
  });

  it('lectura completa: tabla con las métricas no nulas y el link al Inspector', () => {
    hook(
      secuencia('LECTURA', 'COMPLETADA', ['OK', 'OK', 'OK'], {
        lectura: {
          recibidaEn: 5,
          metricas: {
            humSus: 41,
            humAmb: 63,
            temp: 22.5,
            tempSuelo: null,
            uv: 78,
            ce: 1.2,
            phSuelo: null,
            n: null,
            p: null,
            k: null,
          },
        },
      }),
    );
    montar();

    const tabla = screen.getByRole('table');
    expect(within(tabla).getAllByRole('row')).toHaveLength(5);
    expect(within(tabla).getByText('Luz (%)')).toBeTruthy();
    expect(within(tabla).getByText('78')).toBeTruthy();
    expect(within(tabla).getByText('Conductividad (CE)')).toBeTruthy();
    expect(within(tabla).getByText('1,2')).toBeTruthy();
    expect(within(tabla).getByText('dS/m')).toBeTruthy();
    expect(within(tabla).queryByText('pH del sustrato')).toBeNull();
    expect(
      screen.getByRole('link', { name: /Ver la evaluación en el Inspector/ }).getAttribute('href'),
    ).toBe('/reglas');
  });

  it('lectura sin valores todavía: no hay tabla ni link', () => {
    hook(secuencia('LECTURA', 'EN_CURSO', ['OK', 'EN_CURSO', 'PENDIENTE']));
    montar();
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('link', { name: /Inspector/ })).toBeNull();
    expect(screen.getByText('Pedir lectura a MZ-1')).toBeTruthy();
    expect(screen.getByText('Esperando la telemetría…')).toBeTruthy();
  });

  it('descarta el error de un intento cuando la pasada deja de bloquear', () => {
    const h = hook(null, { error: 'Hay una pasada del riel en curso.' });
    const { rerender } = montar(true);
    h.descartarError.mockClear();

    rerender(tree(false));

    expect(h.descartarError).toHaveBeenCalled();
  });

  it('no descarta el error mientras nada cambie', () => {
    const h = hook(null, { error: 'Hay una pasada del riel en curso.' });
    const { rerender } = montar(false);
    h.descartarError.mockClear();

    rerender(tree(false));

    expect(h.descartarError).not.toHaveBeenCalled();
  });
});
