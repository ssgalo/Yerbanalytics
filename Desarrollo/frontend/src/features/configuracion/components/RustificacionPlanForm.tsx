/* Plan de rustificación: cronograma de días y % de apertura por etapa (HU-15 CA-06) */
import type { RustificacionEtapa } from '@/types/domain';
import { etapasError } from '@/lib/configValidation';
import { NumberField } from './NumberField';
import styles from './ConfigForms.module.css';

interface RustificacionPlanFormProps {
  value: RustificacionEtapa[];
  aperturaMax: number;
  onChange: (etapas: RustificacionEtapa[]) => void;
}

/** Reasigna el orden 1..n tras agregar o quitar etapas. */
function reindex(etapas: RustificacionEtapa[]): RustificacionEtapa[] {
  return etapas.map((e, idx) => ({ ...e, orden: idx + 1 }));
}

export function RustificacionPlanForm({ value, aperturaMax, onChange }: RustificacionPlanFormProps) {
  const err = etapasError(value, aperturaMax);

  const update = (i: number, patch: Partial<RustificacionEtapa>) =>
    onChange(value.map((e, idx) => (idx === i ? { ...e, ...patch } : e)));

  const remove = (i: number) => onChange(reindex(value.filter((_, idx) => idx !== i)));

  const add = () => {
    const last = value[value.length - 1];
    const start = last ? last.diaHasta + 1 : 1;
    onChange(
      reindex([
        ...value,
        { orden: 0, diaDesde: start, diaHasta: start + 6, aperturaPct: Math.min(100, aperturaMax) },
      ]),
    );
  };

  return (
    <div>
      <div className={styles.etapas}>
        {value.map((e, i) => (
          <div className={styles.etapaRow} key={e.orden}>
            <span className={styles.etapaOrden}>Etapa {e.orden}</span>
            <NumberField label="Día desde" value={e.diaDesde} min={1} onChange={(v) => update(i, { diaDesde: v })} />
            <NumberField label="Día hasta" value={e.diaHasta} min={1} onChange={(v) => update(i, { diaHasta: v })} />
            <NumberField
              label="Apertura"
              unit="%"
              value={e.aperturaPct}
              onChange={(v) => update(i, { aperturaPct: v })}
            />
            <button
              type="button"
              className={styles.btnRemove}
              aria-label={`Eliminar etapa ${e.orden}`}
              onClick={() => remove(i)}
            >
              ×
            </button>
          </div>
        ))}
      </div>
      {err && (
        <div className={styles.rowError} style={{ padding: '10px 0 0' }}>
          {err}
        </div>
      )}
      <button type="button" className={styles.btnGhost} style={{ marginTop: 14 }} onClick={add}>
        + Agregar etapa
      </button>
    </div>
  );
}
