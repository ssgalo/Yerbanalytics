import { describe, expect, it } from 'vitest';
import { buildRuleSchema } from '@/data/mock/reglasMock';
import type { ActionRecord } from '@/types/domain';
import { decidingRule, historyGraph, orthogonalPath } from './historyGraphModel';

const schema = buildRuleSchema();

const event = (e: Partial<ActionRecord> = {}): ActionRecord => ({
  id: 'HE-1',
  sectorId: 'MZ-1-001',
  zonaName: 'MZ-1',
  tipo: 'Riego',
  time: 'hace 5 min',
  ts: 1,
  fecha: '03/10 10:05',
  lectura: '',
  decision: '',
  accion: '',
  res: 'Efectiva',
  resSoft: '',
  resInk: '',
  sev: '—',
  tint: '',
  ink: '',
  path: 'M0 0',
  evo: null,
  ...e,
});

const stateOf = (g: ReturnType<typeof historyGraph>, id: string) => g.nodes.find((n) => n.id === id)?.state;

/** El esquema de hoy con estas reglas apagadas (como las informa `/api/rules/schema`: `activa` es el estado efectivo). */
const apagadasHoy = (...ids: string[]) => {
  const hoy = structuredClone(schema);
  for (const n of hoy.nodes) if (ids.includes(n.id)) n.activa = false;
  return hoy;
};
const idsDe = (g: ReturnType<typeof historyGraph>) => g.nodes.map((n) => n.id);

describe('historyGraph', () => {
  it('un evento sin `reglasInactivas` (anterior al registro) no dibuja las retiradas de fábrica', () => {
    const ids = idsDe(historyGraph(schema, event({ regla: 'RiegoPorDeficitRule' })));

    expect(ids).toContain('RiegoPorDeficitRule');
    expect(ids).not.toContain('SupplyRule');
    expect(ids).not.toContain('DailyDoseLimitRule');
  });

  it('una regla apagada hoy que estaba activa entonces se dibuja como ejecutada', () => {
    const hoy = apagadasHoy('PosponerPorLluviaRule');

    const g = historyGraph(hoy, event({ regla: 'RiegoPorDeficitRule', reglasInactivas: [] }));

    expect(stateOf(g, 'PosponerPorLluviaRule')).toBe('executed');
  });

  it('también en un evento viejo: lo que cuenta es la fábrica, no el estado de hoy', () => {
    const hoy = apagadasHoy('PosponerPorLluviaRule');

    const g = historyGraph(hoy, event({ regla: 'RiegoPorDeficitRule', reglasInactivas: null }));

    expect(stateOf(g, 'PosponerPorLluviaRule')).toBe('executed');
  });

  it('una regla que estaba apagada al decidir no se dibuja, aunque hoy esté prendida', () => {
    const g = historyGraph(schema, event({ regla: 'RiegoPorDeficitRule', reglasInactivas: ['FueraDeVentanaRiegoRule'] }));

    expect(idsDe(g)).not.toContain('FueraDeVentanaRiegoRule');
    expect(idsDe(g)).toContain('PausaTrasAplicacionRule');
  });

  it('una retirada que estaba prendida entonces (reglasInactivas vacío) sí se dibuja', () => {
    const g = historyGraph(schema, event({ tipo: 'Insumo', regla: 'SupplyRule', reglasInactivas: [] }));

    expect(stateOf(g, 'SupplyRule')).toBe('decided');
  });

  it('un riego por R-02: las globales y las de riego hasta R-02 se ejecutaron, R-02 decidió, el resto no', () => {
    const g = historyGraph(schema, event({ regla: 'DeficitCriticoRule' }));

    expect(stateOf(g, 'ManualLockRule')).toBe('executed');
    expect(stateOf(g, 'SustratoSaturadoRule')).toBe('executed');
    expect(stateOf(g, 'DeficitCriticoRule')).toBe('decided');
    expect(stateOf(g, 'RiegoPorDeficitRule')).toBe('skipped');
    expect(stateOf(g, 'ShadingRule')).toBe('skipped');
    expect(g.nodes.find((n) => n.id === 'success-RIEGO')).toMatchObject({ label: 'Riego efectuado', state: 'executed' });
  });

  it('un riego pospuesto no llega al cierre de la rama', () => {
    const g = historyGraph(schema, event({ regla: 'PosponerPorLluviaRule', res: 'Pospuesta' }));

    expect(stateOf(g, 'PosponerPorLluviaRule')).toBe('decided');
    expect(g.nodes.find((n) => n.id === 'success-RIEGO')).toMatchObject({ label: 'No se regó', state: 'skipped' });
  });

  it('un bloqueo global deja sin ejecutar las globales siguientes y todas las ramas', () => {
    const g = historyGraph(schema, event({ regla: 'HydraulicFaultRule', res: 'Abortada' }));

    expect(stateOf(g, 'ManualLockRule')).toBe('executed');
    expect(stateOf(g, 'HydraulicFaultRule')).toBe('decided');
    expect(stateOf(g, 'StaleSensorRule')).toBe('skipped');
    expect(stateOf(g, 'RiegoPorDeficitRule')).toBe('skipped');
  });

  it('sólo ejecutadas: quedan las que corrieron, unidas en cadena, y las ramas sin ninguna se van', () => {
    const g = historyGraph(schema, event({ regla: 'DeficitCriticoRule' }), true);
    const ids = g.nodes.map((n) => n.id);

    expect(g.nodes.every((n) => n.state !== 'skipped')).toBe(true);
    expect(ids).not.toContain('lane-MEDIASOMBRA');
    expect(ids).toContain('success-RIEGO');
    expect(g.edges.find((e) => e.target === 'success-RIEGO')?.source).toBe('DeficitCriticoRule');
    // Toda arista une dos nodos conservados
    expect(g.edges.every((e) => ids.includes(e.source) && ids.includes(e.target))).toBe(true);
  });
});

