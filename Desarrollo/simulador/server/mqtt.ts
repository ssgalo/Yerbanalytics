/* ============================================================
   Simulator MQTT publisher + command subscriber.

   Uplink: the simulator publishes directly to the broker with the same payload as the ESP32
   firmware, on the same topic (or, with the backend in demo mode, on the separate demo topic). The backend ingests it through its normal pipeline and has
   no way of knowing there was a simulator on the other end.

   Downlink: the simulator subscribes to the command wildcard topic so that orders sent by the
   backend rules engine are visible in the console. This validates the full cyber-physical loop
   (measure → detect → decide → command) without needing physical hardware. For the macro-zones
   it simulates (the ones with a simulated sensor) it also answers each command with the ack the
   firmware would send (queue of loads included: `nodeQueue.ts`); commands for other zones belong to
   a real node and are only logged.

   Error messages reaching the UI are in Spanish; console output is internal, so it is not.
   ============================================================ */
import mqtt, { type MqttClient } from 'mqtt';
import { config } from './config.ts';
import {
  ackTopic,
  errorsTopic,
  isReadNow,
  isZoneShadeCommand,
  parseCommandTopic,
  parseZoneCommandTopic,
  telemetryTopicFor,
  valveDurationWarning,
  type ActuatorAck,
  type ActuatorCommand,
  type NodeErrorPayload,
  type TelemetryPayload,
} from './contract.ts';
import { demoMode } from './demo-mode.ts';
import { answerReadNow } from './emission.ts';
import { SimulatedNodes } from './nodeQueue.ts';
import { listSensors } from './store.ts';
import { shouldAnswerAsNode } from './nodeAcks.ts';

/** Wildcard topic for all sector command messages. Mirrors contrato.h :: contratoTopicComando. */
const COMMAND_TOPIC_WILDCARD = 'nursery/zone/+/sector/+/command';

/**
 * Wildcard topic for zone-level commands: LEER_AHORA and the shade mesh's `shade SET` (the mesh is the macro-zone's).
 * Mirrors contrato.h :: contratoTopicComandoZona.
 */
const ZONE_COMMAND_TOPIC_WILDCARD = 'nursery/zone/+/command';

/** How long to wait for a publish acknowledgement before giving up. */
const PUBLISH_TIMEOUT_MS = 5000;

let client: MqttClient | null = null;

/** Last reported error cause, so the same message is not repeated on every retry. */
let lastError: string | null = null;

/** Publish failure. The server maps it to a 502. */
export class PublishFailed extends Error {}

/**
 * Usable description of an MQTT error. Connection errors arrive with an empty `message` and
 * only a `code` (ECONNREFUSED, ENOTFOUND…), which is precisely the useful part.
 */
function describeError(e: unknown): string {
  const code = (e as { code?: string } | null)?.code;
  const message = e instanceof Error && e.message ? e.message : '';
  return message || code || 'unknown cause';
}

/**
 * Connects to the broker and keeps the connection alive. Called once at startup. Reconnection
 * is automatic: if the broker goes down and comes back, the simulator re-attaches on its own.
 */
export function connect(): void {
  if (client) return;
  // The client id carries the pid so two simulators on the same machine do not evict each
  // other from the broker.
  client = mqtt.connect(config.mqttUrl, {
    clientId: `${config.mqttClientId}-${process.pid}`,
    reconnectPeriod: 5000,
    connectTimeout: 5000,
    clean: true,
  });

  console.log(
    config.nodeAcks
      ? '[simulator] nodo simulado: ACK de actuadores encendidos (SIM_NODE_ACKS=on) — el simulador contesta los comandos de sus zonas'
      : '[simulator] nodo simulado: ACK de actuadores apagados (SIM_NODE_ACKS=off) — los comandos los contesta el hardware real',
  );

  client.on('connect', () => {
    lastError = null;
    console.log(`[simulator] connected to broker ${config.mqttUrl}`);

    // Zone-level commands: the backend's periodic LEER_AHORA pull.
    client!.subscribe(ZONE_COMMAND_TOPIC_WILDCARD, { qos: 1 }, (err) => {
      if (err) {
        console.error(`[simulator] failed to subscribe to zone commands: ${err.message}`);
      } else {
        console.log(`[simulator] subscribed to zone command wildcard: ${ZONE_COMMAND_TOPIC_WILDCARD}`);
      }
    });

    // Subscribe to all sector command topics to validate the downlink from the rules engine.
    client!.subscribe(COMMAND_TOPIC_WILDCARD, { qos: 1 }, (err) => {
      if (err) {
        console.error(`[simulator] failed to subscribe to command topics: ${err.message}`);
      } else {
        console.log(`[simulator] subscribed to command wildcard: ${COMMAND_TOPIC_WILDCARD}`);
      }
    });
  });

  // Log incoming commands from the backend rules engine, and ack the ones for simulated zones.
  // In production this role is played by the ESP32 nodo_actuador / nodo_combinado firmware.
  client.on('message', (topic, message) => {
    if (parseZoneCommandTopic(topic)) {
      handleZoneCommand(topic, message);
      return;
    }
    try {
      const payload = JSON.parse(message.toString()) as ActuatorCommand;
      const params = payload.parametros
        ? ' params=' + JSON.stringify(payload.parametros)
        : '';
      console.log(
        `[simulator] ← COMMAND topic=${topic}` +
          ` actuador=${payload.actuador ?? '?'}` +
          ` accion=${payload.accion ?? '?'}` +
          params +
          ` commandId=${payload.commandId ?? '?'}`,
      );
      const warning = valveDurationWarning(payload);
      if (warning) console.warn(`[simulator] WARNING ${warning}`);
      scheduleAck(topic, payload);
    } catch {
      console.log(`[simulator] ← COMMAND topic=${topic} (unparseable payload)`);
    }
  });

  // Without this handler a downed broker takes the process with it via an uncaught error.
  // Reported once per cause: with a 5s reconnect loop, logging every attempt floods the console.
  client.on('error', (e) => {
    const cause = describeError(e);
    if (cause === lastError) return;
    lastError = cause;
    console.error(
      `[simulator] no connection to broker ${config.mqttUrl}: ${cause}.` +
        ' Retrying in the background; publishing is unavailable meanwhile.',
    );
  });
}

