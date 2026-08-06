import { useCallback, useEffect, useRef, useState } from 'react';
import { capturarJpeg, fijarAjustes, sha256, type ConstraintsAplicados } from '@/lib/captura';
import type { Log } from './useLog';

/* ============================================================
   Cámara del dispositivo.

   Concentra las restricciones de iOS/Safari, que no son sugerencias:

   1. El permiso exige un GESTO del usuario. No se puede pedir el stream al cargar la página.
   2. El <video> necesita playsinline/muted/autoplay o iOS lo abre a pantalla completa.
   3. iOS SUSPENDE la sesión de cámara al perder el primer plano. Nunca se asume que el
      stream sigue vivo: se verifica.
   4. No hay ImageCapture: la foto sale de dibujar en un canvas (ver lib/captura.ts).
   5. El wake lock existe desde iOS 16.4 y se pierde al ir a segundo plano.
   ============================================================ */

export type EstadoCamara = 'apagada' | 'iniciando' | 'calentando' | 'lista' | 'error';

/**
 * La cámara no estaba en condiciones de capturar. Se distingue de un fallo al exportar el
 * fotograma porque el contrato tiene un motivo distinto para cada caso, y confundirlos manda
 * a buscar el problema al lugar equivocado.
 */
export class CamaraNoListaError extends Error {}

interface Opciones {
  anchoMax: number;
  altoMax: number;
  calidadJpeg: number;
  warmupMs: number;
  log: Log;
}

