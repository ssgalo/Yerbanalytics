// @vitest-environment jsdom
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { EstadoReglaError } from '@/data/estadoReglaError';
import type { ReglaCatalogo } from '@/types/domain';
import { ReglasTab } from './ReglasTab';
import type { Borrador } from './borrador';
import { catalogoDeFabrica } from './__fixtures__/catalogos';

afterEach(() => {
  cleanup();
  localStorage.clear();
});

function montar(extra: Partial<Parameters<typeof ReglasTab>[0]> = {}) {
  const catalogo = catalogoDeFabrica();
  const onGuardar = vi.fn(async () => undefined);
  const onCambiarEstado = vi.fn(
    async (nombre: string) =>
      ({ ...catalogo.reglas.find((r) => r.id === nombre)! }) as ReglaCatalogo,
  );
  function Harness() {
    const [borrador, setBorrador] = useState<Borrador>(new Map());
    return (
      <ReglasTab
        catalogo={catalogo}
        saving={false}
        onGuardar={onGuardar}
        onCambiarEstado={onCambiarEstado}
        borrador={borrador}
        onBorradorChange={setBorrador}
        {...extra}
      />
    );
  }
  render(<Harness />);
  return { onGuardar, onCambiarEstado };
}

const campo = (clave: string) => document.getElementById(`param-${clave}`) as HTMLInputElement;
const interruptor = (nombre: string) => screen.getByRole('switch', { name: nombre });

