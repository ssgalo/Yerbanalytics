// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { NutritionPlanCard } from './NutritionPlanCard';
import { getRepository, NutritionPlanError } from '@/data';
import { ConPermisos } from '@/test/sesion';
import type { NutritionPlan, Zona } from '@/types/domain';
import type { Permiso } from '@/types/seguridad';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

const DAY = 86_400_000;
const EDITOR: Permiso[] = ['vivero.ver', 'configuracion.ver', 'configuracion.editar'];
const READER: Permiso[] = ['vivero.ver', 'configuracion.ver'];

const planOf = (over: Partial<NutritionPlan> = {}): NutritionPlan => ({
  intervalDays: 7,
  customInterval: false,
  lastApplicationTs: Date.now() - 2 * DAY,
  nextDueTs: Date.now() + 5 * DAY,
  batchOpen: false,
  advanced: false,
  ...over,
});

async function zoneWith(plan: NutritionPlan | null | undefined, id = 'MZ-3'): Promise<Zona> {
  const zone = (await getRepository().getNursery()).zonas.find((z) => z.id === id)!;
  return { ...zone, nutritionPlan: plan };
}

async function mount(plan: NutritionPlan | null | undefined, permisos: Permiso[] = EDITOR) {
  const zona = await zoneWith(plan);
  return render(
    <ConPermisos rol="INGENIERO_AGRONOMO" permisos={permisos}>
      <NutritionPlanCard zona={zona} />
    </ConPermisos>,
  );
}

describe('NutritionPlanCard', () => {
  it('sin plan en el snapshot no muestra nada', async () => {
    const { container } = await mount(null);

    expect(container.textContent).toBe('');
  });

  it('muestra el intervalo general, la última aplicación y la próxima', async () => {
    await mount(planOf({ intervalDays: 7, customInterval: false }));

    expect(screen.getByText('Cada 7 días')).toBeTruthy();
    expect(screen.getByText('General')).toBeTruthy();
    expect(screen.getByText(/Próxima:/)).toBeTruthy();
  });

  it('un intervalo propio de la zona se marca como tal y 1 día va en singular', async () => {
    await mount(planOf({ intervalDays: 14, customInterval: true }));

    expect(screen.getByText('Cada 14 días')).toBeTruthy();
    expect(screen.getByText('Propio de la zona')).toBeTruthy();
  });

  it('una zona sin aplicaciones previas dice que vence ya', async () => {
    await mount(planOf({ lastApplicationTs: null, nextDueTs: null }));

    expect(screen.getByText('Todavía no se aplicó')).toBeTruthy();
    expect(screen.getByText('Vence ya')).toBeTruthy();
  });

  it('con la tanda en curso dice que se está aplicando y que hay sectores esperando su ventana', async () => {
    await mount(planOf({ batchOpen: true, nextDueTs: null }));

    expect(screen.getByText('Aplicando')).toBeTruthy();
    expect(screen.getByText(/espera su ventana/)).toBeTruthy();
  });

  it('un plan adelantado por CE baja lo dice', async () => {
    await mount(planOf({ advanced: true, nextDueTs: Date.now() - 1000 }));

    expect(screen.getByText('Adelantado por CE baja')).toBeTruthy();
  });

  it('sin configuracion.editar no hay cómo cambiar el intervalo', async () => {
    await mount(planOf(), READER);

    expect(screen.getByText('Cada 7 días')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Cambiar intervalo' })).toBeNull();
  });

  it('cambiar el intervalo lo manda al repositorio y cierra el formulario', async () => {
    const save = vi.spyOn(getRepository(), 'setNutritionPlanInterval');
    await mount(planOf());

    fireEvent.click(screen.getByRole('button', { name: 'Cambiar intervalo' }));
    fireEvent.change(screen.getByLabelText('Días entre aplicaciones'), { target: { value: '14' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar intervalo' }));

    await waitFor(() => expect(save).toHaveBeenCalledWith('MZ-3', 14));
    await waitFor(() => expect(screen.queryByRole('form', { name: 'Intervalo del plan de nutrición' })).toBeNull());
  });

  it('el formulario arranca con el intervalo vigente y no deja guardar lo que no cambió', async () => {
    await mount(planOf({ intervalDays: 10 }));

    fireEvent.click(screen.getByRole('button', { name: 'Cambiar intervalo' }));

    expect((screen.getByLabelText('Días entre aplicaciones') as HTMLInputElement).value).toBe('10');
    expect((screen.getByRole('button', { name: 'Guardar intervalo' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it.each([
    ['2', /entre 3 y 30/],
    ['31', /entre 3 y 30/],
    ['siete', /número entero/],
    ['7,5', /número entero/],
  ])('%s se marca como inválido y no deja guardar', async (value, message) => {
    const save = vi.spyOn(getRepository(), 'setNutritionPlanInterval');
    await mount(planOf());

    fireEvent.click(screen.getByRole('button', { name: 'Cambiar intervalo' }));
    fireEvent.change(screen.getByLabelText('Días entre aplicaciones'), { target: { value } });

    expect(screen.getByRole('alert').textContent).toMatch(message);
    const submit = screen.getByRole('button', { name: 'Guardar intervalo' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    fireEvent.click(submit);
    expect(save).not.toHaveBeenCalled();
  });

  it('un rechazo del backend se muestra tal cual y deja el formulario abierto', async () => {
    vi.spyOn(getRepository(), 'setNutritionPlanInterval').mockRejectedValue(
      new NutritionPlanError('La macro-zona MZ-3 no existe.'),
    );
    await mount(planOf());

    fireEvent.click(screen.getByRole('button', { name: 'Cambiar intervalo' }));
    fireEvent.change(screen.getByLabelText('Días entre aplicaciones'), { target: { value: '14' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar intervalo' }));

    expect(await screen.findByText('La macro-zona MZ-3 no existe.')).toBeTruthy();
    expect(screen.getByLabelText('Días entre aplicaciones')).toBeTruthy();
  });

  it('cancelar descarta lo escrito', async () => {
    const save = vi.spyOn(getRepository(), 'setNutritionPlanInterval');
    await mount(planOf());

    fireEvent.click(screen.getByRole('button', { name: 'Cambiar intervalo' }));
    fireEvent.change(screen.getByLabelText('Días entre aplicaciones'), { target: { value: '20' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByLabelText('Días entre aplicaciones')).toBeNull();
    expect(save).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar intervalo' }));
    expect((screen.getByLabelText('Días entre aplicaciones') as HTMLInputElement).value).toBe('7');
  });
});
