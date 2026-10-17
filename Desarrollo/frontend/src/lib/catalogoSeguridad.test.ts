import { describe, expect, it } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { CATALOGO_PERMISOS, MATRIZ_POR_DEFECTO, ROLES } from './catalogoSeguridad';

describe('hardware.reparar (S-05)', () => {
  it('está en el catálogo, en el grupo Hardware, y exige poder ver el hardware', () => {
    expect(CATALOGO_PERMISOS).toContainEqual(
      expect.objectContaining({ codigo: 'hardware.reparar', grupo: 'Hardware', lectura: 'hardware.ver' }),
    );
  });

  it('lo tienen quienes están en el vivero cuando algo se rompe, y el Administrador; el Productor no', () => {
    expect(MATRIZ_POR_DEFECTO.OPERARIO).toContain('hardware.reparar');
    expect(MATRIZ_POR_DEFECTO.INGENIERO_AGRONOMO).toContain('hardware.reparar');
    expect(MATRIZ_POR_DEFECTO.SERVICIO).toContain('hardware.reparar');
    expect(MATRIZ_POR_DEFECTO.ADMINISTRADOR).toContain('hardware.reparar');
    expect(MATRIZ_POR_DEFECTO.PRODUCTOR_VIVERISTA).not.toContain('hardware.reparar');
  });

  it('el Operario ve el panel de hardware para poder reparar, pero no da de alta ni recambia', () => {
    expect(MATRIZ_POR_DEFECTO.OPERARIO).toContain('hardware.ver');
    expect(MATRIZ_POR_DEFECTO.OPERARIO).not.toContain('hardware.gestionar');
  });
});

describe('matriz por defecto', () => {
  it('ningún rol tiene una edición sin su lectura', () => {
    for (const rol of ROLES) {
      const permisos = new Set<string>(MATRIZ_POR_DEFECTO[rol]);
      for (const p of CATALOGO_PERMISOS) {
        if (p.lectura && permisos.has(p.codigo)) expect(permisos.has(p.lectura), `${rol} / ${p.codigo}`).toBe(true);
      }
    }
  });
});

/**
 * Anti-drift contra el backend: el catálogo es copia de `enum Permiso`. Se saltea si el árbol del backend no está
 * (p. ej. un clon que sólo trae el frontend).
 */
const PERMISO_JAVA = resolve(
  __dirname,
  '../../../backend/src/main/java/com/yerbanalytics/backend/seguridad/Permiso.java',
);

/** Permisos del backend que el catálogo no lista a propósito: la pestaña Demo Expo ya no vive en este dashboard. */
const SIN_ESPEJO = ['demo-expo.configurar'];

describe.skipIf(!existsSync(PERMISO_JAVA))('catálogo de permisos · contra enum Permiso del backend', () => {
  it('tiene los mismos códigos, en el mismo orden (salvo los que no se espejan a propósito)', () => {
    const src = readFileSync(PERMISO_JAVA, 'utf8');
    const delBackend = [...src.matchAll(/^\s+[A-Z_]+\("([a-z-]+\.[a-z]+)",/gm)]
      .map((m) => m[1])
      .filter((codigo) => !SIN_ESPEJO.includes(codigo));

    expect(delBackend.length).toBeGreaterThan(0);
    expect(CATALOGO_PERMISOS.map((p) => p.codigo)).toEqual(delBackend);
  });
});
