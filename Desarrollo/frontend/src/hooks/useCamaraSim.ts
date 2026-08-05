/* ============================================================
   Hook del panel de cámara del simulador.

   Consume EXCLUSIVAMENTE endpoints públicos de la plataforma: emitir una orden es lo que
   hará el planificador de pasadas del riel, y dar de alta un diagnóstico es lo que hará el
   servicio de inferencia. El simulador no tiene superficie de API propia — apagarlo no le
   quita nada al backend ni al dashboard.
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';
import { getRepository } from '@/data';
import type {
  CodigoVinculacion,
  DispositivoCamara,
  NuevoDiagnostico,
  OrdenCaptura,
} from '@/types/domain';

/** Cada cuánto se consulta el avance de una orden en curso. */
const POLL_MS = 1000;

/** Tope de espera antes de dejar de consultar, aunque la orden siga sin resolverse. */
const ESPERA_MAX_MS = 3 * 60_000;

const ESTADOS_TERMINALES = ['RECIBIDA', 'ERROR'] as const;

function esTerminal(orden: OrdenCaptura): boolean {
  return (ESTADOS_TERMINALES as readonly string[]).includes(orden.estado);
}

export function useCamaraSim() {
  const [dispositivos, setDispositivos] = useState<DispositivoCamara[]>([]);
  const [codigo, setCodigo] = useState<CodigoVinculacion | null>(null);
  const [orden, setOrden] = useState<OrdenCaptura | null>(null);
  const [pidiendo, setPidiendo] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  const pollRef = useRef<number | null>(null);
  const limiteRef = useRef(0);

  const detenerPoll = useCallback(() => {
    if (pollRef.current !== null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  /** Estado de la flota. Se refresca solo para reflejar el heartbeat del dispositivo. */
  const refrescarDispositivos = useCallback(async () => {
    try {
      setDispositivos(await getRepository().getDispositivosCamara());
    } catch {
      // Que falle el listado no debe romper el panel: puede no haber ninguno enrolado.
      setDispositivos([]);
    }
  }, []);

  useEffect(() => {
    void refrescarDispositivos();
    const id = window.setInterval(() => void refrescarDispositivos(), 5000);
    return () => window.clearInterval(id);
  }, [refrescarDispositivos]);

  useEffect(() => detenerPoll, [detenerPoll]);

  const generarCodigo = useCallback(async () => {
    setError(null);
    try {
      setCodigo(await getRepository().generarCodigoVinculacion());
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  /** Pide una captura y sigue su avance hasta que se resuelve o vence la espera. */
  const pedirCaptura = useCallback(
    async (sectorId: string, posicionRiel: number) => {
      setError(null);
      setAviso(null);
      setPidiendo(true);
      detenerPoll();

      try {
        const emitida = await getRepository().emitirOrdenCaptura({ sectorId, posicionRiel });
        setOrden(emitida);
        limiteRef.current = Date.now() + ESPERA_MAX_MS;

        pollRef.current = window.setInterval(async () => {
          try {
            const actual = await getRepository().getOrdenCaptura(emitida.ordenId);
            setOrden(actual);

            if (esTerminal(actual)) {
              detenerPoll();
              setPidiendo(false);
              if (actual.estado === 'ERROR') {
                setAviso(
                  `La orden agotó sus reintentos${actual.motivoFallo ? ` (${actual.motivoFallo})` : ''}.` +
                    ' Revisá que la app de cámara esté abierta y conectada.',
                );
              }
            } else if (Date.now() > limiteRef.current) {
              // No se deja el panel esperando para siempre: el backend sigue reintentando por
              // su cuenta, pero el operario necesita saber que acá no pasó nada.
              detenerPoll();
              setPidiendo(false);
              setAviso(
                'La orden sigue sin resolverse. El backend la va a reintentar; mientras tanto,' +
                  ' verificá que haya un dispositivo de captura conectado.',
              );
            }
          } catch (e) {
            detenerPoll();
            setPidiendo(false);
            setError((e as Error).message);
          }
        }, POLL_MS);
      } catch (e) {
        setPidiendo(false);
        setError((e as Error).message);
      }
    },
    [detenerPoll],
  );

  /** Alta del diagnóstico por el mismo endpoint que usará el servicio de inferencia. */
  const cargarDiagnostico = useCallback(async (input: NuevoDiagnostico) => {
    setError(null);
    setAviso(null);
    try {
      const creado = await getRepository().crearDiagnostico(input);
      setAviso(
        `Diagnóstico ${creado.id} registrado. Ya aparece en la vista de Diagnósticos de IA` +
          ' del dashboard, con su fotografía.',
      );
      return creado;
    } catch (e) {
      setError((e as Error).message);
      throw e;
    }
  }, []);

  const limpiar = useCallback(() => {
    detenerPoll();
    setOrden(null);
    setPidiendo(false);
    setError(null);
    setAviso(null);
  }, [detenerPoll]);

  return {
    dispositivos,
    codigo,
    orden,
    pidiendo,
    error,
    aviso,
    generarCodigo,
    pedirCaptura,
    cargarDiagnostico,
    limpiar,
  };
}
