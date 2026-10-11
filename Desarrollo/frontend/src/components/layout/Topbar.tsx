import { useNurseryDataOpcional } from '@/hooks/NurseryContext';
import { usePageMeta } from '@/hooks/PageMeta';
import { AlertsDropdown } from './AlertsDropdown';
import { ClimaDropdown } from './ClimaDropdown';
import { UserMenu } from './UserMenu';
import styles from './Topbar.module.css';

export function Topbar() {
  // Clima y alertas salen del snapshot del vivero: sin `vivero.ver` no hay ninguno de los dos.
  const vivero = useNurseryDataOpcional();
  const { title, subtitle } = usePageMeta();

  return (
    <header className={styles.topbar}>
      <div>
        <h1 className={styles.title}>{title}</h1>
        {subtitle && <div className={styles.subtitle}>{subtitle}</div>}
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
