/* Periodic noise to every macro-zone, to give the nursery background activity.

   Always starts off: once on it overwrites manually entered values, so it has to be asked
   for explicitly. Shutting the simulator down stops it, and the system goes back to waiting
   for telemetry from real hardware. */
import { useState } from 'react';
import { Section } from './Section';
import { setAutoEmission } from '../api';

interface Props {
  active: boolean;
  intervalMs: number;
  onChange: (active: boolean) => void;
}

export function AutoEmission({ active, intervalMs, onChange }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggle = async () => {
    setError(null);
    setBusy(true);
    try {
      await setAutoEmission(!active);
      onChange(!active);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Section
      title="Emisión automática"
      hint={`Publica lecturas al azar a todas las macro-zonas cada ${Math.round(intervalMs / 1000)} s. Pisa los valores cargados a mano.`}
    >
      <div className="row">
        <button type="button" className={active ? '' : 'primary'} disabled={busy} onClick={toggle}>
          {busy
            ? 'Cambiando…'
            : active
              ? 'Apagar emisión automática'
              : 'Encender emisión automática'}
        </button>
        <span className={`badge ${active ? 'badgeOk' : ''}`}>
          {active ? 'Publicando' : 'Apagada'}
        </span>
      </div>
      {error && <p className="errorText">{error}</p>}
    </Section>
  );
}
