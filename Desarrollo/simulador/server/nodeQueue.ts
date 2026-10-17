/* ============================================================
   Simulated node load queue (add-cola-actuadores-nodo, design D-12).

   The real node (firmware `actuacion/cola_cargas`) runs at most MAX_CARGAS_SIMULTANEAS loads at a time (a
   valve/pump run of a sector, or a shade movement) because the prototype's power supply cannot take more, and
   queues the rest in arrival order. The simulator reproduces the same rules and the same ACKs, so a demo without
   hardware behaves like the ESP32:

     [EN_CURSO en_cola] -> EN_CURSO iniciado -> final (SUCCESS | ERROR)

   SOURCE OF TRUTH: the "Ack" section of `Desarrollo/embebido/comun/contrato.h`.

   The simulator does not know which node serves which sectors, so ONE queue per macro-zone (the witness node).

   Pure: the clock and the timers are injected, so it is tested with a fake clock. It emits through a callback and
   knows nothing about MQTT.
   ============================================================ */

import {
  PRIORITY_IMMEDIATE,
  PRIORITY_PARAM,
  simulatedAck,
  type ActuatorAck,
  type ActuatorCommand,
} from './contract.ts';

/** How many commands can wait (COLA_CARGAS_MAX in the firmware). */
export const NODE_QUEUE_CAPACITY = 8;

export interface NodeQueueDeps {
  /** Epoch ms (or any monotonic ms). */
  now: () => number;
  setTimer: (fn: () => void, ms: number) => unknown;
  clearTimer: (handle: unknown) => void;
  /** Loads that can run at once (SIM_NODE_MAX_LOADS, MAX_CARGAS_SIMULTANEAS in the firmware). */
  maxLoads: number;
}

export type EmitAck = (zonaId: string, sectorId: string, ack: ActuatorAck) => void;

type Kind = 'valve' | 'pump' | 'shade';

interface Load {
  /** `valve:S1`, `pump:S1`, or `shade` (the mesh is one per node: its key ignores the sector). */
  key: string;
  kind: Kind;
  sectorId: string;
  commandId?: string;
  durationMs: number;
  /** The final ack the load emits when it ends (what `simulatedAck` computed). */
  finalAck: ActuatorAck;
  startedAt: number;
  timer?: unknown;
}

const keyOf = (kind: Kind, sectorId: string): string => (kind === 'shade' ? 'shade' : `${kind}:${sectorId}`);

class NodeQueue {
  private readonly running = new Map<string, Load>();
  private queue: Load[] = [];
  /** Final acks of the last commands (the firmware's dedup keeps the last 24): a redelivery gets its final ack again. */
  private readonly finished = new Map<string, ActuatorAck>();

  constructor(
    private readonly zonaId: string,
    private readonly deps: NodeQueueDeps,
    private readonly emit: EmitAck,
  ) {}

  receive(sectorId: string, command: ActuatorCommand): void {
    // QoS 1 can redeliver a command: one still queued or running is ignored; one already finished repeats its final ack.
    const id = command.commandId;
    if (id) {
      if ([...this.running.values(), ...this.queue].some((l) => l.commandId === id)) return;
      const previous = this.finished.get(id);
      if (previous) {
        this.emit(this.zonaId, sectorId, previous);
        return;
      }
    }
    const accion = (command.accion ?? '').toUpperCase();
    const kind = command.actuador as Kind;
    const sim = simulatedAck(command);

    // valve OFF / pump OFF: act now, and take out of the queue what is queued for that sector and actuator.
    if ((kind === 'valve' || kind === 'pump') && (accion === 'OFF' || accion === 'CLOSE')) {
      this.cancel(kind, sectorId);
      this.send(sectorId, sim.ack);
      this.startNext();
      return;
    }

    // Invalid or immediate answers (errors, OFF of the shade): they do not take a place.
    if (sim.delayMs === 0) {
      this.send(sectorId, sim.ack);
      return;
    }

    const load: Load = {
      key: keyOf(kind, sectorId),
      kind,
      sectorId,
      commandId: command.commandId,
      durationMs: sim.delayMs,
      finalAck: sim.ack,
      startedAt: 0,
    };
    const immediate = kind === 'shade' && command.parametros?.[PRIORITY_PARAM] === PRIORITY_IMMEDIATE;

    // The same thing is already running: the new command replaces it and starts at once, in the same place.
    const current = this.running.get(load.key);
    if (current) {
      this.stop(current);
      this.send(current.sectorId, this.error(current, 'reemplazado'));
      this.start(load);
      return;
    }

    const queuedAt = this.queue.findIndex((q) => q.key === load.key);

    // The safe state: the mesh does not wait, even with the node full, and it replaces the queued SET.
    if (immediate) {
      if (queuedAt >= 0) this.discard(queuedAt, 'reemplazado');
      this.start(load);
      return;
    }

    // The same thing is queued: the new one takes its place in the line.
    if (queuedAt >= 0) {
      this.discard(queuedAt, 'reemplazado');
      this.queue.splice(queuedAt, 0, load);
      this.announceQueued(load, queuedAt);
      return;
    }

    if (this.running.size < this.deps.maxLoads && this.queue.length === 0) {
      this.start(load);
      return;
    }
    if (this.queue.length >= NODE_QUEUE_CAPACITY) {
      this.send(sectorId, { commandId: load.commandId, status: 'ERROR', detalle: { tipo: 'cola_llena' } });
      return;
    }
    this.queue.push(load);
    this.announceQueued(load, this.queue.length - 1);
  }

