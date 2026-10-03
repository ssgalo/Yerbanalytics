import type { NodoTestigo } from '@/types/domain';
import { Icon } from '@/components/ui/Icon';
import styles from './NodoChip.module.css';

interface NodoChipProps {
  nodo: NodoTestigo;
  /** El nodo no tiene lectura vigente: no se muestran batería ni señal. */
  sinDatos: boolean;
}

/** Chip del nodo testigo de una macro-zona (batería y señal). Habla por todos sus sectores. */
export function NodoChip({ nodo, sinDatos }: NodoChipProps) {
  const estado = sinDatos ? 'stale' : nodo.bateriaBaja ? 'low' : 'ok';
  const partes = [
    nodo.battery != null ? `${nodo.battery}%` : null,
    nodo.signal != null ? `${nodo.signal} dBm` : null,
  ].filter(Boolean);
  const etiqueta = sinDatos || partes.length === 0 ? 'Sin datos' : partes.join(' · ');

  return (
    <span
      className={styles.chip}
      data-estado={estado}
      title="Nodo testigo de la macro-zona (habla por todos sus sectores)"
    >
      <Icon name="sensor" size={12} strokeWidth={2} />
      <span className={styles.dot} />
      {etiqueta}
    </span>
  );
}
