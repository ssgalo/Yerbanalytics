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
  lluviaMm: 1,
  uvIndex: 3,
  dosis24h: 0,
  estadoSector: 'ok',
  confianza: 92,
};
const traza = (e: Partial<EntradaMotor> = {}) => evaluarMotor(catalogo, { ...base, ...e });
const nodo = (id: string) =>
  document.querySelector(`.react-flow__node[data-id="${id}"]`) as HTMLElement;

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
