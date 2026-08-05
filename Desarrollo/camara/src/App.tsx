import { useState } from 'react';
import { useCamara } from '@/hooks/useCamara';
import { useDispositivo, CONFIG_INICIAL } from '@/hooks/useDispositivo';
import { useLog } from '@/hooks/useLog';
import type { ConfigCaptura } from '@/lib/contrato';
import { Vinculacion } from '@/components/Vinculacion';
import { Panel } from '@/components/Panel';

/**
 * App de captura. Implementación de referencia del contrato `camara/v1`.
 *
 * Es el software del dispositivo, no una vista del dashboard: se reemplaza en bloque por un
 * cliente Android que cumpla el mismo contrato, sin tocar el backend.
 *
 * La configuración de captura vive acá y no dentro de un hook porque la comparten los dos:
 * `useDispositivo` la recibe del backend y `useCamara` la usa para tomar la foto. Arranca con
 * los defaults y se reemplaza en cuanto el canal entrega la real — que es lo que pide el
 * contrato: aplicar en la siguiente captura, sin reiniciar el dispositivo.
 */
export function App() {
  const { entradas, log } = useLog();
  const [config, setConfig] = useState<ConfigCaptura>(CONFIG_INICIAL);

  const camara = useCamara({
    anchoMax: config.anchoMax,
    altoMax: config.altoMax,
    calidadJpeg: config.calidadJpeg,
    warmupMs: config.warmupMs,
    log,
  });

  const dispositivo = useDispositivo({
    log,
    config,
    onConfig: setConfig,
    capturar: camara.capturar,
    camaraLista: camara.estado === 'lista',
  });

  if (dispositivo.cargandoCredencial) {
    return <div style={{ padding: 24, color: 'var(--texto-2)' }}>Cargando…</div>;
  }

  if (!dispositivo.credencial) {
    return <Vinculacion onVincular={dispositivo.vincular} />;
  }

  return (
    <Panel
      videoRef={camara.videoRef}
      estadoCamara={camara.estado}
      errorCamara={camara.error}
      conexion={dispositivo.conexion}
      dispositivoId={dispositivo.credencial.dispositivoId}
      capturasOk={dispositivo.capturasOk}
      capturasError={dispositivo.capturasError}
      pendientes={dispositivo.pendientes}
      ultima={dispositivo.ultima}
      entradas={entradas}
      onIniciar={camara.iniciar}
      onCapturarPrueba={dispositivo.capturarManual}
      onReconectar={dispositivo.reconectar}
      onDesvincular={dispositivo.olvidarCredencial}
    />
  );
}
