import { describe, expect, it } from 'vitest';
import { CATALOGO_PERMISOS, INTOCABLES, MATRIZ_POR_DEFECTO, ROLES } from '@/lib/catalogoSeguridad';
import { agruparCatalogo, alternarPermiso, borradorDe, rolesModificados } from './matriz';
import type { RolPermisos } from '@/types/seguridad';

const roles = (): RolPermisos[] =>
  ROLES.map((rol) => ({ rol, nombre: rol, permisos: [...MATRIZ_POR_DEFECTO[rol]], intocables: [...INTOCABLES[rol]] }));

describe('matriz · par de lectura (8.3)', () => {
  it('marcar una edición marca también su lectura', () => {
    const r = alternarPermiso(new Set(['vivero.ver']), 'reglas.editar', CATALOGO_PERMISOS);
    expect([...r].sort()).toEqual(['reglas.editar', 'reglas.ver', 'vivero.ver']);
  });

  it('quitar una lectura quita las ediciones que dependen de ella', () => {
    const r = alternarPermiso(new Set(['reglas.ver', 'reglas.editar', 'vivero.ver']), 'reglas.ver', CATALOGO_PERMISOS);
    expect([...r]).toEqual(['vivero.ver']);
  });

  it('un intocable no se puede desmarcar', () => {
    const r = alternarPermiso(new Set(['usuarios.gestionar']), 'usuarios.gestionar', CATALOGO_PERMISOS, ['usuarios.gestionar']);
    expect(r.has('usuarios.gestionar')).toBe(true);
  });

  it('ninguna combinación de clics deja una edición sin su lectura', () => {
    let s = new Set<string>();
    const codigos = CATALOGO_PERMISOS.map((p) => p.codigo);
    for (let i = 0; i < 200; i++) {
      s = alternarPermiso(s, codigos[(i * 7) % codigos.length], CATALOGO_PERMISOS);
      for (const p of CATALOGO_PERMISOS) if (p.lectura && s.has(p.codigo)) expect(s.has(p.lectura)).toBe(true);
    }
  });
});

describe('matriz · borrador', () => {
  it('detecta los roles modificados con lo agregado y lo quitado', () => {
    const rs = roles();
    const b = borradorDe(rs);
    b.set('OPERARIO', alternarPermiso(b.get('OPERARIO')!, 'pasadas.operar', CATALOGO_PERMISOS));
    b.set('PRODUCTOR_VIVERISTA', alternarPermiso(b.get('PRODUCTOR_VIVERISTA')!, 'pasadas.operar', CATALOGO_PERMISOS));

    expect(rolesModificados(rs, b)).toEqual([
      { rol: 'PRODUCTOR_VIVERISTA', agregados: [], quitados: ['pasadas.operar'] },
      { rol: 'OPERARIO', agregados: ['pasadas.operar'], quitados: [] },
    ]);
  });

  it('agrupa el catálogo en el orden del backend', () => {
    expect(agruparCatalogo(CATALOGO_PERMISOS).map((g) => g.grupo)).toEqual([
      'Vivero',
      'Diagnósticos',
      'Historial',
      'Configuración',
      'Motor de reglas',
      'Hardware',
      'Topología',
      'Captura',
      'Pasadas del riel',
      'Demo Expo',
      'Seguridad',
    ]);
  });
});
