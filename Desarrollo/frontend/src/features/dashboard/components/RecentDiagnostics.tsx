import { useNavigate } from 'react-router-dom';
import type { DiagnosisCard } from '@/types/domain';
import { Badge } from '@/components/ui/Badge';
import styles from './RecentDiagnostics.module.css';

interface RecentDiagnosticsProps {
  recentDiag: DiagnosisCard[];
}

/** Lista de diagnósticos recientes de IA con thumbnail y severidad. */
export function RecentDiagnostics({ recentDiag }: RecentDiagnosticsProps) {
  const navigate = useNavigate();

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <h2 className={styles.title}>Diagnósticos recientes</h2>
        <button
          className={styles.verTodosBtn}
          onClick={() => navigate('/diagnosticos')}
          type="button"
        >
          Ver todos →
        </button>
      </div>

      <div className={styles.list}>
        {recentDiag.map((d) => (
          <button
            key={d.sectorId}
            className={styles.item}
            onClick={() => navigate('/sector/' + d.sectorId)}
            type="button"
          >
            {/* Thumbnail: gradiente de la imagen del diagnóstico */}
            <span
              className={styles.thumb}
              style={{ background: d.thumb }}
            >
              {/* Ícono hoja del diseño original */}
              <svg
                width={20}
                height={20}
                viewBox="0 0 24 24"
                fill="none"
                stroke="rgba(255,255,255,.9)"
                strokeWidth={1.7}
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.5 19 2c1 4 .5 11-4.5 15" />
                <path d="M5 21c.5-4.5 2.5-8 4.5-10" />
              </svg>
            </span>

            <div className={styles.itemBody}>
              <div className={styles.itemEstado}>{d.estado}</div>
              <div className={styles.itemSub}>
                {d.sectorId} · confianza {d.conf}%
              </div>
            </div>

            <Badge soft={d.sevSoft} ink={d.sevInk}>
              {d.sev}
            </Badge>
          </button>
        ))}
      </div>
    </div>
  );
}
