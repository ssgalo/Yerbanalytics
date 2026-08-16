/* ============================================================
   Yerbanalytics hardware simulator.

   All it does is stand in for the physical hardware: it publishes telemetry to the broker as
   an ESP32 node would, and exercises the rail's capture cycle. The system does not know it
   exists, and shutting it down leaves the nursery waiting for real telemetry, which is its
   production behaviour.
   ============================================================ */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { AutoEmission } from './components/AutoEmission';
import { CameraPanel } from './components/CameraPanel';
import { NewSensor } from './components/NewSensor';
import { Section } from './components/Section';
import { SensorCard } from './components/SensorCard';
import { StatusBar } from './components/StatusBar';
import { Topology } from './components/Topology';
import * as api from './api';
import type {
  NurseryTopology,
  SectorRef,
  SimulatedSensor,
  SimulatorStatus,
  TelemetryInput,
} from './types';

export function App() {
  const [status, setStatus] = useState<SimulatorStatus | null>(null);
  const [sensors, setSensors] = useState<SimulatedSensor[]>([]);
  const [topology, setTopology] = useState<NurseryTopology | null>(null);
  const [sectors, setSectors] = useState<SectorRef[]>([]);

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

  useEffect(() => {
    void api
      .getSensors()
      .then(setSensors)
      .catch(() => setSensors([]));
  }, []);

  /** Topology and sectors: used by the sensor form and the camera panel. */
  const loadNursery = useCallback(async () => {
    // The backend may not be up: the simulator starts anyway and the status bar says so, so
    // leaving the lists empty is enough here.
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

  const createSensor = async (sensor: SimulatedSensor) => {
    await api.createSensor(sensor);
    setSensors(await api.getSensors());
  };

  const deleteSensor = async (serial: string) => {
    await api.deleteSensor(serial);
    setSensors(await api.getSensors());
  };

  const sendTelemetry = async (input: TelemetryInput) => {
    await api.sendTelemetry(input);
  };

  return (
    <div className="container">
      <header className="header">
        <h1>Simulador</h1>
        <span className="brand">Yerbanalytics · herramienta de prueba</span>
      </header>

      <p className="intro">
        Simulá el comportamiento del hardware sin equipos físicos: creá sensores, asignalos a
        una macro-zona y publicá sus lecturas en el broker MQTT, exactamente como las emitiría
        un nodo ESP32. El sistema las ingiere sin distinguirlas de las reales. Para que además
        un nodo actualice su estado técnico, registrá el hardware en la sección Hardware del
        dashboard con el <strong>mismo serial/MAC y la misma macro-zona</strong>.
      </p>

      <StatusBar status={status} />

      <Topology
        topology={topology}
        onRegenerated={(next) => {
          setTopology(next);
          void loadNursery();
        }}
      />

      {status && (
        <AutoEmission
          active={status.emission.active}
          intervalMs={status.emission.intervalMs}
          onChange={(active) =>
            setStatus((prev) =>
              prev ? { ...prev, emission: { ...prev.emission, active } } : prev,
            )
          }
        />
      )}

      <NewSensor zones={zones} onCreate={createSensor} />

      {sensors.length === 0 ? (
        <Section title="Sensores simulados">
          <p className="empty">
            No hay sensores simulados todavía. Creá uno arriba para empezar a publicar lecturas.
          </p>
        </Section>
      ) : (
        <div className="sensors">
          {sensors.map((s) => (
            <SensorCard
              key={s.serial}
              sensor={s}
              onSend={sendTelemetry}
              onDelete={deleteSensor}
            />
          ))}
        </div>
      )}

      <h2 className="sectionTitle" style={{ margin: '28px 0 4px' }}>
        Cámara del riel
      </h2>
      <p className="sectionHint">
        Banco de pruebas del dispositivo de captura y del recorrido completo hasta el
        diagnóstico.
      </p>

      <CameraPanel sectors={sectors} />
    </div>
  );
}
