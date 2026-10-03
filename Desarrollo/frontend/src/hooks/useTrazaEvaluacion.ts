/* ============================================================
   Hook de la última evaluación del motor para un sector y origen. Pide la traza al
   repositorio (mock o http), permite refrescarla a mano y, opcionalmente, cada 5 s como el
   dashboard. La respuesta de un sector que ya no es el elegido se descarta.
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';
import { getRepository } from '@/data';
import type { OrigenEvaluacion, TrazaEvaluacion } from '@/types/domain';

const INTERVALO_AUTO_MS = 5000;

interface UseTrazaEvaluacionResult {
  /** null = todavía no se evaluó ese sector desde el arranque del backend. */
  traza: TrazaEvaluacion | null;
  loading: boolean;
  error: Error | null;
  refrescar: () => void;
}

export function useTrazaEvaluacion(
  sectorId: string,
  origen: OrigenEvaluacion,
  auto: boolean,
): UseTrazaEvaluacionResult {
  const [traza, setTraza] = useState<TrazaEvaluacion | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [version, setVersion] = useState(0);
  // Cada pedido lleva un número; sólo el último puede escribir el estado.
  const pedido = useRef(0);

  const cargar = useCallback(
    (silencioso: boolean) => {
      const mio = ++pedido.current;
      if (!silencioso) {
        // Un pedido nuevo (otro sector u origen, o "Actualizar") no hereda ni la traza ni el error
        // del anterior: el error quedaría rotulado con el sector nuevo.
        setTraza(null);
        setError(null);
        setLoading(true);
      }
      getRepository()
        .getTrazaEvaluacion(sectorId, origen)
        .then((t) => {
          if (mio !== pedido.current) return;
          setTraza(t);
          setError(null);
          setLoading(false);
        })
        .catch((e) => {
          if (mio !== pedido.current) return;
          setTraza(null);
          setError(e instanceof Error ? e : new Error(String(e)));
          setLoading(false);
        });
    },
    [sectorId, origen],
  );

  // Cambiar de sector u origen (o pedir "Actualizar") recarga sin mostrar la traza anterior.
  useEffect(() => {
    // `pedido` es un contador, no un nodo del DOM: se invalida el pedido en vuelo al limpiar.
    const contador = pedido;
    cargar(false);
    return () => {
      contador.current++;
    };
  }, [cargar, version]);

  useEffect(() => {
    if (!auto) return undefined;
    const id = setInterval(() => cargar(true), INTERVALO_AUTO_MS);
    return () => clearInterval(id);
  }, [auto, cargar]);

  const refrescar = useCallback(() => setVersion((v) => v + 1), []);

  return { traza, loading, error, refrescar };
}
