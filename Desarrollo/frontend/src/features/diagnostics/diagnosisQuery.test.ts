import { describe, expect, it } from 'vitest';
import type { DiagnosisCard } from '@/types/domain';
import {
  NO_COLUMN_FILTERS,
  columnExtent,
  columnOptions,
  countByClass,
  filterByClass,
  filterByColumns,
  sortDiagnoses,
  valueLabel,
} from './diagnosisQuery';

const NOW = 1_000_000_000;

const card = (p: Partial<DiagnosisCard>): DiagnosisCard => ({
  id: 'DG-001',
  sectorId: 'MZ1-S001',
  zonaName: 'MZ-1',
  estado: 'Clorosis',
  conf: 90,
  confidence: 'Alta',
  detectionCount: 0,
  sev: 'Media',
  sevSoft: '',
  sevInk: '',
  thumb: '',
  time: 'hace 5 min',
  creadoEn: NOW - 300_000,
  concluyente: true,
  ...p,
});

const list = [
  card({
    id: 'a',
    sectorId: 'MZ2-S010',
    zonaName: 'MZ-2',
    conf: 72,
    confidence: 'Media',
    sev: 'Alta',
    time: 'hace 30 s',
    creadoEn: NOW - 30_000,
  }),
  card({
    id: 'b',
    sectorId: 'MZ1-S002',
    conf: 95,
    sev: 'Baja',
    time: 'hace 90 min',
    creadoEn: NOW - 5_400_000,
    estado: 'Sano',
  }),
  card({
    id: 'c',
    sectorId: 'MZ1-S100',
    conf: null,
    confidence: null,
    sev: '—',
    time: 'hace —',
    creadoEn: null,
    estado: 'No concluyente',
    concluyente: false,
  }),
];
const ids = (l: DiagnosisCard[]) => l.map((d) => d.id);

describe('filterByColumns', () => {
  it('deja sólo los valores elegidos de las columnas categóricas', () => {
    const f = NO_COLUMN_FILTERS;
    expect(ids(filterByColumns(list, { ...f, zone: ['MZ-1'] }))).toEqual(['b', 'c']);
    expect(ids(filterByColumns(list, { ...f, severity: ['Alta', 'Baja'] }))).toEqual(['a', 'b']);
    expect(ids(filterByClass(list, 'Sano'))).toEqual(['b']);
  });

  it('filtra la confianza por nivel y la fecha por rango, con extremos opcionales', () => {
    const f = NO_COLUMN_FILTERS;
    expect(ids(filterByColumns(list, { ...f, confidence: ['Alta'] }))).toEqual(['b']);
    // No concluyente no tiene nivel: se elige como "—" ("Sin nivel")
    expect(ids(filterByColumns(list, { ...f, confidence: ['—'] }))).toEqual(['c']);
    // Último minuto: sólo "hace 30 s"; los de fecha desconocida quedan afuera
    expect(ids(filterByColumns(list, { ...f, date: { min: NOW - 60_000 } }))).toEqual(['a']);
    expect(ids(filterByColumns(list, { ...f, date: { max: NOW - 60_000 } }))).toEqual(['b']);
  });

  it('arma las opciones y extremos de una columna sin aplicar su propio filtro', () => {
    const filters = { ...NO_COLUMN_FILTERS, zone: ['MZ-1'], severity: ['Alta'] };
    expect(columnOptions(filterByColumns(list, filters, 'zone'), 'zone')).toEqual([
      { value: 'MZ-2', count: 1 },
    ]);
    expect(columnOptions(list, 'severity').map((o) => o.value)).toEqual(['Alta', 'Baja', '—']);
    expect(columnOptions(list, 'confidence').map((o) => o.value)).toEqual(['Alta', 'Media', '—']);
    expect(valueLabel('confidence', '—')).toBe('Sin nivel');
    expect(valueLabel('severity', '—')).toBe('Sin severidad');
    expect(columnExtent(list, 'date')).toEqual({ min: NOW - 5_400_000, max: NOW - 30_000 });
  });
});

describe('sortDiagnoses', () => {
  it('ordena por cada columna en los dos sentidos', () => {
    expect(sortDiagnoses(list, null)).toBe(list);
    expect(ids(sortDiagnoses(list, { column: 'date', dir: 'desc' }))).toEqual(['a', 'b', 'c']);
    expect(ids(sortDiagnoses(list, { column: 'date', dir: 'asc' }))).toEqual(['b', 'a', 'c']);
    expect(ids(sortDiagnoses(list, { column: 'confidence', dir: 'desc' }))).toEqual([
      'b',
      'a',
      'c',
    ]);
    // Por nivel, no por score: sin nivel (No concluyente) es el más bajo
    expect(ids(sortDiagnoses(list, { column: 'confidence', dir: 'asc' }))).toEqual(['c', 'a', 'b']);
    expect(ids(sortDiagnoses(list, { column: 'severity', dir: 'desc' }))).toEqual(['a', 'b', 'c']);
    expect(ids(sortDiagnoses(list, { column: 'sector', dir: 'asc' }))).toEqual(['b', 'c', 'a']);
    expect(ids(sortDiagnoses(list, { column: 'zone', dir: 'desc' }))).toEqual(['a', 'b', 'c']);
  });
});

describe('countByClass', () => {
  it('incluye las clases conocidas aunque estén en cero', () => {
    const counts = countByClass(list);
    expect(counts.find((c) => c.name === 'Estrés solar')?.count).toBe(0);
    expect(counts.find((c) => c.name === 'Sano')?.count).toBe(1);
  });
});
