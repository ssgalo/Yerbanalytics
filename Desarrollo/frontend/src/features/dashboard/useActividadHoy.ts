/* ============================================================
   Actividad del sistema del día en curso.

   Es el historial (HU-11) recortado a hoy: el snapshot del vivero sólo trae las
   últimas acciones, sin fecha, así que se lee del historial.

   El recorte lo hace el backend (`tipo` + `desde`), nunca el navegador: el historial
   real acumula cientos de miles de eventos `Info` (las inacciones del motor) y bajarlo
   entero congela la pestaña. Por eso se piden sólo las acciones reales, tipo por tipo
   —las mismas que cuenta el KPI de acciones de hoy—, y se dibujan como mucho
   `MAX_EVENTOS`.
   ============================================================ */
import { useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { ActionRecord } from '@/types/domain';

const REFRESCO_MS = 30_000;

/** Tipos de evento que son acciones del sistema (sin `Info` ni `Configuración`). */
export const TIPOS_ACTIVIDAD = ['Riego', 'Insumo', 'Mediasombra', 'Alerta'] as const;

/** Tope de eventos dibujados: el resto se ve en el Historial. */
export const MAX_EVENTOS = 50;

/** Inicio del día en curso (hora local), en epoch ms. */
export function inicioDelDia(ahora: Date = new Date()): number {
  return new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate()).getTime();
}

/** Registros de hoy, más recientes primero. */
export function registrosDeHoy(records: ActionRecord[], ahora: Date = new Date()): ActionRecord[] {
  const desde = inicioDelDia(ahora);
  return records.filter((r) => r.ts >= desde).sort((a, b) => b.ts - a.ts);
}

interface UseActividadHoyResult {
  /** Los más recientes de hoy, hasta `MAX_EVENTOS`. */
  records: ActionRecord[];
  /** Cantidad total de eventos de hoy (puede superar a `records`). */
  total: number;
  loading: boolean;
  error: Error | null;
}

/** `habilitado` = el rol puede ver el historial; si no, no se consulta. */
export function useActividadHoy(habilitado: boolean): UseActividadHoyResult {
  const [records, setRecords] = useState<ActionRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(habilitado);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!habilitado) return;
    let active = true;
    let enCurso = false;

    function cargar() {
      // Sin solapar pedidos si uno tarda más que el intervalo.
      if (enCurso) return;
      enCurso = true;
      const repo = getRepository();
      const desde = inicioDelDia();
      Promise.all(TIPOS_ACTIVIDAD.map((tipo) => repo.getHistory({ tipo, desde })))
        .then((porTipo) => {
          if (!active) return;
          const hoy = registrosDeHoy(porTipo.flat());
          setRecords(hoy.slice(0, MAX_EVENTOS));
          setTotal(hoy.length);
          setError(null);
          setLoading(false);
        })
        .catch((e) => {
          if (!active) return;
          setError(e instanceof Error ? e : new Error(String(e)));
          setLoading(false);
        })
        .finally(() => {
          enCurso = false;
        });
    }

    cargar();
    const interval = setInterval(cargar, REFRESCO_MS);
    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [habilitado]);

  return { records, total, loading, error };
}
