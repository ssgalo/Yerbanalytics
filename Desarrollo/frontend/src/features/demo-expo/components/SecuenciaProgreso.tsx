/* ============================================================
   Avance de una secuencia: badge de estado, botón Cancelar, banner de error, los tres pasos
   (reusando el ícono y el estilo de `PasoItem`) y, en la lectura, la tabla de valores.
   ============================================================ */
import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/Badge';
import type { PasoSecuencia, Secuencia } from '@/types/domain';
import { ETIQUETA_ESTADO } from '../pasadaPresentacion';
import {
  duracionPasoSecuenciaSeg,
  filasLectura,
  textoPasoSecuencia,
  tituloPasoSecuencia,
} from '../secuenciaPresentacion';
import { COLOR, COLOR_ESTADO } from './colorPaso';
import { IconoEstado } from './PasoItem';
import pasoStyles from './PasoItem.module.css';
import styles from './SecuenciaProgreso.module.css';

function PasoFila({
  paso,
  secuencia,
  ahoraMs,
}: {
  paso: PasoSecuencia;
  secuencia: Secuencia;
  ahoraMs: number;
}) {
  const { fg } = COLOR[paso.estado];
  const seg = duracionPasoSecuenciaSeg(paso, ahoraMs);
  return (
    <li
      className={pasoStyles.item}
      style={paso.estado === 'EN_CURSO' ? { borderColor: 'var(--info)' } : undefined}
    >
      <IconoEstado estado={paso.estado} n={paso.n} />
      <div className={pasoStyles.cuerpo}>
        <div className={pasoStyles.cabecera}>
          <span
            className={pasoStyles.titulo}
            style={{ opacity: paso.estado === 'PENDIENTE' ? 0.55 : 1 }}
          >
            {tituloPasoSecuencia(paso, secuencia)}
          </span>
          {seg !== null && <span className={pasoStyles.tiempo}>{seg} s</span>}
        </div>
        <div
          className={pasoStyles.estado}
          style={{ color: paso.estado === 'ERROR' ? 'var(--crit)' : fg }}
        >
          {textoPasoSecuencia(paso, ahoraMs)}
        </div>
      </div>
    </li>
  );
}

function TablaLectura({ secuencia }: { secuencia: Secuencia }) {
  const filas = filasLectura(secuencia.lectura);
  if (filas.length === 0) return null;
  return (
    <div className={styles.lectura}>
      <table className={styles.tabla}>
        <caption className={styles.caption}>Lectura de {secuencia.zonaId}</caption>
        <tbody>
          {filas.map((f) => (
            <tr key={f.key}>
              <th scope="row">{f.etiqueta}</th>
              <td className={styles.valor}>{f.valor}</td>
              <td className={styles.unidad}>{f.unidad}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Link to="/reglas" className={styles.enlace}>
        Ver la evaluación en el Inspector
      </Link>
    </div>
  );
}

interface Props {
  secuencia: Secuencia;
  onCancelar: () => void;
}

export function SecuenciaProgreso({ secuencia, onCancelar }: Props) {
  const enCurso = secuencia.estado === 'EN_CURSO';
  const color = COLOR_ESTADO[secuencia.estado];
  const ahoraMs = Date.now();

  return (
    <div className={styles.progreso}>
      <div className={styles.cabecera}>
        <Badge soft={color.soft} ink={color.ink} style={{ fontSize: '13px', padding: '5px 14px' }}>
          {ETIQUETA_ESTADO[secuencia.estado]}
          {enCurso && secuencia.cancelacionSolicitada ? ' · cancelando…' : ''}
        </Badge>
        {enCurso && !secuencia.cancelacionSolicitada && (
          <button type="button" className={styles.btnCancelar} onClick={onCancelar}>
            Cancelar
          </button>
        )}
      </div>

      {secuencia.error && !enCurso && (
        <div role="alert" className={styles.error}>
          {secuencia.error}
        </div>
      )}

      <ol className={styles.pasos}>
        {secuencia.pasos.map((paso) => (
          <PasoFila key={paso.n} paso={paso} secuencia={secuencia} ahoraMs={ahoraMs} />
        ))}
      </ol>

      {secuencia.tipo === 'LECTURA' && <TablaLectura secuencia={secuencia} />}
    </div>
  );
}
