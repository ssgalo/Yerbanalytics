/* ============================================================
   Preview interactivo de la topología (HU-18 CA-01). Replica el aspecto del panel
   general con celdas placeholder y permite ajustar la disposición por fila arrastrando
   las solapas laterales (estilo pestaña): la solapa de una macro-zona cambia los
   sectores por fila; la solapa del borde derecho de la box cambia las macro-zonas por
   fila. Todas las macro-zonas se muestran; si no entran, la box hace scroll.

   Animaciones: las cards y celdas que se agregan entran con un fundido (CSS); al
   redimensionar, las que ya existen se deslizan a su nueva posición con una animación
   FLIP (Web Animations API), de modo que el reacomodo no sea brusco.
   ============================================================ */
import { useLayoutEffect, useRef, type PointerEvent as ReactPointerEvent } from 'react';
import styles from './TopologiaPreview.module.css';

interface TopologiaPreviewProps {
  macroZonas: number;
  sectoresPorMacroZona: number;
  macroZonasPorFila: number;
  sectoresPorFila: number;
  onChange: (layout: { macroZonasPorFila: number; sectoresPorFila: number }) => void;
}

/** Tope de celdas por card para mantener liviano el dibujo del heatmap. */
const MAX_CELLS_PER_CARD = 120;

/** Geometría de las celdas/cards del preview (px). Debe coincidir con el CSS. */
const SECTOR_CELL = 13;
const SECTOR_GAP = 3;
const CARD_GAP = 12;

/** Topes de elementos a animar con FLIP (arriba de eso, el reacomodo es instantáneo). */
const MAX_FLIP_CARDS = 60;
const MAX_FLIP_CELLS = 2000;

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

type Pos = { x: number; y: number };

/**
 * Animación FLIP: compara la posición previa (guardada en `store`) con la actual y, para
 * cada elemento que se movió, lo lleva visualmente desde su posición vieja a la nueva.
 * `mode` 'viewport' usa coordenadas absolutas (cards); 'offset' usa la posición relativa
 * a la card (celdas), para no duplicar el movimiento del contenedor.
 */
