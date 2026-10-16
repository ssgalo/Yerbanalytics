import { useEffect, useId, useState } from 'react';
import { Link, NavLink, matchPath, useLocation } from 'react-router-dom';
import { useAuth } from '@/hooks/AuthContext';
import { useSidebar } from '@/hooks/SidebarContext';
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
  {
    to: '/diagnosticos',
    vista: VISTAS.diagnosticos,
    icon: 'diagnostics',
    label: 'Diagnósticos de IA',
  },
];

const GESTION: NavItem[] = [
  { to: '/historial', vista: VISTAS.historial, icon: 'history', label: 'Historial' },
];

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

/** Muestra el nombre del ítem al costado mientras la sidebar está colapsada a íconos. */
type ShowHint = (label: string, el: HTMLElement) => void;

interface HintHandlers {
  onHint: ShowHint;
  onHintEnd: () => void;
}

const hintProps = (label: string, { onHint, onHintEnd }: HintHandlers) => ({
  onMouseEnter: (e: React.MouseEvent<HTMLElement>) => onHint(label, e.currentTarget),
  onFocus: (e: React.FocusEvent<HTMLElement>) => onHint(label, e.currentTarget),
  onMouseLeave: onHintEnd,
  onBlur: onHintEnd,
});

function NavItemLink({ item, sub, hint }: { item: NavItem; sub?: boolean; hint: HintHandlers }) {
  const base = sub ? `${styles.item} ${styles.subItem}` : styles.item;
  return (
    <NavLink
      to={item.to}
      end={item.end}
      className={({ isActive }) => (isActive ? `${base} ${styles.itemActive}` : base)}
      {...(sub ? {} : hintProps(item.label, hint))}
    >
      <Icon name={item.icon} size={sub ? 16 : 18} />
      <span className={styles.label}>{item.label}</span>
    </NavLink>
  );
}

/**
 * Grupo que se despliega y colapsa con un clic en su encabezado (no navega). Se abre solo al
 * entrar a una de sus vistas, para que el ítem activo quede siempre a la vista. Con la sidebar
 * colapsada sus vistas no entran: el clic la expande y abre el grupo.
 */
function NavGroup({
  icon,
  label,
  items,
  hint,
}: {
  icon: IconName;
  label: string;
  items: NavItem[];
  hint: HintHandlers;
}) {
  const { pathname } = useLocation();
  const { collapsed, setCollapsed } = useSidebar();
  const contieneActiva = items.some((i) => matchPath({ path: i.to, end: false }, pathname));
  const [abierto, setAbierto] = useState(contieneActiva);
  const idLista = useId();

  useEffect(() => {
    if (contieneActiva) setAbierto(true);
  }, [contieneActiva, pathname]);

  const expandido = abierto && !collapsed;
  const clase = [
    styles.item,
    styles.groupHead,
    contieneActiva && collapsed && styles.itemActive,
    contieneActiva && !abierto && !collapsed && styles.groupHeadActive,
  ]
    .filter(Boolean)
    .join(' ');

  const onClick = () => {
    if (collapsed) {
      setCollapsed(false);
      setAbierto(true);
      hint.onHintEnd();
    } else {
      setAbierto((a) => !a);
    }
  };

  return (
    <div>
      <button
        type="button"
        className={clase}
        aria-expanded={expandido}
        aria-controls={idLista}
        onClick={onClick}
        {...hintProps(label, hint)}
      >
        <Icon name={icon} size={18} />
        <span className={styles.label}>{label}</span>
        <span className={expandido ? `${styles.chevron} ${styles.chevronOpen}` : styles.chevron}>
          <Icon name="chevron-down" size={16} />
        </span>
      </button>
      {abierto && (
        <div className={styles.subWrap}>
          <div id={idLista} className={styles.subNav} role="group" aria-label={label}>
            {items.map((item) => (
              <NavItemLink key={item.to} item={item} sub hint={hint} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export function Sidebar() {
  const { puede } = useAuth();
  const { collapsed, toggle } = useSidebar();
  const [tip, setTip] = useState<{ label: string; top: number } | null>(null);
  // Cada ítem sólo si el rol puede ver la vista; un grupo sin ítems no se muestra.
  const habilitados = (items: NavItem[]) => items.filter((i) => puedeVer(i.vista, puede));
  const principal = habilitados(PRINCIPAL);
  const gestion = habilitados(GESTION);
  const configuracion = habilitados(CONFIGURACION);
  const hayGestion = gestion.length > 0 || configuracion.length > 0;

  useEffect(() => {
    if (!collapsed) setTip(null);
  }, [collapsed]);

  const hint: HintHandlers = {
    onHint: (label, el) => {
      if (!collapsed) return;
      const r = el.getBoundingClientRect();
      setTip({ label, top: r.top + r.height / 2 });
    },
    onHintEnd: () => setTip(null),
  };

  return (
    <aside
      className={collapsed ? `${styles.sidebar} ${styles.collapsed}` : styles.sidebar}
      data-collapsed={collapsed}
    >
      <Link to="/" className={styles.brand} aria-label="Ir al panel general">
        <img className={styles.logo} src="/yerbanalytics_simbolo.svg" alt="" />
        <div className={styles.brandText}>
          <div className={styles.brandName}>Yerbanalytics</div>
          <div className={styles.brandSub}>Datos bajados a tierra</div>
        </div>
      </Link>

      {principal.length > 0 && (
        <>
          <div className={styles.section} aria-hidden={collapsed}>
            Principal
          </div>
          <nav className={styles.nav} aria-label="Principal">
            {principal.map((item) => (
              <NavItemLink key={item.to} item={item} hint={hint} />
            ))}
          </nav>
        </>
      )}

      {hayGestion && (
        <>
          <div
            className={
              principal.length > 0 ? `${styles.section} ${styles.sectionTop}` : styles.section
            }
            aria-hidden={collapsed}
          >
            Gestión
          </div>
          <nav className={styles.nav} aria-label="Gestión">
            {gestion.map((item) => (
              <NavItemLink key={item.to} item={item} hint={hint} />
            ))}
            {configuracion.length > 0 && (
              <NavGroup icon="config" label="Configuración" items={configuracion} hint={hint} />
            )}
          </nav>
        </>
      )}

      {/* Borde clickeable, como el de shadcn: no entra en el orden de tabulación (está el botón
          de la topbar y Ctrl/⌘ + B). */}
      <button
        type="button"
        className={styles.rail}
        tabIndex={-1}
        aria-label={collapsed ? 'Expandir menú' : 'Colapsar menú'}
        title={collapsed ? 'Expandir menú' : 'Colapsar menú'}
        onClick={toggle}
      />

      {tip && (
        <div className={styles.tooltip} style={{ top: tip.top }} role="tooltip">
          {tip.label}
        </div>
      )}
    </aside>
  );
}
