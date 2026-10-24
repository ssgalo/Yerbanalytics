/* ============================================================
   Interruptor del nodo simulado (SIM_NODE_ACKS).

   Con un ESP32 real atendiendo la misma zona, el simulador no debe contestar los comandos
   de actuadores: el backend toma el primer ACK final por commandId, y un SUCCESS simulado
   cerraría el paso (o la dosis) con el actuador real todavía en movimiento. Lógica pura,
   sin leer el entorno, para poder probarla.
   ============================================================ */

export interface NodeAcksSetting {
  enabled: boolean;
  /** Presente si el valor no se entendió (se asume `on`). */
  warning?: string;
}

/** Interpreta SIM_NODE_ACKS: on/off, true/false, 1/0. Vacío = on; desconocido = on con advertencia. */
export function parseNodeAcks(raw: string | undefined): NodeAcksSetting {
  const v = (raw ?? '').trim().toLowerCase();
  if (v === '' || v === 'on' || v === 'true' || v === '1') return { enabled: true };
  if (v === 'off' || v === 'false' || v === '0') return { enabled: false };
  return {
    enabled: true,
    warning: `SIM_NODE_ACKS="${raw}" no es válido (on|off); se usa on.`,
  };
}

/** El simulador contesta como nodo sólo si los ACK están encendidos y la zona tiene un sensor simulado. */
export function shouldAnswerAsNode(args: { acksEnabled: boolean; zoneHasSimulatedSensor: boolean }): boolean {
  return args.acksEnabled && args.zoneHasSimulatedSensor;
}
