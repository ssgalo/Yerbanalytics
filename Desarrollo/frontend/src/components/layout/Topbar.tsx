import { useNurseryData } from '@/hooks/NurseryContext';
import { usePageMeta } from '@/hooks/PageMeta';
import { Icon } from '@/components/ui/Icon';
import { AlertsDropdown } from './AlertsDropdown';
import styles from './Topbar.module.css';

export function Topbar() {
  const { weather } = useNurseryData();
  const { title, subtitle } = usePageMeta();

  return (
    <header className={styles.topbar}>
      <div>
        <h1 className={styles.title}>{title}</h1>
        {subtitle && <div className={styles.subtitle}>{subtitle}</div>}
      </div>

      <div className={styles.right}>
        <div className={styles.weather}>
          <Icon name="sun" size={18} stroke="var(--warn)" />
          <div style={{ lineHeight: 1.1 }}>
            <div className={styles.weatherTemp}>{weather.tempC}°C</div>
            <div className={styles.weatherSub}>
              UV {weather.uv} · {weather.cond}
            </div>
          </div>
        </div>

        <AlertsDropdown />

        <div className={styles.divider} />

        <div className={styles.user}>
          <div style={{ textAlign: 'right', lineHeight: 1.15 }}>
            <div className={styles.userName}>Mariano Duarte</div>
            <div className={styles.userRole}>Productor Viverista</div>
          </div>
          <div className={styles.avatar}>MD</div>
        </div>
      </div>
    </header>
  );
}
