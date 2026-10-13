/* ============================================================
   Interruptor "Mostrar Demo Expo". A diferencia del resto de Configuración NO pasa por el
   borrador ni por "Guardar cambios": es una preferencia de visualización, no un parámetro
   operativo, y se guarda en el momento para que la pestaña aparezca o desaparezca ya.
   ============================================================ */
import { useId, useState } from 'react';
import { useDemoExpo } from '@/hooks/DemoExpoContext';
import { Switch } from '@/components/ui/Seleccion';
import styles from './DemoExpoSwitch.module.css';

interface DemoExpoSwitchProps {
  /** false = el rol no tiene `demo-expo.configurar`: se ve el valor pero no se puede cambiar. */
  editable?: boolean;
}

export function DemoExpoSwitch({ editable = true }: DemoExpoSwitchProps) {
  const { visible, cargando, cambiar } = useDemoExpo();
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const id = useId();

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
      <div className={styles.row}>
        <span className={styles.texts}>
          <span id={`${id}-label`} className={styles.label}>
            Mostrar Demo Expo
          </span>
          <span className={styles.hint}>
            {editable ? 'Muestra la pestaña Demo Expo en el menú' : 'Tu rol no puede cambiar esta preferencia'}
          </span>
        </span>
        <Switch
          checked={visible}
          labelledBy={`${id}-label`}
          disabled={!editable || cargando || guardando}
          onChange={(v) => void onChange(v)}
        />
      </div>
      {error && (
        <div role="alert" className={styles.error}>
          No se pudo guardar: {error}
        </div>
      )}
    </div>
  );
}
