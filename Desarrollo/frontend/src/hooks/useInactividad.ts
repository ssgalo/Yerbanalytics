/* ============================================================
   Cierre por inactividad del lado del dashboard (HU-01 CA-03, design D4).

   La autoridad es el backend: vence la sesión si no recibió actividad en `inactividadMin`, y
   los sondeos (GET) NO cuentan. Este hook hace dos cosas para que eso funcione bien:

   1. Avisarle al backend que hay alguien: ante interacción real (puntero, teclado, rueda, toque,
      scroll) manda `POST /api/auth/actividad`, como mucho una vez por minuto.
   2. Mostrar el login justo al vencer, sin esperar al próximo sondeo: un temporizador local con
      el mismo valor del perfil, que además avisa un minuto antes.

   Las pestañas comparten la actividad por `BroadcastChannel`: usar la de adelante mantiene viva
   la de atrás (y el "último ping" también se comparte, porque la sesión —la cookie— es una sola).
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';

export const NOMBRE_CANAL_ACTIVIDAD = 'yerbanalytics-actividad';

/** Como mucho un ping al backend por minuto. */
export const PING_CADA_MS = 60_000;
/** El aviso aparece un minuto antes del cierre. */
export const AVISO_ANTES_MS = 60_000;

const EVENTOS_INTERACCION = ['pointerdown', 'keydown', 'wheel', 'touchstart', 'scroll'] as const;

/** Lo que una pestaña le cuenta a las otras: cuándo hubo interacción y cuándo fue el último ping. */
export interface MensajeActividad {
  en: number;
  pingEn: number;
}

/** La parte de `BroadcastChannel` que se usa; inyectable para probar dos pestañas sin navegador. */
export interface CanalActividad {
  postMessage(mensaje: MensajeActividad): void;
  onmessage: ((ev: MessageEvent<MensajeActividad>) => void) | null;
  close(): void;
}

function canalDelNavegador(): CanalActividad | null {
  return typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(NOMBRE_CANAL_ACTIVIDAD);
}

export interface OpcionesInactividad {
  /** Tiempo máximo de inactividad vigente (el del perfil). */
  inactividadMin: number;
  /** El ping al backend. Sus errores se ignoran: si la sesión ya cerró, el 401 llega por su canal. */
  registrarActividad: () => Promise<void>;
  /** Se llama una vez, cuando el temporizador local vence. */
  alVencer: () => void;
  /** Fábrica del canal entre pestañas. Por defecto, `BroadcastChannel` (o ninguno si no existe). */
  crearCanal?: () => CanalActividad | null;
  /** Dónde se escucha la interacción. Por defecto, `window` (inyectable para probar dos "pestañas"). */
  objetivo?: EventTarget;
}

export interface EstadoInactividad {
  /** Falta un minuto o menos para el cierre. */
  aviso: boolean;
  segundosRestantes: number;
  /** Cuenta como interacción (p. ej. el botón "Seguir conectado"). */
  mantenerViva: () => void;
}

export function useInactividad({
  inactividadMin,
  registrarActividad,
  alVencer,
  crearCanal = canalDelNavegador,
  objetivo,
}: OpcionesInactividad): EstadoInactividad {
  const limiteMs = inactividadMin * 60_000;
  const ultimaActividad = useRef(Date.now());
  const ultimoPing = useRef(0);
  const vencida = useRef(false);
  const canal = useRef<CanalActividad | null>(null);
  const [restanteMs, setRestanteMs] = useState(limiteMs);

  // Los callbacks cambian de identidad con cada render del padre; los listeners no se re-registran por eso.
  const registrarRef = useRef(registrarActividad);
  const alVencerRef = useRef(alVencer);
  useEffect(() => {
    registrarRef.current = registrarActividad;
    alVencerRef.current = alVencer;
  }, [registrarActividad, alVencer]);

  const marcar = useCallback(
    (en: number, pingEn: number) => {
      ultimaActividad.current = Math.max(ultimaActividad.current, en);
      ultimoPing.current = Math.max(ultimoPing.current, pingEn);
      // El aviso desaparece en el momento, no en el próximo tic.
      setRestanteMs(ultimaActividad.current + limiteMs - Date.now());
    },
    [limiteMs],
  );

  const alInteractuar = useCallback(() => {
    if (vencida.current) return;
    const ahora = Date.now();
    let pingEn = ultimoPing.current;
    if (ahora - ultimoPing.current >= PING_CADA_MS) {
      pingEn = ahora;
      void registrarRef.current().catch(() => undefined);
    }
    marcar(ahora, pingEn);
    canal.current?.postMessage({ en: ahora, pingEn });
  }, [marcar]);

  // Listeners de interacción y canal entre pestañas.
  useEffect(() => {
    const c = crearCanal();
    canal.current = c;
    if (c) c.onmessage = (ev) => marcar(ev.data.en, ev.data.pingEn);

    // En captura: `scroll` no burbujea y el scroll de la app vive en un <main>, no en el body.
    const destino = objetivo ?? window;
    EVENTOS_INTERACCION.forEach((e) => destino.addEventListener(e, alInteractuar, { capture: true, passive: true }));
    return () => {
      EVENTOS_INTERACCION.forEach((e) => destino.removeEventListener(e, alInteractuar, { capture: true }));
      c?.close();
      canal.current = null;
    };
  }, [crearCanal, objetivo, alInteractuar, marcar]);

  // Temporizador local: cuenta regresiva y cierre al vencer.
  useEffect(() => {
    const tic = () => {
      const restante = ultimaActividad.current + limiteMs - Date.now();
      setRestanteMs(restante);
      if (restante <= 0 && !vencida.current) {
        vencida.current = true;
        alVencerRef.current();
      }
    };
    tic();
    const id = setInterval(tic, 1000);
    return () => clearInterval(id);
  }, [limiteMs]);

  return {
    aviso: restanteMs > 0 && restanteMs <= AVISO_ANTES_MS,
    segundosRestantes: Math.max(0, Math.ceil(restanteMs / 1000)),
    mantenerViva: alInteractuar,
  };
}
