import { humidityDueForIrrigation } from './shared';
import type { RuleScenario } from './types';

/** Detail the simulator gives the repair it registers; it shows up in the dashboard history next to the user. */
export const REPAIR_DETAIL = 'Reparación de prueba desde el simulador';

/**
 * S-05, repair registration. The counterpart of the fault scenarios: it registers the repair of every device of the
 * target zone that the backend has marked as faulty (`POST /api/hardware/{id}/reparacion`), which clears the mark
 * and puts the device back under the autonomous control. With the dry soil pinned, the next pulse irrigates the sector
 * again.
 *
 * The simulator never marks a device as faulty or clears the mark by itself: it only uses the public repair endpoint,
 * the same one the dashboard uses. It looks for the faulty devices in the hardware registry (read-only).
 */
export const repair: RuleScenario = {
  key: 'REPAIR',
  label: 'Reparación (S-05)',
  description:
    'Registra la reparación de los dispositivos averiados de la zona: la falla se levanta y el siguiente pulso vuelve a regar el sector',
  branch: 'GLOBAL',
  backendRules: ['HardwareFaultService'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 45,
  conflicts: [
    ...['R02', 'R04', 'LOCK', 'NODE_OFFLINE', 'INVALID_READING', 'INCOMPLETE_HARDWARE'].map((other) => ({
      with: other,
      reason: () =>
        'La reparación necesita que el sector vuelva a regar con el sustrato seco: con bloqueo, nodo mudo, una humedad imposible o hardware incompleto la causa de que no riegue sería otra. Ejecutalo por separado.',
    })),
    ...['FAULT_NO_FLOW', 'FAULT_OVERFLOW', 'FAULT_SHADE_LIMIT'].map((other) => ({
      with: other,
      reason: () => 'Una falla y su reparación en la misma corrida no muestran nada: marcá la falla primero y reparala después.',
    })),
  ],
  apply: (ctx) => {
    const dry = humidityDueForIrrigation(ctx);
    return {
      pinnedReadings: { humSus: { value: dry.value, note: `${dry.note}: el sector vuelve a regar al quedar sano` } },
      repair: { detail: REPAIR_DETAIL },
      needsFaultyDevice: true,
      notes: [
        'se registra la reparación de los dispositivos averiados de la zona (POST /api/hardware/{id}/reparacion); el simulador no los marca ni los limpia por su cuenta',
        'la falla se levanta, queda el evento «Reparación» en el historial y el siguiente pulso vuelve a regar el sector',
        'la emisión automática tiene que estar activa: el sustrato seco viaja en la respuesta al pulso',
        'la cuenta de rol Servicio necesita `hardware.reparar` y `hardware.ver` (en una base ya sembrada, correr `migracion-permisos-hardware-reparar.sql` del backend)',
      ],
    };
  },
};
