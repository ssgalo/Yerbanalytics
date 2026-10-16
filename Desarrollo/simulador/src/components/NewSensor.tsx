/* Registers a simulated sensor: serial/MAC plus a macro-zone of the current topology.

   Optionally also registers the device in the backend's hardware fleet so the witness
   node shows up there with its technical status (battery, signal, heartbeat). The checkbox
   is an ergonomic shortcut: without it the operator would have to switch to the dashboard's
   Hardware view to do the same thing manually.

   Simulated sensors are a SIMULATOR list, separate from the system's hardware registry:
   adding one here does NOT create a device unless the checkbox is checked. */
import { useEffect, useState, type FormEvent } from 'react';
import { Section } from './Section';
import * as api from '../api';
import type { SimulatedSensor } from '../types';

interface Props {
  zones: string[];
  onCreate: (sensor: SimulatedSensor) => Promise<void>;
}

export function NewSensor({ zones, onCreate }: Props) {
  const [serial, setSerial] = useState('');
  const [zonaId, setZonaId] = useState('');
  const [registerHardware, setRegisterHardware] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // Preselects the first macro-zone and drops the chosen one if it disappeared after the
  // topology was regenerated.
  useEffect(() => {
    if (zones.length === 0) setZonaId('');
    else if (!zones.includes(zonaId)) setZonaId(zones[0]);
  }, [zones, zonaId]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setNotice(null);
    setSaving(true);
    try {
      await onCreate({ serial, zonaId });

      if (registerHardware) {
        await api.registrarHardware({
          serial,
          tipo: 'Nodo testigo',
          zonaId,
          sectorId: null,
        });
        setNotice(`Sensor creado y registrado en la flota de hardware como "${serial}".`);
      }

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
      hint="Un emisor de telemetría por macro-zona. Por defecto no se registra como dispositivo del sistema."
    >
      <form onSubmit={submit}>
        <div className="row">
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
        </div>

        <label className="checkboxRow" style={{ marginTop: 10 }}>
          <input
            type="checkbox"
            checked={registerHardware}
            onChange={(e) => setRegisterHardware(e.target.checked)}
          />
          <span style={{ fontSize: 13 }}>
            Registrar también en la flota de Hardware del backend (
            <code>POST /api/hardware</code>) — para que el nodo aparezca con batería y señal.
          </span>
        </label>
      </form>

      {error && <p className="errorText" style={{ marginTop: 8 }}>{error}</p>}
      {notice && <p className="okText" style={{ marginTop: 8 }}>{notice}</p>}
    </Section>
  );
}
