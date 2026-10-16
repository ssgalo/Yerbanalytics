/* ============================================================
   Hook de la pasada del riel para el simulador. Adapted from the dashboard's `usePasada`
   but calls the simulator's api module instead of the DataRepository.

   Polling intervals:
   - 1 s while the pasada is in progress.
   - 3 s once finished, while any capture step is waiting for its AI diagnosis
     (up to 5 minutes after the pasada ended).
   After that the hook goes quiet. The timer is cleared on unmount and only the most
   recent in-flight request can write to state.
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';
import * as api from '../api';
import type { Pasada } from '../types';

const INTERVALO_EN_CURSO_MS = 1000;
const INTERVALO_DIAGNOSTICO_MS = 3000;
const VENTANA_DIAGNOSTICO_MS = 5 * 60_000;

function intervaloDePolling(p: Pasada | null, ahoraMs: number): number | null {
  if (!p) return null;
  if (p.estado === 'EN_CURSO') return INTERVALO_EN_CURSO_MS;
  const esperaDiagnostico = p.pasos.some(
    (s) => s.tipo === 'CAPTURAR' && s.capturaId !== null && s.diagnostico === null,
  );
  const dentroDeVentana =
    p.finalizadaEn !== null && ahoraMs - p.finalizadaEn < VENTANA_DIAGNOSTICO_MS;
  return esperaDiagnostico && dentroDeVentana ? INTERVALO_DIAGNOSTICO_MS : null;
}

export interface UsePasadaSimulatorResult {
  pasada: Pasada | null;
  cargando: boolean;
  error: string | null;
  iniciando: boolean;
  iniciar: () => Promise<void>;
  cancelar: () => Promise<void>;
}

export function usePasadaSimulator(): UsePasadaSimulatorResult {
  const [pasada, setPasada] = useState<Pasada | null>(null);
  const [cargando, setCargando] = useState(true);
  const [iniciando, setIniciando] = useState(false);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [errorConsulta, setErrorConsulta] = useState<string | null>(null);
  const [ahora, setAhora] = useState(() => Date.now());
  const pedido = useRef(0);

  const consultar = useCallback(() => {
    const mio = ++pedido.current;
    api
      .getPasadaActual()
      .then((p) => {
        if (mio !== pedido.current) return;
        setPasada(p);
        setAhora(Date.now());
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

  const intervalo = intervaloDePolling(pasada, ahora);
  useEffect(() => {
    if (intervalo === null) return undefined;
    const id = setInterval(consultar, intervalo);
    return () => clearInterval(id);
  }, [intervalo, consultar]);

  const iniciar = useCallback(async () => {
    setErrorAccion(null);
    setIniciando(true);
    try {
      const nueva = await api.iniciarPasada();
      pedido.current++;
      setPasada(nueva);
      setErrorConsulta(null);
    } catch (e) {
      setErrorAccion(e instanceof Error ? e.message : String(e));
    } finally {
      setIniciando(false);
    }
  }, []);

  const cancelar = useCallback(async () => {
    setErrorAccion(null);
    try {
      const cancelada = await api.cancelarPasada();
      pedido.current++;
      setPasada(cancelada);
    } catch (e) {
      setErrorAccion(e instanceof Error ? e.message : String(e));
    }
  }, []);

  return {
    pasada,
    cargando,
    error: errorAccion ?? errorConsulta,
    iniciando,
    iniciar,
    cancelar,
  };
}
