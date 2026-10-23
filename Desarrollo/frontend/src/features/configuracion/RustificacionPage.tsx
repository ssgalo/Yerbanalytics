/* Vista Rustificación (Configuración): el horario por defecto de la mediasombra y un resumen del plan de cada
   macro-zona. El horario es del mismo recurso que Umbrales y Preferencias; el alta y la baja de un plan siguen en el
   detalle de la zona, al que cada fila enlaza. */
import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Chip } from '@/components/ui/Chip';
import { Skeleton } from '@/components/ui/EstadosPantalla';
import { linkClass } from '@/components/ui/clases';
import { useAuth } from '@/hooks/AuthContext';
import { useNurseryData } from '@/hooks/NurseryContext';
import { usePageTitle } from '@/hooks/PageMeta';
import { buildConfig } from '@/data/mock/config';
import { useHardeningPlan } from '@/features/map/useHardeningPlan';
import { horarioError, formatDate } from '@/lib/horario';
import { planVigente, progreso } from '@/lib/planRustificacion';
import type { ConfigOperativa, Zona } from '@/types/domain';
import { ConfigFieldset, ConfigLayout, ConfigSection } from './components/ConfigLayout';
import { ShadeScheduleForm } from './components/ShadeScheduleForm';
import { pendingFixes, useConfigDraft } from './useConfigDraft';
import styles from './Rustificacion.module.css';

/** Una macro-zona con su plan vigente (o sin plan) y el enlace a su detalle. */
function ZonePlanRow({ zona }: { zona: Zona }) {
  const { plan, loading, error } = useHardeningPlan(zona.id);
  const vigente = planVigente(plan) ? plan : null;
  const pr = vigente ? progreso(vigente) : null;

  return (
    <li className={styles.row}>
      <Link className={linkClass} to={`/mapa?zona=${zona.id}`}>
        {zona.name} ({zona.id})
      </Link>
      <span className={styles.rowPlan}>
        {loading ? (
          <Skeleton width={160} height={14} />
        ) : error ? (
          <span>No se pudo leer el plan.</span>
        ) : vigente && pr ? (
          pr.dia === 0 ? (
            <>
              <Chip tone="line">Programado</Chip> Empieza el {formatDate(vigente.fechaInicio)}
            </>
          ) : (
            <>
              <Chip tone="info">En curso</Chip>{' '}
              {pr.terminado
                ? `Terminaron las ${vigente.etapas.length} etapas: se sostiene la última`
                : `Etapa ${pr.etapa?.orden ?? 1} de ${vigente.etapas.length} · día ${pr.dia} de ${pr.totalDias}`}
            </>
          )
        ) : (
          <>
            <Chip tone="line">Sin plan</Chip> Sin plan: rige el horario por defecto
          </>
        )}
      </span>
    </li>
  );
}

export function RustificacionPage() {
  const state = useConfigDraft();
  const { draft, config, update } = state;
  const { zonas } = useNurseryData();
  // Sin `configuracion.editar` la vista es de sólo lectura: campos deshabilitados, sin guardar.
  const editable = useAuth().puede('configuracion.editar');

  usePageTitle('Rustificación', 'Horario por defecto de la mediasombra y plan de cada macro-zona');

  const errors = useMemo(() => {
    if (!draft) return [];
    const e = horarioError(
      draft.operativa.mediasombraAbiertaDesde,
      draft.operativa.mediasombraAbiertaHasta,
    );
    return e ? [`Horario de la mediasombra: ${e}`] : [];
  }, [draft]);

  const patchOperativa = (patch: Partial<ConfigOperativa>) =>
    update((d) => ({ ...d, operativa: { ...d.operativa, ...patch } }));

  // De fábrica, sólo el horario por defecto.
  const onReset = () => {
    const f = buildConfig().operativa;
    patchOperativa({
      mediasombraAbiertaDesde: f.mediasombraAbiertaDesde,
      mediasombraAbiertaHasta: f.mediasombraAbiertaHasta,
    });
  };

  return (
    <ConfigLayout
      state={state}
      editable={editable}
      onReset={onReset}
      blockedReason={pendingFixes(errors)}
      intro="Con un plan en curso rige el plan; sin plan, el horario por defecto."
    >
      {draft && (
        <>
          <ConfigSection
            title="Horario por defecto"
            hint="Cuándo está abierta la mediasombra en las macro-zonas sin plan"
          >
            <ConfigFieldset disabled={!editable}>
              <ShadeScheduleForm
                value={draft.operativa}
                saved={config?.operativa}
                onChange={patchOperativa}
              />
            </ConfigFieldset>
          </ConfigSection>

          <ConfigSection
            title="Plan de cada macro-zona"
            hint="El plan se inicia y se elimina en el detalle de la zona"
          >
            <ul className={styles.list} aria-label="Plan de cada macro-zona">
              {zonas.map((z) => (
                <ZonePlanRow key={z.id} zona={z} />
              ))}
            </ul>
          </ConfigSection>
        </>
      )}
    </ConfigLayout>
  );
}
