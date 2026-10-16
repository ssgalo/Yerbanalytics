/* Borrador del criterio de diagnóstico: el mismo armazón que la configuración agronómica
   (`useResourceDraft`), sobre su propio recurso. */
import { useDiagnosisCriteria } from '@/hooks/useDiagnosisCriteria';
import { countCriteriaChanges } from '@/lib/diagnosisCriteria';
import type { DiagnosisCriteria } from '@/types/domain';
import { useResourceDraft, type ConfigDraft } from './useConfigDraft';

export function useDiagnosisCriteriaDraft(): ConfigDraft<DiagnosisCriteria> {
  const { criteria, loading, error, saving, save } = useDiagnosisCriteria();
  return useResourceDraft(
    { data: criteria, loading, error, saving, save },
    countCriteriaChanges,
    'Criterio de diagnóstico guardado. El servicio de inferencia lo usa desde su próximo ciclo.',
  );
}
