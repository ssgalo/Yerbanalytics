/* Panel lateral del Inspector: el detalle completo de una regla evaluada. */
import { clasificarRegla, type EstadoNodo } from '@/components/DAGViewer/trazaANodos';
import type { TrazaRegla } from '@/types/domain';
import { formatoComparacion } from './lecturaTraza';
import styles from './Inspector.module.css';

interface NodoPanelProps {
  regla: TrazaRegla;
  label: string;
  /** La regla declara parámetros en el catálogo: se puede ir a editarlos. */
  tieneParametros: boolean;
  /** Nombre legible de una regla por id. */
  nombreDe: (id: string) => string;
  onEditar: (ruleId: string) => void;
  onCerrar: () => void;
}

const TITULO_ESTADO: Partial<Record<EstadoNodo, string>> = {
  paso: 'Pasó',
  bloqueo: 'Bloqueó',
  pospuso: 'Pospuso',
  accion: 'Accionó',
  omitida: 'Omitida',
  noAlcanzada: 'No alcanzada',
  error: 'Error',
};

export function NodoPanel({ regla, label, tieneParametros, nombreDe, onEditar, onCerrar }: NodoPanelProps) {
  const estado = clasificarRegla(regla);
  const corto = regla.bloqueadaPor ? nombreDe(regla.bloqueadaPor) : null;

  return (
    <aside className={styles.panel} aria-label={`Detalle de ${label}`}>
      <div className={styles.panelCabecera}>
        <div>
          <h3 className={styles.panelTitulo}>{label}</h3>
          <div className={styles.panelSub}>
            {regla.ruleId} · rama {regla.rama} · prioridad {regla.prioridad}
          </div>
        </div>
        <button type="button" className={styles.cerrar} aria-label="Cerrar detalle" onClick={onCerrar}>
          ×
        </button>
      </div>

      <span className={styles.estadoChip} data-estado={estado}>
        {TITULO_ESTADO[estado]}
      </span>

      {regla.estado === 'OMITIDA_RAMA_BLOQUEADA' && (
        <p className={styles.panelNota}>Omitida: la rama la cortó {corto}. No llegó a evaluar nada.</p>
      )}
      {regla.estado === 'NO_ALCANZADA' && (
        <p className={styles.panelNota}>No alcanzada: el motor se detuvo en {corto} antes de llegar acá.</p>
      )}
      {regla.estado === 'ERROR' && <p className={styles.panelError}>{regla.error}</p>}

      {regla.comparaciones.length > 0 && (
        <section>
          <h4 className={styles.panelSeccion}>Comparaciones</h4>
          {regla.comparaciones.map((c, i) => {
            const f = formatoComparacion(c);
            return (
              <div key={i} className={styles.fila} data-testid="fila-comparacion" data-resultado={c.resultado}>
                <div className={styles.filaEtiqueta}>{c.etiqueta}</div>
                <div className={styles.filaValores}>
                  <span className={f.resultado === 'SIN_DATO' ? styles.sinDato : undefined}>{f.recibido}</span>
                  <span className={styles.operador}>{f.operador}</span>
                  <span>{f.umbral}</span>
                  <span className={styles.marca} data-resultado={c.resultado}>
                    {f.marca}
                  </span>
                </div>
                <div className={styles.filaClave}>
                  {c.configurable && c.clave ? c.clave : 'Condición fija: no se edita'}
                </div>
              </div>
            );
          })}
        </section>
      )}

      {regla.acciones.length > 0 && (
        <section>
          <h4 className={styles.panelSeccion}>Acción y motivo</h4>
          {regla.acciones.map((a, i) => (
            <div key={i} className={styles.accion}>
              <code className={styles.accionTipo} data-alerta={a.tipo === 'ALERTA'}>
                {a.tipo === 'ALERTA' ? '⚠ ALERTA' : a.tipo}
              </code>
              <p className={styles.accionMotivo}>{a.motivo}</p>
            </div>
          ))}
        </section>
      )}

      {tieneParametros && (
        <button type="button" className={styles.btnEditar} onClick={() => onEditar(regla.ruleId)}>
          Editar parámetro
        </button>
      )}
    </aside>
  );
}
