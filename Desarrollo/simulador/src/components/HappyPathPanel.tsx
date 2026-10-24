/* ============================================================
   Happy path de la demo (pestaña Demo Expo): un botón que evalúa riego, insumo y mediasombra.

   Según el interruptor de fuente de telemetría del backend:
     SIMULATOR  el simulador genera lectura, clima y diagnóstico para que las tres reglas actúen.
     REAL_NODE  no inyecta nada: sensor real, pronóstico real y diagnóstico de una pasada previa.
   La decisión y la orquestación viven en `happyPath.ts` (probado); acá sólo está la UI.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import * as api from '../api';
import { registerDiagnosisOnLatestCapture } from '../captureFlow';
import { ensureTankStock } from '../tankStock';
import {
  hintFor,
  initialProgress,
  loadThresholds,
  pickTarget,
  progressDone,
  readProgress,
  runHappyPath,
  type ActionProgress,
  type DiagnosisSource,
  type HappyPathMode,
  type HappyPathOutcome,
  type HappyTarget,
} from '../happyPath';
import type { SectorRef, SimulatedSensor } from '../types';

interface Props {
  sectors: SectorRef[];
  sensors: SimulatedSensor[];
  /** Hay una pasada del riel en curso: el happy path espera. */
  pasadaEnCurso: boolean;
  /** Cambia cada vez que se elige otra fuente de telemetría en la misma página: el panel relee su modo. */
  sourceVersion?: number;
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

type ActionKey = keyof ActionProgress;

const ACTIONS: ActionKey[] = ['riego', 'insumo', 'mediasombra'];

const LABELS: Record<ActionKey, string> = { riego: 'Riego', insumo: 'Dosificación de insumo', mediasombra: 'Mediasombra' };

/** Cada cuánto se relee el estado de los actuadores mientras hay acciones sin terminar, y hasta cuándo. */
const PROGRESS_POLL_MS = 2_000;
const PROGRESS_GIVE_UP_MS = 10 * 60_000;

const SOURCE_KEY = 'happyPath.diagnosisSource';

/** La elección se recuerda por navegador; sin localStorage (o con un valor ajeno) rige el simulado. */
function loadSource(): DiagnosisSource {
  try {
    return window.localStorage.getItem(SOURCE_KEY) === 'LATEST_CAMERA' ? 'LATEST_CAMERA' : 'SIMULATED';
  } catch {
    return 'SIMULATED';
  }
}

