// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ComponentType } from 'react';
import { ConfiguracionPage } from './ConfiguracionPage';
import { PreferencesPage } from './PreferencesPage';
import { PageMetaProvider } from '@/hooks/PageMeta';
import { getRepository, PermisoDenegadoError } from '@/data';
import { ConPermisos } from '@/test/sesion';
import type { Rol } from '@/types/seguridad';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

const montar = (Pagina: ComponentType, rol: Rol = 'INGENIERO_AGRONOMO') =>
  render(
    <MemoryRouter>
      <ConPermisos rol={rol}>
        <PageMetaProvider>
          <Pagina />
        </PageMetaProvider>
      </ConPermisos>
    </MemoryRouter>,
  );

const guardar = () => screen.getByRole('button', { name: /Guardar cambios/ }) as HTMLButtonElement;
const barra = () => screen.queryByRole('region', { name: 'Cambios sin guardar' });

/** Ensucia el borrador de Umbrales con un cambio válido. */
const ensuciarUmbrales = async () => {
  const campo = (await screen.findByLabelText('Latencia de seguimiento')) as HTMLInputElement;
  fireEvent.change(campo, { target: { value: String(Number(campo.value) + 1) } });
};

describe('Umbrales (ConfiguracionPage)', () => {
  it('trae umbrales, insumos, seguimiento y mediasombra; la rustificación y las frecuencias ya no', async () => {
    montar(ConfiguracionPage);

    await screen.findByText('Umbrales de métricas');
    expect(screen.getByText('Límites de insumos')).toBeTruthy();
    expect(screen.getByText('Seguimiento post-acción')).toBeTruthy();
    expect(screen.getByText('Mediasombra')).toBeTruthy();
    expect(screen.queryByText('Plan de rustificación')).toBeNull();
    expect(screen.queryByText('Frecuencia operativa')).toBeNull();
    expect(screen.queryByRole('switch', { name: /Mostrar Demo Expo/ })).toBeNull();
    expect(screen.getByRole('link', { name: /Motor de reglas/ })).toBeTruthy();
  });

  it('no depende del catálogo del motor para guardar', async () => {
    vi.spyOn(getRepository(), 'getCatalogoReglas').mockReturnValue(new Promise(() => undefined));
    montar(ConfiguracionPage);

    await ensuciarUmbrales();

    expect(guardar().disabled).toBe(false);
  });

  it('la barra de guardado cuenta los cambios y "Descartar" vuelve a lo guardado', async () => {
    montar(ConfiguracionPage);
    const campo = (await screen.findByLabelText('Delta mín. de recuperación')) as HTMLInputElement;
    const antes = campo.value;
    expect(barra()).toBeNull();

    fireEvent.change(campo, { target: { value: String(Number(antes) + 1) } });
    await ensuciarUmbrales();

    expect(barra()!.textContent).toContain('2 cambios sin guardar');
    fireEvent.click(screen.getByRole('button', { name: 'Descartar' }));

    expect(campo.value).toBe(antes);
    expect(barra()).toBeNull();
  });

  it('un valor inválido bloquea Guardar', async () => {
    montar(ConfiguracionPage);
    const campo = await screen.findByLabelText('Latencia de seguimiento');

    fireEvent.change(campo, { target: { value: '0' } });

    expect(guardar().disabled).toBe(true);
    expect(barra()!.textContent).toMatch(/para corregir/);
  });

  it('restablecer de fábrica sólo toca lo de la vista', async () => {
    montar(ConfiguracionPage);
    const campo = (await screen.findByLabelText('Latencia de seguimiento')) as HTMLInputElement;
    const fabrica = campo.value;
    fireEvent.change(campo, { target: { value: '9' } });

    fireEvent.click(screen.getByRole('button', { name: /Restablecer/ }));

    await waitFor(() => expect(campo.value).toBe(fabrica));
    expect(barra()).toBeNull();
  });

  it('edita el horario por defecto de la mediasombra y bloquea un cierre anterior a la apertura', async () => {
    montar(ConfiguracionPage);
    const desde = (await screen.findByLabelText('Abierta desde')) as HTMLInputElement;
    const hasta = screen.getByLabelText('Abierta hasta') as HTMLInputElement;
    expect([desde.value, hasta.value]).toEqual(['08:00', '17:00']);

    fireEvent.change(hasta, { target: { value: '07:00' } });

    expect(screen.getByRole('alert').textContent).toMatch(/cierre debe ser posterior/);
    expect(guardar().disabled).toBe(true);
  });

  it('sin configuracion.editar: campos deshabilitados y sin botones de guardar (7.5)', async () => {
    montar(ConfiguracionPage, 'PRODUCTOR_VIVERISTA');

    const campo = await screen.findByLabelText('Latencia de seguimiento');
    // Lo deshabilita el <fieldset disabled> que lo envuelve: la propiedad del input no cambia.
    expect(campo.matches(':disabled')).toBe(true);
    expect(screen.queryByRole('button', { name: /Guardar cambios/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Restablecer valores de fábrica/ })).toBeNull();
    expect(screen.getByText(/Sólo lectura/)).toBeTruthy();
  });

  it('un 403 al guardar muestra el aviso uniforme y no da el cambio por aplicado (7.5)', async () => {
    vi.spyOn(getRepository(), 'saveConfig').mockRejectedValue(new PermisoDenegadoError('configuracion.editar'));
    montar(ConfiguracionPage);
    await ensuciarUmbrales();

    fireEvent.click(guardar());

    expect(await screen.findByText('No tenés permiso para esta acción.')).toBeTruthy();
    expect(screen.queryByText('Configuración guardada correctamente.')).toBeNull();
  });
});

