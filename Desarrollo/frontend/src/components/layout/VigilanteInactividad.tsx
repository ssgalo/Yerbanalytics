/* ============================================================
   Monta el control de inactividad del dashboard y muestra el aviso previo al cierre.
   El cierre en sí lo decide el backend; esto sólo lo adelanta en pantalla (design D4).
   ============================================================ */
import { useCallback } from 'react';
import { getSeguridadRepository } from '@/data';
import { useAuth } from '@/hooks/AuthContext';
import { useInactividad } from '@/hooks/useInactividad';
import { INACTIVIDAD_POR_DEFECTO } from '@/lib/catalogoSeguridad';
import styles from './VigilanteInactividad.module.css';

export function VigilanteInactividad() {
  const { perfil, cerrarPorInactividad } = useAuth();
  const registrarActividad = useCallback(() => getSeguridadRepository().registrarActividad(), []);
  const { aviso, segundosRestantes, mantenerViva } = useInactividad({
    inactividadMin: perfil?.inactividadMin ?? INACTIVIDAD_POR_DEFECTO,
    registrarActividad,
    alVencer: cerrarPorInactividad,
  });

  if (!aviso) return null;

  return (
    <div role="alertdialog" aria-live="assertive" aria-label="Aviso de inactividad" className={styles.aviso}>
      <div className={styles.texto}>
        <span className={styles.titulo}>Tu sesión está por cerrarse</span>
        <span className={styles.detalle}>
          Por inactividad, se cierra en {segundosRestantes} {segundosRestantes === 1 ? 'segundo' : 'segundos'}.
        </span>
      </div>
      <button type="button" className={styles.boton} onClick={mantenerViva}>
        Seguir conectado
      </button>
    </div>
  );
}
