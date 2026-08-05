/* ============================================================
   Captura del fotograma y utilidades de cámara.

   En Safari NO existe la ImageCapture API: no hay `takePhoto()`. La única vía es dibujar un
   fotograma del <video> en un <canvas> y exportarlo con `toBlob`. No es una preferencia de
   estilo — es la restricción que define cómo se toma la foto en este dispositivo.
   ============================================================ */

/** Ajustes que el navegador aceptó de verdad, para poder juzgar la consistencia entre fotos. */
export interface ConstraintsAplicados {
  soportaExposicion: boolean;
  soportaBalanceBlancos: boolean;
  aplicados: string[];
  rechazados: string[];
  settings: Record<string, unknown>;
  capabilities: Record<string, unknown>;
}

/**
 * Intenta fijar exposición y balance de blancos. Best effort declarado: Safari los soporta
 * de forma muy parcial y un fallo acá NUNCA debe abortar la captura.
 *
 * Lo importante es lo que se devuelve: qué se pudo fijar realmente. Sin esa evidencia no hay
 * forma de decidir con datos si las imágenes son lo bastante consistentes para entrenar el
 * modelo, y se terminaría discutiendo de memoria.
 */
export async function fijarAjustes(track: MediaStreamTrack): Promise<ConstraintsAplicados> {
  const capabilities = (
    typeof track.getCapabilities === 'function' ? track.getCapabilities() : {}
  ) as Record<string, unknown>;

  const soportaExposicion = 'exposureMode' in capabilities;
  const soportaBalanceBlancos = 'whiteBalanceMode' in capabilities;

  const aplicados: string[] = [];
  const rechazados: string[] = [];

  const intentar = async (nombre: string, constraint: MediaTrackConstraints) => {
    try {
      await track.applyConstraints(constraint);
      aplicados.push(nombre);
    } catch {
      rechazados.push(nombre);
    }
  };

  if (soportaExposicion) {
    await intentar('exposureMode=continuous', {
      advanced: [{ exposureMode: 'continuous' } as MediaTrackConstraintSet],
    });
  } else {
    rechazados.push('exposureMode (no soportado)');
  }

  if (soportaBalanceBlancos) {
    await intentar('whiteBalanceMode=continuous', {
      advanced: [{ whiteBalanceMode: 'continuous' } as MediaTrackConstraintSet],
    });
  } else {
    rechazados.push('whiteBalanceMode (no soportado)');
  }

  const settings = (
    typeof track.getSettings === 'function' ? track.getSettings() : {}
  ) as Record<string, unknown>;

  return { soportaExposicion, soportaBalanceBlancos, aplicados, rechazados, settings, capabilities };
}

/**
 * Escala preservando la relación de aspecto, sin agrandar más allá del original.
 *
 * El límite se aplica de forma **agnóstica a la orientación**: se compara el lado largo del
 * video contra el lado largo del máximo, y el corto contra el corto. Comparar ancho con ancho
 * y alto con alto tiraría resolución a la basura cuando el video viene en vertical —que es el
 * caso normal de un teléfono montado en el riel—: un video de 1080x1920 contra un máximo de
 * 1920x1080 se reduciría a 810x1080 en lugar de quedarse tal cual.
 */
export function dimensiones(
  anchoVideo: number,
  altoVideo: number,
  anchoMax: number,
  altoMax: number,
): { ancho: number; alto: number } {
  if (!anchoVideo || !altoVideo) return { ancho: anchoMax, alto: altoMax };

  const largoVideo = Math.max(anchoVideo, altoVideo);
  const cortoVideo = Math.min(anchoVideo, altoVideo);
  const largoMax = Math.max(anchoMax, altoMax);
  const cortoMax = Math.min(anchoMax, altoMax);

  const escala = Math.min(largoMax / largoVideo, cortoMax / cortoVideo, 1);
  return {
    ancho: Math.max(1, Math.round(anchoVideo * escala)),
    alto: Math.max(1, Math.round(altoVideo * escala)),
  };
}

/** Dibuja el fotograma actual del video y lo exporta como JPEG. */
export async function capturarJpeg(
  video: HTMLVideoElement,
  anchoMax: number,
  altoMax: number,
  calidad: number,
): Promise<{ blob: Blob; ancho: number; alto: number }> {
  const { ancho, alto } = dimensiones(video.videoWidth, video.videoHeight, anchoMax, altoMax);

  const canvas = document.createElement('canvas');
  canvas.width = ancho;
  canvas.height = alto;

  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('No se pudo obtener el contexto 2D del canvas.');
  ctx.drawImage(video, 0, 0, ancho, alto);

  const blob = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, 'image/jpeg', calidad),
  );
  if (!blob) throw new Error('El fotograma no pudo exportarse a JPEG.');

  return { blob, ancho, alto };
}

/** SHA-256 hexadecimal, el formato que declara el contrato. */
export async function sha256(blob: Blob): Promise<string> {
  const buffer = await blob.arrayBuffer();
  const hash = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}
