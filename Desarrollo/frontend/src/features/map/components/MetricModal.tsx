/* ============================================================
   Detalle de una métrica del nodo testigo en un modal centrado.

   Se abre desde una fila de "Valores sensados" sobre el fondo
   oscurecido: la lista no se desplaza ni deja huecos. Muestra el valor
   contra su rango y el histórico, y permite pasar a la métrica
   anterior o siguiente sin cerrarlo.

   Se cierra con Escape, con la X o con un clic fuera; al cerrar, el
   foco vuelve a la fila que lo abrió.
   ============================================================ */
import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import type { Metric, Range, Status, Zona } from '@/types/domain';
import { Icon } from '@/components/ui/Icon';
import { RangeBar } from '@/components/ui/Medidas';
import { StateGlyph } from '@/components/ui/StateGlyph';
import { TONO, cx } from '@/components/ui/estado';
import { fmtNum } from '@/components/ui/rango';
import { MetricHistoryChart } from './MetricHistoryChart';
import styles from './MetricModal.module.css';

interface MetricModalProps {
  zona: Zona;
  metric: Metric;
  status: Status;
  statusText: string;
  /** Métricas vecinas en el orden de la lista; null en los extremos. */
  prev: Metric | null;
  next: Metric | null;
  onNavigate: (key: string) => void;
  range: Range;
  onRangeChange: (r: Range) => void;
  onClose: () => void;
}

export function MetricModal({
  zona,
  metric: m,
  status,
  statusText,
  prev,
  next,
  onNavigate,
  range,
  onRangeChange,
  onClose,
}: MetricModalProps) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const titleId = `metric-modal-${m.key}`;

  useEffect(() => {
    // Recuerda quién lo abrió para devolverle el foco al cerrar
    const opener = document.activeElement as HTMLElement | null;
    closeRef.current?.focus();
    // Sin scroll de la página de fondo mientras el modal está abierto
    const overflowPrevio = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = overflowPrevio;
      opener?.focus({ preventScroll: true });
    };
  }, []);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  // En un portal: un ancestro con `transform` (la animación de entrada de la página) haría que
  // `position: fixed` se midiera contra él y no contra la ventana.
  return createPortal(
    <div className={styles.backdrop} onClick={onClose}>
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(e) => e.stopPropagation()}
      >
        <header className={styles.head}>
          <div className={styles.headText}>
            <h2 id={titleId} className={styles.title}>
              {m.label}
            </h2>
            <span className={cx(styles.status, styles[TONO[status]])}>
              <StateGlyph status={status} />
              {statusText}
            </span>
          </div>
          <div className={styles.value}>
            {m.raw == null ? (
              <span className={styles.number}>—</span>
            ) : (
              <>
                <span className={styles.number}>{fmtNum(m.raw, m.spec.dec)}</span>
                <span className={styles.unit}>{m.unit}</span>
              </>
            )}
          </div>
          <button
            ref={closeRef}
            type="button"
            className={styles.iconBtn}
            onClick={onClose}
            aria-label="Cerrar"
          >
            <Icon name="close" size={16} />
          </button>
        </header>

        <div className={styles.body}>
          {m.raw == null ? (
            <span className={styles.barEmpty} />
          ) : (
            <RangeBar value={m.raw} spec={m.spec} />
          )}
          <MetricHistoryChart zona={zona} metric={m} range={range} onRangeChange={onRangeChange} />
        </div>

        {/* Recorrer las métricas sin cerrar el modal */}
        <footer className={styles.foot}>
          <button
            type="button"
            className={styles.navBtn}
            disabled={!prev}
            onClick={() => prev && onNavigate(prev.key)}
          >
            <span className={cx(styles.navIcon, styles.navIconPrev)}>
              <Icon name="chevron-left" size={15} />
            </span>
            <span className={styles.navLabel}>{prev?.label ?? 'Anterior'}</span>
          </button>
          <button
            type="button"
            className={cx(styles.navBtn, styles.navNext)}
            disabled={!next}
            onClick={() => next && onNavigate(next.key)}
          >
            <span className={styles.navLabel}>{next?.label ?? 'Siguiente'}</span>
            <span className={cx(styles.navIcon, styles.navIconNext)}>
              <Icon name="chevron-right" size={15} />
            </span>
          </button>
        </footer>
      </div>
    </div>,
    document.body,
  );
}
