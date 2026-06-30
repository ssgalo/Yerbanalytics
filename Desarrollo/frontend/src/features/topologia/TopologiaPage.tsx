/* Vista de Administración: generación de la topología del vivero (HU-18 CA-01) */
import { useEffect, useRef, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { usePageTitle } from '@/hooks/PageMeta';
import { useTopologia } from '@/hooks/useTopologia';
import {
  clampDisposicion,
  DEFAULT_MACRO_ZONAS_POR_FILA,
  DEFAULT_SECTORES_POR_FILA,
  disposicionError,
  MAX_MACRO_ZONAS,
  MAX_SECTORES_POR_ZONA,
} from '@/data/mock/topologia';
import { TopologiaPreview } from './components/TopologiaPreview';
import styles from './Topologia.module.css';

type Feedback = { kind: 'ok'; msg: string } | null;

export function TopologiaPage() {
  const { data, loading, error, generating, generar, guardarDisposicion } = useTopologia();
  const [macroZonas, setMacroZonas] = useState('6');
  const [sectores, setSectores] = useState('100');
  const [mzPorFila, setMzPorFila] = useState(DEFAULT_MACRO_ZONAS_POR_FILA);
  const [secPorFila, setSecPorFila] = useState(DEFAULT_SECTORES_POR_FILA);
  const [formError, setFormError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [feedback, setFeedback] = useState<Feedback>(null);

  usePageTitle(
    'Topología del vivero',
    data ? `${data.macroZonas} macro-zonas · ${data.totalSectores} sectores` : '',
  );

  // Inicializa el formulario y la disposición con la topología real una sola vez.
  const initialized = useRef(false);
  useEffect(() => {
    if (data && !initialized.current) {
      initialized.current = true;
      setMacroZonas(String(data.macroZonas));
      setSectores(String(data.sectoresPorMacroZona));
      setMzPorFila(data.macroZonasPorFila);
      setSecPorFila(data.sectoresPorFila);
    }
  }, [data]);

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

  const mzNum = Number(macroZonas) || 0;
  const spzNum = Number(sectores) || 0;
  const totalPrevisto = mzNum * spzNum;

  // ¿Cambió la cantidad de la grilla respecto de la topología cargada? Eso es destructivo.
  const cantidadesCambiaron = mzNum !== data.macroZonas || spzNum !== data.sectoresPorMacroZona;
  // ¿Cambió solo la disposición visual? (no destructivo)
  const disposicionCambiada =
    mzPorFila !== data.macroZonasPorFila || secPorFila !== data.sectoresPorFila;
  // Hay algo para guardar → mostramos el popup de guardado.
  const hayCambios = cantidadesCambiaron || disposicionCambiada;

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
    const dispErr = disposicionError({ macroZonasPorFila: mzPorFila, sectoresPorFila: secPorFila }, mz, spz);
    if (dispErr) return dispErr;
    return { mz, spz };
  }

  /**
   * Guardado unificado: si cambiaron las cantidades, regenera la grilla (destructivo, con
   * confirmación). Si solo cambió la disposición, la guarda sin tocar la grilla ni el hardware.
   */
  const handleGuardar = async () => {
    const v = validar();
    if (typeof v === 'string') {
      setFormError(v);
      setConfirming(false);
      return;
    }
    setFormError(null);

    if (cantidadesCambiaron) {
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
          macroZonasPorFila: mzPorFila,
          sectoresPorFila: secPorFila,
        });
        setConfirming(false);
        setMzPorFila(updated.macroZonasPorFila);
        setSecPorFila(updated.sectoresPorFila);
        setFeedback({
          kind: 'ok',
          msg: `Topología generada: ${updated.macroZonas} macro-zonas × ${updated.sectoresPorMacroZona} sectores (${updated.totalSectores} en total).`,
        });
      } catch (e) {
        setConfirming(false);
        setFormError(e instanceof Error ? e.message : String(e));
      }
      return;
    }

    // Solo cambió la disposición: guardado no destructivo.
    try {
      const updated = await guardarDisposicion({ macroZonasPorFila: mzPorFila, sectoresPorFila: secPorFila });
      setMzPorFila(updated.macroZonasPorFila);
      setSecPorFila(updated.sectoresPorFila);
      setFeedback({
        kind: 'ok',
        msg: `Disposición guardada: ${updated.macroZonasPorFila} macro-zonas por fila · ${updated.sectoresPorFila} sectores por fila.`,
      });
    } catch (e) {
      setFormError(e instanceof Error ? e.message : String(e));
    }
  };

  /** Descarta los cambios sin guardar y vuelve a los valores de la topología cargada. */
  const handleDescartar = () => {
    setMacroZonas(String(data.macroZonas));
    setSectores(String(data.sectoresPorMacroZona));
    setMzPorFila(data.macroZonasPorFila);
    setSecPorFila(data.sectoresPorFila);
    setConfirming(false);
    setFormError(null);
    setFeedback(null);
  };

  return (
    <div>
      <p className={styles.intro}>
        Definí la distribución física del vivero indicando la cantidad de macro-zonas y de
        sectores por macro-zona, y cómo se muestran en pantalla (macro-zonas y sectores por fila).
        Arrastrá los controles laterales del preview o ajustá los valores. Cambiar las cantidades
        regenera la grilla; cambiar solo la disposición no afecta el hardware.
      </p>

      <Card className={styles.section}>
        <div className={styles.sectionHead}>
          <span className={styles.sectionTitle}>Topología y disposición</span>
          <span className={styles.sectionHint}>
            Los sectores nacen fuera de servicio hasta recibir telemetría
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
                const n = Number(e.target.value);
                if (n > 0) setMzPorFila((p) => clampDisposicion(p, n));
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
                const n = Number(e.target.value);
                if (n > 0) setSecPorFila((p) => clampDisposicion(p, n));
              }}
            />
          </div>

          <div className={styles.group}>
            <label className={styles.label} htmlFor="mzPorFila">
              Macro-zonas por fila
            </label>
            <input
              id="mzPorFila"
              className={styles.input}
              type="number"
              min={1}
              max={mzNum || 1}
              value={mzPorFila}
              onChange={(e) => {
                setMzPorFila(clampDisposicion(Number(e.target.value) || 1, mzNum));
                setFeedback(null);
              }}
            />
          </div>

          <div className={styles.group}>
            <label className={styles.label} htmlFor="secPorFila">
              Sectores por fila
            </label>
            <input
              id="secPorFila"
              className={styles.input}
              type="number"
              min={1}
              max={spzNum || 1}
              value={secPorFila}
              onChange={(e) => {
                setSecPorFila(clampDisposicion(Number(e.target.value) || 1, spzNum));
                setFeedback(null);
              }}
            />
          </div>

          <div className={styles.group}>
            <label className={styles.label}>Sectores totales</label>
            <span className={styles.fixedValue}>{totalPrevisto}</span>
          </div>
        </div>

        <TopologiaPreview
          macroZonas={mzNum}
          sectoresPorMacroZona={spzNum}
          macroZonasPorFila={mzPorFila}
          sectoresPorFila={secPorFila}
          onChange={({ macroZonasPorFila, sectoresPorFila }) => {
            setMzPorFila(macroZonasPorFila);
            setSecPorFila(sectoresPorFila);
            setFeedback(null);
          }}
        />

        {feedback && !hayCambios && <div className={styles.feedbackOk}>{feedback.msg}</div>}
      </Card>

      {/* Popup de guardado: aparece solo si hay cambios, fuera del área de edición. */}
      {hayCambios && (
        <div className={styles.savebar} role="dialog" aria-live="polite">
          <div className={styles.savebarBody}>
            <span className={styles.savebarTitle}>
              {confirming ? 'Confirmá la regeneración' : 'Tenés cambios sin guardar'}
            </span>
            <span className={styles.savebarHint}>
              {confirming
                ? 'Reemplazar la grilla descarta los dispositivos registrados y el historial asociado. No se puede deshacer.'
                : cantidadesCambiaron
                  ? 'Cambiar las cantidades regenera la grilla del vivero.'
                  : 'Se actualizará solo la disposición en pantalla; no afecta el hardware.'}
            </span>
            {formError && <span className={styles.savebarError}>{formError}</span>}
          </div>
          <div className={styles.savebarActions}>
            {confirming ? (
              <>
                <button
                  type="button"
                  className={styles.btnSecondary}
                  onClick={() => setConfirming(false)}
                >
                  Cancelar
                </button>
                <button
                  type="button"
                  className={styles.btnDanger}
                  disabled={generating}
                  onClick={handleGuardar}
                >
                  {generating ? 'Regenerando…' : 'Confirmar regeneración'}
                </button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  className={styles.btnSecondary}
                  disabled={generating}
                  onClick={handleDescartar}
                >
                  Descartar
                </button>
                <button
                  type="button"
                  className={styles.btnPrimary}
                  disabled={generating}
                  onClick={handleGuardar}
                >
                  {generating ? 'Guardando…' : 'Guardar cambios'}
                </button>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
