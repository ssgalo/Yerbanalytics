/* Alta de un sensor simulado (serial/MAC + macro-zona). Las macro-zonas ofrecidas son las
   de la topología vigente del vivero (provistas por la página): si la topología se regenera,
   el desplegable acompaña. */
import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import type { SensorSimulado } from '@/types/domain';
import styles from '../Simulacion.module.css';

interface NuevoSensorFormProps {
  busy: boolean;
  /** Macro-zonas de la topología vigente (por defecto las 6 del seed). */
  zonas: string[];
  zonasLoading: boolean;
  onSubmit: (sensor: SensorSimulado) => Promise<void>;
}

export function NuevoSensorForm({ busy, zonas, zonasLoading, onSubmit }: NuevoSensorFormProps) {
  const [serial, setSerial] = useState('');
  const [zonaId, setZonaId] = useState('');
  const [error, setError] = useState<string | null>(null);

  // Preselecciona la primera zona y descarta la elegida si dejó de existir tras un cambio de topología.
  useEffect(() => {
    if (zonas.length === 0) {
      setZonaId('');
    } else if (!zonas.includes(zonaId)) {
      setZonaId(zonas[0]);
    }
  }, [zonas, zonaId]);

  const handleSubmit = async () => {
    if (!serial.trim()) {
      setError('El serial/MAC del sensor es obligatorio.');
      return;
    }
    if (!zonaId.trim()) {
      setError('Elegí la macro-zona del sensor.');
      return;
    }
    setError(null);
    try {
      await onSubmit({ serial: serial.trim(), zonaId: zonaId.trim() });
      setSerial('');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <Card className={styles.section}>
      <div className={styles.sectionHead}>
        <span className={styles.sectionTitle}>Crear sensor simulado</span>
        <span className={styles.sectionHint}>
          Un emisor identificado por serial/MAC y asignado a una macro-zona de la topología actual
        </span>
      </div>
      <div className={styles.formGrid}>
        <div className={styles.group}>
          <label className={styles.label}>Serial / MAC</label>
          <input
            className={styles.input}
            type="text"
            placeholder="ej. A4:CF:12:9A:00:07"
            value={serial}
            onChange={(e) => setSerial(e.target.value)}
          />
        </div>
        <div className={styles.group}>
          <label className={styles.label}>Macro-zona</label>
          <select
            className={styles.select}
            value={zonaId}
            disabled={zonasLoading || zonas.length === 0}
            onChange={(e) => setZonaId(e.target.value)}
          >
            {zonas.length === 0 ? (
              <option value="">{zonasLoading ? 'Cargando…' : 'Sin macro-zonas'}</option>
            ) : (
              zonas.map((z) => (
                <option key={z} value={z}>
                  {z}
                </option>
              ))
            )}
          </select>
        </div>
        <button
          type="button"
          className={styles.btnPrimary}
          disabled={busy || zonasLoading || zonas.length === 0}
          onClick={handleSubmit}
        >
          {busy ? 'Creando…' : 'Crear sensor'}
        </button>
      </div>
      {error && <div className={styles.formError}>{error}</div>}
    </Card>
  );
}
