/* ============================================================
   MapPage — detalle de una macro-zona.

   Se llega SIEMPRE eligiendo una macro-zona en el Panel general: sin
   `?zona=` no hay contexto que mostrar y se vuelve ahí. Por eso adentro
   no hay selector de zonas, sólo "Volver".

   Tarjetas: sectores, valores sensados, rustificación, tanques de insumo y bloqueo manual. La forma
   de la grilla decide la disposición: una macro-zona más ancha que alta
   apila las tarjetas a todo el ancho; una más alta que ancha deja los
   sectores a la izquierda, el sensado a la derecha y la rustificación
   abajo.
   ============================================================ */
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useNurseryData } from '@/hooks/NurseryContext';
import { usePageTitle } from '@/hooks/PageMeta';
import { Icon } from '@/components/ui/Icon';
import { cx } from '@/components/ui/estado';
import { SectorsCard } from './components/SectorsCard';
import { SensingCard } from './components/SensingCard';
import { HardeningCard } from './components/HardeningCard';
import { ManualLockCard } from '@/features/manual-lock/ManualLockCard';
import { TankCard } from '@/features/supply/TankCard';
import styles from './MapPage.module.css';

export function MapPage() {
  const { zonas, layout } = useNurseryData();
  const [params] = useSearchParams();
  const navigate = useNavigate();

  const zona = zonas.find((z) => z.id === params.get('zona'));

  usePageTitle(zona ? zona.name : 'Macro-zona', zona ? `${zona.sub} · ${zona.total} sectores` : '');

  /* Sin zona en la URL —o con una que no existe— no hay nada que mostrar: al Panel
     general, que es de donde se elige. */
  if (!zona) return <Navigate to="/" replace />;

  const columns = Math.max(1, Math.min(layout.sectoresPorFila, zona.sectors.length));
  const rows = Math.ceil(zona.sectors.length / columns);
  /* Sólo una grilla estrictamente más ancha que alta se apila; una cuadrada va al lado del sensado. */
  const wide = columns > rows;

  return (
    <div className={styles.page}>
      {/* Rastro de navegación. Hoy sólo la vuelta al Panel general —única puerta de
          entrada a esta vista—; queda como el lugar donde irán los breadcrumbs. */}
      <nav className={styles.crumbs} aria-label="Navegación">
        <button type="button" className={styles.backBtn} onClick={() => navigate('/')}>
          <Icon name="chevron-left" size={15} />
          Panel general
        </button>
      </nav>

      <div className={cx(styles.layout, wide ? styles.wide : styles.tall)}>
        <SectorsCard
          className={styles.sectors}
          zona={zona}
          columns={columns}
          wide={wide}
          onSectorClick={(id) => navigate('/sector/' + id)}
        />
        <SensingCard className={styles.sensing} zona={zona} wide={wide} />
        <HardeningCard className={styles.hardening} zona={zona} />
        <TankCard className={styles.tanks} zoneId={zona.id} />
        <ManualLockCard className={styles.lock} zonaId={zona.id} label={`la macro-zona ${zona.id}`} />
      </div>
    </div>
  );
}
