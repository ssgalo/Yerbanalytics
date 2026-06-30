/* Filtros de la flota: tipo · estado · macro-zona */
import { TIPO_OPTIONS, ZONA_IDS } from '@/data/mock/hardware';
import styles from '../Hardware.module.css';

const ESTADO_OPTS: { value: string; label: string }[] = [
  { value: 'Todos', label: 'Todos' },
  { value: 'operativo', label: 'Operativo' },
  { value: 'intermitente', label: 'Señal intermitente' },
  { value: 'fuera_de_servicio', label: 'Fuera de servicio' },
];

export interface HardwareFilterState {
  tipo: string;
  estado: string;
  zona: string;
}

interface HardwareFiltersProps {
  value: HardwareFilterState;
  count: number;
  onChange: (patch: Partial<HardwareFilterState>) => void;
}

export function HardwareFilters({ value, count, onChange }: HardwareFiltersProps) {
  return (
    <>
      <div className={styles.group}>
        <label className={styles.label}>Tipo</label>
        <select
          className={styles.select}
          value={value.tipo}
          onChange={(e) => onChange({ tipo: e.target.value })}
        >
          <option value="Todos">Todos</option>
          {TIPO_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.group}>
        <label className={styles.label}>Estado</label>
        <select
          className={styles.select}
          value={value.estado}
          onChange={(e) => onChange({ estado: e.target.value })}
        >
          {ESTADO_OPTS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
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
          {['Todas', ...ZONA_IDS].map((o) => (
            <option key={o} value={o}>
              {o}
            </option>
          ))}
        </select>
      </div>

      <span className={styles.count}>
        <b style={{ color: 'var(--ink)', fontFamily: 'var(--font-display)' }}>{count}</b> dispositivos
      </span>
    </>
  );
}
