/* ============================================================
   Pestaña Roles y permisos (HU-20 CA-01/CA-02): la matriz rol × permiso, agrupada como el
   catálogo. Se edita un borrador y se guarda por rol (`PUT /api/roles/{rol}/permisos`).

   - La UI mantiene la matriz válida: una edición arrastra su lectura y sacar la lectura saca
     las ediciones (ver `matriz.ts`). El backend igual lo valida.
   - Los intocables del Administrador se ven marcados y bloqueados.
   - Guardar cierra las sesiones de los usuarios de cada rol modificado (salvo la del autor):
     se avisa antes, no después.
   ============================================================ */
import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { getSeguridadRepository } from '@/data';
import { useAuth } from '@/hooks/AuthContext';
import { contar } from '@/lib/plural';
import type { PermisoCatalogo, Rol, RolPermisos } from '@/types/seguridad';
import {
  agruparCatalogo,
  alternarPermiso,
  borradorDe,
  enOrdenDeCatalogo,
  rolesModificados,
  type BorradorMatriz,
} from './matriz';
import styles from './Usuarios.module.css';

export function RolesTab() {
  const { perfil, recargarPerfil } = useAuth();
  const [catalogo, setCatalogo] = useState<PermisoCatalogo[] | null>(null);
  const [roles, setRoles] = useState<RolPermisos[] | null>(null);
  const [borrador, setBorrador] = useState<BorradorMatriz>(new Map());
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [resultado, setResultado] = useState<{ ok: string[]; errores: string[] } | null>(null);

  const cargar = useCallback(async () => {
    try {
      const repo = getSeguridadRepository();
      const [c, r] = await Promise.all([repo.getCatalogoPermisos(), repo.getRoles()]);
      setCatalogo(c);
      setRoles(r);
      setBorrador(borradorDe(r));
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  const modificados = useMemo(() => (roles ? rolesModificados(roles, borrador) : []), [roles, borrador]);
  const grupos = useMemo(() => (catalogo ? agruparCatalogo(catalogo) : []), [catalogo]);

  if (error) {
    return (
      <div className={styles.state} style={{ color: 'var(--crit)' }}>
        No se pudo cargar la matriz de permisos: {error}
      </div>
    );
  }
  if (!catalogo || !roles) return <div className={styles.state}>Cargando la matriz de permisos…</div>;

  const alternar = (r: RolPermisos, codigo: string) => {
    setResultado(null);
    setBorrador((b) => {
      const n = new Map(b);
      n.set(r.rol, alternarPermiso(b.get(r.rol) ?? new Set(), codigo, catalogo, r.intocables));
      return n;
    });
  };

  const guardar = async () => {
    setGuardando(true);
    setResultado(null);
    const ok: string[] = [];
    const errores: string[] = [];
    const guardados = new Map<Rol, RolPermisos>();
    // Un PUT por rol: si uno falla, los otros igual se guardan y cada resultado se informa.
    for (const m of modificados) {
      const nombre = roles.find((r) => r.rol === m.rol)?.nombre ?? m.rol;
      try {
        const permisos = enOrdenDeCatalogo(borrador.get(m.rol) ?? new Set(), catalogo);
        guardados.set(m.rol, await getSeguridadRepository().guardarPermisosRol(m.rol, permisos));
        ok.push(nombre);
      } catch (e) {
        errores.push(`${nombre}: ${e instanceof Error ? e.message : String(e)}`);
      }
    }
    const nuevos = roles.map((r) => guardados.get(r.rol) ?? r);
    setRoles(nuevos);
    // Lo guardado pasa a ser la base; lo que falló queda en el borrador para corregirlo.
    setBorrador((b) => {
      const n = new Map(b);
      for (const [rol, r] of guardados) n.set(rol, new Set(r.permisos));
      return n;
    });
    setResultado({ ok, errores });
    setGuardando(false);
    // El autor sigue operando, pero con la matriz nueva: su menú y sus acciones se actualizan.
    if (perfil && guardados.has(perfil.rol)) await recargarPerfil();
  };

  return (
    <div className={styles.page}>
      <p className={styles.intro}>
        Qué puede hacer cada rol. Un permiso de edición necesita su par de lectura: marcarlo marca también la lectura,
        y quitar la lectura quita la edición. El Administrador conserva siempre la gestión de usuarios y la auditoría.
      </p>

      <Card className={styles.tableCard}>
        <div className={styles.tableScroll}>
          <table className={`${styles.table} ${styles.matriz}`} aria-label="Matriz de permisos">
            <thead>
              <tr>
                <th>Permiso</th>
                {roles.map((r) => {
                  const cambiado = modificados.some((m) => m.rol === r.rol);
                  return (
                    <th key={r.rol} className={`${styles.rolCol} ${cambiado ? styles.rolModificado : ''}`}>
                      {r.nombre}
                      {cambiado ? ' •' : ''}
                    </th>
                  );
                })}
              </tr>
            </thead>
            <tbody>
              {grupos.map((g) => (
                <Fragment key={g.grupo}>
                  <tr className={styles.grupoFila}>
                    <td colSpan={roles.length + 1}>{g.grupo}</td>
                  </tr>
                  {g.permisos.map((p) => (
                    <tr key={p.codigo}>
                      <td>
                        <div className={styles.codigo}>{p.codigo}</div>
                        <div className={styles.sub}>{p.descripcion}</div>
                        {p.lectura && <div className={styles.dependeDe}>Requiere {p.lectura}</div>}
                      </td>
                      {roles.map((r) => {
                        const marcado = borrador.get(r.rol)?.has(p.codigo) ?? false;
                        const intocable = r.intocables.includes(p.codigo);
                        const cambio = marcado !== r.permisos.includes(p.codigo);
                        return (
                          <td key={r.rol} className={`${styles.celda} ${cambio ? styles.celdaCambio : ''}`}>
                            <input
                              type="checkbox"
                              aria-label={`${p.codigo} para ${r.nombre}`}
                              checked={marcado}
                              disabled={intocable || guardando}
                              title={intocable ? `El ${r.nombre} siempre conserva este permiso` : undefined}
                              onChange={() => alternar(r, p.codigo)}
                            />
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {modificados.length > 0 && (
        <div role="note" className={styles.advertencia}>
          Guardar cierra las sesiones abiertas de los usuarios con{' '}
          {modificados.length === 1 ? 'el rol' : 'los roles'}{' '}
          {modificados.map((m) => roles.find((r) => r.rol === m.rol)?.nombre).join(', ')} (salvo la tuya): vuelven a
          ingresar con los permisos nuevos.
        </div>
      )}
      {resultado && resultado.ok.length > 0 && (
        <div role="status" className={styles.okBox}>
          Permisos guardados: {resultado.ok.join(', ')}.
        </div>
      )}
      {resultado && resultado.errores.length > 0 && (
        <div role="alert" className={styles.errorBox}>
          {resultado.errores.map((e) => (
            <div key={e}>{e}</div>
          ))}
        </div>
      )}

      <div className={styles.toolbar}>
        <span className={styles.sectionHint}>
          {modificados.length === 0
            ? 'Sin cambios pendientes.'
            : `${contar(modificados.length, 'rol modificado', 'roles modificados')} sin guardar`}
        </span>
        <span className={styles.spacer} />
        <button
          type="button"
          className={styles.btnSecondary}
          disabled={guardando || modificados.length === 0}
          onClick={() => {
            setBorrador(borradorDe(roles));
            setResultado(null);
          }}
        >
          Descartar
        </button>
        <button
          type="button"
          className={styles.btnPrimary}
          disabled={guardando || modificados.length === 0}
          onClick={() => void guardar()}
        >
          {guardando ? 'Guardando…' : 'Guardar cambios'}
        </button>
      </div>
    </div>
  );
}
