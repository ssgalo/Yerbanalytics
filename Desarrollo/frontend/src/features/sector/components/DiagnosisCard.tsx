/* Tarjeta de diagnóstico de IA del plantín del sector */
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Icon } from '@/components/ui/Icon';
import { buttonClass } from '@/components/ui/clases';
import { useAuth } from '@/hooks/AuthContext';
import { puedeVer, VISTAS } from '@/lib/vistas';
import { hasConfidence, isAnomalous } from '@/lib/diagnosis';
import { CroppedPhoto } from '@/features/diagnostics/components/CroppedPhoto';
import { ConfidenceChip } from '@/features/diagnostics/components/ConfidenceChip';
import type { DiagnosisDetail } from '@/types/domain';
import { CapturaModal } from './CapturaModal';
import styles from './DiagnosisCard.module.css';

interface DiagnosisCardProps {
  diag: DiagnosisDetail;
  ago: string;
  sectorId: string;
  isOffline?: boolean;
}

export function DiagnosisCard({ diag, ago, sectorId, isOffline }: DiagnosisCardProps) {
  const [capturaAbierta, setCapturaAbierta] = useState(false);
  const { puede } = useAuth();

  return (
    <Card className={styles.card}>
      <div className={styles.head}>
        <h3 className={styles.title}>Diagnóstico de IA</h3>
        {/* Los diagnósticos previos viven en su vista, ya filtrada por este sector */}
        {puedeVer(VISTAS.diagnosticos, puede) && (
          <Link
            className={buttonClass('secondary', 'sm')}
            to={`/diagnosticos?sector=${encodeURIComponent(sectorId)}`}
          >
            Ver anteriores
          </Link>
        )}
      </div>

      <div className={styles.body}>
        {/* Miniatura cenital: abre la captura a tamaño completo */}
        <button
          type="button"
          className={styles.thumb}
          // Con foto, el fondo es sólo el de respaldo: la imagen (recortada) va encima.
          style={{ background: diag.imagenUrl ? undefined : diag.thumb }}
          onClick={() => setCapturaAbierta(true)}
          aria-label={`Ver la captura cenital de ${sectorId} a tamaño completo`}
        >
          {diag.imagenUrl ? (
            <CroppedPhoto src={diag.imagenUrl} crop={diag.crop} />
          ) : (
            <Icon
              name="leaf-simple"
              size={60}
              stroke="rgba(255,255,255,.55)"
              strokeWidth={1.3}
              style={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%,-50%)',
              }}
            />
          )}
          <span className={styles.thumbLabel}>Imagen cenital · {ago}</span>
          <span className={styles.thumbHint}>Ampliar</span>
        </button>

        {capturaAbierta && (
          <CapturaModal
            thumb={diag.thumb}
            imagenUrl={diag.imagenUrl}
            estado={diag.estado}
            ago={ago}
            sectorId={sectorId}
            diagnosisId={diag.diagnosisId}
            detectionCount={diag.detectionCount}
            onClose={() => setCapturaAbierta(false)}
          />
        )}

        {/* Columna derecha */}
        <div className={styles.info}>
          <div className={styles.estadoRow}>
            <span className={styles.estadoText}>{diag.estado}</span>
            {/* La severidad sólo existe en los estados anómalos */}
            {isAnomalous(diag.estado) && diag.sev !== '—' && (
              <Badge soft={diag.sevSoft} ink={diag.sevInk} style={{ fontSize: '11.5px', fontWeight: 700, padding: '3px 10px' }}>
                Severidad {diag.sev}
              </Badge>
            )}
          </div>

          {/* Nivel de confianza. Qué hace el motor con el diagnóstico no se dice acá: es decisión
              del motor. */}
          {hasConfidence(diag.estado) && (
            <div className={styles.confSection}>
              <ConfidenceChip level={diag.confidence} />
            </div>
          )}

          {/* Caja de advertencia para modo offline */}
          {isOffline && diag.estado !== 'Sin diagnóstico' && (
            <div className={styles.warningBox}>
              <Icon
                name="alert"
                size={17}
                stroke="var(--warn-ink)"
                strokeWidth={2.2}
                style={{ flexShrink: 0, marginTop: 1 }}
              />
              <span className={styles.warningText}>
                Sin conexión: puede estar desactualizado.
              </span>
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}
