/* ============================================================
   Sección "Motor de reglas": dos vistas sobre el mismo motor.
   - Parámetros: los umbrales agrupados por regla, con edición.
   - Inspector: el DAG del motor coloreado con la última evaluación de un sector.

   La pestaña vive en la URL (`?tab=`), así que se puede enlazar: el detalle de sector manda
   a `?tab=inspector&sector=…` y el Inspector a `?regla=…` para editar un umbral.
   ============================================================ */
import { useSearchParams } from 'react-router-dom';
import { usePageTitle } from '@/hooks/PageMeta';
import { useCatalogoReglas } from '@/hooks/useCatalogoReglas';
import { ParametrosTab } from './ParametrosTab';
import { InspectorTab } from './inspector/InspectorTab';
import styles from './Reglas.module.css';

type Pestania = 'parametros' | 'inspector';

export function ReglasPage() {
  const [params, setParams] = useSearchParams();
  const tab: Pestania = params.get('tab') === 'inspector' ? 'inspector' : 'parametros';
  const { catalogo, loading, error, saving, save } = useCatalogoReglas();

  usePageTitle('Motor de reglas', 'Umbrales de cada regla y última evaluación por sector');

  /** "Editar parámetro" desde el Inspector: a Parámetros, con esa regla abierta. */
  const editarRegla = (ruleId: string) => setParams({ regla: ruleId });

  const cambiarSector = (id: string) => {
    const next = new URLSearchParams(params);
    next.set('sector', id);
    setParams(next, { replace: true });
  };

  const elegir = (t: Pestania) => {
    const next = new URLSearchParams(params);
    if (t === 'parametros') next.delete('tab');
    else next.set('tab', t);
    setParams(next, { replace: true });
  };

  return (
    <div className={styles.page}>
      <div className={styles.tabs} role="tablist" aria-label="Vistas del motor de reglas">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'parametros'}
          className={tab === 'parametros' ? `${styles.tab} ${styles.tabActive}` : styles.tab}
          onClick={() => elegir('parametros')}
        >
          Parámetros
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'inspector'}
          className={tab === 'inspector' ? `${styles.tab} ${styles.tabActive}` : styles.tab}
          onClick={() => elegir('inspector')}
        >
          Inspector
        </button>
      </div>

      {tab === 'parametros' && (
        <>
          <p className={styles.intro}>
            Cada regla del motor decide comparando lo que recibe contra uno o más umbrales. Acá ves cuánto
            vale cada uno y podés cambiarlo. Un umbral que usan varias reglas es un solo valor: editarlo en
            una lo cambia en todas.
          </p>
          {error ? (
            <div className={styles.state} style={{ color: 'var(--crit)' }}>
              No se pudieron cargar los parámetros: {error.message}
            </div>
          ) : loading || !catalogo ? (
            <div className={styles.state}>Cargando parámetros…</div>
          ) : (
            <ParametrosTab
              catalogo={catalogo}
              saving={saving}
              onGuardar={save}
              reglaInicial={params.get('regla')}
            />
          )}
        </>
      )}

      {tab === 'inspector' && (
        <InspectorTab
          sectorInicial={params.get('sector')}
          onEditarRegla={editarRegla}
          onSectorChange={cambiarSector}
        />
      )}
    </div>
  );
}
