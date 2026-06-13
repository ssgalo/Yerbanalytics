/* Seguimiento post-acción — replica exacta del diseño HTML (líneas 505-528) */
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Icon } from '@/components/ui/Icon';
import type { Evolution } from '@/types/domain';
import styles from './PostActionCard.module.css';

interface PostActionCardProps {
  evo: Evolution;
}

export function PostActionCard({ evo }: PostActionCardProps) {
  return (
    <Card className={styles.card}>
      <h3 className={styles.title}>Seguimiento post-acción</h3>

      {evo.show ? (
        <>
          <div className={styles.latencia}>Latencia configurada: {evo.latencia}</div>

          {/* Comparación antes / ahora */}
          <div className={styles.comparison}>
            {/* Antes */}
            <div className={styles.box}>
              <div className={styles.boxLabel}>Antes</div>
              <div className={styles.boxValue}>{evo.antes}%</div>
            </div>

            {/* Flecha */}
            <Icon name="arrow-right" size={20} stroke="var(--faint)" strokeWidth={2} />

            {/* Ahora — fondo ok-soft */}
            <div className={`${styles.box} ${styles.boxOk}`}>
              <div className={`${styles.boxLabel} ${styles.boxLabelOk}`}>Ahora</div>
              <div className={styles.boxValue} style={{ color: 'var(--ok)' }}>
                {evo.ahora}%
              </div>
            </div>
          </div>

          {/* Delta + badge de veredicto */}
          <div className={styles.footer}>
            <span className={styles.delta}>
              Delta {evo.delta}{evo.unit}
            </span>
            <Badge soft={evo.vSoft} ink={evo.vInk} style={{ fontSize: '12px', fontWeight: 700, padding: '3px 11px' }}>
              {evo.verdict}
            </Badge>
          </div>
        </>
      ) : (
        /* Estado vacío */
        <div className={styles.empty}>
          Sin acciones recientes que requieran seguimiento. El sector opera dentro de los parámetros esperados.
        </div>
      )}
    </Card>
  );
}
