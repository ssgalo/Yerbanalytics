/* ============================================================
   Alta y edición de un usuario (HU-20 CA-01). En la edición el nombre de usuario no se toca.
   Valida lo mismo que el backend antes de enviar; lo que el backend rechace igual (409 por
   username repetido, último Administrador…) se muestra con su mensaje.
   ============================================================ */
import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Field, Input, Select } from '@/components/ui/Field';
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

/** Título y aclaración del formulario; el formulario se muestra dentro de un `Popover`. */
function FormSection({ title, hint, children }: { title: string; hint: string; children: ReactNode }) {
  return (
    <>
      <header className={styles.sectionHead}>
        <h2 className={styles.sectionTitle}>{title}</h2>
        <p className={styles.sectionHint}>{hint}</p>
      </header>
      {children}
    </>
  );
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

  const id = useId();

  return (
    <FormSection
      title={props.modo === 'alta' ? 'Nuevo usuario' : `Editar «${original?.username}»`}
      hint={
        props.modo === 'alta'
          ? 'Queda activo y tiene que cambiar la contraseña temporal en su primer ingreso'
          : 'El nombre de usuario no se puede cambiar'
      }
    >
      <form onSubmit={onSubmit} noValidate>
        <div className={styles.formGrid}>
          <Field label="Nombre de usuario" htmlFor={`${id}-username`}>
            <Input
              id={`${id}-username`}
              value={username}
              disabled={props.modo === 'edicion'}
              autoCapitalize="none"
              spellCheck={false}
              placeholder="jperez"
              onChange={(e) => setUsername(e.target.value)}
            />
          </Field>
          <Field label="Nombre a mostrar" htmlFor={`${id}-nombre`}>
            <Input id={`${id}-nombre`} value={nombre} placeholder="Juan Pérez" onChange={(e) => setNombre(e.target.value)} />
          </Field>
          <Field label="Rol" htmlFor={`${id}-rol`}>
            <Select id={`${id}-rol`} value={rol} modified={cambiaRol} onChange={(e) => setRol(e.target.value as Rol)}>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {nombreRol(r)}
                </option>
              ))}
            </Select>
          </Field>
          {props.modo === 'alta' && (
            <Field label="Contraseña temporal" htmlFor={`${id}-clave`}>
              <Input
                id={`${id}-clave`}
                type="password"
                autoComplete="new-password"
                value={clave}
                onChange={(e) => setClave(e.target.value)}
              />
            </Field>
          )}
        </div>

        <div className={styles.formNotes}>
          {rol === 'SERVICIO' && (
            <Callout tone="warn" role="note">
              El rol Servicio es para integraciones (el simulador, el servicio de inferencia), no para personas: sus
              permisos son los que esas herramientas necesitan para hablar con la API.
            </Callout>
          )}
          {cambiaRol && (
            <Callout tone="info" role="note">
              Al cambiar el rol se cierran las sesiones abiertas de «{original?.username}»: vuelve a entrar con los
              permisos de {nombreRol(rol)}.
            </Callout>
          )}
          {error && (
            <Callout tone="crit" role="alert">
              {error}
            </Callout>
          )}
        </div>

        <div className={styles.formActions}>
          <Button type="submit" variant="primary" disabled={enviando}>
            {enviando ? 'Guardando…' : props.modo === 'alta' ? 'Dar de alta' : 'Guardar cambios'}
          </Button>
          <Button variant="ghost" onClick={props.onCancel}>
            Cancelar
          </Button>
        </div>
      </form>
    </FormSection>
  );
}

interface BlanqueoProps {
  usuario: Usuario;
  onSubmit: (clave: string) => Promise<void>;
  onCancel: () => void;
}

/** Restablecimiento: una clave temporal nueva. Cierra sus sesiones y lo obliga a cambiarla. */
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

  const id = useId();

  return (
    <FormSection
      title={`Restablecer la contraseña de «${usuario.username}»`}
      hint="Se cierran sus sesiones y tiene que cambiarla al ingresar. La contraseña no queda registrada."
    >
      <form onSubmit={enviar} noValidate>
        <div className={styles.formGrid}>
          <Field label="Contraseña temporal" htmlFor={`${id}-clave`} error={error}>
            <Input
              id={`${id}-clave`}
              type="password"
              autoComplete="new-password"
              value={clave}
              invalid={error !== null}
              onChange={(e) => setClave(e.target.value)}
            />
          </Field>
        </div>
        <div className={styles.formActions}>
          <Button type="submit" variant="primary" disabled={enviando}>
            {enviando ? 'Guardando…' : 'Asignar contraseña temporal'}
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            Cancelar
          </Button>
        </div>
      </form>
    </FormSection>
  );
}
