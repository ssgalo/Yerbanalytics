// @vitest-environment jsdom
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { ParametrosTab } from './ParametrosTab';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import type { CambioParametro, CatalogoReglas } from '@/types/domain';
import type { Borrador } from './borrador';
import { catalogoDeFabrica, conValor } from './__fixtures__/catalogos';

afterEach(cleanup);

/** Lo usan cuatro reglas: R-01, R-03, R-05 y R-06. */
const COMPARTIDO = 'riego.umbral-humedad';
const ETIQUETA_COMPARTIDO = 'Umbral de riego (humedad de sustrato)';

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
      expect.stringMatching(/Riego · 7 reglas/),
      expect.stringMatching(/Ejecución del riego/),
      expect.stringMatching(/Insumos/),
      expect.stringMatching(/Mediasombra/),
      expect.stringMatching(/Seguimiento/),
    ]);
    // Colapsadas: ningún campo de regla a la vista (el grupo de ejecución del riego no se colapsa)
    expect(screen.queryAllByRole('spinbutton')).toHaveLength(1);
    // Orden por prioridad dentro de RIEGO: ciclo → R-04 → R-02 → R-05 → R-06 → R-03 → R-01
    const botones = screen.getAllByRole('button', { expanded: false }).map((b) => b.textContent ?? '');
    const orden = ['Un riego por ciclo', 'Sustrato saturado', 'Déficit hídrico crítico', 'fuera de ventana', 'Pausa tras', 'Posponer por lluvia', 'Riego por déficit'];
    const posiciones = orden.map((t) => botones.findIndex((b) => b.includes(t)));
    expect(posiciones.every((p) => p >= 0)).toBe(true);
    expect(posiciones).toEqual([...posiciones].sort((x, y) => x - y));
  });

  it('muestra "Sin parámetros configurables" para las reglas sin umbrales', () => {
    montar(catalogoDeFabrica());
    // Bloqueo manual, ciclo de lectura y seguimiento.
    expect(screen.getAllByText('Sin parámetros configurables')).toHaveLength(3);
  });
});

describe('ParametrosTab · ejecución del riego (13.3)', () => {
  it('riego.sectores-simultaneos va en el grupo "Ejecución del riego", no bajo una regla', () => {
    montar(catalogoDeFabrica());

    const grupo = screen.getByRole('region', { name: 'Ejecución del riego' });
    const campo = within(grupo).getByLabelText(/Sectores regando a la vez/) as HTMLInputElement;
    expect(campo.value).toBe('10');
    // No es de una regla: ninguna tarjeta de regla lo declara ni lo marca como compartido.
    expect(document.querySelectorAll('[data-clave="riego.sectores-simultaneos"]')).toHaveLength(1);
    expect(within(grupo).queryByText(/Compartido con/)).toBeNull();
  });

  it('se edita y se guarda como cualquier otro parámetro', async () => {
    const { onGuardar } = montar(catalogoDeFabrica());

    fireEvent.change(screen.getByLabelText(/Sectores regando a la vez/), { target: { value: '5' } });
    fireEvent.click(guardar());

    await waitFor(() => expect(onGuardar).toHaveBeenCalledWith([{ clave: 'riego.sectores-simultaneos', valor: '5' }]));
  });

  it('la rama Insumo no lo muestra, y buscar "ejecución" lo encuentra', () => {
    montar(catalogoDeFabrica());

    fireEvent.change(screen.getByLabelText('Rama'), { target: { value: 'INSUMO' } });
    expect(screen.queryByRole('region', { name: 'Ejecución del riego' })).toBeNull();

    fireEvent.change(screen.getByLabelText('Rama'), { target: { value: 'TODAS' } });
    fireEvent.change(screen.getByPlaceholderText(/Buscar/), { target: { value: 'ejecución' } });
    expect(screen.getByRole('region', { name: 'Ejecución del riego' })).toBeTruthy();
  });
});

