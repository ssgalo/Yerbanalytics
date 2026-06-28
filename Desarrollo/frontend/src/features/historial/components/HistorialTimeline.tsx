/* Timeline global del historial de acciones. Cada entrada expande su cadena de
   justificación (lectura → decisión → acción) y el seguimiento post-acción.
   Vista de solo lectura: no expone edición ni borrado (HU-11 CA-03). */
import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Glyph, Icon } from '@/components/ui/Icon';
import type { ActionRecord, Evolution } from '@/types/domain';
import styles from './HistorialTimeline.module.css';

interface HistorialTimelineProps {
  records: ActionRecord[];
}

export function HistorialTimeline({ records }: HistorialTimelineProps) {
  const [open, setOpen] = useState<Record<string, boolean>>({});
  const toggle = (id: string) => setOpen((o) => ({ ...o, [id]: !o[id] }));

  if (records.length === 0) {
    return (
      <Card className={styles.card}>
        <div className={styles.empty}>
          No hay acciones registradas para los criterios seleccionados.
        </div>
      </Card>
    );
  }

  return (
    <Card className={styles.card}>
      <h3 className={styles.title}>Historial de acciones</h3>
      <div className={styles.subtitle}>
        Registro inalterable · lectura → decisión → acción ejecutada
      </div>

      <div className={styles.feed}>
        {records.map((r, i) => {
          const isOpen = !!open[r.id];
          return (
            <div key={r.id} className={styles.entry}>
              {/* Columna ícono + conector */}
              <div className={styles.timeline}>
                <span className={styles.iconWrap} style={{ background: r.tint, color: r.ink }}>
                  <Glyph path={r.path} stroke="currentColor" size={16} />
                </span>
                {i < records.length - 1 && <span className={styles.connector} />}
              </div>

              {/* Cuerpo */}
              <div className={styles.body}>
                <button
                  type="button"
                  className={styles.header}
                  onClick={() => toggle(r.id)}
                  aria-expanded={isOpen}
                >
                  <span className={styles.tipo}>{r.tipo}</span>
                  <span className={styles.sector}>{r.sectorId}</span>
                  <span className={styles.zona}>· {r.zonaName}</span>
                  <Badge soft={r.resSoft} ink={r.resInk} style={{ fontSize: '10.5px', fontWeight: 700, padding: '1px 8px' }}>
                    {r.res}
                  </Badge>
                  <span className={styles.spacer} />
                  <span className={styles.time}>{r.time}</span>
                  <Icon
                    name="arrow-right"
                    size={15}
                    stroke="var(--faint)"
                    style={{ transform: isOpen ? 'rotate(90deg)' : 'none', transition: 'transform .15s' }}
                  />
                </button>

                <div className={styles.accion}>{r.accion}</div>

                {isOpen && (
                  <div className={styles.detail}>
                    <Chain label="Lectura / diagnóstico" text={r.lectura} />
                    <Chain label="Decisión del motor" text={r.decision} />
                    <Chain label="Acción ejecutada" text={r.accion} />
                    {r.evo && <EvoBlock evo={r.evo} />}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </Card>
  );
}

function Chain({ label, text }: { label: string; text: string }) {
  return (
    <div className={styles.chainRow}>
      <div className={styles.chainLabel}>{label}</div>
      <div className={styles.chainText}>{text}</div>
    </div>
  );
}

function EvoBlock({ evo }: { evo: Evolution }) {
  return (
    <div className={styles.evo}>
      <div className={styles.evoLatencia}>
        Seguimiento post-acción · latencia {evo.latencia}
      </div>
      <div className={styles.evoRow}>
        <div className={styles.evoBox}>
          <div className={styles.evoBoxLabel}>Antes</div>
          <div className={styles.evoBoxValue}>
            {evo.antes}
            {evo.unit}
          </div>
        </div>
        <Icon name="arrow-right" size={18} stroke="var(--faint)" strokeWidth={2} />
        <div className={`${styles.evoBox} ${styles.evoBoxNow}`}>
          <div className={styles.evoBoxLabel}>Ahora</div>
          <div className={styles.evoBoxValue}>
            {evo.ahora}
            {evo.ahora !== '—' ? evo.unit : ''}
          </div>
        </div>
        <span className={styles.evoDelta}>
          Delta {evo.delta}
          {evo.delta !== '—' ? evo.unit : ''}
        </span>
        <Badge soft={evo.vSoft} ink={evo.vInk} style={{ fontSize: '11.5px', fontWeight: 700, padding: '2px 10px' }}>
          {evo.verdict}
        </Badge>
      </div>
    </div>
  );
}
