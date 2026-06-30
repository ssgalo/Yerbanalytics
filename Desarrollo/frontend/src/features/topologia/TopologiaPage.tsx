/* Vista de Administración: generación de la topología del vivero (HU-18 CA-01) */
import { useState } from 'react';
import { Card } from '@/components/ui/Card';
import { usePageTitle } from '@/hooks/PageMeta';
import { useTopologia } from '@/hooks/useTopologia';
import {
  MAX_MACRO_ZONAS,
  MAX_SECTORES_POR_ZONA,
} from '@/data/mock/topologia';
import styles from './Topologia.module.css';

type Feedback = { kind: 'ok'; msg: string } | null;

export function TopologiaPage() {
  const { data, loading, error, generating, generar } = useTopologia();
  const [macroZonas, setMacroZonas] = useState('6');
  const [sectores, setSectores] = useState('100');
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);

  usePageTitle(
    'Topología del vivero',
    data ? `${data.macroZonas} macro-zonas · ${data.totalSectores} sectores` : '',
  );

  if (error) {
    return (
      <div className={styles.state} style={{ color: 'var(--crit)' }}>
        No se pudo cargar la topología: {error.message}
      </div>
    );
  }

  if (loading || !data) {
    return <div className={styles.state}>Cargando topología…</div>;
  }

  /** Validación en cliente, espejo del backend (rangos operativos). */
  function validar(): { mz: number; spz: number } | string {
    const mz = Number(macroZonas);
    const spz = Number(sectores);
    if (!Number.isInteger(mz) || mz <= 0 || mz > MAX_MACRO_ZONAS) {
      return `La cantidad de macro-zonas debe estar entre 1 y ${MAX_MACRO_ZONAS}.`;
    }
    if (!Number.isInteger(spz) || spz <= 0 || spz > MAX_SECTORES_POR_ZONA) {
      return `La cantidad de sectores por macro-zona debe estar entre 1 y ${MAX_SECTORES_POR_ZONA}.`;
    }
    return { mz, spz };
  }

  const handleGenerar = async () => {
    const v = validar();
    if (typeof v === 'string') {
      setFormError(v);
      setConfirming(false);
      return;
    }
    setFormError(null);
    // Regenerar sobre una topología ya cargada exige confirmación explícita (HU-18 CA-01).
    if (data.generada && !confirming) {
      setFeedback(null);
      setConfirming(true);
      return;
    }
    try {
      const updated = await generar({
        macroZonas: v.mz,
        sectoresPorMacroZona: v.spz,
        regenerar: data.generada,
      });
      setConfirming(false);
      setFeedback({
        kind: 'ok',
        msg: `Topología generada: ${updated.macroZonas} macro-zonas × ${updated.sectoresPorMacroZona} sectores (${updated.totalSectores} en total).`,
      });
    } catch (e) {
      setConfirming(false);
      setFormError(e instanceof Error ? e.message : String(e));
    }
  };

  const totalPrevisto = (Number(macroZonas) || 0) * (Number(sectores) || 0);

  return (
    <div>
      <p className={styles.intro}>
        Definí la distribución física del vivero indicando la cantidad de macro-zonas y de
        sectores por macro-zona. El sistema genera la grilla lógica, asigna un identificador
        único a cada macro-zona (MZ-1, MZ-2…) y a cada sector (MZ-1-001…), y la deja disponible
        para el mapa de producción.
      </p>

      <div className={styles.summary}>
        <Card className={styles.summaryCard}>
          <div className={styles.summaryValue}>{data.macroZonas}</div>
          <div className={styles.summaryLabel}>Macro-zonas</div>
        </Card>
        <Card className={styles.summaryCard}>
          <div className={styles.summaryValue}>{data.sectoresPorMacroZona}</div>
          <div className={styles.summaryLabel}>Sectores por macro-zona</div>
        </Card>
        <Card className={styles.summaryCard}>
          <div className={styles.summaryValue}>{data.totalSectores}</div>
          <div className={styles.summaryLabel}>Sectores totales</div>
        </Card>
        <Card className={styles.summaryCard}>
          <div className={styles.summaryValue}>{data.generada ? 'Cargada' : 'Sin cargar'}</div>
          <div className={styles.summaryLabel}>Topología</div>
        </Card>
      </div>

      <Card className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>Generar topología</span>
          <span className={styles.sectionHint}>
            Grilla uniforme de macro-zonas × sectores; los sectores nacen fuera de servicio hasta
            recibir telemetría
          </span>
        </div>

        <div className={styles.formGrid}>
          <div className={styles.group}>
            <label className={styles.label} htmlFor="macroZonas">
              Macro-zonas
            </label>
            <input
              id="macroZonas"
              className={styles.input}
              type="number"
              min={1}
              max={MAX_MACRO_ZONAS}
              value={macroZonas}
              onChange={(e) => {
                setMacroZonas(e.target.value);
                setConfirming(false);
              }}
            />
          </div>

          <div className={styles.group}>
            <label className={styles.label} htmlFor="sectores">
              Sectores por macro-zona
            </label>
            <input
              id="sectores"
              className={styles.input}
              type="number"
              min={1}
              max={MAX_SECTORES_POR_ZONA}
              value={sectores}
              onChange={(e) => {
                setSectores(e.target.value);
                setConfirming(false);
              }}
            />
          </div>

          <div className={styles.group}>
            <label className={styles.label}>Sectores totales</label>
            <span className={styles.fixedValue}>{totalPrevisto}</span>
          </div>

          <span className={styles.toolbarSpacer} />

          {confirming ? (
            <>
              <button type="button" className={styles.btnSecondary} onClick={() => setConfirming(false)}>
                Cancelar
              </button>
              <button type="button" className={styles.btnDanger} disabled={generating} onClick={handleGenerar}>
                {generating ? 'Regenerando…' : 'Confirmar regeneración'}
              </button>
            </>
          ) : (
            <button type="button" className={styles.btnPrimary} disabled={generating} onClick={handleGenerar}>
              {generating ? 'Generando…' : 'Generar grilla'}
            </button>
          )}
        </div>

        {confirming && (
          <div className={styles.warn}>
            El vivero ya tiene una topología cargada. Regenerarla <strong>reemplaza la grilla</strong>{' '}
            y descarta los dispositivos registrados y el historial asociado. Esta acción no se puede
            deshacer.
          </div>
        )}

        {formError && <div className={styles.formError}>{formError}</div>}
        {feedback && <div className={styles.feedbackOk}>{feedback.msg}</div>}
      </Card>
    </div>
  );
}
