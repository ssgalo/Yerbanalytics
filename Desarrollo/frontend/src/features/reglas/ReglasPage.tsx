/* ============================================================
   Motor de reglas: qué piezas corren, sus nombres y los valores de cada una (`RuleCatalog`).
   El Inspector vive en su propia ruta (`/reglas/inspector`); el enlace viejo `?tab=inspector`
   redirige allá conservando el sector. `?regla=` despliega y lleva a esa fila.
   ============================================================ */
import { Navigate, useSearchParams } from 'react-router-dom';
import { usePageTitle } from '@/hooks/PageMeta';
import { useAuth } from '@/hooks/AuthContext';
import { useCatalogoReglas } from '@/hooks/useCatalogoReglas';
import { RuleCatalog } from './RuleCatalog';
import styles from './components/RuleCatalog.module.css';

export function ReglasPage() {
  const [params] = useSearchParams();
  const { catalogo, loading, error, saving, save, setEstado, renombrar, eliminar, restaurar } =
    useCatalogoReglas();
  // Sin `reglas.editar` el catálogo se ve en sólo lectura, sin botón para guardar.
  const soloLectura = !useAuth().puede('reglas.editar');

  usePageTitle('Motor de reglas', 'Qué reglas corren y con qué valores');

  if (params.get('tab') === 'inspector') {
    const sector = params.get('sector');
    return (
      <Navigate
        to={`/reglas/inspector${sector ? `?sector=${encodeURIComponent(sector)}` : ''}`}
        replace
      />
    );
  }

  if (error) {
    return (
      <div className={styles.estado} role="alert">
        No se pudieron cargar las reglas: {error.message}
      </div>
    );
  }
  if (loading || !catalogo) return <div className={styles.estado}>Cargando reglas…</div>;

  return (
    <RuleCatalog
      catalogo={catalogo}
      saving={saving}
      onGuardar={save}
      onCambiarEstado={setEstado}
      onRenombrar={renombrar}
      onEliminar={eliminar}
      onRestaurar={restaurar}
      reglaInicial={params.get('regla')}
      soloLectura={soloLectura}
    />
  );
}
