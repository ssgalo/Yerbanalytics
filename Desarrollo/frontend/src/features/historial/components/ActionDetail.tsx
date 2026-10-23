/* Detalle de la acción elegida: lectura, decisión y acción, y el acceso al razonamiento del motor.
   Cabecera y botón quedan fijos; si el contenido es largo, se desplaza sólo el cuerpo. */
import { Button } from '@/components/ui/Button';
import { Chip, type ChipTone } from '@/components/ui/Chip';
import { Glyph, Icon } from '@/components/ui/Icon';
import { cx } from '@/components/ui/estado';
import type { ActionRecord } from '@/types/domain';
import {
  formatDuration,
  formatVolume,
  isZoneWide,
  readingText,
  resultLabel,
  resultTone,
  typeIconPath,
} from '../actionPresentation';
import styles from './ActionDetail.module.css';

const VERDICT_TONE: Record<string, ChipTone> = {
  Efectiva: 'ok',
  'En seguimiento': 'info',
  'Sin efectividad': 'critical',
};

const isStatus = (tone: ChipTone) => tone === 'ok' || tone === 'warning' || tone === 'critical' || tone === 'offline';

interface ActionDetailProps {
  record: ActionRecord | null;
  /** Nombre legible de una regla por su id. */
  ruleLabel: (id: string) => string;
  /** Hay una evaluación del motor que mostrar para esta acción. */
  hasReasoning: boolean;
  onOpenReasoning: () => void;
  /** En celular el detalle ocupa la pantalla: abierto o no. */
  open: boolean;
  onBack: () => void;
}

export function ActionDetail({ record: r, ruleLabel, hasReasoning, onOpenReasoning, open, onBack }: ActionDetailProps) {
  if (!r) {
    return (
      <aside className={cx(styles.card, styles.placeholder)} aria-label="Detalle de la acción">
        Elegí una acción de la tabla para ver su detalle.
      </aside>
    );
  }

  const tone = resultTone(r);
  const zoneWide = isZoneWide(r);
  const facts: [string, string][] = [
    ['Macro-zona', r.zonaName],
    ['Sector', zoneWide ? 'Toda la macro-zona' : r.sectorId],
  ];
  if (r.regla) facts.push(['Regla', ruleLabel(r.regla)]);
  if (r.volumenL != null) facts.push(['Volumen', formatVolume(r.volumenL)]);
  if (r.duracionSeg != null) facts.push(['Duración', formatDuration(r.duracionSeg)]);

  return (
    <aside className={cx(styles.card, open && styles.open)} aria-label="Detalle de la acción">
      <header className={styles.head}>
        <Button variant="ghost" size="sm" className={styles.back} onClick={onBack}>
          <Icon name="chevron-left" size={16} />
          Historial
        </Button>
        <div className={styles.headRow}>
          <span className={styles.type}>
            <Glyph path={typeIconPath(r)} size={16} />
            {r.tipo}
          </span>
          <Chip tone={tone} glyph={isStatus(tone)}>
            {resultLabel(r)}
          </Chip>
        </div>
        <h2 className={styles.title}>{zoneWide ? r.zonaName : r.sectorId}</h2>
        <span className={styles.when}>{r.fecha}</span>
      </header>

      <div className={styles.body}>
        <dl className={styles.facts}>
          {facts.map(([k, v]) => (
            <div key={k}>
              <dt>{k}</dt>
              <dd>{v}</dd>
            </div>
          ))}
        </dl>

        <dl className={styles.chain}>
          <div>
            <dt>Lectura</dt>
            <dd>{readingText(r, ruleLabel)}</dd>
          </div>
          <div>
            <dt>Decisión</dt>
            <dd>{r.decision}</dd>
          </div>
          <div>
            <dt>Acción</dt>
            <dd>{r.accion}</dd>
          </div>
          {r.evo && (
            <div>
              <dt>Seguimiento</dt>
              <dd className={styles.evo}>
                <span className={styles.evoValues}>
                  {r.evo.antes}
                  {r.evo.unit} → {r.evo.ahora}
                  {r.evo.ahora !== '—' ? r.evo.unit : ''}
                </span>
                <Chip tone={VERDICT_TONE[r.evo.verdict] ?? 'line'} glyph={isStatus(VERDICT_TONE[r.evo.verdict] ?? 'line')}>
                  {r.evo.verdict}
                </Chip>
              </dd>
            </div>
          )}
        </dl>
      </div>

      <footer className={styles.foot}>
        {hasReasoning ? (
          <Button className={styles.reasoning} onClick={onOpenReasoning}>
            <Icon name="rules" size={17} />
            Ver razonamiento del motor
          </Button>
        ) : (
          <p className={styles.note}>
            {r.tipo === 'Alerta'
              ? 'Las alertas son de la macro-zona: no tienen una evaluación del motor que mostrar.'
              : 'Esta acción no tiene una evaluación del motor que mostrar.'}
          </p>
        )}
      </footer>
    </aside>
  );
}