/**
 * Routes a zone-level command: a LEER_AHORA is answered by the emission; a `shade SET` goes to the simulated node of the
 * zone, which acks it on the zone ack topic (the mesh has no sector); anything else is ignored.
 */
function handleZoneCommand(topic: string, message: Buffer): void {
  const target = parseZoneCommandTopic(topic);
  if (!target) return;
  let payload: unknown;
  try {
    payload = JSON.parse(message.toString());
  } catch {
    console.warn(`[simulator] ← ZONE COMMAND topic=${topic} (unparseable payload, ignored)`);
    return;
  }
  if (isZoneShadeCommand(payload)) {
    console.log(
      `[simulator] ← COMMAND topic=${topic} actuador=shade accion=${payload.accion ?? '?'}` +
        (payload.parametros ? ' params=' + JSON.stringify(payload.parametros) : '') +
        ` commandId=${payload.commandId ?? '?'}`,
    );
    if (answersAsNode(target.zonaId)) {
      simulatedNodes.receive(target.zonaId, '', payload);
    }
    return;
  }
  if (!isReadNow(payload)) {
    console.warn(`[simulator] ← ZONE COMMAND topic=${topic} (unknown accion, ignored)`);
    return;
  }
  console.log(`[simulator] ← LEER_AHORA zone=${target.zonaId}`);
  void answerReadNow(target.zonaId);
}

/** Whether the simulated node answers this zone's actuator commands (SIM_NODE_ACKS on and a simulated sensor there). */
function answersAsNode(zonaId: string): boolean {
  return shouldAnswerAsNode({
    acksEnabled: config.nodeAcks,
    zoneHasSimulatedSensor: listSensors().some((s) => s.zonaId === zonaId),
  });
}

/**
 * Hands the command to the simulated node of its macro-zone, which queues loads like the firmware does and emits
 * the intermediate and final acks (add-cola-actuadores-nodo). Only for macro-zones with a simulated sensor: a
 * command for another zone is a real node's to answer.
 */
function scheduleAck(topic: string, command: ActuatorCommand): void {
  const target = parseCommandTopic(topic);
  if (!target || !answersAsNode(target.zonaId)) return;
  simulatedNodes.receive(target.zonaId, target.sectorId, command);
}

/** One load queue per simulated macro-zone, with the real clock. */
const simulatedNodes = new SimulatedNodes(
  {
    now: () => Date.now(),
    setTimer: (fn, ms) => setTimeout(fn, ms),
    clearTimer: (handle) => clearTimeout(handle as NodeJS.Timeout),
    maxLoads: config.nodeMaxLoads,
  },
  publishAck,
);

function publishAck(zonaId: string, sectorId: string, ack: ActuatorAck): void {
  const ackOn = ackTopic(zonaId, sectorId);
  if (!client || !client.connected) {
    console.warn(`[simulator] no broker connection: ack for ${ack.commandId ?? '?'} not sent`);
    return;
  }
  client.publish(ackOn, JSON.stringify(ack), { qos: 1 }, (error) => {
    if (error) console.error(`[simulator] failed to publish ack on ${ackOn}: ${error.message}`);
    else console.log(`[simulator] → ACK topic=${ackOn} status=${ack.status} tipo=${ack.detalle.tipo}`);
  });
}

/** `true` when the broker connection is alive. The UI uses it to warn before trying. */
export function isConnected(): boolean {
  return client?.connected ?? false;
}

/**
 * Publishes a payload on a macro-zone's topic, with QoS 1.
 *
 * Fails fast when there is no connection instead of queueing the message: if the broker is
 * down the user has to find out now, not discover half an hour later that every reading went
 * out at once.
 */
export async function publish(zonaId: string, payload: TelemetryPayload): Promise<void> {
  // With the backend in demo mode the telemetry goes on the separate demo topic (no race with the real ESP32).
  await publishJson(telemetryTopicFor(zonaId, await demoMode.isAvailable()), payload);
}

/**
 * Publishes an error as the zone's node would (S-05): same topic and payload as the firmware, QoS 1, no retain. The
 * backend marks the device as faulty without being able to tell there was a simulator on the other end.
 */
export async function publishNodeError(zonaId: string, payload: NodeErrorPayload): Promise<void> {
  await publishJson(errorsTopic(zonaId), payload);
}

async function publishJson(topic: string, payload: TelemetryPayload | NodeErrorPayload): Promise<void> {
  if (!client || !client.connected) {
    throw new PublishFailed(
      `No hay conexión con el broker MQTT (${config.mqttUrl}). Verificá que Mosquitto esté levantado.`,
    );
  }
  const message = JSON.stringify(payload);

  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(
      () => reject(new PublishFailed(`El broker no confirmó la publicación en ${topic}.`)),
      PUBLISH_TIMEOUT_MS,
    );
    client!.publish(topic, message, { qos: 1 }, (error) => {
      clearTimeout(timer);
      if (error) reject(new PublishFailed(`No se pudo publicar en ${topic}: ${error.message}`));
      else resolve();
    });
  });
}

/** Closes the connection on shutdown. */
export async function disconnect(): Promise<void> {
  if (!client) return;
  await client.endAsync();
  client = null;
}
