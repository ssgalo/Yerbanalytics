/* Bandas de severidad del diagnóstico: por cada estado anómalo, desde cuántas hojas cuenta
   (mínimo) y desde cuántas su severidad es media y alta. Una fila por estado, tres campos. */
import type { SeverityBand } from '@/types/domain';
import { InputUnit } from '@/components/ui/Field';
import {
  BAND_FIELD_LABEL,
  BAND_FIELDS,
  bandErrors,
  bandSummary,
  type BandField,
} from '@/lib/diagnosisCriteria';
import { cx } from '@/components/ui/estado';
import styles from './DiagnosisCriteria.module.css';

interface SeverityBandsFormProps {
  value: SeverityBand[];
  /** Bandas guardadas: marcan qué campo cambió. */
  saved?: SeverityBand[];
  onChange: (state: string, patch: Partial<SeverityBand>) => void;
}

/** Un id de DOM estable para un estado ("Daño fúngico" → "dano-fungico"). */
const slug = (s: string) =>
  s
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-');

/** Un campo vacío queda como NaN: la validación lo marca en lugar de convertirlo en 0. */
const parse = (raw: string) => (raw.trim() === '' ? Number.NaN : Number(raw));

export function SeverityBandsForm({ value, saved, onChange }: SeverityBandsFormProps) {
  return (
    <div className={styles.bands} role="table" aria-label="Bandas de severidad por estado">
      <div className={cx(styles.bandRow, styles.bandHead)} role="row">
        <span role="columnheader">Estado</span>
        {BAND_FIELDS.map((f) => (
          <span key={f} role="columnheader">
            {BAND_FIELD_LABEL[f]}
          </span>
        ))}
      </div>

      {value.map((band) => {
        const id = slug(band.state);
        const before = saved?.find((b) => b.state === band.state);
        const errors = bandErrors(band);
        const errorOf = (f: BandField) => errors.find((e) => e.field === f);
        const errorId = `${id}-error`;
        return (
          <div key={band.state} className={styles.bandRow} role="row">
            <div className={styles.bandState} role="rowheader">
              <b>{band.state}</b>
              {errors.length > 0 ? (
                <span id={errorId} className={styles.bandError} role="alert">
                  {errors.map((e) => e.message).join(' ')}
                </span>
              ) : (
                <span className={styles.bandSummary}>{bandSummary(band)}</span>
              )}
            </div>
            {BAND_FIELDS.map((f) => {
              const v = band[f];
              const invalid = !!errorOf(f);
              return (
                <label key={f} className={styles.bandCell} role="cell">
                  <span className={styles.bandCellLabel}>{BAND_FIELD_LABEL[f]}</span>
                  <InputUnit
                    unit="hojas"
                    type="number"
                    inputMode="numeric"
                    min={1}
                    step={1}
                    value={Number.isNaN(v) ? '' : v}
                    aria-label={`${band.state} · ${BAND_FIELD_LABEL[f]}`}
                    aria-describedby={invalid ? errorId : undefined}
                    invalid={invalid}
                    modified={before !== undefined && !Object.is(before[f], v)}
                    onChange={(e) => onChange(band.state, { [f]: parse(e.target.value) })}
                  />
                </label>
              );
            })}
          </div>
        );
      })}
    </div>
  );
}
