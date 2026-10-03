/* Catálogos de prueba para los tests de la sección Motor de reglas (no se importan en la app). */
import fixture from '@/data/mock/catalogoReglas.fixture.json';
import type { CatalogoReglas, ParametroRegla } from '@/types/domain';

/** El catálogo de fábrica tal cual lo entrega el backend. */
export const catalogoDeFabrica = (): CatalogoReglas => structuredClone(fixture as CatalogoReglas);

/** Marca un parámetro como editado (valor distinto de fábrica). */
export function conValor(c: CatalogoReglas, clave: string, valor: string): CatalogoReglas {
  const out = structuredClone(c);
  const p = out.parametros.find((x) => x.clave === clave) as ParametroRegla;
  p.valor = valor;
  p.modificado = valor !== p.fabrica;
  p.updatedBy = 'Ingeniero Agrónomo';
  p.updatedTs = Date.UTC(2026, 5, 13, 15, 30);
  return out;
}
