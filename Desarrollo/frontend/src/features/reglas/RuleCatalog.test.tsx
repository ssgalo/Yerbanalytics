// @vitest-environment jsdom
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { EstadoReglaError } from '@/data/estadoReglaError';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import type { CambioEstadoRegla, CatalogoReglas, ReglaCatalogo } from '@/types/domain';
import { RuleCatalog } from './RuleCatalog';
import { catalogoDeFabrica } from './__fixtures__/catalogos';

afterEach(cleanup);

type Props = Parameters<typeof RuleCatalog>[0];

/** Monta la vista con un catálogo que se actualiza como lo hace `useCatalogoReglas`. */
function montar(extra: Partial<Props> = {}, inicial: CatalogoReglas = catalogoDeFabrica()) {
  let actual = inicial;
  const reemplazar = (id: string, cambio: Partial<ReglaCatalogo>) => {
    const r = { ...actual.reglas.find((x) => x.id === id)!, ...cambio };
    actual = { ...actual, reglas: actual.reglas.map((x) => (x.id === id ? r : x)) };
    return r;
  };
  let set: (c: CatalogoReglas) => void = () => undefined;
  const onGuardar = vi.fn(async () => undefined);
  const onCambiarEstado = vi.fn(async (id: string, c: CambioEstadoRegla) => {
    const r = reemplazar(id, { activa: c.activa });
    set(actual);
    return r;
  });
  const onRenombrar = vi.fn(async (id: string, etiqueta: string | null) => {
    const r = reemplazar(id, { label: etiqueta ?? actual.reglas.find((x) => x.id === id)!.etiquetaFabrica });
    set(actual);
    return r;
  });
  const onEliminar = vi.fn(async (id: string) => {
    const r = reemplazar(id, { eliminada: true, activa: false });
    set(actual);
    return r;
  });
  const onRestaurar = vi.fn(async (id: string) => {
    const r = reemplazar(id, { eliminada: false });
    set(actual);
    return r;
  });
  function Harness() {
    const [catalogo, setCatalogo] = useState(inicial);
    set = setCatalogo;
    return (
      <RuleCatalog
        catalogo={catalogo}
        saving={false}
        onGuardar={onGuardar}
        onCambiarEstado={onCambiarEstado}
        onRenombrar={onRenombrar}
        onEliminar={onEliminar}
        onRestaurar={onRestaurar}
        {...extra}
      />
    );
  }
  render(<Harness />);
  return { onGuardar, onCambiarEstado, onRenombrar, onEliminar, onRestaurar };
}

const grupo = (nombre: string) => screen.getByRole('region', { name: nombre });
const fila = (id: string) => document.getElementById(`regla-${id}`)!;
const campo = (regla: string, clave: string) =>
  document.getElementById(`param-${regla}-${clave}`) as HTMLInputElement;
const desplegar = (nombre: RegExp) => fireEvent.click(screen.getByRole('button', { name: nombre }));
const menu = (nombre: RegExp) => {
  fireEvent.click(screen.getByRole('button', { name: new RegExp(`Más acciones de ${nombre.source}`) }));
  return screen.getByRole('menu');
};

