import { describe, expect, it } from 'vitest';
import type { ActionRecord, Dispositivo, Stats, Zona } from '@/types/domain';
import {
  actionsBreakdown,
  activityTime,
  activityTitle,
  equipmentText,
  gridColumns,
  joinWords,
  latestReading,
  pluralize,
  resultTone,
  summarizeEquipment,
} from './dashboardSummary';

describe('pluralize / joinWords', () => {
  it('usa singular sólo con 1', () => {
    expect(pluralize(1, 'sector', 'sectores')).toBe('1 sector');
    expect(pluralize(0, 'sector', 'sectores')).toBe('0 sectores');
  });
  it('enumera con comas y "y"', () => {
    expect(joinWords([])).toBe('');
    expect(joinWords(['a'])).toBe('a');
    expect(joinWords(['a', 'b'])).toBe('a y b');
    expect(joinWords(['a', 'b', 'c'])).toBe('a, b y c');
  });
});

describe('gridColumns', () => {
  it('respeta la disposición sin pasarse de la cantidad', () => {
    expect(gridColumns(100, 10)).toBe(10);
    expect(gridColumns(3, 10)).toBe(3);
    expect(gridColumns(0, 10)).toBe(1);
  });
});

describe('latestReading', () => {
  const zona = (ts: number | null) => ({ lectura: { ts } }) as Zona;
  it('toma la lectura más reciente e ignora los nodos que nunca reportaron', () => {
    expect(latestReading([zona(10), zona(null), zona(30)])).toBe(30);
    expect(latestReading([zona(null)])).toBeNull();
  });
});

describe('actionsBreakdown', () => {
  const stats = (r: number, i: number, s: number) =>
    ({ actToday: r + i + s, actRiego: r, actInsumo: i, actSombra: s }) as Stats;
  it('enumera sólo los tipos que hubo', () => {
    expect(actionsBreakdown(stats(28, 7, 6))).toBe(
      '28 riegos, 7 dosificaciones y 6 movimientos de mediasombra.',
    );
    expect(actionsBreakdown(stats(1, 0, 0))).toBe('1 riego.');
  });
  it('sin acciones lo dice', () => {
    expect(actionsBreakdown(stats(0, 0, 0))).toBe('El motor todavía no actuó hoy.');
  });
});

describe('summarizeEquipment', () => {
  const dev = (d: Partial<Dispositivo>) =>
    ({ tipo: 'electrovalvula', estado: 'operativo', bateriaBaja: false, falla: null, ...d }) as Dispositivo;

  it('cuenta cada equipo una sola vez, por su peor problema', () => {
    const s = summarizeEquipment([
      dev({ estado: 'fuera_de_servicio', falla: 'sin caudal', bateriaBaja: true }),
      dev({ falla: 'sin caudal', bateriaBaja: true }),
      dev({ estado: 'intermitente' }),
      dev({ tipo: 'nodo_testigo', bateriaBaja: true }),
      dev({ tipo: 'nodo_testigo', estado: 'fuera_de_servicio' }),
      dev({}),
    ]);
    expect(s).toEqual({ total: 5, outOfService: 2, faulty: 1, warning: 2, nodesReporting: 1, nodes: 2 });
    expect(equipmentText(s)).toBe('2 fuera de servicio, 1 con falla y 2 con aviso. 1 de 2 nodos reportan al día.');
  });

  it('sin problemas lo dice', () => {
    expect(equipmentText(summarizeEquipment([dev({})]))).toBe('Todos los equipos funcionan.');
  });
});

describe('actividad', () => {
  const rec = (r: Partial<ActionRecord>) =>
    ({ tipo: 'Riego', sectorId: 'MZ-4-005', zonaName: 'Macro-zona 4', fecha: '11/10 14:26', time: 'hace 6 min', ...r }) as ActionRecord;

  it('titula en lenguaje llano con el sector, o la zona si es una alerta', () => {
    expect(activityTitle(rec({}))).toBe('Riego en MZ-4-005');
    expect(activityTitle(rec({ tipo: 'Insumo' }))).toBe('Dosificación de insumo en MZ-4-005');
    expect(activityTitle(rec({ tipo: 'Alerta', sectorId: '—' }))).toBe('Alerta en Macro-zona 4');
  });

  it('toma la hora de la fecha', () => {
    expect(activityTime(rec({}))).toBe('14:26');
  });

  it('elige el tono del resultado y cae en neutro si no lo conoce', () => {
    expect(resultTone('Efectiva')).toBe('ok');
    expect(resultTone('En seguimiento')).toBe('info');
    expect(resultTone('Pospuesta')).toBe('warning');
    expect(resultTone('Abortada')).toBe('critical');
    expect(resultTone('Otra cosa')).toBe('line');
  });
});
