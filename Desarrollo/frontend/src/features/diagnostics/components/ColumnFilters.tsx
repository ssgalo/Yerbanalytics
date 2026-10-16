/* Filtros del menú de columna. Las columnas categóricas se filtran eligiendo valores con casillas;
   la fecha, con un rango "desde / hasta". Los dos confirman con "Aplicar". */
import { useId, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Field, Input } from '@/components/ui/Field';
import { cx } from '@/components/ui/estado';
import type { Range } from '../diagnosisQuery';
import styles from './ColumnFilters.module.css';

interface ActionsProps {
  filtered: boolean;
  onClear: () => void;
  onCancel: () => void;
  onApply: () => void;
  canApply: boolean;
}

function FilterActions({ filtered, onClear, onCancel, onApply, canApply }: ActionsProps) {
  return (
    <div className={styles.actions}>
      {filtered && (
        <Button variant="ghost" size="sm" onClick={onClear}>
          Quitar filtro
        </Button>
      )}
      <Button size="sm" onClick={onCancel} className={styles.push}>
        Cancelar
      </Button>
      <Button variant="primary" size="sm" onClick={onApply} disabled={!canApply}>
        Aplicar
      </Button>
    </div>
  );
}

interface ValuesFilterProps {
  label: string;
  options: { value: string; label: string; count: number }[];
  /** Valores elegidos; `null` = todos. */
  selected: string[] | null;
  onApply: (values: string[] | null) => void;
  close: () => void;
  /** Muestra un buscador sobre la lista (columnas con muchos valores). */
  searchable?: boolean;
}

/** Casillas con los valores presentes, como el autofiltro de una planilla. */
export function ValuesFilter({
  label,
  options,
  selected,
  onApply,
  close,
  searchable,
}: ValuesFilterProps) {
  // El borrador arranca de lo aplicado: sin filtro, todo marcado
  const [draft, setDraft] = useState(() => new Set(selected ?? options.map((o) => o.value)));
  const [query, setQuery] = useState('');

  const q = query.trim().toLowerCase();
  const visible = q ? options.filter((o) => o.label.toLowerCase().includes(q)) : options;
  const allChecked = visible.length > 0 && visible.every((o) => draft.has(o.value));
  const someChecked = visible.some((o) => draft.has(o.value));

  function toggle(value: string) {
    setDraft((d) => {
      const next = new Set(d);
      if (next.has(value)) next.delete(value);
      else next.add(value);
      return next;
    });
  }

  function toggleAll() {
    setDraft((d) => {
      const next = new Set(d);
      for (const o of visible) {
        if (allChecked) next.delete(o.value);
        else next.add(o.value);
      }
      return next;
    });
  }

  function apply() {
    // Todo marcado equivale a no filtrar: así un valor nuevo que llegue después también se ve
    const all = options.every((o) => draft.has(o.value));
    onApply(all ? null : options.map((o) => o.value).filter((v) => draft.has(v)));
    close();
  }

  return (
    <>
      <div className={styles.body}>
        <span className={styles.caption}>Mostrar</span>
        {searchable && (
          <Input
            type="search"
            placeholder="Buscar"
            aria-label={`Buscar ${label.toLowerCase()}`}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        )}
        <div className={styles.list}>
          <label className={cx(styles.option, styles.all)}>
            <input
              type="checkbox"
              checked={allChecked}
              ref={(el) => {
                if (el) el.indeterminate = someChecked && !allChecked;
              }}
              onChange={toggleAll}
            />
            <span>Seleccionar todo</span>
          </label>
          {visible.map((o) => (
            <label key={o.value} className={styles.option}>
              <input
                type="checkbox"
                checked={draft.has(o.value)}
                onChange={() => toggle(o.value)}
              />
              <span>{o.label}</span>
              <span className={styles.count}>{o.count}</span>
            </label>
          ))}
          {visible.length === 0 && <span className={styles.none}>Sin coincidencias</span>}
        </div>
      </div>
      <FilterActions
        filtered={selected != null}
        onClear={() => {
          onApply(null);
          close();
        }}
        onCancel={close}
        onApply={apply}
        canApply={draft.size > 0}
      />
    </>
  );
}

// datetime-local trabaja en hora local y sin segundos
function toInput(v: number): string {
  const d = new Date(v);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

// "Hasta las 14:05" incluye todo ese minuto
const fromInput = (s: string, end: boolean) => new Date(s).getTime() + (end ? 59_999 : 0);

const show = (v: number) =>
  new Date(v).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' });

interface RangeFilterProps {
  value: Range | null;
  /** Extremos presentes en los datos, para orientar al completar. */
  extent: Required<Range> | null;
  onApply: (range: Range | null) => void;
  close: () => void;
}

/** Rango de fechas "desde / hasta"; un extremo vacío no limita. */
export function RangeFilter({ value, extent, onApply, close }: RangeFilterProps) {
  const id = useId();
  const [from, setFrom] = useState(value?.min != null ? toInput(value.min) : '');
  const [to, setTo] = useState(value?.max != null ? toInput(value.max) : '');

  const min = from.trim() === '' ? undefined : fromInput(from, false);
  const max = to.trim() === '' ? undefined : fromInput(to, true);
  const invalid = (v: number | undefined) => v != null && Number.isNaN(v);
  const reversed = min != null && max != null && min > max;
  const fromError = invalid(min) ? 'Valor no válido' : undefined;
  const toError = invalid(max) ? 'Valor no válido' : reversed ? 'Es anterior al inicio' : undefined;

  function apply() {
    onApply(min == null && max == null ? null : { min, max });
    close();
  }

  const fields = (
      <>
        <Field label="Desde" htmlFor={`${id}-from`} error={fromError}>
          <Input
            id={`${id}-from`}
            type="datetime-local"
            value={from}
            invalid={!!fromError}
            onChange={(e) => setFrom(e.target.value)}
          />
        </Field>
        <Field label="Hasta" htmlFor={`${id}-to`} error={toError}>
          <Input
            id={`${id}-to`}
            type="datetime-local"
            value={to}
            invalid={!!toError}
            onChange={(e) => setTo(e.target.value)}
          />
        </Field>
      </>
  );

  return (
    <>
      <div className={styles.body}>
        <span className={styles.caption}>Mostrar el rango</span>
        <div className={cx(styles.range, styles.stacked)}>{fields}</div>
        <span className={styles.hint}>
          {extent
            ? `En los datos: de ${show(extent.min)} a ${show(extent.max)}`
            : 'No hay valores conocidos'}
        </span>
      </div>
      <FilterActions
        filtered={value != null}
        onClear={() => {
          onApply(null);
          close();
        }}
        onCancel={close}
        onApply={apply}
        canApply={!fromError && !toError}
      />
    </>
  );
}