describe('RuleCatalog · grupos y filas', () => {
  it('cada fila dice nombre (código), con menú e interruptor; la de ejecución no tiene ninguno de los dos', () => {
    montar();
    const r01 = fila('RiegoPorDeficitRule');

    expect(within(r01).getByText(/Riego por déficit hídrico/).textContent).toBe('Riego por déficit hídrico (R-01)');
    expect(within(r01).getByRole('switch', { name: 'Apagar Riego por déficit hídrico' })).toBeTruthy();
    expect(within(r01).getByRole('button', { name: /Más acciones/ })).toBeTruthy();

    const despacho = fila('DespachoRiego');
    expect(grupo('Riego').contains(despacho)).toBe(true);
    expect(within(despacho).queryByRole('switch')).toBeNull();
    expect(within(despacho).queryByRole('button', { name: /Más acciones/ })).toBeNull();
  });

  it('sin código, entre paréntesis va el nombre interno sin "Rule"', () => {
    montar();
    expect(within(fila('ShadingRule')).getByText(/\(Shading\)/)).toBeTruthy();
  });

  it('la cabecera del grupo cuenta las activas y su interruptor está prendido si lo están todas', () => {
    montar();
    const riego = grupo('Riego');
    expect(within(riego).getByText('8 de 8')).toBeTruthy();
    expect(within(riego).getByRole('switch', { name: 'Todas las reglas de Riego' }).getAttribute('aria-checked')).toBe(
      'true',
    );
  });

  it('una fila sin parámetros no tiene flecha ni reacciona al clic', () => {
    montar();
    const bloqueo = fila('ManualLockRule');
    // Su único botón es el menú ⋮: no hay uno para desplegar.
    expect(within(bloqueo).getAllByRole('button').filter((b) => !b.hasAttribute('aria-haspopup'))).toEqual([]);
    fireEvent.click(within(bloqueo).getByText(/Bloqueo manual/));
    expect(bloqueo.querySelector('input')).toBeNull();
  });

  it('desplegar muestra los parámetros con unidad y rango; otra fila cierra la anterior', () => {
    montar();
    desplegar(/^Riego por déficit hídrico/);

    const umbral = campo('RiegoPorDeficitRule', 'riego.umbral-humedad');
    expect(umbral.value).toBe('45');
    expect(within(fila('RiegoPorDeficitRule')).getByText('35–60 %')).toBeTruthy();

    desplegar(/^Posponer por lluvia/);
    expect(campo('RiegoPorDeficitRule', 'riego.umbral-humedad')).toBeNull();
    expect(campo('PosponerPorLluviaRule', 'riego.umbral-humedad')).toBeTruthy();
  });

  it('?regla= despliega la fila y lleva hasta ella', () => {
    const scroll = vi.fn();
    window.HTMLElement.prototype.scrollIntoView = scroll;
    montar({ reglaInicial: 'PosponerPorLluviaRule' });

    expect(campo('PosponerPorLluviaRule', 'riego.lluvia-probabilidad')).toBeTruthy();
    expect(scroll).toHaveBeenCalled();
  });
});

describe('RuleCatalog · parámetros por copia', () => {
  it('editar marca la fila y el campo, y guarda en lote un cambio por (regla, clave)', async () => {
    const { onGuardar } = montar();
    desplegar(/^Riego por déficit hídrico/);
    fireEvent.change(campo('RiegoPorDeficitRule', 'riego.umbral-humedad'), { target: { value: '40' } });
    desplegar(/^Posponer por lluvia/);
    // La copia de R-03 sigue en su valor: no es la misma que la de R-01.
    expect(campo('PosponerPorLluviaRule', 'riego.umbral-humedad').value).toBe('45');
    fireEvent.change(campo('PosponerPorLluviaRule', 'riego.umbral-humedad'), { target: { value: '50' } });

    expect(within(fila('RiegoPorDeficitRule')).getByRole('img', { name: 'Cambios sin guardar' })).toBeTruthy();
    expect(screen.getByText(/2 cambios sin guardar/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(onGuardar).toHaveBeenCalledTimes(1));
    expect(onGuardar).toHaveBeenCalledWith(
      expect.arrayContaining([
        { regla: 'RiegoPorDeficitRule', clave: 'riego.umbral-humedad', valor: '40' },
        { regla: 'PosponerPorLluviaRule', clave: 'riego.umbral-humedad', valor: '50' },
      ]),
    );
  });

  it('un valor inválido bloquea el guardado y se puede descartar', () => {
    montar();
    desplegar(/^Riego por déficit hídrico/);
    fireEvent.change(campo('RiegoPorDeficitRule', 'riego.umbral-humedad'), { target: { value: '99' } });

    expect(screen.getByText('Debe estar entre 35 y 60 %.')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Guardar' }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Descartar' }));
    expect(campo('RiegoPorDeficitRule', 'riego.umbral-humedad').value).toBe('45');
    expect(screen.queryByText(/sin guardar/)).toBeNull();
  });

  it('un rechazo del servidor se muestra junto a la copia y despliega su fila', async () => {
    const onGuardar = vi.fn(async () => {
      throw new ParametrosInvalidosError([
        { regla: 'RiegoPorDeficitRule', clave: 'riego.umbral-humedad', mensaje: 'Choca con el umbral crítico.' },
      ]);
    });
    montar({ onGuardar });
    desplegar(/^Riego por déficit hídrico/);
    fireEvent.change(campo('RiegoPorDeficitRule', 'riego.umbral-humedad'), { target: { value: '40' } });
    desplegar(/^Riego por déficit hídrico/); // se cierra
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('Choca con el umbral crítico.')).toBeTruthy();
    expect(campo('RiegoPorDeficitRule', 'riego.umbral-humedad')).toBeTruthy();
  });
});

