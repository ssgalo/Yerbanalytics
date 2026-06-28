/* Umbrales de las 5 métricas: bandas ideal / advertencia / crítico (HU-15 CA-03) */
import { Fragment } from 'react';
import type { MetricThreshold } from '@/types/domain';
import { umbralError } from '@/lib/configValidation';
import styles from './ConfigForms.module.css';

interface UmbralesFormProps {
  value: MetricThreshold[];
  onChange: (index: number, patch: Partial<MetricThreshold>) => void;
}

type BandKey = keyof Pick<
  MetricThreshold,
  'idealMin' | 'idealMax' | 'warnMin' | 'warnMax' | 'critMin' | 'critMax'
>;

const COLS: Array<{ key: BandKey; head: string }> = [
  { key: 'idealMin', head: 'Ideal mín' },
  { key: 'idealMax', head: 'Ideal máx' },
  { key: 'warnMin', head: 'Advert. mín' },
  { key: 'warnMax', head: 'Advert. máx' },
  { key: 'critMin', head: 'Crít. mín' },
  { key: 'critMax', head: 'Crít. máx' },
];

export function UmbralesForm({ value, onChange }: UmbralesFormProps) {
  return (
    <table className={styles.table}>
      <thead>
        <tr>
          <th>Métrica</th>
          {COLS.map((c) => (
            <th key={c.key}>{c.head}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {value.map((u, i) => {
          const err = umbralError(u);
          const step = u.dec > 0 ? 0.1 : 1;
          return (
            <Fragment key={u.key}>
              <tr>
                <td>
                  <span className={styles.metricName}>{u.label}</span>
                  <span className={styles.metricUnit}>{u.unit}</span>
                </td>
                {COLS.map((c) => (
                  <td key={c.key}>
                    <input
                      className={styles.cellInput}
                      type="number"
                      step={step}
                      value={u[c.key]}
                      aria-label={`${u.label} · ${c.head}`}
                      onChange={(e) => {
                        const n = Number(e.target.value);
                        if (!Number.isNaN(n)) onChange(i, { [c.key]: n });
                      }}
                    />
                  </td>
                ))}
              </tr>
              {err && (
                <tr>
                  <td className={styles.rowError} colSpan={COLS.length + 1}>
                    {err}
                  </td>
                </tr>
              )}
            </Fragment>
          );
        })}
      </tbody>
    </table>
  );
}
