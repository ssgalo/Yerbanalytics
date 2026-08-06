import { useCallback, useRef, useState } from 'react';

/**
 * Log de eventos en pantalla.
 *
 * No es un lujo: depurar con las devtools enchufadas a un iPhone montado en un riel es
 * impracticable. Todo lo que pase —cámara, canal, captura, envío— tiene que poder leerse
 * desde el propio teléfono.
 */

export type NivelLog = 'info' | 'ok' | 'warn' | 'error';

export interface EntradaLog {
  id: number;
  ts: number;
  nivel: NivelLog;
  mensaje: string;
}

/** Tope de entradas: el equipo corre durante horas y la memoria no es infinita. */
const MAX_ENTRADAS = 300;

export function useLog() {
  const [entradas, setEntradas] = useState<EntradaLog[]>([]);
  const siguienteId = useRef(0);

  const registrar = useCallback((nivel: NivelLog, mensaje: string) => {
    const entrada: EntradaLog = { id: siguienteId.current++, ts: Date.now(), nivel, mensaje };
    // También a la consola, para cuando SÍ hay devtools a mano (desarrollo en la máquina).
    console[nivel === 'error' ? 'error' : nivel === 'warn' ? 'warn' : 'log'](mensaje);
    setEntradas((previas) => [entrada, ...previas].slice(0, MAX_ENTRADAS));
  }, []);

  const log = useRef({
    info: (m: string) => registrar('info', m),
    ok: (m: string) => registrar('ok', m),
    warn: (m: string) => registrar('warn', m),
    error: (m: string) => registrar('error', m),
  }).current;

  return { entradas, log };
}

export type Log = ReturnType<typeof useLog>['log'];
