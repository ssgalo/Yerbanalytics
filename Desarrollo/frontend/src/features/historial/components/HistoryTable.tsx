/* Tabla de acciones del historial. Cada columna se filtra y se ordena desde su encabezado, como en
   Usuarios; al elegir una fila, su detalle se ve al lado (en celular, a pantalla completa). */
import type { KeyboardEvent } from 'react';
import { Chip } from '@/components/ui/Chip';
import { ColumnHeader } from '@/components/ui/ColumnHeader';
import { Glyph } from '@/components/ui/Icon';
import { linkClass } from '@/components/ui/clases';
import { cx } from '@/components/ui/estado';
import type { ColumnQuery } from '@/hooks/useColumnQuery';
import type { ActionRecord } from '@/types/domain';
import { HISTORY_COLUMNS, isZoneWide, resultLabel, resultTone, typeIconPath } from '../actionPresentation';
import styles from './HistoryTable.module.css';

const isStatus = (tone: string) => tone === 'ok' || tone === 'warning' || tone === 'critical' || tone === 'offline';

function ResultChip({ record }: { record: ActionRecord }) {
  const tone = resultTone(record);
  return (
    <Chip tone={tone} glyph={isStatus(tone)}>
      {resultLabel(record)}
    </Chip>
  );
}

interface HistoryTableProps {
  query: ColumnQuery<ActionRecord>;
  selectedId: string | null;
  onSelect: (id: string) => void;
  /** Aviso al pie cuando el historial vino recortado. */
  footnote?: string;
}

export function HistoryTable({ query, selectedId, onSelect, footnote }: HistoryTableProps) {
  const total = query.rows.length;
  const shown = query.visible.length;

  function onKeyDown(e: KeyboardEvent<HTMLTableRowElement>, id: string) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      onSelect(id);
    }
  }

  return (
    <section className={styles.card} aria-label="Acciones">
      <header className={styles.head}>
        <h2 className={styles.title}>Acciones</h2>
        <span className={styles.result} aria-live="polite">
          {query.anyFilter ? (
            <>
              <span>
                <b>{shown}</b> de {total} {total === 1 ? 'acción' : 'acciones'}
              </span>
              <button type="button" className={linkClass} onClick={query.clearFilters}>
                Quitar filtros
              </button>
            </>
          ) : (
            <span>
              <b>{total}</b> {total === 1 ? 'acción' : 'acciones'}
            </span>
          )}
        </span>
      </header>

      <div className={styles.scroll}>
        <table className={styles.table} aria-label="Acciones">
          <thead>
            <tr>
              {HISTORY_COLUMNS.map((c, i) => (
                <th key={c.key}>
                  <ColumnHeader column={c} query={query} alignEnd={i === HISTORY_COLUMNS.length - 1} />
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {shown === 0 && (
              <tr className={styles.emptyRow}>
                <td colSpan={HISTORY_COLUMNS.length}>
                  {total === 0 ? (
                    'No hay acciones registradas.'
                  ) : (
                    <>
                      Ninguna acción coincide con los filtros.{' '}
                      <button type="button" className={linkClass} onClick={query.clearFilters}>
                        Quitar filtros
                      </button>
                    </>
                  )}
                </td>
              </tr>
            )}
            {query.visible.map((r) => {
              const zoneWide = isZoneWide(r);
              return (
                <tr
                  key={r.id}
                  tabIndex={0}
                  aria-selected={r.id === selectedId}
                  onClick={() => onSelect(r.id)}
                  onKeyDown={(e) => onKeyDown(e, r.id)}
                >
                  <td className={cx(styles.num, styles.colDate)}>{r.fecha}</td>
                  <td className={styles.colType}>
                    <span className={styles.type}>
                      <Glyph path={typeIconPath(r)} size={15} />
                      {r.tipo}
                    </span>
                  </td>
                  <td className={cx(styles.code, styles.colZone)}>{r.zonaName}</td>
                  <td className={cx(zoneWide ? styles.muted : styles.code, styles.colSector)}>{r.sectorId}</td>
                  <td className={styles.colResult}>
                    <ResultChip record={r} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {footnote && <p className={styles.footnote}>{footnote}</p>}
    </section>
  );
}