export function useCamara({ anchoMax, altoMax, calidadJpeg, warmupMs, log }: Opciones) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const wakeLockRef = useRef<WakeLockSentinel | null>(null);
  const iniciandoRef = useRef(false);

  const [estado, setEstado] = useState<EstadoCamara>('apagada');
  const [error, setError] = useState<string | null>(null);
  const [ajustes, setAjustes] = useState<ConstraintsAplicados | null>(null);

  const pistaViva = useCallback(() => {
    const pista = streamRef.current?.getVideoTracks()[0];
    return !!pista && pista.readyState === 'live';
  }, []);

  // ------------------------------------------------------------------
  // Wake lock
  // ------------------------------------------------------------------

  const pedirWakeLock = useCallback(async () => {
    if (!('wakeLock' in navigator)) {
      log.warn('Wake Lock no disponible (iOS < 16.4): la pantalla puede apagarse sola.');
      return;
    }
    try {
      wakeLockRef.current = await navigator.wakeLock.request('screen');
      log.ok('Wake lock tomado: la pantalla no se apaga.');
    } catch (e) {
      // Degradación silenciosa: no poder tomarlo no impide capturar.
      log.warn(`No se pudo tomar el wake lock: ${(e as Error).message}`);
    }
  }, [log]);

  // ------------------------------------------------------------------
  // Ciclo de vida del stream
  // ------------------------------------------------------------------

  const detener = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    wakeLockRef.current?.release().catch(() => undefined);
    wakeLockRef.current = null;
    setEstado('apagada');
  }, []);

  /**
   * Abre el stream y lo deja abierto. Reabrirlo en cada disparo agrega segundos de latencia
   * por captura y es la causa más común de que estos sistemas terminen sin usarse.
   */
  const iniciar = useCallback(async () => {
    if (iniciandoRef.current) return;
    iniciandoRef.current = true;
    setError(null);
    setEstado('iniciando');

    try {
      if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error(
          'getUserMedia no está disponible. Requiere HTTPS con certificado válido (o localhost).',
        );
      }

      streamRef.current?.getTracks().forEach((t) => t.stop());

      // Pedir la resolución explícitamente. Sin constraints, Safari entrega su modo por
      // defecto —480x640, apenas 0,3 MP— que no tiene nada que ver con lo que la cámara del
      // iPhone puede dar. Se piden como `ideal` y no `exact` para que el navegador degrade a
      // lo mejor disponible en vez de fallar si el modo pedido no existe.
      //
      // Se piden en orientación apaisada (lado largo como ancho) porque es el espacio en que
      // los dispositivos suelen enumerar sus modos de captura; si el track termina en
      // vertical, `dimensiones()` lo respeta sin recortar resolución.
      const largo = Math.max(anchoMax, altoMax);
      const corto = Math.min(anchoMax, altoMax);
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: 'environment',
          width: { ideal: largo },
          height: { ideal: corto },
        },
        audio: false,
      });
      streamRef.current = stream;

      const video = videoRef.current;
      if (video) {
        video.srcObject = stream;
        await video.play().catch(() => undefined);
      }

      const pista = stream.getVideoTracks()[0];
      const aplicados = await fijarAjustes(pista);
      setAjustes(aplicados);
      log.info(
        `Ajustes de cámara — aplicados: [${aplicados.aplicados.join(', ') || 'ninguno'}] · ` +
          `rechazados: [${aplicados.rechazados.join(', ') || 'ninguno'}]`,
      );
      const s = aplicados.settings;
      const anchoReal = Number(s.width) || 0;
      const altoReal = Number(s.height) || 0;
      const mp = (anchoReal * altoReal) / 1_000_000;
      log.info(
        `Resolución: pedida ${largo}x${corto} · obtenida ${anchoReal}x${altoReal} ` +
          `(${mp.toFixed(1)} MP) · frameRate ${s.frameRate ?? '—'}`,
      );

      // Si la cámara entregó bastante menos de lo pedido, conviene que se vea: puede ser un
      // límite del equipo, o que el modo pedido no exista y haya caído a uno mucho menor.
      const pedidos = largo * corto;
      if (anchoReal && altoReal && anchoReal * altoReal < pedidos * 0.5) {
        log.warn(
          `La cámara entregó menos de la mitad de los píxeles pedidos. Es el máximo que ` +
            `expone este equipo para captura de video; subir la configuración no va a cambiarlo.`,
        );
      }

      // Warm-up: los primeros fotogramas salen con la exposición sin converger.
      setEstado('calentando');
      await new Promise((r) => setTimeout(r, warmupMs));

      setEstado('lista');
      log.ok('Cámara lista.');
      await pedirWakeLock();
    } catch (e) {
      const msg = (e as Error).message;
      setError(msg);
      setEstado('error');
      log.error(`No se pudo iniciar la cámara: ${msg}`);
    } finally {
      iniciandoRef.current = false;
    }
  }, [altoMax, anchoMax, log, pedirWakeLock, warmupMs]);

  /**
   * La resolución sólo se puede cambiar reabriendo el stream: no es un parámetro de la
   * exportación, es del modo de captura de la cámara. Sin esto, bajar una resolución nueva
   * desde el backend no tendría ningún efecto hasta que alguien reiniciara la app — y el
   * contrato promete que se aplica en la siguiente captura.
   */
  const resolucionAbiertaRef = useRef<string | null>(null);
  useEffect(() => {
    const pedida = `${anchoMax}x${altoMax}`;
    if (estado !== 'lista') return;
    if (resolucionAbiertaRef.current === null) {
      resolucionAbiertaRef.current = pedida;
      return;
    }
    if (resolucionAbiertaRef.current === pedida) return;

    resolucionAbiertaRef.current = pedida;
    log.info(`La resolución cambió a ${pedida}: reabriendo la cámara para aplicarla.`);
    void iniciar();
  }, [altoMax, anchoMax, estado, iniciar, log]);

  /**
   * iOS suspende la cámara al perder el primer plano. Al volver se VERIFICA la pista: si
   * murió, se reinicializa. Nunca se asume que sobrevivió.
   */
  useEffect(() => {
    const alCambiarVisibilidad = () => {
      if (document.visibilityState !== 'visible') {
        log.warn('La app pasó a segundo plano: iOS suspende la cámara y no ejecuta nada.');
        return;
      }
      if (estado === 'apagada') return;

      if (!pistaViva()) {
        log.warn('Al volver, la pista de video estaba muerta. Reinicializando la cámara…');
        void iniciar();
      } else {
        log.info('De vuelta en primer plano; la cámara sobrevivió.');
        void pedirWakeLock(); // el lock siempre se pierde al ir a segundo plano
      }
    };

    document.addEventListener('visibilitychange', alCambiarVisibilidad);
    return () => document.removeEventListener('visibilitychange', alCambiarVisibilidad);
  }, [estado, iniciar, log, pedirWakeLock, pistaViva]);

  useEffect(() => () => detener(), [detener]);

  // ------------------------------------------------------------------
  // Captura
  // ------------------------------------------------------------------

  const capturar = useCallback(async () => {
    const video = videoRef.current;
    if (!video || estado !== 'lista' || !pistaViva()) {
      throw new CamaraNoListaError(
        `La cámara no está lista (estado: ${estado}, pista viva: ${pistaViva()}).`,
      );
    }
    const { blob, ancho, alto } = await capturarJpeg(video, anchoMax, altoMax, calidadJpeg);
    return {
      blob,
      ancho,
      alto,
      sha256: await sha256(blob),
      capturadaEn: Date.now(),
      constraints: ajustes
        ? {
            aplicados: ajustes.aplicados,
            rechazados: ajustes.rechazados,
            settings: ajustes.settings,
          }
        : undefined,
    };
  }, [ajustes, altoMax, anchoMax, calidadJpeg, estado, pistaViva]);

  return { videoRef, estado, error, ajustes, iniciar, detener, capturar, pistaViva };
}
