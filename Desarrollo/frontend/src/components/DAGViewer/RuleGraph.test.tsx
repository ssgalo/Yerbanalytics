// @vitest-environment jsdom
import '@/test/reactFlowJsdom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, within } from '@testing-library/react';
import { RuleGraph } from './RuleGraph';
import { buildRuleSchema } from '@/data/mock/reglasMock';
import { evaluarMotor, type EntradaMotor } from '@/data/mock/trazaReglas';
import fixture from '@/data/mock/catalogoReglas.fixture.json';
import type { CatalogoReglas } from '@/types/domain';

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
  uvIndex: 3,
  riegos24h: 0,
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

    expect(nodo('IrrigationRule')).toBeTruthy();
    expect(within(nodo('IrrigationRule')).getByText('💦 Riego')).toBeTruthy();
    expect(document.querySelector('.react-flow__node-regla')).toBeNull();
    expect(document.querySelector('[data-estado]')).toBeNull();
  });
});

describe('RuleGraph con traza (Inspector)', () => {
  it('pinta cada regla con el estado que sale de la traza', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 38 })} />);

    expect(nodo('IrrigationRule').querySelector('[data-estado="accion"]')).toBeTruthy();
    expect(nodo('ManualLockRule').querySelector('[data-estado="paso"]')).toBeTruthy();
  });

  it('muestra "recibido vs. umbral" en el nodo de riego', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 38 })} />);

    const linea = within(nodo('IrrigationRule')).getByTestId('comparacion-0');
    expect(linea.textContent).toContain('38 %');
    expect(linea.textContent).toContain('42 %');
    expect(linea.textContent).toContain('✓');
  });

  it('humedad sobre el umbral: ✗ en la regla y terminal "No se regó"', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 55 })} />);

    expect(within(nodo('IrrigationRule')).getByTestId('comparacion-0').textContent).toContain('✗');
    expect(within(nodo('success-RIEGO')).getByText('No se regó')).toBeTruthy();
  });

  it('riego efectuado cuando la rama accionó', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 38 })} />);
    expect(within(nodo('success-RIEGO')).getByText('Riego efectuado')).toBeTruthy();
  });

  it('lluvia: el nodo pospuso con la probabilidad recibida y las siguientes omitidas', () => {
    render(<RuleGraph schema={schema} traza={traza({ humSus: 38, lluviaPct: 80 })} />);

    const lluvia = nodo('WeatherOverrideRule');
    expect(lluvia.querySelector('[data-estado="pospuso"]')).toBeTruthy();
    expect(within(lluvia).getByTestId('comparacion-0').textContent).toContain('80 %');
    expect(nodo('IrrigationRule').querySelector('[data-estado="omitida"]')).toBeTruthy();
  });

  it('el clic en una regla avisa cuál se eligió', () => {
    const onSeleccionar = vi.fn();
    render(<RuleGraph schema={schema} traza={traza()} onSeleccionar={onSeleccionar} />);

    fireEvent.click(nodo('IrrigationRule'));

    expect(onSeleccionar).toHaveBeenCalledWith('IrrigationRule');
  });

  it('marca como seleccionada la regla elegida', () => {
    render(<RuleGraph schema={schema} traza={traza()} seleccionada="IrrigationRule" />);
    expect(nodo('IrrigationRule').querySelector('[data-seleccionado="true"]')).toBeTruthy();
    expect(nodo('ManualLockRule').querySelector('[data-seleccionado="true"]')).toBeNull();
  });
});
