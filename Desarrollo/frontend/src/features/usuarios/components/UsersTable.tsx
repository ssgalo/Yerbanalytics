/* Tabla de cuentas (personas o servicio). Cada columna se filtra y se ordena desde su encabezado,
   como en una planilla; las acciones de la fila las resuelve la pestaña. */
import { Button } from '@/components/ui/Button';
import { Chip } from '@/components/ui/Chip';
import { ColumnHeader } from '@/components/ui/ColumnHeader';
import { useColumnQuery } from '@/hooks/useColumnQuery';
import { ROLES, nombreRol } from '@/lib/catalogoSeguridad';
import type { Column } from '@/lib/columnQuery';
import type { EstadoUsuario, Usuario } from '@/types/seguridad';
import { ESTADO_USUARIO, fechaHora } from '../presentacion';
import { EmptyRow, TableResult } from './TableParts';
import styles from '../Usuarios.module.css';

const ESTADOS: EstadoUsuario[] = ['ACTIVO', 'SUSPENDIDO', 'BAJA'];
const AZ = { asc: 'De A a Z', desc: 'De Z a A' };

const COLUMNS: Column<Usuario>[] = [
  {
    key: 'username',
    label: 'Usuario',
    kind: 'values',
    value: (u) => u.username,
    optionLabel: (v, u) => `${v} · ${u.nombre}`,
    searchable: true,
    sortLabels: AZ,
  },
  {
    key: 'rol',
    label: 'Rol',
    kind: 'values',
    value: (u) => u.rol,
    optionLabel: nombreRol,
    compare: (a, b) => ROLES.indexOf(a as Usuario['rol']) - ROLES.indexOf(b as Usuario['rol']),
    sortLabels: { asc: 'Como el catálogo', desc: 'Al revés del catálogo' },
  },
  {
    key: 'estado',
    label: 'Estado',
    kind: 'values',
    value: (u) => u.estado,
    optionLabel: (v) => ESTADO_USUARIO[v as EstadoUsuario].label,
    compare: (a, b) => ESTADOS.indexOf(a as EstadoUsuario) - ESTADOS.indexOf(b as EstadoUsuario),
    sortLabels: { asc: 'Activos primero', desc: 'Bajas primero' },
  },
  {
    key: 'ultimoIngreso',
    label: 'Último ingreso',
    kind: 'date',
    date: (u) => (u.ultimoIngreso ? Date.parse(u.ultimoIngreso) : null),
    sortLabels: { asc: 'Más antiguos primero', desc: 'Más recientes primero' },
  },
];

interface UsersTableProps {
  title: string;
  hint: string;
  users: Usuario[];
  /** id de quien mira: no puede suspenderse ni darse de baja. */
  selfId: number | undefined;
  /** Hay una operación en curso: las acciones esperan. */
  busy: boolean;
  onEdit: (u: Usuario, anchor: HTMLElement) => void;
  onResetPassword: (u: Usuario, anchor: HTMLElement) => void;
  onSuspend: (u: Usuario) => void;
  onReactivate: (u: Usuario) => void;
  /* Editar, contraseña y baja se abren en un popover anclado al botón que se tocó. */
  onRemove: (u: Usuario, anchor: HTMLElement) => void;
}

export function UsersTable({
  title,
  hint,
  users,
  selfId,
  busy,
  onEdit,
  onResetPassword,
  onSuspend,
  onReactivate,
  onRemove,
}: UsersTableProps) {
  const query = useColumnQuery(users, COLUMNS);

  return (
    <section className={styles.tableSection}>
      <header className={styles.sectionHead}>
        <div>
          <h2 className={styles.sectionTitle}>{title}</h2>
          <p className={styles.sectionHint}>{hint}</p>
        </div>
        <TableResult query={query} noun={['cuenta', 'cuentas']} />
      </header>
      <div className={styles.tableScroll}>
        <table className={styles.table} aria-label={title}>
          <thead>
            <tr>
              {COLUMNS.map((c) => (
                <th key={c.key} className={styles.thMenu}>
                  <ColumnHeader column={c} query={query} />
                </th>
              ))}
              <th className={styles.thActions}>
                <span className="sr">Acciones</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {query.visible.length === 0 && (
              <EmptyRow query={query} colSpan={COLUMNS.length + 1} empty={`No hay ${title.toLowerCase()}.`} />
            )}
            {query.visible.map((u) => {
              const self = u.id === selfId;
              const estado = ESTADO_USUARIO[u.estado];
              const removed = u.estado === 'BAJA';
              return (
                <tr key={u.id} data-username={u.username}>
                  <td>
                    <div className={styles.userCell}>
                      {u.username}
                      {self && <Chip tone="line">vos</Chip>}
                    </div>
                    <div className={styles.sub}>{u.nombre}</div>
                  </td>
                  <td>{nombreRol(u.rol)}</td>
                  <td>
                    <div className={styles.stateCell}>
                      <Chip tone={estado.tone}>{estado.label}</Chip>
                      {u.debeCambiarClave && !removed && <span className={styles.sub}>Clave temporal</span>}
                    </div>
                  </td>
                  <td className={u.ultimoIngreso ? styles.num : styles.muted}>{fechaHora(u.ultimoIngreso)}</td>
                  <td>
                    {!removed && (
                      <div className={styles.actions}>
                        <Button size="sm" variant="ghost" disabled={busy} onClick={(e) => onEdit(u, e.currentTarget)}>
                          Editar
                        </Button>
                        <Button size="sm" variant="ghost" disabled={busy} onClick={(e) => onResetPassword(u, e.currentTarget)}>
                          Restablecer contraseña
                        </Button>
                        {/* El botón deshabilitado no recibe el puntero: el motivo va en el contenedor. */}
                        {u.estado === 'ACTIVO' ? (
                          <span title={self ? 'No podés suspenderte a vos mismo' : undefined}>
                            <Button size="sm" disabled={busy || self} onClick={() => onSuspend(u)}>
                              Suspender
                            </Button>
                          </span>
                        ) : (
                          <Button size="sm" disabled={busy} onClick={() => onReactivate(u)}>
                            Reactivar
                          </Button>
                        )}
                        <span title={self ? 'No podés darte de baja a vos mismo' : undefined}>
                          <Button size="sm" variant="danger" disabled={busy || self} onClick={(e) => onRemove(u, e.currentTarget)}>
                            Dar de baja
                          </Button>
                        </span>
                      </div>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
