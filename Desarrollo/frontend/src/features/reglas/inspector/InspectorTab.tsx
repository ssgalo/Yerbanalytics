/* ============================================================
   Pestaña Inspector: el DAG del motor pintado con la última evaluación de un sector.

   Sirve para probar y entender el motor: qué recibió cada regla, contra qué umbral, y por qué
   una regla disparó o no. La traza es en vivo (vive en memoria en el backend), no auditoría.
   ============================================================ */
import { useEffect, useMemo, useState } from 'react';
import { RuleGraph } from '@/components/DAGViewer/RuleGraph';
import { useNurseryData } from '@/hooks/NurseryContext';
import { useRuleEngineSchema } from '@/hooks/useRuleEngineSchema';
import { useTrazaEvaluacion } from '@/hooks/useTrazaEvaluacion';
import type { OrigenEvaluacion } from '@/types/domain';
import { etiquetaOrigen, resumenTraza } from './lecturaTraza';
import { NodoPanel } from './NodoPanel';
import styles from './Inspector.module.css';

interface InspectorTabProps {
  /** Sector que llega por `?sector=` (desde el detalle de sector). */
  sectorInicial: string | null;
  /** Ir a Parámetros con esa regla abierta. */
  onEditarRegla: (ruleId: string) => void;
  /** Se eligió otro sector: la página lo refleja en la URL. */
  onSectorChange?: (sectorId: string) => void;
}

function fechaHora(iso: string): string {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export function InspectorTab({ sectorInicial, onEditarRegla, onSectorChange }: InspectorTabProps) {
  const { zonas, byId } = useNurseryData();
  const primero = zonas[0]?.sectors[0]?.id ?? '';
  const valido = (id: string | null): id is string => !!id && !!byId[id];

  const [sectorId, setSectorId] = useState(valido(sectorInicial) ? sectorInicial : primero);
  const [origen, setOrigen] = useState<OrigenEvaluacion>('TELEMETRIA');
  const [auto, setAuto] = useState(false);
  const [seleccionada, setSeleccionada] = useState<string | null>(null);

  // Si se navega a otro sector estando acá (mismo componente), se sigue la URL.
  useEffect(() => {
    if (valido(sectorInicial)) {
      setSectorId(sectorInicial);
      setSeleccionada(null);
    }
    // `valido` sólo depende de `byId`
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sectorInicial]);

  const zonaId = byId[sectorId]?.zona ?? zonas[0]?.id ?? '';
  const sectoresDeZona = useMemo(() => zonas.find((z) => z.id === zonaId)?.sectors ?? [], [zonas, zonaId]);

  const { schema, loading: cargandoEsquema, error: errorEsquema } = useRuleEngineSchema();
  const { traza: ultima, loading, error, refrescar } = useTrazaEvaluacion(sectorId, origen, auto);

  // Mientras llega la traza del sector nuevo no se muestra la del anterior.
  const traza = ultima && ultima.sectorId === sectorId ? ultima : null;

  const elegirSector = (id: string) => {
    setSectorId(id);
    setSeleccionada(null);
    onSectorChange?.(id);
  };

  const elegirZona = (id: string) => {
    const primeroDeLaZona = zonas.find((z) => z.id === id)?.sectors[0]?.id;
    if (primeroDeLaZona) elegirSector(primeroDeLaZona);
  };

  const resumen = useMemo(() => (schema && traza ? resumenTraza(schema, traza) : []), [schema, traza]);
  const reglaElegida = traza?.reglas.find((r) => r.ruleId === seleccionada) ?? null;
  const nodoElegido = schema?.nodes.find((n) => n.id === seleccionada);
  const nombreDe = (id: string) => schema?.nodes.find((n) => n.id === id)?.label ?? id;

  return (
    <div className={styles.raiz}>
      <div className={styles.controles}>
        <label className={styles.campo}>
          <span className={styles.campoEtiqueta}>Macro-zona</span>
          <select className={styles.select} aria-label="Macro-zona" value={zonaId} onChange={(e) => elegirZona(e.target.value)}>
            {zonas.map((z) => (
              <option key={z.id} value={z.id}>
                {z.id} · {z.sub || z.name}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.campo}>
          <span className={styles.campoEtiqueta}>Sector</span>
          <select className={styles.select} aria-label="Sector" value={sectorId} onChange={(e) => elegirSector(e.target.value)}>
            {sectoresDeZona.map((s) => (
              <option key={s.id} value={s.id}>
                {s.id}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.campo}>
          <span className={styles.campoEtiqueta}>Origen</span>
          <select
            className={styles.select}
            aria-label="Origen"
            value={origen}
            onChange={(e) => setOrigen(e.target.value as OrigenEvaluacion)}
          >
            <option value="TELEMETRIA">Telemetría (al llegar una lectura)</option>
            <option value="BARRIDO">Barrido (cada 5 min)</option>
          </select>
        </label>
        <button type="button" className={styles.btn} onClick={refrescar}>
          Actualizar
        </button>
        <label className={styles.check}>
          <input type="checkbox" checked={auto} onChange={(e) => setAuto(e.target.checked)} />
          Actualizar cada 5 s
        </label>
      </div>

      {error ? (
        <div className={`${styles.estado} ${styles.estadoError}`}>
          No se pudo obtener la evaluación del sector {sectorId}: {error.message}
        </div>
      ) : loading && !traza ? (
        <div className={styles.estado}>Cargando evaluación…</div>
      ) : !traza ? (
        <div className={styles.estado}>
          Este sector todavía no se evaluó: sin evaluaciones desde el último arranque del backend.
          <br />
          La traza vive en memoria: el barrido la regenera como máximo cada 5 minutos, y la telemetría con cada
          lectura nueva de la zona.
        </div>
      ) : errorEsquema ? (
        <div className={`${styles.estado} ${styles.estadoError}`}>
          No se pudo cargar el esquema del motor: {errorEsquema.message}
        </div>
      ) : cargandoEsquema || !schema ? (
        <div className={styles.estado}>Cargando motor de reglas…</div>
      ) : (
        <>
          <section className={styles.resumen}>
            <div className={styles.resumenTitulo}>
              <h3 className={styles.resumenH}>Qué pasó en la última evaluación</h3>
              <span className={styles.resumenMeta}>
                {etiquetaOrigen(traza.origen)} · {fechaHora(traza.ts)} · parámetros #{traza.parametrosHash}
              </span>
            </div>
            <ul className={styles.lineas}>
              {resumen.map((l, i) => (
                <li key={i} className={styles.linea} data-tipo={l.tipo}>
                  <span className={styles.puntoLinea} />
                  <span>{l.texto}</span>
                </li>
              ))}
            </ul>
          </section>

          <div className={styles.cuerpo}>
            <div className={styles.lienzo}>
              <RuleGraph schema={schema} traza={traza} seleccionada={seleccionada} onSeleccionar={setSeleccionada} />
            </div>
            {reglaElegida && (
              <NodoPanel
                regla={reglaElegida}
                label={nombreDe(reglaElegida.ruleId)}
                tieneParametros={(nodoElegido?.parametros.length ?? 0) > 0}
                nombreDe={nombreDe}
                onEditar={onEditarRegla}
                onCerrar={() => setSeleccionada(null)}
              />
            )}
          </div>
        </>
      )}
    </div>
  );
}
