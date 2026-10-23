/* ============================================================
   Apartado 3: Automatización de Telemetría para Ejecución de Reglas.

   Lets the operator select one or more agronomic rules, automatically computes the
   telemetry values needed to trigger them (reading current thresholds from the backend),
   and executes the full cycle in one click:
     1. Zeroes demo cooldowns (if in demo mode and the rules need it).
     1b. Turns on the bypasses a scenario asks for (N-02's `samples`), on top of the ones already active.
     2. Injects simulated weather when needed (R-03 / ShadingRule).
     3. Publishes the calculated MQTT telemetry for the target sensor.
     4. Registers the prior AI diagnosis (SupplyRule only acts on a NEW one).
     4b. Registers the same diagnosis on adjacent sectors of the zone (the outbreak scenario).
     5. Creates the manual lock the "Bloqueo manual" scenario asks for (before the pulse).
     5b. Publishes the node errors / registers the repairs of the S-05 scenarios (before the pulse).
     6. Triggers a sensing pulse with the rule engine (or the chain of pulses a scenario asks for).

   The "node without answer" scenario is the exception: it silences the target zone instead of
   publishing telemetry, so the pulses find no answer. The "invalid reading" scenario sticks a metric of the
   target sensor on an impossible value (`pinnedReadings`): the pulse and every reread carry it, so the backend's
   reread-and-discard flow (S-03) happens inside the pulse. The "incomplete hardware" scenario pins dry soil and reads
   the hardware registry (never edits it) to tell which sectors of the zone have no electrovalve (S-04). The "fault"
   scenarios (S-05) publish the error a node would (`POST /api/node-errors`), wait until the registry shows the mark and
   then ask for the pulse; the "repair" scenario registers the repair of the zone's faulty devices through the public
   repair endpoint, the one the dashboard uses.

   No backend modifications: everything goes through existing public endpoints.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import * as api from '../api';
import { registerDiagnosisWithCapture } from '../captureFlow';
import type { DemoRule, ManualLock, ParametroRegla, SectorRef, SimulatedSensor } from '../types';
import { buildThresholds, calculateConditions } from '../ruleConditions';
import { shadeMarked, valveMarked, waitUntil } from '../faultFlow';
import { faultyDevicesOf, sectorsWithValve, sectorsWithoutValve, type HardwareDevice } from '../hardwareCoverage';
import { runPulses } from '../pulseChain';
import { applyBypassRules } from '../bypassFlow';
import { pickOutbreakSectors } from '../outbreakFlow';
import { ensureTankStock } from '../tankStock';
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
  const [pinnedReadings, setPinnedReadings] = useState<api.PinnedReadings>({});
  // Hardware registry (read-only): only loaded when the scenario that needs it is selected.
  const [devices, setDevices] = useState<HardwareDevice[] | null>(null);
  const [hardwareError, setHardwareError] = useState<string | null>(null);
  const [manualLocks, setManualLocks] = useState<ManualLock[]>([]);
  // '' = lock the whole zone; otherwise the id of one of its sectors.
  const [lockSectorId, setLockSectorId] = useState('');
  // Sector whose valve the S-05 hydraulic fault breaks; '' = the first of the zone that has its valve registered.
  const [faultSectorId, setFaultSectorId] = useState('');

  // Zones the simulator keeps mute: they stay that way until released, so they are shown after the run.
  useEffect(() => {
    api.getSilencedZones().then(setSilencedZones).catch(() => setSilencedZones([]));
  }, []);

  // Pinned readings: the scenario sets them and they stay until the zone is released, so they are listed after the run.
  useEffect(() => {
    api.getPinnedReadings().then(setPinnedReadings).catch(() => setPinnedReadings({}));
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

  const releasePinned = async (zonaId: string) => {
    try {
      setPinnedReadings(await api.pinReadings(zonaId, {}));
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

  const needsSectorWithoutValve = conditions?.needsSectorWithoutValve ?? false;
  const needsSectorWithValve = conditions?.needsSectorWithValve ?? false;
  const needsFaultyDevice = conditions?.needsFaultyDevice ?? false;
  // The hardware registry (read-only) is only consulted when a selected scenario needs to know what is in it.
  const needsRegistry = needsSectorWithoutValve || needsSectorWithValve || needsFaultyDevice;

  const loadDevices = useCallback(async () => {
    try {
      setDevices(await api.getHardwareDevices());
      setHardwareError(null);
    } catch (e) {
      setDevices(null);
      setHardwareError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    if (needsRegistry) void loadDevices();
  }, [needsRegistry, loadDevices]);

  const targetSensor = sensors.find((s) => s.serial === targetSerial);
  // The diagnosis is per sector, the sensing per zone: the first sector of the target zone stands in.
  const diagnosisSector = sectors.find((s) => s.zonaId === targetSensor?.zonaId);
  const missingDiagnosisSector = conditions?.diagnostico != null && !diagnosisSector;
  const zoneSectors = sectors.filter((s) => s.zonaId === targetSensor?.zonaId);
  // A sector picked for a previous zone is not valid for this one.
  const lockSector = zoneSectors.find((s) => s.id === lockSectorId);
  // Sectors of the target zone that have no electrovalve registered: where the S-04 abort shows up.
  const uncoveredSectors = needsSectorWithoutValve && devices ? sectorsWithoutValve(zoneSectors, devices) : [];
  const hardwareUnreadable = needsRegistry && devices === null && hardwareError !== null;
  const noSectorWithoutValve = needsSectorWithoutValve && devices !== null && uncoveredSectors.length === 0;
  // S-05: sectors of the target zone whose valve a hydraulic fault can be marked on, and the faulty devices to repair.
  const valveSectors = needsSectorWithValve && devices ? sectorsWithValve(zoneSectors, devices) : [];
  const faultSector = valveSectors.find((s) => s.id === faultSectorId) ?? valveSectors[0];
  const noSectorWithValve = needsSectorWithValve && devices !== null && valveSectors.length === 0;
  const faultyDevices = needsFaultyDevice && devices && targetSensor ? faultyDevicesOf(targetSensor.zonaId, sectors, devices) : [];
  const noFaultyDevice = needsFaultyDevice && devices !== null && faultyDevices.length === 0;

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

      // Step 1b: bypasses a scenario needs (they go on top of the ones the operator already has). Without the backend in
      // demo mode there is no bypass to turn on: the run goes on and says so, because the rule may not decide without it.
      if (Object.keys(conditions.bypassRules).length > 0) {
        if (demoEnabled) {
          log.push(...(await applyBypassRules(conditions.bypassRules, { getRules: api.getDemoRules, updateRule: api.updateDemoRule })));
        } else {
          log.push('⚠ El backend no está en modo demo: no se pueden activar los bypasses que pide el escenario, y la regla puede no decidir');
        }
        setExecLog([...log]);
      }

      // Step 1c: the «sin pronóstico» mode (O-01…O-03). It goes in before the weather: the offline mode wins over it.
      if (conditions.offlineForecast) {
        if (demoEnabled) {
          await api.setOfflineForecast(true);
          log.push('✓ Modo sin pronóstico activado: las reglas asumen que no llueve');
        } else {
          log.push('⚠ El backend no está en modo demo: no se puede activar el modo sin pronóstico');
        }
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

      // Step 3b: pinned readings go in after the healthy reading and before the pulse, so the pulse's answer (the one the
      // engine decides on) and each reread carry them.
      if (Object.keys(conditions.pinnedMetrics).length > 0) {
        setPinnedReadings(await api.pinReadings(targetSensor.zonaId, conditions.pinnedMetrics));
        log.push(
          `✓ ${targetSensor.zonaId} contesta siempre ${Object.entries(conditions.pinnedMetrics)
            .map(([metric, value]) => `${metric} = ${value}`)
            .join(', ')}`,
        );
        setExecLog([...log]);
      }

      // Step 3c: S-04 needs a sector with no valve. The registry is read again: it may have changed since it loaded.
      if (conditions.needsSectorWithoutValve) {
        const uncovered = sectorsWithoutValve(zoneSectors, await api.getHardwareDevices());
        if (uncovered.length === 0) {
          throw new Error(`Todos los sectores de ${targetSensor.zonaId} tienen su electroválvula registrada: no hay dónde mostrar el aborto.`);
        }
        log.push(
          `✓ ${uncovered.length} sectores de ${targetSensor.zonaId} sin electroválvula registrada (${uncovered
            .slice(0, 5)
            .map((s) => s.id)
            .join(', ')}${uncovered.length > 5 ? '…' : ''}): el pulso debe abortarles el riego`,
        );
        setExecLog([...log]);
      }

      // Step 3d: S-05 node errors. The node publishes them on the broker; the backend marks the device when it processes
      // them, so the pulse waits until the registry shows the mark. If it never does (receiver switched off in the rules
      // panel, device not registered) the run goes on and says so.
      if (conditions.nodeErrors.length > 0) {
        for (const plan of conditions.nodeErrors) {
          const isShade = plan.tipo === 'MEDIASOMBRA_FIN_DE_CARRERA';
          if (!isShade && !faultSector) {
            throw new Error(`Ningún sector de ${targetSensor.zonaId} tiene su electroválvula registrada: no hay dónde marcar la falla.`);
          }
          await api.sendNodeError({ ...plan, zonaId: targetSensor.zonaId, sector: isShade ? '' : faultSector!.id });
          log.push(
            isShade
              ? `✓ ${targetSensor.zonaId} informa ${plan.tipo} (final «${String(plan.valor).toLowerCase()}»)`
              : `✓ ${targetSensor.zonaId} informa ${plan.tipo} (${plan.valor} L/h) en ${faultSector!.id}`,
          );
          setExecLog([...log]);
        }
        const marked = await waitUntil(
          async () => {
            const registry = await api.getHardwareDevices();
            return conditions.nodeErrors.every((plan) =>
              plan.tipo === 'MEDIASOMBRA_FIN_DE_CARRERA'
                ? shadeMarked(registry, targetSensor.zonaId, sectors)
                : valveMarked(registry, faultSector!.id),
            );
          },
          { sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)) },
        );
        log.push(
          marked
            ? '✓ El backend marcó la falla en el registro de hardware'
            : '⚠ El backend no marcó la falla: ¿el receptor de errores está apagado (Telemetría → Control de reglas) o el dispositivo no está registrado? El pulso sigue igual',
        );
        setExecLog([...log]);
      }

      // Step 3e: S-05 repair. The registry is read again (the faults may have changed since it loaded) and every faulty
      // device of the zone gets its repair registered through the public endpoint.
      if (conditions.repair) {
        const faulty = faultyDevicesOf(targetSensor.zonaId, sectors, await api.getHardwareDevices());
        if (faulty.length === 0) {
          throw new Error(`${targetSensor.zonaId} no tiene dispositivos marcados como averiados: no hay nada que reparar.`);
        }
        for (const d of faulty) {
          await api.registerRepair(d.id, conditions.repair.detail);
          log.push(`✓ Reparación registrada en ${d.tipo} ${d.sectorId ?? d.zonaId} (${d.id}, ${d.falla})`);
          setExecLog([...log]);
        }
      }

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

      // Step 4b: an outbreak (F-04) needs the same diagnosis on several sectors of the zone, adjacent ones from the middle of
      // its grid. They all go in before the pulse, one capture each, through the same public endpoints as any diagnosis.
      if (conditions.outbreak) {
        const affected = pickOutbreakSectors(zoneSectors, conditions.outbreak.sectors);
        if (affected.length === 0) {
          throw new Error(
            `${targetSensor.zonaId} tiene menos de ${conditions.outbreak.sectors} sectores: no alcanza para mostrar un foco.`,
          );
        }
        for (const affectedSector of affected) {
          const created = await registerDiagnosisWithCapture({
            ...conditions.outbreak.diagnosis,
            sectorId: affectedSector.id,
            zonaId: affectedSector.zonaId,
          });
          log.push(
            `✓ Diagnóstico IA ${created.id} registrado en ${affectedSector.id} (${conditions.outbreak.diagnosis.estado}, conf. ${conditions.outbreak.diagnosis.conf}%)`,
          );
          setExecLog([...log]);
        }
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

      // Step 5b: an application is rejected against an unregistered or empty tank, so the modifier leaves the tanks
      // of the target zone with stock (registers what is missing, tops up what is low) before the engine decides.
      if (conditions.ensureTankStock) {
        for (const line of await ensureTankStock(targetSensor.zonaId, { getTanks: api.getTanks, loadTank: api.loadTank })) {
          log.push(line);
        }
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
    !hardwareUnreadable &&
    !noSectorWithoutValve &&
    !noSectorWithValve &&
    !noFaultyDevice &&
    (!needsRegistry || devices !== null) &&
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

        {Object.keys(pinnedReadings).length > 0 && (
          <div className="card" style={{ marginTop: 12 }}>
            <p className="sectionTitle" style={{ marginBottom: 6 }}>Lecturas fijadas</p>
            {Object.entries(pinnedReadings).map(([zonaId, metrics]) => (
              <div key={zonaId} className="row" style={{ alignItems: 'center', gap: 12 }}>
                <span>
                  {zonaId} contesta siempre{' '}
                  {Object.entries(metrics)
                    .map(([metric, value]) => `${metric} = ${value}`)
                    .join(', ')}
                </span>
                <button type="button" onClick={() => void releasePinned(zonaId)}>
                  Soltar
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

        {hardwareUnreadable && (
          <div className="errorMsg" style={{ marginTop: 12 }}>
            No se pudo leer el registro de hardware ({hardwareError}). El rol Servicio del simulador necesita
            `hardware.ver` (y `hardware.reparar` para registrar reparaciones): en una base ya sembrada, corré
            `migracion-permisos-hardware-servicio.sql` y `migracion-permisos-hardware-reparar.sql` del backend.
          </div>
        )}

        {noSectorWithValve && (
          <div className="errorMsg" style={{ marginTop: 12 }}>
            Ningún sector de {targetSensor?.zonaId} tiene su electroválvula registrada: no hay dónde marcar la falla
            hidráulica. Elegí otra zona.
          </div>
        )}

        {noFaultyDevice && (
          <div className="errorMsg" style={{ marginTop: 12 }}>
            {targetSensor?.zonaId} no tiene dispositivos marcados como averiados: no hay nada que reparar. Marcá una falla
            primero («Sin caudal», «Caudal excesivo» o «Falla de mediasombra»).
          </div>
        )}

        {noSectorWithoutValve && (
          <div className="errorMsg" style={{ marginTop: 12 }}>
            Todos los sectores de {targetSensor?.zonaId} tienen su electroválvula registrada: no hay un sector donde
            mostrar el aborto. Elegí otra zona.
          </div>
        )}

        {uncoveredSectors.length > 0 && (
          <div className="card" style={{ marginTop: 12 }}>
            <p className="sectionTitle" style={{ marginBottom: 6 }}>
              Sectores de {targetSensor?.zonaId} sin electroválvula registrada ({uncoveredSectors.length})
            </p>
            <p style={{ margin: 0, fontSize: 13 }}>
              {uncoveredSectors
                .slice(0, 8)
                .map((s) => s.id)
                .join(', ')}
              {uncoveredSectors.length > 8 && ` y ${uncoveredSectors.length - 8} más`}. El pulso debe abortarles el riego.
            </p>
          </div>
        )}

        {faultyDevices.length > 0 && (
          <div className="card" style={{ marginTop: 12 }}>
            <p className="sectionTitle" style={{ marginBottom: 6 }}>
              Dispositivos averiados de {targetSensor?.zonaId} ({faultyDevices.length})
            </p>
            <p style={{ margin: 0, fontSize: 13 }}>
              {faultyDevices.map((d) => `${d.id} · ${d.sectorId} · ${d.falla}`).join(' — ')}. Se registra su reparación antes
              del pulso.
            </p>
          </div>
        )}

        {needsSectorWithValve && valveSectors.length > 0 && (
          <div className="row" style={{ marginTop: 12 }}>
            <label className="field">
              <span className="label">Sector de la electroválvula con falla</span>
              <select value={faultSector?.id ?? ''} onChange={(e) => setFaultSectorId(e.target.value)}>
                {valveSectors.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.id}
                  </option>
                ))}
              </select>
            </label>
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
