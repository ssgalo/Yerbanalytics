/* ============================================================
   Hook del dashboard de simulación. Obtiene el modo de operación y los sensores
   simulados del repositorio (mock o http) y expone el cambio de modo, el alta/baja
   de sensores y el envío manual de telemetría (parcial o completo), sin que la vista
   conozca el origen de los datos.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type {
  EnvioTelemetria,
  ModoSimulacion,
  SensorSimulado,
  SimulacionEstado,
} from '@/types/domain';

interface UseSimulacionResult {
  estado: SimulacionEstado | null;
  sensores: SensorSimulado[];
  loading: boolean;
  error: Error | null;
  sending: boolean;
  mutating: boolean;
  /** Cambia el modo (estático/simulación) y actualiza el estado local. */
  setModo: (modo: ModoSimulacion) => Promise<SimulacionEstado>;
  /** Da de alta un sensor simulado (serial/MAC + macro-zona). */
  crearSensor: (input: SensorSimulado) => Promise<void>;
  /** Elimina un sensor simulado por serial/MAC. */
  eliminarSensor: (serial: string) => Promise<void>;
  /** Envía una lectura manual (parcial o completa); rechaza con el error del backend. */
  enviar: (input: EnvioTelemetria) => Promise<void>;
}

export function useSimulacion(): UseSimulacionResult {
  const [estado, setEstado] = useState<SimulacionEstado | null>(null);
  const [sensores, setSensores] = useState<SensorSimulado[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [sending, setSending] = useState(false);
  const [mutating, setMutating] = useState(false);

  useEffect(() => {
    let active = true;

    Promise.all([getRepository().getSimulacionEstado(), getRepository().getSensoresSimulados()])
      .then(([e, s]) => {
        if (active) {
          setEstado(e);
          setSensores(s);
          setLoading(false);
        }
      })
      .catch((e) => {
        if (active) {
          setError(e instanceof Error ? e : new Error(String(e)));
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const setModo = useCallback(async (modo: ModoSimulacion): Promise<SimulacionEstado> => {
    const updated = await getRepository().setModoSimulacion(modo);
    setEstado(updated);
    return updated;
  }, []);

  const crearSensor = useCallback(async (input: SensorSimulado): Promise<void> => {
    setMutating(true);
    try {
      setSensores(await getRepository().crearSensorSimulado(input));
    } finally {
      setMutating(false);
    }
  }, []);

  const eliminarSensor = useCallback(async (serial: string): Promise<void> => {
    setMutating(true);
    try {
      setSensores(await getRepository().eliminarSensorSimulado(serial));
    } finally {
      setMutating(false);
    }
  }, []);

  const enviar = useCallback(async (input: EnvioTelemetria): Promise<void> => {
    setSending(true);
    try {
      await getRepository().enviarTelemetria(input);
    } finally {
      setSending(false);
    }
  }, []);

  return { estado, sensores, loading, error, sending, mutating, setModo, crearSensor, eliminarSensor, enviar };
}
