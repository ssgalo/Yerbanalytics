/* Barra de filtros del historial: tipo · macro-zona · sector · resultado · rango de fechas */
import styles from './HistorialFilters.module.css';

const TIPO_OPTS = ['Todas', 'Riego', 'Insumo', 'Mediasombra'];
const RES_OPTS = ['Todas', 'Efectiva', 'En seguimiento', 'Pospuesta', 'Abortada'];

export interface HistorialFilterState {
  tipo: string;
  zona: string;
  sector: string;
  resultado: string;
  desde: string;
  hasta: string;
}

interface HistorialFiltersProps {
  value: HistorialFilterState;
  zonaOpts: string[];
  count: number;
  onChange: (patch: Partial<HistorialFilterState>) => void;
}

export function HistorialFilters({ value, zonaOpts, count, onChange }: HistorialFiltersProps) {
  return (
    <div className={styles.row}>
      <div className={styles.group}>
        <label className={styles.label}>Tipo de acción</label>
        <select
          className={styles.select}
          value={value.tipo}
          onChange={(e) => onChange({ tipo: e.target.value })}
        >
          {TIPO_OPTS.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.group}>
        <label className={styles.label}>Macro-zona</label>
        <select
          className={styles.select}
          value={value.zona}
          onChange={(e) => onChange({ zona: e.target.value })}
        >
          {['Todas', ...zonaOpts].map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.group}>
        <label className={styles.label}>Sector</label>
        <input
          className={styles.input}
          type="text"
          placeholder="ej. MZ-2-014"
          value={value.sector}
          onChange={(e) => onChange({ sector: e.target.value })}
        />
      </div>

      <div className={styles.group}>
        <label className={styles.label}>Resultado</label>
        <select
          className={styles.select}
          value={value.resultado}
          onChange={(e) => onChange({ resultado: e.target.value })}
        >
          {RES_OPTS.map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.group}>
        <label className={styles.label}>Desde</label>
        <input
          className={styles.input}
          type="date"
          value={value.desde}
          onChange={(e) => onChange({ desde: e.target.value })}
        />
      </div>

      <div className={styles.group}>
        <label className={styles.label}>Hasta</label>
        <input
          className={styles.input}
          type="date"
          value={value.hasta}
          onChange={(e) => onChange({ hasta: e.target.value })}
        />
      </div>

      <div className={styles.count}>
        <b style={{ color: 'var(--ink)', fontFamily: 'var(--font-display)' }}>{count}</b> acciones
      </div>
    </div>
  );
}
