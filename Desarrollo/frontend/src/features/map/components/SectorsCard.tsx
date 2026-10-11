/* ============================================================
   Sectores de la macro-zona, en su disposición física: tantos por
   fila como diga la topología. Cada celda lleva la forma de su estado
   y abre el detalle del sector.

   Las celdas tienen un lado mínimo para que el número siempre entre.
   Si la grilla no cabe, se desplaza dentro de la bandeja (la ruedita la
   mueve a lo ancho cuando sobran columnas) y un fade con una flecha en
   cada borde avisa que quedan sectores por ver de ese lado.
   ============================================================ */
import type { CSSProperties } from 'react';
import type { Status, Zona } from '@/types/domain';
import { Card } from '@/components/ui/Card';
import { StateGlyph } from '@/components/ui/StateGlyph';
import { Bandeja, GridCell } from '@/components/ui/SectorCell';
import { ScrollArea } from '@/components/ui/ScrollArea';
import { cx } from '@/components/ui/estado';
import panel from './Panel.module.css';
import styles from './SectorsCard.module.css';

const COUNTS: { status: Status; one: string; many: string }[] = [
  { status: 'ok', one: 'saludable', many: 'saludables' },
  { status: 'warning', one: 'en observación', many: 'en observación' },
  { status: 'critical', one: 'crítico', many: 'críticos' },
  { status: 'offline', one: 'sin señal', many: 'sin señal' },
];

interface SectorsCardProps {
  zona: Zona;
  /** Sectores por fila, ya acotado a la cantidad de sectores. */
  columns: number;
  /** Macro-zona más ancha que alta: la grilla se estira a todo el ancho. */
  wide: boolean;
  onSectorClick: (sectorId: string) => void;
  className?: string;
}

export function SectorsCard({ zona, columns, wide, onSectorClick, className }: SectorsCardProps) {
  const counts = COUNTS.map((c) => ({ ...c, n: zona.sectors.filter((s) => s.status === c.status).length }));

  return (
    <Card className={cx(panel.panel, className)}>
      <div className={cx(panel.head, styles.head)}>
        <h2 className={panel.title}>Sectores</h2>
        <span className={panel.meta}>
          {counts
            .filter((c) => c.n > 0)
            .map((c) => (
              <span key={c.status} className={styles.count}>
                <StateGlyph status={c.status} />
                <b>{c.n}</b> {c.n === 1 ? c.one : c.many}
              </span>
            ))}
        </span>
      </div>

      <Bandeja className={cx(styles.tray, wide && styles.trayWide)}>
        <ScrollArea className={styles.scroll}>
          <div className={styles.grid} style={{ '--columns': columns } as CSSProperties}>
            {zona.sectors.map((s) => (
              <GridCell
                key={s.id}
                status={s.status}
                numero={s.n}
                label={`Sector ${s.id}`}
                onClick={() => onSectorClick(s.id)}
              />
            ))}
          </div>
        </ScrollArea>
      </Bandeja>
    </Card>
  );
}
