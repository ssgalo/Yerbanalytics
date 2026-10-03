import { Glyph } from '@/components/ui/Icon';
import styles from './JerarquiaExplainer.module.css';

const PASOS = [
  { label: 'Vivero', path: 'M3 21h18M5 21V9l7-6 7 6v12M9 21v-6h6v6' },
  { label: 'Macro-zona', path: 'M3 3h8v8H3zM13 3h8v8h-8zM3 13h8v8H3zM13 13h8v8h-8z' },
  { label: 'Sector', path: 'M4 4h16v16H4z M4 10h16 M10 4v16' },
  { label: 'Bandeja', path: 'M3 8h18l-2 11H5z M8 8V5h8v3' },
  { label: 'Tubete / plantín', path: 'M12 2C9 6 7 9 7 12a5 5 0 0010 0c0-3-2-6-5-10z' },
];

interface JerarquiaExplainerProps {
  /** Índices de los pasos a resaltar: dónde estamos parados. */
  activos: number[];
}

/** Tira Vivero → Macro-zona → Sector → Bandeja → Tubete, con los niveles de esta vista resaltados. */
export function JerarquiaExplainer({ activos }: JerarquiaExplainerProps) {
  return (
    <div className={styles.strip}>
      {PASOS.map((p, i) => (
        <span key={p.label} className={styles.grupo}>
          {i > 0 && <span className={styles.flecha}>→</span>}
          <span className={activos.includes(i) ? `${styles.paso} ${styles.activo}` : styles.paso}>
            <Glyph path={p.path} size={14} strokeWidth={1.8} />
            {p.label}
          </span>
        </span>
      ))}
    </div>
  );
}
