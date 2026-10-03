// @vitest-environment jsdom
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ParametrosTab } from './ParametrosTab';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import type { CambioParametro, CatalogoReglas } from '@/types/domain';
import type { Borrador } from './borrador';
import { catalogoConCompartido, catalogoDeFabrica, conValor } from './__fixtures__/catalogos';

afterEach(cleanup);

const COMPARTIDO = 'riego.max-riegos-24h';
const ETIQUETA_COMPARTIDO = 'Máximo de riegos en 24 h (límite de volumen)';

/** El dueño del borrador (en la app, `ReglasPage`): acá un host mínimo con el estado y el guardado. */
function Host({
  catalogo,
  onGuardar,
  ...extra
}: {
  catalogo: CatalogoReglas;
  onGuardar: (c: CambioParametro[]) => Promise<unknown>;
} & Partial<React.ComponentProps<typeof ParametrosTab>>) {
  const [borrador, setBorrador] = useState<Borrador>(new Map());
  const [saving, setSaving] = useState(false);
  const guardar = async (c: CambioParametro[]) => {
    setSaving(true);
    try {
      return await onGuardar(c);
    } finally {
      setSaving(false);
    }
  };
  return (
    <ParametrosTab
      catalogo={catalogo}
      saving={saving}
      onGuardar={guardar}
      borrador={borrador}
      onBorradorChange={setBorrador}
      {...extra}
    />
  );
}

function montar(
  catalogo: CatalogoReglas,
  extra: Partial<React.ComponentProps<typeof Host>> = {},
) {
  const onGuardar = vi.fn<(c: CambioParametro[]) => Promise<void>>().mockResolvedValue(undefined);
  render(<Host catalogo={catalogo} onGuardar={onGuardar} {...extra} />);
  return { onGuardar };
}

const abrir = (nombre: RegExp | string) => fireEvent.click(screen.getByRole('button', { name: nombre }));
const guardar = () => screen.getByRole('button', { name: /Guardar/ }) as HTMLButtonElement;

describe('ParametrosTab · agrupación (7.2)', () => {
  it('agrupa por rama en orden de prioridad, con las reglas colapsadas', () => {
    montar(catalogoDeFabrica());

    const grupos = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent);
    expect(grupos).toEqual([
      expect.stringMatching(/Global/),
      expect.stringMatching(/Riego/),
      expect.stringMatching(/Insumos/),
      expect.stringMatching(/Mediasombra/),
      expect.stringMatching(/Seguimiento/),
    ]);
    // Colapsadas: ningún campo a la vista
    expect(screen.queryAllByRole('spinbutton')).toHaveLength(0);
    // Orden por prioridad dentro de RIEGO: lluvia (2), volumen (4), riego (10)
    const riego = screen.getAllByRole('button', { expanded: false }).map((b) => b.textContent ?? '');
    const iLluvia = riego.findIndex((t) => t.includes('Condición climática'));
    const iVolumen = riego.findIndex((t) => t.includes('Límite de volumen'));
    const iRiego = riego.findIndex((t) => t.includes('💦 Riego'));
    expect(iLluvia).toBeLessThan(iVolumen);
    expect(iVolumen).toBeLessThan(iRiego);
  });

  it('muestra "Sin parámetros configurables" para las reglas sin umbrales', () => {
    montar(catalogoDeFabrica());
    expect(screen.getAllByText('Sin parámetros configurables')).toHaveLength(2);
  });
});

describe('ParametrosTab · parámetro compartido (7.3)', () => {
  it('marca el parámetro compartido con las otras reglas', () => {
    montar(catalogoConCompartido());
    abrir(/Límite de volumen/);

    expect(screen.getByText(/Compartido con 1 regla: 💦 Riego/)).toBeTruthy();
  });

  it('editarlo bajo una regla cambia el valor bajo la otra y se guarda UN solo cambio', async () => {
    const { onGuardar } = montar(catalogoConCompartido());
    abrir(/Límite de volumen/);
    abrir(/💦 Riego/);

    const campos = screen.getAllByLabelText(ETIQUETA_COMPARTIDO) as HTMLInputElement[];
    expect(campos).toHaveLength(2);

    fireEvent.change(campos[0], { target: { value: '3' } });

    expect(campos.map((c) => c.value)).toEqual(['3', '3']);
    fireEvent.click(guardar());

    await waitFor(() => expect(onGuardar).toHaveBeenCalledOnce());
    expect(onGuardar).toHaveBeenCalledWith([{ clave: COMPARTIDO, valor: '3' }]);
  });

  it('la edición resalta todas las apariciones del parámetro', () => {
    montar(catalogoConCompartido());
    abrir(/Límite de volumen/);
    abrir(/💦 Riego/);

    fireEvent.change(screen.getAllByLabelText(ETIQUETA_COMPARTIDO)[0], { target: { value: '3' } });

    const filas = document.querySelectorAll(`[data-clave="${COMPARTIDO}"]`);
    expect(filas).toHaveLength(2);
    filas.forEach((f) => expect(f.className).toMatch(/filaEditada/));
  });
});

