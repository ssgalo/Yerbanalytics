/* ============================================================
   Interruptor "Mostrar Demo Expo". A diferencia del resto de Configuración NO pasa por el
   borrador ni por "Guardar cambios": es una preferencia de visualización, no un parámetro
   operativo, y se guarda en el momento para que la pestaña aparezca o desaparezca ya.
   ============================================================ */
import { useState } from 'react';
import { useDemoExpo } from '@/hooks/DemoExpoContext';
import styles from './DemoExpoSwitch.module.css';

export function DemoExpoSwitch() {
  const { visible, cargando, cambiar } = useDemoExpo();
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onChange = async (nuevo: boolean) => {
    setError(null);
    setGuardando(true);
    try {
      await cambiar(nuevo);
    } catch (e) {
      // `cambiar` no toca el estado si falla, así que el switch ya vuelve solo al valor previo.
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div>
      <label className={styles.row}>
        <input
          type="checkbox"
          role="switch"
          className={styles.input}
          checked={visible}
          disabled={cargando || guardando}
          onChange={(e) => void onChange(e.target.checked)}
        />
        <span className={styles.track} aria-hidden="true">
          <span className={styles.thumb} />
        </span>
        <span className={styles.texts}>
          <span className={styles.label}>Mostrar Demo Expo</span>
          <span className={styles.hint}>Muestra la pestaña Demo Expo en el menú</span>
        </span>
      </label>
      {error && (
        <div role="alert" className={styles.error}>
          No se pudo guardar: {error}
        </div>
      )}
    </div>
  );
}
