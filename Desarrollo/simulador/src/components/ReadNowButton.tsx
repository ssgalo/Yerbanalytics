import { useState } from 'react';
import * as api from '../api';

interface Props {
  /** When true the backend is in demo mode; the button still works in production but the label clarifies. */
  demoEnabled: boolean;
}

/**
 * Triggers a full sensing pulse (LEER_AHORA → telemetry → rule engine).
 *
 * Pressing it calls POST /api/pulsos with ejecutarMotor=true. The backend sends a LEER_AHORA
 * MQTT command to every macro-zone; the simulator's server layer listens and responds by
 * publishing the current sensor values — closing the full ciberfísico cycle without waiting
 * for the 4-hour production interval.
 */
export function ReadNowButton({ demoEnabled }: Props) {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<'ok' | 'conflict' | 'error' | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleClick = async () => {
    setLoading(true);
    setResult(null);
    setErrorMsg(null);
    try {
      await api.triggerSensingPulse(true);
      setResult('ok');
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg.includes('409') || msg.toLowerCase().includes('en curso')) {
        setResult('conflict');
      } else {
        setResult('error');
        setErrorMsg(msg);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card demoCard">
      <div className="demoCardHeader">
        <h3>Leer ahora</h3>
        {demoEnabled && <span className="badge badgeOn">DEMO</span>}
      </div>
      <p className="hint">
        Dispara un pulso de sensado completo (
        <code>POST /api/pulsos</code> con <code>ejecutarMotor=true</code>). El backend envía{' '}
        <code>LEER_AHORA</code> a cada macro-zona por MQTT; el simulador responde publicando la
        telemetría actual y el motor de reglas evalúa con las lecturas que llegan.
      </p>

      <div className="actions">
        <button className="primary" onClick={() => void handleClick()} disabled={loading}>
          {loading ? 'Leyendo…' : 'Leer ahora'}
        </button>

        {result === 'ok' && (
          <span className="okText">Pulso disparado — el motor va a evaluar en segundos.</span>
        )}
        {result === 'conflict' && (
          <span className="muted">Ya hay un pulso en curso (409). Esperá que termine.</span>
        )}
        {result === 'error' && errorMsg && <span className="errorText">{errorMsg}</span>}
      </div>
    </div>
  );
}