describe('ParametrosTab · edición validada (7.4)', () => {
  it('un valor fuera de rango marca el campo y deshabilita Guardar', () => {
    montar(catalogoDeFabrica());
    abrir(/💦 Riego/);

    const campo = screen.getByLabelText(/Umbral de riego/) as HTMLInputElement;
    fireEvent.change(campo, { target: { value: '99' } });

    expect(campo.className).toMatch(/inputError/);
    expect(screen.getByText('Debe estar entre 35 y 60 %.')).toBeTruthy();
    expect(guardar().disabled).toBe(true);
  });

  it('sin cambios Guardar está deshabilitado; con un cambio válido, habilitado', () => {
    montar(catalogoDeFabrica());
    abrir(/💦 Riego/);
    expect(guardar().disabled).toBe(true);

    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });
    expect(guardar().disabled).toBe(false);
  });

  it('"Restablecer" envía valor null', async () => {
    const { onGuardar } = montar(conValor(catalogoDeFabrica(), 'riego.umbral-humedad', '40'));
    abrir(/💦 Riego/);

    fireEvent.click(screen.getByRole('button', { name: /Restablecer/ }));
    expect((screen.getByLabelText(/Umbral de riego/) as HTMLInputElement).value).toBe('42');
    fireEvent.click(guardar());

    await waitFor(() => expect(onGuardar).toHaveBeenCalledWith([{ clave: 'riego.umbral-humedad', valor: null }]));
  });

  it('"Descartar" vuelve al valor vigente', () => {
    montar(catalogoDeFabrica());
    abrir(/💦 Riego/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(screen.getByRole('button', { name: /Descartar/ }));

    expect((screen.getByLabelText(/Umbral de riego/) as HTMLInputElement).value).toBe('42');
  });
});

describe('ParametrosTab · búsqueda y filtros (7.5)', () => {
  it('buscar "lluvia" deja sólo las reglas que coinciden', () => {
    montar(catalogoDeFabrica());

    fireEvent.change(screen.getByPlaceholderText(/Buscar/), { target: { value: 'lluvia' } });

    expect(screen.getByText('🌧️ Condición climática (lluvia)')).toBeTruthy();
    expect(screen.queryByText('💦 Riego')).toBeNull();
    // Al buscar, las coincidencias se abren solas
    expect(screen.getByLabelText(/Probabilidad de lluvia/)).toBeTruthy();
  });

  it('filtra por rama', () => {
    montar(catalogoDeFabrica());

    fireEvent.change(screen.getByLabelText('Rama'), { target: { value: 'INSUMO' } });

    expect(screen.getByText('🧪 Dosificación de insumo')).toBeTruthy();
    expect(screen.queryByText('💦 Riego')).toBeNull();
  });

  it('"sólo modificados" muestra las reglas con algún parámetro editado', () => {
    montar(conValor(catalogoDeFabrica(), 'insumo.max-dosis-24h', '2'));

    fireEvent.click(screen.getByLabelText(/Sólo modificados/));

    expect(screen.getByText('🛑 Límite de dosis diaria')).toBeTruthy();
    expect(screen.queryByText('💦 Riego')).toBeNull();
  });

  it('"Ver por parámetro" lista cada parámetro una sola vez con las reglas que lo usan', () => {
    montar(catalogoConCompartido());

    fireEvent.click(screen.getByRole('button', { name: 'Por parámetro' }));

    expect(screen.getAllByLabelText(ETIQUETA_COMPARTIDO)).toHaveLength(1);
    expect(screen.getByText(/Usado por: .*Límite de volumen.*Riego/)).toBeTruthy();
    // 11 parámetros del catálogo = 11 filas
    expect(document.querySelectorAll('[data-clave]')).toHaveLength(11);
  });
});

