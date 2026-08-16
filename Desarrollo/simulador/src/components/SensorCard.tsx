/* Card for one simulated sensor (a macro-zone's witness node).

   Each of the 10 metrics can be sent on its own, with its own optional timestamp, and there
   is also a "Enviar todo" that sends them together with battery and signal. Empty timestamp
   means now.

   The fields come from `METRICS`, so adding a metric to the contract does not force a change
   to this form. */
import { useState } from 'react';
import { METRICS } from '../metrics';
import type { SimulatedSensor, TelemetryInput } from '../types';

interface Props {
  sensor: SimulatedSensor;
  onSend: (input: TelemetryInput) => Promise<void>;
  onDelete: (serial: string) => Promise<void>;
}

type Feedback = { kind: 'ok' | 'error'; text: string } | null;

export function SensorCard({ sensor, onSend, onDelete }: Props) {
  // Value and timestamp per metric, prefilled with each metric's base.
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(METRICS.map((m) => [m.key, String(m.base)])),
  );
  const [dates, setDates] = useState<Record<string, string>>({});
  const [battery, setBattery] = useState('');
  const [signal, setSignal] = useState('');
  const [dateAll, setDateAll] = useState('');
  const [sending, setSending] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);

  const setValue = (key: string, v: string) => setValues((prev) => ({ ...prev, [key]: v }));
  const setDate = (key: string, v: string) => setDates((prev) => ({ ...prev, [key]: v }));

  /** Empty → absent; non-numeric text → `undefined`, which is treated as an error. */
  const optionalNumber = (v: string): number | null | undefined => {
    if (v.trim() === '') return null;
    const n = Number(v);
    return Number.isNaN(n) ? undefined : n;
  };

  const epochOf = (date: string): number | null => (date ? new Date(date).getTime() : null);

  const send = async (input: TelemetryInput, success: string) => {
    setSending(true);
    try {
      await onSend(input);
      setFeedback({ kind: 'ok', text: success });
    } catch (e) {
      setFeedback({ kind: 'error', text: e instanceof Error ? e.message : String(e) });
    } finally {
      setSending(false);
    }
  };

  /** Sends a single metric with its timestamp. The others keep their last value. */
  const sendMetric = async (key: string, label: string) => {
    setFeedback(null);
    const raw = values[key];
    const n = Number(raw);
    if (raw.trim() === '' || Number.isNaN(n)) {
      setFeedback({ kind: 'error', text: `Valor inválido en ${label}.` });
      return;
    }
    await send(
      {
        serial: sensor.serial,
        zonaId: sensor.zonaId,
        timestamp: epochOf(dates[key] ?? ''),
        metrics: { [key]: n },
      },
      `${label} publicada ${dates[key] ? 'con la fecha indicada' : 'con la hora actual'}.`,
    );
  };

  /** Sends all 10 metrics together, plus battery and signal. */
  const sendAll = async () => {
    setFeedback(null);
    const metrics: Record<string, number> = {};
    for (const m of METRICS) {
      const raw = values[m.key];
      const n = Number(raw);
      if (raw.trim() === '' || Number.isNaN(n)) {
        setFeedback({ kind: 'error', text: `Valor inválido en ${m.label}.` });
        return;
      }
      metrics[m.key] = n;
    }
    const bat = optionalNumber(battery);
    const sig = optionalNumber(signal);
    if (bat === undefined || sig === undefined) {
      setFeedback({
        kind: 'error',
        text: 'Batería y señal deben ser numéricas (o quedar vacías).',
      });
      return;
    }
    await send(
      {
        serial: sensor.serial,
        zonaId: sensor.zonaId,
        battery: bat,
        signal: sig,
        timestamp: epochOf(dateAll),
        metrics,
      },
      dateAll
        ? 'Lectura completa publicada con la fecha indicada.'
        : 'Lectura completa publicada con la hora actual.',
    );
  };

  const remove = async () => {
    setFeedback(null);
    try {
      await onDelete(sensor.serial);
    } catch (e) {
      setFeedback({ kind: 'error', text: e instanceof Error ? e.message : String(e) });
    }
  };

  return (
    <article className="card">
      <div className="sensorHead">
        <div>
          <div className="serial">{sensor.serial}</div>
          <div className="muted">{sensor.zonaId}</div>
        </div>
        <div className="spacer" />
        <button
          type="button"
          className="small danger"
          onClick={remove}
          disabled={sending}
          aria-label={`Eliminar sensor ${sensor.serial}`}
        >
          Eliminar
        </button>
      </div>

      {METRICS.map((m) => (
        <div key={m.key} className="metricRow">
          <span className="name">{m.label}</span>
          <span>
            <input
              className="num"
              type="number"
              step={m.dec > 0 ? 0.1 : 1}
              inputMode="decimal"
              value={values[m.key]}
              aria-label={m.label}
              onChange={(e) => setValue(m.key, e.target.value)}
              style={{ width: 76 }}
            />
            <span className="unit">{m.unit}</span>
          </span>
          <input
            type="datetime-local"
            value={dates[m.key] ?? ''}
            aria-label={`Fecha y hora de ${m.label}`}
            onChange={(e) => setDate(m.key, e.target.value)}
          />
          <button
            type="button"
            className="small"
            disabled={sending}
            onClick={() => sendMetric(m.key, m.label)}
          >
            Enviar
          </button>
        </div>
      ))}

      <hr className="divider" />

      <div className="row">
        <label className="field">
          <span className="label">Batería (%)</span>
          <input
            className="num"
            type="number"
            placeholder="opcional"
            value={battery}
            aria-label="Batería"
            onChange={(e) => setBattery(e.target.value)}
          />
        </label>
        <label className="field">
          <span className="label">Señal (dBm)</span>
          <input
            className="num"
            type="number"
            placeholder="opcional"
            value={signal}
            aria-label="Señal"
            onChange={(e) => setSignal(e.target.value)}
          />
        </label>
        <label className="field">
          <span className="label">Fecha del envío conjunto (vacío = ahora)</span>
          <input
            type="datetime-local"
            value={dateAll}
            aria-label="Fecha y hora del envío conjunto"
            onChange={(e) => setDateAll(e.target.value)}
          />
        </label>
      </div>

      <div className="actions">
        <button type="button" className="primary" disabled={sending} onClick={sendAll}>
          {sending ? 'Publicando…' : 'Enviar todo'}
        </button>
        {feedback && (
          <span className={feedback.kind === 'ok' ? 'okText' : 'errorText'}>{feedback.text}</span>
        )}
      </div>
    </article>
  );
}
