/* ============================================================
   Yerbanalytics hardware simulator.

   All it does is stand in for the physical hardware: it publishes telemetry to the broker as
   an ESP32 node would, and exercises the rail's capture cycle. The system does not know it
   exists, and shutting it down leaves the nursery waiting for real telemetry, which is its
   production behaviour.
   ============================================================ */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { CameraPanel } from './components/CameraPanel';
import { DemoExpoSection } from './components/DemoExpoSection';
import { GeneralConfigSection } from './components/GeneralConfigSection';
import { NavigationTabs, useActiveTab } from './components/NavigationTabs';
import { RuleAutomationSection } from './components/RuleAutomationSection';
import { Section } from './components/Section';
import { StatusBar } from './components/StatusBar';
import { TelemetryRulesSection } from './components/TelemetryRulesSection';
import * as api from './api';
import type {
  NurseryTopology,
  SectorRef,
  SimulatedSensor,
  SimulatorStatus,
} from './types';

export function App() {
  const [activeTab, setActiveTab] = useActiveTab();

  const [status, setStatus] = useState<SimulatorStatus | null>(null);
  const [sensors, setSensors] = useState<SimulatedSensor[]>([]);
  const [topology, setTopology] = useState<NurseryTopology | null>(null);
  const [sectors, setSectors] = useState<SectorRef[]>([]);
  const [demoEnabled, setDemoEnabled] = useState(false);

  // Status is polled: the broker or the backend may go down and come back while the
  // simulator stays open, and the bar has to reflect it without a reload.
  useEffect(() => {
    let alive = true;
    const refresh = async () => {
      try {
        const current = await api.getStatus();
        if (alive) setStatus(current);
      } catch {
        if (alive) setStatus(null);
      }
    };
    void refresh();
    const id = window.setInterval(() => void refresh(), 5000);
    return () => {
      alive = false;
      window.clearInterval(id);
    };
  }, []);

  const reloadSensors = useCallback(async () => {
    setSensors(await api.getSensors().catch(() => []));
  }, []);

  useEffect(() => {
    void reloadSensors();
  }, [reloadSensors]);

  // Detect demo mode: if GET /api/demo/rules succeeds, the backend is in demo mode.
  useEffect(() => {
    api
      .getDemoRules()
      .then(() => setDemoEnabled(true))
      .catch(() => setDemoEnabled(false));
  }, []);

  /** Topology and sectors: used by the sensor form and the camera panel. */
  const loadNursery = useCallback(async () => {
    setTopology(await api.getTopology().catch(() => null));
    setSectors(await api.getSectors().catch(() => []));
  }, []);

  useEffect(() => {
    void loadNursery();
  }, [loadNursery]);

  /** Macro-zones of the current topology, for the sensor form. */
  const zones = useMemo(
    () => Array.from({ length: Math.max(0, topology?.macroZonas ?? 0) }, (_, i) => `MZ-${i + 1}`),
    [topology],
  );

  const handleTopologyRegenerated = (next: NurseryTopology) => {
    setTopology(next);
    void loadNursery();
  };

  const handleStatusChanged = (active: boolean) => {
    setStatus((prev) => (prev ? { ...prev, emission: { ...prev.emission, active } } : prev));
  };

  return (
    <div className="container">
      <header className="header">
        <h1>Simulador</h1>
        <span className="brand">Yerbanalytics · herramienta de prueba</span>
      </header>

      <StatusBar status={status} />

      <NavigationTabs active={activeTab} onChange={setActiveTab} />

      <main>
        {activeTab === 'configuracion' && (
          <GeneralConfigSection
            topology={topology}
            zones={zones}
            sensors={sensors}
            status={status}
            onTopologyRegenerated={handleTopologyRegenerated}
            onSensorsChanged={() => void reloadSensors()}
            onStatusChanged={handleStatusChanged}
          />
        )}

        {activeTab === 'telemetria' && (
          <TelemetryRulesSection
            sensors={sensors}
            status={status}
            demoEnabled={demoEnabled}
          />
        )}

        {activeTab === 'automatizacion' && (
          <RuleAutomationSection sensors={sensors} demoEnabled={demoEnabled} />
        )}

        {activeTab === 'camara' && (
          <Section
            title="Cámara del riel"
            hint="Banco de pruebas del dispositivo de captura y del recorrido completo hasta el diagnóstico."
          >
            <CameraPanel sectors={sectors} />
          </Section>
        )}

        {activeTab === 'demo-expo' && <DemoExpoSection />}
      </main>
    </div>
  );
}
