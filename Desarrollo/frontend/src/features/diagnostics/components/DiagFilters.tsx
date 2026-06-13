/* Barra de filtros: Estado / anomalía + Severidad + contador */
import styles from './DiagFilters.module.css';

const ESTADO_OPTS = ['Todas', 'Estrés solar', 'Clorosis', 'Plaga foliar', 'Daño fúngico', 'No concluyente'];
const SEV_OPTS = ['Todas', 'Alta', 'Media', 'Baja'];

interface DiagFiltersProps {
  diagEstado: string;
  diagSev: string;
  diagCount: number;
  onEstado: (v: string) => void;
  onSev: (v: string) => void;
}

export function DiagFilters({ diagEstado, diagSev, diagCount, onEstado, onSev }: DiagFiltersProps) {
  return (
    <div className={styles.row}>
      {/* Filtro por estado / anomalía */}
      <div className={styles.group}>
        <label className={styles.label}>Estado / anomalía</label>
        <select
          className={styles.select}
          value={diagEstado}
          onChange={(e) => onEstado(e.target.value)}
        >
          {ESTADO_OPTS.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      </div>

      {/* Filtro por severidad */}
      <div className={styles.group}>
        <label className={styles.label}>Severidad</label>
        <select
          className={styles.select}
          value={diagSev}
          onChange={(e) => onSev(e.target.value)}
        >
          {SEV_OPTS.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      </div>

      {/* Contador alineado a la derecha */}
      <div className={styles.count}>
        <b style={{ color: 'var(--ink)', fontFamily: 'var(--font-display)' }}>{diagCount}</b> diagnósticos
      </div>
    </div>
  );
}
