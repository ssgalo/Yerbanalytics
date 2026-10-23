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

describe('historyGraph', () => {
  it('sólo dibuja reglas activas: las retiradas no aparecen', () => {
    const ids = historyGraph(schema, event({ regla: 'RiegoPorDeficitRule' })).nodes.map((n) => n.id);

    expect(ids).toContain('RiegoPorDeficitRule');
    expect(ids).not.toContain('SupplyRule');
    expect(ids).not.toContain('DailyDoseLimitRule');
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

  it('una regla apagada no puede haber decidido', () => {
    expect(decidingRule(schema, event({ tipo: 'Insumo', regla: 'SupplyRule' }))).toBeNull();
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
