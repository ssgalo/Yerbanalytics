/* Vista Preferencias: pulso de sensado y evaluación, y pasada diaria del riel. */
import { useMemo } from 'react';
import { usePageTitle } from '@/hooks/PageMeta';
import { useAuth } from '@/hooks/AuthContext';
import { operativaErrors } from '@/lib/configValidation';
import { buildConfig } from '@/data/mock/config';
import type { ConfigOperativa } from '@/types/domain';
import { IntervalosForm } from './components/IntervalosForm';
import { DailyPassForm } from './components/DailyPassForm';
import { ConfigFieldset, ConfigLayout, ConfigSection } from './components/ConfigLayout';
import { configAudit, pendingFixes, useConfigDraft } from './useConfigDraft';

export function PreferencesPage() {
  const state = useConfigDraft();
  const { draft, config, update } = state;
  const { puede } = useAuth();
  const editable = puede('configuracion.editar');

  usePageTitle('Preferencias', 'Frecuencias de operación del sistema');

  const errors = useMemo(() => (draft ? operativaErrors(draft.operativa) : []), [draft]);

  const patchOperativa = (patch: Partial<ConfigOperativa>) =>
    update((d) => ({ ...d, operativa: { ...d.operativa, ...patch } }));

  // De fábrica, sólo lo que edita esta vista. El umbral de lectura vieja lo calcula el backend al guardar.
  const onReset = () => {
    const f = buildConfig().operativa;
    patchOperativa({
      intervaloSensadoMinutos: f.intervaloSensadoMinutos,
      pasadaDiariaActiva: f.pasadaDiariaActiva,
      pasadaDiariaHora: f.pasadaDiariaHora,
    });
  };

  return (
    <ConfigLayout
      state={state}
      audit={configAudit}
      editable={editable}
      onReset={onReset}
      blockedReason={pendingFixes(errors)}
      intro="Cada cuánto el sistema lee los nodos y evalúa los sectores, y cuándo pasa el riel."
    >
      {draft && (
        <>
          <ConfigSection title="Frecuencia operativa" hint="Pulso de sensado (IoT) y evaluación del motor">
            <ConfigFieldset disabled={!editable}>
              <IntervalosForm value={draft.operativa} saved={config?.operativa} onChange={patchOperativa} />
            </ConfigFieldset>
          </ConfigSection>

          <ConfigSection title="Pasada diaria del riel" hint="Fotos cenitales para el diagnóstico de IA">
            <ConfigFieldset disabled={!editable}>
              <DailyPassForm value={draft.operativa} saved={config?.operativa} onChange={patchOperativa} />
            </ConfigFieldset>
          </ConfigSection>
        </>
      )}
    </ConfigLayout>
  );
}
