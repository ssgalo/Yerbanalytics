/* ============================================================
   ProblemsList — lista de sectores críticos / en observación,
   ordenados por severidad (crítico primero).

   No se trunca a N ítems: la lista scrollea dentro de su tarjeta, así
   que un recorte silencioso escondería sectores que hay que revisar.
   Hover: fondo var(--bg).
   ============================================================ */
import type { Sector } from '@/types/domain';
import type { ColorPair, Severity } from '@/types/domain';
import { Badge } from '@/components/ui/Badge';
import { StatusDot } from '@/components/ui/StatusDot';
import { Card } from '@/components/ui/Card';
import styles from './ProblemsList.module.css';

/* Ítem derivado que necesita la lista */
interface ProblemItem {
  id: string;
  color: string;
  reason: string;
  sev: Severity;
  sevSoft: string;
  sevInk: string;
}

interface ProblemsListProps {
  sectors: Sector[];
  sevMap: Record<Severity, ColorPair>;
  onSectorClick: (sectorId: string) => void;
}

export function ProblemsList({ sectors, sevMap, onSectorClick }: ProblemsListProps) {
  /* Filtrar críticos y en alerta, ordenar críticos primero */
  const problems: ProblemItem[] = sectors
    .filter((s) => s.status === 'critical' || s.status === 'warning')
    .sort((a, b) => (a.status === 'critical' ? 0 : 1) - (b.status === 'critical' ? 0 : 1))
    .map((s) => ({
      id: s.id,
      color: s.color,
      reason: s.reason,
      sev: s.diagnosis.sev,
      sevSoft: sevMap[s.diagnosis.sev].soft,
      sevInk: sevMap[s.diagnosis.sev].ink,
    }));

  return (
    <Card className={styles.card}>
      <h2 className={styles.title}>
        Sectores a revisar
        {problems.length > 0 && <span className={styles.count}>{problems.length}</span>}
      </h2>
      {problems.length === 0 ? (
        <p className={styles.empty}>Ningún sector requiere revisión.</p>
      ) : (
        <div className={styles.list}>
          {problems.map((p) => (
            <button key={p.id} className={styles.item} onClick={() => onSectorClick(p.id)}>
              {/* Dot de color del sector */}
              <StatusDot color={p.color} size={9} />

              {/* Id + razón */}
              <div className={styles.info}>
                <div className={styles.sectorId}>{p.id}</div>
                <div className={styles.reason}>{p.reason}</div>
              </div>

              {/* Badge de severidad */}
              <Badge
                soft={p.sevSoft}
                ink={p.sevInk}
                style={{ fontSize: '10.5px', padding: '2px 8px' }}
              >
                {p.sev}
              </Badge>
            </button>
          ))}
        </div>
      )}
    </Card>
  );
}
