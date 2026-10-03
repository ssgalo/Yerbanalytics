// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { ReglaCard } from './ReglaCard';
import { catalogoDeFabrica, conValor } from '../__fixtures__/catalogos';
import { indicePorClave } from '../catalogoView';
import type { CatalogoReglas } from '@/types/domain';

afterEach(cleanup);

function montar(catalogo: CatalogoReglas, id: string, abierta = false, onToggle = vi.fn()) {
  const regla = catalogo.reglas.find((r) => r.id === id)!;
  return render(
    <ReglaCard
      regla={regla}
      indice={indicePorClave(catalogo)}
      nombresReglas={Object.fromEntries(catalogo.reglas.map((r) => [r.id, r.label]))}
      borrador={new Map()}
      erroresCliente={new Map()}
      erroresServidor={new Map()}
      abierta={abierta}
      onToggle={onToggle}
      onEditar={() => undefined}
      onRestablecer={() => undefined}
    />,
  );
}

describe('ReglaCard', () => {
  it('colapsada muestra el nombre, la prioridad y el resumen "N parámetros · M modificados"', () => {
    montar(conValor(catalogoDeFabrica(), 'riego.umbral-humedad', '40'), 'RiegoPorDeficitRule');

    expect(screen.getByText('💦 Riego por déficit hídrico (R-01)')).toBeTruthy();
    expect(screen.getByText('6 parámetros · 1 modificado')).toBeTruthy();
    // Cerrada no renderiza los campos...
    expect(screen.queryByLabelText(/Umbral de riego/)).toBeNull();
  });

  it('colapsada deja ver el valor vigente de cada parámetro sin abrirla', () => {
    montar(conValor(catalogoDeFabrica(), 'riego.umbral-humedad', '40'), 'RiegoPorDeficitRule');

    const resumen = screen.getByTestId('valores-RiegoPorDeficitRule');
    expect(resumen.textContent).toContain('40 %');
    expect(resumen.textContent).toContain('30 L/h');
  });

  it('el encabezado abre y cierra la tarjeta', () => {
    const onToggle = vi.fn();
    montar(catalogoDeFabrica(), 'RiegoPorDeficitRule', false, onToggle);

    const cabecera = screen.getByRole('button', { name: /Riego/ });
    expect(cabecera.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(cabecera);
    expect(onToggle).toHaveBeenCalledOnce();
  });

  it('abierta lista cada parámetro con su valor, unidad, rango, fábrica y referencia', () => {
    montar(catalogoDeFabrica(), 'RiegoPorDeficitRule', true);

    expect((screen.getByLabelText(/Umbral de riego/) as HTMLInputElement).value).toBe('45');
    expect(screen.getByText(/Rango 35–60 %/)).toBeTruthy();
    expect(screen.getAllByText(/Fábrica 45 %/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/reglas_v2 §5 R-01/).length).toBeGreaterThan(0);
  });

  it('una regla sin parámetros lo dice, abierta o cerrada', () => {
    const { unmount } = montar(catalogoDeFabrica(), 'ManualLockRule');
    expect(screen.getByText('Sin parámetros configurables')).toBeTruthy();
    unmount();

    montar(catalogoDeFabrica(), 'ManualLockRule', true);
    expect(screen.getAllByText('Sin parámetros configurables').length).toBeGreaterThanOrEqual(1);
  });

  it('un parámetro modificado se marca y ofrece restablecer a fábrica', () => {
    montar(conValor(catalogoDeFabrica(), 'riego.umbral-humedad', '40'), 'RiegoPorDeficitRule', true);

    expect(screen.getByText('Modificado')).toBeTruthy();
    expect(screen.getByText(/Fábrica 45 %/)).toBeTruthy();
    expect(screen.getByRole('button', { name: /Restablecer/ })).toBeTruthy();
    expect(screen.getByText(/Ingeniero Agrónomo/)).toBeTruthy();
  });
});
