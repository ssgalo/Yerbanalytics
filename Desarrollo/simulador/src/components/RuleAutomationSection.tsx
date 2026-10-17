/* ============================================================
   Apartado 3: Automatización de Telemetría para Ejecución de Reglas.

   Lets the operator select one or more agronomic rules, automatically computes the
   telemetry values needed to trigger them (reading current thresholds from the backend),
   and executes the full cycle in one click:
     1. Zeroes demo cooldowns (if in demo mode and the rules need it).
     2. Injects simulated weather when needed (R-03 / ShadingRule).
     3. Publishes the calculated MQTT telemetry for the target sensor.
     4. Registers the prior AI diagnosis (SupplyRule only acts on a NEW one).
     5. Creates the manual lock the "Bloqueo manual" scenario asks for (before the pulse).
     6. Triggers a sensing pulse with the rule engine (or the chain of pulses a scenario asks for).

   The "node without answer" scenario is the exception: it silences the target zone instead of
   publishing telemetry, so the pulses find no answer.

   No backend modifications: everything goes through existing public endpoints.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import * as api from '../api';
import { registerDiagnosisWithCapture } from '../captureFlow';
import type { DemoRule, ManualLock, ParametroRegla, SectorRef, SimulatedSensor } from '../types';
import { buildThresholds, calculateConditions } from '../ruleConditions';
import { runPulses } from '../pulseChain';
import { rulesWithoutScenario, SCENARIOS } from '../scenarios';

interface Props {
  sensors: SimulatedSensor[];
  sectors: SectorRef[];
  demoEnabled: boolean;
}

type ExecutionState = 'idle' | 'running' | 'ok' | 'conflict' | 'error';

export function RuleAutomationSection({ sensors, sectors, demoEnabled }: Props) {
  const [parametros, setParametros] = useState<ParametroRegla[]>([]);
  const [loadingParams, setLoadingParams] = useState(true);
  const [withoutScenario, setWithoutScenario] = useState<DemoRule[]>([]);
  const [selectedRules, setSelectedRules] = useState<Set<string>>(new Set());
  const [targetSerial, setTargetSerial] = useState('');
  const [execState, setExecState] = useState<ExecutionState>('idle');
  const [execLog, setExecLog] = useState<string[]>([]);
  const [execError, setExecError] = useState<string | null>(null);
  const [silencedZones, setSilencedZones] = useState<string[]>([]);
  const [manualLocks, setManualLocks] = useState<ManualLock[]>([]);
  // '' = lock the whole zone; otherwise the id of one of its sectors.
  const [lockSectorId, setLockSectorId] = useState('');

  // Zones the simulator keeps mute: they stay that way until released, so they are shown after the run.
  useEffect(() => {
    api.getSilencedZones().then(setSilencedZones).catch(() => setSilencedZones([]));
  }, []);

  // Manual locks in force: the scenario creates them and they stay until lifted, so they are listed here.
  useEffect(() => {
    api.getManualLocks().then(setManualLocks).catch(() => setManualLocks([]));
  }, []);

  const liftLock = async (id: number) => {
    try {
      await api.releaseManualLock(id);
      setManualLocks(await api.getManualLocks());
    } catch (e) {
      setExecError(e instanceof Error ? e.message : String(e));
    }
  };

  const releaseZone = async (zonaId: string) => {
    try {
      setSilencedZones(await api.setZoneSilenced(zonaId, false));
    } catch (e) {
      setExecError(e instanceof Error ? e.message : String(e));
    }
  };

  // Preselect first sensor.
  useEffect(() => {
    if (sensors.length > 0 && !targetSerial) {
      setTargetSerial(sensors[0].serial);
    }
  }, [sensors, targetSerial]);

  // Load rule parameters on mount (and retry-able).
  const loadParams = useCallback(async () => {
    setLoadingParams(true);
    try {
      setParametros(await api.getParametrosCatalogo());
    } catch {
      // Use defaults; the ruleConditions module handles the fallback.
      setParametros([]);
    } finally {
      setLoadingParams(false);
    }
  }, []);

  useEffect(() => {
    void loadParams();
  }, [loadParams]);

  // Backend rules the simulator cannot arm: they can be switched off in the rules panel, nothing more.
  useEffect(() => {
    if (!demoEnabled) return;
    api
      .getDemoRules()
      .then((rules) => setWithoutScenario(rulesWithoutScenario(rules)))
      .catch(() => setWithoutScenario([]));
  }, [demoEnabled]);

  const thresholds = buildThresholds(parametros);

  const toggleRule = (key: string) => {
    setSelectedRules((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  const conditions = selectedRules.size > 0
    ? calculateConditions([...selectedRules], thresholds)
    : null;

  const targetSensor = sensors.find((s) => s.serial === targetSerial);
  // The diagnosis is per sector, the sensing per zone: the first sector of the target zone stands in.
  const diagnosisSector = sectors.find((s) => s.zonaId === targetSensor?.zonaId);
  const missingDiagnosisSector = conditions?.diagnostico != null && !diagnosisSector;
  const zoneSectors = sectors.filter((s) => s.zonaId === targetSensor?.zonaId);
  // A sector picked for a previous zone is not valid for this one.
  const lockSector = zoneSectors.find((s) => s.id === lockSectorId);

  const execute = async () => {
    if (!targetSensor || !conditions || selectedRules.size === 0) return;
    if (conditions.conflictos.length > 0) return;

    setExecState('running');
    setExecLog([]);
    setExecError(null);
    const log: string[] = [];

    try {
      // Step 1: zero cooldowns if needed.
      if (conditions.needsCooldownReset && demoEnabled) {
        await api.zeroDemoCooldowns();
        log.push('✓ Cooldowns de demo → 0');
        setExecLog([...log]);
      }

      // Step 2: inject simulated weather if needed.
      if (conditions.clima) {
        await api.setSimulatedWeather(conditions.clima);
        log.push(`✓ Clima simulado aplicado (uvIndex: ${conditions.clima.uvIndex}, probLluvia: ${conditions.clima.probLluviaPct}%)`);
        setExecLog([...log]);
      }

      // Step 3: publish telemetry to the broker. A scenario that silences the zone publishes none: the
      // point is that the node does not answer.
      if (conditions.silenceZone) {
        setSilencedZones(await api.setZoneSilenced(targetSensor.zonaId, true));
        log.push(`✓ ${targetSensor.zonaId} deja de responder LEER_AHORA`);
      } else {
        await api.sendTelemetry({
          serial: targetSensor.serial,
          zonaId: targetSensor.zonaId,
          metrics: conditions.telemetria,
        });
        log.push(`✓ Telemetría publicada para ${targetSensor.zonaId} (humSus: ${conditions.telemetria.humSus}%)`);
      }
      setExecLog([...log]);

      // Step 4: register the prior diagnosis. It must exist before the pulse: that is the only
      // moment the engine decides, and the rule acts once per new diagnosis.
      if (conditions.diagnostico && diagnosisSector) {
        const created = await registerDiagnosisWithCapture({
          ...conditions.diagnostico,
          sectorId: diagnosisSector.id,
          zonaId: diagnosisSector.zonaId,
        });
        log.push(
          `✓ Diagnóstico IA ${created.id} registrado en ${diagnosisSector.id} (${conditions.diagnostico.estado}, conf. ${conditions.diagnostico.conf}%)`,
        );
        setExecLog([...log]);
      }

      // Step 5: the manual lock goes in before the pulse: that is the only moment the engine decides, and
      // the point is to see it NOT irrigate. An existing lock on the same scope is fine (409): it already does the job.
      if (conditions.manualLock) {
        const scope = lockSector ? `el sector ${lockSector.id}` : `la zona ${targetSensor.zonaId}`;
        try {
          const created = await api.createManualLock({
            zonaId: targetSensor.zonaId,
            ...(lockSector && { sectorId: lockSector.id }),
            motivo: conditions.manualLock.reason,
          });
          log.push(`✓ Bloqueo manual #${created.id} creado sobre ${scope}`);
        } catch (e) {
          const msg = e instanceof Error ? e.message : String(e);
          if (!msg.toLowerCase().includes('ya tiene un bloqueo')) throw e;
          log.push(`✓ ${scope} ya estaba bloqueada: se usa ese bloqueo`);
        }
        setManualLocks(await api.getManualLocks());
        setExecLog([...log]);
      }

      // Step 6: trigger the sensing pulse(s) with motor. A chain waits for each pulse to end before asking
      // for the next, because the backend runs one at a time.
      await runPulses(conditions.pulses, {
        trigger: () => api.triggerSensingPulse(true),
        sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
        onPulse: (n) => {
          log.push(
            conditions.pulses === 1
              ? '✓ Pulso de sensado con motor disparado'
              : `✓ Pulso ${n} de ${conditions.pulses} con motor disparado (esperando que termine para pedir el siguiente)`,
          );
          setExecLog([...log]);
        },
      });

      setExecState('ok');
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      if (msg.includes('409') || msg.toLowerCase().includes('en curso')) {
        setExecState('conflict');
        log.push('✗ Conflicto (409): ya hay una operación de hardware en curso. Esperá que termine.');
      } else {
        setExecState('error');
        setExecError(msg);
        log.push(`✗ Error: ${msg}`);
      }
      setExecLog([...log]);
    }
  };

  const canExecute =
    selectedRules.size > 0 &&
    targetSensor !== undefined &&
    conditions !== null &&
    conditions.conflictos.length === 0 &&
    !missingDiagnosisSector &&
    execState !== 'running';

  return (
    <div>
      <section className="card">
        <h2 className="sectionTitle">Automatización de reglas</h2>
        <p className="sectionHint">
          Seleccioná una o más reglas agronómicas. El simulador calcula la telemetría y el
          pronóstico climático necesarios según los umbrales vigentes del backend, y los
          despacha en un clic.
          {loadingParams && ' (Cargando umbrales del backend…)'}
          {!loadingParams && parametros.length === 0 && ' (Usando valores de fábrica por defecto.)'}
        </p>

        {/* Sensor selector */}
        {sensors.length === 0 ? (
          <p className="note">
            No hay sensores simulados. Creá uno en la pestaña Configuración para poder usar la
            automatización.
          </p>
        ) : (
          <div className="row" style={{ marginBottom: 16 }}>
            <label className="field">
              <span className="label">Sensor destino (zona)</span>
              <select value={targetSerial} onChange={(e) => setTargetSerial(e.target.value)}>
                {sensors.map((s) => (
                  <option key={s.serial} value={s.serial}>
                    {s.serial} · {s.zonaId}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}

        {/* Rule checkboxes */}
        <div className="ruleList">
          {SCENARIOS.map((rule) => (
            <label key={rule.key} className="ruleRow ruleToggle" style={{ display: 'flex', gap: 10, cursor: 'pointer', padding: '8px 12px' }}>
              <input
                type="checkbox"
                checked={selectedRules.has(rule.key)}
                onChange={() => toggleRule(rule.key)}
              />
              <div style={{ flex: 1 }}>
                <div className="ruleLabel">{rule.label}</div>
                <div className="ruleMeta">{rule.description} · {rule.branch}</div>
              </div>
            </label>
          ))}
        </div>

        {withoutScenario.length > 0 && (
          <details style={{ marginTop: 12 }}>
            <summary className="muted">Reglas sin escenario ({withoutScenario.length})</summary>
            <div className="ruleList" style={{ marginTop: 8 }}>
              {withoutScenario.map((rule) => (
                <div
                  key={rule.name}
                  className="ruleRow ruleDisabled"
                  style={{ padding: '8px 12px' }}
                  title="El simulador no sabe armar las condiciones de esta regla. Se puede apagar desde Telemetría → Control de reglas."
                >
                  <div className="ruleLabel">{rule.label}</div>
                  <div className="ruleMeta">sin escenario · {rule.branch}</div>
                </div>
              ))}
            </div>
          </details>
        )}

        {silencedZones.length > 0 && (
          <div className="card" style={{ marginTop: 12 }}>
            <p className="sectionTitle" style={{ marginBottom: 6 }}>Zonas sin responder</p>
            {silencedZones.map((zonaId) => (
              <div key={zonaId} className="row" style={{ alignItems: 'center', gap: 12 }}>
                <span>{zonaId} no contesta LEER_AHORA</span>
                <button type="button" onClick={() => void releaseZone(zonaId)}>
                  Volver a responder
                </button>
              </div>
            ))}
          </div>
        )}

        {manualLocks.length > 0 && (
          <div className="card" style={{ marginTop: 12 }}>
            <p className="sectionTitle" style={{ marginBottom: 6 }}>Bloqueos vigentes</p>
            {manualLocks.map((l) => (
              <div key={l.id} className="row" style={{ alignItems: 'center', gap: 12 }}>
                <span>
                  {l.sectorId ? `Sector ${l.sectorId}` : `Zona ${l.zonaId}`} · {l.motivo} ({l.activadoPor})
                </span>
                <button type="button" onClick={() => void liftLock(l.id)}>
                  Levantar bloqueo
                </button>
              </div>
            ))}
          </div>
        )}

        {/* Conflict warnings */}
        {conditions && conditions.conflictos.length > 0 && (
          <div className="errorMsg" style={{ marginTop: 12 }}>
            {conditions.conflictos.map((c, i) => (
              <div key={i}>{c}</div>
            ))}
          </div>
        )}

        {missingDiagnosisSector && (
          <div className="errorMsg" style={{ marginTop: 12 }}>
            La zona {targetSensor?.zonaId} no tiene sectores en la topología vigente: no hay dónde
            registrar el diagnóstico que necesita la dosificación.
          </div>
        )}

        {conditions?.manualLock && (
          <div className="row" style={{ marginTop: 12 }}>
            <label className="field">
              <span className="label">Alcance del bloqueo</span>
              <select value={lockSector?.id ?? ''} onChange={(e) => setLockSectorId(e.target.value)}>
                <option value="">Toda la zona {targetSensor?.zonaId}</option>
                {zoneSectors.map((s) => (
                  <option key={s.id} value={s.id}>
                    Sólo el sector {s.id}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}

        {/* Preview of what will be sent */}
        {conditions && conditions.conflictos.length === 0 && (
          <div className="card" style={{ marginTop: 12, background: 'var(--accent-soft)', border: '1px solid var(--line)' }}>
            <p className="sectionTitle" style={{ marginBottom: 6 }}>Vista previa</p>
            <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
              {conditions.resumen.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
              {conditions.resumen.length === 0 && (
                <li className="muted">Sin condiciones especiales: telemetría de vivero saludable.</li>
              )}
            </ul>
          </div>
        )}

        <div className="actions" style={{ marginTop: 16 }}>
          <button
            type="button"
            className="primary"
            disabled={!canExecute}
            onClick={() => void execute()}
          >
            {execState === 'running' ? 'Ejecutando…' : 'Ejecutar reglas seleccionadas'}
          </button>

          {execState === 'ok' && (
            <span className="okText">Ciclo completado. El motor evaluó las reglas.</span>
          )}
          {execState === 'conflict' && (
            <span className="muted">Ya hay una operación de hardware en curso (409). Esperá.</span>
          )}
          {execState === 'error' && execError && (
            <span className="errorText">{execError}</span>
          )}
        </div>
      </section>

      {/* Execution log */}
      {execLog.length > 0 && (
        <section className="card">
          <h3 className="sectionTitle" style={{ marginBottom: 8 }}>Traza de ejecución</h3>
          <div style={{ fontFamily: 'ui-monospace, Consolas, monospace', fontSize: 12.5, lineHeight: 1.7 }}>
            {execLog.map((line, i) => (
              <div
                key={i}
                style={{
                  color: line.startsWith('✓')
                    ? 'var(--accent)'
                    : line.startsWith('✗')
                      ? 'var(--error)'
                      : 'var(--ink)',
                }}
              >
                {line}
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
