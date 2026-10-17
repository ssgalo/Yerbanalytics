/* Piezas comunes de las tablas filtrables de la vista Usuarios (cuentas y auditoría). */
import { linkClass } from '@/components/ui/clases';
import type { ColumnQuery } from '@/hooks/useColumnQuery';
import styles from '../Usuarios.module.css';

/** "3 de 5 · Quitar filtros" con filtros; si no, sólo el total. */
export function TableResult<T>({ query, noun }: { query: ColumnQuery<T>; noun: [string, string] }) {
  const total = query.rows.length;
  const shown = query.visible.length;
  const word = total === 1 ? noun[0] : noun[1];
  return (
    <span className={styles.result} aria-live="polite">
      {query.anyFilter ? (
        <>
          <span>
            <b>{shown}</b> de {total} {word}
          </span>
          <button type="button" className={linkClass} onClick={query.clearFilters}>
            Quitar filtros
          </button>
        </>
      ) : (
        <span>
          <b>{total}</b> {word}
        </span>
      )}
    </span>
  );
}

/** Fila de tabla vacía: una línea discreta. Si el vacío es por los filtros, ofrece quitarlos. */
export function EmptyRow<T>({ query, colSpan, empty }: { query: ColumnQuery<T>; colSpan: number; empty: string }) {
  return (
    <tr>
      <td colSpan={colSpan} className={styles.emptyCell}>
        {query.rows.length === 0 ? (
          empty
        ) : (
          <>
            Ninguna fila coincide con los filtros.{' '}
            <button type="button" className={linkClass} onClick={query.clearFilters}>
              Quitar filtros
            </button>
          </>
        )}
      </td>
    </tr>
  );
}
