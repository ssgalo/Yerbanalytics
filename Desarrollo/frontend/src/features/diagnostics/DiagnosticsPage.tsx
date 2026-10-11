/* Vista principal: Diagnósticos de IA */
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useNurseryData } from '@/hooks/NurseryContext';
import { usePageTitle } from '@/hooks/PageMeta';
import { EmptyState } from '@/components/ui/EstadosPantalla';
import { DiagFilters } from './components/DiagFilters';
import { DiagCard } from './components/DiagCard';
import { PhotoModal } from './components/PhotoModal';
import styles from './DiagnosticsPage.module.css';

export function DiagnosticsPage() {
  /* Dataset del vivero */
  const { diagnoses, diagById } = useNurseryData();

  /* Estado local de filtros y foto seleccionada */
  const [diagEstado, setDiagEstado] = useState('Todas');
  const [diagSev, setDiagSev] = useState('Todas');
  const [photoId, setPhotoId] = useState<string | null>(null);

  /* Filtro por sector: llega en la URL desde "Ver anteriores" del detalle de sector */
  const [params, setParams] = useSearchParams();
  const sector = params.get('sector');
  const quitarSector = () =>
    setParams(
      (p) => {
        p.delete('sector');
        return p;
      },
      { replace: true },
    );

  /* Lista filtrada */
  const diagList = diagnoses.filter(
    (d) =>
      (!sector || d.sectorId === sector) &&
      (diagEstado === 'Todas' || d.estado === diagEstado) &&
      (diagSev === 'Todas' || d.sev === diagSev),
  );

  /* Título de la página */
  usePageTitle('Diagnósticos de IA', `${diagnoses.length} diagnósticos registrados`);

  /* Foto seleccionada para el modal */
  const photo = photoId ? diagById[photoId] : null;

  return (
    <div>
      {/* Filtros */}
      <DiagFilters
        diagEstado={diagEstado}
        diagSev={diagSev}
        diagCount={diagList.length}
        sector={sector}
        onQuitarSector={quitarSector}
        onEstado={setDiagEstado}
        onSev={setDiagSev}
      />

      {/* Grid de cards */}
      {diagList.length > 0 ? (
        <div className={styles.grid}>
          {diagList.map((d) => (
            <DiagCard key={d.id} d={d} onClick={setPhotoId} />
          ))}
        </div>
      ) : (
        <EmptyState title="Sin diagnósticos">Ningún diagnóstico coincide con los filtros.</EmptyState>
      )}

      {/* Modal de foto cenital */}
      {photo && <PhotoModal photo={photo} onClose={() => setPhotoId(null)} />}
    </div>
  );
}
