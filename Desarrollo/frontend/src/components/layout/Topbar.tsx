import { useNurseryDataOpcional } from '@/hooks/NurseryContext';
import { usePageMeta } from '@/hooks/PageMeta';
import { useSidebar } from '@/hooks/SidebarContext';
import { Icon } from '@/components/ui/Icon';
import { AlertsDropdown } from './AlertsDropdown';
import { ClimaDropdown } from './ClimaDropdown';
import { UserMenu } from './UserMenu';
import styles from './Topbar.module.css';

export function Topbar() {
  // Clima y alertas salen del snapshot del vivero: sin `vivero.ver` no hay ninguno de los dos.
  const vivero = useNurseryDataOpcional();
  const { title, subtitle } = usePageMeta();
  const { collapsed, toggle } = useSidebar();
  const toggleLabel = collapsed ? 'Expandir menú' : 'Colapsar menú';

  return (
    <header className={styles.topbar}>
      <div className={styles.left}>
        <button
          type="button"
          className={styles.sidebarToggle}
          onClick={toggle}
          aria-label={toggleLabel}
          aria-expanded={!collapsed}
          title={`${toggleLabel} (Ctrl+B)`}
        >
          <Icon name="sidebar" size={18} />
        </button>
        <div className={styles.divider} />
        <div>
          <h1 className={styles.title}>{title}</h1>
          {subtitle && <div className={styles.subtitle}>{subtitle}</div>}
        </div>
      </div>

      <div className={styles.right}>
        {vivero && (
          <>
            <ClimaDropdown />

            <AlertsDropdown />

            <div className={styles.divider} />
          </>
        )}

        <UserMenu />
      </div>
    </header>
  );
}
