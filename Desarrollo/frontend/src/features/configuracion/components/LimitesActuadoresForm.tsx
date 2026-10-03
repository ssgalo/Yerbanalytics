/* Límites operativos de riego e insumos (HU-15 CA-04 / CA-05) */
import { Link } from 'react-router-dom';
import type { ConfigOperativa } from '@/types/domain';
import { NumberField } from './NumberField';
import styles from './ConfigForms.module.css';

interface LimitesActuadoresFormProps {
  value: ConfigOperativa;
  onChange: (patch: Partial<ConfigOperativa>) => void;
}

/**
 * Sólo quedan acá los topes que NINGUNA regla del motor compara (aparecen únicamente en textos
 * de decisión). El tiempo máx. de apertura de riego y la apertura máx. de mediasombra sí los
 * compara una regla: son parámetros del catálogo y se editan en Motor de reglas, para no tener
 * dos lugares que editen lo mismo.
 */
export function LimitesActuadoresForm({ value, onChange }: LimitesActuadoresFormProps) {
  return (
    <div className={styles.grid}>
      <div className={styles.aviso}>
        <span>
          Los umbrales que usan las reglas del motor —tiempo máx. de apertura de riego, apertura máx. de
          mediasombra y el resto— se editan en{' '}
          <Link to="/reglas">Motor de reglas</Link>.
        </span>
      </div>
      <NumberField
        label="Volumen máx. diario de riego"
        unit="ml/día"
        hint="No intervienen en decisiones del motor: sólo se informan en los textos."
        value={value.riegoVolMaxDiarioMl}
        invalid={value.riegoVolMaxDiarioMl <= 0}
        onChange={(v) => onChange({ riegoVolMaxDiarioMl: v })}
      />
      <NumberField
        label="Dosis máx. de insumo"
        unit="ml/24 h"
        step={0.5}
        hint="No intervienen en decisiones del motor: sólo se informan en los textos."
        value={value.insumoDosisMax24hMl}
        invalid={value.insumoDosisMax24hMl <= 0}
        onChange={(v) => onChange({ insumoDosisMax24hMl: v })}
      />
    </div>
  );
}
