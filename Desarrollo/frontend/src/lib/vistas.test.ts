import { describe, expect, it } from 'vitest';
import { MATRIZ_POR_DEFECTO, CATALOGO_PERMISOS } from '@/lib/catalogoSeguridad';
import { destinoTrasLogin, primeraVistaHabilitada, vistaDeRuta, VISTAS } from './vistas';
import type { Permiso, Rol } from '@/types/seguridad';

const puedeRol = (rol: Rol) => (p: Permiso) => MATRIZ_POR_DEFECTO[rol].includes(p);

describe('tabla vista → permiso (7.2)', () => {
  it('cada permiso de la tabla existe en el catálogo', () => {
    const catalogo = new Set(CATALOGO_PERMISOS.map((p) => p.codigo));
    for (const v of Object.values(VISTAS)) for (const p of v.permisos) expect(catalogo.has(p)).toBe(true);
  });

  it('resuelve la vista por el primer segmento de la ruta', () => {
    expect(vistaDeRuta('/sector/MZ-1-001')).toBe(VISTAS.sector);
    expect(vistaDeRuta('/')).toBe(VISTAS.panel);
    expect(vistaDeRuta('/nada')).toBeNull();
  });

  it('tras el login vuelve a la ruta pedida si el rol la puede ver; si no, a la primera habilitada', () => {
    expect(destinoTrasLogin('/reglas?tab=inspector', puedeRol('PRODUCTOR_VIVERISTA'))).toBe('/reglas?tab=inspector');
    expect(destinoTrasLogin('/reglas', puedeRol('OPERARIO'))).toBe('/');
    expect(destinoTrasLogin(null, puedeRol('OPERARIO'))).toBe('/');
  });

  it('un rol sin vistas no tiene primera vista', () => {
    expect(primeraVistaHabilitada(() => false)).toBeNull();
    expect(primeraVistaHabilitada((p) => p === 'historial.ver')).toBe(VISTAS.historial);
  });
});
