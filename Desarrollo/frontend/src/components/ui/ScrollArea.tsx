import { useEffect, useRef, useState, type MouseEvent, type ReactNode, type RefObject } from 'react';
import { Icon } from './Icon';
import { cx } from './estado';
import styles from './ScrollArea.module.css';

type Side = 'left' | 'right' | 'top' | 'bottom';
type Edges = Record<Side, boolean>;

const NONE: Edges = { left: false, right: false, top: false, bottom: false };
const SIDES: Side[] = ['left', 'right', 'top', 'bottom'];

/** Margen en px para no avisar por un desborde de redondeo. */
const SLACK = 2;

/** Rotación del chevrón (apunta hacia abajo) para cada borde. */
const ROTATION: Record<Side, number> = { bottom: 0, left: 90, top: 180, right: 270 };

/**
 * Hacia qué lados queda contenido sin ver. Si el contenido sólo desborda a lo ancho, convierte
 * además la ruedita en desplazamiento horizontal: con una rueda común no hay otra forma de llegar
 * a lo que falta. En los extremos la deja pasar y la página sigue desplazándose como siempre.
 */
function useScrollEdges(ref: RefObject<HTMLElement>): Edges {
  const [edges, setEdges] = useState<Edges>(NONE);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const update = () => {
      const next: Edges = {
        left: el.scrollLeft > SLACK,
        right: el.scrollLeft + el.clientWidth < el.scrollWidth - SLACK,
        top: el.scrollTop > SLACK,
        bottom: el.scrollTop + el.clientHeight < el.scrollHeight - SLACK,
      };
      setEdges((prev) => (SIDES.every((s) => prev[s] === next[s]) ? prev : next));
    };

    const onWheel = (e: WheelEvent) => {
      const overflowsX = el.scrollWidth > el.clientWidth + SLACK;
      const overflowsY = el.scrollHeight > el.clientHeight + SLACK;
      if (!overflowsX || overflowsY || e.shiftKey || Math.abs(e.deltaX) >= Math.abs(e.deltaY)) return;
      const canMove = e.deltaY > 0 ? el.scrollLeft + el.clientWidth < el.scrollWidth - SLACK : el.scrollLeft > SLACK;
      if (!canMove) return;
      e.preventDefault();
      el.scrollLeft += e.deltaY;
    };

    update();
    el.addEventListener('scroll', update, { passive: true });
    el.addEventListener('wheel', onWheel, { passive: false });
    const ro = new ResizeObserver(update);
    ro.observe(el);
    if (el.firstElementChild) ro.observe(el.firstElementChild);
    return () => {
      el.removeEventListener('scroll', update);
      el.removeEventListener('wheel', onWheel);
      ro.disconnect();
    };
  }, [ref]);

  return edges;
}

interface ScrollAreaProps {
  children: ReactNode;
  /** Clase del marco: ahí se fijan `--scroll-max-h`, `--scroll-fade` y `--scroll-fade-bg`. */
  className?: string;
}

/**
 * Área desplazable sin barra de desplazamiento, para una grilla que no entra entera. Cada borde
 * detrás del cual queda contenido se funde hacia el fondo y muestra una flecha chica que avanza
 * casi una pantalla hacia ese lado. La ruedita también desplaza (a lo ancho si sólo sobran
 * columnas), y el teclado llega solo: el foco trae a la vista lo enfocado.
 */
export function ScrollArea({ children, className }: ScrollAreaProps) {
  const viewportRef = useRef<HTMLDivElement>(null);
  const edges = useScrollEdges(viewportRef);

  function step(side: Side, e: MouseEvent) {
    /* La flecha puede vivir dentro de algo clicable (la bandeja del plano lleva al detalle). */
    e.stopPropagation();
    e.preventDefault();
    const el = viewportRef.current;
    if (!el) return;
    const horizontal = side === 'left' || side === 'right';
    const amount = (horizontal ? el.clientWidth : el.clientHeight) * 0.8;
    const sign = side === 'left' || side === 'top' ? -1 : 1;
    const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
    el.scrollBy({ [horizontal ? 'left' : 'top']: sign * amount, behavior });
  }

  return (
    <div className={cx(styles.frame, className)}>
      <div ref={viewportRef} className={styles.viewport}>
        {children}
      </div>

      {/* Ayudas sólo para el mouse: con teclado, el foco ya trae a la vista lo que falta */}
      {SIDES.filter((side) => edges[side]).map((side) => (
        <span key={side} className={cx(styles.fade, styles[side])} aria-hidden="true">
          <button
            type="button"
            className={styles.arrow}
            tabIndex={-1}
            onClick={(e) => step(side, e)}
          >
            <Icon name="chevron-down" size={14} strokeWidth={2.4} style={{ transform: `rotate(${ROTATION[side]}deg)` }} />
          </button>
        </span>
      ))}
    </div>
  );
}
