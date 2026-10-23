import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { cx } from './estado';
import styles from './Popover.module.css';

interface PopoverProps {
  /** Elemento al que se ancla (el botón que lo abrió). Recibe el foco al cerrar. */
  anchor: HTMLElement | null;
  onClose: () => void;
  /** Nombre accesible del diálogo. */
  label: string;
  children: ReactNode;
  /** Ancho en px. */
  width?: number;
  /** Se alinea al borde derecho del ancla (botones del borde derecho). Si no entra, lo hace solo. */
  alignEnd?: boolean;
  /** `alertdialog` para una confirmación destructiva. */
  role?: 'dialog' | 'alertdialog';
  /**
   * Modal (confirmaciones destructivas): `aria-modal`, el Tab no sale del panel y un clic afuera no lo cierra.
   * Escape sigue llamando a `onClose`, que decide si cierra (p. ej. no, con un pedido en vuelo) y a quién devuelve el foco.
   */
  modal?: boolean;
  /** Sin relleno: el contenido trae el suyo (p. ej. los filtros de columna). */
  flush?: boolean;
  className?: string;
}

const GAP = 6;
const MARGIN = 8;
const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/** Bajo el ancla; si no entra abajo y hay más lugar arriba, arriba. Nunca fuera de la pantalla. */
function place(anchor: HTMLElement, panel: HTMLElement, width: number, alignEnd: boolean): CSSProperties {
  const a = anchor.getBoundingClientRect();
  const h = panel.offsetHeight;
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const below = vh - a.bottom - GAP - MARGIN;
  const above = a.top - GAP - MARGIN;
  const up = h > below && above > below;
  const end = alignEnd || a.left + width > vw - MARGIN;
  const left = end ? Math.max(MARGIN, a.right - width) : Math.min(a.left, vw - width - MARGIN);
  return {
    left,
    width,
    maxHeight: Math.max(160, up ? above : below),
    ...(up ? { bottom: vh - a.top + GAP } : { top: a.bottom + GAP }),
  };
}

/**
 * Panel flotante anclado a un elemento: se abre encima del contenido, sin correr nada de lugar.
 * Va en un portal, fijo a la pantalla, así no lo recorta una tabla con scroll ni una tarjeta con
 * `overflow: hidden`. Se cierra con Escape o con un clic afuera; al abrir enfoca su primer campo.
 */
export function Popover({
  anchor,
  onClose,
  label,
  children,
  width = 320,
  alignEnd = false,
  role = 'dialog',
  modal = false,
  flush = false,
  className,
}: PopoverProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<CSSProperties | null>(null);
  const closeRef = useRef(onClose);
  useEffect(() => {
    closeRef.current = onClose;
  });

  // Posición, y de nuevo si se desplaza la página o la tabla, o cambia el alto del contenido.
  useLayoutEffect(() => {
    const panel = panelRef.current;
    if (!anchor || !panel) return;
    const update = () => setPosition(place(anchor, panel, width, alignEnd));
    update();
    window.addEventListener('scroll', update, true);
    window.addEventListener('resize', update);
    const observer = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(update) : null;
    observer?.observe(panel);
    return () => {
      window.removeEventListener('scroll', update, true);
      window.removeEventListener('resize', update);
      observer?.disconnect();
    };
  }, [anchor, width, alignEnd]);

  useEffect(() => {
    panelRef.current
      ?.querySelector<HTMLElement>('input:not([disabled]), select:not([disabled]), textarea, button:not([disabled])')
      ?.focus();
    function onPointer(e: MouseEvent) {
      const t = e.target as Node;
      if (modal || panelRef.current?.contains(t) || anchor?.contains(t)) return;
      closeRef.current();
    }
    function trap(e: KeyboardEvent) {
      const panel = panelRef.current;
      if (!panel) return;
      const items = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (items.length === 0) {
        e.preventDefault();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      const active = document.activeElement;
      if (!panel.contains(active)) {
        e.preventDefault();
        (e.shiftKey ? last : first).focus();
      } else if (e.shiftKey && active === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && active === last) {
        e.preventDefault();
        first.focus();
      }
    }
    function onKey(e: KeyboardEvent) {
      if (modal && e.key === 'Tab') {
        trap(e);
        return;
      }
      if (e.key !== 'Escape') return;
      closeRef.current();
      if (!modal) anchor?.focus();
    }
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [anchor, modal]);

  return createPortal(
    <div
      ref={panelRef}
      role={role}
      aria-modal={modal ? true : undefined}
      aria-label={label}
      className={cx(styles.popover, !flush && styles.padded, className)}
      style={position ?? { visibility: 'hidden', width }}
    >
      {children}
    </div>,
    document.body,
  );
}
