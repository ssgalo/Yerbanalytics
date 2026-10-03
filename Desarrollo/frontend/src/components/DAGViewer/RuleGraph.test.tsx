// @vitest-environment jsdom
import '@/test/reactFlowJsdom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, within } from '@testing-library/react';
import { RuleGraph } from './RuleGraph';
import { buildRuleSchema } from '@/data/mock/reglasMock';
import { evaluarMotor, type EntradaMotor } from '@/data/mock/trazaReglas';
import fixture from '@/data/mock/catalogoReglas.fixture.json';
import type { ActionRecord, CatalogoReglas } from '@/types/domain';

afterEach(cleanup);

const schema = buildRuleSchema();
const catalogo = fixture as CatalogoReglas;
const base: EntradaMotor = {
  sectorId: 'MZ-1-001',
  zonaId: 'MZ-1',
  origen: 'TELEMETRIA',
  ts: '2026-06-13T12:00:00.000Z',
  antiguedadSeg: 1,
  bloqueoManual: false,
  humSus: 55,
  lluviaPct: 10,
  lluviaMm: 1,
  uvIndex: 3,
  dosis24h: 0,
  estadoSector: 'ok',
  confianza: 92,
};
const traza = (e: Partial<EntradaMotor> = {}) => evaluarMotor(catalogo, { ...base, ...e });
const nodo = (id: string) =>
  document.querySelector(`.react-flow__node[data-id="${id}"]`) as HTMLElement;

describe('RuleGraph sin traza (modo Historial)', () => {
  it('se comporta como hoy: nodos por defecto, sin nodos de traza', () => {
    render(<RuleGraph schema={schema} activeEvents={[]} />);

    expect(nodo('RiegoPorDeficitRule')).toBeTruthy();
    expect(within(nodo('RiegoPorDeficitRule')).getByText('💦 Riego por déficit hídrico (R-01)')).toBeTruthy();
    expect(document.querySelector('.react-flow__node-regla')).toBeNull();
    expect(document.querySelector('[data-estado]')).toBeNull();
  });
});

describe('RuleGraph con traza (Inspector)', () => {
  it('pinta cada regla con el estado que sale de la traza', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 38 })} />);

    expect(nodo('RiegoPorDeficitRule').querySelector('[data-estado="accion"]')).toBeTruthy();
    expect(nodo('ManualLockRule').querySelector('[data-estado="paso"]')).toBeTruthy();
  });

  it('muestra "recibido vs. umbral" en el nodo de riego', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 38 })} />);

    // comparacion-0 es "humedad ≥ umbral crítico"; la 1, "humedad < umbral de riego".
    const linea = within(nodo('RiegoPorDeficitRule')).getByTestId('comparacion-1');
    expect(linea.textContent).toContain('38 %');
    expect(linea.textContent).toContain('45 %');
    expect(linea.textContent).toContain('✓');
  });

  it('humedad sobre el umbral: ✗ en la regla y terminal "No se regó"', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 55 })} />);

    expect(within(nodo('RiegoPorDeficitRule')).getByTestId('comparacion-1').textContent).toContain('✗');
    expect(within(nodo('success-RIEGO')).getByText('No se regó')).toBeTruthy();
  });

  it('riego efectuado cuando la rama accionó', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 38 })} />);
    expect(within(nodo('success-RIEGO')).getByText('Riego efectuado')).toBeTruthy();
  });

  it('lluvia: el nodo pospuso con la probabilidad recibida y las siguientes omitidas', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 40, lluviaPct: 80, lluviaMm: 8 })} />);

    const lluvia = nodo('PosponerPorLluviaRule');
    expect(lluvia.querySelector('[data-estado="pospuso"]')).toBeTruthy();
    // Las dos primeras comparaciones son las de R-01 (aplica); a la lluvia le quedan las de probabilidad y mm.
    expect(within(lluvia).getByTestId('comparacion-0').textContent).toContain('≥');
    expect(lluvia.textContent).toContain('+2 comparaciones más');
    expect(nodo('RiegoPorDeficitRule').querySelector('[data-estado="omitida"]')).toBeTruthy();
  });

  it('el clic en una regla avisa cuál se eligió', () => {
    const onSeleccionar = vi.fn();
    render(<RuleGraph schema={schema} traza={traza()} onSeleccionar={onSeleccionar} />);

    fireEvent.click(nodo('RiegoPorDeficitRule'));

    expect(onSeleccionar).toHaveBeenCalledWith('RiegoPorDeficitRule');
  });

  it('marca como seleccionada la regla elegida', () => {
    render(<RuleGraph schema={schema} traza={traza()} seleccionada="RiegoPorDeficitRule" />);
    expect(nodo('RiegoPorDeficitRule').querySelector('[data-seleccionado="true"]')).toBeTruthy();
    expect(nodo('ManualLockRule').querySelector('[data-seleccionado="true"]')).toBeNull();
  });
});

describe('RuleGraph en el Historial: a qué regla se atribuye cada evento (13.4)', () => {
  const evento = (e: Partial<ActionRecord>): ActionRecord => ({
    id: 'HE-1',
    sectorId: 'MZ-1-001',
    zonaName: 'Macro-zona 1',
    tipo: 'Riego',
    time: 'hace 5 min',
    ts: 1,
    fecha: '03/10 10:05',
    lectura: 'Humedad de sustrato 38%.',
    decision: 'El motor de reglas ordena regar.',
    accion: 'Electroválvula abierta.',
    res: 'Efectiva',
    resSoft: '#fff',
    resInk: '#000',
    sev: '—',
    tint: '#fff',
    ink: '#000',
    path: 'M0 0',
    evo: null,
    ...e,
  });
  const ACCION = 'rgb(12, 42, 107)'; // fondo de un nodo que accionó
  const fondo = (id: string) => nodo(id).style.background;

  it('un riego ordenado por R-02 marca a DeficitCriticoRule (campo `regla`)', () => {
    render(<RuleGraph schema={schema} activeEvents={[evento({ regla: 'DeficitCriticoRule' })]} />);

    expect(fondo('DeficitCriticoRule')).toBe(ACCION);
    expect(fondo('RiegoPorDeficitRule')).not.toBe(ACCION);
  });

  it('un riego sin `regla` (historial viejo) se atribuye a RiegoPorDeficitRule, no a la regla borrada', () => {
    render(<RuleGraph schema={schema} activeEvents={[evento({ regla: null })]} />);

    expect(fondo('RiegoPorDeficitRule')).toBe(ACCION);
    expect(nodo('IrrigationRule')).toBeNull();
  });

  it('un riego pospuesto por lluvia marca a la regla que lo pospuso', () => {
    render(
      <RuleGraph
        schema={schema}
        activeEvents={[
          evento({ regla: 'PosponerPorLluviaRule', res: 'Pospuesta', decision: 'Lluvia prevista: se pospone el riego.' }),
        ]}
      />,
    );

    expect(fondo('PosponerPorLluviaRule')).not.toBe(ACCION);
    expect(fondo('PosponerPorLluviaRule')).not.toBe(fondo('RiegoPorDeficitRule'));
  });

  it('una alerta de macro-zona no se atribuye a ningún nodo (no es una decisión de un sector)', () => {
    render(
      <RuleGraph schema={schema} activeEvents={[evento({ tipo: 'Alerta', sectorId: '—', regla: 'SustratoSaturadoRule', alerta: 'WARNING' })]} />,
    );

    expect(fondo('SustratoSaturadoRule')).not.toBe(ACCION);
    expect(fondo('SustratoSaturadoRule')).toBe(fondo('ManualLockRule'));
  });
});
