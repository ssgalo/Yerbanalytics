/* ============================================================
   Cambio de contraseña propio. Dos entradas:
   - Obligatoria: la clave es temporal (alta o blanqueo). El backend rechaza todo lo demás con
     `CAMBIO_CLAVE_REQUERIDO` hasta que se cambie, así que acá no hay "volver": sólo cambiarla o
     cerrar sesión.
   - Voluntaria: desde el menú de usuario de la topbar.
   Cambiarla cierra las demás sesiones del usuario; la actual sigue vigente.
   ============================================================ */
import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/AuthContext';
import { errorClave } from '@/lib/claves';
import { destinoTrasLogin, rutaPedida } from '@/lib/vistas';
import { PantallaAuth, PantallaEspera } from './PantallaAuth';
import styles from './Auth.module.css';

export function CambiarClavePage() {
  const { estado, perfil, puede, cambiarClave, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const desde = rutaPedida(location.state);

  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [repetida, setRepetida] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  if (estado === 'cargando') return <PantallaEspera />;
  if (estado !== 'autenticado' || !perfil) return <Navigate to="/login" replace />;

  const obligatoria = perfil.debeCambiarClave;

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const invalida =
      errorClave(nueva, perfil.username) ?? (nueva !== repetida ? 'Las contraseñas nuevas no coinciden.' : null);
    if (invalida) {
      setError(invalida);
      return;
    }
    setError(null);
    setEnviando(true);
    try {
      await cambiarClave(actual, nueva);
      navigate(destinoTrasLogin(desde, puede) ?? '/', { replace: true });
    } catch (err) {
      // 400 del backend: clave actual errónea o nueva inválida, con su mensaje.
      setError(err instanceof Error ? err.message : 'No se pudo cambiar la contraseña.');
      setActual('');
    } finally {
      setEnviando(false);
    }
  };

  return (
    <PantallaAuth>
      <h1 className={styles.titulo}>Cambiar contraseña</h1>
      <p className={styles.subtitulo}>
        {obligatoria
          ? `Hola, ${perfil.nombre}. Tu contraseña es temporal: elegí una nueva para empezar a usar el sistema.`
          : 'Al cambiarla se cierran tus otras sesiones abiertas; ésta sigue activa.'}
      </p>

      {error && (
        <div role="alert" className={styles.error}>
          {error}
        </div>
      )}

      <form className={styles.form} onSubmit={onSubmit}>
        <label className={styles.campo}>
          <span className={styles.label}>{obligatoria ? 'Contraseña temporal' : 'Contraseña actual'}</span>
          <input
            className={styles.input}
            type="password"
            autoComplete="current-password"
            value={actual}
            onChange={(e) => setActual(e.target.value)}
            required
          />
        </label>
        <div className={styles.campo}>
          <label className={styles.campo}>
            <span className={styles.label}>Contraseña nueva</span>
            <input
              className={styles.input}
              type="password"
              autoComplete="new-password"
              aria-describedby="ayuda-clave-nueva"
              value={nueva}
              onChange={(e) => setNueva(e.target.value)}
              required
            />
          </label>
          <span id="ayuda-clave-nueva" className={styles.ayuda}>
            Al menos 8 caracteres y distinta de tu nombre de usuario.
          </span>
        </div>
        <label className={styles.campo}>
          <span className={styles.label}>Repetí la contraseña nueva</span>
          <input
            className={styles.input}
            type="password"
            autoComplete="new-password"
            value={repetida}
            onChange={(e) => setRepetida(e.target.value)}
            required
          />
        </label>
        <button type="submit" className={styles.btnPrimario} disabled={enviando || !actual || !nueva || !repetida}>
          {enviando ? 'Guardando…' : 'Cambiar contraseña'}
        </button>
        <div className={styles.acciones}>
          {obligatoria ? (
            <button type="button" className={styles.btnSecundario} onClick={() => void logout()}>
              Cerrar sesión
            </button>
          ) : (
            <button type="button" className={styles.btnSecundario} onClick={() => navigate(desde ?? '/')}>
              Volver
            </button>
          )}
        </div>
      </form>
    </PantallaAuth>
  );
}
