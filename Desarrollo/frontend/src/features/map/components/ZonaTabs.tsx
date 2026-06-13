/* ============================================================
   ZonaTabs — barra de navegación entre macro-zonas del mapa.
   Tab activa: fondo g800 + borde g700 + texto cream.
   Tab inactiva: fondo card + borde line + texto ink.
   ============================================================ */
import type { Zona } from '@/types/domain';
import styles from './ZonaTabs.module.css';

interface ZonaTabsProps {
  zonas: Zona[];
  zonaId: string;
  onSelect: (id: string) => void;
}

export function ZonaTabs({ zonas, zonaId, onSelect }: ZonaTabsProps) {
  return (
    <div className={styles.tabsBar}>
      {zonas.map((z) => {
        const isActive = z.id === zonaId;
        return (
          <button
            key={z.id}
            className={styles.tab}
            style={
              isActive
                ? {
                    background: 'var(--g800)',
                    border: '1px solid var(--g700)',
                    color: 'var(--cream)',
                  }
                : {
                    background: 'var(--card)',
                    border: '1px solid var(--line)',
                    color: 'var(--ink)',
                  }
            }
            onClick={() => onSelect(z.id)}
          >
            {/* Nombre de zona */}
            <span className={styles.tabName}>{z.name}</span>
            {/* Subtexto: cantidad en alerta */}
            <span
              className={styles.tabSub}
              style={{
                color: isActive ? 'rgba(236,241,233,.62)' : 'var(--muted)',
              }}
            >
              {z.alerta} en alerta
            </span>
          </button>
        );
      })}
    </div>
  );
}
