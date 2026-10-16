// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import * as data from '@/data';
import { MockRepository } from '@/data/mock/mockRepository';
import { PageMetaProvider } from '@/hooks/PageMeta';
import { ConPermisos } from '@/test/sesion';
import type { Permiso, Rol } from '@/types/seguridad';
import { DiagnosisCriteriaPage } from './DiagnosisCriteriaPage';

let repo: MockRepository;

beforeEach(() => {
  // Un repositorio nuevo por test: lo guardado en uno no se filtra al siguiente.
  repo = new MockRepository(20260613);
  vi.spyOn(data, 'getRepository').mockReturnValue(repo);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const montar = (rol: Rol = 'INGENIERO_AGRONOMO', permisos?: Permiso[]) =>
  render(
    <MemoryRouter>
      <ConPermisos rol={rol} permisos={permisos}>
        <PageMetaProvider>
          <DiagnosisCriteriaPage />
        </PageMetaProvider>
      </ConPermisos>
    </MemoryRouter>,
  );

const campo = (nombre: string) => screen.findByLabelText(nombre) as Promise<HTMLInputElement>;
const barra = () => screen.queryByRole('region', { name: 'Cambios sin guardar' });
const guardar = () => screen.getByRole('button', { name: /Guardar cambios/ }) as HTMLButtonElement;
const orden = () =>
  within(screen.getByRole('list', { name: /Orden de criticidad/ }))
    .getAllByRole('listitem')
    .map((li) => li.textContent?.replace(/^\d/, '').replace(/(más|menos) crítico$/, '').trim());

describe('Criterio de diagnóstico', () => {
  it('muestra el criterio por defecto del mock y aclara que la confianza la calibra el equipo', async () => {
    montar();

    expect((await campo('Daño fúngico · Media desde')).value).toBe('4');
    expect((await campo('Clorosis · Alta desde')).value).toBe('20');
    expect(orden()).toEqual(['Daño fúngico', 'Plaga foliar', 'Clorosis', 'Estrés solar']);
    expect(screen.getByText(/los calibra el equipo en el servicio de inferencia/)).toBeTruthy();
    expect(screen.getByText('Baja 2–3 · Media 4–7 · Alta 8 o más')).toBeTruthy();
    expect(barra()).toBeNull();
  });

  it('editar una banda cuenta el cambio, guarda y la barra deja de contar', async () => {
    const save = vi.spyOn(repo, 'saveDiagnosisCriteria');
    montar();

    fireEvent.change(await campo('Daño fúngico · Media desde'), { target: { value: '3' } });
    expect(barra()!.textContent).toContain('1 cambio sin guardar');

    fireEvent.click(guardar());
    await waitFor(() => expect(barra()).toBeNull());
    expect(save.mock.calls[0][0].severityBands.find((b) => b.state === 'Daño fúngico')?.mediumFrom).toBe(3);
    expect((await repo.getDiagnosisCriteria()).severityBands[3].mediumFrom).toBe(3);
    expect(screen.getByRole('status').textContent).toMatch(/guardado/);
  });

  it('reordena la criticidad con los botones y guarda el orden nuevo', async () => {
    montar();
    await campo('Clorosis · Mínimo');

    const subir = screen.getByRole('button', { name: 'Subir Clorosis' });
    fireEvent.click(subir);
    expect(orden()).toEqual(['Daño fúngico', 'Clorosis', 'Plaga foliar', 'Estrés solar']);
    // El foco sigue en el mismo control, y el cambio se anuncia
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Subir Clorosis' }));
    expect(screen.getByText('Clorosis queda en el puesto 2 de 4.')).toBeTruthy();
    // En los extremos no se puede seguir
    expect((screen.getByRole('button', { name: 'Subir Daño fúngico' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'Bajar Estrés solar' }) as HTMLButtonElement).disabled).toBe(true);

    fireEvent.click(guardar());
    await waitFor(() => expect(barra()).toBeNull());
    expect((await repo.getDiagnosisCriteria()).criticality).toEqual([
      'Daño fúngico',
      'Clorosis',
      'Plaga foliar',
      'Estrés solar',
    ]);
  });

  it('un mínimo mayor o igual que "media desde" marca el campo, lo explica y no deja guardar', async () => {
    const save = vi.spyOn(repo, 'saveDiagnosisCriteria');
    montar();

    const minimo = await campo('Clorosis · Mínimo');
    fireEvent.change(minimo, { target: { value: '10' } });

    expect(minimo.getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByRole('alert').textContent).toMatch(/mínimo tiene que ser menor que "media desde"/);
    expect(barra()!.textContent).toMatch(/para corregir antes de guardar/);
    expect(guardar().disabled).toBe(true);
    expect(save).not.toHaveBeenCalled();

    // Un campo vacío tampoco pasa
    fireEvent.change(minimo, { target: { value: '' } });
    expect(minimo.getAttribute('aria-invalid')).toBe('true');
    expect(guardar().disabled).toBe(true);
  });

  it('descartar vuelve a lo guardado y restablecer trae los valores por defecto', async () => {
    await repo.saveDiagnosisCriteria({
      ...(await repo.getDiagnosisCriteria()),
      criticality: ['Clorosis', 'Daño fúngico', 'Plaga foliar', 'Estrés solar'],
    });
    montar();

    const media = await campo('Plaga foliar · Media desde');
    fireEvent.change(media, { target: { value: '6' } });
    fireEvent.click(screen.getByRole('button', { name: 'Descartar' }));
    expect(media.value).toBe('5');
    expect(barra()).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /Restablecer valores de fábrica/ }));
    expect(orden()).toEqual(['Daño fúngico', 'Plaga foliar', 'Clorosis', 'Estrés solar']);
    expect(barra()!.textContent).toContain('1 cambio sin guardar');
  });

  it('sin configuracion.editar es de sólo lectura', async () => {
    montar('OPERARIO', ['configuracion.ver', 'diagnosticos.ver']);

    const minimo = await campo('Clorosis · Mínimo');
    expect(screen.getByText(/Sólo lectura/)).toBeTruthy();
    expect(minimo.closest('fieldset')?.disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'Subir Clorosis' }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.queryByRole('button', { name: /Restablecer/ })).toBeNull();
    expect(barra()).toBeNull();
  });

  it('si el backend rechaza el guardado, muestra su mensaje', async () => {
    vi.spyOn(repo, 'saveDiagnosisCriteria').mockRejectedValue(new Error('Plaga foliar: valor fuera de rango.'));
    montar();

    fireEvent.change(await campo('Plaga foliar · Alta desde'), { target: { value: '12' } });
    fireEvent.click(guardar());

    expect((await screen.findByRole('alert')).textContent).toContain('Plaga foliar: valor fuera de rango.');
    expect(barra()).not.toBeNull();
  });
});