describe('decidingRule', () => {
  it('sin `regla` (historial viejo), un riego se atribuye a R-01', () => {
    expect(decidingRule(schema, event({ regla: null }))?.id).toBe('RiegoPorDeficitRule');
  });

  it('una alerta de macro-zona no tiene evaluación que mostrar', () => {
    expect(decidingRule(schema, event({ tipo: 'Alerta', sectorId: '—', regla: 'SustratoSaturadoRule' }))).toBeNull();
  });

  it('la regla que decidió siempre se encuentra y se dibuja, esté o no apagada hoy o de fábrica', () => {
    const hoy = apagadasHoy('RiegoPorDeficitRule');
    expect(decidingRule(hoy, event({ regla: 'RiegoPorDeficitRule' }))?.id).toBe('RiegoPorDeficitRule');

    const retirada = event({ tipo: 'Insumo', regla: 'SupplyRule' });
    expect(decidingRule(schema, retirada)?.id).toBe('SupplyRule');
    expect(stateOf(historyGraph(schema, retirada), 'SupplyRule')).toBe('decided');
    // Aunque el registro la diera por apagada, la que decidió corrió.
    const g = historyGraph(hoy, event({ regla: 'RiegoPorDeficitRule', reglasInactivas: ['RiegoPorDeficitRule'] }));
    expect(stateOf(g, 'RiegoPorDeficitRule')).toBe('decided');
  });

  it('un cambio de configuración (prender o apagar una regla) no es una decisión del motor: sin grafo', () => {
    expect(decidingRule(schema, event({ tipo: 'Configuración', sectorId: '—', regla: 'RiegoPorDeficitRule' }))).toBeNull();
  });
});

describe('orthogonalPath', () => {
  it('en la misma columna es una recta vertical', () => {
    expect(orthogonalPath(100, 0, 100, 50)).toBe('M100,0 L100,50');
  });

  it('hacia otra columna cruza a la mitad: dos destinos de la misma fila comparten la altura del tramo horizontal', () => {
    const level = (path: string) => path.split(' ').find((p) => p.startsWith('L') && !p.startsWith('L100'));
    // Mismo origen, destinos a izquierda y derecha en la misma fila
    expect(orthogonalPath(400, 46, 100, 90)).toContain(',68');
    expect(orthogonalPath(400, 46, 700, 90)).toContain(',68');
    expect(level(orthogonalPath(400, 46, 100, 90))).toBe('L400,60');
  });
});
