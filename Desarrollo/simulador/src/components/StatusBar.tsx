/* Simulator health at a glance: with no broker nothing can be published; with no backend no
   capture can be requested and the topology cannot be regenerated. Telling the two apart
   saves chasing the wrong error. */
import type { SimulatorStatus } from '../types';

interface Props {
  status: SimulatorStatus | null;
}

function Indicator({ up, name, detail }: { up: boolean; name: string; detail: string }) {
  return (
    <span>
      <span className={`statusDot ${up ? 'up' : 'down'}`} aria-hidden="true" />
      <strong>{name}</strong>{' '}
      <span className="muted">
        {up ? 'conectado' : 'no disponible'} · {detail}
      </span>
    </span>
  );
}

export function StatusBar({ status }: Props) {
  if (!status) {
    return <div className="statusBar muted">Consultando el estado…</div>;
  }
  return (
    <div className="statusBar">
      <Indicator up={status.broker.connected} name="Broker MQTT" detail={status.broker.url} />
      <Indicator up={status.backend.up} name="Backend" detail={status.backend.url} />
    </div>
  );
}
