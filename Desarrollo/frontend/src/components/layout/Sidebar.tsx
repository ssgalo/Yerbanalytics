import { NavLink } from 'react-router-dom';
import { useNurseryDataOpcional } from '@/hooks/NurseryContext';
import { useDemoExpo } from '@/hooks/DemoExpoContext';
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
  /** muestra el contador de diagnósticos */
  showCount?: boolean;
}

/* El detalle de macro-zona (/mapa?zona=) NO está acá a propósito: se entra eligiendo una
   macro-zona en el Panel general, y desde adentro sólo se vuelve. Sin ese contexto previo
   la vista no tiene de dónde sacar qué zona mostrar. */
const PRINCIPAL: NavItem[] = [
  { to: '/', vista: VISTAS.panel, icon: 'dashboard', label: 'Panel general', end: true },
  { to: '/diagnosticos', vista: VISTAS.diagnosticos, icon: 'diagnostics', label: 'Diagnósticos de IA', showCount: true },
];

/* Sólo se agrega si el interruptor de Configuración la enciende; va pegada a Diagnósticos de IA. */
const DEMO_EXPO: NavItem = { to: '/demo-expo', vista: VISTAS.demoExpo, icon: 'camera', label: 'Demo Expo' };

const GESTION: NavItem[] = [
  { to: '/historial', vista: VISTAS.historial, icon: 'history', label: 'Historial' },
  { to: '/configuracion', vista: VISTAS.configuracion, icon: 'config', label: 'Configuración' },
  { to: '/reglas', vista: VISTAS.reglas, icon: 'rules', label: 'Motor de reglas' },
  { to: '/hardware', vista: VISTAS.hardware, icon: 'hardware', label: 'Hardware' },
  { to: '/topologia', vista: VISTAS.topologia, icon: 'cube', label: 'Topología' },
  { to: '/usuarios', vista: VISTAS.usuarios, icon: 'users', label: 'Usuarios' },
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
  const vivero = useNurseryDataOpcional();
  const diagCount = vivero?.stats.diagCount ?? 0;
  const { visible: demoExpoVisible } = useDemoExpo();
  const { puede } = useAuth();
  // Cada ítem sólo si el rol puede ver la vista; un grupo sin ítems no se muestra.
  const habilitados = (items: NavItem[]) => items.filter((i) => puedeVer(i.vista, puede));
  const principal = habilitados(demoExpoVisible ? [...PRINCIPAL, DEMO_EXPO] : PRINCIPAL);
  const gestion = habilitados(GESTION);

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

      {principal.length > 0 && (
        <>
          <div className={styles.section}>Principal</div>
          <nav className={styles.nav} aria-label="Principal">
            {principal.map((item) => (
              <NavItemLink key={item.to} item={item} count={diagCount} />
            ))}
          </nav>
        </>
      )}

      {gestion.length > 0 && (
        <>
          <div className={principal.length > 0 ? `${styles.section} ${styles.sectionTop}` : styles.section}>
            Gestión
          </div>
          <nav className={styles.nav} aria-label="Gestión">
            {gestion.map((item) => (
              <NavItemLink key={item.to} item={item} count={diagCount} />
            ))}
          </nav>
        </>
      )}

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
