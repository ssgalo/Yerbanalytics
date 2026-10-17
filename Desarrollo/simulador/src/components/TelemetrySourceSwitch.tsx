import { useEffect, useState } from 'react';
import * as api from '../api';
import type { TelemetrySource } from '../api';

/**
 * Chooses which telemetry the backend reads in demo mode: the real ESP32 node or this simulator.
 *
 * With the backend in demo mode the simulator publishes on a separate topic
 * (`nursery/demo/zone/{id}/telemetry`), so the two sources never race. The backend processes only
 * the selected one and keeps sending the commands to the ESP32's topics, so the real actuators move.
 * Only shown when the backend is in demo mode (the parent decides); lost when the backend restarts.
 */
export function TelemetrySourceSwitch() {
  const [source, setSource] = useState<TelemetrySource | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .getTelemetrySource()
      .then(setSource)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)));
  }, []);

  const choose = async (next: TelemetrySource) => {
    if (next === source) return;
    setSaving(true);
    setError(null);
    try {
      setSource(await api.setTelemetrySource(next));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card demoCard">
      <div className="demoCardHeader">
        <h3>Telemetría que usa el backend</h3>
        <span className="badge badgeOn">DEMO</span>
      </div>
      <p className="hint">
        El simulador publica en un tópico aparte (<code>nursery/demo/zone/…/telemetry</code>), así que
        no compite con el ESP32. El backend evalúa las reglas con la fuente elegida y sigue mandando
        los comandos a los actuadores reales.
      </p>
      <div className="actions">
        <button
          className={source === 'REAL_NODE' ? 'primary' : 'btnSecondary'}
          onClick={() => void choose('REAL_NODE')}
          disabled={saving || source === null}
        >
          ESP32 real
        </button>
        <button
          className={source === 'SIMULATOR' ? 'primary' : 'btnSecondary'}
          onClick={() => void choose('SIMULATOR')}
          disabled={saving || source === null}
        >
          Simulador
        </button>
        {error && <span className="errorText">{error}</span>}
      </div>
    </div>
  );
}
