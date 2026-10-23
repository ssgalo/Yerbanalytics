// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { ReglaCard } from './ReglaCard';
import { catalogoDeFabrica } from '../__fixtures__/catalogos';
import { indicePorClave } from '../catalogoView';
import type { CatalogoReglas, ReglaCatalogo } from '@/types/domain';

afterEach(cleanup);

function montar(
  id: string,
  cambios: Partial<ReglaCatalogo> = {},
  extra: Partial<Parameters<typeof ReglaCard>[0]> = {},
  c: CatalogoReglas = catalogoDeFabrica(),
) {
  const regla = { ...c.reglas.find((r) => r.id === id)!, ...cambios };
  const props = {
    onCambiarEstado: vi.fn(),
    onEditar: vi.fn(),
    onRestablecer: vi.fn(),
  };
  render(
    <ReglaCard
      regla={regla}
      indice={indicePorClave(c)}
      borrador={new Map()}
      erroresCliente={new Map()}
      erroresServidor={new Map()}
      {...props}
      {...extra}
    />,
  );
  return props;
}

describe('ReglaCard', () => {
  it('muestra el nombre y el código, el chip de efecto y el interruptor prendido', () => {
    montar('RiegoPorDeficitRule');

    expect(screen.getByRole('heading', { name: /Riego por déficit hídrico · R-01/ })).toBeTruthy();
    expect(screen.getByText('Actúa')).toBeTruthy();
    const sw = screen.getByRole('switch', { name: 'Apagar Riego por déficit hídrico' });
    expect(sw.getAttribute('aria-checked')).toBe('true');
    expect(screen.queryByText('Retirada')).toBeNull();
  });

  it('una pieza sin código muestra sólo el nombre', () => {
    montar('CicloLecturaRiegoRule');
    expect(screen.getByRole('heading').textContent).toBe('Un riego por ciclo de lectura');
  });

  it('el chip de efecto distingue Protege y Avisa', () => {
    montar('SustratoSaturadoRule');
    expect(screen.getByText('Protege')).toBeTruthy();
    cleanup();
    montar('FollowUpRule');
    expect(screen.getByText('Avisa')).toBeTruthy();
  });

  it('una retirada lo dice y arranca apagada con su aviso', () => {
    montar('SupplyRule');

    expect(screen.getByText('Retirada')).toBeTruthy();
    expect(
      screen
        .getByRole('switch', { name: 'Prender Dosificación de insumo' })
        .getAttribute('aria-checked'),
    ).toBe('false');
    expect(screen.getByText(/Apagada de fábrica/)).toBeTruthy();
  });

  it('una regla de acción apagada muestra quién, cuándo y el motivo en un aviso neutro', () => {
    montar('RiegoPorDeficitRule', {
      activa: false,
      estado: {
        motivo: 'riego manual esta semana',
        updatedBy: 'Ana López',
        updatedTs: new Date(2026, 9, 22, 14, 5).getTime(),
      },
    });

    const aviso = screen.getByText(/Apagada por Ana López el 22\/10 14:05/);
    expect(aviso.textContent).toContain('Motivo: riego manual esta semana');
    expect(aviso.textContent).not.toContain('Consecuencia');
  });

  it('una protección apagada agrega la consecuencia', () => {
    montar('SustratoSaturadoRule', {
      activa: false,
      estado: {
        motivo: 'sonda en recalibración',
        updatedBy: 'Ana López',
        updatedTs: new Date(2026, 9, 22, 14, 5).getTime(),
      },
    });

    const aviso = screen.getByText(/Apagada por Ana López/);
    expect(aviso.textContent).toContain('Consecuencia: Se riega con el sustrato saturado.');
  });

  it('avisa si la demo la apagó', () => {
    montar('RiegoPorDeficitRule', { apagadaPorDemo: true });
    expect(screen.getByText(/Apagada por la demo/)).toBeTruthy();
  });

  it('un clic en el interruptor pide el cambio con el botón como ancla', () => {
    const { onCambiarEstado } = montar('RiegoPorDeficitRule');

    fireEvent.click(screen.getByRole('switch'));

    expect(onCambiarEstado).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'RiegoPorDeficitRule' }),
      false,
      screen.getByRole('switch'),
    );
  });

  it('sin permiso el interruptor está deshabilitado', () => {
    montar('RiegoPorDeficitRule', {}, { soloLectura: true });
    expect((screen.getByRole('switch') as HTMLButtonElement).disabled).toBe(true);
  });

  it('sus parámetros propios se editan en línea con su unidad, rango y fábrica', () => {
    const { onEditar } = montar('RiegoPorDeficitRule');

    const campo = screen.getByLabelText(/Humedad objetivo/) as HTMLInputElement;
    fireEvent.change(campo, { target: { value: '60' } });

    expect(onEditar).toHaveBeenCalledWith(
      expect.objectContaining({ clave: 'riego.humedad-objetivo' }),
      '60',
    );
    expect(screen.getAllByText(/fábrica/).length).toBeGreaterThan(0);
  });

  it('un parámetro compartido va en sólo lectura con "Compartido con N" y el enlace al bloque', () => {
    montar('RiegoPorDeficitRule');

    const fila = document.querySelector('[data-clave="riego.umbral-humedad"]') as HTMLElement;
    expect(within(fila).getByText('Compartido con 5 reglas')).toBeTruthy();
    expect(within(fila).queryByRole('spinbutton')).toBeNull();
    expect(within(fila).getByRole('button', { name: /Editar en Compartidos/ })).toBeTruthy();
  });
});
