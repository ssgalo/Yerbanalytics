import { describe, expect, it } from 'vitest';
import { columnExtent, columnOptions, filterRows, sortRows, type Column } from './columnQuery';

interface Fila {
  nombre: string;
  rol: string;
  fecha: number | null;
}

const FILAS: Fila[] = [
  { nombre: 'carla', rol: 'B', fecha: 300 },
  { nombre: 'ana', rol: 'A', fecha: null },
  { nombre: 'beto', rol: 'B', fecha: 100 },
];

const AZ = { asc: 'De A a Z', desc: 'De Z a A' };
const NOMBRE: Column<Fila> = { key: 'nombre', label: 'Nombre', kind: 'values', value: (f) => f.nombre, sortLabels: AZ };
const ROL: Column<Fila> = { key: 'rol', label: 'Rol', kind: 'values', value: (f) => f.rol, sortLabels: AZ };
const FECHA: Column<Fila> = { key: 'fecha', label: 'Fecha', kind: 'date', date: (f) => f.fecha, sortLabels: AZ };
const COLUMNAS = [NOMBRE, ROL, FECHA];
const nombres = (filas: Fila[]) => filas.map((f) => f.nombre);

describe('columnQuery', () => {
  it('filtra por valores y por rango; sin fecha queda fuera de cualquier rango', () => {
    expect(nombres(filterRows(FILAS, COLUMNAS, { rol: ['B'] }))).toEqual(['carla', 'beto']);
    expect(nombres(filterRows(FILAS, COLUMNAS, { fecha: { min: 200 } }))).toEqual(['carla']);
    expect(nombres(filterRows(FILAS, COLUMNAS, { rol: ['B'], fecha: { max: 200 } }))).toEqual(['beto']);
  });

  it('las opciones de una columna salen de lo que dejan las demás', () => {
    const pool = filterRows(FILAS, COLUMNAS, { rol: ['B'], nombre: ['beto'] }, 'nombre');
    expect(columnOptions(pool, NOMBRE as Extract<Column<Fila>, { kind: 'values' }>)).toEqual([
      { value: 'beto', label: 'beto', count: 1 },
      { value: 'carla', label: 'carla', count: 1 },
    ]);
  });

  it('ordena sin mutar; las filas sin fecha quedan al final en los dos sentidos', () => {
    expect(nombres(sortRows(FILAS, COLUMNAS, { column: 'nombre', dir: 'asc' }))).toEqual(['ana', 'beto', 'carla']);
    expect(nombres(sortRows(FILAS, COLUMNAS, { column: 'fecha', dir: 'desc' }))).toEqual(['carla', 'beto', 'ana']);
    expect(nombres(sortRows(FILAS, COLUMNAS, { column: 'fecha', dir: 'asc' }))).toEqual(['beto', 'carla', 'ana']);
    expect(nombres(sortRows(FILAS, COLUMNAS, null))).toEqual(['carla', 'ana', 'beto']);
  });

  it('los empates conservan el orden de llegada', () => {
    expect(nombres(sortRows(FILAS, COLUMNAS, { column: 'rol', dir: 'desc' }))).toEqual(['carla', 'beto', 'ana']);
  });

  it('extremos de una columna de fecha', () => {
    expect(columnExtent(FILAS, FECHA as Extract<Column<Fila>, { kind: 'date' }>)).toEqual({ min: 100, max: 300 });
  });
});
