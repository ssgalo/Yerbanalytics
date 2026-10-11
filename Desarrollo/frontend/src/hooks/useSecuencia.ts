/* ============================================================
   Hook de las secuencias de actuadores (Demo Expo). Calcado de `usePasada`, más simple: trae
   la secuencia actual al montar y consulta cada 1 s sólo mientras está en curso. Después se
   detiene. El timer se limpia al desmontar y sólo la respuesta más reciente puede escribir el
   estado. No hay fase de "esperar diagnóstico": una secuencia termina cuando termina.
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';
import { getRepository, SecuenciaRechazadaError } from '@/data';
import type { ParametrosSecuencia, Secuencia, TipoSecuencia } from '@/types/domain';

const INTERVALO_EN_CURSO_MS = 1000;
const SIN_BACKEND = 'No se pudo contactar al backend';

interface UseSecuenciaResult {
  secuencia: Secuencia | null;
  cargando: boolean;
  /** Último error: el de un intento de iniciar/cancelar (mensaje del backend) o el de la consulta. */
  error: string | null;
  iniciando: boolean;
  iniciar: (tipo: TipoSecuencia, parametros?: ParametrosSecuencia) => Promise<void>;
  cancelar: () => Promise<void>;
  /** Borra el error de un intento (lo usa la vista cuando deja de existir la causa del rechazo). */
  descartarError: () => void;
}

const mensajeDe = (e: unknown) => (e instanceof SecuenciaRechazadaError ? e.message : SIN_BACKEND);

export function useSecuencia(): UseSecuenciaResult {
  const [secuencia, setSecuencia] = useState<Secuencia | null>(null);
  const [cargando, setCargando] = useState(true);
  const [iniciando, setIniciando] = useState(false);
  const [errorAccion, setErrorAccion] = useState<string | null>(null);
  const [errorConsulta, setErrorConsulta] = useState<string | null>(null);
  // Cada pedido lleva un número; sólo el último puede escribir el estado.
  const pedido = useRef(0);

  const consultar = useCallback(() => {
    const mio = ++pedido.current;
    getRepository()
      .getSecuenciaActual()
      .then((s) => {
        if (mio !== pedido.current) return;
        setSecuencia(s);
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

  const enCurso = secuencia?.estado === 'EN_CURSO';
  useEffect(() => {
    if (!enCurso) return undefined;
    const id = setInterval(consultar, INTERVALO_EN_CURSO_MS);
    return () => clearInterval(id);
  }, [enCurso, consultar]);

  const iniciar = useCallback(async (tipo: TipoSecuencia, parametros?: ParametrosSecuencia) => {
    setErrorAccion(null);
    setIniciando(true);
    try {
      const nueva = await getRepository().iniciarSecuencia(tipo, parametros);
      pedido.current++; // una consulta anterior en vuelo no puede pisar a la secuencia recién creada
      setSecuencia(nueva);
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
      const cancelada = await getRepository().cancelarSecuencia();
      pedido.current++;
      setSecuencia(cancelada);
    } catch (e) {
      if (!(e instanceof SecuenciaRechazadaError)) {
        setErrorAccion(mensajeDe(e));
        return;
      }
      // Pudo haber terminado entre dos consultas: se vuelve a mirar y, si ya no está en curso,
      // el rechazo no le dice nada útil al operador.
      const mio = ++pedido.current;
      try {
        const actual = await getRepository().getSecuenciaActual();
        if (mio !== pedido.current) return;
        setSecuencia(actual);
        setErrorConsulta(null);
        if (actual?.estado === 'EN_CURSO') setErrorAccion(e.message);
      } catch {
        if (mio === pedido.current) setErrorAccion(e.message);
      }
    }
  }, []);

  const descartarError = useCallback(() => setErrorAccion(null), []);

  return { secuencia, cargando, error: errorAccion ?? errorConsulta, iniciando, iniciar, cancelar, descartarError };
}
