/* Tile de métrica individual — replica exacta del diseño HTML (líneas 472-481) */
import { Card } from '@/components/ui/Card';
import { Sparkline } from '@/components/ui/Sparkline';
import type { MetricTile as MetricTileType } from '@/types/domain';
import styles from './MetricTile.module.css';

interface MetricTileProps {
  tile: MetricTileType;
}

export function MetricTile({ tile }: MetricTileProps) {
  return (
    <Card className={styles.tile}>
      <div className={styles.label}>{tile.label}</div>

      <div className={styles.valueRow}>
        {/* Valor numérico con color dinámico */}
        <span className={styles.value} style={{ color: tile.color }}>
          {tile.value}
        </span>
        <span className={styles.unit}>{tile.unit}</span>
      </div>

      {/* Sparkline de tendencia */}
      <Sparkline
        line={tile.line}
        color={tile.color}
        vbWidth={120}
        vbHeight={34}
        height={34}
        style={{ marginTop: 8 }}
      />

      <div className={styles.ideal}>Óptimo {tile.ideal}</div>
    </Card>
  );
}
