import { useId } from 'react';
import type { Sector, Zona } from '@/types/domain';
import { calcularGeometria } from '../geometriaSector';
import styles from './SectorDiagram.module.css';

interface SectorDiagramProps {
  sector: Sector;
  zona: Zona | null;
}

const GEO = calcularGeometria();

/** Azul del microaspersor: sólo se usa acá, no es un color de estado. */
const AGUA = '#3f8fd1';

/**
 * Dibujo del sector físico: 4 bandejas de 5×5 tubetes, un microaspersor compartido y el riel.
 *
 * Los 100 tubetes se pintan igual con el color del sector: el diagnóstico sale de UNA foto y se
 * aplica al sector completo, sin distinguir tubetes. El nodo testigo va fuera de la caja del
 * sector porque pertenece a la macro-zona.
 */
export function SectorDiagram({ sector, zona }: SectorDiagramProps) {
  const patronId = 'hatch' + useId().replace(/[^a-zA-Z0-9]/g, '');
  const { viewBox, caja, bandejas, tubetes, radio, aspersor, nodo } = GEO;
  const color = sector.color;
  const camX = caja.x + caja.w / 2;

  return (
    <div className={styles.wrap}>
      <svg
        viewBox={`0 0 ${viewBox.w} ${viewBox.h}`}
        className={styles.svg}
        role="img"
        aria-label={`Dibujo del sector ${sector.id}: 4 bandejas de 25 plantines, un microaspersor y el riel con la cámara`}
      >
        <defs>
          <pattern
            id={patronId}
            width="6"
            height="6"
            patternTransform="rotate(45)"
            patternUnits="userSpaceOnUse"
          >
            <rect width="6" height="6" style={{ fill: color }} opacity="0.3" />
            <line x1="0" y1="0" x2="0" y2="6" style={{ stroke: color }} strokeWidth="2" opacity="0.55" />
          </pattern>
        </defs>

        {/* Riel: recorre más que este sector */}
        <line x1="0" y1="16" x2={viewBox.w} y2="16" className={styles.rielGuia} />
        <rect x="0" y="22" width={viewBox.w} height="8" rx="4" className={styles.riel} />
        <line x1={camX} y1="30" x2={camX} y2="34" className={styles.rielBrazo} />
        <g transform={`translate(${camX - 11},34)`}>
          <rect x="0" y="6" width="22" height="15" rx="3" className={styles.camara} />
          <rect x="7" y="2" width="8" height="6" rx="1.5" className={styles.camara} />
          <circle cx="11" cy="13.5" r="5" className={styles.lente} />
          <circle cx="11" cy="13.5" r="2.4" className={styles.camara} />
        </g>
        <text x={camX + 18} y="46" className={styles.textoMuted}>
          riel/cámara del vivero
        </text>

        {/* Caja del sector */}
        <rect x={caja.x} y={caja.y} width={caja.w} height={caja.h} rx="14" className={styles.caja} />
        <text x={caja.x} y={caja.y - 8} className={styles.titulo}>
          Sector {sector.id}
        </text>

        {/* Área de riego del microaspersor (1 por sector) */}
        <circle
          cx={aspersor.x}
          cy={aspersor.y}
          r={aspersor.radioRiego}
          fill="none"
          stroke={AGUA}
          strokeWidth="1.5"
          strokeDasharray="4 4"
          opacity="0.55"
        />

        {bandejas.map((b, i) => (
          <rect key={i} x={b.x} y={b.y} width={b.w} height={b.h} rx="9" className={styles.bandeja} />
        ))}
        {tubetes.map((t, i) => (
          <circle
            key={i}
            cx={t.cx}
            cy={t.cy}
            r={radio}
            fill={sector.status === 'offline' ? undefined : `url(#${patronId})`}
            style={sector.status === 'offline' ? { fill: color, opacity: 0.45 } : undefined}
            className={styles.tubete}
          />
        ))}

        <circle cx={aspersor.x} cy={aspersor.y} r="6" fill={AGUA} />
        <text x={aspersor.x} y={aspersor.y + 20} textAnchor="middle" className={styles.aspersor}>
          microaspersor (1 por sector)
        </text>

        {/* Nodo testigo: deliberadamente fuera de la caja del sector */}
        <g transform={`translate(${nodo.x},${nodo.y})`}>
          <rect width={nodo.w} height={nodo.h} rx="10" className={styles.nodo} />
          <text x="12" y="20" className={styles.nodoTitulo}>
            Nodo testigo
          </text>
          <text x="12" y="34" className={styles.textoMuted}>
            de la macro-zona {zona?.id ?? sector.zona}
          </text>
          <text x="12" y="46" className={styles.textoMuted}>
            no está en este sector
          </text>
        </g>
        <path
          d={`M${caja.x + caja.w},${caja.y + 30} Q 450,60 ${nodo.x},${nodo.y + 20}`}
          className={styles.enlace}
        />
      </svg>

      <div className={styles.leyenda}>
        <span className={styles.item}>
          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
            <circle cx="12" cy="12" r="9" fill={`url(#${patronId})`} />
            <circle cx="12" cy="12" r="9" fill="none" style={{ stroke: color }} strokeWidth="1.5" />
          </svg>
          Los 100 tubetes llevan el diagnóstico del sector
        </span>
        <span className={styles.item}>
          <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true">
            <circle cx="12" cy="12" r="6" fill={AGUA} />
          </svg>
          Microaspersor + área de riego
        </span>
      </div>
    </div>
  );
}