describe('Preferencias (PreferencesPage)', () => {
  // El intervalo de fábrica (240 min) se muestra en horas.
  const pulseField = async () =>
    (await screen.findByLabelText('Intervalo de sensado y evaluación')) as HTMLInputElement;
  const hourField = async () => (await screen.findByLabelText('Hora de la pasada')) as HTMLInputElement;

  it('trae la frecuencia operativa y la pasada diaria', async () => {
    montar(PreferencesPage);

    expect(await screen.findByText('Frecuencia operativa')).toBeTruthy();
    expect(screen.getByText('Pasada diaria del riel')).toBeTruthy();
    expect(screen.queryByRole('switch', { name: /Mostrar Demo Expo/ })).toBeNull();
    expect(screen.queryByText('Umbrales de métricas')).toBeNull();
  });

  it('no hay intervalo de evaluación aparte ni pedido de lectura en segundos', async () => {
    montar(PreferencesPage);
    await pulseField();

    expect(screen.queryByLabelText(/Intervalo de Evaluación/i)).toBeNull();
    expect(screen.queryByLabelText(/frecuencia de pedido de lectura/i)).toBeNull();
  });

  it.each([
    ['0.5', '30 min'],
    ['7', '420 min'],
  ])('con %s h (%s) el intervalo queda inválido y Guardar se deshabilita', async (horas) => {
    montar(PreferencesPage);
    const campo = await pulseField();

    fireEvent.change(campo, { target: { value: horas } });

    expect(campo.getAttribute('aria-invalid')).toBe('true');
    expect(guardar().disabled).toBe(true);
  });

  it('restablecer vuelve el intervalo a 4 h y apaga la pasada diaria', async () => {
    montar(PreferencesPage);
    const campo = await pulseField();
    fireEvent.change(campo, { target: { value: '2' } });
    fireEvent.click(screen.getByRole('switch', { name: 'Pasada diaria activa' }));
    expect(campo.value).toBe('2');

    fireEvent.click(screen.getByRole('button', { name: /Restablecer/ }));

    await waitFor(() => expect(campo.value).toBe('4'));
    expect(screen.getByRole('switch', { name: 'Pasada diaria activa' }).getAttribute('aria-checked')).toBe('false');
  });

  it('la hora vacía queda inválida y Guardar se deshabilita', async () => {
    montar(PreferencesPage);
    const hora = await hourField();

    fireEvent.change(hora, { target: { value: '' } });

    expect(hora.getAttribute('aria-invalid')).toBe('true');
    expect(guardar().disabled).toBe(true);
  });

  it('la hora se edita aunque la pasada esté desactivada', async () => {
    montar(PreferencesPage);
    const hora = await hourField();
    expect(screen.getByRole('switch', { name: 'Pasada diaria activa' }).getAttribute('aria-checked')).toBe('false');

    expect(hora.disabled).toBe(false);
    fireEvent.change(hora, { target: { value: '09:15' } });
    expect(hora.value).toBe('09:15');
  });

  // Los dos que guardan van al final: el repositorio mock es único y conserva lo guardado.
  it('muestra el umbral de lectura vieja del backend y lo refresca al guardar', async () => {
    montar(PreferencesPage);
    expect(await screen.findByText(/lectura vieja.*14580 s/)).toBeTruthy();

    fireEvent.change(await pulseField(), { target: { value: '2' } });
    fireEvent.click(guardar());

    expect(await screen.findByText(/lectura vieja.*7380 s/)).toBeTruthy();
  });

  it('activar la pasada a las 10:30 y guardar se lo manda a saveConfig', async () => {
    const save = vi.spyOn(getRepository(), 'saveConfig');
    montar(PreferencesPage);
    const hora = await hourField();
    expect(hora.value).toBe('11:00');

    fireEvent.click(screen.getByRole('switch', { name: 'Pasada diaria activa' }));
    fireEvent.change(hora, { target: { value: '10:30' } });
    fireEvent.click(guardar());

    await waitFor(() => expect(save).toHaveBeenCalled());
    expect(save.mock.calls[0][0].operativa).toMatchObject({ pasadaDiariaActiva: true, pasadaDiariaHora: '10:30' });
  });
});
