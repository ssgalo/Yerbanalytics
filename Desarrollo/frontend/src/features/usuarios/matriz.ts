/* ============================================================
   Reglas de la matriz de permisos en la UI, puras para poder probarlas.

   El backend rechaza un permiso de edición sin su par de lectura (400). En vez de dejar que el
   Administrador arme una matriz inválida y enterarse al guardar, la UI la mantiene válida:
   marcar una edición marca su lectura, y quitar una lectura quita las ediciones que dependen de
   ella. Los intocables del Administrador no se pueden desmarcar.
   ============================================================ */
import type { PermisoCatalogo, Rol, RolPermisos } from '@/types/seguridad';

export type BorradorMatriz = Map<Rol, Set<string>>;

export function borradorDe(roles: RolPermisos[]): BorradorMatriz {
  return new Map(roles.map((r) => [r.rol, new Set(r.permisos)]));
}

/** Marca o desmarca `codigo` en un rol respetando el par de lectura. Devuelve un set nuevo. */
export function alternarPermiso(
  actuales: ReadonlySet<string>,
  codigo: string,
  catalogo: readonly PermisoCatalogo[],
  intocables: readonly string[] = [],
): Set<string> {
  const nuevos = new Set(actuales);
  if (nuevos.has(codigo)) {
    if (intocables.includes(codigo)) return nuevos;
    nuevos.delete(codigo);
    // Sin la lectura no puede quedar ninguna edición que la requiera.
    for (const p of catalogo) if (p.lectura === codigo && !intocables.includes(p.codigo)) nuevos.delete(p.codigo);
  } else {
    nuevos.add(codigo);
    const lectura = catalogo.find((p) => p.codigo === codigo)?.lectura;
    if (lectura) nuevos.add(lectura);
  }
  return nuevos;
}

/** Roles cuyo borrador difiere de lo guardado, con lo que se agrega y lo que se quita. */
export function rolesModificados(
  roles: RolPermisos[],
  borrador: BorradorMatriz,
): { rol: Rol; agregados: string[]; quitados: string[] }[] {
  return roles
    .map((r) => {
      const nuevos = borrador.get(r.rol) ?? new Set<string>();
      const antes = new Set(r.permisos);
      return {
        rol: r.rol,
        agregados: [...nuevos].filter((c) => !antes.has(c)),
        quitados: r.permisos.filter((c) => !nuevos.has(c)),
      };
    })
    .filter((m) => m.agregados.length > 0 || m.quitados.length > 0);
}

/** El catálogo agrupado por `grupo`, conservando el orden del backend. */
export function agruparCatalogo(catalogo: readonly PermisoCatalogo[]): { grupo: string; permisos: PermisoCatalogo[] }[] {
  const grupos: { grupo: string; permisos: PermisoCatalogo[] }[] = [];
  for (const p of catalogo) {
    const ultimo = grupos[grupos.length - 1];
    if (ultimo && ultimo.grupo === p.grupo) ultimo.permisos.push(p);
    else grupos.push({ grupo: p.grupo, permisos: [p] });
  }
  return grupos;
}

/** Permisos en el orden del catálogo (el backend no exige orden, pero el diff de la auditoría queda prolijo). */
export function enOrdenDeCatalogo(permisos: ReadonlySet<string>, catalogo: readonly PermisoCatalogo[]): string[] {
  return catalogo.map((p) => p.codigo).filter((c) => permisos.has(c));
}
