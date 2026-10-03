/* Un paso de la pasada: ícono de estado, qué hace, cuánto tardó y (en las capturas) la foto y su diagnóstico. */
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Icon } from '@/components/ui/Icon';
import { Badge } from '@/components/ui/Badge';
import type { EstadoPaso, PasoPasada } from '@/types/domain';
import { duracionPasoSeg, textoPaso, tituloPaso } from '../pasadaPresentacion';
import styles from './PasoItem.module.css';

/** Color de cada estado, con los tokens del sistema. */
const COLOR: Record<EstadoPaso, { fg: string; bg: string }> = {
  PENDIENTE: { fg: 'var(--faint)', bg: 'var(--off-soft)' },
  EN_CURSO: { fg: 'var(--info)', bg: 'var(--info-soft)' },
  OK: { fg: 'var(--ok)', bg: 'var(--ok-soft)' },
  ERROR: { fg: 'var(--crit)', bg: 'var(--crit-soft)' },
  OMITIDO: { fg: 'var(--off)', bg: 'var(--off-soft)' },
};

const SEVERIDAD: Record<string, { soft: string; ink: string }> = {
  Alta: { soft: 'var(--crit-soft)', ink: 'var(--crit-ink)' },
  Media: { soft: 'var(--warn-soft)', ink: 'var(--warn-ink)' },
  Baja: { soft: 'var(--ok-soft)', ink: 'var(--ok-ink)' },
};
const SEVERIDAD_NEUTRA = { soft: 'var(--off-soft)', ink: 'var(--muted)' };

function IconoEstado({ paso }: { paso: PasoPasada }) {
  const { fg, bg } = COLOR[paso.estado];
  return (
    <span className={styles.icono} style={{ background: bg, color: fg }} aria-hidden="true">
      {paso.estado === 'EN_CURSO' && <span className={styles.spinner} style={{ borderTopColor: fg }} />}
      {paso.estado === 'OK' && <Icon name="check" size={22} strokeWidth={2.4} />}
      {paso.estado === 'ERROR' && <Icon name="alert" size={22} strokeWidth={2.2} />}
      {paso.estado === 'OMITIDO' && <Icon name="close" size={20} strokeWidth={2.2} />}
      {paso.estado === 'PENDIENTE' && <span className={styles.numero}>{paso.n}</span>}
    </span>
  );
}

function Miniatura({ paso }: { paso: PasoPasada }) {
  // Si la imagen no carga, un recuadro neutro en lugar del ícono roto del navegador.
  const [rota, setRota] = useState(false);
  return (
    <div className={styles.miniatura}>
      {paso.imagenUrl && !rota ? (
        <img
          className={styles.foto}
          src={paso.imagenUrl}
          alt={`Foto del sector ${paso.sectorId}`}
          width={160}
          height={107}
          onError={() => setRota(true)}
        />
      ) : (
        <Icon name="camera" size={28} stroke="var(--faint)" />
      )}
    </div>
  );
}

function Diagnostico({ paso }: { paso: PasoPasada }) {
  const d = paso.diagnostico;
  if (!d) {
    return (
      <div className={styles.espera}>
        <span className={styles.puntito} />
        <span>
          <strong>Esperando diagnóstico de IA</strong>{' '}
          <span className={styles.ayuda}>(se analiza tras 1 min sin fotos nuevas)</span>
        </span>
      </div>
    );
  }
  const sev = SEVERIDAD[d.sev] ?? SEVERIDAD_NEUTRA;
  return (
    <div className={styles.diagnostico}>
      <span className={styles.diagEstado}>{d.estado}</span>
      <span className={styles.diagConf}>{Math.round(d.conf)} %</span>
      <Badge soft={sev.soft} ink={sev.ink}>
        {d.sev}
      </Badge>
      <Link to="/diagnosticos" className={styles.enlace}>
        Ver en Diagnósticos de IA
      </Link>
    </div>
  );
}

export function PasoItem({ paso, ahoraMs }: { paso: PasoPasada; ahoraMs: number }) {
  const { fg } = COLOR[paso.estado];
  const seg = duracionPasoSeg(paso, ahoraMs);
  const esCaptura = paso.tipo === 'CAPTURAR';
  const hayFoto = esCaptura && paso.capturaId !== null;
  const falla = paso.estado === 'ERROR';

  return (
    <li className={styles.item} style={paso.estado === 'EN_CURSO' ? { borderColor: 'var(--info)' } : undefined}>
      <IconoEstado paso={paso} />
      <div className={styles.cuerpo}>
        <div className={styles.cabecera}>
          <span className={styles.titulo} style={{ opacity: paso.estado === 'PENDIENTE' ? 0.55 : 1 }}>
            {tituloPaso(paso)}
          </span>
          {seg !== null && <span className={styles.tiempo}>{seg} s</span>}
        </div>
        <div className={styles.estado} style={{ color: falla ? 'var(--crit)' : fg }}>
          {textoPaso(paso)}
        </div>
        {hayFoto && <Diagnostico paso={paso} />}
      </div>
      {hayFoto && <Miniatura paso={paso} />}
    </li>
  );
}