describe('ParametrosTab · errores del servidor (7.6)', () => {
  it('muestra cada error junto a su parámetro y conserva el borrador', async () => {
    const onGuardar = vi
      .fn()
      .mockRejectedValue(
        new ParametrosInvalidosError([
          { clave: 'riego.umbral-humedad', mensaje: 'Debe ser menor que el umbral crítico.' },
          { clave: null, mensaje: 'Error general del lote.' },
        ]),
      );
    montar(catalogoDeFabrica(), { onGuardar });
    abrir(/💦 Riego/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(guardar());

    const fila = await waitFor(() => {
      const f = document.querySelector('[data-clave="riego.umbral-humedad"]') as HTMLElement;
      expect(within(f).getByText('Debe ser menor que el umbral crítico.')).toBeTruthy();
      return f;
    });
    expect(screen.getByText('Error general del lote.')).toBeTruthy();
    // El borrador sigue ahí
    expect((within(fila).getByLabelText(/Umbral de riego/) as HTMLInputElement).value).toBe('40');
  });

  it('abre solas las reglas con errores del servidor', async () => {
    const onGuardar = vi
      .fn()
      .mockRejectedValue(new ParametrosInvalidosError([{ clave: 'riego.umbral-humedad', mensaje: 'Inválido.' }]));
    montar(catalogoDeFabrica(), { onGuardar });
    abrir(/💦 Riego/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });
    abrir(/💦 Riego/); // la cierra con la edición pendiente

    fireEvent.click(guardar());

    expect(await screen.findByText('Inválido.')).toBeTruthy();
  });

  it('un error que no es de validación se muestra como aviso y conserva el borrador', async () => {
    const onGuardar = vi.fn().mockRejectedValue(new Error('Error 500 al guardar los parámetros del motor'));
    montar(catalogoDeFabrica(), { onGuardar });
    abrir(/💦 Riego/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(guardar());

    expect(await screen.findByText(/Error 500/)).toBeTruthy();
    expect((screen.getByLabelText(/Umbral de riego/) as HTMLInputElement).value).toBe('40');
  });

  it('al guardar bien, el borrador se vacía', async () => {
    const { onGuardar } = montar(catalogoDeFabrica());
    abrir(/💦 Riego/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(guardar());

    await waitFor(() => expect(onGuardar).toHaveBeenCalled());
    await waitFor(() => expect(guardar().disabled).toBe(true));
  });
});

describe('ParametrosTab · regla inicial', () => {
  it('abre la regla que llega por parámetro (desde "Editar parámetro" del Inspector)', () => {
    montar(catalogoDeFabrica(), { reglaInicial: 'IrrigationRule' });
    expect(screen.getByLabelText(/Umbral de riego/)).toBeTruthy();
  });
});

describe('ParametrosTab · guardado en vuelo (#2)', () => {
  it('deshabilita los campos y los botones mientras el PUT está en vuelo', async () => {
    let resolver: () => void = () => undefined;
    const onGuardar = vi.fn().mockReturnValue(new Promise<void>((r) => (resolver = r)));
    montar(catalogoDeFabrica(), { onGuardar });
    abrir(/💦 Riego/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(guardar());

    await waitFor(() => expect((screen.getByLabelText(/Umbral de riego/) as HTMLInputElement).disabled).toBe(true));
    expect((screen.getByRole('button', { name: /Descartar/ }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: /Restablecer fábrica/ }) as HTMLButtonElement).disabled).toBe(true);

    resolver();
    await waitFor(() => expect((screen.getByLabelText(/Umbral de riego/) as HTMLInputElement).disabled).toBe(false));
  });
});

describe('ParametrosTab · errores ocultos por los filtros (#5)', () => {
  it('avisa cuántos errores quedaron ocultos y "Ver los que tienen error" limpia los filtros', () => {
    montar(catalogoDeFabrica());
    abrir(/💦 Riego/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '99' } });
    expect(screen.queryByText(/oculto/)).toBeNull();

    fireEvent.change(screen.getByPlaceholderText(/Buscar/), { target: { value: 'lluvia' } });

    expect(screen.getByText(/Corregí los valores marcados/)).toBeTruthy();
    expect(screen.getByText(/1 parámetro con error queda oculto por los filtros/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Ver los que tienen error' }));

    expect((screen.getByPlaceholderText(/Buscar/) as HTMLInputElement).value).toBe('');
    const campo = screen.getByLabelText(/Umbral de riego/) as HTMLInputElement;
    expect(campo.className).toMatch(/inputError/);
    expect(screen.queryByText(/oculto/)).toBeNull();
  });

  it('también cuenta los errores del servidor y la rama filtrada', async () => {
    const onGuardar = vi
      .fn()
      .mockRejectedValue(new ParametrosInvalidosError([{ clave: 'riego.umbral-humedad', mensaje: 'Inválido.' }]));
    montar(catalogoDeFabrica(), { onGuardar });
    abrir(/💦 Riego/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });
    fireEvent.click(guardar());
    await screen.findByText('Inválido.');

    fireEvent.change(screen.getByLabelText('Rama'), { target: { value: 'INSUMO' } });

    expect(screen.getByText(/1 parámetro con error queda oculto por los filtros/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Ver los que tienen error' }));
    expect((screen.getByLabelText('Rama') as HTMLSelectElement).value).toBe('TODAS');
    expect(screen.getByText('Inválido.')).toBeTruthy();
  });

  it('en plural: "N parámetros con error quedan ocultos"', () => {
    montar(catalogoDeFabrica());
    abrir(/💦 Riego/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '99' } });
    abrir(/Límite de volumen/);
    fireEvent.change(screen.getByLabelText(ETIQUETA_COMPARTIDO), { target: { value: '0' } });

    fireEvent.change(screen.getByLabelText('Rama'), { target: { value: 'INSUMO' } });

    expect(screen.getByText(/2 parámetros con error quedan ocultos por los filtros/)).toBeTruthy();
  });
});
