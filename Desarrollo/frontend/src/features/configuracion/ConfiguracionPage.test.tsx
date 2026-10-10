// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ConfiguracionPage } from './ConfiguracionPage';
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

const montar = (rol: Rol = 'INGENIERO_AGRONOMO') =>
  render(
    <MemoryRouter>
      <ConPermisos rol={rol}>
        <PageMetaProvider>
          <DemoExpoProvider>
            <ConfiguracionPage />
          </DemoExpoProvider>
        </PageMetaProvider>
      </ConPermisos>
    </MemoryRouter>,
  );

describe('ConfiguracionPage', () => {
  it('carga sin los dos límites mudados y con el enlace al motor de reglas', async () => {
    montar();

    await screen.findByText('Umbrales de métricas');
    expect(screen.queryByLabelText(/Tiempo máx\. de apertura de riego/)).toBeNull();
    expect(screen.getByRole('link', { name: /Motor de reglas/ })).toBeTruthy();
  });

  it('tiene el interruptor de Demo Expo y usarlo no ensucia el borrador del formulario', async () => {
    montar('ADMINISTRADOR');
    await waitFor(() => expect(screen.queryByText(/Cargando la apertura máxima/)).toBeNull());
    const sw = (await screen.findByRole('switch', { name: /Mostrar Demo Expo/ })) as HTMLInputElement;
    await waitFor(() => expect(sw.disabled).toBe(false));

    fireEvent.click(sw);

    await waitFor(() => expect(sw.checked).toBe(true));
    expect((screen.getByRole('button', { name: /Guardar cambios/ }) as HTMLButtonElement).disabled).toBe(true);
  });

  /** Ensucia el borrador con un cambio válido ajeno a la rustificación. */
  const ensuciar = async () => {
    const campo = (await screen.findByLabelText('Latencia de seguimiento')) as HTMLInputElement;
    fireEvent.change(campo, { target: { value: String(Number(campo.value) + 1) } });
  };
  const guardar = () => screen.getByRole('button', { name: /Guardar cambios/ }) as HTMLButtonElement;

  it('con un borrador sucio y válido, Guardar se habilita (control del test siguiente)', async () => {
    montar();
    await waitFor(() => expect(screen.queryByText(/Cargando la apertura máxima/)).toBeNull());

    await ensuciar();

    expect(guardar().disabled).toBe(false);
  });

  it('valida el plan de rustificación contra la apertura máxima del catálogo del motor', async () => {
    const repo = getRepository();
    await repo.saveParametros([{ clave: 'mediasombra.apertura-maxima', valor: '50' }]);
    try {
      montar();
      await waitFor(() => expect(screen.getByText(/apertura máxima \(50 %\)/)).toBeTruthy());

      // El borrador está sucio y todo lo demás es válido: sólo la rustificación (100 % > 50 %)
      // puede deshabilitar Guardar. Si se quita esa validación, este test falla.
      await ensuciar();

      expect(guardar().disabled).toBe(true);
    } finally {
      await repo.saveParametros([{ clave: 'mediasombra.apertura-maxima', valor: null }]);
    }
  });

  it('mientras el catálogo carga muestra el estado y no deja guardar contra un máximo supuesto', async () => {
    vi.spyOn(getRepository(), 'getCatalogoReglas').mockReturnValue(new Promise(() => undefined));
    montar();

    expect(await screen.findByText(/Cargando la apertura máxima/)).toBeTruthy();
    await ensuciar();

    expect(guardar().disabled).toBe(true);
  });

  it('si el catálogo falla avisa, bloquea Guardar y "Reintentar" lo vuelve a pedir', async () => {
    const espia = vi
      .spyOn(getRepository(), 'getCatalogoReglas')
      .mockRejectedValueOnce(new Error('Error 503 al leer los parámetros del motor'));
    montar();

    expect(await screen.findByText(/No se pudo obtener la apertura máxima/)).toBeTruthy();
    await ensuciar();
    expect(guardar().disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(screen.queryByText(/No se pudo obtener la apertura máxima/)).toBeNull());
    expect(espia).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(guardar().disabled).toBe(false));
  });
  it('sin configuracion.editar: campos deshabilitados y sin botones de guardar (7.5)', async () => {
    montar('PRODUCTOR_VIVERISTA');

    const campo = await screen.findByLabelText('Latencia de seguimiento');
    // Lo deshabilita el <fieldset disabled> que lo envuelve: la propiedad del input no cambia.
    expect(campo.matches(':disabled')).toBe(true);
    expect(screen.queryByRole('button', { name: /Guardar cambios/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Restablecer valores de fábrica/ })).toBeNull();
    expect(screen.getByText(/Sólo lectura/)).toBeTruthy();
  });

  it('sin demo-expo.configurar el interruptor de Demo Expo queda deshabilitado con el motivo (7.5)', async () => {
    montar('INGENIERO_AGRONOMO');

    const sw = (await screen.findByRole('switch', { name: /Mostrar Demo Expo/ })) as HTMLInputElement;
    expect(screen.getByText('Tu rol no puede cambiar esta preferencia')).toBeTruthy();
    expect(sw.disabled).toBe(true);
  });

  it('un 403 al guardar muestra el aviso uniforme y no da el cambio por aplicado (7.5)', async () => {
    vi.spyOn(getRepository(), 'saveConfig').mockRejectedValue(new PermisoDenegadoError('configuracion.editar'));
    montar('INGENIERO_AGRONOMO');
    await waitFor(() => expect(screen.queryByText(/Cargando la apertura máxima/)).toBeNull());
    await ensuciar();

    fireEvent.click(guardar());

    expect(await screen.findByText('No tenés permiso para esta acción.')).toBeTruthy();
    expect(screen.queryByText('Configuración guardada correctamente.')).toBeNull();
  });
});
