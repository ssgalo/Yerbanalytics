/* ============================================================
   Valores sensados de la macro-zona: lo que reportó su nodo testigo,
   que habla por todos sus sectores.

   Cada métrica es una fila: su valor contra las bandas de su rango.
   Al abrirla, su histórico aparece en un modal centrado
   (MetricModal), así la lista no se desplaza ni deja huecos. Van
   agrupadas aparte, sin título visible, las que definen el estado de
   los sectores y las informativas, cuyos rangos todavía no están
   validados (el grupo se nombra sólo para el lector de pantalla).
   ============================================================ */
import { useState } from 'react';
import type { Metric, Range, Status, Zona } from '@/types/domain';
import { Card } from '@/components/ui/Card';
import { Callout } from '@/components/ui/Callout';
import { Icon } from '@/components/ui/Icon';
import { RangeBar } from '@/components/ui/Medidas';
import { StateGlyph } from '@/components/ui/StateGlyph';
import { TONO, cx } from '@/components/ui/estado';
import { fmtNum } from '@/components/ui/rango';
import { MetricModal } from './MetricModal';
import panel from './Panel.module.css';
import styles from './SensingCard.module.css';

interface SensingCardProps {
  zona: Zona;
  /** A todo el ancho: los dos grupos de métricas van lado a lado. */
  wide: boolean;
  className?: string;
}

/** Sin lectura de la métrica: no hay estado que mostrar, se dibuja como sin señal. */
const statusOf = (m: Metric): Status => (m.raw == null ? 'offline' : m.status);

/** Dónde está el valor respecto del ideal, en palabras. */
function statusText(m: Metric): string {
  if (m.raw == null) return 'sin dato';
  if (m.status === 'ok') return 'en el ideal';
  const below = m.raw < m.spec.ideal[0];
  if (m.status === 'warning') return below ? 'por debajo del ideal' : 'por encima del ideal';
  return below ? 'crítico, muy bajo' : 'crítico, muy alto';
}

export function SensingCard({ zona, wide, className }: SensingCardProps) {
  const { lectura, nodo } = zona;
  /* Una métrica abierta por vez en el modal; el rango del histórico se conserva al
     cambiar de métrica. */
  const [openKey, setOpenKey] = useState<string | null>(null);
  const [range, setRange] = useState<Range>('7d');

  const groups = [
    {
      title: 'Definen el estado de los sectores',
      metrics: lectura.metrics.filter((m) => m.spec.afectaEstado),
    },
    {
      title: 'Informativas, con rango provisional',
      metrics: lectura.metrics.filter((m) => !m.spec.afectaEstado),
    },
  ].filter((g) => g.metrics.length > 0);

  /* El modal recorre las métricas en el mismo orden en que se ven en la lista */
  const ordered = groups.flatMap((g) => g.metrics);
  const openIndex = ordered.findIndex((m) => m.key === openKey);
  const openMetric = openIndex >= 0 ? ordered[openIndex] : null;

  return (
    <Card className={cx(panel.panel, className)}>
      <div className={panel.head}>
        <h2 className={panel.title}>Valores sensados</h2>
        <span className={panel.meta}>
          <span>Nodo testigo</span>
          <span className={styles.metaItem}>
            <Icon name="clock" size={13} />
            {lectura.ts == null ? 'sin reportes' : lectura.ago}
          </span>
          <span className={cx(styles.metaItem, nodo.bateriaBaja && styles.metaWarn)}>
            <Icon name="battery" size={13} />
            {nodo.battery != null ? `${nodo.battery} %` : '—'}
            {nodo.bateriaBaja && ' · batería baja'}
          </span>
          <span className={styles.metaItem}>
            <Icon name="signal" size={13} />
            {nodo.signal != null ? `${nodo.signal} dBm` : '—'}
          </span>
        </span>
      </div>

      {/* La lectura vencida es la advertencia más importante del panel: todo lo que
          sigue son valores viejos. */}
      {lectura.stale && (
        <div className={styles.stale}>
          <Callout tone="warn" icon="clock">
            Sin reporte reciente del nodo testigo: los valores son los últimos conocidos, no los
            vigentes.
          </Callout>
        </div>
      )}

      <div className={cx(styles.groups, wide && styles.groupsWide)}>
        {groups.map((g) => (
          <section key={g.title} className={styles.group} aria-label={g.title}>
            <ul className={styles.list}>
              {g.metrics.map((m) => (
                <MetricRow
                  key={m.key}
                  metric={m}
                  selected={openKey === m.key}
                  onOpen={() => setOpenKey(m.key)}
                />
              ))}
            </ul>
          </section>
        ))}
      </div>

      {openMetric && (
        <MetricModal
          zona={zona}
          metric={openMetric}
          status={statusOf(openMetric)}
          statusText={statusText(openMetric)}
          prev={ordered[openIndex - 1] ?? null}
          next={ordered[openIndex + 1] ?? null}
          onNavigate={setOpenKey}
          range={range}
          onRangeChange={setRange}
          onClose={() => setOpenKey(null)}
        />
      )}
    </Card>
  );
}

interface MetricRowProps {
  metric: Metric;
  /** La métrica que se está mostrando en el modal. */
  selected: boolean;
  onOpen: () => void;
}

function MetricRow({ metric: m, selected, onOpen }: MetricRowProps) {
  const status = statusOf(m);
  const tone = TONO[status];

  return (
    <li className={cx(styles.item, selected && styles.itemOpen)}>
      <button type="button" className={styles.row} aria-haspopup="dialog" onClick={onOpen}>
        <span className={styles.name}>
          <span className={styles.label}>{m.label}</span>
          <span className={cx(styles.status, styles[tone])}>
            <StateGlyph status={status} />
            {statusText(m)}
          </span>
        </span>

        <span className={styles.value}>
          {m.raw == null ? (
            <span className={styles.number}>—</span>
          ) : (
            <>
              <span className={styles.number}>{fmtNum(m.raw, m.spec.dec)}</span>
              <span className={styles.unit}>{m.unit}</span>
            </>
          )}
        </span>

        <span className={styles.bar}>
          {m.raw == null ? (
            <span className={styles.barEmpty} />
          ) : (
            <RangeBar value={m.raw} spec={m.spec} />
          )}
        </span>
      </button>
    </li>
  );
}
