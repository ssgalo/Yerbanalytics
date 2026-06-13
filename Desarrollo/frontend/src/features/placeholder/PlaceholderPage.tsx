import { useNavigate } from 'react-router-dom';
import { usePageTitle } from '@/hooks/PageMeta';
import { Icon } from '@/components/ui/Icon';

interface PlaceholderPageProps {
  title: string;
}

/** Módulos del producto no incluidos en esta demo (Historial / Config / Hardware). */
export function PlaceholderPage({ title }: PlaceholderPageProps) {
  const navigate = useNavigate();
  usePageTitle(title, 'Módulo del producto Yerbanalytics');

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '60vh',
        textAlign: 'center',
        animation: 'ybFade .4s both',
      }}
    >
      <div
        style={{
          width: 74,
          height: 74,
          borderRadius: 20,
          background: 'var(--card)',
          border: '1px solid var(--line)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--brand)',
        }}
      >
        <Icon name="cube" size={34} strokeWidth={1.6} />
      </div>
      <h2
        style={{
          margin: '20px 0 6px',
          fontFamily: 'var(--font-display)',
          fontSize: 22,
          fontWeight: 600,
        }}
      >
        {title}
      </h2>
      <p style={{ margin: 0, maxWidth: 420, color: 'var(--muted)', fontSize: 14, lineHeight: 1.5 }}>
        Este módulo forma parte del producto Yerbanalytics pero no está incluido en esta demo
        interactiva. Las pantallas activas son Panel general, Mapa de producción, Detalle de sector y
        Diagnósticos de IA.
      </p>
      <button
        onClick={() => navigate('/')}
        style={{
          marginTop: 22,
          background: 'var(--brand)',
          color: '#fff',
          border: 'none',
          borderRadius: 11,
          padding: '11px 20px',
          fontWeight: 700,
          fontSize: 13.5,
          cursor: 'pointer',
          fontFamily: 'var(--font-body)',
        }}
      >
        Volver al panel
      </button>
    </div>
  );
}
