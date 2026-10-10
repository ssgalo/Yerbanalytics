/* ============================================================
   Guardas de ruta.
   - `RequireAuth`: sin sesión, al login (recordando la ruta pedida); con clave temporal, al
     cambio de contraseña; con un rol sin ninguna vista, la pantalla que lo dice.
   - `RequirePermiso`: la vista sin su permiso muestra el aviso y NO monta la página, así que
     no se le piden datos al backend.
   - `Inicio`: el Panel general, o la primera vista habilitada si el rol no ve el vivero.

   Son presentación: la autoridad sigue siendo el backend, que igual responde 401/403.
   ============================================================ */
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { Icon } from '@/components/ui/Icon';
import { useAuth } from '@/hooks/AuthContext';
import { primeraVistaHabilitada, puedeVer, VISTAS, type Vista } from '@/lib/vistas';
import { PantallaAuth, PantallaEspera } from './PantallaAuth';
import styles from './Auth.module.css';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { estado, perfil, puede } = useAuth();
  const location = useLocation();
  const desde = `${location.pathname}${location.search}`;

  if (estado === 'cargando') return <PantallaEspera />;
  if (estado !== 'autenticado' || !perfil) return <Navigate to="/login" replace state={{ desde }} />;
  if (perfil.debeCambiarClave) return <Navigate to="/cambiar-clave" replace state={{ desde }} />;
  if (!primeraVistaHabilitada(puede)) return <SinSecciones />;
  return <>{children}</>;
}

export function RequirePermiso({ vista, children }: { vista: Vista; children: ReactNode }) {
  const { puede } = useAuth();
  if (!puedeVer(vista, puede)) return <SinPermiso />;
  return <>{children}</>;
}

/** Índice: el Panel general si se puede; si no, la primera vista que el rol tenga. */
export function Inicio({ children }: { children: ReactNode }) {
  const { puede } = useAuth();
  if (puedeVer(VISTAS.panel, puede)) return <>{children}</>;
  const primera = primeraVistaHabilitada(puede);
  return primera ? <Navigate to={primera.ruta} replace /> : <SinPermiso />;
}

export function SinPermiso() {
  return (
    <Card className={styles.sinPermiso}>
      <div className={styles.sinPermisoIcono}>
        <Icon name="lock" size={22} />
      </div>
      <h2 className={styles.sinPermisoTitulo}>No tenés permiso para ver esta sección</h2>
      <p className={styles.sinPermisoTexto}>Si la necesitás para tu trabajo, pedíselo a un Administrador.</p>
    </Card>
  );
}

function SinSecciones() {
  const { perfil, logout } = useAuth();
  return (
    <PantallaAuth>
      <h1 className={styles.titulo}>Tu rol no tiene secciones habilitadas</h1>
      <p className={styles.subtitulo}>
        {perfil ? `${perfil.nombre} (${perfil.rolNombre}): ` : ''}
        tu rol no tiene permiso para ver ninguna sección del dashboard. Si es un error, pedíle a un
        Administrador que revise la matriz de permisos.
      </p>
      <button type="button" className={styles.btnPrimario} onClick={() => void logout()}>
        Cerrar sesión
      </button>
    </PantallaAuth>
  );
}
