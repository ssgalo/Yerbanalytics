/* Regenera la grilla del vivero (N macro-zonas × M sectores) desde el flujo de simulación,
   para que la topología coincida con el hardware a simular. La regeneración deja cada sector
   sin valores históricos (offline). Pide confirmación si ya hay una topología cargada. */
import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import type { TopologiaVivero } from '@/types/domain';
import styles from '../Simulacion.module.css';

interface RegenerarTopologiaFormProps {
  topologia: TopologiaVivero | null;
  generating: boolean;
  onRegenerar: (macroZonas: number, sectoresPorMacroZona: number) => Promise<void>;
}

export function RegenerarTopologiaForm({
  topologia,
  generating,
  onRegenerar,
}: RegenerarTopologiaFormProps) {
  const [macroZonas, setMacroZonas] = useState(6);
  const [sectores, setSectores] = useState(100);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);

  // Precarga con las cantidades vigentes cuando la topología termina de cargar.
  useEffect(() => {
    if (topologia && topologia.macroZonas > 0) {
      setMacroZonas(topologia.macroZonas);
      setSectores(topologia.sectoresPorMacroZona);
    }
  }, [topologia]);

  const handleRegenerar = async () => {
    setError(null);
    setOk(null);
    if (macroZonas < 1 || sectores < 1) {
      setError('Ingresá cantidades válidas (≥ 1) de macro-zonas y sectores.');
      return;
    }
    // Reemplazar una grilla existente descarta sus datos: confirmar antes.
    if (topologia?.generada) {
      const total = macroZonas * sectores;
      const confirmado = window.confirm(
        `Esto reemplaza la topología actual por ${macroZonas} macro-zonas × ${sectores} sectores ` +
          `(${total} sectores) y deja todos los sectores sin lecturas ni históricos. ¿Continuar?`,
      );
      if (!confirmado) return;
    }
    try {
      await onRegenerar(macroZonas, sectores);
      setOk(`Topología regenerada: ${macroZonas} × ${sectores} sectores, sin históricos.`);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <Card className={styles.section}>
      <div className={styles.sectionHead}>
        <span className={styles.sectionTitle}>Regenerar topología</span>
        <span className={styles.sectionHint}>
          Genera la grilla del vivero para que coincida con el hardware a simular. Deja cada sector
          offline, sin valores históricos.
        </span>
      </div>
      <div className={styles.formGrid}>
        <div className={styles.group}>
          <label className={styles.label}>Macro-zonas</label>
          <input
            className={styles.numInput}
            type="number"
            min={1}
            value={macroZonas}
            onChange={(e) => setMacroZonas(Number(e.target.value))}
          />
        </div>
        <div className={styles.group}>
          <label className={styles.label}>Sectores por macro-zona</label>
          <input
            className={styles.numInput}
            type="number"
            min={1}
            value={sectores}
            onChange={(e) => setSectores(Number(e.target.value))}
          />
        </div>
        <button
          type="button"
          className={styles.btnPrimary}
          disabled={generating}
          onClick={handleRegenerar}
        >
          {generating ? 'Regenerando…' : 'Regenerar topología'}
        </button>
      </div>
      {error && <div className={styles.formError}>{error}</div>}
      {ok && <div className={styles.feedbackOk}>{ok}</div>}
    </Card>
  );
}