describe('RuleCatalog · interruptores', () => {
  it('apagar una regla común es inmediato y deja el grupo a medio camino', async () => {
    const { onCambiarEstado } = montar();

    fireEvent.click(screen.getByRole('switch', { name: 'Apagar Riego por déficit hídrico' }));

    await waitFor(() => expect(onCambiarEstado).toHaveBeenCalledWith('RiegoPorDeficitRule', { activa: false }));
    expect(screen.queryByRole('alertdialog')).toBeNull();
    const sw = within(grupo('Riego')).getByRole('switch', { name: 'Todas las reglas de Riego' });
    await waitFor(() => expect(sw.getAttribute('aria-checked')).toBe('mixed'));
    expect(within(grupo('Riego')).getByText('7 de 8')).toBeTruthy();
  });

  it('el grupo a medio camino prende las apagadas', async () => {
    const { onCambiarEstado } = montar();
    fireEvent.click(screen.getByRole('switch', { name: 'Apagar Riego por déficit hídrico' }));
    await waitFor(() => expect(onCambiarEstado).toHaveBeenCalledTimes(1));

    fireEvent.click(within(grupo('Riego')).getByRole('switch', { name: 'Todas las reglas de Riego' }));

    await waitFor(() => expect(onCambiarEstado).toHaveBeenLastCalledWith('RiegoPorDeficitRule', { activa: true }));
    expect(onCambiarEstado).toHaveBeenCalledTimes(2);
  });

  it('apagar un grupo sin protecciones apaga todas sin pedir confirmación', async () => {
    const { onCambiarEstado } = montar();

    fireEvent.click(within(grupo('Sanidad')).getByRole('switch', { name: 'Todas las reglas de Sanidad' }));

    await waitFor(() => expect(onCambiarEstado).toHaveBeenCalledTimes(5));
    expect(screen.queryByRole('alertdialog')).toBeNull();
    for (const [, cambio] of onCambiarEstado.mock.calls) expect(cambio).toEqual({ activa: false });
    await waitFor(() =>
      expect(
        within(grupo('Sanidad')).getByRole('switch', { name: 'Todas las reglas de Sanidad' }).getAttribute('aria-checked'),
      ).toBe('false'),
    );
  });

  it('apagar un grupo con protecciones pide motivo antes de apagar nada', async () => {
    const { onCambiarEstado } = montar();

    fireEvent.click(within(grupo('Riego')).getByRole('switch', { name: 'Todas las reglas de Riego' }));
    const dialogo = await screen.findByRole('alertdialog');
    expect(onCambiarEstado).not.toHaveBeenCalled();
    expect(within(dialogo).getByText(/Se riega con el sustrato saturado/)).toBeTruthy();
    const confirmar = within(dialogo).getByRole('button', { name: 'Apagar 8' }) as HTMLButtonElement;
    expect(confirmar.disabled).toBe(true);

    fireEvent.change(within(dialogo).getByLabelText(/Motivo/), { target: { value: 'mantenimiento' } });
    fireEvent.click(confirmar);

    await waitFor(() => expect(onCambiarEstado).toHaveBeenCalledTimes(8));
    expect(onCambiarEstado).toHaveBeenCalledWith('SustratoSaturadoRule', {
      activa: false,
      motivo: 'mantenimiento',
      confirmaProteccion: true,
    });
    expect(onCambiarEstado).toHaveBeenCalledWith('RiegoPorDeficitRule', { activa: false, motivo: 'mantenimiento' });
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull());
  });

  it('apagar una protección muestra la consecuencia y exige el motivo', async () => {
    const { onCambiarEstado } = montar();

    fireEvent.click(screen.getByRole('switch', { name: 'Apagar Sustrato saturado' }));
    const dialogo = await screen.findByRole('alertdialog');
    expect(dialogo.getAttribute('aria-modal')).toBe('true');
    expect(within(dialogo).getByText(/Se riega con el sustrato saturado/)).toBeTruthy();
    const confirmar = within(dialogo).getByRole('button', { name: 'Apagar protección' }) as HTMLButtonElement;
    fireEvent.change(within(dialogo).getByLabelText(/Motivo/), { target: { value: '   ' } });
    expect(confirmar.disabled).toBe(true);
    fireEvent.change(within(dialogo).getByLabelText(/Motivo/), { target: { value: 'sonda en recalibración' } });
    fireEvent.click(confirmar);

    await waitFor(() =>
      expect(onCambiarEstado).toHaveBeenCalledWith('SustratoSaturadoRule', {
        activa: false,
        motivo: 'sonda en recalibración',
        confirmaProteccion: true,
      }),
    );
  });

  it('cancelar (o Escape) no cambia nada y devuelve el foco al interruptor', async () => {
    const { onCambiarEstado } = montar();
    const sw = screen.getByRole('switch', { name: 'Apagar Sustrato saturado' });

    fireEvent.click(sw);
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(document.activeElement).toBe(sw);

    fireEvent.click(sw);
    await screen.findByRole('alertdialog');
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('alertdialog')).toBeNull();
    expect(onCambiarEstado).not.toHaveBeenCalled();
  });

  it('si el backend pide algo más, el diálogo lo muestra y sigue abierto', async () => {
    const onCambiarEstado = vi.fn(async () => {
      throw new EstadoReglaError('MOTIVO_REQUERIDO', 'Para apagar una protección hay que decir el motivo.');
    });
    montar({ onCambiarEstado });

    fireEvent.click(screen.getByRole('switch', { name: 'Apagar Sustrato saturado' }));
    const dialogo = await screen.findByRole('alertdialog');
    fireEvent.change(within(dialogo).getByLabelText(/Motivo/), { target: { value: 'x' } });
    fireEvent.click(within(dialogo).getByRole('button', { name: 'Apagar protección' }));

    expect(await within(dialogo).findByText(/hay que decir el motivo/)).toBeTruthy();
  });
});

