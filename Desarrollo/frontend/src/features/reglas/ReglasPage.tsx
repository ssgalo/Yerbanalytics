/* ============================================================
   Sección "Motor de reglas": dos vistas sobre el mismo motor.
   - Reglas: cada pieza con su interruptor y sus umbrales, agrupada por objetivo.
   - Inspector: el DAG del motor coloreado con la última evaluación de un sector.

   La pestaña vive en la URL (`?tab=`), así que se puede enlazar: el detalle de sector manda
   a `?tab=inspector&sector=…` y el Inspector a `?regla=…` para ir a la tarjeta de la regla.
   (`?tab=parametros`, el nombre de la pestaña anterior, abre Reglas.)
   ============================================================ */
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { contar } from '@/lib/plural';
import { usePageTitle } from '@/hooks/PageMeta';
import { useAuth } from '@/hooks/AuthContext';
import { useCatalogoReglas } from '@/hooks/useCatalogoReglas';
import { cambiosDelBorrador, type Borrador } from './borrador';
import { ReglasTab } from './ReglasTab';
import { InspectorTab } from './inspector/InspectorTab';
import styles from './Reglas.module.css';

type Pestania = 'reglas' | 'inspector';

export function ReglasPage() {
  const [params, setParams] = useSearchParams();
  const tab: Pestania = params.get('tab') === 'inspector' ? 'inspector' : 'reglas';
  const { catalogo, loading, error, saving, save, setEstado } = useCatalogoReglas();
  // Sin `reglas.editar` el catálogo se ve en sólo lectura, sin botón para guardar.
  const soloLectura = !useAuth().puede('reglas.editar');

  // El borrador de parámetros vive acá, no en la pestaña: ésta se desmonta al ir al Inspector.
  const [borrador, setBorrador] = useState<Borrador>(new Map());
  const pendientes = useMemo(
    () => (catalogo ? cambiosDelBorrador(catalogo, borrador).length : 0),
    [catalogo, borrador],
  );

  usePageTitle('Motor de reglas', 'Qué reglas corren, sus umbrales y la última evaluación por sector');

  /** "Editar parámetro" desde el Inspector: a Reglas, en la tarjeta de esa regla. */
  const editarRegla = (ruleId: string) => setParams({ regla: ruleId });

  const cambiarSector = (id: string) => {
    const next = new URLSearchParams(params);
    next.set('sector', id);
    setParams(next, { replace: true });
  };

  const elegir = (t: Pestania) => {
    const next = new URLSearchParams(params);
    if (t === 'reglas') next.delete('tab');
    else next.set('tab', t);
    setParams(next, { replace: true });
  };

  return (
    <div className={styles.page}>
      <div className={styles.tabs} role="tablist" aria-label="Vistas del motor de reglas">
        <button
          type="button"
          role="tab"
          aria-selected={tab === 'reglas'}
          className={tab === 'reglas' ? `${styles.tab} ${styles.tabActive}` : styles.tab}
          onClick={() => elegir('reglas')}
        >
          Reglas
          {pendientes > 0 && (
            <span className={styles.tabBadge} title={`${contar(pendientes, 'cambio', 'cambios')} sin guardar`}>
              {pendientes}
            </span>
          )}
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

      {tab === 'reglas' && (
        <>
          <p className={styles.intro}>
            Acá ves qué reglas corren y cuánto vale cada umbral
            {soloLectura ? '' : '; podés prender o apagar una regla y cambiar sus umbrales'}. Un umbral que usan varias
            reglas es un solo valor, y se edita una vez en «Compartidos» de su grupo.
          </p>
          {error ? (
            <div className={styles.state} style={{ color: 'var(--crit-ink)' }}>
              No se pudieron cargar las reglas: {error.message}
            </div>
          ) : loading || !catalogo ? (
            <div className={styles.state}>Cargando reglas…</div>
          ) : (
            <ReglasTab
              catalogo={catalogo}
              saving={saving}
              onGuardar={save}
              onCambiarEstado={setEstado}
              borrador={borrador}
              onBorradorChange={setBorrador}
              reglaInicial={params.get('regla')}
              soloLectura={soloLectura}
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
