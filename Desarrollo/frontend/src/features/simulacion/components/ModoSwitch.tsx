/* Switch entre modo de datos estáticos y modo de simulación manual */
import { Card } from '@/components/ui/Card';
import type { ModoSimulacion } from '@/types/domain';
import styles from '../Simulacion.module.css';

interface ModoSwitchProps {
  modo: ModoSimulacion;
  busy: boolean;
  onChange: (modo: ModoSimulacion) => void;
}

export function ModoSwitch({ modo, busy, onChange }: ModoSwitchProps) {
  const esSim = modo === 'simulacion';
  return (
    <Card className={styles.switchCard}>
      <div className={styles.switchInfo}>
        <div className={styles.switchTitle}>
          Modo de operación: {esSim ? 'Simulación' : 'Datos estáticos'}
        </div>
        <div className={styles.switchHint}>
          {esSim
            ? 'Creá sensores y enviá lecturas por MQTT: el vivero refleja lo que enviás.'
            : 'El vivero muestra los datos sembrados, sin ingesta. Activá la simulación para enviar lecturas.'}
        </div>
      </div>
      <div className={styles.toggle} role="group" aria-label="Modo de operación">
        <button
          type="button"
          className={!esSim ? `${styles.toggleOption} ${styles.toggleActive}` : styles.toggleOption}
          disabled={busy || !esSim}
          onClick={() => onChange('estatico')}
        >
          Estático
        </button>
        <button
          type="button"
          className={esSim ? `${styles.toggleOption} ${styles.toggleActive}` : styles.toggleOption}
          disabled={busy || esSim}
          onClick={() => onChange('simulacion')}
        >
          Simulación
        </button>
      </div>
    </Card>
  );
}
