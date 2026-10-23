/* Vista Umbrales (Configuración agronómica, HU-15): las bandas de alerta de las 10 métricas sensadas. Lo que decide el
   motor se edita en Motor de reglas y el horario por defecto de la mediasombra, en Rustificación. En el menú vive
   dentro del grupo Configuración. */
import { useMemo } from 'react';
import { usePageTitle } from '@/hooks/PageMeta';
import { useAuth } from '@/hooks/AuthContext';
import { umbralError } from '@/lib/configValidation';
import { buildConfig } from '@/data/mock/config';
import type { MetricThreshold } from '@/types/domain';
import { UmbralesForm } from './components/UmbralesForm';
import { ConfigFieldset, ConfigLayout, ConfigSection } from './components/ConfigLayout';
import { pendingFixes, useConfigDraft } from './useConfigDraft';

export function ConfiguracionPage() {
  const state = useConfigDraft();
  const { draft, config, update } = state;
  // Sin `configuracion.editar` la vista es de sólo lectura: campos deshabilitados, sin guardar.
  const editable = useAuth().puede('configuracion.editar');

  usePageTitle('Umbrales', 'Bandas de alerta de las métricas sensadas');

  const errors = useMemo(
    () => (draft ? draft.umbrales.map(umbralError).filter((e): e is string => e !== null) : []),
    [draft],
  );

  const patchUmbral = (index: number, patch: Partial<MetricThreshold>) =>
    update((d) => ({ ...d, umbrales: d.umbrales.map((u, i) => (i === index ? { ...u, ...patch } : u)) }));

  // De fábrica, sólo lo de esta vista: las bandas.
  const onReset = () => update((d) => ({ ...d, umbrales: buildConfig().umbrales }));

  return (
    <ConfigLayout
      state={state}
      editable={editable}
      onReset={onReset}
      blockedReason={pendingFixes(errors)}
    >
      {draft && (
        <>
          <ConfigSection
            title="Umbrales de métricas"
            hint="Arrastrá las manijas o escribí los valores. Más allá de cada punto de alerta, la campana avisa; el estado no cambia."
          >
            <ConfigFieldset disabled={!editable}>
              <UmbralesForm value={draft.umbrales} saved={config?.umbrales} disabled={!editable} onChange={patchUmbral} />
            </ConfigFieldset>
          </ConfigSection>
        </>
      )}
    </ConfigLayout>
  );
}