describe('ReglasTab · parámetros en lote', () => {
  it('editar un parámetro muestra la SaveBar y guarda todo en un solo envío', async () => {
    const { onGuardar } = montar();

    fireEvent.change(campo('riego.humedad-objetivo'), { target: { value: '60' } });
    fireEvent.change(campo('riego.umbral-humedad'), { target: { value: '40' } });

    expect(screen.getByText(/2 cambios sin guardar/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(onGuardar).toHaveBeenCalledTimes(1));
    expect(onGuardar).toHaveBeenCalledWith([
      { clave: 'riego.umbral-humedad', valor: '40' },
      { clave: 'riego.humedad-objetivo', valor: '60' },
    ]);
  });

  it('un parámetro compartido se edita una vez, en Compartidos, y las tarjetas lo muestran en sólo lectura', () => {
    montar();

    expect(document.querySelectorAll('input[id="param-riego.umbral-humedad"]')).toHaveLength(1);
    const bloque = screen.getAllByRole('region', { name: 'Compartidos del grupo' })[0];
    expect(bloque.contains(campo('riego.umbral-humedad'))).toBe(true);
    expect(within(bloque).queryByText(/Compartido con 5 reglas/)).toBeNull();
  });

  it('"Lo usan N reglas" abre una lista con código y nombre, y Escape la cierra devolviendo el foco', () => {
    montar();
    const bloque = screen.getAllByRole('region', { name: 'Compartidos del grupo' })[0];
    const boton = within(bloque).getAllByRole('button', { name: /Lo usan 5 reglas/ })[0];

    fireEvent.click(boton);
    const lista = screen.getByRole('dialog', { name: /Reglas que lo usan/ });
    expect(within(lista).getAllByRole('listitem')).toHaveLength(5);
    expect(within(lista).getAllByText(/^R-\d+/).length).toBeGreaterThan(0);

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog', { name: /Reglas que lo usan/ })).toBeNull();
    expect(document.activeElement).toBe(boton);
  });

  it('el Segmented cambia el orden de las piezas y muestra la prioridad sólo por prioridad', () => {
    localStorage.clear();
    montar();
    const orden = () =>
      within(screen.getAllByRole('region', { name: /Riego/ })[0])
        .getAllByRole('heading', { level: 3 })
        .map((h) => h.textContent ?? '')
        .filter((t) => /· R-\d+/.test(t))
        .map((t) => t.match(/R-\d+/)![0]);
    const porCodigo = orden();
    expect(porCodigo).toEqual(
      [...porCodigo].sort((a, b) => a.localeCompare(b, 'es', { numeric: true })),
    );
    expect(screen.queryAllByLabelText(/^Prioridad del motor/)).toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: 'Por prioridad del motor' }));
    expect(screen.getAllByLabelText(/^Prioridad del motor/).length).toBeGreaterThan(0);
    expect(localStorage.getItem('reglas.orden')).toBe('prioridad');
  });

  it('no muestra quién modificó un parámetro', () => {
    montar();
    expect(screen.queryByText(/Modificado por/)).toBeNull();
  });

  it('un valor inválido bloquea el guardado y se puede descartar', () => {
    montar();

    fireEvent.change(campo('riego.humedad-objetivo'), { target: { value: '999' } });

    expect(
      (screen.getByRole('button', { name: 'Guardar cambios' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Descartar' }));
    expect(screen.queryByRole('region', { name: 'Cambios sin guardar' })).toBeNull();
  });
});

describe('ReglasTab · interruptores', () => {
  it('apagar una regla de acción con parámetros sin guardar no toca el borrador', async () => {
    const { onCambiarEstado } = montar();
    fireEvent.change(campo('riego.humedad-objetivo'), { target: { value: '60' } });
    fireEvent.change(campo('riego.litros-por-punto'), { target: { value: '4' } });

    fireEvent.click(interruptor('Apagar Riego por déficit hídrico'));
    const dialogo = await screen.findByRole('alertdialog');
    // No es protección: el motivo es opcional y se puede confirmar sin escribirlo.
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Apagar regla' }));

    await waitFor(() =>
      expect(onCambiarEstado).toHaveBeenCalledWith('RiegoPorDeficitRule', { activa: false }),
    );
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull());
    expect(screen.getByText(/2 cambios sin guardar/)).toBeTruthy();
    expect(campo('riego.humedad-objetivo').value).toBe('60');
  });

  it('el motivo opcional viaja si se escribe', async () => {
    const { onCambiarEstado } = montar();

    fireEvent.click(interruptor('Apagar Riego por déficit hídrico'));
    const dialogo = await screen.findByRole('alertdialog');
    fireEvent.change(within(dialogo).getByLabelText(/Motivo/), {
      target: { value: ' riego manual ' },
    });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Apagar regla' }));

    await waitFor(() =>
      expect(onCambiarEstado).toHaveBeenCalledWith('RiegoPorDeficitRule', {
        activa: false,
        motivo: 'riego manual',
      }),
    );
  });

  it('apagar una protección muestra la consecuencia y no se confirma sin motivo', async () => {
    const { onCambiarEstado } = montar();

    fireEvent.click(interruptor('Apagar Sustrato saturado'));
    const dialogo = await screen.findByRole('alertdialog');

    expect(within(dialogo).getByText(/Se riega con el sustrato saturado/)).toBeTruthy();
    const confirmar = within(dialogo).getByRole('button', {
      name: 'Apagar protección',
    }) as HTMLButtonElement;
    expect(confirmar.disabled).toBe(true);

    fireEvent.change(within(dialogo).getByLabelText(/Motivo/), { target: { value: '   ' } });
    expect(confirmar.disabled).toBe(true);
    fireEvent.change(within(dialogo).getByLabelText(/Motivo/), {
      target: { value: 'sonda en recalibración' },
    });
    expect(confirmar.disabled).toBe(false);
    fireEvent.click(confirmar);

    await waitFor(() =>
      expect(onCambiarEstado).toHaveBeenCalledWith('SustratoSaturadoRule', {
        activa: false,
        motivo: 'sonda en recalibración',
        confirmaProteccion: true,
      }),
    );
  });

  it('cancelar cierra el diálogo, no cambia nada y devuelve el foco al interruptor', async () => {
    const { onCambiarEstado } = montar();
    const sw = interruptor('Apagar Sustrato saturado');

    fireEvent.click(sw);
    const dialogo = await screen.findByRole('alertdialog');
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(onCambiarEstado).not.toHaveBeenCalled();
    expect(sw.getAttribute('aria-checked')).toBe('true');
    expect(document.activeElement).toBe(sw);
  });

  it('un clic afuera no cierra la confirmación, haya o no motivo escrito', async () => {
    montar();
    fireEvent.click(interruptor('Apagar Sustrato saturado'));
    const dialogo = await screen.findByRole('alertdialog');

    fireEvent.mouseDown(document.body);
    expect(screen.getByRole('alertdialog')).toBeTruthy();

    fireEvent.change(within(dialogo).getByLabelText(/Motivo/), { target: { value: 'sonda' } });
    fireEvent.mouseDown(document.body);
    expect(screen.getByRole('alertdialog')).toBeTruthy();
    expect((within(dialogo).getByLabelText(/Motivo/) as HTMLTextAreaElement).value).toBe('sonda');
  });

  it('en vuelo no se cancela: ni con clic afuera ni con Escape', async () => {
    let resolver: (r: ReglaCatalogo) => void = () => undefined;
    const onCambiarEstado = vi.fn(
      () => new Promise<ReglaCatalogo>((res) => (resolver = res)),
    );
    montar({ onCambiarEstado });
    fireEvent.click(interruptor('Apagar Riego por déficit hídrico'));
    const dialogo = await screen.findByRole('alertdialog');
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Apagar regla' }));
    await waitFor(() => expect(onCambiarEstado).toHaveBeenCalled());

    fireEvent.mouseDown(document.body);
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.getByRole('alertdialog')).toBeTruthy();

    resolver({ ...catalogoDeFabrica().reglas[0], activa: false });
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull());
  });

  it('Escape cancela la confirmación cuando no hay un pedido en vuelo, y devuelve el foco', async () => {
    const { onCambiarEstado } = montar();
    const sw = interruptor('Apagar Sustrato saturado');
    fireEvent.click(sw);
    await screen.findByRole('alertdialog');

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(onCambiarEstado).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(sw);
  });

  it('tras apagar con éxito el foco vuelve al interruptor de la regla', async () => {
    const { onCambiarEstado } = montar();
    const sw = interruptor('Apagar Riego por déficit hídrico');
    fireEvent.click(sw);
    const dialogo = await screen.findByRole('alertdialog');
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Apagar regla' }));

    await waitFor(() => expect(onCambiarEstado).toHaveBeenCalled());
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull());
    expect(document.activeElement).toBe(
      screen.getByRole('switch', { name: /Riego por déficit hídrico/ }),
    );
  });

  it('es modal: aria-modal y el Tab no se escapa del diálogo', async () => {
    montar();
    fireEvent.click(interruptor('Apagar Riego por déficit hídrico'));
    const dialogo = await screen.findByRole('alertdialog');
    expect(dialogo.getAttribute('aria-modal')).toBe('true');

    const ultimo = within(dialogo).getByRole('button', { name: 'Apagar regla' });
    ultimo.focus();
    fireEvent.keyDown(ultimo, { key: 'Tab' });
    expect(document.activeElement).toBe(within(dialogo).getByLabelText(/Motivo/));

    fireEvent.keyDown(document.activeElement!, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(ultimo);
  });

  it('prender es inmediato: sin diálogo, sin motivo', async () => {
    const { onCambiarEstado } = montar();

    fireEvent.click(interruptor('Prender Dosificación de insumo'));

    await waitFor(() =>
      expect(onCambiarEstado).toHaveBeenCalledWith('SupplyRule', { activa: true }),
    );
    expect(screen.queryByRole('alertdialog')).toBeNull();
  });

  it('si el backend pide algo más, el diálogo lo muestra y sigue abierto', async () => {
    const onCambiarEstado = vi.fn(async () => {
      throw new EstadoReglaError(
        'MOTIVO_REQUERIDO',
        'Para apagar una protección hay que decir el motivo.',
      );
    });
    montar({ onCambiarEstado });

    fireEvent.click(interruptor('Apagar Sustrato saturado'));
    const dialogo = await screen.findByRole('alertdialog');
    fireEvent.change(within(dialogo).getByLabelText(/Motivo/), { target: { value: 'x' } });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Apagar protección' }));

    expect(await within(dialogo).findByText(/hay que decir el motivo/)).toBeTruthy();
    expect(screen.getByRole('alertdialog')).toBeTruthy();
  });
});

