/* Datos propios de un evento: volumen, duración y regla de un riego; nivel y regla de una alerta. */
import type { ActionRecord } from '@/types/domain';
import { formatoDuracion, formatoVolumen, NIVEL_ALERTA } from '../eventoPresentacion';
import styles from './EventoMeta.module.css';

interface EventoMetaProps {
  registro: ActionRecord;
  /** id de regla → nombre legible (del esquema del motor). Sin nombre se muestra el id. */
  nombresReglas: Record<string, string>;
}

export function EventoMeta({ registro: r, nombresReglas }: EventoMetaProps) {
  const chips: { clave: string; etiqueta: string; texto: string; estilo?: React.CSSProperties }[] = [];

  if (r.alerta) {
    const n = NIVEL_ALERTA[r.alerta];
    chips.push({ clave: 'nivel', etiqueta: 'Nivel', texto: n.nombre, estilo: { background: n.soft, color: n.ink } });
  }
  if (r.volumenL != null) chips.push({ clave: 'volumen', etiqueta: 'Volumen', texto: formatoVolumen(r.volumenL) });
  if (r.duracionSeg != null) chips.push({ clave: 'duracion', etiqueta: 'Duración', texto: formatoDuracion(r.duracionSeg) });
  if (r.regla) chips.push({ clave: 'regla', etiqueta: 'Regla', texto: nombresReglas[r.regla] ?? r.regla });

  if (chips.length === 0) return null;
  return (
    <div className={styles.meta} data-testid="evento-meta">
      {chips.map((c) => (
        <span key={c.clave} className={styles.chip} style={c.estilo}>
          <span className={styles.etiqueta}>{c.etiqueta}</span>
          <span>{c.texto}</span>
        </span>
      ))}
    </div>
  );
}
