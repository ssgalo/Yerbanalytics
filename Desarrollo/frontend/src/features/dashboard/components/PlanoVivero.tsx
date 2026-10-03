import type { Zona } from '@/types/domain';
import { Icon } from '@/components/ui/Icon';
import { Card } from '@/components/ui/Card';
import { estadoZona, resumenVivero, resumenZona } from '../resumenVivero';
import { ComoLeer } from './ComoLeer';
import styles from './PlanoVivero.module.css';

interface PlanoViveroProps {
  zonas: Zona[];
  /** Tocar una parcela lleva al bloque detallado de esa macro-zona. */
  onSelectZona: (zonaId: string) => void;
}

/** "Plano" del vivero: cada macro-zona como una parcela, coloreada por su peor estado. */
export function PlanoVivero({ zonas, onSelectZona }: PlanoViveroProps) {
  const resumen = resumenVivero(zonas);

  return (
    <Card className={styles.card}>
      <h2 className={styles.title}>Plano del vivero</h2>
      <p className={styles.resumen} data-status={resumen.status}>
        <span className={styles.resumenDot} />
        {resumen.text}
      </p>
      <p className={styles.sub}>
        Cada parcela de abajo es una macro-zona del vivero. Tocá una para ir a su detalle.
      </p>

      <div className={styles.grid}>
        {zonas.map((zona) => (
          <button
            key={zona.id}
            type="button"
            className={styles.parcela}
            data-status={estadoZona(zona)}
            onClick={() => onSelectZona(zona.id)}
          >
            <span className={styles.badge}>{zona.id}</span>
            <span className={styles.sensor}>
              <Icon name="sensor" size={13} strokeWidth={2} />
              sensor testigo
            </span>
            <span className={styles.resumenZona}>{resumenZona(zona)}</span>
          </button>
        ))}
      </div>

      <ComoLeer />
    </Card>
  );
}
