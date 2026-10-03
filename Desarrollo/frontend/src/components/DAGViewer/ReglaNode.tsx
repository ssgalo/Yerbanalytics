/* ============================================================
   Nodo de regla del DAG en modo traza (nodo personalizado de React Flow).

   Muestra, de un vistazo, qué pasó con la regla y "recibido vs. umbral" de hasta dos de sus
   comparaciones. El clic y el panel con todo el detalle los resuelve quien lo monta.
   ============================================================ */
import { Handle, Position, type Node, type NodeProps } from '@xyflow/react';
import { formatoComparacion } from '@/features/reglas/inspector/lecturaTraza';
import type { TrazaRegla } from '@/types/domain';
import type { EstadoNodo } from './trazaANodos';
import styles from './ReglaNode.module.css';

/** Cantidad de comparaciones que caben en el nodo; el resto está en el panel. */
const MAX_COMPARACIONES = 2;

export interface ReglaNodeData extends Record<string, unknown> {
  label: string;
  ruleId: string;
  estado: EstadoNodo;
  /** La traza de esta regla; null si la evaluación no la menciona. */
  traza: TrazaRegla | null;
  /** Nombre de la regla que cortó la rama (omitida) o el motor (no alcanzada). */
  bloqueadaPorLabel: string | null;
  seleccionado: boolean;
}

export type ReglaNodeType = Node<ReglaNodeData, 'regla'>;

const TEXTO_ESTADO: Partial<Record<EstadoNodo, string>> = {
  paso: 'Pasó',
  bloqueo: 'Bloqueó',
  pospuso: 'Pospuso',
  accion: 'Accionó',
  omitida: 'Omitida',
  noAlcanzada: 'No alcanzada',
  error: 'Error',
  sinTraza: 'Sin evaluar',
};

const MARCA_CLASE = { CUMPLE: 'marcaSi', NO_CUMPLE: 'marcaNo', SIN_DATO: 'marcaSd' } as const;

/** Contenido del nodo, sin nada de React Flow: se prueba y se estiliza aparte. */
export function ReglaNodeView({ data }: { data: ReglaNodeData }) {
  const { traza, estado } = data;
  const comparaciones = traza?.estado === 'EVALUADA' ? traza.comparaciones : [];
  const visibles = comparaciones.slice(0, MAX_COMPARACIONES);
  const ocultas = comparaciones.length - visibles.length;

  return (
    <div className={styles.nodo} data-estado={estado} data-seleccionado={data.seleccionado}>
      <div className={styles.cabecera}>
        <span className={styles.titulo}>{data.label}</span>
        <span className={styles.chip}>{TEXTO_ESTADO[estado] ?? ''}</span>
      </div>

      {traza?.estado === 'EVALUADA' && comparaciones.length === 0 && (
        <div className={styles.nota}>Sin comparaciones</div>
      )}

      {visibles.map((c, i) => {
        const f = formatoComparacion(c);
        return (
          <div
            key={i}
            className={styles.comparacion}
            data-testid={`comparacion-${i}`}
            data-resultado={c.resultado}
          >
            <div className={styles.compEtiqueta}>
              {!c.configurable && (
                <span
                  className={styles.candado}
                  role="img"
                  aria-label="No configurable"
                  title="Condición fija: no se edita"
                >
                  🔒
                </span>
              )}
              {c.etiqueta}
            </div>
            <div className={styles.compValores}>
              <span className={f.resultado === 'SIN_DATO' ? styles.sinDato : undefined}>
                {f.recibido}
              </span>
              <span className={styles.operador}>{f.operador}</span>
              <span>{f.umbral}</span>
              <span className={`${styles.marca} ${styles[MARCA_CLASE[f.resultado]]}`}>
                {f.marca}
              </span>
            </div>
          </div>
        );
      })}

      {ocultas > 0 && (
        <div className={styles.nota}>
          +{ocultas} {ocultas === 1 ? 'comparación más' : 'comparaciones más'}
        </div>
      )}

      {estado === 'omitida' && (
        <div className={styles.nota}>
          Omitida: la cortó {data.bloqueadaPorLabel ?? traza?.bloqueadaPor ?? 'una regla anterior'}
        </div>
      )}
      {estado === 'noAlcanzada' && (
        <div className={styles.nota}>
          El motor se detuvo en{' '}
          {data.bloqueadaPorLabel ?? traza?.bloqueadaPor ?? 'una regla anterior'}
        </div>
      )}
      {estado === 'error' && (
        <div className={styles.nota}>{traza?.error ?? 'La regla lanzó una excepción'}</div>
      )}
    </div>
  );
}

/** Nodo de React Flow: el contenido más los puntos de conexión. */
export function ReglaNode({ data }: NodeProps<ReglaNodeType>) {
  return (
    <>
      <Handle
        type="target"
        position={Position.Top}
        className={styles.handle}
        isConnectable={false}
      />
      <ReglaNodeView data={data} />
      <Handle
        type="source"
        position={Position.Bottom}
        className={styles.handle}
        isConnectable={false}
      />
    </>
  );
}
