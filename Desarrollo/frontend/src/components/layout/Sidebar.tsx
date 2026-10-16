import { useEffect, useId, useState } from 'react';
import { Link, NavLink, matchPath, useLocation } from 'react-router-dom';
import { useAuth } from '@/hooks/AuthContext';
import { Icon, type IconName } from '@/components/ui/Icon';
import { puedeVer, VISTAS, type Vista } from '@/lib/vistas';
import styles from './Sidebar.module.css';

interface NavItem {
  to: string;
  /** Permisos de la vista: la misma tabla que usa el router. */
  vista: Vista;
  icon: IconName;
  label: string;
  /** ruta exacta (para '/') */
  end?: boolean;
}

/* El detalle de macro-zona (/mapa?zona=) NO está acá a propósito: se entra eligiendo una
   macro-zona en el Panel general, y desde adentro sólo se vuelve. Sin ese contexto previo
   la vista no tiene de dónde sacar qué zona mostrar. */
const PRINCIPAL: NavItem[] = [
  { to: '/', vista: VISTAS.panel, icon: 'dashboard', label: 'Panel general', end: true },
  { to: '/diagnosticos', vista: VISTAS.diagnosticos, icon: 'diagnostics', label: 'Diagnósticos de IA' },
];

const GESTION: NavItem[] = [{ to: '/historial', vista: VISTAS.historial, icon: 'history', label: 'Historial' }];

/* Grupo desplegable "Configuración": todo lo que ajusta cómo opera el sistema. Umbrales y
   Preferencias editan partes de la misma configuración agronómica (Umbrales sigue en
   /configuracion); el criterio de diagnóstico es otro recurso, con su propia ruta. Los planes de
   rustificación no están acá: son de cada macro-zona. */
const CONFIGURACION: NavItem[] = [
  { to: '/configuracion', vista: VISTAS.configuracion, icon: 'gauge', label: 'Umbrales' },
  {
    to: '/criterio-diagnostico',
    vista: VISTAS.criterioDiagnostico,
    icon: 'detections',
    label: 'Criterio de diagnóstico',
  },
  { to: '/reglas', vista: VISTAS.reglas, icon: 'rules', label: 'Motor de reglas' },
  { to: '/hardware', vista: VISTAS.hardware, icon: 'hardware', label: 'Hardware' },
  { to: '/topologia', vista: VISTAS.topologia, icon: 'cube', label: 'Topología' },
  { to: '/usuarios', vista: VISTAS.usuarios, icon: 'users', label: 'Usuarios' },
  { to: '/preferencias', vista: VISTAS.preferencias, icon: 'toggle', label: 'Preferencias' },
];

function NavItemLink({ item, sub }: { item: NavItem; sub?: boolean }) {
  const base = sub ? `${styles.item} ${styles.subItem}` : styles.item;
  return (
    <NavLink
      to={item.to}
      end={item.end}
      className={({ isActive }) => (isActive ? `${base} ${styles.itemActive}` : base)}
    >
      <Icon name={item.icon} size={sub ? 16 : 18} />
      <span>{item.label}</span>
    </NavLink>
  );
}

/**
 * Grupo que se despliega y colapsa con un clic en su encabezado (no navega). Se abre solo al
 * entrar a una de sus vistas, para que el ítem activo quede siempre a la vista.
 */
function NavGroup({ icon, label, items }: { icon: IconName; label: string; items: NavItem[] }) {
  const { pathname } = useLocation();
  const contieneActiva = items.some((i) => matchPath({ path: i.to, end: false }, pathname));
  const [abierto, setAbierto] = useState(contieneActiva);
  const idLista = useId();

  useEffect(() => {
    if (contieneActiva) setAbierto(true);
  }, [contieneActiva, pathname]);

  const clase = [styles.item, styles.groupHead, !abierto && contieneActiva && styles.groupHeadActive]
    .filter(Boolean)
    .join(' ');

  return (
    <div>
      <button
        type="button"
        className={clase}
        aria-expanded={abierto}
        aria-controls={idLista}
        onClick={() => setAbierto((a) => !a)}
      >
        <Icon name={icon} size={18} />
        <span>{label}</span>
        <span className={abierto ? `${styles.chevron} ${styles.chevronOpen}` : styles.chevron}>
          <Icon name="chevron-down" size={16} />
        </span>
      </button>
      {abierto && (
        <div id={idLista} className={styles.subNav} role="group" aria-label={label}>
          {items.map((item) => (
            <NavItemLink key={item.to} item={item} sub />
          ))}
        </div>
      )}
    </div>
  );
}

export function Sidebar() {
  const { puede } = useAuth();
  // Cada ítem sólo si el rol puede ver la vista; un grupo sin ítems no se muestra.
  const habilitados = (items: NavItem[]) => items.filter((i) => puedeVer(i.vista, puede));
  const principal = habilitados(PRINCIPAL);
  const gestion = habilitados(GESTION);
  const configuracion = habilitados(CONFIGURACION);
  const hayGestion = gestion.length > 0 || configuracion.length > 0;

  return (
    <aside className={styles.sidebar}>
      <Link to="/" className={styles.brand} aria-label="Ir al panel general">
        <img className={styles.logo} src="/yerbanalytics_simbolo.svg" alt="" />
        <div>
          <div className={styles.brandName}>Yerbanalytics</div>
          <div className={styles.brandSub}>Datos bajados a tierra</div>
        </div>
      </Link>

      {principal.length > 0 && (
        <>
          <div className={styles.section}>Principal</div>
          <nav className={styles.nav} aria-label="Principal">
            {principal.map((item) => (
              <NavItemLink key={item.to} item={item} />
            ))}
          </nav>
        </>
      )}

      {hayGestion && (
        <>
          <div className={principal.length > 0 ? `${styles.section} ${styles.sectionTop}` : styles.section}>
            Gestión
          </div>
          <nav className={styles.nav} aria-label="Gestión">
            {gestion.map((item) => (
              <NavItemLink key={item.to} item={item} />
            ))}
            {configuracion.length > 0 && (
              <NavGroup icon="config" label="Configuración" items={configuracion} />
            )}
          </nav>
        </>
      )}
    </aside>
  );
}
