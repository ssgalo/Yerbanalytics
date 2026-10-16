/* ============================================================
   Hook de las secuencias de actuadores para el simulador. Adapted from the dashboard's
   `useSecuencia` but calls the simulator's api module instead of the DataRepository.

   Polls every 1 s only while a secuencia is in progress; stops after it ends.
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';
import * as api from '../api';
import type { ParametrosSecuencia, Secuencia, TipoSecuencia } from '../types';

const INTERVALO_EN_CURSO_MS = 1000;

export interface UseSecuenciaSimulatorResult {
  secuencia: Secuencia | null;
  cargando: boolean;
  error: string | null;
  iniciando: boolean;
  iniciar: (tipo: TipoSecuencia, parametros?: ParametrosSecuencia) => Promise<void>;
  cancelar: () => Promise<void>;
  descartarError: () => void;
}

export function useSecuenciaSimulator(): UseSecuenciaSimulatorResult {
  const [secuencia, setSecuencia] = useState<Secuencia | null>(null);
  const [cargando, setCargando] = useState(true);
  const [iniciando, setIniciando] = useState(false);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [errorConsulta, setErrorConsulta] = useState<string | null>(null);
  const pedido = useRef(0);

  const consultar = useCallback(() => {
    const mio = ++pedido.current;
    api
      .getSecuenciaActual()
      .then((s) => {
        if (mio !== pedido.current) return;
        setSecuencia(s);
        setErrorConsulta(null);
        setCargando(false);
      })
      .catch(() => {
        if (mio !== pedido.current) return;
        setErrorConsulta('No se pudo contactar al backend.');
        setCargando(false);
      });
  }, []);

  useEffect(() => {
    const contador = pedido;
    consultar();
    return () => {
      contador.current++;
    };
  }, [consultar]);

  const enCurso = secuencia?.estado === 'EN_CURSO';
  useEffect(() => {
    if (!enCurso) return undefined;
    const id = setInterval(consultar, INTERVALO_EN_CURSO_MS);
    return () => clearInterval(id);
  }, [enCurso, consultar]);

  const iniciar = useCallback(
    async (tipo: TipoSecuencia, parametros?: ParametrosSecuencia) => {
      setErrorAccion(null);
      setIniciando(true);
      try {
        const nueva = await api.iniciarSecuencia(tipo, parametros);
        pedido.current++;
        setSecuencia(nueva);
        setErrorConsulta(null);
      } catch (e) {
        setErrorAccion(e instanceof Error ? e.message : String(e));
      } finally {
        setIniciando(false);
      }
    },
    [],
  );

  const cancelar = useCallback(async () => {
    setErrorAccion(null);
    try {
      const cancelada = await api.cancelarSecuencia();
      pedido.current++;
      setSecuencia(cancelada);
    } catch (e) {
      setErrorAccion(e instanceof Error ? e.message : String(e));
    }
  }, []);

  const descartarError = useCallback(() => setErrorAccion(null), []);

  return {
    secuencia,
    cargando,
    error: errorAccion ?? errorConsulta,
    iniciando,
    iniciar,
    cancelar,
    descartarError,
  };
}
