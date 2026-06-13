import { useEffect, useRef, useState } from 'react';
import { useNurseryData } from '@/hooks/NurseryContext';
import { Icon } from '@/components/ui/Icon';
import styles from './AlertsDropdown.module.css';

export function AlertsDropdown() {
  const { alerts } = useNurseryData();
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const unread = alerts.filter((a) => !a.read).length;

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
      <button className={styles.bell} onClick={() => setOpen((o) => !o)} aria-label="Alertas">
        <Icon name="bell" size={20} />
        <span className={styles.count}>{unread}</span>
      </button>

      {open && (
        <div className={styles.panel}>
          <div className={styles.header}>
            <span className={styles.headerTitle}>Alertas activas</span>
            <span className={styles.headerSub}>{unread} sin atender</span>
          </div>
          <div className={styles.list}>
            {alerts.map((a, i) => (
              <div className={styles.alert} key={i}>
                <span className={styles.alertDot} style={{ background: a.color }} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div className={styles.alertHead}>
                    <span className={styles.alertLevel} style={{ color: a.color }}>
                      {a.level}
                    </span>
                    <span className={styles.alertMeta}>
                      {a.time} · {a.sectorId}
                    </span>
                  </div>
                  <div className={styles.alertMsg}>{a.msg}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
