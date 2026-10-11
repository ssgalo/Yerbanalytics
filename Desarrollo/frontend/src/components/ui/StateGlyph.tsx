import type { Status } from '@/types/domain';
import { ETIQUETA_ESTADO, TONO, cx } from './estado';
import styles from './StateGlyph.module.css';

interface StateGlyphProps {
  status: Status;
  size?: 'md' | 'lg';
  /**
   * Nombre del estado para el lector de pantalla. Activarlo sólo cuando no hay un texto al lado
   * que ya lo diga; si lo hay, el glifo es decorativo.
   */
  label?: boolean;
}

/**
 * Glifo de estado: círculo lleno (saludable), medio lleno (observación), rombo (crítico),
 * aro vacío (sin señal). El estado nunca depende sólo del color.
 */
export function StateGlyph({ status, size = 'md', label = false }: StateGlyphProps) {
  return (
    <>
      <span
        className={cx(styles.g, styles[TONO[status]], size === 'lg' && styles.lg)}
        data-estado={status}
        aria-hidden="true"
      />
      {label && <span className="sr">{ETIQUETA_ESTADO[status]}</span>}
    </>
  );
}
