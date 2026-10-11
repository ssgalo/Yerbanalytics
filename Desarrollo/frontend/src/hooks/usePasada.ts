/* ============================================================
   Hook de la pasada del riel (Demo Expo). Trae la pasada actual al montar y mantiene el
   polling mientras haga falta:
   - cada 1 s mientras la pasada está en curso;
   - cada 3 s ya terminada, mientras a alguna foto le falte el diagnóstico de IA y no hayan
     pasado 5 min desde que terminó (la IA tarda ~1 min tras la última foto).
   Después se detiene. El timer se limpia al desmontar y sólo la respuesta más reciente puede
   escribir el estado (mismo criterio que `useTrazaEvaluacion`).
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';
import { getRepository, PasadaRechazadaError, PermisoDenegadoError } from '@/data';
import type { Pasada } from '@/types/domain';

const INTERVALO_EN_CURSO_MS = 1000;
const INTERVALO_DIAGNOSTICO_MS = 3000;
const VENTANA_DIAGNOSTICO_MS = 5 * 60_000;
const SIN_BACKEND = 'No se pudo contactar al backend';

/** Cada cuánto hay que consultar según el estado de la pasada; null = no hace falta. */
export function intervaloDePolling(p: Pasada | null, ahoraMs: number): number | null {
  if (!p) return null;
  if (p.estado === 'EN_CURSO') return INTERVALO_EN_CURSO_MS;
  const esperaDiagnostico = p.pasos.some(
    (s) => s.tipo === 'CAPTURAR' && s.capturaId !== null && s.diagnostico === null,
  );
  const dentroDeVentana = p.finalizadaEn !== null && ahoraMs - p.finalizadaEn < VENTANA_DIAGNOSTICO_MS;
  return esperaDiagnostico && dentroDeVentana ? INTERVALO_DIAGNOSTICO_MS : null;
}

interface UsePasadaResult {
  pasada: Pasada | null;
  cargando: boolean;
  /** Último error: el de un intento de iniciar/cancelar (mensaje del backend) o el de la consulta. */
  error: string | null;
  iniciando: boolean;
  iniciar: () => Promise<void>;
  cancelar: () => Promise<void>;
}

// Un 403 (la matriz cambió mientras se miraba la vista) se dice tal cual, no como "sin backend".
const mensajeDe = (e: unknown) =>
  e instanceof PasadaRechazadaError || e instanceof PermisoDenegadoError ? e.message : SIN_BACKEND;

export function usePasada(): UsePasadaResult {
  const [pasada, setPasada] = useState<Pasada | null>(null);
  const [cargando, setCargando] = useState(true);
  const [iniciando, setIniciando] = useState(false);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [errorConsulta, setErrorConsulta] = useState<string | null>(null);
  // Hora de la última respuesta: fuerza a re-evaluar el intervalo (la ventana de 5 min vence con
  // el tiempo, no con los datos) aunque el backend devuelva una pasada idéntica.
  const [ahora, setAhora] = useState(() => Date.now());
  // Cada pedido lleva un número; sólo el último puede escribir el estado.
  const pedido = useRef(0);

  const consultar = useCallback(() => {
    const mio = ++pedido.current;
    getRepository()
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
        setErrorConsulta(SIN_BACKEND);
        setCargando(false);
      });
  }, []);

  useEffect(() => {
    // `pedido` es un contador, no un nodo del DOM: al desmontar se invalida lo que esté en vuelo.
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
      const nueva = await getRepository().iniciarPasada();
      pedido.current++; // una consulta anterior en vuelo no puede pisar a la pasada recién creada
      setPasada(nueva);
      setErrorConsulta(null);
    } catch (e) {
      setErrorAccion(mensajeDe(e));
    } finally {
      setIniciando(false);
    }
  }, []);

  const cancelar = useCallback(async () => {
    setErrorAccion(null);
    try {
      const cancelada = await getRepository().cancelarPasada();
      pedido.current++;
      setPasada(cancelada);
    } catch (e) {
      setErrorAccion(mensajeDe(e));
    }
  }, []);

  return { pasada, cargando, error: errorAccion ?? errorConsulta, iniciando, iniciar, cancelar };
}
