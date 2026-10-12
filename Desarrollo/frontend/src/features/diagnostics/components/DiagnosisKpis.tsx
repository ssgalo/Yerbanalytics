/* KPI de la vista de Diagnósticos: cómo se reparten los diagnósticos entre las clases.

   Mismo lenguaje que el resumen del Panel general: cifras grandes en display, cada una con su
   rótulo, y debajo la barra de composición. Las clases no son estados de salud: la cifra va en
   tinta neutra y el color de la clase (`--dx-*`) sólo aparece en la barra y en su muestra.
   Cada cifra es además un filtro de la grilla. */
import { cx } from '@/components/ui/estado';
import styles from './DiagnosisKpis.module.css';

/** Clase de CSS de cada clase de diagnóstico; una desconocida cae a la de "No concluyente". */
const CLASS_TONE: Record<string, string> = {
  Sano: styles.sano,
  Clorosis: styles.clorosis,
  'Estrés solar': styles.estres,
  'Plaga foliar': styles.plaga,
  'Daño fúngico': styles.fungico,
  'No concluyente': styles.nc,
};
const toneOf = (name: string) => CLASS_TONE[name] ?? styles.nc;

interface DiagnosisKpisProps {
  classes: { name: string; count: number }[];
  total: number;
  selected: string | null;
  onSelect: (diagnosisClass: string | null) => void;
}

export function DiagnosisKpis({ classes, total, selected, onSelect }: DiagnosisKpisProps) {
  const present = classes.filter((c) => c.count > 0);
  const barLabel = `${total} diagnósticos: ${present.map((c) => `${c.count} ${c.name.toLowerCase()}`).join(', ')}`;

  return (
    <section className={styles.summary} aria-label="Diagnósticos por clase">
      <div className={styles.head}>
        <h2 className={styles.heading}>Clases de los {total} diagnósticos</h2>
        <span className={styles.meta}>
          Según los filtros de la grilla · elegí una clase para verla sola
        </span>
      </div>

      <div className={styles.figures} role="group" aria-label="Filtrar por clase">
        {classes.map(({ name, count }) => {
          const pressed = selected === name;
          const pct = total > 0 ? Math.round((count / total) * 100) : 0;
          return (
            <button
              key={name}
              type="button"
              className={styles.figure}
              aria-pressed={pressed}
              onClick={() => onSelect(pressed ? null : name)}
            >
              <span className={cx(styles.number, count === 0 && styles.zero)}>{count}</span>
              <span className={styles.figureLabel}>
                <span className={cx(styles.swatch, toneOf(name))} aria-hidden="true" />
                {name.toLowerCase()}, {pct} %
              </span>
            </button>
          );
        })}
      </div>

      <div className={styles.bar} role="img" aria-label={barLabel}>
        {present.map((c) => (
          <i
            key={c.name}
            className={toneOf(c.name)}
            style={{ flex: c.count }}
            title={`${c.name}: ${c.count}`}
          />
        ))}
      </div>
    </section>
  );
}
