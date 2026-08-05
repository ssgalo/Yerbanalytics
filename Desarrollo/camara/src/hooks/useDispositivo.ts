import { useCallback, useEffect, useRef, useState } from 'react';
import * as api from '@/lib/contrato';
import type { ConfigCaptura, MotivoFallo, Orden } from '@/lib/contrato';
import * as almacen from '@/lib/almacen';
import { ColaOrdenes, dormir, esperaBackoff } from '@/lib/cola';
import { CamaraNoListaError } from './useCamara';
import type { Log } from './useLog';

/* ============================================================
   Sesión del dispositivo: credenciales, canal de órdenes, cola y envío.

   Cumple las obligaciones que el contrato le exige a cualquier cliente conforme:
   sostener el canal, encolar en vez de descartar, acusar todo descarte, reintentar con
   espera creciente, respaldar lo no enviado, drenar al volver la red, reportar cada
   resultado y latir. Ver contratos/camara/v1/README.md §5.
   ============================================================ */

export type EstadoConexion = 'desconectado' | 'conectando' | 'conectado';

/** Config por defecto: sólo para el arranque, antes de que el backend entregue la suya. */
export const CONFIG_INICIAL: ConfigCaptura = {
  anchoMax: 1920,
  altoMax: 1080,
  calidadJpeg: 0.85,
  warmupMs: 500,
  heartbeatSeg: 15,
  timeoutOrdenSeg: 60,
  maxColaOrdenes: 20,
};

/** Reintentos inmediatos antes de mandar la imagen al respaldo persistente. */
const REINTENTOS_INMEDIATOS = 5;

interface Opciones {
  log: Log;
  /** Config vigente. Vive en App porque la comparten este hook y la cámara. */
  config: ConfigCaptura;
  /** El backend es la fuente de la configuración; acá sólo se propaga hacia arriba. */
  onConfig: (config: ConfigCaptura) => void;
  /** Toma la foto. La provee useCamara; devuelve null si la cámara no está lista. */
  capturar: () => Promise<{
    blob: Blob;
    ancho: number;
    alto: number;
    sha256: string;
    capturadaEn: number;
    constraints?: Record<string, unknown>;
  }>;
  camaraLista: boolean;
}

