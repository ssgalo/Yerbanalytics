/* Registers a simulated sensor: serial/MAC plus a macro-zone of the current topology.

   Simulated sensors are a SIMULATOR list, separate from the system's hardware registry:
   adding one here creates no device. For a node to also refresh its technical status it must
   be registered under Hardware with the same serial/MAC. */
import { useEffect, useState, type FormEvent } from 'react';
import { Section } from './Section';
import type { SimulatedSensor } from '../types';

interface Props {
  zones: string[];
  onCreate: (sensor: SimulatedSensor) => Promise<void>;
}

export function NewSensor({ zones, onCreate }: Props) {
  const [serial, setSerial] = useState('');
  const [zonaId, setZonaId] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Preselects the first macro-zone and drops the chosen one if it disappeared after the
  // topology was regenerated.
  useEffect(() => {
    if (zones.length === 0) setZonaId('');
    else if (!zones.includes(zonaId)) setZonaId(zones[0]);
  }, [zones, zonaId]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      await onCreate({ serial, zonaId });
      setSerial('');
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Section
      title="Nuevo sensor simulado"
      hint="Un emisor de telemetría por macro-zona. No se registra como dispositivo del sistema."
    >
      <form className="row" onSubmit={submit}>
        <label className="field">
          <span className="label">Serial / MAC</span>
          <input
            value={serial}
            placeholder="A4:CF:12:9A:00:01"
            onChange={(e) => setSerial(e.target.value)}
            style={{ width: 190 }}
          />
        </label>
        <label className="field">
          <span className="label">Macro-zona</span>
          <select
            value={zonaId}
            onChange={(e) => setZonaId(e.target.value)}
            disabled={zones.length === 0}
          >
            {zones.map((z) => (
              <option key={z} value={z}>
                {z}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="primary" disabled={saving || zones.length === 0}>
          {saving ? 'Creando…' : 'Crear sensor'}
        </button>
        {zones.length === 0 && (
          <span className="muted">
            No hay macro-zonas: generá la topología del vivero antes de crear sensores.
          </span>
        )}
      </form>
      {error && <p className="errorText">{error}</p>}
    </Section>
  );
}
