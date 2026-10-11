import { useNurseryDataOpcional } from '@/hooks/NurseryContext';
import { usePageMeta } from '@/hooks/PageMeta';
import { Icon } from '@/components/ui/Icon';
import { AlertsDropdown } from './AlertsDropdown';
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
            <div className={styles.weather}>
              <Icon name="sun" size={18} stroke="var(--warn)" />
              <div style={{ lineHeight: 1.1 }}>
                <div className={styles.weatherTemp}>{vivero.weather.tempC}°C</div>
                <div className={styles.weatherSub}>
                  UV {vivero.weather.uv} · {vivero.weather.cond}
                </div>
              </div>
            </div>

            <AlertsDropdown />

            <div className={styles.divider} />
          </>
        )}

        <UserMenu />
      </div>
    </header>
  );
}
