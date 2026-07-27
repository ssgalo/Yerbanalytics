/* ============================================================
   MapPage — detalle de una macro-zona.

   Se llega SIEMPRE eligiendo una macro-zona en el Panel general: sin
   `?zona=` no hay contexto que mostrar y se vuelve ahí. Por eso adentro
   no hay selector de zonas, sólo "Volver".

   Layout a pantalla completa, sin scroll de página: la grilla ocupa
   sólo lo que necesita, y el resto del ancho es para el panel de
   sensado. Al elegir una métrica, su histórico entra como inspector
   deslizándose sobre la grilla.
   ============================================================ */
import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Navigate, useSearchParams, useNavigate } from 'react-router-dom';
import { useNurseryData } from '@/hooks/NurseryContext';
import { usePageTitle } from '@/hooks/PageMeta';
import { selectSensadoTiles, selectSerieMetrica } from '@/data';
import { Icon } from '@/components/ui/Icon';
import type { Range } from '@/types/domain';
import { anchoDeCaja, calcularCelda } from './gridLayout';
import { SectorGrid } from './components/SectorGrid';
import { ProblemsList } from './components/ProblemsList';
import { SensadoCard } from './components/SensadoCard';
import { SensadoChart } from './components/SensadoChart';
import styles from './MapPage.module.css';

/** Métrica que muestra el inspector la primera vez que se abre. */
const METRICA_INICIAL = 'humSus';

/* Presupuesto de ancho del layout, para acotar cuánto puede crecer la grilla.
   Deben coincidir con MapPage.module.css. */
const SENSADO_MIN = 430;
const PROBLEMAS = 300;
const GAP = 14;

export function MapPage() {
  const { zonas, sevMap, layout } = useNurseryData();
  const [params] = useSearchParams();
  const navigate = useNavigate();

  /* Métrica abierta en el inspector (null = cerrado) y rango del histórico. */
  const [metricKey, setMetricKey] = useState<string | null>(null);
  /* Última métrica mostrada: mantiene el contenido durante la animación de cierre,
     que si no se vaciaría de golpe mientras el panel todavía se desliza. */
  const [ultimaKey, setUltimaKey] = useState(METRICA_INICIAL);
  const [range, setRange] = useState<Range>('7d');

  /* Medida del área de trabajo: de acá salen el lado de celda y el ancho de la
     columna del mapa, que se reparte con la de sensado. */
  const layoutRef = useRef<HTMLDivElement>(null);
  const [caja, setCaja] = useState({ ancho: 0, alto: 0 });

  useLayoutEffect(() => {
    const el = layoutRef.current;
    if (!el) return;
    const medir = () => {
      const r = el.getBoundingClientRect();
      setCaja({ ancho: r.width, alto: r.height });
    };
    medir();
    const ro = new ResizeObserver(medir);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const zonaId = params.get('zona');
  const mapZona = zonas.find((z) => z.id === zonaId);

  usePageTitle(
    mapZona ? mapZona.name : 'Macro-zona',
    mapZona ? `${mapZona.sub} · ${mapZona.total} sectores` : '',
  );

  const tiles = useMemo(() => (mapZona ? selectSensadoTiles(mapZona) : []), [mapZona]);
  /* El histórico se calcula sobre la última métrica mostrada, no sobre la
     seleccionada: así el inspector conserva su contenido mientras se cierra. */
  const serie = useMemo(
    () => (mapZona ? selectSerieMetrica(mapZona, ultimaKey, range) : null),
    [mapZona, ultimaKey, range],
  );
  const tileMostrado = tiles.find((t) => t.key === ultimaKey);
  const abierto = metricKey !== null;

  /* Lado de celda y ancho de la columna del mapa. Lo que la grilla no usa queda
     para el panel de sensado. */
  const cols = Math.max(1, layout.sectoresPorFila);
  const rows = Math.max(1, Math.ceil((mapZona?.sectors.length ?? 0) / cols));
  const anchoMaximoGrilla = Math.max(160, caja.ancho - SENSADO_MIN - PROBLEMAS - GAP * 2);
  const cell = caja.alto > 0 ? calcularCelda(cols, rows, caja.alto, anchoMaximoGrilla) : 0;
  const anchoMapa = cell > 0 ? anchoDeCaja(cols, cell) : 0;

  /* Sin zona en la URL —o con una que no existe— no hay nada que mostrar: al Panel
     general, que es de donde se elige. */
  if (!mapZona) return <Navigate to="/" replace />;

  /* Clic en la métrica ya abierta = cerrar el inspector. */
  function handleMetric(key: string) {
    setMetricKey((prev) => (prev === key ? null : key));
    setUltimaKey(key);
  }

  function handleSectorClick(sectorId: string) {
    navigate('/sector/' + sectorId);
  }

  return (
    <div className={styles.page}>
      {/* Rastro de navegación. Hoy sólo la vuelta al Panel general —única puerta de
          entrada a esta vista—; queda como el lugar donde irán los breadcrumbs. */}
      <nav className={styles.crumbs} aria-label="Navegación">
        <button className={styles.backBtn} onClick={() => navigate('/')}>
          <Icon name="chevron-left" size={15} />
          Panel general
        </button>
      </nav>

      {/* Tres columnas: mapa (justo lo que ocupa la grilla) · sensado · a revisar */}
      <div
        className={styles.layout}
        ref={layoutRef}
        style={{ gridTemplateColumns: `${anchoMapa}px minmax(0, 1fr) ${PROBLEMAS}px` }}
      >
        <div className={styles.mapCol}>
          <SectorGrid
            zona={mapZona}
            sectoresPorFila={layout.sectoresPorFila}
            cell={cell}
            onSectorClick={handleSectorClick}
          />

          {/* Entra desde el borde derecho de esta columna —el borde izquierdo del panel
              de sensado— así que tapa la grilla pero nunca los botones de las métricas. */}
          <div
            className={abierto ? `${styles.inspector} ${styles.inspectorOpen}` : styles.inspector}
            aria-hidden={!abierto}
          >
            {tileMostrado && (
              <SensadoChart
                tile={tileMostrado}
                serie={serie}
                range={range}
                onRangeChange={setRange}
                onClose={() => setMetricKey(null)}
              />
            )}
          </div>
        </div>

        <SensadoCard
          zonaName={mapZona.name}
          lectura={mapZona.lectura}
          nodo={mapZona.nodo}
          tiles={tiles}
          selected={metricKey}
          onSelect={handleMetric}
        />

        <ProblemsList
          sectors={mapZona.sectors}
          sevMap={sevMap}
          onSectorClick={handleSectorClick}
        />
      </div>
    </div>
  );
}
