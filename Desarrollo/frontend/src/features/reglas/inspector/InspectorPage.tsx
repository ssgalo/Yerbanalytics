/* Inspector del motor en su propia ruta: el sector va en `?sector=` y "Editar parámetro" lleva a la fila de la regla. */
import { useNavigate, useSearchParams } from 'react-router-dom';
import { usePageTitle } from '@/hooks/PageMeta';
import { InspectorTab } from './InspectorTab';

export function InspectorPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();

  usePageTitle('Inspector del motor', 'La última evaluación del motor de reglas en un sector');

  const cambiarSector = (id: string) => {
    const next = new URLSearchParams(params);
    next.set('sector', id);
    setParams(next, { replace: true });
  };

  return (
    <InspectorTab
      sectorInicial={params.get('sector')}
      onEditarRegla={(ruleId) => navigate(`/reglas?regla=${encodeURIComponent(ruleId)}`)}
      onSectorChange={cambiarSector}
    />
  );
}