export function useDispositivo({ log, config, onConfig, capturar, camaraLista }: Opciones) {
  const [credencial, setCredencial] = useState<almacen.CredencialGuardada | null>(null);
  const [cargandoCredencial, setCargandoCredencial] = useState(true);
  const [conexion, setConexion] = useState<EstadoConexion>('desconectado');
  const [capturasOk, setCapturasOk] = useState(0);
  const [capturasError, setCapturasError] = useState(0);
  const [pendientes, setPendientes] = useState(0);
  const [ultima, setUltima] = useState<{ url: string; capturaId: string; ts: number } | null>(null);

  const tokenRef = useRef<string | null>(null);
  const colaRef = useRef(new ColaOrdenes(CONFIG_INICIAL.maxColaOrdenes));
  const procesandoRef = useRef(false);
  const esRef = useRef<EventSource | null>(null);
  const camaraListaRef = useRef(camaraLista);
  camaraListaRef.current = camaraLista;

  /**
   * `capturar` también va por ref, y no es un detalle de estilo.
   *
   * El listener del canal SSE se registra UNA vez, al abrir el canal — que ocurre apenas hay
   * credencial, antes de que el operario toque "Iniciar cámara". Si el handler cerrara sobre
   * `capturar` directamente, se quedaría con la versión de cuando la cámara estaba apagada, y
   * TODA orden fallaría con "la cámara no está lista" aunque la cámara estuviera funcionando
   * perfectamente. La captura manual seguiría andando (esa sí usa la versión del render
   * actual), lo que hace el síntoma especialmente confuso.
   */
  const capturarRef = useRef(capturar);
  capturarRef.current = capturar;

  // ------------------------------------------------------------------
  // Credenciales
  // ------------------------------------------------------------------

  useEffect(() => {
    void (async () => {
      try {
        const guardada = await almacen.leerCredencial();
        setCredencial(guardada ?? null);
        if (guardada) log.info(`Credencial encontrada para ${guardada.dispositivoId}.`);
        else log.info('Sin credencial: hay que vincular el dispositivo.');
        await almacen.pedirPersistencia();
        setPendientes(await almacen.contarPendientes());
      } finally {
        setCargandoCredencial(false);
      }
    })();
  }, [log]);

  const vincular = useCallback(
    async (codigo: string, nombre: string) => {
      const cred = await api.enrolar(codigo, nombre, `PWA/${navigator.userAgent}`);
      const guardada = { ...cred, nombre };
      await almacen.guardarCredencial(guardada);
      setCredencial(guardada);
      log.ok(`Dispositivo vinculado como ${cred.dispositivoId}.`);
    },
    [log],
  );

  const olvidarCredencial = useCallback(async () => {
    await almacen.borrarCredencial();
    setCredencial(null);
    tokenRef.current = null;
    esRef.current?.close();
    esRef.current = null;
    setConexion('desconectado');
    log.warn('Credencial descartada. Volvé a vincular el dispositivo.');
  }, [log]);

  /** Renueva el token de forma proactiva, sin esperar a comerse un 401. */
  const renovarToken = useCallback(async (): Promise<string | null> => {
    if (!credencial) return null;
    try {
      const { accessToken, expiraEnSeg } = await api.obtenerToken(credencial.refreshToken);
      tokenRef.current = accessToken;
      log.info(`Token renovado (vence en ${expiraEnSeg} s).`);
      return accessToken;
    } catch (e) {
      if (e instanceof api.CredencialRechazadaError) {
        await olvidarCredencial();
      } else {
        log.error(`No se pudo renovar el token: ${(e as Error).message}`);
      }
      return null;
    }
  }, [credencial, log, olvidarCredencial]);

  // ------------------------------------------------------------------
  // Envío de una captura
  // ------------------------------------------------------------------

  const entregar = useCallback(
    async (ordenId: string, jpeg: Blob, meta: api.MetadataImagen): Promise<boolean> => {
      for (let intento = 1; intento <= REINTENTOS_INMEDIATOS; intento++) {
        const token = tokenRef.current ?? (await renovarToken());
        if (!token) break;
        try {
          const creada = await api.subirImagen(token, ordenId, jpeg, meta);
          setUltima({ url: URL.createObjectURL(jpeg), capturaId: creada.capturaId, ts: Date.now() });
          setCapturasOk((n) => n + 1);
          log.ok(`Captura ${creada.capturaId} entregada (orden ${ordenId.slice(0, 8)}…).`);
          await almacen.quitarPendiente(ordenId);
          setPendientes(await almacen.contarPendientes());
          return true;
        } catch (e) {
          // El contrato manda tratar el 409 como éxito: la subida anterior sí llegó.
          if (e instanceof api.OrdenYaResueltaError) {
            log.info(`La orden ${ordenId.slice(0, 8)}… ya tenía imagen: se cuenta como entregada.`);
            setCapturasOk((n) => n + 1);
            await almacen.quitarPendiente(ordenId);
            setPendientes(await almacen.contarPendientes());
            return true;
          }
          // 404/403: reintentar no arregla nada.
          if (e instanceof api.OrdenNoEntregableError) {
            log.error(`Orden ${ordenId.slice(0, 8)}… no entregable: ${e.message}`);
            await almacen.quitarPendiente(ordenId);
            setPendientes(await almacen.contarPendientes());
            return false;
          }
          if (e instanceof api.CredencialRechazadaError) {
            tokenRef.current = null;
            continue;
          }
          const espera = esperaBackoff(intento);
          log.warn(
            `Fallo al enviar (intento ${intento}/${REINTENTOS_INMEDIATOS}): ` +
              `${(e as Error).message}. Reintento en ${Math.round(espera / 1000)} s.`,
          );
          await dormir(espera);
        }
      }
      return false;
    },
    [log, renovarToken],
  );

  const acusar = useCallback(
    async (ordenId: string, motivo: MotivoFallo, detalle?: string) => {
      const token = tokenRef.current ?? (await renovarToken());
      if (!token) return;
      try {
        await api.acusarFallo(token, ordenId, motivo, detalle);
        log.warn(`Orden ${ordenId.slice(0, 8)}… acusada como ${motivo}.`);
      } catch (e) {
        log.error(`No se pudo acusar el fallo: ${(e as Error).message}`);
      }
    },
    [log, renovarToken],
  );

  // ------------------------------------------------------------------
  // Procesamiento de la cola de órdenes
  // ------------------------------------------------------------------

  const procesarCola = useCallback(async () => {
    if (procesandoRef.current) return;
    procesandoRef.current = true;
    try {
      for (let orden = colaRef.current.desencolar(); orden; orden = colaRef.current.desencolar()) {
        if (!camaraListaRef.current) {
          await acusar(orden.ordenId, 'CAMARA_NO_LISTA', 'La cámara no estaba inicializada.');
          setCapturasError((n) => n + 1);
          continue;
        }

        let foto;
        try {
          foto = await capturarRef.current();
        } catch (e) {
          // Cada causa tiene su motivo en el contrato: mandar todo como EXPORTACION_FALLIDA
          // hace que el backend clasifique mal y que el log apunte al lugar equivocado.
          const motivo = e instanceof CamaraNoListaError ? 'CAMARA_NO_LISTA' : 'EXPORTACION_FALLIDA';
          await acusar(orden.ordenId, motivo, (e as Error).message);
          setCapturasError((n) => n + 1);
          continue;
        }

        const meta: api.MetadataImagen = {
          ancho: foto.ancho,
          alto: foto.alto,
          sha256: foto.sha256,
          capturadaEn: foto.capturadaEn,
          constraints: foto.constraints,
        };

        const entregada = await entregar(orden.ordenId, foto.blob, meta);
        if (entregada) continue;

        // Agotados los reintentos inmediatos: al respaldo persistente para cuando vuelva la red.
        const descartadas = await almacen.encolarEnvio({
          ordenId: orden.ordenId,
          jpeg: foto.blob,
          meta,
          bytes: foto.blob.size,
          intentos: REINTENTOS_INMEDIATOS,
        });
        setPendientes(await almacen.contarPendientes());
        setCapturasError((n) => n + 1);
        log.warn(`Captura de ${orden.ordenId.slice(0, 8)}… guardada para reintentar.`);
        if (descartadas > 0) {
          log.error(
            `El respaldo local llegó a su tope: se descartaron ${descartadas} captura(s) vieja(s).`,
          );
        }
      }
    } finally {
      procesandoRef.current = false;
    }
  }, [acusar, entregar, log]);

  /** Drena el respaldo persistente. Se llama al volver la conectividad y al reconectar. */
  const drenarRespaldo = useCallback(async () => {
    const guardadas = await almacen.listarPendientes();
    if (guardadas.length === 0) return;
    log.info(`Reintentando ${guardadas.length} captura(s) pendiente(s) de envío…`);
    for (const p of guardadas) {
      await almacen.marcarIntento(p.ordenId);
      await entregar(p.ordenId, p.jpeg, p.meta);
    }
    setPendientes(await almacen.contarPendientes());
  }, [entregar, log]);

  // ------------------------------------------------------------------
  // Canal de órdenes
  // ------------------------------------------------------------------

  const conectar = useCallback(async () => {
    if (!credencial) return;
    setConexion('conectando');

    const token = await renovarToken();
    if (!token) {
      setConexion('desconectado');
      return;
    }

    try {
      const cfg = await api.obtenerConfig(token);
      onConfig(cfg);
      colaRef.current.setTope(cfg.maxColaOrdenes);
      log.info(
        `Config del backend: ${cfg.anchoMax}x${cfg.altoMax} · calidad ${cfg.calidadJpeg} · ` +
          `warm-up ${cfg.warmupMs} ms · cola ${cfg.maxColaOrdenes}`,
      );
    } catch (e) {
      log.warn(`No se pudo leer la configuración: ${(e as Error).message}`);
    }

    esRef.current?.close();
    // EventSource no permite fijar headers, así que el token va por query string. Es la
    // única ruta del contrato donde eso se admite; el resto usa Authorization.
    const es = new EventSource(api.streamUrl(token));
    esRef.current = es;

    es.onopen = () => {
      setConexion('conectado');
      log.ok('Canal de órdenes abierto.');
      void drenarRespaldo();
    };

    es.addEventListener('ping', () => setConexion('conectado'));

    es.addEventListener('config', (evento) => {
      try {
        const cfg = JSON.parse((evento as MessageEvent).data) as ConfigCaptura;
        onConfig(cfg);
        colaRef.current.setTope(cfg.maxColaOrdenes);
        log.info('Configuración actualizada desde el backend.');
      } catch {
        log.warn('Llegó un evento de configuración ilegible.');
      }
    });

    es.addEventListener('orden', (evento) => {
      let orden: Orden;
      try {
        orden = JSON.parse((evento as MessageEvent).data) as Orden;
      } catch {
        log.warn('Llegó una orden ilegible.');
        return;
      }
      log.info(
        `Orden ${orden.ordenId.slice(0, 8)}… → sector ${orden.sectorId}, riel ${orden.posicionRiel}` +
          (orden.intento > 1 ? ` (reintento ${orden.intento})` : ''),
      );

      const { descartada } = colaRef.current.encolar(orden);
      if (descartada) {
        // Nunca en silencio: si el backend no se entera, la orden espera su vencimiento.
        log.error(`Cola llena: se descartó la orden ${descartada.ordenId.slice(0, 8)}….`);
        void acusar(descartada.ordenId, 'COLA_LLENA', 'Tope de cola del dispositivo alcanzado.');
        setCapturasError((n) => n + 1);
      }
      void procesarCola();
    });

    es.onerror = () => {
      // EventSource reconecta solo; sólo se refleja el estado.
      setConexion('conectando');
    };
  }, [acusar, credencial, drenarRespaldo, log, onConfig, procesarCola, renovarToken]);

  /** Conecta en cuanto hay credencial, y se desconecta al perderla. */
  useEffect(() => {
    if (!credencial) return;
    void conectar();
    return () => {
      esRef.current?.close();
      esRef.current = null;
    };
    // conectar cambia de identidad con cada render; sólo interesa reconectar al cambiar la
    // credencial, porque EventSource ya se reconecta solo ante cortes de red.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [credencial]);

  /** Renovación proactiva del token, y reapertura del canal con el token nuevo. */
  useEffect(() => {
    if (!credencial) return;
    const id = setInterval(() => void conectar(), 10 * 60 * 1000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [credencial]);

  /** Señal de vida. Sin ella el backend sólo se enteraría de una caída al fallar una captura. */
  useEffect(() => {
    if (!credencial) return;
    const enviar = async () => {
      const token = tokenRef.current;
      if (!token) return;
      try {
        await api.heartbeat(token, {
          capturaListo: camaraListaRef.current,
          capturasOk,
          capturasError,
          pendientesEnvio: pendientes,
        });
      } catch {
        // Un heartbeat perdido no es noticia: el siguiente lo cubre.
      }
    };
    void enviar();
    const id = setInterval(enviar, config.heartbeatSeg * 1000);
    return () => clearInterval(id);
  }, [capturasError, capturasOk, config.heartbeatSeg, credencial, pendientes]);

  /** Al volver la red, drenar lo que quedó guardado. */
  useEffect(() => {
    const online = () => {
      log.info('Conectividad restablecida.');
      void drenarRespaldo();
    };
    const offline = () => log.warn('Sin conectividad.');
    window.addEventListener('online', online);
    window.addEventListener('offline', offline);
    return () => {
      window.removeEventListener('online', online);
      window.removeEventListener('offline', offline);
    };
  }, [drenarRespaldo, log]);

  /** Captura manual de prueba: fabrica una orden local sin pasar por el backend. */
  const capturarManual = useCallback(async () => {
    try {
      const foto = await capturarRef.current();
      setUltima({ url: URL.createObjectURL(foto.blob), capturaId: 'prueba local', ts: Date.now() });
      log.ok(
        `Captura de prueba: ${foto.ancho}x${foto.alto}, ${Math.round(foto.blob.size / 1024)} kB. ` +
          'No se envía al backend (no tiene orden que la correlacione).',
      );
    } catch (e) {
      log.error(`Captura de prueba fallida: ${(e as Error).message}`);
    }
  }, [log]);

  return {
    credencial,
    cargandoCredencial,
    conexion,
    capturasOk,
    capturasError,
    pendientes,
    ultima,
    vincular,
    olvidarCredencial,
    capturarManual,
    reconectar: conectar,
  };
}
