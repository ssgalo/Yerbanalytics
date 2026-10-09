// @vitest-environment jsdom
/* Recorrido de punta a punta de la demo estática: panel + useSecuencia + MockRepository reales,
   con el reloj simulado. Cubre por tests lo que B.8 pide mirar a mano en `npm run dev:demo`. */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { SecuenciasPanel } from './SecuenciasPanel';
import * as data from '@/data';
import { MockRepository } from '@/data/mock/mockRepository';

let repo: MockRepository;

beforeEach(() => {
  vi.useFakeTimers({ now: 1_000_000_000 });
  repo = new MockRepository(1);
  vi.spyOn(data, 'getRepository').mockReturnValue(repo);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

const montar = async () => {
  render(
    <MemoryRouter>
      <SecuenciasPanel pasadaEnCurso={false} />
    </MemoryRouter>,
  );
  await act(async () => vi.advanceTimersByTimeAsync(0));
};
const avanzar = async (s: number) => {
  for (let t = 0; t < s; t++) await act(async () => vi.advanceTimersByTimeAsync(1000));
};
const clic = async (nombre: RegExp) => {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: nombre }));
  });
};

describe('Secuencias · demo estática', () => {
  it('riego completo: abre, espera con cuenta regresiva, cierra y completa', async () => {
    await montar();
    fireEvent.change(screen.getByLabelText('Segundos'), { target: { value: '5' } });

    await clic(/^Regar$/);
    expect(screen.getByText('En curso')).toBeTruthy();
    expect(screen.getByText('Abriendo…')).toBeTruthy();

    await avanzar(3);
    expect(screen.getByText(/Faltan \d s/)).toBeTruthy();

    await avanzar(6);
    expect(screen.getByText('Completada')).toBeTruthy();
    expect(screen.getByText('Válvula cerrada')).toBeTruthy();
    expect((screen.getByRole('button', { name: /^Regar$/ }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('mediasombra: cancelar a mitad omite la espera y enrolla', async () => {
    await montar();

    await clic(/Desplegar y enrollar/);
    await avanzar(5);
    await clic(/Cancelar/);

    expect(screen.getByText(/cancelando/i)).toBeTruthy();
    expect(screen.getByText('Cancelada por el operador')).toBeTruthy();
    expect(screen.getByText('Enrollando…')).toBeTruthy();

    await avanzar(5);
    expect(screen.getByText('Cancelada')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Cancelar/ })).toBeNull();
  });

  it('lectura: llega la tabla con los valores de la zona y el link al Inspector', async () => {
    await montar();

    await clic(/Leer sensores ahora/);
    expect(screen.queryByRole('table')).toBeNull();

    await avanzar(4);
    expect(screen.getByText('Completada')).toBeTruthy();
    expect(screen.getByRole('table')).toBeTruthy();
    expect(screen.getByRole('link', { name: /Inspector/ }).getAttribute('href')).toBe('/reglas');
  });

  it('con la pasada del riel en curso, iniciar una secuencia muestra el rechazo del backend', async () => {
    await montar();
    await repo.iniciarPasada();

    await clic(/^Regar$/);

    expect(screen.getByRole('alert').textContent).toContain('Hay una pasada del riel en curso.');
  });

  it('con una secuencia en curso, la pasada se rechaza con el mensaje del backend', async () => {
    await montar();
    await clic(/Leer sensores ahora/);

    await expect(repo.iniciarPasada()).rejects.toThrow('Hay una secuencia de LECTURA en curso.');
  });
});
