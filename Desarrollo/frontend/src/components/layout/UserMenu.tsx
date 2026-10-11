/* Usuario de la sesión en la topbar: nombre, rol e iniciales, con su menú (HU-01). */
import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/AuthContext';
import { Icon } from '@/components/ui/Icon';
import { iniciales } from '@/lib/catalogoSeguridad';
import styles from './Topbar.module.css';

export function UserMenu() {
  const { perfil, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  // cerrar al hacer clic fuera
  useEffect(() => {
    if (!open) return;
    function onClick(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  if (!perfil) return null;

  return (
    <div className={styles.userWrap} ref={wrapRef}>
      <button
        type="button"
        className={styles.user}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Menú de ${perfil.nombre}`}
        onClick={() => setOpen((o) => !o)}
      >
        <div style={{ textAlign: 'right', lineHeight: 1.15 }}>
          <div className={styles.userName}>{perfil.nombre}</div>
          <div className={styles.userRole}>{perfil.rolNombre}</div>
        </div>
        <div className={styles.avatar} aria-hidden="true">
          {iniciales(perfil.nombre)}
        </div>
      </button>

      {open && (
        <div className={styles.menu} role="menu">
          <div className={styles.menuHead}>
            <span className={styles.menuUser}>{perfil.username}</span>
          </div>
          <button
            type="button"
            role="menuitem"
            className={styles.menuItem}
            onClick={() => {
              setOpen(false);
              navigate('/cambiar-clave', { state: { desde: `${location.pathname}${location.search}` } });
            }}
          >
            <Icon name="key" size={16} />
            Cambiar contraseña
          </button>
          <button
            type="button"
            role="menuitem"
            className={styles.menuItem}
            onClick={() => {
              setOpen(false);
              void logout();
            }}
          >
            <Icon name="logout" size={16} />
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  );
}
