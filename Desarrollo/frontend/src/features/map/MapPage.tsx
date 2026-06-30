/* ============================================================
   MapPage — vista "Mapa de producción".
   Layout: zona-tabs arriba, grilla grande a la izquierda,
   resumen + sectores a revisar a la derecha (320px fija).
   Zona activa se persiste en el query-param ?zona=MZ-1.
   ============================================================ */
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useNurseryData } from '@/hooks/NurseryContext';
import { usePageTitle } from '@/hooks/PageMeta';
import { ZonaTabs } from './components/ZonaTabs';
import { SectorGrid } from './components/SectorGrid';
import { ZoneSummary } from './components/ZoneSummary';
import { ProblemsList } from './components/ProblemsList';
import styles from './MapPage.module.css';

export function MapPage() {
  /* Metadata de la topbar */
  usePageTitle('Mapa de producción', '600 sectores · seleccioná una macro-zona');

  const { zonas, sevMap, layout } = useNurseryData();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();

  /* Zona activa: viene del query param o cae en la primera */
  const zonaId = params.get('zona') ?? zonas[0]?.id ?? '';
  const mapZona = zonas.find((z) => z.id === zonaId) ?? zonas[0];

  /* Guardar zona en la URL sin pushear una nueva entrada de historial */
  function handleZonaSelect(id: string) {
    setParams({ zona: id });
  }

  /* Navegar al detalle del sector */
  function handleSectorClick(sectorId: string) {
    navigate('/sector/' + sectorId);
  }

  if (!mapZona) return null;

  return (
    <div className={styles.page}>
      {/* Tabs de macro-zonas */}
      <ZonaTabs zonas={zonas} zonaId={zonaId} onSelect={handleZonaSelect} />

      {/* Layout principal: grilla grande + columna derecha */}
      <div className={styles.layout}>
        {/* Grilla de sectores (disposición configurable) */}
        <SectorGrid
          zona={mapZona}
          sectoresPorFila={layout.sectoresPorFila}
          onSectorClick={handleSectorClick}
        />

        {/* Columna derecha: resumen + sectores a revisar */}
        <div className={styles.sidebar}>
          <ZoneSummary zona={mapZona} />
          <ProblemsList
            sectors={mapZona.sectors}
            sevMap={sevMap}
            onSectorClick={handleSectorClick}
          />
        </div>
      </div>
    </div>
  );
}
