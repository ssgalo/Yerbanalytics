// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, renderHook } from '@testing-library/react';
import { useInactividad, type CanalActividad, type MensajeActividad } from './useInactividad';

beforeEach(() => vi.useFakeTimers());
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

const MIN = 60_000;

/** Un "BroadcastChannel" en memoria: lo que publica una pestaña lo reciben las demás (no ella). */
function bus() {
  const canales: CanalActividad[] = [];
  const crear = (): CanalActividad => {
    const c: CanalActividad = {
      onmessage: null,
      postMessage: (m: MensajeActividad) =>
        canales.filter((o) => o !== c).forEach((o) => o.onmessage?.({ data: m } as MessageEvent<MensajeActividad>)),
      close: () => canales.splice(canales.indexOf(c), 1),
    };
    canales.push(c);
    return c;
  };
  return crear;
}

interface Opciones {
  inactividadMin?: number;
  crearCanal?: () => CanalActividad | null;
  objetivo?: EventTarget;
}

function montar(opciones: Opciones = {}) {
  const registrarActividad = vi.fn().mockResolvedValue(undefined);
  const alVencer = vi.fn();
  const hook = renderHook(() =>
    useInactividad({
      inactividadMin: opciones.inactividadMin ?? 60,
      registrarActividad,
      alVencer,
      crearCanal: opciones.crearCanal ?? (() => null),
      objetivo: opciones.objetivo,
    }),
  );
  return { ...hook, registrarActividad, alVencer };
}

const avanzar = (ms: number) => act(() => vi.advanceTimersByTime(ms));

describe('useInactividad (6.5)', () => {
  it('sin interacción no manda ninguna señal de actividad, por más tiempo que pase', () => {
    const { registrarActividad } = montar();
    avanzar(30 * MIN);
    expect(registrarActividad).not.toHaveBeenCalled();
  });

  it('la interacción manda la señal como mucho una vez por minuto', () => {
    const { registrarActividad } = montar();

    fireEvent.pointerDown(document.body);
    fireEvent.keyDown(document.body, { key: 'a' });
    fireEvent.wheel(document.body);
    expect(registrarActividad).toHaveBeenCalledTimes(1);

    avanzar(30_000);
    fireEvent.scroll(document.body);
    expect(registrarActividad).toHaveBeenCalledTimes(1);

    avanzar(31_000);
    fireEvent.touchStart(document.body);
    expect(registrarActividad).toHaveBeenCalledTimes(2);
  });

  it('avisa un minuto antes y cierra al vencer', () => {
    const { result, alVencer } = montar({ inactividadMin: 5 });

    avanzar(3 * MIN + 59_000);
    expect(result.current.aviso).toBe(false);

    avanzar(2000);
    expect(result.current.aviso).toBe(true);
    expect(result.current.segundosRestantes).toBeLessThanOrEqual(60);
    expect(alVencer).not.toHaveBeenCalled();

    avanzar(60_000);
    expect(alVencer).toHaveBeenCalledTimes(1);
    avanzar(10 * MIN);
    expect(alVencer).toHaveBeenCalledTimes(1);
  });

  it('interactuar durante el aviso lo retira y reinicia la cuenta', () => {
    const { result, alVencer } = montar({ inactividadMin: 5 });
    avanzar(4 * MIN + 30_000);
    expect(result.current.aviso).toBe(true);

    act(() => result.current.mantenerViva());

    expect(result.current.aviso).toBe(false);
    avanzar(4 * MIN);
    expect(alVencer).not.toHaveBeenCalled();
  });

  it('dos pestañas comparten la actividad: la de atrás no vence mientras se usa la de adelante', () => {
    // Cada "pestaña" escucha su propio documento; sólo se interactúa con la de adelante.
    const crearCanal = bus();
    const docAdelante = document.createElement('div');
    const docAtras = document.createElement('div');
    const adelante = montar({ inactividadMin: 5, crearCanal, objetivo: docAdelante });
    const atras = montar({ inactividadMin: 5, crearCanal, objetivo: docAtras });

    for (let i = 0; i < 10; i++) {
      avanzar(2 * MIN);
      act(() => {
        fireEvent.pointerDown(docAdelante);
      });
    }

    expect(adelante.alVencer).not.toHaveBeenCalled();
    expect(atras.alVencer).not.toHaveBeenCalled();
  });

  it('sin el canal, la pestaña de atrás vence aunque se use la otra (control del test anterior)', () => {
    const docAdelante = document.createElement('div');
    montar({ inactividadMin: 5, objetivo: docAdelante });
    const atras = montar({ inactividadMin: 5, objetivo: document.createElement('div') });

    for (let i = 0; i < 10; i++) {
      avanzar(2 * MIN);
      act(() => {
        fireEvent.pointerDown(docAdelante);
      });
    }

    expect(atras.alVencer).toHaveBeenCalled();
  });

  it('el ping se comparte: si una pestaña ya avisó al backend, la otra no repite dentro del minuto', () => {
    const crearCanal = bus();
    const docA = document.createElement('div');
    const docB = document.createElement('div');
    const a = montar({ crearCanal, objetivo: docA });
    const b = montar({ crearCanal, objetivo: docB });

    fireEvent.pointerDown(docA);
    avanzar(20_000);
    fireEvent.pointerDown(docB);

    expect(a.registrarActividad).toHaveBeenCalledTimes(1);
    expect(b.registrarActividad).not.toHaveBeenCalled();

    avanzar(41_000);
    fireEvent.pointerDown(docB);
    expect(b.registrarActividad).toHaveBeenCalledTimes(1);
  });
});