export function HappyPathPanel({ sectors, sensors, pasadaEnCurso, sourceVersion = 0 }: Props) {
  const [mode, setMode] = useState<HappyPathMode | null>(null);
  const [modeError, setModeError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [log, setLog] = useState<string[]>([]);
  const [outcome, setOutcome] = useState<HappyPathOutcome | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState<ActionProgress | null>(null);
  const [progressTarget, setProgressTarget] = useState<HappyTarget | null>(null);
  const [catalogWarning, setCatalogWarning] = useState<string | null>(null);
  const [diagnosisSource, setDiagnosisSource] = useState<DiagnosisSource>(loadSource);

  const chooseSource = (next: DiagnosisSource) => {
    setDiagnosisSource(next);
    try {
      window.localStorage.setItem(SOURCE_KEY, next);
    } catch {
      /* sin almacenamiento: la elección vale sólo para esta sesión */
    }
  };

  const loadMode = useCallback(async () => {
    try {
      setMode(await api.getTelemetrySource());
      setModeError(null);
    } catch (e) {
      setMode(null);
      setModeError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    void loadMode();
  }, [loadMode, sourceVersion]);

  const target = mode ? pickTarget(sectors, sensors, mode) : null;
  const noTarget = mode !== null && target === null;

  const run = async () => {
    setRunning(true);
    setError(null);
    setOutcome(null);
    setProgress(null);
    setProgressTarget(null);
    const lines: string[] = [];
    const push = (line: string) => {
      lines.push(line);
      setLog([...lines]);
    };
    setLog([]);
    try {
      // El interruptor se relee: pudo cambiar desde que se montó la pestaña.
      const current = await api.getTelemetrySource();
      setMode(current);
      const chosen = pickTarget(sectors, sensors, current);
      if (!chosen) throw new Error('No hay sector (o sensor simulado) donde correr el happy path.');
      if ((await api.getSecuenciaActual())?.estado === 'EN_CURSO') {
        throw new Error('Hay una secuencia en curso: esperá que termine.');
      }
      push(`Modo ${current === 'SIMULATOR' ? 'simulador' : 'ESP32 real'} · ${chosen.sectorId} (${chosen.zonaId})`);
      const { thresholds, warning } = await loadThresholds(api.getParametrosCatalogo);
      if (warning) {
        setCatalogWarning(warning);
        push(`⚠ ${warning}`);
      } else {
        setCatalogWarning(null);
      }
      const result = await runHappyPath(current, chosen, thresholds, {
          zeroCooldowns: api.zeroDemoCooldowns,
          getRules: api.getDemoRules,
          updateRule: api.updateDemoRule,
          ensureTankStock: (zonaId) => ensureTankStock(zonaId, { getTanks: api.getTanks, loadTank: api.loadTank }),
          setWeather: api.setSimulatedWeather,
          clearWeather: api.clearSimulatedWeather,
          setOffline: api.setOfflineForecast,
          getEmission: async () => (await api.getStatus()).emission.active,
          setEmission: api.setAutoEmission,
          getPinned: async (zonaId) => (await api.getPinnedReadings())[zonaId] ?? {},
          pinReadings: api.pinReadings,
          registerDiagnosis: (request) => registerDiagnosisOnLatestCapture(request, push),
          getSectorDiagnosis: api.getSectorDiagnosis,
          triggerPulse: () => api.triggerSensingPulse(true),
          getTrace: api.getEvaluationTrace,
          sleep,
          log: push,
        }, { diagnosisSource });
      setOutcome(result);
      // El avance de cada acción se sigue del snapshot (efecto de abajo): la corrida no espera que termine ninguna.
      setProgress(initialProgress(result.readout));
      setProgressTarget(chosen);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
      push(`✗ ${msg}`);
    } finally {
      setRunning(false);
    }
  };

  // El nodo serializa las cargas: el riego y la dosificación avanzan de a una (en cola → en curso → hecho). Se sigue su
  // estado en el snapshot hasta que terminen (o hasta rendirse). La corrida del pulso no espera nada de esto.
  useEffect(() => {
    if (!progress || !progressTarget || progressDone(progress)) return undefined;
    let cancelled = false;
    const startedAt = Date.now();
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt > PROGRESS_GIVE_UP_MS) {
        window.clearInterval(timer);
        return;
      }
      void readProgress(progress, progressTarget.sectorId, () => api.getZoneActuators(progressTarget.zonaId)).then((next) => {
        if (!cancelled) setProgress((current) => (current === progress ? next : current));
      });
    }, PROGRESS_POLL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [progress, progressTarget]);

  const disabled = running || pasadaEnCurso || mode === null || noTarget;

  return (
    <section className="card">
      <h2 className="sectionTitle">Happy path de la demo</h2>
      <p className="sectionHint">
        Evalúa en un solo pulso el riego, la dosificación de insumo (según el diagnóstico de IA) y la mediasombra.{' '}
        {mode ? hintFor(mode) : (modeError ?? 'Consultando la fuente de telemetría…')}
      </p>
      {target && (
        <p className="muted" style={{ fontSize: 12.5 }}>
          Sector destino: <strong>{target.sectorId}</strong> ({target.zonaId}, primera macro-zona con sectores).
        </p>
      )}
      {noTarget && (
        <p className="errorMsg">
          {mode === 'SIMULATOR'
            ? 'No hay un sensor simulado en una zona con sectores: creá uno en Configuración.'
            : 'La topología no tiene sectores.'}
        </p>
      )}
      {mode === 'SIMULATOR' && (
        <div style={{ margin: '8px 0', fontSize: 13 }}>
          <span>Diagnóstico de IA:</span>
          <div className="actions" role="group" aria-label="Diagnóstico de IA">
            <button
              type="button"
              className={diagnosisSource === 'LATEST_CAMERA' ? 'primary' : 'btnSecondary'}
              aria-pressed={diagnosisSource === 'LATEST_CAMERA'}
              disabled={running}
              onClick={() => chooseSource('LATEST_CAMERA')}
            >
              Última foto de IA
            </button>
            <button
              type="button"
              className={diagnosisSource === 'SIMULATED' ? 'primary' : 'btnSecondary'}
              aria-pressed={diagnosisSource === 'SIMULATED'}
              disabled={running}
              onClick={() => chooseSource('SIMULATED')}
            >
              Diagnóstico simulado
            </button>
          </div>
          <span className="muted" style={{ fontSize: 12.5 }}>
            {diagnosisSource === 'LATEST_CAMERA'
              ? 'Usa el diagnóstico real de la última foto del sector (hacé una pasada del riel antes). No registra nada.'
              : 'El simulador registra un diagnóstico inventado para que el insumo actúe, colgado de la última foto del sector (no pide una orden, así que no compite con el celular). Sólo si el sector no tiene fotos pide una captura nueva, y ahí necesita la cámara del simulador libre.'}
          </span>
        </div>
      )}
      <div className="actions">
        <button type="button" className="primary" disabled={disabled} onClick={() => void run()}>
          {running ? 'Ejecutando…' : 'Ejecutar happy path'}
        </button>
        {pasadaEnCurso && <span className="muted">Hay una pasada del riel en curso.</span>}
        {error && <span className="errorText">{error}</span>}
      </div>

      {catalogWarning && <p className="errorMsg" style={{ marginTop: 8 }}>{catalogWarning}</p>}

      {log.length > 0 && (
        <div style={{ fontFamily: 'ui-monospace, Consolas, monospace', fontSize: 12.5, lineHeight: 1.7, marginTop: 12 }}>
          {log.map((line, i) => (
            <div key={i} style={{ color: line.startsWith('✓') ? 'var(--accent)' : line.startsWith('✗') ? 'var(--error)' : 'var(--ink)' }}>
              {line}
            </div>
          ))}
        </div>
      )}

      {(running || outcome) && (
        <div className="card" style={{ marginTop: 12 }}>
          <p className="sectionTitle" style={{ marginBottom: 6 }}>Resultado del pulso</p>
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
            {ACTIONS.map((key) => {
              const readout = outcome?.readout?.[key];
              const label = progress?.[key];
              return (
                <li key={key} style={{ color: readout?.acted ? 'var(--accent)' : 'var(--ink)' }}>
                  <strong>{LABELS[key]}:</strong> {label ?? (running ? 'evaluando…' : 'sin dato')}
                  {readout && ` — ${readout.acted ? 'actuó' : 'no actuó'}: ${readout.detail}`}
                </li>
              );
            })}
          </ul>
          {outcome && outcome.leftActive.length > 0 && (
            <p className="muted" style={{ marginTop: 8, fontSize: 12.5 }}>
              Quedó activo: {outcome.leftActive.join(', ')}. Se desactivan desde Telemetría → Control de reglas.
            </p>
          )}
          {outcome?.warnings.map((w, i) => (
            <p key={i} className="errorMsg" style={{ marginTop: 8 }}>{w}</p>
          ))}
        </div>
      )}
    </section>
  );
}
