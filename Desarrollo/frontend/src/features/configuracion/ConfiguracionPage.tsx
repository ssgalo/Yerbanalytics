/* Vista Umbrales (Configuración agronómica, HU-15): bandas de las métricas, límites de insumos,
   seguimiento post-acción y horario por defecto de la mediasombra. En el menú vive dentro del grupo Configuración. */
import { useMemo } from 'react';
import { usePageTitle } from '@/hooks/PageMeta';
import { useAuth } from '@/hooks/AuthContext';
import { operativaErrors, umbralError } from '@/lib/configValidation';
import { buildConfig } from '@/data/mock/config';
import type { ConfigOperativa, MetricThreshold } from '@/types/domain';
import { UmbralesForm } from './components/UmbralesForm';
import { LimitesActuadoresForm } from './components/LimitesActuadoresForm';
import { SeguimientoForm } from './components/SeguimientoForm';
import { ShadeScheduleForm } from './components/ShadeScheduleForm';
import { ConfigFieldset, ConfigLayout, ConfigRow, ConfigSection } from './components/ConfigLayout';
import { pendingFixes, useConfigDraft } from './useConfigDraft';

export function ConfiguracionPage() {
  const state = useConfigDraft();
  const { draft, config, update } = state;
  // Sin `configuracion.editar` la vista es de sólo lectura: campos deshabilitados, sin guardar.
  const editable = useAuth().puede('configuracion.editar');

  usePageTitle('Umbrales', 'Bandas de las métricas, insumos, seguimiento y horario de la mediasombra');

  const errors = useMemo(
    () =>
      draft
        ? [...draft.umbrales.map(umbralError).filter((e): e is string => e !== null), ...operativaErrors(draft.operativa)]
        : [],
    [draft],
  );

  const patchUmbral = (index: number, patch: Partial<MetricThreshold>) =>
    update((d) => ({ ...d, umbrales: d.umbrales.map((u, i) => (i === index ? { ...u, ...patch } : u)) }));

  const patchOperativa = (patch: Partial<ConfigOperativa>) =>
    update((d) => ({ ...d, operativa: { ...d.operativa, ...patch } }));

  // De fábrica, sólo lo de esta vista: bandas, dosis de insumo, seguimiento y horario de la mediasombra.
  const onReset = () => {
    const f = buildConfig();
    update((d) => ({
      ...d,
      umbrales: f.umbrales,
      operativa: {
        ...d.operativa,
        insumoDosisMax24hMl: f.operativa.insumoDosisMax24hMl,
        seguimientoLatenciaMin: f.operativa.seguimientoLatenciaMin,
        seguimientoDeltaMin: f.operativa.seguimientoDeltaMin,
        mediasombraAbiertaDesde: f.operativa.mediasombraAbiertaDesde,
        mediasombraAbiertaHasta: f.operativa.mediasombraAbiertaHasta,
      },
    }));
  };

  return (
    <ConfigLayout
      state={state}
      editable={editable}
      onReset={onReset}
      blockedReason={pendingFixes(errors)}
      intro="Calibrá las decisiones automáticas del sistema a la realidad del vivero. Arranca con valores de fábrica seguros para la yerba mate, y cada cambio se valida contra el rango fisiológico antes de aplicarse."
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

          <ConfigRow>
            <ConfigSection title="Límites de insumos" hint="Topes informativos de dosis">
              <ConfigFieldset disabled={!editable}>
                <LimitesActuadoresForm value={draft.operativa} saved={config?.operativa} onChange={patchOperativa} />
              </ConfigFieldset>
            </ConfigSection>

            <ConfigSection title="Seguimiento post-acción" hint="Cuándo y cuánto debe mejorar una métrica después de actuar">
              <ConfigFieldset disabled={!editable}>
                <SeguimientoForm value={draft.operativa} saved={config?.operativa} onChange={patchOperativa} />
              </ConfigFieldset>
            </ConfigSection>

            <ConfigSection title="Mediasombra" hint="Horario de apertura por defecto, sin plan de rustificación">
              <ConfigFieldset disabled={!editable}>
                <ShadeScheduleForm value={draft.operativa} saved={config?.operativa} onChange={patchOperativa} />
              </ConfigFieldset>
            </ConfigSection>
          </ConfigRow>
        </>
      )}
    </ConfigLayout>
  );
}
