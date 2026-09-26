import { useMemo, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Glyph, Icon } from '@/components/ui/Icon';
import { RuleGraph } from '@/components/DAGViewer/RuleGraph';
import type { ActionRecord, Evolution, DagSchema } from '@/types/domain';
import styles from './HistorialTimeline.module.css';

interface HistorialTimelineProps {
  records: ActionRecord[];
  schema: DagSchema | null;
  schemaLoading: boolean;
}

interface SectorGroup {
  sectorId: string;
  zonaName: string;
  actions: ActionRecord[];
}

interface ZonaGroup {
  zonaName: string;
  sectors: Record<string, SectorGroup>;
}

interface CycleGroup {
  fecha: string;
  ts: number;
  zonas: Record<string, ZonaGroup>;
}

export function HistorialTimeline({ records, schema, schemaLoading }: HistorialTimelineProps) {
  const [openCycles, setOpenCycles] = useState<Record<string, boolean>>({});
  const toggleCycle = (fecha: string) => setOpenCycles((o) => ({ ...o, [fecha]: !o[fecha] }));

  const [openZonas, setOpenZonas] = useState<Record<string, boolean>>({});
  const toggleZona = (id: string) => setOpenZonas((o) => ({ ...o, [id]: !o[id] }));

  const [openSectors, setOpenSectors] = useState<Record<string, boolean>>({});
  const toggleSector = (id: string) => setOpenSectors((o) => ({ ...o, [id]: !o[id] }));

  const [openDag, setOpenDag] = useState<Record<string, boolean>>({});
  const toggleDag = (id: string) => setOpenDag((o) => ({ ...o, [id]: !o[id] }));

  const groupedCycles = useMemo(() => {
    const cycles: Record<string, CycleGroup> = {};
    const order: string[] = [];

    records.forEach((r) => {
      if (!cycles[r.fecha]) {
        cycles[r.fecha] = { fecha: r.fecha, ts: r.ts, zonas: {} };
        order.push(r.fecha);
      }
      
      const cycle = cycles[r.fecha];
      if (!cycle.zonas[r.zonaName]) {
        cycle.zonas[r.zonaName] = { zonaName: r.zonaName, sectors: {} };
      }
      
      const zona = cycle.zonas[r.zonaName];
      if (!zona.sectors[r.sectorId]) {
        zona.sectors[r.sectorId] = {
          sectorId: r.sectorId,
          zonaName: r.zonaName,
          actions: [],
        };
      }
      
      zona.sectors[r.sectorId].actions.push(r);
    });

    return order.map((f) => cycles[f]);
  }, [records]);

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
      <h3 className={styles.title}>Historial de acciones por Ciclo</h3>
      <div className={styles.subtitle}>
        Evaluaciones agrupadas por fecha y hora · Clic en un ciclo para ver los sectores · Clic en un sector para ver el DAG
      </div>

      <div className={styles.feed}>
        {groupedCycles.map((cycle, cycleIndex) => {
          const isCycleOpen = !!openCycles[cycle.fecha];
          const zonaList = Object.values(cycle.zonas).sort((a, b) => a.zonaName.localeCompare(b.zonaName));
          
          let totalSectorsCycle = 0;
          let totalDecisionsCycle = 0;
          zonaList.forEach(z => {
            const sectors = Object.values(z.sectors);
            totalSectorsCycle += sectors.length;
            totalDecisionsCycle += sectors.reduce((acc, s) => acc + s.actions.length, 0);
          });

          return (
            <div key={cycle.fecha} className={styles.cycleBlock}>
              <button
                type="button"
                className={styles.cycleHeader}
                onClick={() => toggleCycle(cycle.fecha)}
                aria-expanded={isCycleOpen}
              >
                <Icon name="calendar" size={18} stroke="var(--ink)" />
                <span className={styles.cycleTitle}>Ciclo {cycle.fecha}</span>
                <span className={styles.cycleCount}>
                  {totalSectorsCycle} sector{totalSectorsCycle !== 1 ? 'es' : ''} evaluados · {totalDecisionsCycle} decisión{totalDecisionsCycle !== 1 ? 'es' : ''}
                </span>
                <span className={styles.spacer} />
                <Icon
                  name="chevron-down"
                  size={18}
                  stroke="var(--faint)"
                  style={{ transform: isCycleOpen ? 'rotate(180deg)' : 'none', transition: 'transform .2s' }}
                />
              </button>

              {isCycleOpen && (
                <div className={styles.zonasContainer} style={{ paddingLeft: '16px' }}>
                  {zonaList.map((zona) => {
                    const zonaId = `${cycle.fecha}_${zona.zonaName}`;
                    const isZonaOpen = !!openZonas[zonaId];
                    const sectorList = Object.values(zona.sectors).sort((a, b) => a.sectorId.localeCompare(b.sectorId));
                    const totalDecisionsZona = sectorList.reduce((acc, s) => acc + s.actions.length, 0);

                    return (
                      <div key={zonaId} className={styles.zonaBlock} style={{ marginBottom: '8px', borderLeft: '2px solid var(--border)', paddingLeft: '12px' }}>
                        <button
                          type="button"
                          className={styles.cycleHeader}
                          onClick={() => toggleZona(zonaId)}
                          style={{ background: 'var(--surface-sunken)', borderRadius: '6px' }}
                        >
                          <Icon name="map-pin" size={16} stroke="var(--ink)" />
                          <span className={styles.cycleTitle} style={{ fontSize: '13px' }}>{zona.zonaName}</span>
                          <span className={styles.cycleCount}>
                            {sectorList.length} sector{sectorList.length !== 1 ? 'es' : ''} evaluados · {totalDecisionsZona} decisión{totalDecisionsZona !== 1 ? 'es' : ''}
                          </span>
                          <span className={styles.spacer} />
                          <Icon
                            name="chevron-down"
                            size={16}
                            stroke="var(--faint)"
                            style={{ transform: isZonaOpen ? 'rotate(180deg)' : 'none', transition: 'transform .2s' }}
                          />
                        </button>

                        {isZonaOpen && (
                          <div className={styles.sectorsContainer} style={{ marginTop: '8px' }}>
                            {sectorList.map((sector) => {
                              const secId = `${cycle.fecha}_${sector.sectorId}`;
                              const isSectorOpen = !!openSectors[secId];

                              return (
                                <div key={secId} className={styles.sectorBlock}>
                                  <button
                                    type="button"
                                    className={styles.sectorHeader}
                                    onClick={() => toggleSector(secId)}
                                  >
                                    <span className={styles.sectorName}>{sector.sectorId}</span>
                                    <span className={styles.spacer} />
                                    <div className={styles.badges}>
                                      {sector.actions.map((r) => (
                                        <Badge key={r.id} soft={r.resSoft} ink={r.resInk} style={{ fontSize: '10.5px', fontWeight: 700, padding: '2px 6px' }}>
                                          {r.tipo}: {r.res}
                                        </Badge>
                                      ))}
                                    </div>
                                    <Icon
                                      name="arrow-right"
                                      size={16}
                                      stroke="var(--faint)"
                                      style={{ transform: isSectorOpen ? 'rotate(90deg)' : 'none', transition: 'transform .15s', marginLeft: 12 }}
                                    />
                                  </button>

                                  {isSectorOpen && (
                                    <div className={styles.sectorContent}>
                                      {/* Boton para ver el DAG del motor de reglas */}
                                      <button
                                        type="button"
                                        className={styles.dagToggleBtn}
                                        onClick={() => toggleDag(secId)}
                                        aria-expanded={!!openDag[secId]}
                                      >
                                        <span className={styles.dagToggleIcon}>{openDag[secId] ? '▲' : '▼'}</span>
                                        {openDag[secId]
                                          ? 'Ocultar razonamiento del motor'
                                          : '🧠 Ver razonamiento del motor — cómo se tomó esta decisión'}
                                      </button>

                                      {openDag[secId] && (
                                        <div className={styles.dagContainer}>
                                          <div className={styles.dagHeader}>Pipeline de decisión automática</div>
                                          {schemaLoading ? (
                                            <div className={styles.dagLoading}>Cargando motor de reglas…</div>
                                          ) : schema ? (
                                            <RuleGraph schema={schema} activeEvents={sector.actions} />
                                          ) : (
                                            <div className={styles.dagError}>Error al cargar el motor</div>
                                          )}
                                        </div>
                                      )}

                                      <div className={styles.actionList}>
                                        {sector.actions.map((r) => (
                                          <div key={r.id} className={styles.actionDetail}>
                                            <div className={styles.actionHeader}>
                                              <span className={styles.iconWrap} style={{ background: r.tint, color: r.ink }}>
                                                <Glyph path={r.path} stroke="currentColor" size={14} />
                                              </span>
                                              <span style={{ fontWeight: 600 }}>{r.tipo}</span>
                                            </div>
                                            <Chain 
                                              label="Lectura / diagnóstico" 
                                              text={r.lectura.replace(/Ciclo de evaluaci[oó]n:\s*(\w+)\.?/i, (match, ruleName) => {
                                                const node = schema?.nodes.find((n) => n.id === ruleName);
                                                return `Evaluando: ${node ? node.label : ruleName}`;
                                              })} 
                                            />
                                            <Chain label="Decisión del motor" text={r.decision} />
                                            <Chain label="Acción ejecutada" text={r.accion} />
                                            {r.evo && <EvoBlock evo={r.evo} />}
                                          </div>
                                        ))}
                                      </div>
                                    </div>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
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