  /** Removes the queued and the running load of (kind, sector); each one gets its `cancelado`. */
  private cancel(kind: Kind, sectorId: string): void {
    const key = keyOf(kind, sectorId);
    for (let i = this.queue.length - 1; i >= 0; i--) {
      if (this.queue[i].key === key) this.discard(i, 'cancelado');
    }
    const current = this.running.get(key);
    if (current) {
      this.stop(current);
      this.send(current.sectorId, this.error(current, 'cancelado'));
    }
  }

  /** Takes the queued load at `index` out of the line with an ERROR final ack. */
  private discard(index: number, motivo: 'cancelado' | 'reemplazado'): void {
    const [gone] = this.queue.splice(index, 1);
    this.send(gone.sectorId, this.error(gone, motivo));
  }

  private error(load: Load, tipo: 'cancelado' | 'reemplazado'): ActuatorAck {
    return { commandId: load.commandId, status: 'ERROR', detalle: { tipo } };
  }

  private announceQueued(load: Load, index: number): void {
    this.send(load.sectorId, {
      commandId: load.commandId,
      status: 'EN_CURSO',
      detalle: { tipo: 'en_cola', posicion: index + 1, esperaMaxSec: Math.ceil(this.waitUpperBoundMs(index) / 1000) },
    });
  }

  /** What the running loads still have, at most, plus the duration of what is ahead in the line. */
  private waitUpperBoundMs(index: number): number {
    const now = this.deps.now();
    let wait = 0;
    for (const r of this.running.values()) wait = Math.max(wait, r.startedAt + r.durationMs - now);
    wait = Math.max(0, wait);
    for (let i = 0; i < index; i++) wait += this.queue[i].durationMs;
    return wait;
  }

  private start(load: Load): void {
    load.startedAt = this.deps.now();
    this.running.set(load.key, load);
    const { durationSec, ml, targetPct } = load.finalAck.detalle;
    const detalle: ActuatorAck['detalle'] = { tipo: 'iniciado' };
    if (durationSec !== undefined) detalle.durationSec = durationSec;
    if (ml !== undefined) detalle.ml = ml;
    if (targetPct !== undefined) detalle.targetPct = targetPct;
    this.send(load.sectorId, { commandId: load.commandId, status: 'EN_CURSO', detalle });
    load.timer = this.deps.setTimer(() => this.finish(load), load.durationMs);
  }

  private finish(load: Load): void {
    if (this.running.get(load.key) !== load) return; // it was replaced or cut meanwhile
    this.running.delete(load.key);
    this.send(load.sectorId, load.finalAck);
    this.startNext();
  }

  private stop(load: Load): void {
    this.deps.clearTimer(load.timer);
    this.running.delete(load.key);
  }

  private startNext(): void {
    while (this.queue.length > 0 && this.running.size < this.deps.maxLoads) {
      this.start(this.queue.shift()!);
    }
  }

  private send(sectorId: string, ack: ActuatorAck): void {
    if (ack.commandId && ack.status !== 'EN_CURSO') {
      this.finished.delete(ack.commandId);
      this.finished.set(ack.commandId, ack);
      if (this.finished.size > 24) this.finished.delete(this.finished.keys().next().value as string);
    }
    this.emit(this.zonaId, sectorId, ack);
  }
}

/** All the simulated nodes: one load queue per macro-zone. */
export class SimulatedNodes {
  private readonly nodes = new Map<string, NodeQueue>();

  constructor(
    private readonly deps: NodeQueueDeps,
    private readonly emit: EmitAck,
  ) {
    if (!(deps.maxLoads >= 1)) throw new Error('maxLoads must be >= 1');
  }

  /** A command the backend published for a sector of `zonaId`. */
  receive(zonaId: string, sectorId: string, command: ActuatorCommand): void {
    let node = this.nodes.get(zonaId);
    if (!node) {
      node = new NodeQueue(zonaId, this.deps, this.emit);
      this.nodes.set(zonaId, node);
    }
    node.receive(sectorId, command);
  }
}