describe('ParametrosTab · parámetro compartido (7.3)', () => {
  it('marca el parámetro compartido con las otras tres reglas que lo usan', () => {
    montar(catalogoDeFabrica());
    abrir(/Riego por déficit/);

    const chips = screen.getAllByText(/Compartido con 3 reglas/);
    const chip = chips.find((c) => c.closest('[data-clave]')?.getAttribute('data-clave') === COMPARTIDO)!;
    expect(chip.textContent).toMatch(/fuera de ventana.*Pausa tras.*Posponer por lluvia/);
    // R-01 no se cita a sí misma.
    expect(chip.textContent).not.toMatch(/Riego por déficit/);
  });

  it('editarlo bajo una regla cambia el valor bajo la otra y se guarda UN solo cambio', async () => {
    const { onGuardar } = montar(catalogoDeFabrica());
    abrir(/Riego por déficit/);
    abrir(/Posponer por lluvia/);

    const campos = screen.getAllByLabelText(ETIQUETA_COMPARTIDO) as HTMLInputElement[];
    expect(campos).toHaveLength(2);

    fireEvent.change(campos[0], { target: { value: '40' } });

    expect(campos.map((c) => c.value)).toEqual(['40', '40']);
    fireEvent.click(guardar());

    await waitFor(() => expect(onGuardar).toHaveBeenCalledOnce());
    expect(onGuardar).toHaveBeenCalledWith([{ clave: COMPARTIDO, valor: '40' }]);
  });

  it('la edición resalta todas las apariciones del parámetro', () => {
    montar(catalogoDeFabrica());
    abrir(/Riego por déficit/);
    abrir(/Posponer por lluvia/);

    fireEvent.change(screen.getAllByLabelText(ETIQUETA_COMPARTIDO)[0], { target: { value: '40' } });

    const filas = document.querySelectorAll(`[data-clave="${COMPARTIDO}"]`);
    expect(filas).toHaveLength(2);
    filas.forEach((f) => expect(f.className).toMatch(/filaEditada/));
  });
});

describe('ParametrosTab · edición validada (7.4)', () => {
  it('un valor fuera de rango marca el campo y deshabilita Guardar', () => {
    montar(catalogoDeFabrica());
    abrir(/Riego por déficit/);

    const campo = screen.getByLabelText(/Umbral de riego/) as HTMLInputElement;
    fireEvent.change(campo, { target: { value: '99' } });

    expect(campo.className).toMatch(/inputError/);
    expect(screen.getByText('Debe estar entre 35 y 60 %.')).toBeTruthy();
    expect(guardar().disabled).toBe(true);
  });

  it('sin cambios Guardar está deshabilitado; con un cambio válido, habilitado', () => {
    montar(catalogoDeFabrica());
    abrir(/Riego por déficit/);
    expect(guardar().disabled).toBe(true);

    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });
    expect(guardar().disabled).toBe(false);
  });

  it('"Restablecer" envía valor null', async () => {
    const { onGuardar } = montar(conValor(catalogoDeFabrica(), 'riego.umbral-humedad', '40'));
    abrir(/Riego por déficit/);

    fireEvent.click(screen.getByRole('button', { name: /Restablecer/ }));
    expect((screen.getByLabelText(/Umbral de riego/) as HTMLInputElement).value).toBe('45');
    fireEvent.click(guardar());

    await waitFor(() => expect(onGuardar).toHaveBeenCalledWith([{ clave: 'riego.umbral-humedad', valor: null }]));
  });

  it('"Descartar" vuelve al valor vigente', () => {
    montar(catalogoDeFabrica());
    abrir(/Riego por déficit/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(screen.getByRole('button', { name: /Descartar/ }));

    expect((screen.getByLabelText(/Umbral de riego/) as HTMLInputElement).value).toBe('45');
  });
});

