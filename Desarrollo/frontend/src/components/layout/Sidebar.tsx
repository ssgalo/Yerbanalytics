import { NavLink } from 'react-router-dom';
import { useNurseryData } from '@/hooks/NurseryContext';
import { Icon, type IconName } from '@/components/ui/Icon';
import styles from './Sidebar.module.css';

interface NavItem {
  to: string;
  icon: IconName;
  label: string;
  /** ruta exacta (para '/') */
  end?: boolean;
  /** muestra el contador de diagnósticos */
  showCount?: boolean;
}

const PRINCIPAL: NavItem[] = [
  { to: '/', icon: 'dashboard', label: 'Panel general', end: true },
  { to: '/mapa', icon: 'map', label: 'Mapa de producción' },
  { to: '/diagnosticos', icon: 'diagnostics', label: 'Diagnósticos de IA', showCount: true },
];

const GESTION: NavItem[] = [
  { to: '/historial', icon: 'history', label: 'Historial' },
  { to: '/configuracion', icon: 'config', label: 'Configuración' },
  { to: '/hardware', icon: 'hardware', label: 'Hardware' },
];

function NavItemLink({ item, count }: { item: NavItem; count: number }) {
  return (
    <NavLink
      to={item.to}
      end={item.end}
      className={({ isActive }) => (isActive ? `${styles.item} ${styles.itemActive}` : styles.item)}
    >
      <Icon name={item.icon} size={18} />
      <span>{item.label}</span>
      {item.showCount && <span className={styles.count}>{count}</span>}
    </NavLink>
  );
}

export function Sidebar() {
  const { stats } = useNurseryData();

  return (
    <aside className={styles.sidebar}>
      <div className={styles.brand}>
        <div className={styles.logo}>
          <Icon name="leaf" size={22} stroke="#EBFBF1" strokeWidth={1.9} />
        </div>
        <div style={{ lineHeight: 1.05 }}>
          <div className={styles.brandName}>Yerbanalytics</div>
          <div className={styles.brandSub}>Monitoreo IA</div>
        </div>
      </div>

      <div className={styles.section}>Principal</div>
      <nav className={styles.nav}>
        {PRINCIPAL.map((item) => (
          <NavItemLink key={item.to} item={item} count={stats.diagCount} />
        ))}
      </nav>

      <div className={`${styles.section} ${styles.sectionTop}`}>Gestión</div>
      <nav className={styles.nav}>
        {GESTION.map((item) => (
          <NavItemLink key={item.to} item={item} count={stats.diagCount} />
        ))}
      </nav>

      <div className={styles.status}>
        <div className={styles.statusRow}>
          <span className={styles.statusDot} />
          Sistema en línea
        </div>
        <div className={styles.statusSub}>
          Edge activo · sincronizado
          <br />
          hace 40 segundos
        </div>
      </div>
    </aside>
  );
}
