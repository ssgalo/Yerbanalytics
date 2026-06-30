/* Alta y recambio de dispositivos con validación en cliente (HU-18 CA-02/03 · HU-21 CA-05) */
import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { NODO_TESTIGO, TIPO_OPTIONS, ZONA_IDS } from '@/data/mock/hardware';
import type { Dispositivo, NuevoDispositivo } from '@/types/domain';
import styles from '../Hardware.module.css';

interface AltaHardwareFormProps {
  mode: 'alta' | 'recambio';
  /** Dispositivo a recambiar (sólo en modo recambio). */
  device?: Dispositivo;
  busy: boolean;
  onSubmit: (device: NuevoDispositivo) => Promise<void>;
  onCancel: () => void;
}

export function AltaHardwareForm({ mode, device, busy, onSubmit, onCancel }: AltaHardwareFormProps) {
  const esRecambio = mode === 'recambio';
  const [serial, setSerial] = useState('');
  const [tipo, setTipo] = useState(device?.tipo ?? NODO_TESTIGO);
  const [zonaId, setZonaId] = useState(device?.zonaId ?? ZONA_IDS[0]);
  const [sectorId, setSectorId] = useState(device?.sectorId ?? '');
  const [error, setError] = useState<string | null>(null);

  const esNodo = (esRecambio ? device?.tipo : tipo) === NODO_TESTIGO;

  /** Validación en cliente, espejo del backend (HU-18 CA-02). */
  function validar(): string | null {
    if (!serial.trim()) {
      return esRecambio ? 'Ingresá el serial/MAC de la pieza nueva.' : 'El serial/MAC es obligatorio.';
    }
    if (esRecambio) return null;
    if (esNodo) {
      if (!zonaId.trim()) return 'Elegí la macro-zona del nodo testigo.';
    } else if (!sectorId.trim()) {
      return 'Ingresá el sector del actuador (ej. MZ-1-003).';
    }
    return null;
  }

  const handleSubmit = async () => {
    const localError = validar();
    if (localError) {
      setError(localError);
      return;
    }
    setError(null);
    const payload: NuevoDispositivo = esRecambio
      ? {
          serial: serial.trim(),
          tipo: device!.tipo,
          zonaId: device!.zonaId,
          sectorId: device!.sectorId,
        }
      : {
          serial: serial.trim(),
          tipo,
          zonaId: esNodo ? zonaId.trim() : null,
          sectorId: esNodo ? null : sectorId.trim(),
        };
    try {
      await onSubmit(payload);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <Card className={styles.section}>
      <div className={styles.sectionHead}>
        <span className={styles.sectionTitle}>
          {esRecambio ? `Recambiar ${device?.tipoLabel} · ${device?.ubicacion}` : 'Registrar dispositivo'}
        </span>
        <span className={styles.sectionHint}>
          {esRecambio
            ? 'La pieza nueva reutiliza el registro y limpia la avería'
            : 'Asociá el equipo a su sector o macro-zona por serial/MAC'}
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

        {esRecambio ? (
          <div className={styles.group}>
            <label className={styles.label}>Tipo · ubicación</label>
            <span className={styles.fixedValue}>
              {device?.tipoLabel} · {device?.ubicacion}
            </span>
          </div>
        ) : (
          <>
            <div className={styles.group}>
              <label className={styles.label}>Tipo</label>
              <select className={styles.select} value={tipo} onChange={(e) => setTipo(e.target.value)}>
                {TIPO_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </div>

            {esNodo ? (
              <div className={styles.group}>
                <label className={styles.label}>Macro-zona</label>
                <select
                  className={styles.select}
                  value={zonaId}
                  onChange={(e) => setZonaId(e.target.value)}
                >
                  {ZONA_IDS.map((z) => (
                    <option key={z} value={z}>
                      {z}
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <div className={styles.group}>
                <label className={styles.label}>Sector</label>
                <input
                  className={styles.input}
                  type="text"
                  placeholder="ej. MZ-1-003"
                  value={sectorId}
                  onChange={(e) => setSectorId(e.target.value)}
                />
              </div>
            )}
          </>
        )}

        <span className={styles.toolbarSpacer} />
        <button type="button" className={styles.btnSecondary} onClick={onCancel}>
          Cancelar
        </button>
        <button type="button" className={styles.btnPrimary} disabled={busy} onClick={handleSubmit}>
          {busy ? 'Guardando…' : esRecambio ? 'Confirmar recambio' : 'Registrar'}
        </button>
      </div>

      {error && <div className={styles.formError}>{error}</div>}
    </Card>
  );
}
