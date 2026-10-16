/* Vista Criterio de diagnóstico (Configuración): el criterio agronómico con que el servicio de
   inferencia convierte las detecciones del modelo en un diagnóstico por sector.

   - Bandas de severidad: cuántas hojas de un estado anómalo hacen falta para que cuente y para
     que su severidad sea media o alta.
   - Orden de criticidad: qué estado gana ante un empate de relevancia.

   Los umbrales de confianza por clase NO están acá: los calibra el equipo en el servicio de
   inferencia. Es otro recurso que la configuración agronómica, con su propio borrador. */
import { useMemo } from 'react';
import { usePageTitle } from '@/hooks/PageMeta';
import { useAuth } from '@/hooks/AuthContext';
import { Callout } from '@/components/ui/Callout';
import { criteriaErrors, criticalityError, defaultDiagnosisCriteria, moveCriticality } from '@/lib/diagnosisCriteria';
import type { SeverityBand } from '@/types/domain';
import { ConfigFieldset, ConfigLayout, ConfigSection } from './components/ConfigLayout';
import { SeverityBandsForm } from './components/SeverityBandsForm';
import { CriticalityOrder } from './components/CriticalityOrder';
import { pendingFixes } from './useConfigDraft';
import { useDiagnosisCriteriaDraft } from './useDiagnosisCriteriaDraft';
import styles from './components/DiagnosisCriteria.module.css';

export function DiagnosisCriteriaPage() {
  const state = useDiagnosisCriteriaDraft();
  const { draft, config, update } = state;
  // Sin `configuracion.editar` la vista es de sólo lectura: campos y botones deshabilitados.
  const editable = useAuth().puede('configuracion.editar');

  usePageTitle('Criterio de diagnóstico', 'Severidad por cantidad de hojas y orden de criticidad');

  const errors = useMemo(() => (draft ? criteriaErrors(draft) : []), [draft]);
  const orderError = draft ? criticalityError(draft.criticality) : null;

  const patchBand = (bandState: string, patch: Partial<SeverityBand>) =>
    update((d) => ({
      ...d,
      severityBands: d.severityBands.map((b) => (b.state === bandState ? { ...b, ...patch } : b)),
    }));

  const move = (s: string, delta: -1 | 1) =>
    update((d) => ({ ...d, criticality: moveCriticality(d.criticality, s, delta) }));

  return (
    <ConfigLayout
      state={state}
      resourceName="el criterio de diagnóstico"
      editable={editable}
      onReset={() => update(() => defaultDiagnosisCriteria())}
      blockedReason={pendingFixes(errors)}
      intro="Cómo se convierten las hojas que detecta el modelo en el diagnóstico de cada sector: cuántas hacen falta para que un estado cuente y qué tan grave es, y cuál gana cuando dos pesan lo mismo."
    >
      {draft && (
        <>
          <Callout icon="info">
            Los umbrales y las bandas de confianza de cada clase los calibra el equipo en el servicio de inferencia
            y no se editan desde el dashboard. Acá se define el criterio agronómico.
          </Callout>

          <ConfigSection
            title="Bandas de severidad"
            hint="En cantidad de hojas detectadas por sector. Debajo del mínimo, el estado no cuenta para el diagnóstico."
          >
            <ConfigFieldset disabled={!editable}>
              <SeverityBandsForm value={draft.severityBands} saved={config?.severityBands} onChange={patchBand} />
            </ConfigFieldset>
          </ConfigSection>

          <ConfigSection
            title="Orden de criticidad"
            hint="Desempata dos estados con la misma severidad y confianza: gana el más crítico."
          >
            <ConfigFieldset disabled={!editable}>
              <CriticalityOrder
                value={draft.criticality}
                saved={config?.criticality}
                disabled={!editable}
                onMove={move}
              />
            </ConfigFieldset>
            {orderError && (
              <p className={styles.bandError} role="alert">
                {orderError}
              </p>
            )}
            <p className={styles.orderNote}>Sano no entra en el orden: siempre es el menos crítico.</p>
          </ConfigSection>
        </>
      )}
    </ConfigLayout>
  );
}
