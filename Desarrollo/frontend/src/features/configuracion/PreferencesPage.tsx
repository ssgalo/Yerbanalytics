/* Vista Preferencias: pulso de sensado y evaluación, pasada diaria del riel y visualización (Demo Expo). */
import { useMemo } from 'react';
import { usePageTitle } from '@/hooks/PageMeta';
import { useAuth } from '@/hooks/AuthContext';
import { operativaErrors } from '@/lib/configValidation';
import { buildConfig } from '@/data/mock/config';
import type { ConfigOperativa } from '@/types/domain';
import { IntervalosForm } from './components/IntervalosForm';
import { DailyPassForm } from './components/DailyPassForm';
import { DemoExpoSwitch } from './components/DemoExpoSwitch';
import { ConfigFieldset, ConfigLayout, ConfigSection } from './components/ConfigLayout';
import { pendingFixes, useConfigDraft } from './useConfigDraft';

export function PreferencesPage() {
  const state = useConfigDraft();
  const { draft, config, update } = state;
  const { puede } = useAuth();
  const editable = puede('configuracion.editar');

  usePageTitle('Preferencias', 'Frecuencias de operación y visualización del dashboard');

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
      editable={editable}
      onReset={onReset}
      blockedReason={pendingFixes(errors)}
      intro="Cada cuánto el sistema lee los nodos y evalúa los sectores, cuándo pasa el riel y qué muestra el dashboard."
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

          <ConfigSection title="Demo Expo" hint="Preferencia de visualización · se guarda al instante">
            {/* No pasa por el borrador: depende de otro permiso y se aplica en el momento. */}
            <DemoExpoSwitch editable={puede('demo-expo.configurar')} />
          </ConfigSection>
        </>
      )}
    </ConfigLayout>
  );
}
