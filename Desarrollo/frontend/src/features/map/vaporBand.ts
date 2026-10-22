import type { Status, VaporBand } from '@/types/domain';

/**
 * Estado visual de cada tramo del DPV, para elegir su glifo y su tinta (nunca un hex). El DPV es informativo:
 * esto es sólo presentación y no mueve la salud de los sectores. La transición (0,3–0,5 kPa) no tiene alerta propia
 * en reglas_v2 §1.4, por eso se dibuja como el rango confortable.
 */
export const VAPOR_BAND_STATUS: Record<VaporBand, Status> = {
  SATURATED: 'warning',
  TRANSITION: 'ok',
  COMFORTABLE: 'ok',
  HIGH_DEMAND: 'warning',
  DRY_HEAT: 'critical',
};
