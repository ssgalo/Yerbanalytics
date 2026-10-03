import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Zona } from '@/types/domain';
import { Icon } from '@/components/ui/Icon';
import {
  columnasGrilla,
  contadoresZona,
  estadoZona,
  resumenZona,
  textoPlantines,
} from '../resumenVivero';
import { NodoChip } from './NodoChip';
import styles from './ZonaBlock.module.css';

export interface FocoZona {
  zonaId: string;
  /** Cambia en cada toque, para volver a destacar una zona ya destacada. */
  tick: number;
}

interface ZonaBlockProps {
  zona: Zona;
  sectoresPorFila: number;
  foco: FocoZona | null;
}

/** Bloque de una macro-zona: resumen en palabras, nodo testigo y grilla de sectores. */
export function ZonaBlock({ zona, sectoresPorFila, foco }: ZonaBlockProps) {
  const navigate = useNavigate();
  const ref = useRef<HTMLElement>(null);
  const estado = estadoZona(zona);
  const sinDatos = zona.lectura.stale;
  const enFoco = foco?.zonaId === zona.id ? foco.tick : null;

  /* Al tocar su parcela en el plano: desplazar hasta acá. */
  useEffect(() => {
    if (enFoco !== null) ref.current?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
  }, [enFoco]);

  return (
    <section ref={ref} id={`zona-${zona.id}`} className={styles.block} data-status={estado}>
      {/* Destello al llegar desde el plano; `key` reinicia la animación en cada toque */}
      {enFoco !== null && <span key={enFoco} className={styles.flash} aria-hidden="true" />}

      <div className={styles.head}>
        <div className={styles.id}>
          <span className={styles.badge} data-status={estado}>
            {zona.id}
          </span>
          <span className={styles.sub}>
            1 sensor testigo · {textoPlantines(zona)}
          </span>
        </div>
        <NodoChip nodo={zona.nodo} sinDatos={sinDatos} />
      </div>

      <div className={styles.plain} data-status={estado}>
        {resumenZona(zona)}
      </div>
      <div className={styles.stats}>{contadoresZona(zona)}</div>

      {sinDatos && (
        <div className={styles.empty}>
          <Icon name="wifi-off" size={22} stroke="var(--off)" strokeWidth={1.8} />
          <p>Nodo testigo sin datos — la lectura de esta macro-zona no es vigente</p>
        </div>
      )}

      {/* Celdas de tamaño acotado y centradas: no crecen aunque haya un solo sector */}
      <div className={styles.heatmapWrap}>
        <div
          className={styles.heatmap}
          style={{
            gridTemplateColumns: `repeat(${columnasGrilla(zona.sectors.length, sectoresPorFila)}, minmax(10px, 22px))`,
          }}
        >
          {zona.sectors.map((s) => (
            <button
              key={s.id}
              type="button"
              className={styles.cell}
              style={{ background: s.color }}
              title={s.tip}
              aria-label={`Sector ${s.id}: ${s.statusLabel}`}
              onClick={() => navigate('/sector/' + s.id)}
            />
          ))}
        </div>
      </div>

      <button
        type="button"
        className={styles.detalle}
        onClick={() => navigate('/mapa?zona=' + zona.id)}
      >
        Ver detalle de la zona
        <Icon name="arrow-right" size={13} strokeWidth={2} />
      </button>
    </section>
  );
}