describe('RuleCatalog · menú de la regla', () => {
  it('Editar nombre cambia el nombre en la misma fila', async () => {
    const { onRenombrar } = montar();
    fireEvent.click(within(menu(/Posponer por lluvia/)).getByRole('menuitem', { name: 'Editar nombre' }));

    const input = within(fila('PosponerPorLluviaRule')).getByRole('textbox', { name: 'Nombre de la regla' });
    fireEvent.change(input, { target: { value: 'Lluvia a la vista' } });
    fireEvent.click(within(fila('PosponerPorLluviaRule')).getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(onRenombrar).toHaveBeenCalledWith('PosponerPorLluviaRule', 'Lluvia a la vista'));
    expect(await within(fila('PosponerPorLluviaRule')).findByText(/Lluvia a la vista/)).toBeTruthy();
    expect(within(fila('PosponerPorLluviaRule')).getByText(/\(R-03\)/)).toBeTruthy();
  });

  it('una protección sólo ofrece Editar nombre', () => {
    montar();
    const m = menu(/Sustrato saturado/);
    expect(within(m).getAllByRole('menuitem').map((b) => b.textContent)).toEqual(['Editar nombre']);
  });

  it('eliminar pide confirmación en la fila; la fila sale y el pie la deja restaurar', async () => {
    const { onEliminar, onRestaurar } = montar();
    fireEvent.click(within(menu(/Posponer por lluvia/)).getByRole('menuitem', { name: 'Eliminar' }));
    const confirmacion = within(fila('PosponerPorLluviaRule')).getByRole('alertdialog');
    expect(onEliminar).not.toHaveBeenCalled();

    fireEvent.click(within(confirmacion).getByRole('button', { name: 'Eliminar' }));

    await waitFor(() => expect(fila('PosponerPorLluviaRule')).toBeNull());
    expect(onEliminar).toHaveBeenCalledWith('PosponerPorLluviaRule');
    expect(screen.getByText(/1 regla eliminada/)).toBeTruthy();
    expect(within(grupo('Riego')).getByText('7 de 7')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Ver' }));
    const lista = screen.getByRole('list', { name: 'Reglas eliminadas' });
    fireEvent.click(within(lista).getByRole('button', { name: 'Restaurar' }));

    await waitFor(() => expect(onRestaurar).toHaveBeenCalledWith('PosponerPorLluviaRule'));
    expect(await screen.findByRole('switch', { name: 'Prender Posponer por lluvia' })).toBeTruthy();
    expect(screen.queryByText(/regla eliminada/)).toBeNull();
  });
});

describe('RuleCatalog · sólo lectura', () => {
  it('sin reglas.editar: interruptores bloqueados, sin menú ni SaveBar, campos de sólo lectura y el aviso', () => {
    montar({ soloLectura: true });

    expect(
      (screen.getByRole('switch', { name: 'Apagar Riego por déficit hídrico' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    expect(
      (screen.getByRole('switch', { name: 'Todas las reglas de Riego' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    expect(screen.queryByRole('button', { name: /Más acciones/ })).toBeNull();
    desplegar(/^Riego por déficit hídrico/);
    expect(campo('RiegoPorDeficitRule', 'riego.umbral-humedad').readOnly).toBe(true);
    expect(screen.getByText(/hace falta el permiso/)).toBeTruthy();
  });
});
