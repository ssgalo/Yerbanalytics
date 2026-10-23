/* Razonamiento del motor de una acción: el DAG casi a pantalla completa. Se cierra con Escape, con la
   X o con un clic fuera del panel. */
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { HistoryGraph } from '@/components/DAGViewer/HistoryGraph';
import { Icon } from '@/components/ui/Icon';
import { Segmented } from '@/components/ui/Seleccion';
import type { ActionRecord, DagSchema } from '@/types/domain';
import { isZoneWide } from '../actionPresentation';
import styles from './ReasoningModal.module.css';

type View = 'all' | 'executed';

const VIEWS: { value: View; label: string }[] = [
  { value: 'all', label: 'Todas las reglas' },
  { value: 'executed', label: 'Sólo ejecutadas' },
];

interface ReasoningModalProps {
  record: ActionRecord;
  schema: DagSchema;
  onClose: () => void;
}

export function ReasoningModal({ record: r, schema, onClose }: ReasoningModalProps) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const [view, setView] = useState<View>('all');

  useEffect(() => {
    closeRef.current?.focus();
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    // Sin scroll de la página de fondo mientras el grafo ocupa la pantalla
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previous;
    };
  }, [onClose]);

  return createPortal(
    <div className={styles.backdrop} onClick={onClose}>
      <div
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="reasoning-title"
        onClick={(e) => e.stopPropagation()}
      >
        <header className={styles.head}>
          <div className={styles.meta}>
            <h2 id="reasoning-title" className={styles.title}>
              Razonamiento del motor
            </h2>
            <span className={styles.sub}>
              {r.tipo} · {isZoneWide(r) ? r.zonaName : r.sectorId} · {r.fecha}
            </span>
          </div>
          <Segmented label="Reglas a mostrar" options={VIEWS} value={view} onChange={setView} />
          <button ref={closeRef} type="button" className={styles.close} onClick={onClose} aria-label="Cerrar">
            <Icon name="close" size={18} />
          </button>
        </header>
        <div className={styles.graph}>
          <HistoryGraph schema={schema} event={r} onlyExecuted={view === 'executed'} />
        </div>
      </div>
    </div>,
    document.body,
  );
}
