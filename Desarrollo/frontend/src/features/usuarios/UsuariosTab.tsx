/* ============================================================
   Pestaña Usuarios (HU-20 CA-01/CA-02): personas y cuentas de servicio por separado, bajas a
   pedido, alta, edición y cambio de rol, suspensión/reactivación, baja con confirmación y
   restablecimiento de contraseña. Cada operación que corresponde cierra las sesiones del usuario: eso
   lo hace el backend; la UI sólo lo avisa.

   Nada aparece en línea corriendo las tablas: los formularios y la confirmación de baja se abren
   en un popover anclado al botón que se tocó, y el resultado de cada operación es un toast.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Popover } from '@/components/ui/Popover';
import { Toast } from '@/components/ui/Toast';
import { Skeleton } from '@/components/ui/EstadosPantalla';
import { Switch } from '@/components/ui/Seleccion';
import { getSeguridadRepository } from '@/data';
import { useAuth } from '@/hooks/AuthContext';
import { nombreRol } from '@/lib/catalogoSeguridad';
import type { Usuario } from '@/types/seguridad';
import { BlanqueoForm, UsuarioForm } from './components/UsuarioForm';
import { UsersTable } from './components/UsersTable';
import styles from './Usuarios.module.css';

/** Popover abierto y el botón al que se ancla. */
type Panel =
  | ({ anchor: HTMLElement } & (
      | { modo: 'alta' }
      | { modo: 'edicion'; usuario: Usuario }
      | { modo: 'clave'; usuario: Usuario }
      | { modo: 'baja'; usuario: Usuario }
    ))
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
      <Callout tone="crit" role="alert">
        No se pudieron cargar los usuarios: {error}
      </Callout>
    );
  }
  if (!usuarios) {
    return (
      <div className={styles.page} aria-busy="true">
        <span className="sr">Cargando usuarios…</span>
        <Skeleton height={220} />
        <Skeleton height={140} />
      </div>
    );
  }

  const personas = usuarios.filter((u) => u.rol !== 'SERVICIO');
  const servicio = usuarios.filter((u) => u.rol === 'SERVICIO');
  const repo = getSeguridadRepository();

  const tabla = (lista: Usuario[], titulo: string, hint: string) => (
    <UsersTable
      title={titulo}
      hint={hint}
      users={lista}
      selfId={perfil?.id}
      busy={ocupado !== null}
      onEdit={(u, anchor) => setPanel({ modo: 'edicion', usuario: u, anchor })}
      onResetPassword={(u, anchor) => setPanel({ modo: 'clave', usuario: u, anchor })}
      onSuspend={(u) =>
        void operar(u, () => repo.suspenderUsuario(u.id), `«${u.username}» quedó suspendido; sus sesiones se cerraron.`)
      }
      onReactivate={(u) => void operar(u, () => repo.reactivarUsuario(u.id), `«${u.username}» puede volver a ingresar.`)}
      onRemove={(u, anchor) => setPanel({ modo: 'baja', usuario: u, anchor })}
    />
  );

  const cerrar = () => setPanel(null);

  return (
    <div className={styles.page}>
      <div className={styles.toolbar}>
        <span className={styles.toggle}>
          <Switch checked={incluirBajas} onChange={setIncluirBajas} labelledBy="usuarios-mostrar-bajas" />
          <span id="usuarios-mostrar-bajas">Mostrar bajas</span>
        </span>
        <span className={styles.spacer} />
        <Button
          variant="primary"
          aria-expanded={panel?.modo === 'alta'}
          onClick={(e) => {
            setFeedback(null);
            setPanel({ modo: 'alta', anchor: e.currentTarget });
          }}
        >
          Nuevo usuario
        </Button>
      </div>

      {tabla(personas, 'Personas', 'Quienes operan el dashboard')}
      {tabla(servicio, 'Cuentas de servicio', 'Integraciones: simulador, servicio de inferencia')}

      {panel?.modo === 'alta' && (
        <Popover anchor={panel.anchor} onClose={cerrar} label="Nuevo usuario" width={460} alignEnd>
          <UsuarioForm
            modo="alta"
            onCancel={cerrar}
            onSubmit={async (nuevo) => {
              await repo.crearUsuario(nuevo);
              setPanel(null);
              setFeedback({ tipo: 'ok', msg: `«${nuevo.username}» quedó dado de alta. Tiene que cambiar la contraseña al ingresar.` });
              await cargar();
            }}
          />
        </Popover>
      )}

      {panel?.modo === 'edicion' && (
        <Popover anchor={panel.anchor} onClose={cerrar} label={`Editar «${panel.usuario.username}»`} width={460} alignEnd>
          <UsuarioForm
            key={panel.usuario.id}
            modo="edicion"
            usuario={panel.usuario}
            onCancel={cerrar}
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
        </Popover>
      )}

      {panel?.modo === 'clave' && (
        <Popover
          anchor={panel.anchor}
          onClose={cerrar}
          label={`Restablecer la contraseña de «${panel.usuario.username}»`}
          width={340}
          alignEnd
        >
          <BlanqueoForm
            key={panel.usuario.id}
            usuario={panel.usuario}
            onCancel={cerrar}
            onSubmit={async (clave) => {
              await repo.blanquearClave(panel.usuario.id, clave);
              setPanel(null);
              setFeedback({ tipo: 'ok', msg: `Se asignó una contraseña temporal a «${panel.usuario.username}».` });
              await cargar();
            }}
          />
        </Popover>
      )}

      {panel?.modo === 'baja' && (
        <Popover anchor={panel.anchor} onClose={cerrar} label="Confirmar baja" role="alertdialog" width={340} alignEnd>
          <header className={styles.sectionHead}>
            <h2 className={styles.sectionTitle}>¿Dar de baja a «{panel.usuario.username}»?</h2>
          </header>
          <p className={styles.popText}>
            La baja es definitiva: se cierran sus sesiones, no puede volver a ingresar ni reactivarse, y su nombre de
            usuario no se reutiliza. El registro se conserva para la auditoría.
          </p>
          <div className={styles.formActions}>
            <Button
              variant="danger"
              disabled={ocupado !== null}
              onClick={() => {
                const u = panel.usuario;
                setPanel(null);
                void operar(u, () => repo.darDeBajaUsuario(u.id), `«${u.username}» quedó dado de baja.`);
              }}
            >
              Confirmar baja
            </Button>
            <Button variant="ghost" onClick={cerrar}>
              Cancelar
            </Button>
          </div>
        </Popover>
      )}

      {feedback && (
        <Toast tone={feedback.tipo === 'ok' ? 'ok' : 'crit'} onClose={() => setFeedback(null)}>
          {feedback.msg}
        </Toast>
      )}
    </div>
  );
}