function runFlip(
  root: HTMLElement | null,
  attr: string,
  mode: 'viewport' | 'offset',
  maxCount: number,
  store: { current: Map<string, Pos> },
  skipAnimation: boolean,
) {
  if (!root) return;
  const nodes = Array.from(root.querySelectorAll<HTMLElement>(`[${attr}]`));
  const next = new Map<string, Pos>();
  // Durante un arrastre el reacomodo debe ser inmediato (la solapa es manipulación
  // directa); animar cada paso encimaría animaciones y haría temblar las cards. Igual
  // registramos las posiciones para que el próximo cambio discreto anime bien.
  const canAnimate = !skipAnimation && nodes.length <= maxCount && !prefersReducedMotion();

  for (const node of nodes) {
    const id = node.getAttribute(attr);
    if (!id) continue;
    let pos: Pos;
    if (mode === 'viewport') {
      const r = node.getBoundingClientRect();
      pos = { x: r.left, y: r.top };
    } else {
      pos = { x: node.offsetLeft, y: node.offsetTop };
    }
    next.set(id, pos);

    if (canAnimate) {
      const first = store.current.get(id);
      if (first) {
        const dx = first.x - pos.x;
        const dy = first.y - pos.y;
        if (dx || dy) {
          node.animate(
            [{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'translate(0, 0)' }],
            { duration: 240, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' },
          );
        }
      }
    }
  }
  store.current = next;
}

export function TopologiaPreview({
  macroZonas,
  sectoresPorMacroZona,
  macroZonasPorFila,
  sectoresPorFila,
  onChange,
}: TopologiaPreviewProps) {
  const zonaGridRef = useRef<HTMLDivElement>(null);
  const heatmapRef = useRef<HTMLDivElement>(null);
  const draggingSector = useRef(false);
  const draggingMacro = useRef(false);
  const cardPositions = useRef<Map<string, Pos>>(new Map());
  const cellPositions = useRef<Map<string, Pos>>(new Map());

  // Se muestran TODAS las macro-zonas (con scroll si no entran en la box).
  const renderedMacro = Math.max(1, macroZonas);
  const cellsPerCard = Math.min(Math.max(1, sectoresPorMacroZona), MAX_CELLS_PER_CARD);
  // Columnas a dibujar: la disposición elegida, acotada a las celdas visibles del preview.
  const displaySecCols = clamp(sectoresPorFila, 1, cellsPerCard);
  const displayMacroCols = clamp(macroZonasPorFila, 1, renderedMacro);

  // FLIP de las cards al cambiar las macro-zonas por fila (o su cantidad).
  useLayoutEffect(() => {
    runFlip(
      zonaGridRef.current,
      'data-flip-card',
      'viewport',
      MAX_FLIP_CARDS,
      cardPositions,
      draggingMacro.current,
    );
  }, [displayMacroCols, renderedMacro]);

  // FLIP de las celdas al cambiar los sectores por fila (o su cantidad). Tampoco anima
  // mientras se arrastra la solapa de sectores.
  useLayoutEffect(() => {
    runFlip(
      zonaGridRef.current,
      'data-flip-cell',
      'offset',
      MAX_FLIP_CELLS,
      cellPositions,
      draggingSector.current,
    );
  }, [displaySecCols, cellsPerCard, displayMacroCols]);

  // --- Resize de sectores por fila (solapa de una macro-zona) ---
  const onSectorMove = (e: ReactPointerEvent) => {
    if (!draggingSector.current || !heatmapRef.current) return;
    const rect = heatmapRef.current.getBoundingClientRect();
    const pitch = SECTOR_CELL + SECTOR_GAP;
    const cols = clamp(Math.round((e.clientX - rect.left) / pitch), 1, sectoresPorMacroZona);
    if (cols !== sectoresPorFila) onChange({ macroZonasPorFila, sectoresPorFila: cols });
  };

  // --- Resize de macro-zonas por fila (solapa del borde derecho de la box) ---
  const onMacroMove = (e: ReactPointerEvent) => {
    if (!draggingMacro.current || !zonaGridRef.current) return;
    const rect = zonaGridRef.current.getBoundingClientRect();
    const pitch = rect.width / displayMacroCols + CARD_GAP;
    const cols = clamp(Math.round((e.clientX - rect.left) / pitch), 1, macroZonas);
    if (cols !== macroZonasPorFila) onChange({ macroZonasPorFila: cols, sectoresPorFila });
  };

  const startDrag = (ref: typeof draggingSector) => (e: ReactPointerEvent) => {
    ref.current = true;
    e.currentTarget.setPointerCapture(e.pointerId);
    e.preventDefault();
  };
  const endDrag = (ref: typeof draggingSector) => (e: ReactPointerEvent) => {
    ref.current = false;
    if (e.currentTarget.hasPointerCapture(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId);
    }
  };

  const cards = Array.from({ length: renderedMacro }, (_, i) => i);
  const cells = Array.from({ length: cellsPerCard }, (_, i) => i);

  return (
    <div className={styles.wrapper}>
      <div className={styles.previewBox}>
        <div className={styles.scrollArea}>
          <div
            ref={zonaGridRef}
            className={styles.zonaGrid}
            style={{ gridTemplateColumns: `repeat(${displayMacroCols}, max-content)` }}
          >
            {cards.map((ci) => (
              <div key={ci} className={styles.zonaCard} data-flip-card={`c${ci}`}>
                <div className={styles.zonaHeader}>
                  <span className={styles.zonaName}>MZ-{ci + 1}</span>
                </div>
                <div
                  ref={ci === 0 ? heatmapRef : undefined}
                  className={styles.heatmap}
                  style={{ gridTemplateColumns: `repeat(${displaySecCols}, ${SECTOR_CELL}px)` }}
                >
                  {cells.map((si) => (
                    <span key={si} className={styles.cell} data-flip-cell={`c${ci}-${si}`} />
                  ))}
                </div>
                {ci === 0 && (
                  <span
                    className={styles.gripSector}
                    title="Arrastrá para cambiar los sectores por fila"
                    onPointerDown={startDrag(draggingSector)}
                    onPointerMove={onSectorMove}
                    onPointerUp={endDrag(draggingSector)}
                    onPointerCancel={endDrag(draggingSector)}
                  >
                    <span className={styles.gripDots} />
                  </span>
                )}
              </div>
            ))}
          </div>
        </div>
        <span
          className={styles.gripMacro}
          title="Arrastrá para cambiar las macro-zonas por fila"
          onPointerDown={startDrag(draggingMacro)}
          onPointerMove={onMacroMove}
          onPointerUp={endDrag(draggingMacro)}
          onPointerCancel={endDrag(draggingMacro)}
        >
          <span className={styles.gripDots} />
        </span>
      </div>
    </div>
  );
}
