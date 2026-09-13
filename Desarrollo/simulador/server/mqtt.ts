/* ============================================================
   Simulator MQTT publisher + command subscriber.

   Uplink: the simulator publishes directly to the broker on the same topic and with the same
   payload as the ESP32 firmware. The backend ingests it through its normal pipeline and has
   no way of knowing there was a simulator on the other end.

   Downlink: the simulator subscribes to the command wildcard topic so that orders sent by the
   backend rules engine are visible in the console. This validates the full cyber-physical loop
   (measure → detect → decide → command) without needing physical hardware.

   Error messages reaching the UI are in Spanish; console output is internal, so it is not.
   ============================================================ */
import mqtt, { type MqttClient } from 'mqtt';
import { config } from './config.ts';
import { telemetryTopic, type TelemetryPayload } from './contract.ts';

/** Wildcard topic for all sector command messages. Mirrors contrato.h :: contratoTopicComando. */
const COMMAND_TOPIC_WILDCARD = 'nursery/zone/+/sector/+/command';

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

  client.on('connect', () => {
    lastError = null;
    console.log(`[simulator] connected to broker ${config.mqttUrl}`);

    // Subscribe to all sector command topics to validate the downlink from the rules engine.
    client!.subscribe(COMMAND_TOPIC_WILDCARD, { qos: 1 }, (err) => {
      if (err) {
        console.error(`[simulator] failed to subscribe to command topics: ${err.message}`);
      } else {
        console.log(`[simulator] subscribed to command wildcard: ${COMMAND_TOPIC_WILDCARD}`);
      }
    });
  });

  // Log incoming commands from the backend rules engine.
  // In production this role is played by the ESP32 nodo_actuador firmware.
  client.on('message', (topic, message) => {
    try {
      const payload = JSON.parse(message.toString()) as {
        commandId?: string;
        actuador?: string;
        accion?: string;
        parametros?: Record<string, unknown>;
      };
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
  if (!client || !client.connected) {
    throw new PublishFailed(
      `No hay conexión con el broker MQTT (${config.mqttUrl}). Verificá que Mosquitto esté levantado.`,
    );
  }
  const topic = telemetryTopic(zonaId);
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
