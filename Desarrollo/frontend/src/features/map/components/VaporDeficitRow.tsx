/* ============================================================
   Déficit de presión de vapor (DPV) de la macro-zona.

   Es una métrica derivada (la calcula el backend con la temperatura del aire y la humedad ambiental de la
   lectura) e informativa: se muestra con su tramo pero no mueve la salud de los sectores. A diferencia de las
   métricas sensadas no abre un histórico ni tiene banda de rango, por eso no es un botón.
   ============================================================ */
import type { Status, VaporDeficit } from '@/types/domain';
import { StateGlyph } from '@/components/ui/StateGlyph';
import { TONO, cx } from '@/components/ui/estado';
import { fmtNum } from '@/components/ui/rango';
import { VAPOR_BAND_STATUS } from '../vaporBand';
import styles from './SensingCard.module.css';

interface VaporDeficitRowProps {
  /** `null` si la zona nunca tuvo temperatura o humedad ambiental, o el nodo está fuera de servicio. */
  dpv: VaporDeficit | null;
}

export function VaporDeficitRow({ dpv }: VaporDeficitRowProps) {
  const status: Status = dpv ? VAPOR_BAND_STATUS[dpv.band] : 'offline';

  return (
    <div className={cx(styles.row, styles.rowStatic)}>
      <span className={styles.name}>
        <span className={styles.label}>Déficit de presión de vapor</span>
        <span className={cx(styles.status, styles[TONO[status]])}>
          <StateGlyph status={status} />
          {dpv ? dpv.label : 'sin dato'}
        </span>
      </span>

      <span className={styles.value}>
        {dpv ? (
          <>
            <span className={styles.number}>{fmtNum(dpv.value, 2)}</span>
            <span className={styles.unit}>kPa</span>
          </>
        ) : (
          <span className={styles.number}>—</span>
        )}
      </span>
    </div>
  );
}
