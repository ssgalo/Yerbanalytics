/* Límites operativos de insumos (HU-15 CA-05) */
import { Link } from 'react-router-dom';
import type { ConfigOperativa } from '@/types/domain';
import { Callout } from '@/components/ui/Callout';
import { NumberField } from './NumberField';
import styles from './ConfigForms.module.css';

interface LimitesActuadoresFormProps {
  value: ConfigOperativa;
  saved?: ConfigOperativa;
  onChange: (patch: Partial<ConfigOperativa>) => void;
}

/**
 * Sólo quedan acá los topes que NINGUNA regla del motor compara (aparecen únicamente en textos
 * de decisión). El volumen de riego y la apertura máx. de mediasombra sí los compara una
 * regla: son parámetros del catálogo y se editan en Motor de reglas, para no tener dos lugares
 * que editen lo mismo. (El volumen máx. diario de riego se dio de baja con los límites diarios.)
 */
export function LimitesActuadoresForm({ value, saved, onChange }: LimitesActuadoresFormProps) {
  return (
    <div className={styles.stack}>
      <NumberField
        label="Dosis máx. de insumo"
        unit="ml/24 h"
        step={0.5}
        hint="No intervienen en decisiones del motor: sólo se informan en los textos."
        value={value.insumoDosisMax24hMl}
        invalid={value.insumoDosisMax24hMl <= 0}
        modified={saved !== undefined && saved.insumoDosisMax24hMl !== value.insumoDosisMax24hMl}
        onChange={(v) => onChange({ insumoDosisMax24hMl: v })}
      />
      <Callout>
        Los umbrales que usan las reglas —volumen y caudal de riego, apertura máx. de mediasombra y el
        resto— se editan en <Link to="/reglas">Motor de reglas</Link>.
      </Callout>
    </div>
  );
}
