/* Tarjeta de diagnóstico de IA del plantín del sector */
import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { Icon } from '@/components/ui/Icon';
import { ProgressBar } from '@/components/ui/ProgressBar';
import type { DiagnosisDetail } from '@/types/domain';
import { CapturaModal } from './CapturaModal';
import styles from './DiagnosisCard.module.css';

interface DiagnosisCardProps {
  diag: DiagnosisDetail;
  ago: string;
  sectorId: string;
}

export function DiagnosisCard({ diag, ago, sectorId }: DiagnosisCardProps) {
  const [capturaAbierta, setCapturaAbierta] = useState(false);

  return (
    <Card className={styles.card}>
      <h3 className={styles.title}>Diagnóstico de IA</h3>

      <div className={styles.body}>
        {/* Miniatura cenital: abre la captura a tamaño completo */}
        <button
          type="button"
          className={styles.thumb}
          style={{ background: diag.thumb }}
          onClick={() => setCapturaAbierta(true)}
          aria-label={`Ver la captura cenital de ${sectorId} a tamaño completo`}
        >
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
          <span className={styles.thumbLabel}>Imagen cenital · {ago}</span>
          <span className={styles.thumbHint}>Ampliar</span>
        </button>

        {capturaAbierta && (
          <CapturaModal
            thumb={diag.thumb}
            estado={diag.estado}
            ago={ago}
            sectorId={sectorId}
            onClose={() => setCapturaAbierta(false)}
          />
        )}

        {/* Columna derecha */}
        <div className={styles.info}>
          <div className={styles.estadoRow}>
            <span className={styles.estadoText}>{diag.estado}</span>
            <Badge soft={diag.sevSoft} ink={diag.sevInk} style={{ fontSize: '11.5px', fontWeight: 700, padding: '3px 10px' }}>
              Severidad {diag.sev}
            </Badge>
          </div>

          {/* Barra de confianza */}
          <div className={styles.confSection}>
            <div className={styles.confRow}>
              <span>Nivel de confianza del modelo</span>
              <span className={styles.confValue}>{diag.conf}%</span>
            </div>
            <ProgressBar value={diag.conf ?? 0} color="var(--brand)" height={8} />
          </div>

          {/* Caja concluyente — solo si supera el umbral */}
          {diag.concluyente && (
            <div className={styles.conclusionBox}>
              <Icon
                name="check"
                size={17}
                stroke="var(--ok)"
                strokeWidth={2.2}
                style={{ flexShrink: 0, marginTop: 1 }}
              />
              <span className={styles.conclusionText}>
                Confianza por encima del umbral (85%). El motor de reglas puede ejecutar acciones correctivas automáticas.
              </span>
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}