describe('ParametrosTab · búsqueda y filtros (7.5)', () => {
  it('buscar "pausa" deja sólo las reglas que coinciden', () => {
    montar(catalogoDeFabrica());

    fireEvent.change(screen.getByPlaceholderText(/Buscar/), { target: { value: 'pausa' } });

    expect(screen.getByText('⏸️ Pausa tras una aplicación (R-06)')).toBeTruthy();
    expect(screen.queryByText('🌧️ Posponer por lluvia (R-03)')).toBeNull();
    // Al buscar, las coincidencias se abren solas
    expect(screen.getByLabelText(/Pausa de riego tras una aplicación/)).toBeTruthy();
  });

  it('filtra por rama', () => {
    montar(catalogoDeFabrica());

    fireEvent.change(screen.getByLabelText('Rama'), { target: { value: 'INSUMO' } });

    expect(screen.getByText('🧪 Dosificación de insumo')).toBeTruthy();
    expect(screen.queryByText('💦 Riego por déficit hídrico (R-01)')).toBeNull();
  });

  it('"sólo modificados" muestra las reglas con algún parámetro editado', () => {
    montar(conValor(catalogoDeFabrica(), 'insumo.max-dosis-24h', '2'));

    fireEvent.click(screen.getByLabelText(/Sólo modificados/));

    expect(screen.getByText('🛑 Límite de dosis diaria')).toBeTruthy();
    expect(screen.queryByText('💦 Riego por déficit hídrico (R-01)')).toBeNull();
  });

  it('"Ver por parámetro" lista cada parámetro una sola vez con las reglas que lo usan', () => {
    montar(catalogoDeFabrica());

    fireEvent.click(screen.getByRole('button', { name: 'Por parámetro' }));

    expect(screen.getAllByLabelText(ETIQUETA_COMPARTIDO)).toHaveLength(1);
    const fila = document.querySelector(`[data-clave="${COMPARTIDO}"]`) as HTMLElement;
    expect(within(fila).getByText(/Usado por: .*fuera de ventana.*Pausa tras.*Posponer por lluvia.*Riego por déficit/)).toBeTruthy();
    // El consumidor que no es una regla se rotula por su nombre, no por el id de la clase.
    expect(screen.getByText(/Usado por: Ejecución del riego/)).toBeTruthy();
    // 21 parámetros del catálogo = 21 filas
    expect(document.querySelectorAll('[data-clave]')).toHaveLength(21);
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
    abrir(/Riego por déficit/);
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
    abrir(/Riego por déficit/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });
    abrir(/Riego por déficit/); // la cierra con la edición pendiente

    fireEvent.click(guardar());

    // El parámetro es compartido: se abren las cuatro reglas que lo usan, cada una con su aviso.
    expect(await screen.findAllByText('Inválido.')).toHaveLength(4);
  });

  it('un error que no es de validación se muestra como aviso y conserva el borrador', async () => {
    const onGuardar = vi.fn().mockRejectedValue(new Error('Error 500 al guardar los parámetros del motor'));
    montar(catalogoDeFabrica(), { onGuardar });
    abrir(/Riego por déficit/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(guardar());

    expect(await screen.findByText(/Error 500/)).toBeTruthy();
    expect((screen.getByLabelText(/Umbral de riego/) as HTMLInputElement).value).toBe('40');
  });

  it('al guardar bien, el borrador se vacía', async () => {
    const { onGuardar } = montar(catalogoDeFabrica());
    abrir(/Riego por déficit/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(guardar());

    await waitFor(() => expect(onGuardar).toHaveBeenCalled());
    await waitFor(() => expect(guardar().disabled).toBe(true));
  });
});

describe('ParametrosTab · regla inicial', () => {
  it('abre la regla que llega por parámetro (desde "Editar parámetro" del Inspector)', () => {
    montar(catalogoDeFabrica(), { reglaInicial: 'RiegoPorDeficitRule' });
    expect(screen.getByLabelText(/Umbral de riego/)).toBeTruthy();
  });
});

describe('ParametrosTab · guardado en vuelo (#2)', () => {
  it('deshabilita los campos y los botones mientras el PUT está en vuelo', async () => {
    let resolver: () => void = () => undefined;
    const onGuardar = vi.fn().mockReturnValue(new Promise<void>((r) => (resolver = r)));
    montar(catalogoDeFabrica(), { onGuardar });
    abrir(/Riego por déficit/);
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
    abrir(/Sustrato saturado/);
    fireEvent.change(screen.getByLabelText(/Saturación que bloquea/), { target: { value: '99' } });
    expect(screen.queryByText(/oculto/)).toBeNull();

    fireEvent.change(screen.getByPlaceholderText(/Buscar/), { target: { value: 'lluvia' } });

    expect(screen.getByText(/Corregí los valores marcados/)).toBeTruthy();
    expect(screen.getByText(/1 parámetro con error queda oculto por los filtros/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Ver los que tienen error' }));

    expect((screen.getByPlaceholderText(/Buscar/) as HTMLInputElement).value).toBe('');
    const campo = screen.getByLabelText(/Saturación que bloquea/) as HTMLInputElement;
    expect(campo.className).toMatch(/inputError/);
    expect(screen.queryByText(/oculto/)).toBeNull();
  });

  it('también cuenta los errores del servidor y la rama filtrada', async () => {
    const onGuardar = vi
      .fn()
      .mockRejectedValue(new ParametrosInvalidosError([{ clave: 'riego.umbral-humedad', mensaje: 'Inválido.' }]));
    montar(catalogoDeFabrica(), { onGuardar });
    abrir(/Riego por déficit/);
    fireEvent.change(screen.getByLabelText(/Umbral de riego/), { target: { value: '40' } });
    fireEvent.click(guardar());
    await screen.findAllByText('Inválido.');

    fireEvent.change(screen.getByLabelText('Rama'), { target: { value: 'INSUMO' } });

    expect(screen.getByText(/1 parámetro con error queda oculto por los filtros/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Ver los que tienen error' }));
    expect((screen.getByLabelText('Rama') as HTMLSelectElement).value).toBe('TODAS');
    expect(screen.getAllByText('Inválido.').length).toBeGreaterThan(0);
  });

  it('en plural: "N parámetros con error quedan ocultos"', () => {
    montar(catalogoDeFabrica());
    abrir(/Sustrato saturado/);
    fireEvent.change(screen.getByLabelText(/Saturación que bloquea/), { target: { value: '99' } });
    abrir(/Déficit hídrico crítico/);
    fireEvent.change(screen.getByLabelText(/Intervalo mínimo entre riegos/), { target: { value: '0' } });

    fireEvent.change(screen.getByLabelText('Rama'), { target: { value: 'INSUMO' } });

    expect(screen.getByText(/2 parámetros con error quedan ocultos por los filtros/)).toBeTruthy();
  });
});
