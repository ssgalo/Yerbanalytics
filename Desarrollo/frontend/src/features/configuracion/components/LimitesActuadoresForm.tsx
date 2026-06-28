/* Límites operativos de riego e insumos (HU-15 CA-04 / CA-05) */
import type { ConfigOperativa } from '@/types/domain';
import { NumberField } from './NumberField';
import styles from './ConfigForms.module.css';

interface LimitesActuadoresFormProps {
  value: ConfigOperativa;
  onChange: (patch: Partial<ConfigOperativa>) => void;
}

export function LimitesActuadoresForm({ value, onChange }: LimitesActuadoresFormProps) {
  return (
    <div className={styles.grid}>
      <NumberField
        label="Tiempo máx. de apertura de riego"
        unit="s"
        value={value.riegoTiempoMaxSeg}
        invalid={value.riegoTiempoMaxSeg <= 0}
        onChange={(v) => onChange({ riegoTiempoMaxSeg: v })}
      />
      <NumberField
        label="Volumen máx. diario de riego"
        unit="ml/día"
        value={value.riegoVolMaxDiarioMl}
        invalid={value.riegoVolMaxDiarioMl <= 0}
        onChange={(v) => onChange({ riegoVolMaxDiarioMl: v })}
      />
      <NumberField
        label="Dosis máx. de insumo"
        unit="ml/24 h"
        step={0.5}
        value={value.insumoDosisMax24hMl}
        invalid={value.insumoDosisMax24hMl <= 0}
        onChange={(v) => onChange({ insumoDosisMax24hMl: v })}
      />
      <NumberField
        label="Apertura máx. de mediasombra"
        unit="%"
        value={value.mediasombraAperturaMaxPct}
        invalid={value.mediasombraAperturaMaxPct <= 0 || value.mediasombraAperturaMaxPct > 100}
        onChange={(v) => onChange({ mediasombraAperturaMaxPct: v })}
      />
    </div>
  );
}
