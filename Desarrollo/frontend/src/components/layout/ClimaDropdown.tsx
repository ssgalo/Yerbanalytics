import { useEffect, useRef, useState } from 'react';
import { useNurseryData } from '@/hooks/NurseryContext';
import { Icon } from '@/components/ui/Icon';
import styles from './ClimaDropdown.module.css';

/** Clima de la topbar: al tocarlo despliega el clima y riesgo actual con su pronóstico. */
export function ClimaDropdown() {
  const { weather } = useNurseryData();
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  // cerrar al hacer clic fuera
  useEffect(() => {
    if (!open) return;
    function onClick(e: MouseEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [open]);

  return (
    <div className={styles.wrap} ref={wrapRef}>
      <button
        className={styles.trigger}
        onClick={() => setOpen((o) => !o)}
        aria-label="Clima y riesgo"
        aria-expanded={open}
      >
        <Icon name="sun" size={18} stroke="var(--warn)" />
        <div style={{ lineHeight: 1.1 }}>
          <div className={styles.triggerTemp}>{weather.tempC}°C</div>
          <div className={styles.triggerSub}>
            UV {weather.uv} · {weather.cond}
          </div>
        </div>
      </button>

      {open && (
        <div className={styles.panel}>
          <div className={styles.header}>
            <span className={styles.headerTitle}>Clima y riesgo</span>
          </div>

          <div className={styles.body}>
            {/* Temperatura actual */}
            <div className={styles.currentRow}>
              <div className={styles.sunIcon}>
                <svg
                  width={26}
                  height={26}
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="var(--warn)"
                  strokeWidth={1.7}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <circle cx="12" cy="12" r="4" />
                  <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4" />
                </svg>
              </div>

              <div>
                <div className={styles.tempNumber}>{weather.tempC}°C</div>
                <div className={styles.condText}>
                  {weather.cond} · humedad {weather.hum}%
                </div>
              </div>

              <div className={styles.uvBlock}>
                <div className={styles.uvLabel}>Índice UV</div>
                <div className={styles.uvValue}>
                  {weather.uv} · {weather.uvLabel}
                </div>
              </div>
            </div>

            {/* Banner de alerta de lluvia */}
            <div className={styles.rainBanner}>
              <svg
                width={18}
                height={18}
                viewBox="0 0 24 24"
                fill="none"
                stroke="var(--info-ink)"
                strokeWidth={1.9}
                strokeLinecap="round"
                strokeLinejoin="round"
                style={{ flexShrink: 0, marginTop: 1 }}
              >
                <path d="M12 9v4M12 17h.01" />
                <path d="M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z" />
              </svg>
              <div className={styles.rainText}>{weather.rainText}</div>
            </div>

            {/* Pronóstico por franjas horarias */}
            <div className={styles.forecastGrid}>
              {weather.forecast.map((f) => (
                <div key={f.t} className={styles.forecastSlot}>
                  <div className={styles.forecastTime}>{f.t}</div>
                  <div className={styles.forecastRain}>{f.rain}%</div>
                  <div className={styles.forecastUv}>UV {f.uv}</div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
