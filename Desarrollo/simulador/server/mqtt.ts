/* ============================================================
   Simulator MQTT publisher.

   This is where the decoupling lives: the simulator publishes DIRECTLY to the broker, on the
   same topic and with the same payload as the ESP32 firmware. The backend ingests it through
   its normal pipeline and has no way of knowing there was a simulator on the other end.
   Previously this went through a backend endpoint, which published telemetry to itself so
   the message would travel the real pipeline.

   Error messages reaching the UI are in Spanish; console output is internal, so it is not.
   ============================================================ */
import mqtt, { type MqttClient } from 'mqtt';
import { config } from './config.ts';
import { telemetryTopic, type TelemetryPayload } from './contract.ts';

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
