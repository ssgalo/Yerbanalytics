/* ============================================================
   Alta y edición de un usuario (HU-20 CA-01). En la edición el nombre de usuario no se toca.
   Valida lo mismo que el backend antes de enviar; lo que el backend rechace igual (409 por
   username repetido, último Administrador…) se muestra con su mensaje.
   ============================================================ */
import { useState, type FormEvent } from 'react';
import { Card } from '@/components/ui/Card';
import { ROLES, nombreRol } from '@/lib/catalogoSeguridad';
import { errorClave } from '@/lib/claves';
import type { EdicionUsuario, NuevoUsuario, Rol, Usuario } from '@/types/seguridad';
import styles from '../Usuarios.module.css';

const USERNAME_VALIDO = /^[a-z0-9._-]{3,40}$/;

interface AltaProps {
  modo: 'alta';
  onSubmit: (nuevo: NuevoUsuario) => Promise<void>;
  onCancel: () => void;
}

interface EdicionProps {
  modo: 'edicion';
  usuario: Usuario;
  onSubmit: (edicion: EdicionUsuario) => Promise<void>;
  onCancel: () => void;
}

export function UsuarioForm(props: AltaProps | EdicionProps) {
  const original = props.modo === 'edicion' ? props.usuario : null;
  const [username, setUsername] = useState(original?.username ?? '');
  const [nombre, setNombre] = useState(original?.nombre ?? '');
  const [rol, setRol] = useState<Rol>(original?.rol ?? 'OPERARIO');
  const [clave, setClave] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const cambiaRol = original !== null && rol !== original.rol;

  const validar = (): string | null => {
    if (props.modo === 'alta') {
      const u = username.trim().toLowerCase();
      if (!USERNAME_VALIDO.test(u)) {
        return 'El nombre de usuario debe tener entre 3 y 40 caracteres: letras minúsculas, números, punto, guion o guion bajo.';
      }
      const errClave = errorClave(clave, u);
      if (errClave) return errClave;
    }
    if (!nombre.trim()) return 'El nombre a mostrar es obligatorio.';
    return null;
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const invalido = validar();
    if (invalido) {
      setError(invalido);
      return;
    }
    setError(null);
    setEnviando(true);
    try {
      if (props.modo === 'alta') {
        await props.onSubmit({ username: username.trim().toLowerCase(), nombre: nombre.trim(), rol, clave });
      } else {
        await props.onSubmit({ nombre: nombre.trim(), rol });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Card className={styles.section}>
      <div className={styles.sectionHead}>
        <h2 className={styles.sectionTitle}>
          {props.modo === 'alta' ? 'Nuevo usuario' : `Editar «${original?.username}»`}
        </h2>
        <span className={styles.sectionHint}>
          {props.modo === 'alta'
            ? 'Queda activo y tiene que cambiar la contraseña temporal en su primer ingreso'
            : 'El nombre de usuario no se puede cambiar'}
        </span>
      </div>

      <form onSubmit={onSubmit}>
        <div className={styles.formGrid}>
          <label className={styles.group}>
            <span className={styles.label}>Nombre de usuario</span>
            <input
              className={styles.input}
              value={username}
              disabled={props.modo === 'edicion'}
              autoCapitalize="none"
              spellCheck={false}
              placeholder="jperez"
              onChange={(e) => setUsername(e.target.value)}
            />
          </label>
          <label className={styles.group}>
            <span className={styles.label}>Nombre a mostrar</span>
            <input
              className={styles.input}
              value={nombre}
              placeholder="Juan Pérez"
              onChange={(e) => setNombre(e.target.value)}
            />
          </label>
          <label className={styles.group}>
            <span className={styles.label}>Rol</span>
            <select className={styles.select} value={rol} onChange={(e) => setRol(e.target.value as Rol)}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {nombreRol(r)}
                </option>
              ))}
            </select>
          </label>
          {props.modo === 'alta' && (
            <label className={styles.group}>
              <span className={styles.label}>Contraseña temporal</span>
              <input
                className={styles.input}
                type="password"
                autoComplete="new-password"
                value={clave}
                onChange={(e) => setClave(e.target.value)}
              />
            </label>
          )}
        </div>

        {rol === 'SERVICIO' && (
          <div role="note" className={styles.advertencia}>
            El rol Servicio es para integraciones (el simulador, el servicio de inferencia), no para personas: sus
            permisos son los que esas herramientas necesitan para hablar con la API.
          </div>
        )}
        {cambiaRol && (
          <div role="note" className={styles.info}>
            Al cambiar el rol se cierran las sesiones abiertas de «{original?.username}»: vuelve a entrar con los
            permisos de {nombreRol(rol)}.
          </div>
        )}
        {error && (
          <div role="alert" className={styles.errorBox}>
            {error}
          </div>
        )}

        <div className={styles.formActions}>
          <button type="submit" className={styles.btnPrimary} disabled={enviando}>
            {enviando ? 'Guardando…' : props.modo === 'alta' ? 'Dar de alta' : 'Guardar cambios'}
          </button>
          <button type="button" className={styles.btnSecondary} onClick={props.onCancel}>
            Cancelar
          </button>
        </div>
      </form>
    </Card>
  );
}

interface BlanqueoProps {
  usuario: Usuario;
  onSubmit: (clave: string) => Promise<void>;
  onCancel: () => void;
}

/** Blanqueo: una clave temporal nueva. Cierra sus sesiones y lo obliga a cambiarla. */
export function BlanqueoForm({ usuario, onSubmit, onCancel }: BlanqueoProps) {
  const [clave, setClave] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    const invalida = errorClave(clave, usuario.username);
    if (invalida) {
      setError(invalida);
      return;
    }
    setError(null);
    setEnviando(true);
    try {
      await onSubmit(clave);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Card className={styles.section}>
      <div className={styles.sectionHead}>
        <h2 className={styles.sectionTitle}>Blanquear la contraseña de «{usuario.username}»</h2>
        <span className={styles.sectionHint}>
          Se cierran sus sesiones y tiene que cambiarla al ingresar. La contraseña no queda registrada.
        </span>
      </div>
      <form onSubmit={enviar}>
        <div className={styles.formGrid}>
          <label className={styles.group}>
            <span className={styles.label}>Contraseña temporal</span>
            <input
              className={styles.input}
              type="password"
              autoComplete="new-password"
              value={clave}
              onChange={(e) => setClave(e.target.value)}
            />
          </label>
        </div>
        {error && (
          <div role="alert" className={styles.errorBox}>
            {error}
          </div>
        )}
        <div className={styles.formActions}>
          <button type="submit" className={styles.btnPrimary} disabled={enviando}>
            {enviando ? 'Guardando…' : 'Asignar contraseña temporal'}
          </button>
          <button type="button" className={styles.btnSecondary} onClick={onCancel}>
            Cancelar
          </button>
        </div>
      </form>
    </Card>
  );
}