describe('ReglasTab · sólo lectura y filtros', () => {
  it('sin reglas.editar: interruptores y campos bloqueados, sin SaveBar y con el aviso del permiso', () => {
    montar({ soloLectura: true });

    expect((interruptor('Apagar Sustrato saturado') as HTMLButtonElement).disabled).toBe(true);
    expect(campo('riego.humedad-objetivo').readOnly).toBe(true);
    expect(screen.queryByRole('region', { name: 'Cambios sin guardar' })).toBeNull();
    expect(screen.getByText(/hace falta el permiso «Editar el motor de reglas»/)).toBeTruthy();
  });

  it('el filtro Apagadas deja sólo las apagadas y los grupos vacíos desaparecen', () => {
    montar();

    fireEvent.click(screen.getByRole('button', { name: /Apagadas/ }));

    expect(screen.getByRole('heading', { name: 'Dosificación de insumo' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: /Riego por déficit hídrico/ })).toBeNull();
    expect(screen.queryByRole('heading', { name: /^Riego/ })).toBeNull();
  });

  it('el filtro Protecciones deja las que protegen y Con cambios las que tienen parámetros editados', () => {
    montar();

    fireEvent.click(screen.getByRole('button', { name: /Protecciones/ }));
    expect(screen.getByRole('heading', { name: /Sustrato saturado/ })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: /Riego por déficit hídrico/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /Protecciones/ }));

    fireEvent.change(campo('riego.humedad-objetivo'), { target: { value: '60' } });
    fireEvent.click(screen.getByRole('button', { name: /Con cambios/ }));
    expect(screen.getByRole('heading', { name: /Riego por déficit hídrico/ })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: /Sustrato saturado/ })).toBeNull();
  });

  it('el grupo Riego cuenta sus reglas y las apagadas, y las ejecuciones no tienen interruptor', () => {
    montar();

    expect(screen.getByRole('heading', { name: 'Riego · 7 reglas' })).toBeTruthy();
    const ejec = screen.getByRole('article', { name: 'Ejecución del riego' });
    expect(within(ejec).queryByRole('switch')).toBeNull();
    expect(campo('riego.sectores-simultaneos')).toBeTruthy();
  });
});
