/* Vista principal: Diagnósticos de IA */
import { useState } from 'react';
import { useNurseryData } from '@/hooks/NurseryContext';
import { usePageTitle } from '@/hooks/PageMeta';
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

  /* Lista filtrada */
  const diagList = diagnoses.filter(
    (d) =>
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
        onEstado={setDiagEstado}
        onSev={setDiagSev}
      />

      {/* Grid de cards */}
      <div className={styles.grid}>
        {diagList.map((d) => (
          <DiagCard key={d.id} d={d} onClick={setPhotoId} />
        ))}
      </div>

      {/* Modal de foto cenital */}
      {photo && <PhotoModal photo={photo} onClose={() => setPhotoId(null)} />}
    </div>
  );
}
