/* ============================================================
   Login (HU-01 CA-01/CA-02). Fuera del shell: sin sesión no hay sidebar ni datos del vivero.

   - Error genérico: "Credenciales incorrectas", sin marcar ningún campo y limpiando la clave,
     para no adelantar si falló el usuario, la clave o si la cuenta está suspendida.
   - Si la sesión se cerró sola, un aviso dice por qué (inactividad o cambio de la cuenta).
   - Al entrar vuelve a la ruta que se había pedido, si el rol todavía puede verla.
   - Los usuarios demo se listan sólo en modo mock: el repositorio http no tiene ninguno.
   ============================================================ */
import { useMemo, useState, type FormEvent } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { CredencialesIncorrectasError, getSeguridadRepository, SesionNoEstablecidaError } from '@/data';
import { useAuth, type MotivoAviso } from '@/hooks/AuthContext';
import { destinoTrasLogin, rutaPedida } from '@/lib/vistas';
import { PantallaAuth, PantallaEspera } from './PantallaAuth';
import styles from './Auth.module.css';

const AVISO_CIERRE: Record<MotivoAviso, string> = {
  SESION_EXPIRADA: 'Tu sesión se cerró por inactividad',
  SESION_REVOCADA: 'Tu cuenta cambió; volvé a iniciar sesión',
};

export function LoginPage() {
  const { estado, perfil, puede, login, motivoCierre } = useAuth();
  const location = useLocation();
  const desde = rutaPedida(location.state);
  const demo = useMemo(() => getSeguridadRepository().usuariosDemo(), []);

  const [username, setUsername] = useState('');
  const [clave, setClave] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  if (estado === 'cargando') return <PantallaEspera />;

  // Con sesión (recién iniciada o ya vigente) no hay nada que hacer acá.
  if (estado === 'autenticado' && perfil) {
    if (perfil.debeCambiarClave) return <Navigate to="/cambiar-clave" replace state={{ desde }} />;
    return <Navigate to={destinoTrasLogin(desde, puede) ?? '/'} replace />;
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      await login(username, clave);
    } catch (err) {
      setError(
        err instanceof CredencialesIncorrectasError
          ? 'Credenciales incorrectas'
          : err instanceof SesionNoEstablecidaError
            ? err.message
            : 'No se pudo contactar al backend. Probá de nuevo en unos segundos.',
      );
      setClave('');
    } finally {
      setEnviando(false);
    }
  };

  return (
    <PantallaAuth>
      <h1 className={styles.titulo}>Iniciar sesión</h1>
      <p className={styles.subtitulo}>Ingresá con tu usuario del vivero.</p>

      {motivoCierre && !error && (
        <div role="status" className={styles.aviso}>
          {AVISO_CIERRE[motivoCierre]}
        </div>
      )}
      {error && (
        <div role="alert" className={styles.error}>
          {error}
        </div>
      )}

      <form className={styles.form} onSubmit={onSubmit}>
        <label className={styles.campo}>
          <span className={styles.label}>Usuario</span>
          <input
            className={styles.input}
            name="username"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </label>
        <label className={styles.campo}>
          <span className={styles.label}>Contraseña</span>
          <input
            className={styles.input}
            name="clave"
            type="password"
            autoComplete="current-password"
            value={clave}
            onChange={(e) => setClave(e.target.value)}
            required
          />
        </label>
        <button type="submit" className={styles.btnPrimario} disabled={enviando || !username || !clave}>
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>

      {demo.length > 0 && (
        <div className={styles.demo}>
          <div className={styles.demoTitulo}>Usuarios de la demo</div>
          <div className={styles.demoLista}>
            {demo.map((u) => (
              <button
                key={u.username}
                type="button"
                className={styles.demoUsuario}
                onClick={() => {
                  setUsername(u.username);
                  setClave(u.clave);
                  setError(null);
                }}
              >
                <span className={styles.demoNombre}>{u.nombre}</span>
                <span className={styles.demoRol}>
                  {u.rolNombre} · <code>{u.username}</code>
                </span>
              </button>
            ))}
          </div>
          <div className={styles.demoNota}>
            Contraseña de todos: <code>{demo[0].clave}</code>. Elegí uno para ver el sistema con los permisos de su rol.
          </div>
        </div>
      )}
    </PantallaAuth>
  );
}
