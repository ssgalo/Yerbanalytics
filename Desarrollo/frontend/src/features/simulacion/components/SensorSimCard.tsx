/* Tarjeta de un sensor simulado (nodo testigo de una macro-zona). Cada una de las 10
   métricas se puede enviar por separado (con su propia fecha/hora opcional) y también hay
   un botón "Enviar todo" que las manda juntas con batería/señal y una fecha/hora a nivel
   tarjeta. Fecha/hora vacía = hora actual.

   Los campos salen de `specs`, así que incorporar una métrica al contrato no requiere
   tocar este formulario. */
import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { specs } from '@/data/mock/specs';
import type { EnvioMetrics, EnvioTelemetria, SensorSimulado } from '@/types/domain';
import styles from '../Simulacion.module.css';

interface SensorSimCardProps {
  sensor: SensorSimulado;
  disabled: boolean;
  sending: boolean;
  onEnviar: (input: EnvioTelemetria) => Promise<void>;
  onEliminar: (serial: string) => Promise<void>;
}

type Feedback = { kind: 'ok' | 'error'; msg: string } | null;

export function SensorSimCard({
  sensor,
  disabled,
  sending,
  onEnviar,
  onEliminar,
}: SensorSimCardProps) {
  // Valor y fecha/hora por métrica, precargados con la base de cada spec.
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries(specs.map((s) => [s.key, String(s.base)])),
  );
  const [fechas, setFechas] = useState<Record<string, string>>({});
  const [battery, setBattery] = useState('');
  const [signal, setSignal] = useState('');
  const [fechaTodo, setFechaTodo] = useState(''); // datetime-local del envío conjunto
  const [feedback, setFeedback] = useState<Feedback>(null);

  const setValue = (key: string, v: string) => setValues((prev) => ({ ...prev, [key]: v }));
  const setFecha = (key: string, v: string) => setFechas((prev) => ({ ...prev, [key]: v }));

  const parseOpt = (v: string): number | null | undefined => {
    if (v.trim() === '') return null;
    const n = Number(v);
    return Number.isNaN(n) ? undefined : n;
  };

  const tsDesde = (fecha: string): number | null => (fecha ? new Date(fecha).getTime() : null);

  /** Envía una sola métrica con su fecha/hora. */
  const enviarMetrica = async (key: string, label: string) => {
    setFeedback(null);
    const raw = values[key];
    const n = Number(raw);
    if (raw.trim() === '' || Number.isNaN(n)) {
      setFeedback({ kind: 'error', msg: `Valor inválido en ${label}.` });
      return;
    }
    const input: EnvioTelemetria = {
      serial: sensor.serial,
      zonaId: sensor.zonaId,
      timestamp: tsDesde(fechas[key] ?? ''),
      metrics: { [key]: n } as EnvioMetrics,
    };
    try {
      await onEnviar(input);
      setFeedback({
        kind: 'ok',
        msg: `${label} enviada ${fechas[key] ? 'con la fecha indicada' : 'con la hora actual'}.`,
      });
    } catch (e) {
      setFeedback({ kind: 'error', msg: e instanceof Error ? e.message : String(e) });
    }
  };

  /** Envía las 10 métricas juntas + batería/señal. */
  const enviarTodo = async () => {
    setFeedback(null);
    const metrics: EnvioMetrics = {};
    for (const s of specs) {
      const raw = values[s.key];
      const n = Number(raw);
      if (raw.trim() === '' || Number.isNaN(n)) {
        setFeedback({ kind: 'error', msg: `Valor inválido en ${s.label}.` });
        return;
      }
      // Las claves de `specs` son exactamente las de EnvioMetrics.
      metrics[s.key as keyof EnvioMetrics] = n;
    }
    const bat = parseOpt(battery);
    const sig = parseOpt(signal);
    if (bat === undefined || sig === undefined) {
      setFeedback({ kind: 'error', msg: 'Batería y señal deben ser numéricas (o quedar vacías).' });
      return;
    }
    const input: EnvioTelemetria = {
      serial: sensor.serial,
      zonaId: sensor.zonaId,
      battery: bat,
      signal: sig,
      timestamp: tsDesde(fechaTodo),
      metrics,
    };
    try {
      await onEnviar(input);
      setFeedback({
        kind: 'ok',
        msg: fechaTodo
          ? 'Lectura completa enviada con la fecha indicada.'
          : 'Lectura completa enviada con la hora actual.',
      });
    } catch (e) {
      setFeedback({ kind: 'error', msg: e instanceof Error ? e.message : String(e) });
    }
  };

  const handleEliminar = async () => {
    setFeedback(null);
    try {
      await onEliminar(sensor.serial);
    } catch (e) {
      setFeedback({ kind: 'error', msg: e instanceof Error ? e.message : String(e) });
    }
  };

  return (
    <Card className={styles.sensorCard}>
      <div className={styles.sensorHead}>
        <div>
          <div className={styles.sensorName}>{sensor.serial}</div>
          <div className={styles.sensorMeta}>{sensor.zonaId}</div>
        </div>
        <div className={styles.sensorSpacer} />
        <button
          type="button"
          className={styles.sensorRemove}
          disabled={disabled || sending}
          onClick={handleEliminar}
          aria-label={`Eliminar sensor ${sensor.serial}`}
        >
          Eliminar
        </button>
      </div>

      <div className={styles.metricList}>
        {specs.map((s) => (
          <div key={s.key} className={styles.metricRow}>
            <span className={styles.metricRowLabel}>{s.label}</span>
            <div className={styles.inputWrap}>
              <input
                className={styles.numInput}
                type="number"
                step={s.dec > 0 ? 0.1 : 1}
                inputMode="decimal"
                value={values[s.key]}
                aria-label={s.label}
                disabled={disabled}
                onChange={(e) => setValue(s.key, e.target.value)}
              />
              <span className={styles.unit}>{s.unit}</span>
            </div>
            <input
              className={styles.input}
              type="datetime-local"
              value={fechas[s.key] ?? ''}
              aria-label={`Fecha y hora de ${s.label}`}
              disabled={disabled}
              onChange={(e) => setFecha(s.key, e.target.value)}
            />
            <button
              type="button"
              className={styles.btnSmall}
              disabled={disabled || sending}
              onClick={() => enviarMetrica(s.key, s.label)}
            >
              Enviar
            </button>
          </div>
        ))}
      </div>

      <hr className={styles.divider} />

      <div className={styles.inline}>
        <div className={styles.field}>
          <label className={styles.label}>Batería (%)</label>
          <div className={styles.inputWrap}>
            <input
              className={styles.numInput}
              type="number"
              placeholder="opcional"
              value={battery}
              aria-label="Batería"
              disabled={disabled}
              onChange={(e) => setBattery(e.target.value)}
            />
          </div>
        </div>
        <div className={styles.field}>
          <label className={styles.label}>Señal (dBm)</label>
          <div className={styles.inputWrap}>
            <input
              className={styles.numInput}
              type="number"
              placeholder="opcional"
              value={signal}
              aria-label="Señal"
              disabled={disabled}
              onChange={(e) => setSignal(e.target.value)}
            />
          </div>
        </div>
        <div className={`${styles.field} ${styles.datetime}`}>
          <label className={styles.label}>
            Fecha y hora del envío conjunto (opcional · vacío = ahora)
          </label>
          <input
            className={styles.input}
            type="datetime-local"
            value={fechaTodo}
            aria-label="Fecha y hora del envío conjunto"
            disabled={disabled}
            onChange={(e) => setFechaTodo(e.target.value)}
          />
        </div>
      </div>

      <div className={styles.actions}>
        <button
          type="button"
          className={styles.btnPrimary}
          disabled={disabled || sending}
          onClick={enviarTodo}
        >
          {sending ? 'Enviando…' : 'Enviar todo'}
        </button>
        <span className={styles.feedback}>
          {feedback && (
            <span className={feedback.kind === 'ok' ? styles.feedbackOk : styles.feedbackError}>
              {feedback.msg}
            </span>
          )}
        </span>
      </div>
    </Card>
  );
}
