/* ============================================================
   Pestaña Usuarios (HU-20 CA-01/CA-02): personas y cuentas de servicio por separado, bajas a
   pedido, alta, edición y cambio de rol, suspensión/reactivación, baja con confirmación y
   blanqueo de contraseña. Cada operación que corresponde cierra las sesiones del usuario: eso
   lo hace el backend; la UI sólo lo avisa.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { getSeguridadRepository } from '@/data';
import { useAuth } from '@/hooks/AuthContext';
import { nombreRol } from '@/lib/catalogoSeguridad';
import type { Usuario } from '@/types/seguridad';
import { BlanqueoForm, UsuarioForm } from './components/UsuarioForm';
import { ESTADO_USUARIO, fechaHora } from './presentacion';
import styles from './Usuarios.module.css';

type Panel =
  | { modo: 'alta' }
  | { modo: 'edicion'; usuario: Usuario }
  | { modo: 'clave'; usuario: Usuario }
  | { modo: 'baja'; usuario: Usuario }
  | null;

type Feedback = { tipo: 'ok' | 'err'; msg: string } | null;

export function UsuariosTab() {
  const { perfil, recargarPerfil } = useAuth();
  const [usuarios, setUsuarios] = useState<Usuario[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [incluirBajas, setIncluirBajas] = useState(false);
  const [panel, setPanel] = useState<Panel>(null);
  const [feedback, setFeedback] = useState<Feedback>(null);
  const [ocupado, setOcupado] = useState<number | null>(null);

  const cargar = useCallback(async () => {
    try {
      setUsuarios(await getSeguridadRepository().listarUsuarios(incluirBajas));
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [incluirBajas]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  /** Corre una operación sobre un usuario, refresca la lista y deja el resultado a la vista. */
  const operar = async (u: Usuario, accion: () => Promise<unknown>, ok: string) => {
    setFeedback(null);
    setOcupado(u.id);
    try {
      await accion();
      setFeedback({ tipo: 'ok', msg: ok });
      await cargar();
    } catch (e) {
      setFeedback({ tipo: 'err', msg: e instanceof Error ? e.message : String(e) });
    } finally {
      setOcupado(null);
    }
  };

  if (error) {
    return (
      <div className={styles.state} style={{ color: 'var(--crit)' }}>
        No se pudieron cargar los usuarios: {error}
      </div>
    );
  }
  if (!usuarios) return <div className={styles.state}>Cargando usuarios…</div>;

  const personas = usuarios.filter((u) => u.rol !== 'SERVICIO');
  const servicio = usuarios.filter((u) => u.rol === 'SERVICIO');
  const repo = getSeguridadRepository();

  const tabla = (lista: Usuario[], titulo: string, hint: string) => (
    <Card className={styles.tableCard}>
      <div className={styles.tableHead}>
        <h2 className={styles.sectionTitle}>{titulo}</h2>
        <span className={styles.sectionHint}>{hint}</span>
      </div>
      <div className={styles.tableScroll}>
        <table className={styles.table} aria-label={titulo}>
          <thead>
            <tr>
              <th>Usuario</th>
              <th>Rol</th>
              <th>Estado</th>
              <th>Último ingreso</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {lista.length === 0 && (
              <tr>
                <td colSpan={5} className={styles.empty}>
                  No hay {titulo.toLowerCase()}.
                </td>
              </tr>
            )}
            {lista.map((u) => {
              const yo = u.id === perfil?.id;
              const estado = ESTADO_USUARIO[u.estado];
              const baja = u.estado === 'BAJA';
              const deshabilitado = ocupado !== null;
              return (
                <tr key={u.id} data-username={u.username}>
                  <td>
                    <div className={styles.username}>
                      {u.username}
                      {yo && <span className={styles.yo}>vos</span>}
                    </div>
                    <div className={styles.sub}>{u.nombre}</div>
                  </td>
                  <td>{nombreRol(u.rol)}</td>
                  <td>
                    <Badge soft={estado.soft} ink={estado.ink}>
                      {estado.label}
                    </Badge>
                    {u.debeCambiarClave && !baja && <div className={styles.sub}>Clave temporal</div>}
                  </td>
                  <td className={u.ultimoIngreso ? styles.mono : styles.muted}>{fechaHora(u.ultimoIngreso)}</td>
                  <td>
                    {!baja && (
                      <div className={styles.acciones}>
                        <button
                          type="button"
                          className={styles.btnLink}
                          disabled={deshabilitado}
                          onClick={() => setPanel({ modo: 'edicion', usuario: u })}
                        >
                          Editar
                        </button>
                        <button
                          type="button"
                          className={styles.btnLink}
                          disabled={deshabilitado}
                          onClick={() => setPanel({ modo: 'clave', usuario: u })}
                        >
                          Blanquear contraseña
                        </button>
                        {u.estado === 'ACTIVO' ? (
                          <button
                            type="button"
                            className={styles.btnLink}
                            disabled={deshabilitado || yo}
                            title={yo ? 'No podés suspenderte a vos mismo' : undefined}
                            onClick={() =>
                              void operar(u, () => repo.suspenderUsuario(u.id), `«${u.username}» quedó suspendido; sus sesiones se cerraron.`)
                            }
                          >
                            Suspender
                          </button>
                        ) : (
                          <button
                            type="button"
                            className={styles.btnLink}
                            disabled={deshabilitado}
                            onClick={() =>
                              void operar(u, () => repo.reactivarUsuario(u.id), `«${u.username}» puede volver a ingresar.`)
                            }
                          >
                            Reactivar
                          </button>
                        )}
                        <button
                          type="button"
                          className={styles.btnLinkDanger}
                          disabled={deshabilitado || yo}
                          title={yo ? 'No podés darte de baja a vos mismo' : undefined}
                          onClick={() => setPanel({ modo: 'baja', usuario: u })}
                        >
                          Dar de baja
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </Card>
  );

  return (
    <div className={styles.page}>
      <div className={styles.toolbar}>
        <label className={styles.check}>
          <input type="checkbox" checked={incluirBajas} onChange={(e) => setIncluirBajas(e.target.checked)} />
          Mostrar bajas
        </label>
        <span className={styles.spacer} />
        {feedback && (
          <span role={feedback.tipo === 'err' ? 'alert' : 'status'} className={feedback.tipo === 'ok' ? styles.feedbackOk : styles.feedbackErr}>
            {feedback.msg}
          </span>
        )}
        <button
          type="button"
          className={styles.btnPrimary}
          onClick={() => {
            setFeedback(null);
            setPanel({ modo: 'alta' });
          }}
        >
          Nuevo usuario
        </button>
      </div>

      {panel?.modo === 'alta' && (
        <UsuarioForm
          modo="alta"
          onCancel={() => setPanel(null)}
          onSubmit={async (nuevo) => {
            await repo.crearUsuario(nuevo);
            setPanel(null);
            setFeedback({ tipo: 'ok', msg: `«${nuevo.username}» quedó dado de alta. Tiene que cambiar la contraseña al ingresar.` });
            await cargar();
          }}
        />
      )}

      {panel?.modo === 'edicion' && (
        <UsuarioForm
          key={panel.usuario.id}
          modo="edicion"
          usuario={panel.usuario}
          onCancel={() => setPanel(null)}
          onSubmit={async (edicion) => {
            const u = panel.usuario;
            await repo.editarUsuario(u.id, edicion);
            setPanel(null);
            setFeedback({
              tipo: 'ok',
              msg:
                edicion.rol !== u.rol
                  ? `«${u.username}» ahora es ${nombreRol(edicion.rol)}; sus sesiones se cerraron.`
                  : `«${u.username}» quedó actualizado.`,
            });
            await cargar();
            // El nombre propio se ve en la topbar.
            if (u.id === perfil?.id) await recargarPerfil();
          }}
        />
      )}

      {panel?.modo === 'clave' && (
        <BlanqueoForm
          key={panel.usuario.id}
          usuario={panel.usuario}
          onCancel={() => setPanel(null)}
          onSubmit={async (clave) => {
            await repo.blanquearClave(panel.usuario.id, clave);
            setPanel(null);
            setFeedback({ tipo: 'ok', msg: `Se asignó una contraseña temporal a «${panel.usuario.username}».` });
            await cargar();
          }}
        />
      )}

      {panel?.modo === 'baja' && (
        <Card className={styles.section}>
          <div role="alertdialog" aria-label="Confirmar baja">
            <h2 className={styles.sectionTitle}>¿Dar de baja a «{panel.usuario.username}»?</h2>
            <div className={styles.advertencia}>
              La baja es definitiva: se cierran sus sesiones, no puede volver a ingresar ni reactivarse, y su nombre de
              usuario no se reutiliza. El registro se conserva para la auditoría.
            </div>
            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.btnDanger}
                disabled={ocupado !== null}
                onClick={() => {
                  const u = panel.usuario;
                  setPanel(null);
                  void operar(u, () => repo.darDeBajaUsuario(u.id), `«${u.username}» quedó dado de baja.`);
                }}
              >
                Confirmar baja
              </button>
              <button type="button" className={styles.btnSecondary} onClick={() => setPanel(null)}>
                Cancelar
              </button>
            </div>
          </div>
        </Card>
      )}

      {tabla(personas, 'Personas', 'Quienes operan el dashboard')}
      {tabla(servicio, 'Cuentas de servicio', 'Integraciones: simulador, servicio de inferencia')}
    </div>
  );
}
