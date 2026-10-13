// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ComponentType } from 'react';
import { ConfiguracionPage } from './ConfiguracionPage';
import { PreferencesPage } from './PreferencesPage';
import { PageMetaProvider } from '@/hooks/PageMeta';
import { DemoExpoProvider } from '@/hooks/DemoExpoContext';
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
          <DemoExpoProvider>
            <Pagina />
          </DemoExpoProvider>
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
    expect([desde.value, hasta.value]).toEqual(['08:00', '18:00']);

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
  const pullField = async () =>
    (await screen.findByLabelText(/frecuencia de pedido de lectura/i)) as HTMLInputElement;

  it('trae la frecuencia operativa y el interruptor de Demo Expo', async () => {
    montar(PreferencesPage);

    expect(await screen.findByText('Frecuencia operativa')).toBeTruthy();
    expect(screen.getByRole('switch', { name: /Mostrar Demo Expo/ })).toBeTruthy();
    expect(screen.queryByText('Umbrales de métricas')).toBeNull();
  });

  it('usar el interruptor de Demo Expo no ensucia el borrador', async () => {
    montar(PreferencesPage, 'ADMINISTRADOR');
    const sw = (await screen.findByRole('switch', { name: /Mostrar Demo Expo/ })) as HTMLButtonElement;
    await waitFor(() => expect(sw.disabled).toBe(false));

    fireEvent.click(sw);

    await waitFor(() => expect(sw.getAttribute('aria-checked')).toBe('true'));
    expect(barra()).toBeNull();
  });

  it('sin demo-expo.configurar el interruptor queda deshabilitado con el motivo (7.5)', async () => {
    montar(PreferencesPage, 'INGENIERO_AGRONOMO');

    const sw = (await screen.findByRole('switch', { name: /Mostrar Demo Expo/ })) as HTMLButtonElement;
    expect(screen.getByText('Tu rol no puede cambiar esta preferencia')).toBeTruthy();
    expect(sw.disabled).toBe(true);
  });

  it('disables Save when the node read request frequency is out of range', async () => {
    montar(PreferencesPage);

    fireEvent.change(await pullField(), { target: { value: '5' } });

    expect(guardar().disabled).toBe(true);
  });

  it('factory reset brings the read request frequency back to 60', async () => {
    montar(PreferencesPage);
    const field = await pullField();
    fireEvent.change(field, { target: { value: '30' } });
    expect(field.value).toBe('30');

    fireEvent.click(screen.getByRole('button', { name: /Restablecer/ }));

    await waitFor(() => expect(field.value).toBe('60'));
  });

  it('refreshes the backend-computed threshold hint after saving', async () => {
    montar(PreferencesPage);
    expect(await screen.findByText(/lectura vieja.*180 s/)).toBeTruthy();

    fireEvent.change(await pullField(), { target: { value: '120' } });
    fireEvent.click(guardar());

    expect(await screen.findByText(/lectura vieja.*360 s/)).toBeTruthy();
  });
});
