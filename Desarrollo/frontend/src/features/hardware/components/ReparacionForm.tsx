/* Registro de la reparación de un dispositivo averiado, con el detalle obligatorio (S-05 · HU-21 CA-04) */
import { useId, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { describirFalla } from '@/lib/fallas';
import type { Dispositivo } from '@/types/domain';
import styles from '../Hardware.module.css';

interface ReparacionFormProps {
  /** El dispositivo averiado que se repara. */
  device: Dispositivo;
  busy: boolean;
  onSubmit: (detalle: string) => Promise<void>;
  onCancel: () => void;
}

export function ReparacionForm({ device, busy, onSubmit, onCancel }: ReparacionFormProps) {
  const campoId = useId();
  const [detalle, setDetalle] = useState('');
  const [error, setError] = useState<string | null>(null);
  const falla = device.falla ? describirFalla(device.falla) : null;

  const handleSubmit = async () => {
    setError(null);
    try {
      await onSubmit(detalle.trim());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <Card className={styles.section}>
      <div className={styles.sectionHead}>
        <span className={styles.sectionTitle}>
          Registrar reparación · {device.tipoLabel} · {device.ubicacion}
        </span>
        <span className={styles.sectionHint}>
          {falla ? `${falla.titulo}${falla.detalle ? ` (${falla.detalle})` : ''}. ` : ''}
          Al confirmar vuelve a lo autónomo en el siguiente pulso
        </span>
      </div>

      <div className={styles.formGrid}>
        <div className={`${styles.group} ${styles.groupGrow}`}>
          <label className={styles.label} htmlFor={campoId}>
            Qué se hizo
          </label>
          <input
            id={campoId}
            className={styles.input}
            type="text"
            placeholder="ej. Se destapó el emisor y se probó el caudal"
            value={detalle}
            onChange={(e) => setDetalle(e.target.value)}
          />
        </div>

        <button type="button" className={styles.btnSecondary} onClick={onCancel}>
          Cancelar
        </button>
        <button
          type="button"
          className={styles.btnPrimary}
          disabled={busy || detalle.trim() === ''}
          onClick={handleSubmit}
        >
          {busy ? 'Guardando…' : 'Confirmar reparación'}
        </button>
      </div>

      {error && <div className={styles.formError}>{error}</div>}
    </Card>
  );
}
