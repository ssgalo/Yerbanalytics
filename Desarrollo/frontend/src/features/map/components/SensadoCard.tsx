/* ============================================================
   Panel "Valores sensados · nodo testigo" de la macro-zona.

   El alcance define UN solo nodo sensor por macro-zona: esta lectura
   aplica a sus 100 sectores por igual. Por eso el panel vive acá y no
   en el detalle de cada sector.

   Cada métrica es un botón: abre su histórico en el inspector.
   ============================================================ */
import type { MetricTile, NodoTestigo, LecturaZona } from '@/types/domain';
import { Card } from '@/components/ui/Card';
import { Icon } from '@/components/ui/Icon';
import { Sparkline } from '@/components/ui/Sparkline';
import styles from './SensadoCard.module.css';

interface SensadoCardProps {
  zonaName: string;
  lectura: LecturaZona;
  nodo: NodoTestigo;
  tiles: MetricTile[];
  /** Métrica cuyo histórico está abierto en el inspector; `null` si está cerrado. */
  selected: string | null;
  onSelect: (metricKey: string) => void;
}

export function SensadoCard({
  zonaName,
  lectura,
  nodo,
  tiles,
  selected,
  onSelect,
}: SensadoCardProps) {
  return (
    <Card className={styles.card}>
      <div className={styles.head}>
        <h2 className={styles.title}>Valores sensados</h2>
        <span className={styles.origen}>Nodo testigo · {zonaName}</span>
      </div>


      {/* La lectura vencida es la advertencia más importante del panel: todo lo que
          sigue son valores viejos. */}
      {lectura.stale && (
        <p className={styles.stale}>
          <Icon name="alert" size={13} />
          Sin reporte reciente — los valores no son vigentes.
        </p>
      )}

      <div className={styles.grid}>
        {tiles.map((t) => (
          <MetricButton
            key={t.key}
            tile={t}
            active={t.key === selected}
            onSelect={() => onSelect(t.key)}
          />
        ))}
      </div>

      {/* Estado del nodo que produjo la lectura */}
      <div className={styles.nodo}>
        <span
          className={nodo.bateriaBaja ? `${styles.nodoItem} ${styles.nodoAlerta}` : styles.nodoItem}
        >
          <Icon name="battery" size={14} />
          {nodo.battery != null ? `${nodo.battery}%` : '—'}
        </span>
        <span className={styles.nodoItem}>
          <Icon name="signal" size={14} />
          {nodo.signal != null ? `${nodo.signal} dBm` : '—'}
        </span>
        <span className={styles.nodoItem}>
          <Icon name="clock" size={14} />
          {lectura.ago}
        </span>
        {nodo.bateriaBaja && <span className={styles.bateriaAviso}>Batería baja</span>}
      </div>
    </Card>
  );
}

interface MetricButtonProps {
  tile: MetricTile;
  active: boolean;
  onSelect: () => void;
}

function MetricButton({ tile, active, onSelect }: MetricButtonProps) {
  const sinDato = tile.value === '—';
  return (
    <button
      type="button"
      className={active ? `${styles.metric} ${styles.metricActive}` : styles.metric}
      onClick={onSelect}
      aria-pressed={active}
      title={
        tile.provisional
          ? `${tile.label} · rango provisional, pendiente de validación agronómica`
          : tile.label
      }
    >
      <span className={styles.metricLabel}>
        {tile.label}
        {tile.provisional && (
          <span className={styles.provisional} aria-label="rango provisional">
            *
          </span>
        )}
      </span>

      <span className={styles.valueRow}>
        {/* Sin lectura: se muestra el guión sin color de estado, para no fingir un dato */}
        <span className={styles.value} style={{ color: sinDato ? 'var(--faint)' : tile.color }}>
          {tile.value}
        </span>
        {!sinDato && <span className={styles.unit}>{tile.unit}</span>}
        <span className={styles.ideal}>{tile.ideal}</span>
      </span>

      {tile.line ? (
        <Sparkline line={tile.line} color={tile.color} vbWidth={120} vbHeight={34} height={20} />
      ) : (
        <span className={styles.sinSerie} />
      )}
    </button>
  );
}
