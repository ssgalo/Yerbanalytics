import { useEffect, useState } from 'react';
import * as api from '../api';
import type { SimulatedWeather } from '../types';

const DEFAULT: SimulatedWeather = {
  tempC: 25,
  humedadRelPct: 60,
  probLluviaPct: 0,
  precipMm: 0,
  uvIndex: 5,
};

export function WeatherSimulation() {
  const [form, setForm] = useState<SimulatedWeather>(DEFAULT);
  const [active, setActive] = useState<SimulatedWeather | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .getSimulatedWeather()
      .then((w) => {
        if (w) {
          setActive(w);
          setForm(w);
        }
      })
      .catch(() => null);
  }, []);

  const field = (
    label: string,
    key: keyof SimulatedWeather,
    min: number,
    max: number,
    step: number,
    unit: string,
  ) => (
    <div className="fieldRow">
      <label>{label}</label>
      <input
        type="number"
        min={min}
        max={max}
        step={step}
        value={form[key]}
        onChange={(e) =>
          setForm((prev) => ({ ...prev, [key]: Number(e.target.value) }))
        }
      />
      <span className="unit">{unit}</span>
    </div>
  );

  const apply = async () => {
    setSaving(true);
    setError(null);
    try {
      const saved = await api.setSimulatedWeather(form);
      setActive(saved);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const clear = async () => {
    setSaving(true);
    setError(null);
    try {
      await api.clearSimulatedWeather();
      setActive(null);
      setForm(DEFAULT);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card demoCard">
      <h3>
        Clima simulado
        {active ? (
          <span className="badge badgeOn">activo</span>
        ) : (
          <span className="badge badgeOff">inactivo</span>
        )}
      </h3>
      <p className="hint">
        Mientras el clima simulado esté activo, el motor usa estos valores en lugar de llamar a
        Open-Meteo. Permite disparar PosponerPorLluviaRule y ShadingRule a demanda.
      </p>

      {field('Temperatura', 'tempC', -10, 50, 0.5, '°C')}
      {field('Humedad relativa', 'humedadRelPct', 0, 100, 1, '%')}
      {field('Prob. lluvia', 'probLluviaPct', 0, 100, 1, '%')}
      {field('Precipitación', 'precipMm', 0, 200, 0.1, 'mm')}
      {field('Índice UV', 'uvIndex', 0, 15, 0.1, '')}

      {error && <p className="errorMsg">{error}</p>}

      <div className="actions">
        <button onClick={() => void apply()} disabled={saving}>
          {saving ? 'Aplicando…' : 'Aplicar clima'}
        </button>
        {active && (
          <button onClick={() => void clear()} disabled={saving} className="btnSecondary">
            Limpiar (volver a Open-Meteo)
          </button>
        )}
      </div>
    </div>
  );
}
