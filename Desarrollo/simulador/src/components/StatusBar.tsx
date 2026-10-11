/* Simulator health at a glance: with no broker nothing can be published; with no backend no
   capture can be requested and the topology cannot be regenerated. Telling the two apart
   saves chasing the wrong error.

   The backend has a third state besides up and down: reachable but rejecting the simulator's
   account. That one gets the warning dot and says what to fix, because telemetry still flows
   (it goes through the broker) while everything in the camera panel fails. */
import type { BackendAccess, SimulatorStatus } from '../types';

interface Props {
  status: SimulatorStatus | null;
}

type Tone = 'up' | 'warn' | 'down';

function Indicator({ tone, name, text }: { tone: Tone; name: string; text: string }) {
  return (
    <span>
      <span className={`statusDot ${tone}`} aria-hidden="true" />
      <strong>{name}</strong> <span className="muted">{text}</span>
    </span>
  );
}

/** What each backend state means for the operator, and what to do about it. */
const BACKEND_ACCESS: Record<BackendAccess, { tone: Tone; text: string }> = {
  ok: { tone: 'up', text: 'conectado' },
  unreachable: { tone: 'down', text: 'no disponible' },
  'no-credentials': {
    tone: 'warn',
    text: 'sin credenciales: rechaza las peticiones (falta BACKEND_USUARIO/BACKEND_CLAVE en el .env)',
  },
  rejected: { tone: 'warn', text: 'credenciales rechazadas' },
  'temporary-password': {
    tone: 'warn',
    text: 'la cuenta tiene clave temporal: cambiala entrando al dashboard',
  },
  forbidden: { tone: 'warn', text: 'la cuenta no tiene permiso (¿es de rol Servicio?)' },
  error: { tone: 'down', text: 'responde con error' },
};

export function StatusBar({ status }: Props) {
  if (!status) {
    return <div className="statusBar muted">Consultando el estado…</div>;
  }
  const backend = BACKEND_ACCESS[status.backend.access] ?? BACKEND_ACCESS.error;
  const account = status.backend.user ? ` · como ${status.backend.user}` : '';
  return (
    <div className="statusBar">
      <Indicator
        tone={status.broker.connected ? 'up' : 'down'}
        name="Broker MQTT"
        text={`${status.broker.connected ? 'conectado' : 'no disponible'} · ${status.broker.url}`}
      />
      <Indicator
        tone={backend.tone}
        name="Backend"
        text={`${backend.text} · ${status.backend.url}${account}`}
      />
    </div>
  );
}
