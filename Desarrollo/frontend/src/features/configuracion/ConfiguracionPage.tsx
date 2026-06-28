/* Vista principal: Configuración agronómica (HU-15) */
import { useEffect, useMemo, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { usePageTitle } from '@/hooks/PageMeta';
import { useConfig } from '@/hooks/useConfig';
import { validateConfig } from '@/lib/configValidation';
import { buildConfig } from '@/data/mock/config';
import type { ConfigOperativa, Configuracion, MetricThreshold, RustificacionEtapa } from '@/types/domain';
import { UmbralesForm } from './components/UmbralesForm';
import { LimitesActuadoresForm } from './components/LimitesActuadoresForm';
import { RustificacionPlanForm } from './components/RustificacionPlanForm';
import { SeguimientoForm } from './components/SeguimientoForm';
import styles from './ConfiguracionPage.module.css';

type Feedback = { kind: 'ok' | 'err'; msg: string } | null;

function formatTs(ts: number | null): string {
  if (!ts) return '';
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function ConfiguracionPage() {
  const { config, loading, error, saving, save } = useConfig();
  const [draft, setDraft] = useState<Configuracion | null>(null);
  const [feedback, setFeedback] = useState<Feedback>(null);

  // El borrador se reinicia con la config cargada o recién guardada.
  useEffect(() => {
    if (config) setDraft(structuredClone(config));
  }, [config]);

  const errors = useMemo(() => (draft ? validateConfig(draft) : []), [draft]);
  const dirty = useMemo(
    () => (draft && config ? JSON.stringify(draft) !== JSON.stringify(config) : false),
    [draft, config],
  );

  usePageTitle('Configuración agronómica', 'Umbrales, límites operativos y plan de rustificación');

  if (error) {
    return (
      <div className={styles.state} style={{ color: 'var(--crit)' }}>
        No se pudo cargar la configuración: {error.message}
      </div>
    );
  }

  if (loading || !draft) {
    return <div className={styles.state}>Cargando configuración…</div>;
  }

  const patchUmbral = (index: number, patch: Partial<MetricThreshold>) =>
    setDraft((d) =>
      d ? { ...d, umbrales: d.umbrales.map((u, i) => (i === index ? { ...u, ...patch } : u)) } : d,
    );

  const patchOperativa = (patch: Partial<ConfigOperativa>) =>
    setDraft((d) => (d ? { ...d, operativa: { ...d.operativa, ...patch } } : d));

  const setEtapas = (rustificacion: RustificacionEtapa[]) =>
    setDraft((d) => (d ? { ...d, rustificacion } : d));

  const onSave = async () => {
    setFeedback(null);
    try {
      await save(draft);
      setFeedback({ kind: 'ok', msg: 'Configuración guardada correctamente.' });
    } catch (e) {
      setFeedback({ kind: 'err', msg: e instanceof Error ? e.message : String(e) });
    }
  };

  const onReset = () => {
    setDraft(buildConfig());
    setFeedback(null);
  };

  const canSave = dirty && errors.length === 0 && !saving;

  return (
    <div>
      <p className={styles.intro}>
        Calibrá las decisiones automáticas del sistema a la realidad del vivero. El sistema arranca con
        valores de fábrica seguros para la yerba mate; los cambios se validan contra el rango fisiológico
        antes de aplicarse.
      </p>

      <Card className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>Umbrales de métricas</span>
          <span className={styles.sectionHint}>Bandas ideal · advertencia · crítico por variable</span>
        </div>
        <UmbralesForm value={draft.umbrales} onChange={patchUmbral} />
      </Card>

      <Card className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>Límites de riego e insumos</span>
          <span className={styles.sectionHint}>Topes físicos contra inundaciones y sobredosis</span>
        </div>
        <LimitesActuadoresForm value={draft.operativa} onChange={patchOperativa} />
      </Card>

      <Card className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>Plan de rustificación</span>
          <span className={styles.sectionHint}>Cronograma de exposición gradual de la mediasombra</span>
        </div>
        <RustificacionPlanForm
          value={draft.rustificacion}
          aperturaMax={draft.operativa.mediasombraAperturaMaxPct}
          onChange={setEtapas}
        />
      </Card>

      <Card className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>Seguimiento post-acción</span>
          <span className={styles.sectionHint}>Latencia y mejora mínima para evaluar efectividad</span>
        </div>
        <SeguimientoForm value={draft.operativa} onChange={patchOperativa} />
      </Card>

      <div className={styles.actions}>
        {draft.operativa.updatedBy && (
          <span className={styles.audit}>
            Última edición: {draft.operativa.updatedBy}
            {draft.operativa.updatedTs ? ` · ${formatTs(draft.operativa.updatedTs)}` : ''}
          </span>
        )}
        {feedback && (
          <span className={feedback.kind === 'ok' ? styles.feedbackOk : styles.feedbackErr}>
            {feedback.msg}
          </span>
        )}
        <span className={styles.spacer} />
        <button type="button" className={styles.btnSecondary} onClick={onReset}>
          Restablecer valores de fábrica
        </button>
        <button type="button" className={styles.btnPrimary} disabled={!canSave} onClick={onSave}>
          {saving ? 'Guardando…' : 'Guardar cambios'}
        </button>
      </div>
    </div>
  );
}
